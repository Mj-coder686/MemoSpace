# 架构与演进

## 模块边界

```text
API Controllers
├── Auth / User
├── Relationship / Space
├── Memory / Event
├── Social / Notification
└── File
        ↓
Application Services
├── PermissionService（统一授权）
├── MemoryService（唯一主体 + 多空间投影）
├── RelationshipService（事务状态机）
├── FeedCacheService（Redis 可降级缓存）
└── FileStorageService（Local / MinIO 策略）
        ↓
MyBatis-Plus + JdbcTemplate → MySQL
```

关系接受是一个事务收敛点：锁定邀请、验证接收者/状态/时效、创建 relationship、写入双方成员、选择关系主题、创建 space、写入空间成员、更新邀请、生成通知。任一步失败都整体回滚。

## Design Tokens

全局使用 `--ink`、`--paper`、`--surface`、`--accent`、`--radius` 等变量。每个空间从数据库主题映射为 `--space-primary`、`--space-background`、`--space-text`、`--space-muted`。情侣、朋友、家人和个人空间因此复用布局，只替换设计令牌。

## V2 演进顺序

1. 媒体直传 MinIO 和异步缩略图/转码。
2. WebSocket 通知，用 Redis Pub/Sub 支持多实例。
3. Feed 游标分页与索引；缓存命中率观测。
4. 地图接入合规瓦片供应商和聚合点。
5. 年度回忆生成，统计结果与原始 Memory 解耦。

## V3 AI 边界

AI 服务只能读取经过权限过滤的结构化副本，输出总结、候选标签或推荐。所有标签需用户确认，AI 永远没有修改/删除原始 Memory 的数据库权限。
