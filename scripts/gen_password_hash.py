#!/usr/bin/env python3
"""
生成 PBKDF2-HMAC-SHA256 密码 hash 给 db_seed.sql 用
每个用户独立 salt，输出可粘贴到 SQL。
"""
import hashlib
import os

def pbkdf2_hash(password: str, iterations: int = 100_000) -> str:
    salt = os.urandom(16)
    hash_bytes = hashlib.pbkdf2_hmac('sha256', password.encode('utf-8'), salt, iterations, dklen=32)
    return f"pbkdf2${iterations}${salt.hex()}${hash_bytes.hex()}"

# 统一密码
PASSWORD = "pass123"
ADMIN_PASSWORD = "admin123"

# 11 个用户
users = [
    'c-demo', 'c-customer001', 'c-customer002', 'c-customer003', 'c-customer004',
    'a-agent001', 'a-agent002', 'a-agent003', 'a-agent004', 'a-supervisor'
]

print(f"-- 客户/坐席统一密码: {PASSWORD}")
print(f"-- 管理员密码: {ADMIN_PASSWORD}")
print()
print("-- 每个用户的 password_hash 单独生成 (PBKDF2-HMAC-SHA256):")
print()
for u in users:
    h = pbkdf2_hash(PASSWORD)
    print(f"-- {u}")
    print(f"  password_hash: {h}")