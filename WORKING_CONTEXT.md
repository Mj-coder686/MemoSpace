# MemoSpace V1.2 working context

## Current V1.2 goal

- Execute Web-only iteration items 1–4: immutable 12-digit numeric Memo ID, friend requests/settings, durable one-to-one WebSocket chat, and image/recurring reminders.
- Defer Web Push, Android APK, and iPhone clients.
- Keep `user_account.id` internal; expose `public_id` as the searchable Memo ID.
- Keep friendship separate from Relationship categories/spaces. Only accepted friends can chat.
- Friend-assigned reminders default to direct delivery for this private deployment, but each recipient can disable direct reminders per friend; disabled delivery becomes pending acceptance.
- Small-server architecture: one Spring Boot instance, MySQL persistence, Redis only where useful, MinIO images, in-process scheduler with database deduplication.
- Execution checklist: `docs/web-social-iteration-checklist.md`.

## Scope and acceptance

- Fix actual Memory media delivery for `PUBLIC`, `PRIVATE`, and `RELATIONSHIP`; validate authorized visibility and unauthorized denial across DB, MinIO, signed URL, browser requests, and HTTP responses.
- Add owner-specific relationship categories (four defaults, custom, hide/restore, reorder) without deleting relationships, spaces, or memories.
- One active relationship between a pair owns one shared space; multiple category labels must reuse it.
- Complete search/invite/accept/reject/manage/unbind flow and improve low-contrast visual hierarchy.
- Finish only after Docker deployment and browser-level two-or-more-account verification.

## Decisions already implemented

- New normalized tables: `relationship_category`, `relationship_category_link`, and `relationship_invitation_category`.
- Categories are user-owned labels; category links are independent from `relationships` and `space`, so hiding a label changes only `is_visible`.
- Invitation acceptance finds an existing active relationship before creating one and always reuses its relationship space.
- Docker profile runs the idempotent schema on startup so an existing MySQL volume receives V1.1 tables.
- Legacy invitation requests with `relationshipType` remain supported by mapping to a default category.

## Verification state

- Backend integration suite: 14 tests pass with zero failures, covering V1.1 media/relationship behavior plus Memo ID, friendship, WebSocket chat, reminder authorization and recurrence.
- Frontend production build passes (1751 modules). Docker images build successfully and MySQL, Redis, MinIO, backend and Nginx frontend all run through Compose.
- Both Playwright journeys pass together after a full container restart: V1.1 relationship/media permission regression and V1.2 three-account friend/chat/reminder acceptance.
- V1.2 browser evidence includes unique 12-digit IDs, friend consent, live WebSocket delivery, persisted history, non-friend 403, real reminder-image decoding, outsider 403, reminder acceptance and scheduler notification delivery.
- Restart comparison retained all users, Memo IDs, friendships, messages, reminders, `memory_media`, `file_record` rows and MinIO objects.
- Backend health is exposed at `/actuator/health`; Redis authentication was corrected so it reports UP instead of a false container-only health result.

## Important paths

- Project: `D:\Codex\Project\memo-space-v1-20260826\memo-space`
- Compose: `docker-compose.yml`
- Backend schema: `backend/src/main/resources/schema.sql`
- Relationship services: `backend/src/main/java/com/memospace/service/RelationshipService.java`, `RelationshipCategoryService.java`
- Frontend source: `frontend/src`

## Do not regress

- Do not render a placeholder when a valid media record exists.
- Do not expose a signed object URL until the requesting user passes Memory permission checks.
- Do not create a second relationship or space just because a new category tag is added.
- Do not delete historical data when a category is hidden or a relationship is archived.
