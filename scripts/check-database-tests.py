"""检查本轮数据库集成测试确实执行，防止Docker不可用时CI静默全绿。"""
from pathlib import Path
import json
import sys
import xml.etree.ElementTree as ET


def main():
    """只输出计数，不归档可能含环境配置的完整Surefire属性。"""
    root = Path(__file__).resolve().parent.parent
    reports = []
    for module, name in [
        ("admin", "AdminMapperIntegrationTest"),
        ("training", "TrainingMapperIntegrationTest"),
        ("learning", "LearningMapperIntegrationTest"),
    ]:
        matches = list((root / "backend" / f"train-{module}-service" / "target" / "surefire-reports").glob(f"TEST-*.{name}.xml"))
        if len(matches) != 1:
            raise RuntimeError(f"缺少本轮{name}报告")
        suite = ET.parse(matches[0]).getroot()
        counts = {key: int(suite.get(key, "0")) for key in ("tests", "failures", "errors", "skipped")}
        reports.append({"suite": name, **counts})
    print(json.dumps(reports, ensure_ascii=False, indent=2))
    return 0 if all(row["tests"] > 0 and not any(row[key] for key in ("failures", "errors", "skipped")) for row in reports) else 1


if __name__ == "__main__":
    sys.exit(main())
