"""按标准流程图符号生成本轮两幅论文图，保留历史批次图源。"""

from hashlib import sha256
import json
import math
from pathlib import Path
import xml.etree.ElementTree as ET

from PIL import Image, ImageDraw

from build_advisor_figures import Figure, SCALE


ROOT = Path(__file__).resolve().parents[3]
OUT = ROOT / "docs/thesis/figures/flowcharts-20261017"
PRINT_WIDTH_CM = 15.5


class FlowFigure(Figure):
    """复用同源绘图器，补充预定义子流程及标准注释符号。"""

    def process(self, label, x, y, width, height, predefined=False):
        """添加普通矩形处理，或两侧带竖线的预定义子流程。"""
        node = self.shape("rect", label, x, y, width, height, 24)
        node["predefined"] = predefined
        return node

    def terminal(self, label, x, y, width=160, height=44):
        """使用椭圆表示流程开始或结束。"""
        return self.shape("ellipse", label, x, y, width, height, 24)

    def decision(self, label, x, y, width, height):
        """使用菱形表示判断，出口由调用方分别注明是、否。"""
        return self.shape("diamond", label, x, y, width, height, 24)

    def flow(self, points, label="", at=None):
        """添加有向控制流，将判断标签置于线旁。"""
        self.line(points, arrow=True)
        if label:
            self.shape("text", label, at[0], at[1], 42, 30, 23)

    def annotation(self, x, top, bottom, source):
        """绘制括注及无箭头虚线，区别于业务处理节点和控制流。"""
        self.line([(x + 20, top), (x, top), (x, bottom), (x + 20, bottom)])
        for start in range(int(source[0]), x, 14):
            self.line([(start, source[1]), (min(start + 7, x), source[1])])

    def asynchronous_flow(self, points, label, at):
        """以带箭头虚线表示已提交数据触发的异步同步，不阻塞主受理流程。"""
        for index, (start, end) in enumerate(zip(points, points[1:])):
            length = math.dist(start, end)
            dx, dy = (end[0] - start[0]) / length, (end[1] - start[1]) / length
            has_arrow = index == len(points) - 2
            limit = length - 14 if has_arrow else length
            for offset in range(0, int(limit), 14):
                self.line([(start[0] + dx * offset, start[1] + dy * offset),
                           (start[0] + dx * min(offset + 7, limit),
                            start[1] + dy * min(offset + 7, limit))])
            if has_arrow:
                self.flow([(end[0] - dx * 14, end[1] - dy * 14), end])
        self.shape("text", label, at[0], at[1], 140, 35, 23)

    def write(self, output_dir=None):
        """在三种导出中同步补齐预定义过程的双竖线，并校验画布边界。"""
        output_dir = Path(output_dir) if output_dir is not None else OUT
        for item in self.items:
            if item["kind"] != "line":
                assert 0 <= item["x"] and item["x"] + item["w"] <= self.width
                assert 0 <= item["y"] and item["y"] + item["h"] <= self.height
        result = super().write(output_dir)
        predefined = [item for item in self.items if item.get("predefined")]
        if predefined:
            png_path = output_dir / (self.name + ".png")
            picture = Image.open(png_path)
            pen = ImageDraw.Draw(picture)
            svg_path = output_dir / (self.name + ".svg")
            svg = svg_path.read_text(encoding="utf-8")
            drawio_path = output_dir / (self.name + ".drawio")
            tree = ET.parse(drawio_path)
            extra = []
            for node in predefined:
                for x in (node["x"] + 13, node["x"] + node["w"] - 13):
                    y, end_y = node["y"], node["y"] + node["h"]
                    pen.line([(x * SCALE, y * SCALE), (x * SCALE, end_y * SCALE)],
                             fill="black", width=4)
                    extra.append(f'<path d="M {x} {y} V {end_y}" stroke="black" stroke-width="1.4"/>')
                cell = tree.find(f'.//mxCell[@id="{node["id"]}"]')
                cell.set("style", cell.get("style").replace("rounded=0;", "shape=process;size=0.037;"))
            picture.save(png_path, dpi=(450, 450))
            svg_path.write_text(svg.replace("</svg>", "\n".join(extra) + "\n</svg>"), encoding="utf-8")
            ET.indent(tree)
            tree.write(drawio_path, encoding="utf-8", xml_declaration=True)
        sizes = [item["size"] for item in self.items if item["kind"] != "line" and item["label"]]
        result.update({
            "pixelSize": [self.width * SCALE, self.height * SCALE],
            "printSizeCm": [PRINT_WIDTH_CM, round(PRINT_WIDTH_CM * self.height / self.width, 3)],
            "mainPrintFontPt": round(24 * PRINT_WIDTH_CM / 2.54 * 72 / self.width, 2),
            "minPrintFontPt": round(min(sizes) * PRINT_WIDTH_CM / 2.54 * 72 / self.width, 2),
            "files": {suffix: sha256((output_dir / (self.name + suffix)).read_bytes()).hexdigest()
                      for suffix in (".drawio", ".svg", ".png")},
        })
        return result


def learning_flow():
    """区分同步受理与按需异步同步，保留重复确认及超时暂停的真实语义。"""
    fig = FlowFigure("learning-flow", "学习进度受理与结果同步流程", 1000, 1200)
    fig.terminal("开始", 180, 15)
    fig.process("学员发送进度消息\n核验身份与会话绑定", 55, 93, 410, 80)
    fig.decision("身份与绑定\n是否合法？", 70, 200, 380, 105)
    fig.decision("请求是否\n已经受理？", 70, 355, 380, 110)
    fig.decision("序号、状态、任务\n及位置均合法？", 70, 540, 380, 120)
    fig.process("按规则计算有效学时\n事务保存学时、会话及事件\n按需写入 Outbox", 55, 735, 410, 112)
    fig.process("返回 ACK 与确认学时\n（无须等待异步结果）", 55, 905, 410, 80)
    fig.terminal("受理结束", 180, 1045)

    fig.flow([(260, 59), (260, 93)])
    fig.flow([(260, 173), (260, 200)])
    fig.flow([(260, 305), (260, 355)], "是", (267, 311))
    fig.flow([(260, 465), (260, 540)], "否", (267, 482))
    fig.flow([(260, 660), (260, 735)], "是", (267, 679))
    fig.flow([(260, 847), (260, 905)])
    fig.flow([(260, 985), (260, 1045)])

    fig.process("返回身份或绑定错误", 610, 225, 320, 55)
    fig.terminal("受理结束", 690, 310)
    fig.flow([(450, 252.5), (610, 252.5)], "否", (485, 216))
    fig.flow([(770, 280), (770, 310)])
    fig.process("返回历史确认结果\n（ACK 与确认学时）", 610, 370, 320, 80)
    fig.terminal("受理结束", 690, 485)
    fig.flow([(450, 410), (610, 410)], "是", (485, 374))
    fig.flow([(770, 450), (770, 485)])
    fig.process("返回对应校验错误", 610, 570, 320, 60)
    fig.terminal("受理结束", 690, 670)
    fig.flow([(450, 600), (610, 600)], "否", (485, 564))
    fig.flow([(770, 630), (770, 670)])

    # 事务提交后仅按需触发异步支路，仍由同一开始节点进入。
    fig.asynchronous_flow([(465, 791), (535, 791), (535, 875), (610, 875)],
                          "按需异步", (465, 745))
    fig.process("读取已提交的\n待发送任务 Outbox", 610, 835, 320, 80)
    fig.process("RabbitMQ 至少一次投递\n失败按策略重试", 610, 945, 320, 80)
    fig.process("培训服务幂等去重\n按事件更新参训任务", 610, 1045, 320, 80)
    fig.terminal("同步结束", 690, 1148)
    for start, end in [(915, 945), (1025, 1045), (1125, 1148)]:
        fig.flow([(770, start), (770, end)])
    fig.shape("text", "注：上报超时可成功受理并暂停，\n本次有效学时按规则计为 0。", 25, 1120, 480, 62, 23)
    return fig


def core_flow():
    """主线显式表达学习及考试条件，复杂学习过程以预定义子流程和括注展开。"""
    fig = FlowFigure("core-business-flow", "核心业务流程设计", 1000, 1215)
    fig.terminal("开始", 220, 25)
    fig.process("课程启用与计划发布\n冻结规则、课件及参训任务", 125, 100, 350, 80)
    fig.process("账号登录与任务授权\n读取本人培训任务", 125, 207, 350, 80)
    fig.process("学习与过程核验子流程\n服务端受理并确认学时", 125, 312, 350, 80, predefined=True)
    fig.decision("全部课程\n学习完成？", 125, 420, 350, 110)
    fig.decision("需要考试？", 125, 560, 350, 100)
    fig.process("考试作答与服务端判分\n保存答案并判定成绩", 125, 710, 350, 80)
    fig.decision("考试通过？", 125, 815, 350, 100)
    fig.process("按规则判定培训完成", 125, 965, 350, 60)
    fig.process("动态聚合与查询培训档案\n快照、学时、核验及成绩", 125, 1058, 350, 80)
    fig.terminal("结束", 220, 1160, height=40)

    for start, end in [(69, 100), (180, 207), (287, 312), (392, 420),
                       (790, 815), (1025, 1058), (1138, 1160)]:
        fig.flow([(300, start), (300, end)])
    fig.flow([(300, 530), (300, 560)], "是", (308, 530))
    fig.flow([(300, 660), (300, 710)], "是", (308, 670))
    fig.flow([(300, 915), (300, 965)], "是", (308, 925))
    # 未完成与考试未通过也能查询过程档案，不经过培训完成节点。
    fig.flow([(125, 475), (30, 475), (30, 1098), (125, 1098)], "否", (69, 439))
    fig.flow([(125, 610), (65, 610), (65, 995), (125, 995)], "否", (73, 574))
    fig.flow([(475, 865), (520, 865), (520, 1098), (475, 1098)], "否", (478, 829))

    # 无边框文字、括注及无箭头虚线均表示注释，避免被误读为附加处理步骤。
    fig.shape("text", "学习子流程注释", 560, 40, 410, 38, 24)
    fig.annotation(545, 95, 800, (475, 352))
    fig.shape("text", "播放与确认", 560, 105, 410, 35, 24)
    fig.shape("text", "视频播放与有效学时累计\n受理及结果同步见图 4-7", 560, 145, 410, 70, 24)
    fig.shape("text", "断线恢复", 560, 245, 410, 35, 24)
    fig.shape("text", "暂停视频与计时\n重连后重新绑定并同步确认位置\n保持暂停，由学员手动继续", 560, 285, 410, 110, 24)
    fig.shape("text", "随机抽验", 560, 430, 410, 35, 24)
    fig.shape("text", "启用抽验且达到触发时点\n暂停播放，拍照并作 1∶1 比对\n未通过且可重试：再次拍照\n通过：保持暂停，手动继续\n超时或次数耗尽：终止会话\n保存核验过程与结果", 560, 470, 410, 205, 24)
    fig.shape("text", "课程完成条件", 560, 705, 410, 35, 24)
    fig.shape("text", "全部课件完成且有效学时达标", 560, 745, 410, 40, 24)
    fig.shape("text", "档案随业务过程动态聚合，\n未完成或终止时也可查询留痕。", 555, 970, 420, 75, 23)
    return fig


def main():
    """输出同源三格式与打印尺寸、字号、指纹清单，供论文修订登记。"""
    manifest = [figure.write() for figure in (learning_flow(), core_flow())]
    (OUT / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(manifest, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
