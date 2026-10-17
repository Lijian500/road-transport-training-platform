"""核对真实截图论文的部件、图题、目录与最终校样。"""

import argparse
from hashlib import sha256
import json
from pathlib import Path
import re
import subprocess
from zipfile import ZipFile

from lxml import etree as ET
from PIL import Image
import pdfplumber

from verify_advisor_two import NS, W, POPPLER, compact, page_value, visible


def digest(path):
    """将校验结果绑定到实际文件字节。"""
    return sha256(path.read_bytes()).hexdigest()


def verify(source, target, pdf, assets, pages_dir, report):
    """定向比较旧稿与候选稿，检查新增图片及完整目录。"""
    with ZipFile(source) as old, ZipFile(target) as new:
        before = ET.fromstring(old.read("word/document.xml"))
        after = ET.fromstring(new.read("word/document.xml"))
        changed = sorted(n for n in old.namelist() if old.read(n) != new.read(n))
        added = sorted(set(new.namelist()) - set(old.namelist()))
        assert added == ["word/media/real-scenes-task-detail.png"], added
        assert len(changed) == 10, changed  # 正文、关系和8个替换媒体。
        assert all(n in ("word/document.xml", "word/_rels/document.xml.rels")
                   or n.startswith("word/media/") for n in changed)
        for query in ("//w:tbl", "//m:oMath", "//w:sectPr"):
            assert [ET.tostring(n, method="c14n") for n in before.xpath(query, namespaces=NS)] == [
                ET.tostring(n, method="c14n") for n in after.xpath(query, namespaces=NS)], query
        refs = lambda root: [ET.tostring(p, method="c14n") for p in root.findall("w:body/w:p", NS)
                             if re.match(r"^\[\d+\]", visible(p))]
        assert len(refs(after)) == 21 and refs(before) == refs(after)
        styles = ET.fromstring(new.read("word/styles.xml"))
        toc_ids = {s.get(W + "styleId") for s in styles.findall("w:style", NS)
                   if s.find("w:name", NS) is not None and
                   s.find("w:name", NS).get(W + "val", "").lower().startswith("toc ")}
        toc, captions = [], []
        for p in after.findall("w:body/w:p", NS):
            text = visible(p)
            if re.match(r"^图\d+-\d+ ", text):
                captions.append(text)
            style = p.find("w:pPr/w:pStyle", NS)
            if style is not None and style.get(W + "val") in toc_ids:
                nodes = p.xpath(".//w:t/text()", namespaces=NS)
                toc.append({"title": "".join(nodes[:-1]), "page": nodes[-1]})
        assert len(toc) == 68 and len(captions) == 22
        assert [c.split(" ")[0] for c in captions if c.startswith("图5-")] == [
            "图5-" + str(n) for n in range(1, 12)]
        ids = [n.get("id") for n in after.xpath("//wp:docPr", namespaces=NS)]
        assert len(ids) == len(set(ids)) == len(before.xpath("//wp:docPr", namespaces=NS)) + 1
        # 前四章、前置页仅允许目录页码缓存变化。
        def prefix(root):
            """去掉目录页码，比较第五章之前的完整元素。"""
            result = []
            for node in root.find("w:body", NS):
                if visible(node) == "5 系统实现":
                    break
                copy = ET.fromstring(ET.tostring(node))
                style = copy.find("w:pPr/w:pStyle", NS)
                if style is not None and style.get(W + "val") in toc_ids:
                    copy.xpath(".//w:t", namespaces=NS)[-1].text = "PAGE"
                result.append(ET.tostring(copy, method="c14n"))
            return result
        assert prefix(before) == prefix(after)
        manifest = json.loads((assets / "manifest.json").read_text(encoding="utf-8"))
        embedded_hashes = {sha256(new.read(n)).hexdigest() for n in new.namelist() if n.startswith("word/media/")}
        asset_checks = []
        for item in manifest["figures"]:
            path = assets / item["output_file"]
            assert digest(path) == item["output_sha256"]
            with Image.open(path) as image:
                assert not image.info, path
                assert sha256(image.convert("RGB").tobytes()).hexdigest() == item["output_rgb_pixels_sha256"]
            if item["embedded_in_thesis"]:
                assert item["output_sha256"] in embedded_hashes
            asset_checks.append({"file": item["output_file"], "sha256": digest(path),
                                 "embedded": item["embedded_in_thesis"]})
        assert sum(a["embedded"] for a in asset_checks) == 9
        structure = {"changed_package_parts": changed, "added_media": added,
                     "all_other_package_parts_byte_identical": True,
                     "front_matter_and_chapters_1_to_4_preserved_except_toc_cache": True,
                     "tables_preserved": len(after.xpath("//w:tbl", namespaces=NS)),
                     "native_math_preserved": len(after.xpath("//m:oMath", namespaces=NS)),
                     "references_preserved": 21, "body_figures": len(captions), "assets": asset_checks}

    figure_results, toc_results, pages = [], [], []
    with pdfplumber.open(pdf) as document:
        for page in document.pages:
            lines = page.extract_text_lines()
            footer = [line["text"].strip() for line in lines if line["top"] > page.height - 70
                      and re.fullmatch(r"[0-9IVXⅠⅡⅢⅣⅤⅥⅦⅧⅨⅩ]+", line["text"].strip())]
            pages.append({"physical_page": page.page_number, "footer": footer[-1] if footer else "",
                          "lines": lines, "text": page.extract_text() or ""})
            for caption in captions:
                for match in [line for line in lines if compact(line["text"]) == compact(caption)]:
                    images = [im for im in page.images if im["bottom"] <= match["top"] + 1 and im["width"] >= 250]
                    assert images, (caption, page.page_number, "图题分离")
                    image = max(images, key=lambda im: im["bottom"])
                    ratio_error = abs((image["srcsize"][0] / image["srcsize"][1]) /
                                      (image["width"] / image["height"]) - 1)
                    assert ratio_error < .001, (caption, ratio_error)
                    figure_results.append({"caption": caption, "physical_page": page.page_number,
                        "footer": pages[-1]["footer"], "ratio_error": ratio_error, "same_page": True,
                        "source_pixels": list(image["srcsize"]),
                        "image_bbox": [image[k] for k in ("x0", "top", "x1", "bottom")]})
        assert len(figure_results) == 22, (len(figure_results), captions)
        for entry in toc:
            candidates = [(p, line) for p in pages for line in p["lines"]
                          if compact(line["text"]) == compact(entry["title"])]
            valid = [(p, line) for p, line in candidates if compact(p["footer"]) == compact(entry["page"])]
            if not valid:
                kind, number = page_value(entry["page"])
                valid = [(p, line) for p, line in candidates if not p["footer"]
                         and p["physical_page"] < len(pages)
                         and page_value(pages[p["physical_page"]]["footer"]) == (kind, number + 1)]
            assert valid, entry
            toc_results.append({**entry, "physical_page": valid[0][0]["physical_page"], "verified": True})
    pages_dir.mkdir(parents=True, exist_ok=True)
    assert not list(pages_dir.glob("page-*.png")), "不得复用旧页图"
    subprocess.run([str(POPPLER), "-r", "100", "-png", str(pdf), str(pages_dir / "page")],
                   check=True, capture_output=True)
    page_paths = sorted(pages_dir.glob("page-*.png"), key=lambda p: int(p.stem.split("-")[-1]))
    assert len(page_paths) == len(pages)
    result = {"review_date": "2026-10-17", "baseline_commit": "87e1af622e27a6fd8cdea32689ca46d4b84eacf9",
        "input_sha256": digest(source), "word_sha256": digest(target), "pdf_sha256": digest(pdf),
        "structure": structure, "pdf": {"page_count": len(pages), "figures": figure_results, "toc": toc_results},
        "rendered_pages": [{"physical_page": i, "sha256": digest(p)} for i, p in enumerate(page_paths, 1)],
        "rendering": "标准render_docx.py因缺少捆绑LibreOffice失败；隐藏WPS实例导出临时副本PDF，捆绑Poppler 100DPI渲染",
        "visual_review": "待逐页实际检查后回填", "business_tests_executed": False}
    report.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"pages": len(pages), "figures": len(captions), "toc": len(toc),
                      "word_sha256": result["word_sha256"]}))


def main():
    """从显式的前后版本及校样生成公开验证记录。"""
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("source", "word", "pdf", "assets", "pages", "report"):
        parser.add_argument("--" + name, type=Path, required=True)
    args = parser.parse_args()
    verify(args.source, args.word, args.pdf, args.assets, args.pages, args.report)


if __name__ == "__main__":
    main()
