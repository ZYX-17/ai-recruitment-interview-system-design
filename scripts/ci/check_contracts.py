#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""校验 P0-5 冻结产物：OpenAPI 目录与权限码注册表。

只做静态一致性检查，不依赖第三方库，可在 CI 与本地直接运行：
    python3 scripts/ci/check_contracts.py
失败时返回非 0，并打印逐条问题。
"""
import csv
import io
import re
import sys

CATALOG = "docs/05-design/p0-5-openapi-catalog.md"
REGISTRY_CSV = "docs/05-design/p0-5-permission-registry.csv"
REGISTRY_JSON = "docs/05-design/p0-5-permission-registry.json"

ROLES = ["SUPER_ADMIN", "HR_ADMIN", "RECRUITER", "INTERVIEWER", "CANDIDATE"]
TYPES = {"MENU", "BUTTON"}
PUBLIC_PERMISSION_CELLS = ("公开", "登录", "按 action")

errors = []


def fail(msg):
    errors.append(msg)


def read(path):
    return io.open(path, encoding="utf-8-sig").read()


def check_catalog():
    text = read(CATALOG)
    lines = text.splitlines()

    current_tag = None
    tag_counts = {}
    operations = []
    seen = set()

    for line in lines:
        m = re.match(r"^## \d+\. ([a-z-]+)（tag: ([a-z-]+)", line)
        if m:
            current_tag = m.group(2)
            tag_counts.setdefault(current_tag, 0)
            continue

        m = re.match(r"^\| `([a-z0-9_]+)` \| (GET|POST|PUT|DELETE) \| ([^|]+) \| ([^|]*) \|", line)
        if not m:
            continue

        operation_id, method, path, permission = (x.strip() for x in m.groups())
        path = path.replace("`", "").strip()
        operations.append(operation_id)

        if operation_id in seen:
            fail("catalog: operationId 重复: %s" % operation_id)
        seen.add(operation_id)

        if not re.match(r"^(auth|admin|interview|notify|portal|internal)_[a-z0-9_]+$", operation_id):
            fail("catalog: operationId 不符合 {domain}_{resource}_{action}: %s" % operation_id)

        if not path.startswith("/"):
            fail("catalog: %s 的 path 非法: %s" % (operation_id, path))

        if not permission:
            fail("catalog: %s 缺少权限列" % operation_id)

        if current_tag:
            tag_counts[current_tag] += 1

    stats = {}
    in_stats = False
    for line in lines:
        if line.startswith("## 3."):
            in_stats = True
            continue
        if in_stats and line.startswith("## "):
            break
        if not in_stats:
            continue
        m = re.match(r"^\| ([a-z-]+)[^|]*\| (\d+) \|$", line)
        if m:
            stats[m.group(1)] = int(m.group(2))

    for tag, count in tag_counts.items():
        if tag == "internal":
            continue
        if stats.get(tag) != count:
            fail("catalog: tag %s 统计为 %s，实际 %d" % (tag, stats.get(tag), count))

    public_count = sum(v for k, v in tag_counts.items() if k != "internal")
    total = sum(stats.values())
    if total != public_count:
        fail("catalog: 统计合计 %d，实际公开 operation %d" % (total, public_count))

    codes = set(re.findall(r"`((?:system|interview|portal):[a-z0-9:-]+)`", text))
    return public_count, codes


def check_registry():
    rows = list(csv.DictReader(io.open(REGISTRY_CSV, encoding="utf-8-sig")))
    if not rows:
        fail("registry: CSV 为空")
        return {}, set()

    required = {"code", "type", "parent_code", "module", "name", "api_pattern", "sort"}
    missing = required - set(rows[0].keys())
    if missing:
        fail("registry: CSV 缺少列 %s" % sorted(missing))

    codes = {}
    for row in rows:
        code = row["code"].strip()
        if not code:
            fail("registry: 存在空 code")
            continue
        if code in codes:
            fail("registry: code 重复: %s" % code)
        codes[code] = row

        if row["type"] not in TYPES:
            fail("registry: %s 的 type 非法: %s" % (code, row["type"]))
        if row["type"] == "BUTTON" and not row["api_pattern"].strip():
            fail("registry: BUTTON %s 缺少 api_pattern" % code)
        if row["type"] == "MENU" and row["api_pattern"].strip():
            fail("registry: MENU %s 不应带 api_pattern" % code)
        for role in ROLES:
            value = row.get("role_" + role.lower(), "")
            if value not in ("Y", "N"):
                fail("registry: %s 的 role_%s 取值非法: %s" % (code, role.lower(), value))

    try:
        import json
        data = json.load(io.open(REGISTRY_JSON, encoding="utf-8"))
        if len(data.get("permissions", [])) != len(rows):
            fail("registry: JSON 权限数 %d 与 CSV %d 不一致" % (len(data["permissions"]), len(rows)))
        groups = {g["code"] for g in data.get("groups", [])}
        for row in rows:
            if row["parent_code"] and row["parent_code"] not in groups:
                fail("registry: %s 的 parent_code %s 不在分组表" % (row["code"], row["parent_code"]))
        if sorted(data.get("roles", [])) != sorted(ROLES):
            fail("registry: JSON 角色集合与约定不一致")
    except FileNotFoundError:
        fail("registry: 缺少 %s" % REGISTRY_JSON)
    except ValueError as exc:
        fail("registry: JSON 解析失败: %s" % exc)

    return codes, set(codes)


def check_cross(public_count, catalog_codes, registry_codes):
    unknown = sorted(catalog_codes - registry_codes)
    if unknown:
        fail("cross: catalog 引用了未登记权限码: %s" % ", ".join(unknown))

    text = read(CATALOG)
    for line in text.splitlines():
        m = re.match(r"^\| `([a-z0-9_]+)` \| (?:GET|POST|PUT|DELETE) \| [^|]+ \| ([^|]*) \|", line)
        if not m:
            continue
        operation_id, permission = m.group(1), m.group(2)
        if operation_id.startswith("internal_"):
            continue
        if permission.strip().startswith("`"):
            continue
        if permission.strip().startswith("按"):
            continue
        if any(token in permission for token in PUBLIC_PERMISSION_CELLS):
            continue
        fail("cross: %s 的权限列既不是权限码也不是公开/登录/按 action: %s" % (operation_id, permission.strip()))


def main():
    public_count, catalog_codes = check_catalog()
    _, registry_codes = check_registry()
    check_cross(public_count, catalog_codes, registry_codes)

    if errors:
        print("契约校验失败（%d 项）：" % len(errors))
        for err in errors:
            print("  - " + err)
        return 1

    print("契约校验通过：公开 operation %d 个，权限码 %d 个。" % (public_count, len(registry_codes)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
