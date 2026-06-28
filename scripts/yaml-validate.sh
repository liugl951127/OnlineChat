#!/usr/bin/env bash
# =====================================================
# YAML 语法 + Nacos 配置完整性验证 (v2.2.65)
# =====================================================
#
# 检查项:
#   1. 4 个 application.yml YAML 语法正确
#   2. spring.cloud.nacos.discovery 必须字段 (5 个)
#   3. spring.cloud.nacos.config 必须字段 (5 个)
#   4. spring.config.import 必须存在
#
# 用法:
#   bash scripts/yaml-validate.sh
# =====================================================

set -e
cd "$(dirname "$0")/.."

PASS=0
FAIL=0
ok()   { echo -e "  \033[32m✓\033[0m $1"; PASS=$((PASS+1)); }
fail() { echo -e "  \033[31m✗\033[0m $1"; FAIL=$((FAIL+1)); }

echo "============================================================"
echo " v2.2.65 YAML 语法 + Nacos 配置验证"
echo "============================================================"

for svc in cs-gateway cs-auth cs-im cs-message; do
    echo
    echo "[$svc]"
    YAML_FILE="$svc/src/main/resources/application.yml"
    
    if [ ! -f "$YAML_FILE" ]; then
        fail "文件不存在: $YAML_FILE"
        continue
    fi
    
    # Python yaml 验证
    RESULT=$(python3 << PYEOF
import yaml, sys

with open("$YAML_FILE") as f:
    content = f.read()

docs = list(yaml.safe_load_all(content))
data = docs[0] if docs else {}

if not data:
    print("EMPTY")
    sys.exit(1)

nacos = data.get('spring', {}).get('cloud', {}).get('nacos', {})
discovery = nacos.get('discovery', {})
config = nacos.get('config', {})

errors = []

# discovery 字段
for key in ['enabled', 'server-addr', 'namespace', 'username', 'password']:
    if key not in discovery:
        errors.append(f"discovery.{key} missing")

# config 字段
for key in ['server-addr', 'namespace', 'username', 'password', 'file-extension']:
    if key not in config:
        errors.append(f"config.{key} missing")

# import 字段
spring_config = data.get('spring', {}).get('config', {})
imp = spring_config.get('import', '')
if not imp:
    errors.append("spring.config.import missing")
elif 'nacos:' not in imp:
    errors.append(f"spring.config.import should contain 'nacos:': {imp}")

if errors:
    print("ERRORS:" + ";".join(errors))
else:
    print(f"OK:{len(docs)}_docs,import={imp}")
PYEOF
)
    
    if [[ "$RESULT" == OK:* ]]; then
        ok "$svc YAML 语法 + nacos 配置完整 ($RESULT)"
    else
        fail "$svc $RESULT"
    fi
done

echo
echo "============================================================"
echo -e " \033[32mPASS: $PASS\033[0m   \033[31mFAIL: $FAIL\033[0m"
echo "============================================================"

if [ $FAIL -eq 0 ]; then
    echo
    echo "🎉 4 服务 application.yml 全部健康!"
    echo
    echo "下一步: 编译运行 + 推送 nacos 远程配置"
    echo "  Data ID: cs-gateway-prod.yaml"
    echo "  Data ID: cs-auth-prod.yaml"
    echo "  Data ID: cs-im-prod.yaml"
    echo "  Data ID: cs-message-prod.yaml"
fi

exit $FAIL