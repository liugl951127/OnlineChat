#!/usr/bin/env python3
# =====================================================
# 视频回溯端到端模拟脚本 (v2.2.78)
# =====================================================
#
# 模拟场景:
#   1. 客户登录 → 进入客服会话
#   2. 客户发送"我想买理财"
#   3. 坐席介绍产品
#   4. 客户做 KYC
#   5. 创建订单
#   6. 完成支付
#   7. 会话结束 → 触发合成
#   8. 下载回放视频
#
# 用法:
#   python3 scripts/e2e-replay-demo.py
# =====================================================

import base64
import io
import json
import os
import sys
import time
import urllib.error
import urllib.request
from datetime import datetime, timedelta
from typing import Any, Dict, Optional

try:
    from PIL import Image, ImageDraw, ImageFont
    HAS_PIL = True
except ImportError:
    HAS_PIL = False
    print("[WARN] Pillow 未安装, 用纯文本 PNG 占位", file=sys.stderr)


# =====================================================
# 配置
# =====================================================
GATEWAY = os.environ.get("GATEWAY", "http://127.0.0.1:9000")
IM_BACKEND = os.environ.get("IM_BACKEND", f"{GATEWAY}/im")
TIMEOUT = 30
FRAME_INTERVAL_SEC = 3      # 模拟客户端每 3 秒截一帧
NUM_FRAMES = 12             # 模拟会话期间采集帧数 (约 36 秒)


# =====================================================
# 工具
# =====================================================
def http(method: str, url: str, body: Optional[Dict] = None,
         headers: Optional[Dict] = None) -> Dict[str, Any]:
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    if headers:
        for k, v in headers.items():
            req.add_header(k, v)
    try:
        with urllib.request.urlopen(req, timeout=TIMEOUT) as resp:
            text = resp.read().decode()
            return json.loads(text) if text else {}
    except urllib.error.HTTPError as e:
        body = e.read().decode()
        try:
            return json.loads(body)
        except Exception:
            return {"code": e.code, "message": body}
    except Exception as e:
        return {"code": -1, "message": str(e)}


def login(username: str, password: str) -> Optional[str]:
    """登录拿 token"""
    r = http("POST", f"{GATEWAY}/auth/login",
             {"username": username, "password": password})
    if r.get("code") == 0 and r.get("data", {}).get("token"):
        return r["data"]["token"]
    # 备用: customer 登录
    r = http("POST", f"{GATEWAY}/customer/login",
             {"customerId": username, "password": password})
    if r.get("code") == 0 and r.get("data", {}).get("token"):
        return r["data"]["token"]
    print(f"[登录失败] {username}: {r}")
    return None


def active_session(token: str) -> Optional[int]:
    """获取当前活跃会话 id"""
    r = http("GET", f"{IM_BACKEND}/customer/session/active",
             headers={"Authorization": f"Bearer {token}"})
    if r.get("code") == 0 and r.get("data"):
        return r["data"].get("id") or r["data"].get("sessionId")
    return None


def new_session(token: str) -> Optional[int]:
    """开新会话"""
    r = http("POST", f"{IM_BACKEND}/customer/session/new",
             {}, headers={"Authorization": f"Bearer {token}"})
    if r.get("code") == 0 and r.get("data"):
        return r["data"].get("id") or r["data"].get("sessionId")
    return None


def make_screenshot_png(text: str, seq: int, kind: str = "SCREENSHOT") -> str:
    """生成一张 base64 PNG, 模拟客户端截图"""
    if HAS_PIL:
        img = Image.new("RGB", (1280, 720), color=(245, 245, 250))
        draw = ImageDraw.Draw(img)
        try:
            font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 36)
            small = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 18)
        except Exception:
            font = ImageFont.load_default()
            small = ImageFont.load_default()
        # 顶部状态栏
        draw.rectangle([(0, 0), (1280, 60)], fill=(20, 100, 200))
        draw.text((20, 15), f"Customer Service Replay #{seq}", fill=(255, 255, 255), font=font)
        # 时间戳
        now = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        draw.text((1100, 20), now, fill=(255, 255, 255), font=small)
        # 主区域
        draw.rectangle([(40, 100), (1240, 660)], outline=(180, 180, 200), width=2)
        draw.text((60, 120), f"[{kind}]", fill=(100, 100, 100), font=small)
        # 文本换行
        y = 180
        for line in text.split("\n"):
            draw.text((60, y), line, fill=(50, 50, 50), font=font)
            y += 50
        # 底部
        draw.rectangle([(0, 670), (1280, 720)], fill=(40, 40, 40))
        draw.text((20, 685), f"frame seq={seq}  kind={kind}", fill=(255, 255, 255), font=small)
        buf = io.BytesIO()
        img.save(buf, format="PNG")
    else:
        # 占位 1x1 PNG
        png_bytes = base64.b64decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg=="
        )
        buf = io.BytesIO(png_bytes)
    return "data:image/png;base64," + base64.b64encode(buf.getvalue()).decode()


def capture_frame(token: str, session_id: int, seq: int,
                  text: str, kind: str = "SCREENSHOT") -> Optional[Dict]:
    """调用 capture API 上传一帧"""
    png = make_screenshot_png(text, seq, kind)
    r = http("POST", f"{IM_BACKEND}/replay/capture",
             {
                 "sessionId": session_id,
                 "frameKind": kind,
                 "imageData": png,
                 "width": 1280,
                 "height": 720,
                 "durationMs": 3000,
                 "metadata": json.dumps({
                     "text": text[:100],
                     "scrollY": seq * 50,
                     "url": "/customer",
                     "ts": datetime.now().isoformat()
                 })
             },
             headers={"Authorization": f"Bearer {token}"})
    if r.get("code") == 0:
        return r.get("data")
    print(f"  [上传帧 {seq} 失败] {r}")
    return None


def trigger_finish(token: str, session_id: int) -> Optional[Dict]:
    """触发合成"""
    r = http("POST", f"{IM_BACKEND}/replay/{session_id}/finish",
             {}, headers={"Authorization": f"Bearer {token}"})
    if r.get("code") == 0:
        return r.get("data")
    print(f"  [触发合成失败] {r}")
    return None


def poll_job(token: str, session_id: int, timeout_sec: int = 60) -> Optional[Dict]:
    """轮询 job 状态"""
    start = time.time()
    while time.time() - start < timeout_sec:
        r = http("GET", f"{IM_BACKEND}/replay/{session_id}/job",
                 headers={"Authorization": f"Bearer {token}"})
        if r.get("code") == 0 and r.get("data"):
            job = r["data"]
            print(f"  [job] status={job.get('status')} "
                  f"frames={job.get('frameCount')} "
                  f"duration={job.get('durationMs', 0) // 1000}s")
            if job.get("status") == "SUCCESS":
                return job
            if job.get("status") == "FAILED":
                print(f"  [job FAILED] {job.get('errorMessage')}")
                return job
        time.sleep(2)
    return None


def get_replay(token: str, session_id: int) -> Optional[Dict]:
    """获取完整回放数据"""
    r = http("GET", f"{IM_BACKEND}/replay/{session_id}",
             headers={"Authorization": f"Bearer {token}"})
    if r.get("code") == 0:
        return r.get("data")
    return None


# =====================================================
# 主流程
# =====================================================
def main():
    print("=" * 60)
    print(" 视频回溯端到端模拟 (v2.2.78)")
    print("=" * 60)

    # 1. 登录
    print("\n[1/8] 客户登录 (customer001)")
    cust_token = login("customer001", "pass123")
    if not cust_token:
        print("[FAIL] 客户登录失败, 退出")
        sys.exit(1)
    print(f"  ✓ 客户 token: {cust_token[:20]}...")

    print("\n[2/8] 坐席登录 (agent001)")
    agent_token = login("agent001", "pass123")
    if not agent_token:
        print("[FAIL] 坐席登录失败, 退出")
        sys.exit(1)
    print(f"  ✓ 坐席 token: {agent_token[:20]}...")

    # 2. 开新会话
    print("\n[3/8] 开新会话")
    session_id = active_session(cust_token) or new_session(cust_token)
    if not session_id:
        print("[FAIL] 无法获取 session id, 退出")
        sys.exit(1)
    print(f"  ✓ session_id = {session_id}")

    # 3. 模拟交易对话 + 帧采集
    print(f"\n[4/8] 模拟交易对话 + 定时帧采集 ({NUM_FRAMES} 帧, 间隔 {FRAME_INTERVAL_SEC}s)")
    chat_log = [
        ("CUSTOMER", "你好, 我想咨询理财产品"),
        ("AGENT", "您好, 我是 AI 助手, 请问您对哪类理财感兴趣?"),
        ("CUSTOMER", "稳健型的, 预期年化 3-5%"),
        ("AGENT", "推荐我们银行的【稳健宝】产品, 年化 3.8%, 起购 1000 元"),
        ("CUSTOMER", "需要做什么?"),
        ("AGENT", "1. KYC 实名认证  2. 风险评估  3. 下单"),
        ("CUSTOMER", "开始 KYC"),
        ("AGENT", "请提供身份证号和姓名"),
        ("CUSTOMER", "张三  110101199001011234"),
        ("AGENT", "KYC 通过! 请做风险评估"),
        ("CUSTOMER", "勾选稳健型"),
        ("AGENT", "评估通过, 创建订单: 稳健宝 10000 元")
    ]

    start_time = datetime.now()
    for i in range(NUM_FRAMES):
        # 模拟当前帧 (时间分配到对话)
        if i < len(chat_log):
            role, text = chat_log[i]
        else:
            text = f"会话进行中... 第 {i+1} 帧"
            role = "CUSTOMER"
        kind = "MESSAGE" if i % 2 == 1 else "SCREENSHOT"
        capture_frame(cust_token, session_id, i, f"{role}: {text}", kind)
        print(f"  帧 {i+1:2d}/{NUM_FRAMES}: [{kind}] {role}: {text[:30]}{'...' if len(text) > 30 else ''}")
        if i < NUM_FRAMES - 1:
            time.sleep(FRAME_INTERVAL_SEC)
    elapsed = (datetime.now() - start_time).total_seconds()
    print(f"  ✓ 帧采集完成, 用时 {elapsed:.0f}s")

    # 4. 触发合成
    print("\n[5/8] 触发 ffmpeg 视频合成")
    job = trigger_finish(cust_token, session_id)
    if not job:
        print("[FAIL] 触发合成失败")
        sys.exit(1)
    print(f"  ✓ job_id = {job.get('jobId')}, frames = {job.get('frameCount')}")

    # 5. 轮询
    print("\n[6/8] 轮询合成状态 (最多 60 秒)")
    final = poll_job(cust_token, session_id, timeout_sec=60)
    if not final or final.get("status") != "SUCCESS":
        print(f"[FAIL] 合成未成功: {final}")
        sys.exit(1)
    print(f"  ✓ 合成完成! video_url = {final.get('videoUrl')}")

    # 6. 验证视频可下载
    print("\n[7/8] 验证视频可访问")
    if final.get("videoUrl"):
        try:
            with urllib.request.urlopen(final["videoUrl"], timeout=10) as resp:
                size = len(resp.read())
                print(f"  ✓ 视频下载成功, 大小 {size} bytes")
        except Exception as e:
            print(f"  [WARN] 视频下载失败: {e}")

    # 7. 拉取完整 replay 数据 (含 timeline)
    print("\n[8/8] 拉取完整回放数据")
    replay = get_replay(cust_token, session_id)
    if replay:
        print(f"  ✓ 消息帧数: {replay.get('frameCount')}")
        print(f"  ✓ 截图帧数: {replay.get('replayFrameCount')}")
        print(f"  ✓ 时间线项数: {len(replay.get('timeline', []))}")
        print(f"  ✓ video_url: {replay.get('videoUrl')}")
        # 输出前 5 帧预览
        for t in replay.get("timeline", [])[:5]:
            print(f"    - seq={t['seq']:2d}  {t['frameKind']:12s}  @ +{t['offsetMs'] // 1000}s  "
                  f"msg#{t.get('messageId')}")

    print("\n" + "=" * 60)
    print(" ✅ E2E 视频回溯测试完成")
    print("=" * 60)
    print(f" session_id = {session_id}")
    print(f" video_url  = {final.get('videoUrl')}")
    print(" 在前端用 ReplayPanel 组件打开 /customer?replayId=" + str(session_id) + " 可查看")


if __name__ == "__main__":
    main()