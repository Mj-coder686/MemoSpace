# MemoSpace V1.6 working context

## Current goal

- Keep the small-server architecture: Vue/Nginx + one Spring Boot service + MySQL + Redis + private MinIO.
- Use Flyway for every database change. `V1__baseline.sql` is immutable; add V2/V3 migrations rather than editing deployed migrations.
- Preserve the existing MySQL volume: first upgraded startup baselines a non-empty legacy schema at V1, then applies V2.
- Support user reports for Memory/comments and flexible manual moderation while preserving the rule that administrators cannot browse unrelated private data.
- Support one-shot current-location capture on Web and Android after explicit user action.
- Prevent post-deployment stale chunks from leaving navigation blank: revalidate `index.html`, cache hashed assets, and perform one guarded chunk-recovery reload.

## V1.6 acceptance

- A user can report visible content they do not own; duplicate/self/inaccessible reports are blocked.
- Admin sees only report evidence, reporter/reported account metadata, violation count, and audit history.
- Admin can dismiss, remove the exact target, warn, mute for seven days, ban, or restore an account.
- Banned users cannot log in or keep using old REST sessions. Muted users may read but cannot publish Memory/comments/space messages/chat.
- Existing data survives V1→V2 Flyway migration.
- Web build, Android sync/APK build, backend suite, and browser navigation/location/report tests pass.

## Decisions implemented

- `content_report` keeps an immutable text snapshot so deleting the target does not erase the audit trail.
- Report media access requires both report ID and file ID and verifies that the file belongs to the reported Memory.
- Moderation remains manual and flexible; each confirmed report increments `violation_count` and sends an official in-app notice.
- Three administrators are configured in the ignored local `.env`; public examples do not contain their secrets.
- Current location is not collected in the background. It is requested only from the map or Memory editor.

## Verification state (2026-09-20)

- `mvn test`: 24 passed, including moderation and legacy-schema Flyway migration.
- `npm run build`: passed, 1797 modules transformed.
- Playwright V1.7/V1.8 targeted set: 4 passed (mobile routes, friend search, geolocation, user report).
- `npx cap sync android`: passed with the Capacitor geolocation plugin.
- Android `assembleDebug`: passed; package version code 19 / `1.6.0-android-test`.
- Docker was deliberately not restarted or recreated during this work.

## Important paths

- Project: `D:\Codex\Project\memo-space-v1-20260826\memo-space`
- Compose: `docker-compose.yml`
- Backend migrations: `backend/src/main/resources/db/migration`
- Moderation: `backend/src/main/java/com/memospace/service/ModerationService.java`
- Report UI: `frontend/src/components/ReportModal.vue`, `frontend/src/views/AdminDashboardView.vue`
- Location helper: `frontend/src/utils/geolocation.ts`
- Relationship services: `backend/src/main/java/com/memospace/service/RelationshipService.java`, `RelationshipCategoryService.java`
- Frontend source: `frontend/src`

## Do not regress

- Do not render a placeholder when a valid media record exists.
- Do not expose a signed object URL until the requesting user passes Memory permission checks.
- Do not create a second relationship or space just because a new category tag is added.
- Do not delete historical data when a category is hidden or a relationship is archived.
- Do not expose moderation history or violation counts through ordinary public profile APIs.
- Do not allow admin evidence URLs to become a general-purpose file bypass.
- Do not modify an already-deployed Flyway migration; add a new numbered migration.
