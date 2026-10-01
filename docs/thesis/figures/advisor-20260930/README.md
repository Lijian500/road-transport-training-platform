# 论文设计插图

图源与论文的对应关系如下。每图同时提供可编辑 `.drawio`、矢量 `.svg` 与三倍画布分辨率 `.png`，Word 嵌入 PNG。修订范围、代码基线与同步状态统一见[本次批次记录](../../../changes/2026-09-30-thesis-analysis-design.md)。

| 论文编号 | 文件前缀 | 内容 |
| --- | --- | --- |
| 图3-1 | usecase-platform | 平台管理员用例 |
| 图3-2 | usecase-enterprise | 企业管理员用例 |
| 图3-3 | usecase-student | 学员用例 |
| 图4-1 | architecture | 总体架构，修复原注释压线 |
| 图4-2 | function-structure | 七个功能模块及其子功能 |
| 图4-3 | er-overview | 核心业务概念 ER 总览 |
| 图4-4 | er-admin | 管理域概念 ER |
| 图4-5 | er-training | 培训与考试域概念 ER |
| 图4-6 | er-learning | 学习监管域概念 ER |
| 图4-7 | learning-flow | 学习进度处理，修复原注释压线 |

用例图使用参与者、系统边界及椭圆用例。登录授权与先学后考在正文作为前置条件说明，未把业务执行顺序画成包含关系。功能图按系统、模块、子功能展开。

ER 图采用实体矩形、属性椭圆、联系菱形、下划线标识属性与关系基数。总览突出主线，分域图只列关键属性；关联表、完整字段与有序索引组合由[数据字典](../../../../database/design/data-dictionary.md)承接。图中关系为逻辑关联，迁移及外部表DDL均没有声明物理外键；可选车辆、零次抽验提交和任务的可选考试记录均单独表达。外部行政区域实体的id已按[真实DDL](../../../../database/design/sys_address.source.sql)确认是自增主键，现有ER图的标识与之相符。

用 diagrams.net 打开 `.drawio` 可编辑每个节点和连线。统一生成方式是在项目根目录运行 `python docs/thesis/tools/build_advisor_figures.py`，依赖 Pillow 和 Windows 宋体；图元数据集中在该脚本。手工编辑 drawio 后应重新导出 SVG／PNG 并替换 Word 对应插图；再次运行生成器会按脚本内容覆盖同名图源与图片。
