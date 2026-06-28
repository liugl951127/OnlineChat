#!/usr/bin/env python3
"""YAML 重复顶级 key 检查 (支持 --- 多文档)"""
import sys

def check_yaml(path):
    issues = []
    try:
        with open(path) as f:
            content = f.read()
    except FileNotFoundError:
        return [f"文件不存在: {path}"]
    
    # 按 --- 分文档 (但只切行首 ---)
    lines = content.split("\n")
    docs = [[]]
    for line in lines:
        if line.strip() == "---":
            docs.append([])
        else:
            docs[-1].append(line)
    
    for doc_idx, doc_lines in enumerate(docs):
        top_keys = {}
        for line_no, line in enumerate(doc_lines, 1):
            stripped = line.strip()
            if not stripped or stripped.startswith("#"):
                continue
            if line[0] in (' ', '\t'):
                continue
            if not stripped.endswith(":") and ":" not in stripped:
                continue
            key = stripped.split(":")[0].strip()
            if key in top_keys:
                issues.append(f"文档#{doc_idx+1} 行 {line_no}: 顶级 key '{key}' 重复 (首次出现 行 {top_keys[key]})")
            else:
                top_keys[key] = line_no
    return issues

if __name__ == "__main__":
    paths = sys.argv[1:]
    if not paths:
        print("用法: check-yaml-dup.py <yaml-file>...")
        sys.exit(1)
    total = 0
    for p in paths:
        issues = check_yaml(p)
        if issues:
            print(f"✗ {p}")
            for i in issues:
                print(f"  {i}")
            total += len(issues)
        else:
            print(f"✓ {p}")
    sys.exit(0 if total == 0 else 1)
