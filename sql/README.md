# 数据库脚本

正式环境的建表脚本位于 `backend/src/main/resources/schema.sql`，Docker Compose 会把它挂载到 MySQL 的初始化目录。脚本采用幂等的 `CREATE TABLE IF NOT EXISTS`，本地 H2 开发库与 MySQL 8 共用同一份结构。

演示账号和示例内容由后端 `DemoDataInitializer` 首次启动时创建，密码经过 BCrypt 哈希，不在 SQL 中保存明文。
