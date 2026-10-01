"""按 Flyway 顺序归并结构变更，生成论文与设计说明共用的数据字典。"""

from pathlib import Path
import hashlib
import json
import re

ROOT = Path(__file__).resolve().parents[3]
OUTPUT = ROOT / "database/design"
SERVICES = {"admin": 8, "training": 15, "learning": 7}
COMMON_COMMENTS = {
    "created_at": "创建时间", "updated_at": "更新时间", "deleted_at": "删除时间",
    "attendance_action": "当次人脸核验对应动作", "attendance_sequence": "核验凭据绑定的下一事件序号",
    "attendance_verified_at": "当次人脸核验通过时间", "active_user_id": "活动会话唯一约束生成列",
    "pending_session_id": "待处理抽验任务唯一约束生成列",
}
CORE_TABLES = ["sys_user", "train_course", "train_plan", "train_plan_user",
               "study_session", "study_progress", "face_check_task", "exam_record"]


def split_sql(value, delimiter=","):
    """仅在引号和括号之外分割，保留生成表达式及复合类型。"""
    parts, start, depth, quote, i = [], 0, 0, None, 0
    while i < len(value):
        char = value[i]
        if quote:
            if char == "\\":
                i += 2
                continue
            if char == quote:
                if i + 1 < len(value) and value[i + 1] == quote:
                    i += 2
                    continue
                quote = None
        elif char in "'\"`":
            quote = char
        elif char == "(":
            depth += 1
        elif char == ")":
            depth -= 1
        elif char == delimiter and depth == 0:
            parts.append(value[start:i].strip())
            start = i + 1
        i += 1
    assert not quote and depth == 0, "SQL 引号或括号不平衡"
    parts.append(value[start:].strip())
    return [part for part in parts if part]


def parse_index(text):
    """读取索引名、类型及有序字段，不将复合唯一键误写为单列唯一。"""
    match = re.fullmatch(r"(PRIMARY KEY|UNIQUE KEY|KEY)\s*(?:`?(\w+)`?)?\s*\((.*)\)(?:\s+USING\s+(\w+))?", text, re.I | re.S)
    if not match:
        raise ValueError("不支持的索引定义：" + text)
    kind = {"PRIMARY KEY": "PRIMARY", "UNIQUE KEY": "UNIQUE", "KEY": "INDEX"}[match[1].upper()]
    index = {"name": match[2] or "PRIMARY", "kind": kind,
             "columns": [item.strip(" `") for item in split_sql(match[3])], "ddl": text}
    if match[4]:
        index["method"] = match[4].upper()
    return index


def parse_column(text, source):
    """解析本仓库使用的 MySQL 字段语法，未知类型立即报错。"""
    text = re.sub(r"\s+", " ", text.strip())
    position = re.search(r"\s+AFTER\s+(\w+)$", text, re.I)
    if position:
        text = text[:position.start()]
    match = re.match(r"`?(\w+)`?\s+([A-Z]+(?:\(\d+(?:,\s*\d+)?\))?(?:\s+UNSIGNED)?)", text, re.I)
    # 明确类型集合，防止把未知约束当作普通字段吞掉。
    if not match:
        raise ValueError(text)
    name, data_type = match[1], match[2].upper()
    assert re.match(r"^(BIGINT|INT|TINYINT|VARCHAR|CHAR|DATETIME|DECIMAL|LONGTEXT|TEXT)\b", data_type), text
    suffix = text[match.end():]
    comment = re.search(r"\bCOMMENT\s+'((?:[^']|'')*)'", suffix, re.I)
    attributes = suffix[:comment.start()] if comment else suffix
    default = re.search(r"\bDEFAULT\s+('(?:[^']|'')*'|\w+\([^)]*\)|[\w.+-]+)", attributes, re.I)
    generated = re.search(r"GENERATED ALWAYS AS\s*\((.*)\)\s*STORED", attributes, re.I)
    on_update = re.search(r"ON UPDATE\s+(\w+\([^)]*\)|\w+)", attributes, re.I)
    description = comment[1].replace("''", "'") if comment else COMMON_COMMENTS.get(name, "")
    column = {"name": name, "type": data_type, "nullable": not bool(re.search(r"\bNOT NULL\b", attributes, re.I)),
            "default": default[1] if default else None, "explicit_default": bool(default),
            "generated": generated[1].strip() if generated else None,
            "on_update": on_update[1] if on_update else None,
            "comment": description, "description_source": "DDL" if comment else "根据字段及实现归纳",
            "source": source, "ddl": text, "after": position[1] if position else None}
    if re.search(r"\bAUTO_INCREMENT\b", attributes, re.I):
        column["auto_increment"] = True
    for key, pattern in (("charset", r"\bCHARACTER SET\s+(\w+)"), ("collation", r"\bCOLLATE\s+(\w+)")):
        value = re.search(pattern, attributes, re.I)
        if value:
            column[key] = value[1]
    return column


def apply_statement(tables, statement, service, source):
    """归并 CREATE 和 ALTER；不执行迁移，不连接或修改数据库。"""
    create = re.fullmatch(r"CREATE TABLE\s+`?(\w+)`?\s*\((.*)\)\s*(ENGINE=.*)", statement, re.I | re.S)
    if create:
        name, body, tail = create.groups()
        assert name not in tables, name
        table = {"name": name, "service": service, "database": "road_training_" + service,
                 "columns": [], "indexes": [], "sources": [source], "tail": re.sub(r"\s+", " ", tail),
                 "comment": re.search(r"COMMENT\s*=\s*'([^']*)'", tail, re.I)[1], "verified": True}
        for item in split_sql(body):
            if re.match(r"^(PRIMARY|UNIQUE|KEY)\b", item, re.I):
                table["indexes"].append(parse_index(item))
            else:
                table["columns"].append(parse_column(item, source))
        tables[name] = table
        return
    alter = re.fullmatch(r"ALTER TABLE\s+(\w+)\s+(.*)", statement, re.I | re.S)
    if alter:
        table = tables[alter[1]]
        if source not in table["sources"]:
            table["sources"].append(source)
        for operation in split_sql(alter[2]):
            match = re.match(r"(ADD|MODIFY|DROP)\s+(COLUMN|INDEX|UNIQUE KEY|KEY)\s+(.*)", operation, re.I | re.S)
            if not match:
                raise ValueError("未支持的结构变更：" + operation)
            action, kind, definition = match[1].upper(), match[2].upper(), match[3].strip()
            if kind == "COLUMN":
                if action == "DROP":
                    before = len(table["columns"])
                    table["columns"] = [column for column in table["columns"] if column["name"] != definition]
                    assert len(table["columns"]) == before - 1, operation
                    continue
                column = parse_column(definition, source)
                names = [old["name"] for old in table["columns"]]
                if action == "MODIFY":
                    table["columns"][names.index(column["name"])] = column
                else:
                    assert column["name"] not in names
                    index = names.index(column["after"]) + 1 if column["after"] else len(names)
                    table["columns"].insert(index, column)
            elif action == "DROP":
                before = len(table["indexes"])
                table["indexes"] = [index for index in table["indexes"] if index["name"] != definition]
                assert len(table["indexes"]) == before - 1, operation
            else:
                assert action == "ADD", operation
                table["indexes"].append(parse_index(kind + " " + definition))
        return
    assert not re.match(r"^(CREATE|ALTER|DROP|RENAME|TRUNCATE)\b", statement, re.I), statement


def logical_reference(table, column):
    """集中维护业务逻辑关联；所有关联均不代表数据库外键。"""
    common = {"enterprise_id": "train_org.id", "user_id": "sys_user.id", "owner_user_id": "sys_user.id",
              "org_id": "train_org.id", "vehicle_id": "train_vehicle.id", "area_id": "sys_address.id",
              "role_id": "sys_role.id", "permission_id": "sys_permission.id", "course_id": "train_course.id",
              "plan_id": "train_plan.id", "plan_course_id": "train_plan_course.id",
              "storage_object_id": "train_storage_object.id", "face_reference_object_id": "train_storage_object.id",
              "cover_object_id": "train_storage_object.id", "courseware_id": "train_courseware.id",
              "photo_object_id": "train_storage_object.id", "attendance_photo_object_id": "train_storage_object.id",
              "sign_in_photo_object_id": "train_storage_object.id", "sign_out_photo_object_id": "train_storage_object.id",
              "courseware_snapshot_id": "train_plan_courseware_snapshot.id",
              "current_courseware_snapshot_id": "train_plan_courseware_snapshot.id",
              "source_courseware_id": "train_courseware.id", "session_id": "study_session.id",
              "training_task_id": "train_plan_user.id", "exam_paper_id": "exam_paper.id", "paper_id": "exam_paper.id",
              "source_question_id": "exam_question.id", "record_id": "exam_record.id",
              "paper_question_id": "exam_paper_question.id"}
    if column in ("created_by", "updated_by", "deleted_by", "published_by", "cancelled_by", "enabled_by"):
        return "sys_user.id（操作人标识）"
    if column == "parent_id":
        return table + ".id"
    if table == "sys_address" and column == "parent_code":
        return "sys_address.area_code（根级使用代码0）"
    if column == "task_id":
        return "face_check_task.id" if table == "face_check_log" else "train_plan_user.id"
    return common.get(column, "")


def display_default(column):
    """区分显式默认值、隐式 NULL、无默认值与生成列。"""
    if column.get("unverified"):
        return "待核验"
    if column["generated"]:
        return "生成列"
    if column["explicit_default"]:
        return column["default"]
    return "隐式 NULL" if column["nullable"] else "无"


def enrich_table(table):
    """补充主键、逻辑关联与人工核对后的业务含义。"""
    primary = next(index["columns"] for index in table["indexes"] if index["kind"] == "PRIMARY")
    names = [column["name"] for column in table["columns"]]
    assert len(names) == len(set(names)), table["name"]
    for index in table["indexes"]:
        assert set(index["columns"]) <= set(names), index
    for column in table["columns"]:
        column["primary"] = column["name"] in primary
        if column["primary"]:
            column["nullable"] = False
        column["foreign_key"] = False
        column["logical_reference"] = logical_reference(table["name"], column["name"])
        column["indexes"] = [index["name"] for index in table["indexes"] if column["name"] in index["columns"]]
        if table["name"] == "train_plan" and column["name"] in ("exam_paper_id", "exam_pass_score"):
            column["comment"] = {"exam_paper_id": "关联试卷ID", "exam_pass_score": "计划考试及格分快照"}[column["name"]]
            column["description_source"] = "依据考试实现修正历史 DDL 的预留表述"
        if column["name"] in ("object_type", "upload_type") and table["name"] in ("train_storage_object", "train_private_image_upload_session"):
            column["comment"] += "；当前实现亦支持 LEARNING_PHOTO 学习照片"
            column["description_source"] = "DDL 与 PrivateImageStorageServiceImpl"
        assert column["comment"], f"缺少字段说明：{table['name']}.{column['name']}"


def external_address():
    """解析用户提供的外部表 DDL，保留独立来源，不冒充仓库迁移。"""
    source = "database/design/sys_address.source.sql"
    raw = (ROOT / source).read_text(encoding="utf-8")
    statements = split_sql(re.sub(r"(?m)^\s*--[^\n]*", "", raw), ";")
    assert len(statements) == 1
    tables = {}
    apply_statement(tables, statements[0], "admin", source)
    assert set(tables) == {"sys_address"}
    table = tables["sys_address"]
    table["external"] = True
    table["verification_basis"] = "用户于2026-09-30提供的真实DDL，未连接运行库执行核验"
    table["engine"] = re.search(r"\bENGINE=(\w+)", table["tail"], re.I)[1]
    table["charset"] = re.search(r"\bCHARSET=(\w+)", table["tail"], re.I)[1]
    collation = re.search(r"\bCOLLATE=(\w+)", table["tail"], re.I)
    table["collation"] = collation[1] if collation else None
    table["auto_increment_counter"] = int(re.search(r"\bAUTO_INCREMENT=(\d+)", table["tail"], re.I)[1])
    # id 没有 DDL 注释，仅其说明依据应用字段归纳，物理属性仍全部取自 DDL。
    table["columns"][0]["comment"] = "地址ID"
    table["note"] = "外部行政区域表不由Flyway创建；表级排序规则未显式声明。AUTO_INCREMENT=4063是DDL导出时的自增计数器值，不是记录数或字段默认值。"
    enrich_table(table)
    return table


def markdown_table(table):
    """生成可读表结构，索引独立列出以保留复合字段顺序。"""
    lines = [f"### {table['name']} {table['comment']}", "", f"所属数据库：`{table['database']}`。", ""]
    if not table["verified"]:
        lines += [table["note"], ""]
    elif table.get("external"):
        lines += [f"存储引擎：{table['engine']}；字符集：{table['charset']}；排序规则：DDL未显式声明表级值。", "", table["note"], ""]
    else:
        lines += ["存储引擎：InnoDB；字符集：utf8mb4；排序规则：utf8mb4_0900_ai_ci。", ""]
    lines += ["| 字段名 | 数据类型 | 主键／外键 | 可空 | 默认值 | 字段说明 |", "| --- | --- | --- | --- | --- | --- |"]
    for column in table["columns"]:
        key = "待核验" if not table["verified"] else ("PK；无FK" if column["primary"] else "无")
        note = column["comment"]
        if column["logical_reference"]:
            note += "；逻辑关联 " + column["logical_reference"]
        if column["on_update"]:
            note += "；ON UPDATE " + column["on_update"]
        if column.get("auto_increment"):
            note += "；AUTO_INCREMENT自增"
        if column.get("charset"):
            note += "；CHARACTER SET " + column["charset"] + "；COLLATE " + column["collation"]
        values = [column["name"], column["type"], key,
                  "待核验" if column["nullable"] is None else ("是" if column["nullable"] else "否"), display_default(column), note]
        lines.append("| " + " | ".join(value.replace("|", "\\|") for value in values) + " |")
    lines += ["", "索引：", "", "| 索引名 | 类型 | 有序字段 | 用途 |", "| --- | --- | --- | --- |"]
    for index in table["indexes"]:
        purpose = {"PRIMARY": "唯一标识记录", "UNIQUE": "约束字段组合唯一", "INDEX": "支持所列字段组合的检索"}[index["kind"]]
        kind = index["kind"] + (" / " + index["method"] if index.get("method") else "")
        lines.append(f"| {index['name']} | {kind} | {', '.join(index['columns'])} | {purpose} |")
    if not table["verified"]:
        lines.append("| 待真实 DDL 核验 | — | — | — |")
    for column in table["columns"]:
        if column["generated"]:
            lines += ["", f"生成列 `{column['name']}`：`{column['generated']}`，采用 STORED 存储。"]
    lines += ["", "结构依据：" + "、".join(f"`{source}`" for source in table["sources"]) + "。", ""]
    return "\n".join(lines)


def build():
    """输出单一结构源、Markdown 数据字典及只读归并 DDL。"""
    tables, sources = {}, []
    for service, expected in SERVICES.items():
        directory = ROOT / f"backend/train-{service}-service/src/main/resources/db/migration"
        for path in sorted(directory.glob("V*.sql"), key=lambda item: int(item.name.split("__")[0][1:])):
            source = path.relative_to(ROOT).as_posix()
            raw = path.read_text(encoding="utf-8-sig")
            sources.append({"path": source, "sha256": hashlib.sha256(path.read_bytes()).hexdigest()})
            sql = re.sub(r"(?m)^\s*--[^\n]*", "", raw)
            for statement in split_sql(sql, ";"):
                apply_statement(tables, statement, service, source)
        assert len([table for table in tables.values() if table["service"] == service]) == expected
    for table in tables.values():
        enrich_table(table)
    all_tables = list(tables.values()) + [external_address()]
    external_path = OUTPUT / "sys_address.source.sql"
    external_sources = [{"path": external_path.relative_to(ROOT).as_posix(),
                         "sha256": hashlib.sha256(external_path.read_bytes()).hexdigest(),
                         "origin": "用户于2026-09-30提供的真实DDL"}]
    document = {"baseline": "d273198f78ceaf02f2fbd8b7a5d3dc1efdaf14c4", "schema_source": "30张表按仓库Flyway顺序归并，外部sys_address按用户提供的DDL核验；未连接运行库",
                "physical_foreign_keys": 0, "core_tables": CORE_TABLES, "sources": sources,
                "external_sources": external_sources, "tables": all_tables}
    OUTPUT.mkdir(parents=True, exist_ok=True)
    (OUTPUT / "data-dictionary.json").write_text(json.dumps(document, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    introduction = "# 数据表结构字典\n\n本字典覆盖31张表、476个字段和113项索引：30张迁移管理表按各服务Flyway版本归并，外部sys_address按用户提供的真实DDL核验。它描述仓库及所提供DDL对应的结构，不证明运行库已应用所有迁移。\n\n全部31张表均未声明物理外键；字段说明中的关联是由服务校验的逻辑标识。PK为主键，FK为数据库外键。未显式设置默认值的可空普通列记为隐式NULL，非空列记为无；生成列另列计算表达式，自增属性单独注明。\n\n正文与附录使用同一份JSON结构源；更新迁移后运行 `docs/thesis/tools/build_data_dictionary.py` 重新生成。外部表原始定义保存在 `database/design/sys_address.source.sql`，其来源与指纹单独记录，不作为Flyway迁移。\n\n"
    (OUTPUT / "data-dictionary.md").write_text(introduction + "\n".join(markdown_table(table) for table in all_tables), encoding="utf-8")
    ddl = ["-- 论文结构核查用归并快照，不是迁移；不得直接对共享数据库执行。", "-- 30张迁移表按仓库版本归并，外部sys_address单独取自用户提供的DDL。"]
    for table in all_tables:
        items = [column["ddl"] for column in table["columns"]] + [index["ddl"] for index in table["indexes"]]
        ddl += [f"\n-- {table['database']}", f"CREATE TABLE {table['name']} (\n    " + ",\n    ".join(items) + "\n) " + table["tail"] + ";"]
    (OUTPUT / "schema-snapshot.sql").write_text("\n".join(ddl) + "\n", encoding="utf-8")
    print(json.dumps({"verified_tables": len(all_tables), "managed_tables": len(tables), "pending_tables": [],
                      "columns": sum(len(table["columns"]) for table in all_tables),
                      "indexes": sum(len(table["indexes"]) for table in all_tables)}, ensure_ascii=False))


if __name__ == "__main__":
    build()
