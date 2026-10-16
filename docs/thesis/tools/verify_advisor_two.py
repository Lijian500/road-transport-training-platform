"""核对反馈二Word结构、目录与图题，并渲染最终PDF页图供逐页检查。"""

import argparse
from hashlib import sha256
import json
from pathlib import Path
import re
import subprocess
import unicodedata
from zipfile import ZipFile

from lxml import etree as ET
import pdfplumber
from pypdf import PdfReader


ROOT = Path(__file__).resolve().parents[3]
NS = {"w": "http://schemas.openxmlformats.org/wordprocessingml/2006/main",
      "m": "http://schemas.openxmlformats.org/officeDocument/2006/math",
      "wp": "http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing",
      "a": "http://schemas.openxmlformats.org/drawingml/2006/main",
      "r": "http://schemas.openxmlformats.org/officeDocument/2006/relationships"}
W = "{" + NS["w"] + "}"
POPPLER = Path("C:/Users/Admin/.cache/codex-runtimes/codex-primary-runtime/dependencies/native/poppler/Library/bin/pdftoppm.exe")


def digest(path):
    """按实际文件字节记录版本。"""
    return sha256(path.read_bytes()).hexdigest()


def compact(value):
    """规范空白与罗马数字，用于比较目录和PDF正文。"""
    return re.sub(r"\s+", "", unicodedata.normalize("NFKC", value or ""))


def page_value(value):
    """将本稿使用的阿拉伯或罗马页码转为序号。"""
    value = compact(value)
    if value.isdigit():
        return "arabic", int(value)
    roman = {"I": 1, "II": 2, "III": 3, "IV": 4, "V": 5}
    return ("roman", roman[value]) if value in roman else ("missing", 0)


def visible(node):
    """只读取可见文字，排除域指令。"""
    return "".join(node.xpath(".//w:t/text()", namespaces=NS))


def verify_structure(source, target):
    """核查限定的包部件变化、公式表格文献及21幅图。"""
    with ZipFile(source) as old, ZipFile(target) as new:
        old_names, new_names = set(old.namelist()), set(new.namelist())
        assert old_names <= new_names
        added = sorted(new_names - old_names)
        changed = sorted(name for name in old_names if old.read(name) != new.read(name))
        assert changed == ["word/_rels/document.xml.rels", "word/document.xml"], changed
        assert len(added) == 3 and all(n.startswith("word/media/advisor-two-") for n in added), added
        before = ET.fromstring(old.read("word/document.xml"))
        after = ET.fromstring(new.read("word/document.xml"))
        styles = ET.fromstring(new.read("word/styles.xml"))
        toc_ids = {s.get(W + "styleId") for s in styles.findall("w:style", NS)
                   if s.find("w:name", NS) is not None
                   and s.find("w:name", NS).get(W + "val", "").lower().startswith("toc ")}
        for query in ("//w:tbl", "//m:oMath", "//w:sectPr"):
            assert [ET.tostring(n, method="c14n") for n in before.xpath(query, namespaces=NS)] == [
                ET.tostring(n, method="c14n") for n in after.xpath(query, namespaces=NS)], query
        reference_query = '//w:body/w:p[starts-with(w:r/w:t,"[")]'
        references_before = [ET.tostring(p, method="c14n") for p in before.xpath(reference_query, namespaces=NS)]
        references_after = [ET.tostring(p, method="c14n") for p in after.xpath(reference_query, namespaces=NS)]
        assert len(references_before) == len(references_after) == 21
        assert references_before == references_after
        captions, toc = [], []
        for paragraph in after.findall("w:body/w:p", NS):
            text = visible(paragraph)
            if re.match(r"^图\d+-\d+ ", text):
                captions.append(text)
            style = paragraph.find("w:pPr/w:pStyle", NS)
            if style is not None and style.get(W + "val") in toc_ids:
                nodes = paragraph.xpath(".//w:t/text()", namespaces=NS)
                toc.append({"title": "".join(nodes[:-1]), "page": nodes[-1]})
        assert len(captions) == 21
        assert len(after.xpath("//w:drawing", namespaces=NS)) == len(before.xpath("//w:drawing", namespaces=NS)) + 3
        assert len(toc) == 67
        assert len(after.xpath('//w:instrText[contains(text()," TOC ")]', namespaces=NS)) == 1
        drawing_ids = [p.get("id") for p in after.xpath("//wp:docPr", namespaces=NS)]
        assert len(drawing_ids) == len(set(drawing_ids))
        full_text = visible(after)
        assert not re.search(r"(?:真实|媒体|闭环).{0,18}(?:尚未验收|仍待验收|尚未执行)", full_text)
        assert "本地功能验收" in full_text and "摄像头单帧照片" in full_text
        assert "附录B" not in full_text
        return {"changed_package_parts": changed, "added_media": added,
                "native_math_preserved": len(after.xpath("//m:oMath", namespaces=NS)),
                "tables_preserved": len(after.xpath("//w:tbl", namespaces=NS)),
                "references_preserved": 21, "body_figures": len(captions), "captions": captions,
                "toc_entries": toc, "all_other_package_parts_preserved": True}


def verify_pdf(pdf, captions, toc):
    """逐条核对PDF图题同页、图片比例、目录页码及页脚。"""
    figure_results, toc_results = [], []
    pages = []
    with pdfplumber.open(pdf) as document:
        for page in document.pages:
            lines = page.extract_text_lines()
            footer = [line["text"].strip() for line in lines
                      if line["top"] > page.height - 70
                      and re.fullmatch(r"[0-9IVXⅠⅡⅢⅣⅤⅥⅦⅧⅨⅩ]+", line["text"].strip())]
            pages.append({"physical_page": page.page_number, "footer": footer[-1] if footer else "",
                          "width": page.width, "height": page.height, "lines": lines,
                          "text": page.extract_text() or ""})
            for caption in captions:
                matches = [line for line in lines if compact(line["text"]) == compact(caption)]
                for match in matches:
                    images = [im for im in page.images if im["bottom"] <= match["top"] + 1 and im["width"] >= 250]
                    image = max(images, key=lambda im: im["bottom"]) if images else None
                    assert image is not None, (caption, page.page_number, "图题与图片分离")
                    source_ratio = image["srcsize"][0] / image["srcsize"][1]
                    display_ratio = image["width"] / image["height"]
                    ratio_error = abs(source_ratio / display_ratio - 1)
                    assert ratio_error < .001, (caption, ratio_error)
                    if caption.startswith("图5-"):
                        assert abs(image["width"] - 15.5 / 2.54 * 72) < 1, (caption, image["width"])
                    figure_results.append({"caption": caption, "physical_page": page.page_number,
                                           "source_pixels": list(image["srcsize"]),
                                           "image_bbox": [image[k] for k in ("x0", "top", "x1", "bottom")],
                                           "caption_bbox": [match[k] for k in ("x0", "top", "x1", "bottom")],
                                           "ratio_error": ratio_error, "same_page": True})
        assert len(figure_results) == len(captions) == 21
        for entry in toc:
            title = compact(entry["title"])
            candidates = [(p, line) for p in pages for line in p["lines"] if compact(line["text"]) == title]
            # 目录标题可能换行，但正文标题应在单行且与已缓存页码对应。
            valid = [(p, line) for p, line in candidates if compact(p["footer"]) == compact(entry["page"])]
            method = "标题与同页页脚直接匹配"
            if not valid:
                # 沿用原稿分节：首页页脚留空，依据相邻页编号核对其页码连续性。
                kind, number = page_value(entry["page"])
                valid = [(p, line) for p, line in candidates if not p["footer"]
                         and p["physical_page"] < len(pages)
                         and page_value(pages[p["physical_page"]]["footer"]) == (kind, number + 1)]
                method = "原有分节首页页脚留空，标题定位与下一页页脚连续性核对"
            assert valid, (entry, [(p["physical_page"], p["footer"]) for p, _ in candidates])
            toc_results.append({**entry, "physical_page": valid[0][0]["physical_page"], "method": method, "verified": True})
    return {"page_count": len(pages), "figures": figure_results, "toc": toc_results,
            "pages": [{k: v for k, v in p.items() if k not in ("lines", "text")} for p in pages]}


def render_pages(pdf, directory):
    """用捆绑Poppler按100DPI生成全部页图，不复用旧版本缓存。"""
    directory = directory.resolve()
    directory.mkdir(parents=True, exist_ok=True)
    assert not list(directory.glob("page-*.png")), "请为新版本使用空的页图目录"
    subprocess.run([str(POPPLER), "-r", "100", "-png", str(pdf), str(directory / "page")],
                   check=True, capture_output=True)
    paths = sorted(directory.glob("page-*.png"), key=lambda p: int(p.stem.split("-")[-1]))
    assert len(paths) == len(PdfReader(pdf).pages)
    return [{"physical_page": i, "path": path.relative_to(ROOT).as_posix(), "sha256": digest(path)}
            for i, path in enumerate(paths, 1)]


def main():
    """输出绑定最终Word及PDF指纹的结构与渲染检查结果。"""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--word", type=Path, required=True)
    parser.add_argument("--pdf", type=Path, required=True)
    parser.add_argument("--pages", type=Path, required=True)
    parser.add_argument("--report", type=Path, required=True)
    args = parser.parse_args()
    source = next((ROOT / "docs/thesis").glob("*导师反馈一修订版.docx"))
    structure = verify_structure(source, args.word)
    pdf = verify_pdf(args.pdf, structure["captions"], structure["toc_entries"])
    pages = render_pages(args.pdf, args.pages)
    result = {"word": args.word.relative_to(ROOT).as_posix(), "word_sha256": digest(args.word),
              "pdf_sha256": digest(args.pdf), "baseline_sha256": digest(source),
              "structure": structure, "pdf": pdf, "rendered_pages": pages,
              "visual_review": "待逐页实际查看页图后另行登记", "business_tests_executed": False}
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"word_sha256": result["word_sha256"], "pages": pdf["page_count"],
                      "figures": len(pdf["figures"]), "toc_entries": len(pdf["toc"]),
                      "rendered_pages": len(pages)}, ensure_ascii=False))


if __name__ == "__main__":
    main()
