"""生成导师反馈二的核心业务链图，不改写其他批次图源。"""

from hashlib import sha256
import json
from pathlib import Path

from build_advisor_figures import Figure


ROOT = Path(__file__).resolve().parents[3]
OUT = ROOT / "docs/thesis/figures/advisor-two-20261016"
PRINT_WIDTH_CM = 15.5


def add_node(figure, label, x, y, width, height):
    """统一使用可满足正文打印字号的流程节点。"""
    return figure.shape("rect", label, x, y, width, height, 22)


def build_flow():
    """以七环节主线及两个学习分支展示实际业务衔接。"""
    figure = Figure("core-business-flow", "核心业务链实现流程", 900, 950)
    figure.shape("text", "正常业务主线", 50, 0, 340, 32, 22)
    figure.shape("text", "学习期间的恢复与身份核验", 445, 0, 410, 32, 22)

    main = [
        ("① 课程启用与计划发布\n冻结规则、课件及参训任务", 40, 68),
        ("② 账号登录与任务授权\n读取本人培训任务", 147, 68),
        ("③ 视频播放与学时确认\n以服务端受理结果累计学时", 254, 72),
        ("④ 全部课程学习完成", 540, 60),
        ("⑤ 考试作答与服务端判分\n保存答案，交卷后判定成绩", 665, 72),
        ("⑥ 按规则判定培训完成", 794, 52),
        ("⑦ 聚合形成培训档案\n快照、学时、成绩及过程留痕", 883, 60),
    ]
    for label, y, height in main:
        add_node(figure, label, 50, y, 340, height)
    for top, bottom in [(108, 147), (215, 254), (326, 540),
                        (600, 665), (737, 794), (846, 883)]:
        figure.line([(220, top), (220, bottom)], arrow=True)
    figure.shape("text", "需要考试", 228, 615, 130, 32, 20)
    figure.shape("text", "考试通过", 228, 747, 130, 32, 20)
    # 仅学习模式跳过考试；仅通过考试的考核任务进入完成判定。
    figure.line([(50, 570), (22, 570), (22, 820), (50, 820)], arrow=True)
    figure.shape("text", "仅学习", 28, 749, 112, 32, 20)

    add_node(figure, "断线恢复：暂停视频与计时", 445, 54, 410, 60)
    add_node(figure, "连接恢复：确认服务端位置\n重新绑定并同步状态", 445, 169, 410, 60)
    add_node(figure, "保持暂停\n由学员手动继续③", 445, 278, 410, 60)
    figure.line([(390, 278), (420, 278), (420, 84), (445, 84)], arrow=True)
    figure.line([(650, 114), (650, 169)], arrow=True)
    figure.line([(650, 229), (650, 278)], arrow=True)

    add_node(figure, "启用抽验且到达触发时点\n暂停播放，等待核验", 445, 392, 410, 60)
    add_node(figure, "摄像头拍照与 1∶1 人脸比对", 445, 511, 410, 60)
    add_node(figure, "核验通过：保持暂停\n由学员手动继续③", 445, 627, 410, 60)
    add_node(figure, "超时或次数耗尽\n终止学习会话，记录核验结果", 445, 746, 410, 60)
    figure.line([(390, 306), (420, 306), (420, 422), (445, 422)], arrow=True)
    figure.line([(650, 452), (650, 511)], arrow=True)
    figure.line([(650, 571), (650, 627)], arrow=True)
    figure.shape("text", "通过", 657, 581, 82, 32, 20)
    # 可重试结果回到拍照；超时或次数耗尽分支直接留痕，不经过结业。
    figure.line([(855, 541), (880, 541), (880, 498), (810, 498), (810, 511)], arrow=True)
    figure.shape("text", "未通过且可重试", 679, 463, 198, 32, 20)
    figure.line([(445, 541), (420, 541), (420, 776), (445, 776)], arrow=True)
    figure.line([(650, 806), (650, 913), (390, 913)], arrow=True)
    figure.shape("text", "过程记录", 657, 840, 150, 32, 20)
    return figure


def main():
    """导出同源三格式，输出尺寸、打印字号与文件指纹供论文登记。"""
    figure = build_flow()
    result = figure.write(OUT)
    result.update({
        "pixelSize": [figure.width * 3, figure.height * 3],
        "printSizeCm": [PRINT_WIDTH_CM, round(PRINT_WIDTH_CM * figure.height / figure.width, 3)],
        "minPrintFontPt": round(20 * PRINT_WIDTH_CM / 2.54 * 72 / figure.width, 2),
        "mainPrintFontPt": round(22 * PRINT_WIDTH_CM / 2.54 * 72 / figure.width, 2),
        "files": {suffix: sha256((OUT / (figure.name + suffix)).read_bytes()).hexdigest()
                  for suffix in (".drawio", ".svg", ".png")},
    })
    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
