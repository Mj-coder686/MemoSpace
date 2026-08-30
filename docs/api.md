# API 摘要

启动后完整 OpenAPI 文档位于 `/swagger-ui.html`。除注册登录外，请求需带 `Authorization: Bearer <token>`。普通用户 Token 与管理员 Token 不能混用。

## 账号与用户

| 方法 | 地址 | 说明 |
|---|---|---|
| POST | `/api/auth/register` | 注册并自动创建个人空间 |
| POST | `/api/auth/login` | 获取 JWT |
| GET/PUT | `/api/users/me` | 当前资料读取/修改 |
| PUT | `/api/users/me/password` | 修改密码 |
| GET | `/api/users/search?q=` | 用户名/昵称搜索 |
| GET | `/api/users/{id}` | 用户主页 |
| POST | `/api/users/{id}/follow` | 关注/取消关注 |

## 管理员

管理员使用独立入口 `/admin/login` 和独立 Token。管理员账号不会出现在普通用户搜索、主页、关注、好友或关系邀请中。

| 方法 | 地址 | 说明 |
|---|---|---|
| POST | `/api/admin/auth/login` | 管理员登录，仅数据库 `is_admin=true` 的账号可用 |
| GET | `/api/admin/me` | 当前管理员资料 |
| GET | `/api/admin/users?keyword=&page=&size=` | 最小化账号目录，不返回用户内容 |
| PUT | `/api/admin/users/{id}/password` | 重置临时密码并写入审计记录 |
| PUT | `/api/admin/users/{id}/memo-id` | 修改唯一的 12 位纯数字 Memo ID |
| GET | `/api/admin/audit` | 最近管理员操作记录 |

管理员 Token 对 `/api/memories/**`、`/api/files/**`、`/api/spaces/**`、聊天、提醒、通知等普通产品接口统一返回 403。

## 关系与空间

| 方法 | 地址 | 说明 |
|---|---|---|
| GET | `/api/relationship-categories?includeHidden=true` | 当前用户的关系分类与人数 |
| POST | `/api/relationship-categories` | 创建自定义分类 |
| GET | `/api/relationship-categories/{id}` | 分类详情与该分类绑定用户 |
| PUT | `/api/relationship-categories/{id}/visibility` | 隐藏或恢复分类，不删除数据 |
| PUT | `/api/relationship-categories/reorder` | 保存分类排序 |
| GET | `/api/relationships` | 关系列表、唯一空间与当前用户标签 |
| GET/POST | `/api/relationships/invitations` | 邀请列表/按 `categoryId` 发起邀请 |
| POST | `/api/relationships/invitations/{id}/accept` | 接受并创建或复用双方唯一共同空间 |
| POST | `/api/relationships/invitations/{id}/reject` | 拒绝 |
| PUT | `/api/relationships/{id}/categories` | 为同一关系替换多个分类标签 |
| DELETE | `/api/relationships/{id}` | 封存关系和空间 |
| GET | `/api/spaces` | 当前用户空间 |
| GET | `/api/spaces/{id}` | 空间详情，成员校验 |
| GET | `/api/spaces/{id}/timeline` | 空间时间轴 |
| PUT | `/api/spaces/{id}/theme` | 空间名与预设主题 |
| GET/POST | `/api/spaces/{id}/messages` | 空间留言墙 |
| POST | `/api/spaces/{id}/anniversaries` | 添加纪念日 |

## Memory 与互动

| 方法 | 地址 | 说明 |
|---|---|---|
| GET/POST | `/api/memories` | 我的记忆/创建记忆 |
| GET/PUT/DELETE | `/api/memories/{id}` | 详情/修改/删除 |
| GET | `/api/feed?scope=latest\|following` | 公共动态 |
| GET | `/api/home` | 首页聚合 |
| GET | `/api/calendar` | 月度记忆日期 |
| GET | `/api/map` | 有坐标的足迹 |
| POST | `/api/memories/{id}/comments` | 评论 |
| POST | `/api/memories/{id}/reactions` | 关系回应 |
| POST | `/api/memories/{id}/favorite` | 收藏公开动态 |

创建 Memory 示例：

```json
{
  "title": "西安城墙的晚风",
  "content": "日落以后，我们沿着城墙走了很久。",
  "memoryType": "MIXED",
  "occurredAt": "2026-07-18T19:20:00",
  "location": "西安城墙",
  "latitude": 34.2594,
  "longitude": 108.9470,
  "visibility": "RELATIONSHIP",
  "spaceIds": [2],
  "fileIds": [8, 9]
}
```

## 文件与事件

| 方法 | 地址 | 说明 |
|---|---|---|
| POST multipart | `/api/files` | 上传私有照片/视频 |
| GET | `/api/files/{id}/content` | 权限检查后从私有存储流式返回媒体 |
| POST/GET | `/api/spaces/{spaceId}/events` | 创建/列出共同事件 |
| GET | `/api/events/{id}` | 事件故事和挂载 Memory |
| GET/PUT | `/api/notifications`、`/api/notifications/read` | 通知列表/全部已读 |
