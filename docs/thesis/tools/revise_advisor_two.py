"""补齐核心业务链和三幅图，只改指定Word部件并保留前次修订。"""

import argparse
from copy import deepcopy
from hashlib import sha256
import json
from pathlib import Path
import re
from zipfile import ZipFile

from lxml import etree as ET
from PIL import Image


ROOT = Path(__file__).resolve().parents[3]
NS = {
    "w": "http://schemas.openxmlformats.org/wordprocessingml/2006/main",
    "a": "http://schemas.openxmlformats.org/drawingml/2006/main",
    "wp": "http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing",
    "pic": "http://schemas.openxmlformats.org/drawingml/2006/picture",
    "r": "http://schemas.openxmlformats.org/officeDocument/2006/relationships",
    "m": "http://schemas.openxmlformats.org/officeDocument/2006/math",
}
W = "{" + NS["w"] + "}"
REL = "http://schemas.openxmlformats.org/package/2006/relationships"
BASELINE = "e560cc4badcfda99cbc7d1eacaab61f47f18be331c77ac2962a1126c16cc4b91"
FIGURE_MAP = {1: 2, 2: 3, 3: 4, 4: 5, 5: 7, 6: 9, 7: 10, 8: 11}
NEW_FIGURES = {
    "core-business-flow.png": (1, "核心业务链实现流程", "课程启用与计划发布、账号登录、视频学习、断线恢复、人脸抽验、考试及档案形成"),
    "video-recovery.png": (6, "视频播放与断线恢复", "当前Vue播放器实际解码视频、断线暂停及重连同步后等待手动继续的三个阶段"),
    "face-result.png": (8, "人脸抽验结果与继续学习", "当前Vue抽验通过面板及确认后保持暂停、由学员手动继续的两个阶段"),
}


def visible(element):
    """读取可见文本，不将域指令算入正文。"""
    return "".join(element.xpath(".//w:t/text()", namespaces=NS))


def replace_text(paragraph, value):
    """保留段落格式与书签，仅替换普通文字内容。"""
    assert not paragraph.xpath(".//m:oMath|.//w:drawing|.//w:instrText", namespaces=NS)
    properties = paragraph.find("w:r/w:rPr", NS)
    properties = deepcopy(properties) if properties is not None else None
    for child in list(paragraph):
        if child.tag not in (W + "pPr", W + "bookmarkStart", W + "bookmarkEnd"):
            paragraph.remove(child)
    run = ET.SubElement(paragraph, W + "r")
    if properties is not None:
        run.append(properties)
    ET.SubElement(run, W + "t").text = value


def clone_paragraph(template, value=None):
    """复用原文版式，新段落移除原有书签和段落标识。"""
    node = deepcopy(template)
    node.attrib.clear()
    for bookmark in node.xpath(".//w:bookmarkStart|.//w:bookmarkEnd", namespaces=NS):
        bookmark.getparent().remove(bookmark)
    if value is not None:
        replace_text(node, value)
    return node


def append_after(anchor, elements):
    """按给定顺序插入段落，避免连续addnext导致顺序倒置。"""
    for element in elements:
        anchor.addnext(element)
        anchor = element


def revise(source, target, assets):
    """以固定反馈一基线增补正文、顺排图号并嵌入三幅新图。"""
    original = source.read_bytes()
    assert sha256(original).hexdigest() == BASELINE, "输入不是已登记的反馈一版本"
    assert source.resolve() != target.resolve(), "修订版不得覆盖输入"
    with ZipFile(source) as package:
        parts = {name: package.read(name) for name in package.namelist()}
    document = ET.fromstring(parts["word/document.xml"])
    relations = ET.fromstring(parts["word/_rels/document.xml.rels"])
    body = document.find("w:body", NS)
    paragraphs = body.findall("w:p", NS)
    styles = ET.fromstring(parts["word/styles.xml"])
    toc_styles = {s.get(W + "styleId") for s in styles.findall("w:style", NS)
                  if s.find("w:name", NS) is not None
                  and s.find("w:name", NS).get(W + "val", "").lower().startswith("toc ")}
    # 先按原文字定位，再统一替换图号；每个定位必须唯一。
    original_paragraphs = {i: p for i, p in enumerate(paragraphs)}
    template = original_paragraphs[325]
    image_template = original_paragraphs[313]
    caption_template = original_paragraphs[314]
    changes = []

    def find(prefix):
        """按原文前缀唯一定位，拒绝静默修改错误段落。"""
        matches = [p for p in paragraphs if visible(p).startswith(prefix)
                   and (p.find("w:pPr/w:pStyle", NS) is None
                        or p.find("w:pPr/w:pStyle", NS).get(W + "val") not in toc_styles)]
        assert len(matches) == 1, (prefix, len(matches))
        return matches[0]

    def update(prefix, value):
        """替换一个已确认段落并记录原文及新文。"""
        paragraph = find(prefix)
        changes.append({"before": visible(paragraph), "after": value})
        replace_text(paragraph, value)
        return paragraph

    # 对原有图题、正文引述及替代文字作一次映射，避免数字连锁替换。
    def renumber(value):
        """仅顺排第五章原有八图，不影响其他章节编号。"""
        return re.sub(r"图5-([1-8])(?!\d)", lambda m: "图5-" + str(FIGURE_MAP[int(m[1])]), value)

    for text in document.xpath("//w:t", namespaces=NS):
        text.text = renumber(text.text or "")
    for props in document.xpath("//wp:docPr|//pic:cNvPr", namespaces=NS):
        for key in ("name", "descr"):
            if props.get(key):
                props.set(key, renumber(props.get(key)))

    max_relationship = max(int(r.get("Id")[3:]) for r in relations)
    max_drawing = max(int(n.get("id")) for n in document.xpath("//wp:docPr", namespaces=NS))
    figures = []
    new_parts = {}

    def image_block(filename):
        """复用原图片段落，添加唯一关系并按15.5厘米等比嵌入。"""
        nonlocal max_relationship, max_drawing
        number, title, description = NEW_FIGURES[filename]
        asset = assets / filename
        data = asset.read_bytes()
        with Image.open(asset) as picture:
            width, height = picture.size
        height_cm = 15.5 * height / width
        assert height_cm <= 20.5, (filename, height_cm)
        max_relationship += 1
        max_drawing += 1
        relation_id = "rId" + str(max_relationship)
        media = "media/advisor-two-" + filename
        ET.SubElement(relations, "{" + REL + "}Relationship", Id=relation_id,
                      Type=NS["r"] + "/image", Target=media)
        new_parts["word/" + media] = data
        paragraph = clone_paragraph(image_template)
        for extent in paragraph.xpath(".//wp:extent|.//a:xfrm/a:ext", namespaces=NS):
            extent.set("cx", "5580000")
            extent.set("cy", str(round(height_cm * 360000)))
        paragraph.find(".//a:blip", NS).set("{" + NS["r"] + "}embed", relation_id)
        for props in paragraph.xpath(".//wp:docPr|.//pic:cNvPr", namespaces=NS):
            props.set("id", str(max_drawing))
            props.set("name", filename)
            props.set("descr", description)
        caption = clone_paragraph(caption_template, "图5-{} {}".format(number, title))
        figures.append({"number": "5-" + str(number), "asset": asset.relative_to(ROOT).as_posix(),
                        "media": "word/" + media, "pixels": [width, height],
                        "width_cm": 15.5, "height_cm": round(height_cm, 4), "sha256": sha256(data).hexdigest()})
        return [paragraph, caption]

    overview = (
        "系统以学员培训任务贯穿课程安排、学习过程和考核结果，核心业务链如图5-1所示。"
        "管理员上传视频并启用课程，将课程与参训人员加入计划，发布时固定课程、课件和考核规则。"
        "学员通过账号登录后进入本人任务，完成签到和视频学习；断线时先暂停，重连同步后由本人继续；"
        "启用人脸核验时，按要求拍照完成身份比对。学习完成后，需要考核的任务才允许进入考试，"
        "服务端结合学时与成绩判定是否结业，最后聚合形成可查询的培训档案。"
    )
    append_after(find("5 系统实现"), [clone_paragraph(template, overview)] + image_block("core-business-flow.png"))

    append_after(find("认证使用RSA签名的访问令牌"), [clone_paragraph(template,
        "学员在登录页提交账号与密码，认证服务检查账号和所属企业状态，登录成功后按权限进入学员工作台；"
        "需要首次改密的账号先完成密码修改。任务列表只显示分配给当前用户的培训，进入课程时继续核验企业归属、"
        "任务分配和计划有效期。登录解决访问身份问题，学习中的人脸核验另用于确认当次参与者，两者分别处理。")])

    update("计划发布将课程和课件规则复制为快照", 
        "课程启用前，服务检查课件是否就绪、上传是否完成，以及规定学时是否超过视频总时长。"
        "管理员再配置培训周期、参训人员、考试和人脸核验规则；启用人脸核验时，发布前检查参训人员是否已有登记照。"
        "计划发布重新保存课程与课件快照，并为每名学员建立任务，分别维护学习、考试和结业状态，基础资源后续修改不回写快照。"
        "自然日周期归一为起始日00:00:00和结束日23:59:59，播放授权要求当前时间严格早于结束时刻。"
        "学员目录与规定学时均取自本人任务的发布快照。培训计划发布快照界面见图5-4。")

    # 标题及自动目录中的可见标题同步，保留原书签和TOC域。
    for text in document.xpath("//w:t", namespaces=NS):
        text.text = (text.text or "").replace("5.3 有效学时与补学实现", "5.3 视频学习、断线恢复与补学实现")
    append_after(find("5.3 视频学习、断线恢复与补学实现"), [clone_paragraph(template,
        "学员完成签到后，页面读取发布快照中的已解锁课件，向服务端申请短期播放地址，由浏览器视频组件读取并解码MP4。"
        "播放器加载后先恢复该课件的服务端确认位置；用户点击播放时，页面先提交PLAY事件，收到确认再开始播放。"
        "播放期间串行上报PROGRESS，暂停、切换和签退也按会话序号提交。页面显示服务端确认的有效学时，"
        "视频已播放位置不能直接代替课程完成状态。")])
    recovery_text = (
        "实时连接中断时，页面立即停止播放器与进度上报，避免断线后继续推进本地学时。"
        "连接按1、2、5、10秒间隔退避重试，重连后先绑定原学习会话，再同步服务端状态、事件序号和确认位置。"
        "若服务端仍处于学习中，页面先确认安全暂停；过期的播放和进度操作不作为新学习量补交，"
        "待确认的暂停操作则沿用原请求标识处理。同步完成后播放器保持暂停，学员点击播放才重新计时。"
        "播放地址失效时也重新申请授权并按确认位置加载。视频播放、断线提示和恢复后的状态见图5-6。"
    )
    append_after(find("图5-5 视频学习与补学界面"),
                 [clone_paragraph(template, recovery_text)] + image_block("video-recovery.png"))

    update("源码核查表明，FaceCheckServiceImpl管理抽验任务", 
        "FaceCheckServiceImpl管理学习抽验、提交日志及签到签退核验，人脸适配器提供检测与一对一身份比对。"
        "启用核验的会话在签到、签退时先要求本人拍照；核验成功后，服务端保存与本次动作及下一事件序号绑定、"
        "60秒有效的凭据，正式事件受理后才将照片归入会话档案，凭据不能跨动作复用。"
        "照片使用私有对象引用保存，只有具备相应权限的查询才能取得短期预览地址。")
    append_after(find("FaceCheckServiceImpl管理学习抽验"), [clone_paragraph(template,
        "服务端按计划间隔规则生成累计有效学时阈值，达到阈值后触发学习抽验，创建带截止时间的抽验任务，"
        "将会话转为等待核验并通知页面；页面暂停视频和计时，弹出摄像头采集窗口。"
        "学员授权后可看到实时预览，点击拍照将当前视频帧转为JPEG，再提交照片与请求标识。"
        "这一过程采集的是摄像头单帧照片，不上传连续视频。拍照前后两个阶段见图5-7。")])
    result_text = (
        "人脸适配器使用YuNet检测登记照与提交照，均符合单人脸条件时，再使用SFace完成对齐、特征提取和身份比对，"
        "同时保存本次提交结果与照片引用。同一请求重复到达不重复消耗尝试次数。"
        "核验通过后，抽验任务记为通过，会话恢复为暂停，页面提示本人手动继续播放；"
        "未通过且尚有次数时可重新拍照，次数耗尽或超过截止时间则终止会话并记录原因。"
        "即使学员没有提交照片，超时任务也保留在监管记录中。核验通过及返回学习页的状态见图5-8。"
    )
    append_after(find("图5-7 学习人脸抽验界面"),
                 [clone_paragraph(template, result_text)] + image_block("face-result.png"))

    update("考试服务维护服务端截止时间", 
        "任务内全部课程完成后，学习服务通过结果事件更新培训侧任务；消费端按事件标识去重，"
        "页面在同步期间显示状态提示。考试服务以培训任务的学习完成状态作为新开或继续考试的前置条件，"
        "保留已交卷结果查询。服务端维护开始与截止时间，前端串行保存答案，最终交卷按已启用试卷的题目快照判分；"
        "超时由服务端处理，重复交卷读取已有结果。仅学习模式满足学习要求即可结业，学习加考试模式还须考试及格。"
        "首页“待考试”数量按考试状态和计划有效期统计，可否开考仍由服务端单独检查学习条件。在线考试作答界面见图5-9。")

    update("TrainingRecordController先读取授权任务", 
        "培训档案按学员任务聚合形成：培训服务读取发布快照、任务状态和考试成绩，TrainingRecordController"
        "在取得有权访问的任务后，列表批量查询学习服务的有效学时，详情按发布课程目录组合学习明细返回页面。"
        "完成判定与档案查询使用同一任务标识，使课程要求、学习结果和考试成绩能够相互对应。"
        "没有学习记录的课程按快照补零，计划结束后仍可只读查看，查询不创建学习会话或启动考试。"
        "学员查看本人档案，管理员按企业范围查询参训人员及过程明细。学员学习档案界面见图5-10。")

    # 补充本地验收的定性结果，不将其混入历史自动化统计。
    update("实现依赖Java 17、数据库与中间件", 
        "实现依赖Java 17、数据库与中间件、支持视频及摄像头的浏览器、课程媒体和人脸模型。"
        "对象存储管理私有媒体，人脸适配器负责图像比较。迁移、领域测试和本地联调见第6章；"
        "路线面向单实例演示，生产容量及多实例协调仍需分别评价。")
    update("实验统计按版本及执行记录分别引用", 
        "实验统计按版本及执行记录分别引用：实现分析与2026年10月9日后端全量复验采用13d6463，"
        "其业务源码与96a8d84快照一致；前端采用7ca2f94对应的9月9日归档，既有接口与心跳实验沿用原记录。"
        "本地功能验收另核对核心业务链的连续操作与结果。不同批次不累加测试样本，定性联调结果不并入自动化项数。"
        "后端结果依据10月9日验收报告及逐项记录，前端与历史实验依据9月9日归档，原始材料入口保存在项目测试报告与证据索引中。")
    update("本次后端全量复验覆盖确定输入下的规则", 
        "10月9日后端全量复验覆盖确定输入下的规则、数据库行为、消息路由、真实私有OSS对象操作，"
        "以及授权照片的同人不同照、不同人、多人和无人脸等模型场景。OSS采用196608字节合成内容验证存储协议；"
        "模型使用默认相似度阈值0.363，结果只支持指定输入下的判断，不代表识别准确率。"
        "本地功能验收另覆盖浏览器视频播放、断线恢复、摄像头拍照抽验、考试与档案查询，"
        "用于检查业务链能否连续完成；真实播放学时差额和独立学员并发容量不由该定性结果推导。"
        "UI契约仍使用接口夹具，合成计时仍采用确定输入。")
    update("消息实验采用真实RabbitMQ与生产发送器", 
        "消息实验采用真实RabbitMQ与生产发送器，数据库Mapper及事务管理器为测试替身，"
        "结果限定于发布确认及路由恢复。跨服务持久化重启、多实例高可用、HTTPS/WSS部署、"
        "跨浏览器与跨终端兼容性及生产环境长期表现需按各自条件另行评价。")
    append_after(find("上述结果形成从需求到验证的对应关系"), [clone_paragraph(template,
        "本地功能验收按图5-1的主线进行连续操作：管理员启用课程并发布计划，学员登录本人任务，"
        "播放视频并在断线后同步恢复，完成摄像头拍照抽验，随后参加考试并查询培训档案。"
        "验收确认了视频可播放、断线与核验期间暂停、恢复后由本人继续，以及学习和考试结果能够在档案中衔接。"
        "该结果支持核心流程的功能连通性，不用于估计人脸识别准确率、真实学习率或并发承载能力。")])
    append_after(find("10月9日后端全量266项测试执行通过"), [clone_paragraph(template,
        "本地功能验收进一步走通课程启用与计划发布、学员登录、视频播放、断线恢复、摄像头拍照抽验、考试和档案查询。"
        "各环节通过同一培训任务衔接：学时与身份核验约束学习过程，学习状态决定考试准入，"
        "成绩参与结业判定，档案保留过程与结果的查询入口。")])
    update("本研究的结论适用于规则与单实例组件实验", 
        "本研究的结论来自规则测试、单实例组件实验及本地功能验收，具体范围见6.1节。"
        "进一步研究可采用获得授权的真实使用数据，分析学时差额、状态反馈和操作体验，"
        "并比较不同网络与终端条件下的表现。")

    final_text = "".join(document.xpath("//w:t/text()", namespaces=NS))
    assert "完整培训闭环、真实摄像头采集" not in final_text
    assert not re.search(r"(?:真实|媒体|闭环).{0,18}(?:尚未验收|仍待验收|尚未执行)", final_text)
    chapter_figures = [visible(p) for p in body.findall("w:p", NS)
                       if re.match(r"^图5-\d+ ", visible(p))]
    assert [int(re.match(r"图5-(\d+)", s)[1]) for s in chapter_figures] == list(range(1, 12))
    before = ET.fromstring(parts["word/document.xml"])
    assert len(document.xpath("//w:drawing", namespaces=NS)) == len(before.xpath("//w:drawing", namespaces=NS)) + 3
    for query in ("//m:oMath", "//w:tbl", "//w:sectPr"):
        assert [ET.tostring(n, method="c14n") for n in before.xpath(query, namespaces=NS)] == [
            ET.tostring(n, method="c14n") for n in document.xpath(query, namespaces=NS)], query
    new_parts["word/document.xml"] = ET.tostring(document, encoding="UTF-8", xml_declaration=True, standalone=True)
    new_parts["word/_rels/document.xml.rels"] = ET.tostring(relations, encoding="UTF-8", xml_declaration=True, standalone=True)
    target.parent.mkdir(parents=True, exist_ok=True)
    with ZipFile(source) as old, ZipFile(target, "w") as out:
        for info in old.infolist():
            out.writestr(info, new_parts.pop(info.filename, parts[info.filename]))
        for name, data in new_parts.items():
            out.writestr(name, data)
    return {"input": source.relative_to(ROOT).as_posix(), "input_sha256": BASELINE,
            "output": target.relative_to(ROOT).as_posix(), "output_sha256": sha256(target.read_bytes()).hexdigest(),
            "replaced_paragraphs": changes, "new_figures": figures, "chapter_five_captions": chapter_figures,
            "total_body_figures": 21, "user_acceptance_confirmation_date": "2026-10-16",
            "business_tests_executed_this_task": False,
            "preserved": ["原有全部媒体", "全部既有表格及其字段内容", "原生公式", "分节及页眉页脚", "参考文献"]}


def main():
    """从反馈一另存反馈二，并输出本轮修改范围报告。"""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", type=Path)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--assets", type=Path, default=ROOT / "docs/thesis/figures/advisor-two-20261016")
    parser.add_argument("--report", type=Path, default=ROOT / "tmp/thesis-advisor-two-20261016/content-report.json")
    args = parser.parse_args()
    source = args.input or next((ROOT / "docs/thesis").glob("*导师反馈一修订版.docx"))
    target = args.output or source.with_name(source.name.replace("导师反馈一修订版", "导师反馈二修订版"))
    result = revise(source, target, args.assets)
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({k: v for k, v in result.items() if k not in ("replaced_paragraphs", "new_figures")}, ensure_ascii=False))


if __name__ == "__main__":
    main()
