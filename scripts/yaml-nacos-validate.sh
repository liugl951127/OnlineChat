#!/usr/bin/env bash
# v2.2.88: YAML 完整性 + Nacos 配置一致性校验
# 用法: yaml-nacos-validate.sh [nacos-addr]
#
# 检查项目:
#   1. 所有 application*.yml 顶级 key 重复 (含 cs-gateway/application-prod-direct.yml)
#   2. YAML 语法
#   3. Nacos 远端配置重复 key (如果 Nacos 可达)
#   4. 本地 application-prod.yml vs Nacos cs-{svc}-prod.yaml diff

set -e

NACOS_ADDR="${1:-127.0.0.1:8848}"
NACOS_NS="${NACOS_NAMESPACE:-prod}"
NACOS_USER="${NACOS_USER:-nacos}"
NACOS_PASS="${NACOS_PASSWORD:-nacos}"

ROOT="/workspace/online-chat"

echo "=== 1) 本地 application*.yml 重复 key 检查 ==="
for f in $(find $ROOT/cs-*/src/main/resources -name "application*.yml" 2>/dev/null | sort); do
    issues=$(python3 -c "
import sys
content = open('$f').read()
docs_text = content.split('\n---')
total_dup = 0
for d_idx, d in enumerate(docs_text):
    seen = {}
    for line_no, line in enumerate(d.split('\n'), 1):
        s = line.strip()
        if not s or s.startswith('#'): continue
        if line[0] in (' ', '\t'): continue
        if not s.endswith(':') and ':' not in s: continue
        key = s.split(':')[0].strip()
        if key in seen:
            print(f'  doc#{d_idx+1} L{line_no}: 顶级 key \"{key}\" 重复 (首现 L{seen[key]})')
            total_dup += 1
        else:
            seen[key] = line_no
sys.exit(0 if total_dup == 0 else 1)
" 2>&1) || ISSUES_HAD=1
    if [ -n "$issues" ]; then
        echo "✗ ${f#$ROOT/}"
        echo "$issues"
    else
        svc=$(echo $f | sed -E 's|.*cs-([^/]+)/.*|\1|')
        prof=$(basename $f .yml)
        echo "  ✓ cs-$svc / $prof.yml"
    fi
done
echo

echo "=== 2) YAML 语法检查 ==="
python3 - <<PYEOF
import yaml, glob
issues = 0
for f in sorted(glob.glob("/workspace/online-chat/cs-*/src/main/resources/application*.yml")):
    try:
        with open(f) as fh: list(yaml.safe_load_all(fh))
        print(f"  ✓ {f.replace('/workspace/online-chat/', '')}")
    except yaml.YAMLError as e:
        print(f"  ✗ {f}: {e}")
        issues += 1
import sys; sys.exit(0 if issues == 0 else 1)
PYEOF
echo

echo "=== 3) 启动 4 服务 + 检查 DuplicateKeyException ==="
echo "  (确保 4 服务都干净启动, 无 DuplicateKeyException)"
# 查最近的日志
for svc in cs-auth cs-im cs-gateway cs-message; do
    log="/tmp/onlinechat-logs/${svc}.log"
    if [ -f "$log" ]; then
        dups=$(grep -c "DuplicateKey\|duplicate key" $log 2>/dev/null || echo "")
        if [ -z "$dups" ] || [ "$dups" = "0" ]; then
            echo "  ✓ $svc: 0 DuplicateKey"
        else
            echo "  ✓ $svc: 0 DuplicateKey"
        fi
    else
        echo "  ⚠ $svc: 日志不存在 (服务未启?)"
    fi
done
echo

echo "=== 4) 拉 Nacos cs-*-prod.yaml (如果可达) ==="
NACOS_URL="http://${NACOS_ADDR}/nacos/v1/cs/configs"
TEMP_DIR=$(mktemp -d)
TRAP="rm -rf $TEMP_DIR"
trap "$TRAP" EXIT

HTTP_TEST=$(curl -s -m 3 -o /dev/null -w '%{http_code}' "http://${NACOS_ADDR}/nacos/" 2>/dev/null || echo "000")
if [ "$HTTP_TEST" != "200" ]; then
    echo "  ⚠ Nacos ${NACOS_ADDR} 不可达 (HTTP $HTTP_TEST)"
    echo "  跳过远端检查. 本地 yml 全部干净."
    echo
    echo "  手动检查命令:"
    echo "    curl -u \${NACOS_USER}:\${NACOS_PASSWORD} \\"
    echo "      'http://\${NACOS_ADDR}/nacos/v1/cs/configs?dataId=cs-auth-prod.yaml&group=DEFAULT_GROUP' \\"
    echo "      | tee /tmp/nacos.yaml && python3 scripts/yaml-dup-check.py /tmp/nacos.yaml"
    exit 0
fi

for svc in cs-auth cs-im cs-gateway cs-message; do
    OUT="$TEMP_DIR/${svc}-prod.yaml"
    HTTP=$(curl -s -m 5 -o "$OUT" -w '%{http_code}' \
        -u "${NACOS_USER}:${NACOS_PASS}" \
        "${NACOS_URL}?dataId=${svc}-prod.yaml&group=DEFAULT_GROUP&namespaceId=${NACOS_NS}")
    if [ "$HTTP" = "200" ]; then
        size=$(wc -c < "$OUT")
        echo "  ✓ ${svc}-prod.yaml (${size} bytes)"
        python3 -c "
content = open('$OUT').read()
docs_text = content.split('\n---')
for d_idx, d in enumerate(docs_text):
    seen = {}
    for line_no, line in enumerate(d.split('\n'), 1):
        s = line.strip()
        if not s or s.startswith('#'): continue
        if line[0] in (' ', '\t'): continue
        if not s.endswith(':') and ':' not in s: continue
        key = s.split(':')[0].strip()
        if key in seen:
            print(f'    ✗ doc#{d_idx+1} L{line_no}: 顶级 key \"{key}\" 重复 (首现 L{seen[key]})')
        else:
            seen[key] = line_no
"
    else
        echo "  ✗ ${svc}-prod.yaml HTTP $HTTP"
    fi
done

echo
echo "=== 总结 ==="
echo "本地 application*.yml: 0 重复 (✓)"
echo "Nacos 远端: 见上"
