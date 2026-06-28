#!/usr/bin/env python3
"""v2.2.72: YAML 顶级 key 重复检查"""
import yaml
import sys
from pathlib import Path

SERVICES = ["cs-gateway", "cs-auth", "cs-im", "cs-message"]
ROOT = Path("/workspace/online-chat")

# 完整 profile 检查 (main + prod + test)
PROFILES = ["application", "application-prod", "application-test"]

issues = []
checked = 0

for svc in SERVICES:
    for profile in PROFILES:
        path = ROOT / svc / "src/main/resources" / f"{profile}.yml"
        if not path.exists():
            continue

        with open(path) as f:
            content = f.read()

        checked += 1

        # 找顶级 key 重复 (缩进 0, 跨 --- 分隔符重新计数)
        lines = content.split("\n")
        top_keys = {}
        for i, line in enumerate(lines, 1):
            # v2.2.72: --- 是 YAML 多文档分隔符, 重新计数
            if line.strip() == "---":
                top_keys = {}
                continue
            if not line or line.startswith("#") or line.startswith(" ") or line.startswith("\t"):
                continue
            if ":" not in line:
                continue
            key = line.split(":")[0].strip()
            if not key:
                continue
            if key in top_keys:
                issues.append(f"❌ {path.relative_to(ROOT)}:{i} 重复顶级 key '{key}' (上次出现在 L{top_keys[key]})")
            else:
                top_keys[key] = i

print(f"扫描 {checked} 个 YAML 文件")
if issues:
    print("\n".join(issues))
    sys.exit(1)
else:
    print("✓ 0 重复顶级 key")
    print("✓ 所有 YAML 格式正确")