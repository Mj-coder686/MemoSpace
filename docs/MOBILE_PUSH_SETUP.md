# MemoSpace Android 系统通知配置

MemoSpace 已完成手机令牌登记、Android 通知权限、通知频道、点击跳转、后端 FCM 发送以及失效令牌停用。未提供凭据时，应用内 WebSocket 通知仍照常工作，后端不会因此启动失败。

## 仍需由 Firebase 控制台生成的两个私密文件

1. 在 Firebase 新建项目，并添加 Android 应用，包名必须是 `com.memospace.app`。
2. 下载客户端文件 `google-services.json`，只放到本机：
   `frontend/android/app/google-services.json`
3. 在 Firebase「项目设置 → 服务账号」生成新的私钥 JSON，上传到服务器私密目录，例如：
   `/home/mjie/secrets/memospace-firebase-service-account.json`

这两个文件都已被 Git 忽略，不得提交到 GitHub，也不要发到聊天或公开网盘。

## 服务器启用方式

在服务器项目的 `.env` 中增加：

```dotenv
PUSH_ENABLED=true
FIREBASE_PROJECT_ID=你的Firebase项目ID
FIREBASE_CREDENTIALS_PATH=/run/secrets/firebase-service-account.json
```

再为 backend 容器增加只读挂载：

```yaml
services:
  backend:
    volumes:
      - /home/mjie/secrets/memospace-firebase-service-account.json:/run/secrets/firebase-service-account.json:ro
```

重新构建 backend 后，日志出现 `Firebase mobile push initialized successfully` 才表示服务端推送真正生效。

## Android 重新打包

加入 `google-services.json` 后运行：

```powershell
cd frontend
npm run build
npx cap sync android
cd android
.\gradlew.bat assembleRelease
```

安装新版 APK，登录后在「设置 → 通知」允许系统通知。Android 13 及以上会显示原生权限窗口。

## Redmi / 国内系统说明

FCM 依赖 Google Play 服务。没有 Google Play 服务的国行系统无法保证后台送达；这不是 MemoSpace 代码或服务器故障。要覆盖纯国内系统，还需分别申请小米推送等厂商通道及相应开发者凭据，再接入聚合推送。当前实现会明确显示设备登记或服务端配置状态，不会把未生效显示成“已开启”。
