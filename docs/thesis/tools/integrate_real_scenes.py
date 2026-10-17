"""在当前未提交论文上定向替换真实截图，保留其他包部件。"""

import argparse
from hashlib import sha256
import json
from pathlib import Path
from zipfile import ZipFile

from lxml import etree as ET
from PIL import Image

from revise_advisor_two import NS, W, visible, replace_text, clone_paragraph


BASELINE = "6fc2cd08e8caa83c5dabe9bd60971635c723e28af906f7533a284b4bc490e1bd"


def integrate(source, assets, target, report):
    """按原段落和图题唯一定位，修改截图及直接关联的说明。"""
    original = source.read_bytes()
    assert sha256(original).hexdigest() == BASELINE, "输入应为本轮已确认的未提交主文件"
    assert source.resolve() != target.resolve()
    with ZipFile(source) as package:
        parts = {name: package.read(name) for name in package.namelist()}
        infos = package.infolist()
    root = ET.fromstring(parts["word/document.xml"])
    relations = ET.fromstring(parts["word/_rels/document.xml.rels"])
    body = root.find("w:body", NS)
    original_paragraphs = body.findall("w:p", NS)
    changed_text, figures = [], []
    relation_map = {r.get("Id"): r.get("Target") for r in relations}

    def find(prefix):
        """唯一匹配正文前缀，防止图号与目录误命中。"""
        matches = [p for p in original_paragraphs if visible(p).startswith(prefix)]
        assert len(matches) == 1, (prefix, len(matches))
        return matches[0]

    def update(prefix, text):
        """替换一段关联说明，沿用原有文字与段落格式。"""
        paragraph = find(prefix)
        changed_text.append({"before": visible(paragraph), "after": text})
        replace_text(paragraph, text)
        return paragraph

    def block(number):
        """由唯一图题取得图片段落与图题。"""
        caption = find("图5-{} ".format(number))
        picture = caption.getprevious()
        assert picture.find(".//w:drawing", NS) is not None
        return picture, caption

    blocks = {number: block(number) for number in range(1, 11)}
    template = find("有效学时由LearningTimeCalculator计算")

    def set_image(picture, caption, filename, number, title, description, width_cm=15.5):
        """替换已脱敏图片并按原比例嵌入，图题保持同页。"""
        data = (assets / filename).read_bytes()
        with Image.open(assets / filename) as image:
            width, height = image.size
        height_cm = width_cm * height / width
        assert height_cm <= 18.5, (filename, height_cm)
        drawing = picture.find(".//w:drawing", NS)
        blip = drawing.find(".//a:blip", NS)
        media = "word/" + relation_map[blip.get("{" + NS["r"] + "}embed")]
        parts[media] = data
        for extent in drawing.xpath(".//wp:extent|.//a:xfrm/a:ext", namespaces=NS):
            extent.set("cx", str(round(width_cm * 360000)))
            extent.set("cy", str(round(height_cm * 360000)))
        for props in drawing.xpath(".//wp:docPr|.//pic:cNvPr", namespaces=NS):
            props.set("name", filename)
            props.set("descr", description)
        properties = picture.find("w:pPr", NS)
        if properties is None:
            properties = ET.Element(W + "pPr")
            picture.insert(0, properties)
        keep = properties.find("w:keepNext", NS)
        if keep is None:
            keep = ET.SubElement(properties, W + "keepNext")
        keep.set(W + "val", "1")
        replace_text(caption, "图5-{} {}".format(number, title))
        figures.append({"number": "5-" + str(number), "asset": filename, "media": media,
                        "width_cm": width_cm, "height_cm": round(height_cm, 4),
                        "pixels": [width, height], "sha256": sha256(data).hexdigest()})

    # 两幅未取得真实对应素材的界面继续限定为示例，不改写为实测截图。
    update("图5-1 ", "图5-1 人员管理界面示例")
    update("图5-8 ", "图5-8 在线考试作答界面示例")
    set_image(*blocks[2], "course.png", 2, "课程规则与视频课件管理界面",
              "用户真实测试截图，课程规则与课件表裁剪拼接；未展示第三方封面")
    set_image(*blocks[3], "plan.png", 3, "培训计划规则与发布快照界面",
              "用户真实测试截图，计划基础规则、课程课件及参训学员发布快照")
    update("浏览器取得授权后直传私有OSS", visible(find("浏览器取得授权后直传私有OSS")).replace(
        "课程资源管理界面见图5-2。", "课程规则与课件界面见图5-2。"))

    # 先展示签到采集，再展示视频页面，避免用抽验图代替签到功能。
    sign_picture, sign_caption = blocks[5]
    for node in (sign_picture, sign_caption):
        body.remove(node)
    video_intro = find("学员完成签到后，页面读取发布快照")
    sign_intro = clone_paragraph(template,
        "启用人脸核验时，学员点击学习签到后授权摄像头，在预览中确认拍摄范围并点击拍照，"
        "页面提交身份核验，通过后完成签到。采集界面见图5-4。")
    video_intro.addprevious(sign_intro)
    video_intro.addprevious(sign_picture)
    video_intro.addprevious(sign_caption)
    set_image(sign_picture, sign_caption, "sign-in.png", 4, "签到人脸验证采集界面",
              "用户真实摄像头预览截图，沿用原图已有的人脸马赛克，不追加遮挡；画面尚未展示核验结果",
              width_cm=12.5)

    video_picture, video_caption = blocks[4]
    for node in (video_picture, video_caption):
        body.remove(node)
    video_intro.addnext(video_caption)
    video_intro.addnext(video_picture)
    update("学员完成签到后，页面读取发布快照", visible(video_intro) +
        "视频学习界面见图5-5，页面分别显示有效学时、课件状态、确认位置和播放控件，底部提示当前处于暂停状态。")
    set_image(video_picture, video_caption, "video.png", 5, "视频学习与播放控制界面",
              "用户真实视频学习页面，直接采用已对版权部分打马赛克的原图；保留完整画面、播放控件与当前暂停提示")
    update("系统为此提供从头补学入口", visible(find("系统为此提供从头补学入口")).replace(
        "视频学习与补学界面见图5-4。", ""))
    update("实时连接中断时，页面立即停止播放器", visible(find("实时连接中断时，页面立即停止播放器")).replace(
        "播放地址失效时也重新申请授权并按确认位置加载。视频播放、断线提示和恢复后的状态见图5-5。",
        "播放地址失效时也重新申请授权并按确认位置加载。"))

    update("服务端按计划间隔规则生成累计有效学时阈值", visible(find("服务端按计划间隔规则生成累计有效学时阈值")).replace(
        "拍照前后两个阶段见图5-6。", "摄像头开启前及开启后的预览界面见图5-6；画面保留抽验倒计时、剩余次数及暂停提示。"))
    set_image(*blocks[6], "face-check.png", 6, "学习人脸抽验与摄像头预览界面",
              "用户真实抽验弹窗，摄像头开启前和开启后的预览组图；沿用原图已有的人脸马赛克，不表示已提交或核验通过")
    update("人脸适配器使用YuNet检测登记照与提交照", visible(find("人脸适配器使用YuNet检测登记照与提交照")).replace(
        "核验通过及返回学习页的状态见图5-7。", ""))
    paper_intro = clone_paragraph(template,
        "管理员在试卷编辑页设置考试时长、每题分值和及格分，并从题库选择试题。试卷配置界面见图5-7。"
        "计划发布时固定考核规则和题目快照，后续编辑不改变已发布任务的判分依据。")
    blocks[7][0].addprevious(paper_intro)
    set_image(*blocks[7], "paper.png", 7, "试卷配置与选题界面",
              "用户真实试卷编辑截图，显示考试时长、分值、及格分及选题；不是学员考试结果")

    # 学习详情和只读档案分别展示；保留进行中、未完成及超时等原始状态。
    record_intro = update("培训档案按学员任务聚合形成", visible(find("培训档案按学员任务聚合形成")).replace(
        "学员学习档案界面见图5-9。", "学员任务学习详情见图5-9，学习档案见图5-10。"))
    task_picture = clone_paragraph(blocks[9][0])
    task_caption = clone_paragraph(blocks[9][1])
    max_relation = max(int(r.get("Id")[3:]) for r in relations)
    new_id = "rId" + str(max_relation + 1)
    ET.SubElement(relations, "{http://schemas.openxmlformats.org/package/2006/relationships}Relationship",
                  Id=new_id, Type=NS["r"] + "/image", Target="media/real-scenes-task-detail.png")
    relation_map[new_id] = "media/real-scenes-task-detail.png"
    task_picture.find(".//a:blip", NS).set("{" + NS["r"] + "}embed", new_id)
    drawing_id = max(int(p.get("id")) for p in root.xpath("//wp:docPr", namespaces=NS)) + 1
    for props in task_picture.xpath(".//wp:docPr|.//pic:cNvPr", namespaces=NS):
        props.set("id", str(drawing_id))
    record_intro.addnext(task_caption)
    record_intro.addnext(task_picture)
    set_image(task_picture, task_caption, "task-detail.png", 9, "学员任务学习详情界面",
              "用户真实任务详情截图，显示考试准入提示、课程有效学时及各课件确认位置")
    set_image(*blocks[9], "record.png", 10, "学员学习档案与会话记录界面",
              "用户真实档案截图，当前任务进行中、考试未开始；包含已签退与抽验超时终止会话，照片缩略图已遮挡")
    record_note = clone_paragraph(template,
        "图5-10中的任务仍处于进行中，考试尚未开始。会话列表分别保留已签退记录和因抽验超时终止的记录，"
        "便于追溯不同学习过程；有效学时与完成状态按当前任务分别展示。")
    blocks[9][1].addnext(record_note)
    update("管理员可从任务详情逐层查看课程", visible(find("管理员可从任务详情逐层查看课程")).replace(
        "管理员学时监管与抽验记录界面见图5-10。",
        "管理员还可按计划查询参训、已结业和学习完成等汇总指标，培训统计界面见图5-11。"))
    set_image(*blocks[10], "statistics.png", 11, "管理员培训统计界面",
              "用户真实培训统计截图，按计划汇总任务数量、学习考试状态和学时；不推断总体培训效果")

    parts["word/document.xml"] = ET.tostring(root, encoding="UTF-8", xml_declaration=True, standalone=True)
    parts["word/_rels/document.xml.rels"] = ET.tostring(relations, encoding="UTF-8", xml_declaration=True, standalone=True)
    target.parent.mkdir(parents=True, exist_ok=True)
    with ZipFile(target, "w") as package:
        for info in infos:
            package.writestr(info, parts.pop(info.filename))
        for name, data in parts.items():
            package.writestr(name, data)
    report.write_text(json.dumps({"input_sha256": BASELINE,
        "output_sha256": sha256(target.read_bytes()).hexdigest(),
        "changed_text": changed_text, "figures": figures}, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def main():
    """显式指定输入、资产及候选输出，不直接覆盖论文主文件。"""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", type=Path, required=True)
    parser.add_argument("--assets", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--report", type=Path, required=True)
    args = parser.parse_args()
    integrate(args.input, args.assets, args.output, args.report)


if __name__ == "__main__":
    main()
