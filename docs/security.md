# 权限与安全说明

## 信任边界

客户端提交的 userId、ownerId、creatorId 均不可信。后端从经过签名和有效期验证的 JWT subject 读取当前用户 ID。密码使用 BCrypt cost 12 哈希。

## 权限矩阵

| 对象/动作 | 创建者 | 关系空间成员 | 其他已登录用户 |
|---|---:|---:|---:|
| PRIVATE Memory 读取 | 允许 | 拒绝 | 拒绝 |
| RELATIONSHIP Memory 读取 | 允许 | 关联空间成员允许 | 拒绝 |
| PUBLIC Memory 读取 | 允许 | 允许 | 允许 |
| Memory 编辑/删除 | 允许 | 拒绝 | 拒绝 |
| ACTIVE 空间写入 | 成员允许 | 成员允许 | 拒绝 |
| ARCHIVED 空间写入 | 拒绝 | 拒绝 | 拒绝 |
| 关系邀请响应 | 接收者 | 非接收者拒绝 | 拒绝 |
| 私有媒体读取 | 拥有者允许 | 对挂载 Memory 有读取权时允许 | 拒绝 |

## 文件防护

- 单文件 30MB、单请求 100MB。
- 允许 JPEG、PNG、GIF、WebP、MP4、WebM。
- 使用文件魔数判断真实类型，不采信请求 MIME 或原文件扩展名。
- 对象键由后端生成 UUID；原文件名只作为展示元数据。
- 本地路径经过 `normalize`，且必须仍在配置根目录下。
- MinIO 始终保持私有 bucket。浏览器只请求同源 `/api/files/{id}/content`；后端先执行 Memory 权限判断，再从 MinIO 流式返回真实媒体字节。
- 不再把 presigned URL 重定向给浏览器，也不再依赖 `MINIO_PUBLIC_ENDPOINT`。这避免主机地址、跨域重定向和浏览器 CORS 配置导致合法媒体无法显示，同时不会放宽私有对象权限。

图片压缩和缩略图字段已在模型中预留；V2 应把转码放入异步任务并删除 EXIF 中的敏感位置信息（除非用户明确保留）。

## 默认密钥

`.env.example` 的值仅面向本机。生产环境应使用密钥管理服务或部署平台 secret，关闭演示数据，将 CORS 限定为真实域名，并轮换 JWT/数据库/Redis/MinIO 密钥。不要把 `.env` 提交到版本库。

## 自动测试

`CoreJourneyIntegrationTest` 与 `MediaAccessIntegrationTest` 验证：

1. B 无法读取 A 的 PRIVATE Memory，返回 403。
2. 只有邀请接收者接受后才生成共同空间。
3. 关系成员可以读取共同 Memory 并评论。
4. 公开后 Feed 可见。
5. 封存关系后继续向空间写入会返回 403。
6. PUBLIC 图片可由其他已登录用户读取，PRIVATE 图片仅所有者可读。
7. RELATIONSHIP 图片仅创建者和对应关系空间成员可读，第三人返回 403。
8. 媒体响应是原始 `image/*` 字节，并在列表和空间时间轴返回真实封面文件 ID。

生产上线前还应增加恶意文件、多 Token 过期、所有权伪造、并发接受邀请、SQL 注入、CORS 与限流测试。
