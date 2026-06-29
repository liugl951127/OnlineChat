#!/usr/bin/env bash
# =====================================================
# OnlineChat 国密 SM2 密钥对生成 (v2.2.97)
# =====================================================
#
# 用途:
#   - 生成 SM2 椭圆曲线密钥对 (256 bit)
#   - 公钥给前端 SDK (browser 永远拿不到私钥)
#   - 私钥存 cs-im 服务配置目录
#
# 标准:
#   GB/T 32918.1-2016 SM2 (替代 RSA)
#   GB/T 32907-2016 SM4 (对称)
#   GB/T 32905-2016 SM3 (哈希)
#
# 用法:
#   bash scripts/gen-monitor-keypair.sh                     # 生成到 ~/.cs/cs-monitor.{pem,pub}
#   bash scripts/gen-monitor-keypair.sh --out /etc/cs       # 指定输出目录
#
# 依赖:
#   - openssl 3.x (cn 国产分支支持 SM2, debian 系需 apt install openssl + tass-smd)

set -e

OUT_DIR="${HOME}/.cs"
PRIV="${OUT_DIR}/cs-monitor.priv.pem"
PUB="${OUT_DIR}/cs-monitor.pub.pem"

while [ $# -gt 0 ]; do
    case "$1" in
        --out)
            OUT_DIR="$2"
            PRIV="${OUT_DIR}/cs-monitor.priv.pem"
            PUB="${OUT_DIR}/cs-monitor.pub.pem"
            shift 2
            ;;
        *)
            echo "用法: $0 [--out DIR]"
            exit 1
            ;;
    esac
done

mkdir -p "${OUT_DIR}"

# 检查 openssl 是否支持 SM2
if ! openssl ecparam -list_curves 2>/dev/null | grep -qi "sm2"; then
    echo "⚠️  OpenSSL 不支持 SM2 曲线"
    echo "安装方法:"
    echo "  - macOS: brew install openssl"
    echo "  - debian/ubuntu: apt install openssl tass-smd (TASS-SMD 国产扩展)"
    echo "  - 或者用 Java Bouncy Castle 生成 (推荐):"
    echo "    java -cp bcprov-jdk18on.jar org.bouncycastle.openssl.jcajce.JcaPEMWriter ..."
    echo
    echo "本脚本备用: 用 Java 生成 SM2 密钥对"
    if [ -z "${JAVA_HOME}" ]; then
        JAVA_BIN=java
    else
        JAVA_BIN="${JAVA_HOME}/bin/java"
    fi

    if ! command -v ${JAVA_BIN} >/dev/null 2>&1; then
        echo "✗ 没装 Java, 请装 OpenSSL (国密版) 或 Java 17+"
        exit 1
    fi

    echo "尝试用 Java Bouncy Castle 生成..."

    # 找 bcprov jar
    BC_JAR=""
    for jar in $(find ~/.m2/repository -name "bcprov-jdk18on-*.jar" 2>/dev/null | head -1); do
        BC_JAR="$jar"
    done
    if [ -z "${BC_JAR}" ]; then
        echo "下载 bcprov-jdk18on..."
        mkdir -p /tmp/bc
        curl -sL -o /tmp/bc/bcprov.jar https://repo1.maven.org/maven2/org/bouncycastle/bcprov-jdk18on/1.78/bcprov-jdk18on-1.78.jar
        BC_JAR=/tmp/bc/bcprov.jar
    fi

    cat > /tmp/GenSM2.java <<'JAVA'
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemWriter;
import org.bouncycastle.crypto.generators.ECKeyPairGenerator;
import org.bouncycastle.crypto.params.ECKeyGenerationParameters;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.asn1.x9.ECNamedCurveTable;
import org.bouncycastle.asn1.gm.GMNamedCurves;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.openssl.PKCS8Generator;
import org.bouncycastle.openssl.jcajce.JcaPKCS8Generator;
import org.bouncycastle.openssl.bc.BcPKCS8PEMGenerator;
import org.bouncycastle.operator.OutputType;
import java.io.FileWriter;
import java.io.FileOutputStream;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Security;
import java.security.spec.ECGenParameterSpec;

public class GenSM2 {
    public static void main(String[] args) throws Exception {
        Security.addProvider(new BouncyCastleProvider());
        String priv = args[0];
        String pub = args[1];

        KeyPairGenerator g = KeyPairGenerator.getInstance("EC", "BC");
        g.initialize(new ECGenParameterSpec("sm2p256v1"), new SecureRandom());
        KeyPair kp = g.generateKeyPair();

        // 私钥 (PKCS#8 / PEM)
        JcaPKCS8Generator pkcs8 = new JcaPKCS8Generator(kp.getPrivate(), null);
        try (FileWriter w = new FileWriter(priv)) {
            PemWriter p = new PemWriter(w);
            p.writeObject(pkcs8.generate());
            p.close();
        }

        // 公钥 (X.509 SubjectPublicKeyInfo / PEM)
        try (FileWriter w = new FileWriter(pub)) {
            JcaPEMWriter p = new JcaPEMWriter(w);
            p.writeObject(kp.getPublic());
            p.close();
        }

        System.out.println("SM2 KEYPAIR GENERATED");
        System.out.println("  curve: sm2p256v1 (256 bit, GB/T 32918)");
        System.out.println("  priv: " + priv);
        System.out.println("  pub:  " + pub);
        System.out.println("  pub preview: " + kp.getPublic().toString().substring(0, 80) + "...");
    }
}
JAVA

    cd /tmp
    ${JAVA_BIN} -cp "${BC_JAR}" GenSM2.java "${PRIV}" "${PUB}"
    echo "✓ Java 生成 SM2 密钥对成功"
    echo
else
    echo "OpenSSL 支持 SM2, 直接生成"
    openssl ecparam -genkey -name sm2 -out "${PRIV}" 2>/dev/null || {
        echo "openssl sm2 genkey 失败, 试 -conv_form"
        openssl ecparam -name sm2 -genkey -conv_form compressed -out "${PRIV}"
    }
    openssl ec -in "${PRIV}" -pubout -out "${PUB}"
    echo "✓ OpenSSL 生成 SM2 密钥对成功"
fi

echo
echo "=== 文件 ==="
ls -lh "${PRIV}" "${PUB}"
echo
echo "=== 公钥 preview (前端 SDK 用) ==="
head -3 "${PUB}"
echo "..."
echo
echo "=== ⚠️ 安全提示 ==="
echo "  - 私钥: ${PRIV} — 仅 cs-im 服务进程需要读 (mode 600)"
echo "  - 公钥: ${PUB} — 可发给前端 SDK 嵌入 / 通过 /api/monitor/pubkey 返回"
echo "  - cs-im 配置: cs.monitor.sm2-priv-pem-path=${PRIV}"
echo "  - 建议生产用 KMS (阿里云 KMS / HashiCorp Vault) 托管私钥, 此脚本仅生成本地密钥"
chmod 600 "${PRIV}"
chmod 644 "${PUB}"
