# 数据库脚本

数据库结构由 Flyway 管理，迁移文件位于 `backend/src/main/resources/db/migration`。

- `V1__baseline.sql`：当前完整结构，用于全新数据库。
- 后续 `V2/V3...`：按版本顺序升级已有数据库。

已有 MemoSpace 数据库第一次升级时会自动建立 `flyway_schema_history` 并标记为版本 1，再执行后续迁移；不会重建 MySQL 数据卷。生产部署前仍建议备份数据库。迁移一旦进入共享环境不得修改，新的结构变化必须创建更高版本文件。

演示账号和示例内容由后端 `DemoDataInitializer` 首次启动时创建，密码经过 BCrypt 哈希，不在 SQL 中保存明文。
