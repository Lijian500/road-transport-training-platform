"""按drawio插件原生XML路线调整终止符与实线，复用既有流程定义。"""
from hashlib import sha256
import json
from pathlib import Path
import xml.etree.ElementTree as ET

from PIL import Image, ImageDraw, ImageFont

import build_standard_flowcharts as source
from build_advisor_figures import FONT, SCALE

OUT = source.ROOT / "docs/thesis/figures/flowcharts-drawio-20261017"


class DrawioFlowFigure(source.FlowFigure):
    """保留坐标和业务语义，仅调整起止符及连接线的表示。"""

    def annotation(self, x, top, bottom, start):
        """以无箭头实线连接子流程和括注，避免混入业务箭头。"""
        self.line([(x + 20, top), (x, top), (x, bottom), (x + 20, bottom)])
        self.line([start, (x, start[1])])

    def asynchronous_flow(self, points, label, at):
        """异步分支用连续实线箭头，仍由文字明确其异步含义。"""
        self.line(points, arrow=True)
        self.shape("text", label, at[0], at[1], 140, 35, 23)

    def write(self, output_dir=None):
        """生成原生drawio和同源插图，将终止符改为半圆端胶囊形。"""
        directory = Path(output_dir) if output_dir else OUT
        result = super().write(directory)
        png_path, svg_path, xml_path = [directory / (self.name + suffix)
                                        for suffix in (".png", ".svg", ".drawio")]
        picture = Image.open(png_path)
        pen = ImageDraw.Draw(picture)
        svg = svg_path.read_text(encoding="utf-8")
        tree = ET.parse(xml_path)
        terminal_count = 0
        for node in self.items:
            if node["kind"] != "ellipse":
                continue
            terminal_count += 1
            x, y, w, h = [node[key] for key in ("x", "y", "w", "h")]
            box = tuple(round(value * SCALE) for value in (x, y, x + w, y + h))
            # 在该符号范围内重绘，保持外部流线及其余图元的原像素。
            pen.rectangle(box, fill="white")
            pen.rounded_rectangle(box, radius=h * SCALE / 2, fill="white", outline="black", width=4)
            font = ImageFont.truetype(str(FONT), node["size"] * SCALE)
            text_width = pen.textlength(node["label"], font=font)
            pen.text(((x + w / 2) * SCALE - text_width / 2,
                      (y + (h - node["size"] * 1.3) / 2) * SCALE),
                     node["label"], font=font, fill="black")
            ellipse = f'<ellipse cx="{x+w/2}" cy="{y+h/2}" rx="{w/2}" ry="{h/2}"'
            capsule = f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{h/2}" ry="{h/2}"'
            assert svg.count(ellipse) == 1
            svg = svg.replace(ellipse, capsule)
            cell = tree.find(f'.//mxCell[@id="{node["id"]}"]')
            # mxRectangleShape使用min(宽,高)×arcSize/100作为圆角半径。
            cell.set("style", cell.get("style").replace("ellipse;", "rounded=1;arcSize=50;absoluteArcSize=0;"))
        for cell in tree.findall(".//mxCell[@edge='1']"):
            cell.set("style", cell.get("style") + "html=1;dashed=0;")
        tree.find(".//mxGraphModel").set("adaptiveColors", "none")
        ET.indent(tree)
        tree.write(xml_path, encoding="utf-8", xml_declaration=False)
        picture.save(png_path, dpi=(450, 450))
        svg_path.write_text(svg, encoding="utf-8")
        result.update({
            "terminalCount": terminal_count,
            "terminalStyle": "胶囊形终止符：中部直线，两端半圆",
            "dashedLines": 0,
            "workflow": "drawio@drawio 1.2.3原生XML；无桌面CLI，PNG/SVG由同源本地渲染器生成",
            "files": {suffix: sha256((directory / (self.name + suffix)).read_bytes()).hexdigest()
                      for suffix in (".drawio", ".svg", ".png")},
        })
        return result


def main():
    """借用既有流程定义，限定替换绘图类并输出本批独立图源。"""
    previous = source.FlowFigure
    try:
        source.FlowFigure = DrawioFlowFigure
        figures = [source.learning_flow(), source.core_flow()]
    finally:
        source.FlowFigure = previous
    manifest = [figure.write() for figure in figures]
    (OUT / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(manifest, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
