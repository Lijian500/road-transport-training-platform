"""合并导师反馈修订的图源与目录页码，不改写原论文。"""
import argparse
from io import BytesIO
from pathlib import Path
from zipfile import ZipFile
import hashlib
import json

from lxml import etree as ET
from PIL import Image

NS = {
    "w": "http://schemas.openxmlformats.org/wordprocessingml/2006/main",
    "a": "http://schemas.openxmlformats.org/drawingml/2006/main",
    "wp": "http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing",
    "r": "http://schemas.openxmlformats.org/officeDocument/2006/relationships",
}
MEDIA = {
    "image6.png": "architecture.png", "image8.png": "er-overview.png",
    "image9.png": "er-admin.png", "image10.png": "er-training.png",
    "image11.png": "er-learning.png", "image12.png": "learning-flow.png",
    **{"image{}.png".format(n + 12): "figure-5-{}.png".format(n) for n in range(1, 9)},
}
DESCRIPTIONS = {
    "architecture.png": "系统总体架构及服务调用和结果事件传播关系",
    "er-overview.png": "核心业务实体、菱形联系及1/N/M基数总览",
    "er-admin.png": "管理域组织用户、车辆、区域与角色权限概念ER图",
    "er-training.png": "培训与考试域课程、快照、任务、试卷及答案概念ER图",
    "er-learning.png": "学习会话、事件、进度、抽验与消息概念ER图",
    "learning-flow.png": "学习进度受理、幂等响应与结果事件同步流程",
    "figure-5-1.png": "演示用户详情中的部门、车牌、角色、状态与人脸登记",
    "figure-5-2.png": "演示课程名称、规定学时与学习规则",
    "figure-5-3.png": "发布课程和课件快照的名称、顺序及视频时长",
    "figure-5-4.png": "有效和规定学时、补学提示按钮与当前课件状态",
    "figure-5-5.png": "同一抽验弹窗拍照前后两个操作阶段，照片区域已遮挡",
    "figure-5-6.png": "考试倒计时、作答进度、保存状态与第一题选项",
    "figure-5-7.png": "计划状态、有效和规定学时、考试成绩与培训状态",
    "figure-5-8.png": "抽验时间、零提交、超过抽验时限及无提交照片提示",
}


def digest(value):
    """记录实际字节，避免混用不同候选版本的证据。"""
    return hashlib.sha256(value).hexdigest()


def write_package(source, target, replacements):
    """只替换指定包部件，其他部件逐字节复制。"""
    target.parent.mkdir(parents=True, exist_ok=True)
    with ZipFile(BytesIO(source)) as old, ZipFile(target, "w") as new:
        for item in old.infolist():
            new.writestr(item, replacements.get(item.filename, old.read(item.filename)))


def merge_media(source, assets, target):
    """按原媒体关系替换十四幅图，等比例设置打印尺寸并保持图题同页。"""
    original = source.read_bytes()
    with ZipFile(BytesIO(original)) as package:
        root = ET.fromstring(package.read("word/document.xml"))
        relationships = ET.fromstring(package.read("word/_rels/document.xml.rels"))
        relation_map = {r.get("Id"): r.get("Target") for r in relationships}
        replacements, report = {}, []
        for drawing in root.xpath("//w:drawing", namespaces=NS):
            blip = drawing.find(".//a:blip", NS)
            if blip is None:
                continue
            media = relation_map[blip.get("{" + NS["r"] + "}embed")]
            name = Path(media).name
            if name not in MEDIA:
                continue
            asset = assets / MEDIA[name]
            data = asset.read_bytes()
            with Image.open(BytesIO(data)) as picture:
                width_px, height_px = picture.size
            width_cm = 15.5
            height_cm = width_cm * height_px / width_px
            limit = 18.5 if asset.name.startswith("figure-5-") else 20.5
            if height_cm > limit + .05:
                raise ValueError("图像过高，需先调整构图而非缩小文字：{} {:.2f}cm".format(asset, height_cm))
            cx, cy = round(width_cm * 360000), round(height_cm * 360000)
            for extent in drawing.xpath(".//wp:extent|.//a:xfrm/a:ext", namespaces=NS):
                extent.set("cx", str(cx))
                extent.set("cy", str(cy))
            for props in drawing.xpath(".//wp:docPr", namespaces=NS):
                props.set("name", asset.name)
                props.set("descr", DESCRIPTIONS[asset.name])
            paragraph = drawing.getparent()
            while paragraph.tag != "{" + NS["w"] + "}p":
                paragraph = paragraph.getparent()
            properties = paragraph.find("w:pPr", NS)
            if properties is None:
                properties = ET.Element("{" + NS["w"] + "}pPr")
                paragraph.insert(0, properties)
            keep = properties.find("w:keepNext", NS)
            if keep is None:
                keep = ET.SubElement(properties, "{" + NS["w"] + "}keepNext")
            keep.set("{" + NS["w"] + "}val", "1")
            replacements["word/" + media] = data
            report.append({"asset": asset.as_posix(), "media": "word/" + media,
                           "pixels": [width_px, height_px], "width_cm": width_cm,
                           "height_cm": round(height_cm, 4), "sha256": digest(data)})
        assert len(report) == len(MEDIA) == 14
        replacements["word/document.xml"] = ET.tostring(root, encoding="UTF-8", xml_declaration=True, standalone=True)
    write_package(original, target, replacements)
    return {"input_sha256": digest(original), "output_sha256": digest(target.read_bytes()), "figures": report}


def merge_toc(source, refreshed, target):
    """仅回填办公软件计算的目录页码，保留候选的正文和所有其他部件。"""
    original = source.read_bytes()
    with ZipFile(BytesIO(original)) as package, ZipFile(refreshed) as saved:
        root = ET.fromstring(package.read("word/document.xml"))
        updated = ET.fromstring(saved.read("word/document.xml"))
        old_entries = root.xpath('//w:body/w:p[w:pPr/w:pStyle[starts-with(@w:val,"TOC") or starts-with(@w:val,"toc")]]', namespaces=NS)
        new_entries = updated.xpath('//w:body/w:p[w:pPr/w:pStyle[starts-with(@w:val,"TOC") or starts-with(@w:val,"toc")]]', namespaces=NS)
        # WPS可能使用数字样式ID，因此同时按样式名称定位。
        if not old_entries:
            styles = ET.fromstring(package.read("word/styles.xml"))
            ids = [s.get("{" + NS["w"] + "}styleId") for s in styles.findall("w:style", NS)
                   if s.find("w:name", NS) is not None and s.find("w:name", NS).get("{" + NS["w"] + "}val", "").lower().startswith("toc ")]
            old_entries = [p for p in root.findall("w:body/w:p", NS) if p.find("w:pPr/w:pStyle", NS) is not None and p.find("w:pPr/w:pStyle", NS).get("{" + NS["w"] + "}val") in ids]
            saved_styles = ET.fromstring(saved.read("word/styles.xml"))
            saved_ids = [s.get("{" + NS["w"] + "}styleId") for s in saved_styles.findall("w:style", NS)
                         if s.find("w:name", NS) is not None and s.find("w:name", NS).get("{" + NS["w"] + "}val", "").lower().startswith("toc ")]
            new_entries = [p for p in updated.findall("w:body/w:p", NS) if p.find("w:pPr/w:pStyle", NS) is not None and p.find("w:pPr/w:pStyle", NS).get("{" + NS["w"] + "}val") in saved_ids]
        assert len(old_entries) == len(new_entries) and old_entries
        changed = 0
        for old, new in zip(old_entries, new_entries):
            old_texts = old.xpath(".//w:t", namespaces=NS)
            new_texts = new.xpath(".//w:t", namespaces=NS)
            assert "".join(n.text or "" for n in old_texts[:-1]) == "".join(n.text or "" for n in new_texts[:-1])
            if old_texts[-1].text != new_texts[-1].text:
                changed += 1
                old_texts[-1].text = new_texts[-1].text
        data = ET.tostring(root, encoding="UTF-8", xml_declaration=True, standalone=True)
    write_package(original, target, {"word/document.xml": data})
    return {"toc_entries": len(old_entries), "updated_page_caches": changed, "output_sha256": digest(target.read_bytes())}


def main():
    """分别执行媒体合并或目录缓存回填，报告实际结果。"""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--assets", type=Path)
    parser.add_argument("--toc-source", type=Path)
    parser.add_argument("--report", type=Path, required=True)
    args = parser.parse_args()
    assert args.input.resolve() != args.output.resolve(), "输入和输出必须分开"
    assert bool(args.assets) != bool(args.toc_source), "每次只执行一种合并"
    result = merge_media(args.input, args.assets, args.output) if args.assets else merge_toc(args.input, args.toc_source, args.output)
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(result, ensure_ascii=False))


if __name__ == "__main__":
    main()
