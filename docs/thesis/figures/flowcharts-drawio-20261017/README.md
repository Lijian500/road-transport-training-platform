# 2026-10-17 drawio终止符与实线修订图源

本图库对应[CHG-20261017-06](../../../changes/2026-10-17-thesis-flowchart-symbols.md)，已写入最新论文的图4-7及图4-8。沿用[前批图源](../flowcharts-20261017/README.md)的内容、布局、字号和尺寸，仅修改8个终止符及两处连接线。

| 图号 | 可编辑图源 | 同源插图 | 像素及打印尺寸 |
| --- | --- | --- | --- |
| 4-7 学习进度受理与结果同步流程 | [drawio](learning-flow.drawio) | [PNG](learning-flow.png)、[SVG](learning-flow.svg) | 3000×3600；15.5×18.6厘米 |
| 4-8 核心业务流程设计 | [drawio](core-business-flow.drawio) | [PNG](core-business-flow.png)、[SVG](core-business-flow.svg) | 3000×3645；15.5×18.8325厘米 |

开始和结束均为中部平直、两端半圆的胶囊形终止符。原生drawio使用`rounded=1;arcSize=50;absoluteArcSize=0`，SVG的圆角半径为高度的一半，PNG使用相同半径。两图均只有一个开始，判断均标明是／否。图4-7的异步支路为实线箭头，保留“按需异步”和无须等待异步结果的文字；图4-8注释以无箭头实线接括注，不表示额外业务流转。

调用本机`drawio@drawio` 1.2.3插件的原生XML路线，按其规则生成并执行修复检查；两图XML均无需修复。未发现draw.io Desktop，因此未执行桌面CLI导出；PNG/SVG使用同源本地渲染器。可用draw.io打开原生文件继续编辑。文件指纹及字号见[manifest.json](manifest.json)，生成入口为[build_drawio_flowcharts.py](../../tools/build_drawio_flowcharts.py)。

图元文字、节点坐标、是／否数量、连续实线、UTF-8无BOM及三格式解析均已核对；PNG差异严格限于起止符和连接线。Word的70页比对和第41、44页实际目视检查见[正式验证JSON](../../../test/thesis-flowchart-symbols-2026-10-17.json)，其余68页与输入校样逐字节一致。该图形调整不改变业务逻辑或实验结论。
