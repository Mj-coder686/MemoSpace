# Android release signing

MemoSpace release APKs must be signed with the permanent `memospace` release key. The private key and passwords are intentionally stored outside Git.

## Local configuration

Create `frontend/android/signing.properties` with:

```properties
storeFile=C:/absolute/private/path/memospace-release.jks
storePassword=REPLACE_WITH_SECRET
keyAlias=memospace
keyPassword=REPLACE_WITH_SECRET
```

The properties file and all `*.jks` / `*.keystore` files are ignored by Git. Never remove those ignore rules or commit signing secrets.

## Build

From `frontend`:

```powershell
npm run build
npx cap sync android
cd android
.\gradlew.bat clean assembleRelease
```

The signed APK is written to `frontend/android/app/build/outputs/apk/release/app-release.apk`.

Before every release:

1. Increase `versionCode` in `app/build.gradle`.
2. Set the user-facing `versionName` for the release.
3. Verify the APK with Android SDK `apksigner verify --verbose --print-certs`.
4. Confirm the signer SHA-256 remains:
   `D7:0F:20:2B:58:4A:21:19:55:F5:74:AB:A2:8D:0F:2E:C4:84:9A:0D:EC:19:0E:F1:8F:5E:D5:95:69:1E:B1:46`.

Losing the release key or its password prevents direct updates to existing non-Play installations. Keep encrypted, offline backups and store the password separately.
