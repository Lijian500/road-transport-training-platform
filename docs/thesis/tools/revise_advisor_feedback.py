"""按导师意见精简论文附录，定点修订正文并保留原有公式与文献。"""

from argparse import ArgumentParser
from copy import deepcopy
from hashlib import sha256
import json
from pathlib import Path
import re
from zipfile import ZipFile

from lxml import etree as ET


ROOT = Path(__file__).resolve().parents[3]
NS = {"w": "http://schemas.openxmlformats.org/wordprocessingml/2006/main",
      "m": "http://schemas.openxmlformats.org/officeDocument/2006/math"}
W = "{" + NS["w"] + "}"
BASELINE = "af989235fd176af3c21441436d37fb377bff81129c69c4612ebf2648f9fd254f"
SUPPLEMENTAL = ["train_plan_course", "train_plan_courseware_snapshot",
                "study_event_log", "mq_outbox"]
CORE = ["sys_user", "train_course", "train_plan", "train_plan_user",
        "study_session", "study_progress", "face_check_task", "exam_record"]


def text_of(element):
    """仅提取可见文字，不将域指令混入正文。"""
    return "".join(element.xpath(".//w:t/text()", namespaces=NS))


def canonical(element):
    """以规范化XML检查未授权内容是否保持一致。"""
    return ET.tostring(element, method="c14n")


def paragraph_style(element):
    """读取段落样式标识，供标题与目录匹配使用。"""
    node = element.find("w:pPr/w:pStyle", NS)
    return node.get(W + "val") if node is not None else None


def replace_text(paragraph, value):
    """保留段落属性及书签，仅替换不含引用或图形的文字内容。"""
    assert not paragraph.xpath(".//m:oMath | .//w:drawing | .//w:instrText", namespaces=NS)
    first_run = paragraph.find("w:r", NS)
    properties = first_run.find("w:rPr", NS) if first_run is not None else None
    for child in list(paragraph):
        if child.tag not in (W + "pPr", W + "bookmarkStart", W + "bookmarkEnd"):
            paragraph.remove(child)
    run = ET.SubElement(paragraph, W + "r")
    if properties is not None:
        run.append(deepcopy(properties))
    ET.SubElement(run, W + "t").text = value


def new_paragraph(template, value):
    """复用既有段落版式，新段落不沿用原书签或段落唯一标识。"""
    paragraph = deepcopy(template)
    for key in list(paragraph.attrib):
        paragraph.attrib.pop(key)
    for child in list(paragraph):
        if child.tag in (W + "bookmarkStart", W + "bookmarkEnd"):
            paragraph.remove(child)
    replace_text(paragraph, value)
    return paragraph


def structure_tables(body):
    """定位正文与附录的字段表，表题必须紧邻真实字段表。"""
    result = {}
    for paragraph in body:
        match = re.fullmatch(r"表(4-\d+|B-\d+) (\w+)表结构", text_of(paragraph))
        if match:
            table = paragraph.getnext()
            assert table is not None and table.tag == W + "tbl"
            result[match[1]] = (match[2], paragraph, table)
    return result


def constraint_data(dictionary):
    """从完整字典读取特殊唯一键和生成列表达式，不复制过期DDL。"""
    tables = {table["name"]: table for table in dictionary["tables"]}
    used = []

    def unique(table_name, index_name):
        """核对唯一键并输出有序字段，保留实际业务键范围。"""
        table = tables[table_name]
        index = next(item for item in table["indexes"] if item["name"] == index_name)
        assert index["kind"] == "UNIQUE"
        used.append({"table": table_name, "index": index_name,
                     "columns": index["columns"], "sources": table["sources"]})
        return "UNIQUE (" + ", ".join(index["columns"]) + ")"

    def generated(table_name, column_name):
        """读取生成列原始表达式并核对STORED声明。"""
        table = tables[table_name]
        column = next(item for item in table["columns"] if item["name"] == column_name)
        assert column["generated"] and "STORED" in column["ddl"].upper()
        used.append({"table": table_name, "column": column_name,
                     "expression": column["generated"], "storage": "STORED",
                     "sources": table["sources"]})
        return column_name + " = " + column["generated"]

    rows = [
        ("企业编码", "train_org\n" + generated("train_org", "enterprise_code_key") + "\n"
         + unique("train_org", "uk_org_enterprise_global_code") + "\n"
         + unique("train_org", "uk_org_enterprise_code"), "企业编码全局唯一；部门编码仍在企业内唯一"),
        ("平台管理员", "sys_user\n" + generated("sys_user", "platform_admin_key") + "\n"
         + unique("sys_user", "uk_user_single_platform_admin"), "平台超级管理员至多一个；普通账号生成值为空"),
        ("活动学习会话", "study_session\n" + generated("study_session", "active_user_id") + "\n"
         + unique("study_session", "uk_study_session_active_user"), "同一企业内同一学员至多一个活动会话"),
        ("待处理抽验", "face_check_task\n" + generated("face_check_task", "pending_session_id") + "\n"
         + unique("face_check_task", "uk_face_check_pending_session"), "同一会话至多一个PENDING抽验任务"),
        ("事件幂等", "study_event_log\n" + unique("study_event_log", "uk_study_event_request") + "\n"
         + unique("study_event_log", "uk_study_event_sequence"), "请求标识和事件序号分别去重，防止重复事实"),
        ("课程规则快照", "train_plan_course\n" + unique("train_plan_course", "uk_plan_course"), "同一计划内，同一来源课程至多保留一条快照"),
        ("课件清单快照", "train_plan_courseware_snapshot\n"
         + unique("train_plan_courseware_snapshot", "uk_plan_courseware"), "同一计划内，同一来源课件至多保留一条快照"),
        ("试卷题目快照", "exam_paper_question\n" + unique("exam_paper_question", "uk_exam_paper_question"), "同一试卷内，同一来源题目至多保留一条快照"),
        ("考试记录与答案", "exam_record\n" + unique("exam_record", "uk_exam_record_task")
         + "\nexam_answer\n" + unique("exam_answer", "uk_exam_answer_question"), "每个培训任务至多一条考试记录；同一考试记录内，同一道试卷题目至多一条答案"),
        ("可靠消息与消费", "mq_outbox\n" + unique("mq_outbox", "uk_outbox_event") + "\n"
         + unique("mq_outbox", "uk_outbox_business") + "\nmq_consume_log\n"
         + unique("mq_consume_log", "uk_consume_event"), "发送端约束事件和业务键，消费端按消费者及事件去重"),
    ]
    # 逐项核对迁移来源；字典保留完整31表，不因论文节选而缩减。
    sources = sorted({path for item in used for path in item["sources"]})
    for item in used:
        ddl = "\n".join((ROOT / path).read_text(encoding="utf-8") for path in item["sources"])
        if "index" in item:
            assert re.search(r"\b" + re.escape(item["index"]) + r"\b", ddl), item
        else:
            compact = lambda value: re.sub(r"\s+", "", value)
            assert compact(item["expression"]) in compact(ddl), item
    return rows, used, {path: sha256((ROOT / path).read_bytes()).hexdigest() for path in sources}


def summary_table(template, rows):
    """沿用正文三列表格样式，建立可跨页重复表头的约束汇总。"""
    table = deepcopy(template)
    widths = (1700, 5000, 2372)
    for row in list(table.findall("w:tr", NS)):
        table.remove(row)
    for column, width in zip(table.findall("w:tblGrid/w:gridCol", NS), widths):
        column.set(W + "w", str(width))
    templates = template.findall("w:tr", NS)
    for row_index, values in enumerate([("约束对象", "生成列或唯一键", "业务作用")] + rows):
        row = deepcopy(templates[0 if row_index == 0 else 1])
        row_properties = row.find("w:trPr", NS)
        assert row_properties is not None
        for node in row_properties.findall("w:trHeight", NS):
            row_properties.remove(node)
        for cell_index, (cell, value) in enumerate(zip(row.findall("w:tc", NS), values)):
            cell.find("w:tcPr/w:tcW", NS).set(W + "w", str(widths[cell_index]))
            paragraph = cell.find("w:p", NS)
            for other in list(cell):
                if other.tag not in (W + "tcPr", W + "p") or (other.tag == W + "p" and other is not paragraph):
                    cell.remove(other)
            for key in list(paragraph.attrib):
                paragraph.attrib.pop(key)
            replace_text(paragraph, value.split("\n")[0])
            properties = paragraph.find("w:pPr", NS)
            for node in properties.findall("w:keepNext", NS):
                properties.remove(node)
            alignment = properties.find("w:jc", NS)
            alignment.set(W + "val", "center" if row_index == 0 else "left")
            run = paragraph.find("w:r", NS)
            run_properties = run.find("w:rPr", NS)
            if run_properties is None:
                run_properties = ET.Element(W + "rPr")
                run.insert(0, run_properties)
            fonts = run_properties.find("w:rFonts", NS)
            if fonts is None:
                fonts = ET.SubElement(run_properties, W + "rFonts")
            for key, name in (("ascii", "Times New Roman"), ("hAnsi", "Times New Roman"), ("eastAsia", "宋体")):
                fonts.set(W + key, name)
            for line in value.split("\n")[1:]:
                ET.SubElement(run, W + "br")
                ET.SubElement(run, W + "t").text = line
        table.append(row)
    return table


def rebuild_toc(body, original_toc):
    """按全部实际一级至三级标题重建缓存，保留自动目录外层域。"""
    originals = {}
    templates = {}
    for paragraph in original_toc:
        # 同层级采用末条缓存模板，避免把首条缓存携带的TOC外层begin重复复制。
        templates[paragraph_style(paragraph)] = paragraph
        instructions = paragraph.xpath(".//w:instrText/text()", namespaces=NS)
        anchor = next((re.search(r"PAGEREF\s+(\S+)", value).group(1)
                       for value in instructions if "PAGEREF" in value), None)
        if anchor:
            originals[anchor] = paragraph
    first_index = body.index(original_toc[0])
    for paragraph in original_toc:
        body.remove(paragraph)
    maximum = max(int(value) for value in body.xpath(".//w:bookmarkStart/@w:id", namespaces=NS))
    entries = []
    for paragraph in list(body):
        style = paragraph_style(paragraph)
        if style not in ("3", "4", "5"):
            continue
        bookmarks = paragraph.xpath("./w:bookmarkStart/@w:name", namespaces=NS)
        anchor = next((name for name in bookmarks if name.startswith("_Toc")), None)
        if anchor in originals:
            entry = deepcopy(originals[anchor])
        else:
            maximum += 1
            anchor = "_TocAdvisor20261016_" + str(maximum)
            start = ET.Element(W + "bookmarkStart", {W + "id": str(maximum), W + "name": anchor})
            end = ET.Element(W + "bookmarkEnd", {W + "id": str(maximum)})
            paragraph.insert(1 if paragraph.find("w:pPr", NS) is not None else 0, start)
            paragraph.append(end)
            entry = deepcopy(templates[{"3": "27", "4": "30", "5": "24"}[style]])
            for key in list(entry.attrib):
                entry.attrib.pop(key)
            for instruction in entry.xpath(".//w:instrText", namespaces=NS):
                if "HYPERLINK" in (instruction.text or ""):
                    instruction.text = " HYPERLINK \\l " + anchor + " "
                elif "PAGEREF" in (instruction.text or ""):
                    instruction.text = " PAGEREF " + anchor + " \\h "
                else:
                    raise AssertionError("目录模板包含未预期域")
            visible = entry.xpath(".//w:t", namespaces=NS)
            assert len(visible) == 2
            visible[0].text, visible[1].text = text_of(paragraph), "54"
        entries.append({"heading": text_of(paragraph), "bookmark": anchor, "level": int(style) - 2})
        body.insert(first_index + len(entries) - 1, entry)
    return entries


def revise(input_path, output_path):
    """只改正文XML，生成内容候选与结构核对报告。"""
    assert input_path.resolve() != output_path.resolve(), "请写入独立候选文件"
    original_hash = sha256(input_path.read_bytes()).hexdigest()
    assert original_hash == BASELINE, "输入不是已核对的10月14日论文基线"
    with ZipFile(input_path) as package:
        parts = {info.filename: package.read(info.filename) for info in package.infolist()}
        infos = package.infolist()
    document = ET.fromstring(parts["word/document.xml"])
    body = document.find("w:body", NS)
    original = list(body)
    fields = structure_tables(body)
    original_toc = [paragraph for paragraph in original if paragraph_style(paragraph) in ("27", "30", "24")]
    math_before = [canonical(node) for node in document.xpath(".//m:oMath", namespaces=NS)]
    references_before = [canonical(node) for node in body if re.match(r"^\[\d+\]", text_of(node))]
    core_before = {name: canonical(table) for number, (name, _, table) in fields.items() if number.startswith("4-")}
    assert set(core_before) == set(CORE) and len(references_before) == 21
    appendix_start = next(index for index, paragraph in enumerate(original)
                          if text_of(paragraph) == "附录A 项目证据与复现入口")
    heading_template = original[appendix_start]
    subheading_template = original[appendix_start + 2]
    paragraph_template = original[appendix_start + 1]
    caption_template = fields["B-15"][1]
    three_column_template = next(node for node in original if node.tag == W + "tbl"
                                 and text_of(node).startswith("数据表记录内容关键设计作用"))
    dictionary_path = ROOT / "database/design/data-dictionary.json"
    dictionary = json.loads(dictionary_path.read_text(encoding="utf-8"))
    constraint_rows, constraints, source_hashes = constraint_data(dictionary)
    replacements = {
        "全文共七章，依次介绍研究问题与目标、相关理论与技术、需求与可行性、系统设计、系统实现、测试结果与证据边界、总结与展望。附录补充实验记录和完整数据表结构。":
            "全文共七章，依次介绍研究问题与目标、相关理论与技术、需求与可行性、系统设计、系统实现、测试结果与证据边界、总结与展望。附录补充关键数据表与特殊约束。",
        "三个逻辑数据库由对应服务维护。概念设计以企业、用户、计划、任务和会话为主线：矩形、椭圆和菱形分别表示实体、属性及联系，下划线属性为实体标识，1、N、M表示基数，单线和双线分别表示部分参与和全参与。图4-3为总览，图4-4至图4-6展开各域实体和属性。":
            "三个逻辑数据库由对应服务维护。概念设计以企业、用户、计划、任务和会话为主线：矩形、椭圆和菱形分别表示实体、属性及联系，下划线属性为实体标识；实体与联系、实体与属性均使用单实线连接。1表示一个，N和M表示多个，通过两端标注表达1:1、1:N或M:N联系。必需归属、可选联系及最低数量由各域文字说明。图4-3为总览，图4-4至图4-6展开各域实体和属性。",
        "这些图展示业务概念及主要联系，不将每个上传会话、审计字段或关联表都作为独立概念实体展开。用户—角色、角色—权限联系分别映射到sys_user_role和sys_role_permission；课程、课件和私有图片上传过程由相应上传会话表管理。完整物理字段及索引见4.3.4节和附录B。所有迁移管理表未声明物理外键，图中连线表示逻辑关联，跨服务归属与引用有效性由接口校验。":
            "这些图展示业务概念及主要联系，不将每个上传会话、审计字段或关联表都作为独立概念实体展开。用户—角色、角色—权限联系分别映射到sys_user_role和sys_role_permission；课程、课件和私有图片上传过程由相应上传会话表管理。八张核心表见4.3.4节，四张补充关键表和特殊约束见附录A。图中连线表示业务逻辑关联。",
        "数据库物理设计采用与各服务一致的数据类型和约束。表4-2至表4-9给出主线中的八张核心表；其他资源、关联和过程表与核心表一起在附录B完整列示。主键用于唯一识别记录，复合唯一索引约束业务键，普通索引支持按企业、任务、会话和状态查询。字段默认值“无”表示未设置显式默认值的非空列，“隐式NULL”表示未显式声明默认值的可空普通列，生成列另列计算表达式。八张核心表均采用InnoDB引擎和utf8mb4字符集。":
            "数据库物理设计采用与各服务一致的数据类型和约束。表4-2至表4-9给出主线中的八张核心表；附录A补充发布快照、学习事件与可靠消息四张关键表，并汇总特殊唯一性约束，不再重复正文表结构。主键用于唯一识别记录，复合唯一索引约束业务键，普通索引支持按企业、任务、会话和状态查询。字段默认值“无”表示未设置显式默认值的非空列，“隐式NULL”表示未显式声明默认值的可空普通列，生成列另列计算表达式。八张核心表均采用InnoDB引擎和utf8mb4字符集。",
        "实验统计按版本及执行记录分别引用：后端采用2026年10月9日全量复验，前端与既有接口、心跳实验采用原归档结果，版本定位见附录A。实现说明依据固定源码快照核查，各项结论仅适用于记录覆盖的代码、输入和环境；不同批次不累加测试样本。":
            "实验统计按版本及执行记录分别引用：实现分析与2026年10月9日后端全量复验采用13d6463，其业务源码与96a8d84快照一致；前端采用7ca2f94对应的9月9日归档，既有接口与心跳实验沿用原记录。各项结论仅适用于相应代码、输入和环境，不同批次不累加测试样本。后端结果依据10月9日验收报告及逐项记录，前端与历史实验依据9月9日归档，相关原始材料保存在项目测试报告与证据索引中。",
    }
    changes = []
    for before, after in replacements.items():
        targets = [paragraph for paragraph in body if text_of(paragraph) == before]
        assert len(targets) == 1, before
        replace_text(targets[0], after)
        changes.append({"before": before, "after": after})
    # 将抽验和照片可选关系继续留在各域正文，不用线型承担最低参与数量。
    management = next(p for p in body if text_of(p).startswith("管理域如图4-4所示。"))
    management_text = (text_of(management)
                       .replace("一个企业可有多名用户和多辆车辆，", "每个组织用户必须归属一个企业，每辆车辆也必须归属一个企业；一个企业可有多名用户和多辆车辆，")
                       .replace("组织与行政区域的关系用于保存辖区基础信息，部门层级与主部门归属通过组织及组织用户数据维护。", "组织与行政区域的关系用于保存辖区基础信息，跨企业监管查询尚未实现；组织父节点表达部门层级，组织用户表保存用户主部门归属。"))
    changes.append({"before": text_of(management), "after": management_text})
    replace_text(management, management_text)
    training = next(p for p in body if text_of(p).startswith("培训与考试域如图4-5所示。"))
    training_text = (text_of(training)
                     .replace("计划课程至少包含一份课件快照，", "每份课件快照归属一门计划课程，计划课程至少包含一份课件快照，")
                     .replace("每条答案必须对应一条考试记录及一道试卷题目。", "每条答案必须对应一条考试记录及一道试卷题目，并依据该题的快照判分。"))
    changes.append({"before": text_of(training), "after": training_text})
    replace_text(training, training_text)
    learning = next(p for p in body if text_of(p).startswith("学习监管域如图4-6所示。"))
    learning_text = (text_of(learning)
                     .replace("课程进度及至少一条课件进度同步初始化并保存累计事实。", "每条课程进度至少关联一条课件进度，每条课件进度只归属一条课程进度，两者同步初始化并保存累计事实。")
                     .replace("发件记录保存待投递事件，", "发件记录与对应业务状态在同一本地事务中保存，再按事件标识投递；"))
    changes.append({"before": text_of(learning), "after": learning_text})
    replace_text(learning, learning_text)
    # 图内移出的说明并入对应正文，沿用既有机制与实验边界，不新增结果。
    architecture = next(p for p in body if text_of(p).startswith("管理服务维护组织、用户、角色、权限"))
    architecture_text = text_of(architecture).replace("对象存储保存私有媒体。", "私有OSS保存媒体，Redis缓存认证与授权信息，Nacos提供服务注册和配置管理。")
    changes.append({"before": text_of(architecture), "after": architecture_text})
    replace_text(architecture, architecture_text)
    workflow = next(p for p in body if text_of(p).startswith("学习消息处理按身份与会话绑定、幂等查询"))
    workflow_text = (text_of(workflow)
                     .replace("状态检查决定当前是否具备学习条件。", "状态检查决定当前是否具备学习条件；身份、状态或序号不符合要求时拒绝处理。")
                     + "两次事件的服务端间隔超过“上报间隔＋学时容差”时暂停结算，不补记断线期间的学时。")
    changes.append({"before": text_of(workflow), "after": workflow_text})
    replace_text(workflow, workflow_text)
    # 删除旧附录时只移除正文节点，保留最终分节属性与全部前置正文。
    for node in original[appendix_start:]:
        if node.tag != W + "sectPr":
            body.remove(node)
    final_section = body.find("w:sectPr", NS)
    assert final_section is not None
    insert_at = body.index(final_section)
    appendix = [new_paragraph(heading_template, "附录A 关键补充表与特殊约束"),
                new_paragraph(paragraph_template, "本附录补充发布快照、学习事件和可靠消息四张关键表，字段定义与正文采用同一结构来源，特殊唯一性约束集中列于A.5。前两表由培训服务维护，后两表由学习服务维护。四表均采用InnoDB引擎与utf8mb4字符集；完整31张表的数据字典作为工程配套材料保存。结构说明不表示任一运行环境已执行全部迁移。")]
    descriptions = [
        ("计划课程规则快照", "发布时固定课程名称、规定学时与学习规则。"),
        ("计划课件清单快照", "发布时固定课件标题、时长、顺序和媒体对象引用。"),
        ("关键学习事件", "保存已受理事件及其状态变化、学时计入量与幂等响应。"),
        ("可靠消息发件记录", "在本地事务中保留待发送事件与重试信息。"),
    ]
    for index, (name, (title, explanation)) in enumerate(zip(SUPPLEMENTAL, descriptions), 1):
        source = next(table for number, (table_name, _, table) in fields.items()
                      if number.startswith("B-") and table_name == name)
        appendix.extend([new_paragraph(subheading_template, "A." + str(index) + " " + title),
                         new_paragraph(paragraph_template, explanation),
                         new_paragraph(caption_template, "表A-" + str(index) + " " + name + "表结构"),
                         deepcopy(source)])
    appendix.extend([new_paragraph(subheading_template, "A.5 特殊约束汇总"),
                     new_paragraph(paragraph_template, "表A-5列出与业务规则直接相关的生成列和唯一键。生成列均采用STORED方式保存；MySQL唯一键允许多条NULL，因此结束的会话、已处理的抽验任务及普通账号不会占用对应活动唯一值。业务表未声明物理外键，跨服务引用及归属由服务接口校验。普通查询索引不在本附录逐项列出。"),
                     new_paragraph(caption_template, "表A-5 关键特殊约束"),
                     summary_table(three_column_template, constraint_rows)])
    for node in appendix:
        body.insert(insert_at, node)
        insert_at += 1
    toc = rebuild_toc(body, original_toc)
    assert len(toc) == 67, len(toc)
    assert math_before == [canonical(node) for node in document.xpath(".//m:oMath", namespaces=NS)]
    assert references_before == [canonical(node) for node in body if re.match(r"^\[\d+\]", text_of(node))]
    core_after = {name: canonical(table) for number, (name, _, table) in structure_tables(body).items() if number.startswith("4-")}
    assert core_before == core_after
    assert len(document.xpath('.//w:instrText[contains(text(), " TOC ")]', namespaces=NS)) == 1
    visible = text_of(document)
    assert not any(value in visible for value in ("附录B", "项目证据与复现入口", "部分参与", "全参与", "双线", "表B-"))
    parts["word/document.xml"] = ET.tostring(document, encoding="UTF-8", xml_declaration=True, standalone=True)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    with ZipFile(output_path, "w") as package:
        for info in infos:
            package.writestr(info, parts[info.filename])
    assert original_hash == sha256(input_path.read_bytes()).hexdigest()
    report = {"input": str(input_path.resolve()), "output": str(output_path.resolve()),
              "input_sha256": original_hash, "output_sha256": sha256(output_path.read_bytes()).hexdigest(),
              "modified_parts": ["word/document.xml"], "text_changes": changes,
              "appendix": {"removed": ["附录A 项目证据与复现入口", "附录B 数据表结构字典"],
                           "new": "附录A 关键补充表与特殊约束", "preserved_core_tables": CORE,
                           "supplemental_tables": SUPPLEMENTAL, "ordinary_appendix_index_tables": 0,
                           "constraint_rows": len(constraint_rows)},
              "checks": {"native_math_unchanged": len(math_before), "references_unchanged": len(references_before),
                         "core_field_tables_unchanged": len(core_before), "toc_entries": len(toc),
                         "toc_field_preserved": True, "all_other_package_parts_unchanged": True,
                         "original_docx_unchanged": True},
              "toc": toc, "constraints": constraints, "constraint_source_sha256": source_hashes,
              "dictionary_sha256": sha256(dictionary_path.read_bytes()).hexdigest(),
              "visual_validation": "由合并图像后的最终候选统一渲染核对，本内容候选尚未作版式交付"}
    report_path = output_path.with_name("content-report.json")
    report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return report


def main():
    """接收独立输入输出路径，禁止覆盖正在使用的原论文。"""
    parser = ArgumentParser(description=__doc__)
    parser.add_argument("--input", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    report = revise(args.input, args.output)
    print(json.dumps({"output": report["output"], "sha256": report["output_sha256"],
                      "checks": report["checks"]}, ensure_ascii=False))


if __name__ == "__main__":
    main()
