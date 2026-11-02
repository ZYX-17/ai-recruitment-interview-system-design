## 变更说明

- 任务号：<!-- 例：P0-6 / P5-3 -->
- 一句话说明：
- 影响服务：
- 关联契约：<!-- OpenAPI catalog / EventTypes / Flyway / DDL -->

## 变更类型

- [ ] 功能
- [ ] 修复
- [ ] 文档 / 契约
- [ ] 构建 / 部署
- [ ] 重构（无行为变化）

## 自检（DoD，docs/4 §13.1）

- [ ] 接口 / 事件契约已更新（`docs/05-design/p0-5-openapi-catalog.md`、`EventTypes`、payload）
- [ ] 权限码已登记（`docs/05-design/p0-5-permission-registry.csv`）并说明数据范围
- [ ] 成功 / 失败 / 超时 / 重试路径已处理
- [ ] 日志带 `traceId`；关键操作写审计
- [ ] 至少 1 个单测 + 1 个集成 / 接口用例
- [ ] 无硬编码 URL / 密钥；`.env.example` 已更新
- [ ] 文档 / ADR 已同步

## 验证方式

```bash
# 贴实际执行的命令与结果
mvn -B -ntp -pl <module> -am test
python3 scripts/ci/check_contracts.py
```

## 风险与回滚

- 风险：
- 回滚：

## Reviewer 关注点

- [ ] 契约兼容性（字段只增不改，删除 / 改语义升 `eventVersion`）
- [ ] 数据范围与越权（`@DataScope` / `@OwnerCheck`）
- [ ] 事务边界与幂等（`Idempotency-Key`、`version`、唯一索引兜底）