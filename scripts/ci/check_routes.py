#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""OpenAPI 目录与真实控制器路由 diff（P12-5）。

静态扫描 backend/*/src/main/java/**/controller/*.java 的 @RestController +
@RequestMapping/@XxxMapping，与 docs/05-design/p0-5-openapi-catalog.md 的
operation 表比对。

约定：
- 路径变量名不参与比较：`/x/{id}` 与 `/x/{service}` 都归一化为 `/x/{}`；
- `*/ping` 是探活端点，不进入业务 OpenAPI 目录，扫描时忽略；
- 内部接口登记在目录 §16，同样参与比对。

用法：
    python3 scripts/ci/check_routes.py            # 有差异返回 1
    python3 scripts/ci/check_routes.py --report   # 仅打印差异，始终返回 0
"""
import io
import os
import re
import sys

CATALOG = "docs/05-design/p0-5-openapi-catalog.md"
CONTROLLER_ROOT = "backend"

CLASS_MAPPING = re.compile(r'@RequestMapping\("([^"]*)"\)')
METHOD_MAPPING = re.compile(r'@(Get|Post|Put|Delete|Patch)Mapping(?:\s*\(([^()]*)\))?')
ANY_STRING = re.compile(r'"([^"]*)"')
CATALOG_ROW = re.compile(
    r"^\| `([a-z0-9_]+)` \| (GET|POST|PUT|DELETE|PATCH) \| ([^|]+) \|")
PATH_VARIABLE = re.compile(r"\{[^}]*\}")


def normalize(path):
    """归一化路径变量名与尾部斜杠。"""
    normalized = PATH_VARIABLE.sub("{}", path.strip())
    if len(normalized) > 1:
        normalized = normalized.rstrip("/")
    return normalized or "/"


def is_probe(path):
    """探活端点不进入业务目录。"""
    return normalize(path).endswith("/ping")


def actual_routes():
    routes = {}
    for root, _, files in os.walk(CONTROLLER_ROOT):
        if os.sep + "test" + os.sep in root:
            continue
        for name in files:
            if not name.endswith(".java"):
                continue
            path = os.path.join(root, name)
            text = io.open(path, encoding="utf-8").read()
            if "@RestController" not in text:
                continue
            class_match = CLASS_MAPPING.search(text)
            base = class_match.group(1) if class_match else ""
            base = base.rstrip("/")
            for match in METHOD_MAPPING.finditer(text):
                method = match.group(1).upper()
                args = match.group(2) or ""
                strings = ANY_STRING.findall(args)
                sub = strings[0] if strings else ""
                if sub and not sub.startswith("/"):
                    sub = "/" + sub
                full = (base + sub) or "/"
                if is_probe(full):
                    continue
                routes.setdefault((method, normalize(full)), []).append(
                    path.replace("\\", "/"))
    return routes


def catalog_routes():
    routes = {}
    text = io.open(CATALOG, encoding="utf-8-sig").read()
    for line in text.splitlines():
        match = CATALOG_ROW.match(line)
        if not match:
            continue
        operation_id, method, raw_paths = match.groups()
        raw_paths = raw_paths.replace("`", "")
        for path in re.split(r"[、,\s]+", raw_paths.strip()):
            if not path.startswith("/"):
                continue
            routes[(method, normalize(path))] = operation_id
    return routes


def main():
    report_only = "--report" in sys.argv[1:]
    actual = actual_routes()
    catalog = catalog_routes()

    missing_docs = sorted(set(actual) - set(catalog))
    stale_docs = sorted(set(catalog) - set(actual))

    print("控制器路由 %d 条，目录登记 %d 条" % (len(actual), len(catalog)))
    if missing_docs:
        print("\n[控制器有、目录缺] %d 条" % len(missing_docs))
        for method, path in missing_docs:
            print("  %-6s %-60s %s" % (method, path, actual[(method, path)][0]))
    if stale_docs:
        print("\n[目录有、控制器缺] %d 条" % len(stale_docs))
        for method, path in stale_docs:
            print("  %-6s %-60s %s" % (method, path, catalog[(method, path)]))
    if not missing_docs and not stale_docs:
        print("路由与目录一致")
        return 0
    return 0 if report_only else 1


if __name__ == "__main__":
    sys.exit(main())
