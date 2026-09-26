# MemoSpace UI Redesign Working Context

## Goal

Implement the Web + Capacitor Android UI redesign defined by:

`D:\Codex\Documents\2026-09-21\referenced-chatgpt-conversation-this-is-an-2\outputs\MemoSpace-UI-Redesign-Spec.md`

Preserve all routes, APIs, DTOs, permissions, media authorization, administrator isolation, data, Flyway migrations, Docker volumes, and business outcomes.

## Current baseline

- Git baseline: `c13019c` (`v1.6.0`), exactly matching the specification.
- Frontend: Vue 3, TypeScript, Vite, Pinia, Vue Router, Element Plus, Lucide, Capacitor 8.
- Production build passed before redesign work.
- Local Web/backend services were offline, so browser E2E could not be run during Phase 0 without starting external services.
- Existing user changes must be preserved in:
  - `frontend/android/app/build.gradle`
  - `frontend/android/app/src/main/AndroidManifest.xml`
  - `frontend/capacitor.config.ts`
  - `frontend/e2e/v16-mobile-login.spec.ts`
  - `frontend/src/utils/serverConnection.ts`
  - `frontend/src/views/AuthView.vue`
  - `安卓 APK 使用说明.md`

## Decisions

- The specification is the visual and interaction source of truth.
- UI/UX Pro Max search results were rejected when they proposed Aurora/pink/marketing-page patterns that conflict with the product brief.
- Selected visual world: **Living Memory Atlas / 生活记忆索引**.
  - Memory is organized through time, people, place, and media rather than dashboard statistics.
  - Pages use open editorial structure, real content imagery, and clear privacy boundaries.
  - Relationship themes appear mainly on covers and local accents; controls and body text remain globally consistent.
  - Warm stone colors and limited serif use are intentional brief choices, but generic warm-craft styling is avoided through Ink Violet, restrained shapes, functional structure, and low decoration.
- Implementation follows the specification phases; no all-at-once rewrite.

## Current phase

Web UI redesign complete through Stage 5; ready for product review and release preparation.

Phase 1 passed on 2026-09-22:

- semantic light/dark, typography, spacing, shape, layer, motion, safe-area, and relationship theme tokens added;
- reset, base, focus, reduced-motion, container, and safe-area foundations added;
- remote Google Fonts removed;
- development-only design-system playground added;
- production build and design-token guard passed;
- visual checks passed at 1440px light and Pixel 7 dark widths;
- keyboard focus ring and reduced-motion behavior were verified in Chromium.

Phase 2 passed on 2026-09-22:

- controls, choices, feedback, overlays, loading, empty-state, and pagination primitives added;
- component-level tokens added;
- overlay focus trapping, Escape dismissal, scroll locking, compact bottom sheet, and Toast queue added;
- the full component matrix is available in the development playground;
- 320px has no horizontal overflow;
- production build and token guard passed;
- axe scans passed in Expanded light, Compact dark, and open-dialog focus scenarios with no serious or critical violations.

Phase 3 passed on 2026-09-23:

- WebTopBar, CompactTopBar, BottomNavigation, SecondaryNavigation, and UserMenu added;
- Web primary navigation now follows Home / Memory / Space / Relationship / Feed;
- Compact navigation now follows Home / Memory / Create / Relationship / Feed;
- Memory and Relationship secondary route navigation added;
- Quick Dock and the legacy More sheet were removed with their obsolete CSS;
- route scroll restoration and same-route query preservation added;
- Android back now closes the top overlay before navigating or exiting;
- native status-bar color/style and keyboard offset now track theme and keyboard state;
- default custom background now follows the semantic canvas in dark mode;
- 7 shell/component tests pass, including no-reload Compact navigation, 840px breakpoint, scroll restoration, axe, and overlay focus.

Phase 4 passed on 2026-09-23:

- login, registration, and administrator login were rebuilt in the Living Memory Atlas direction;
- a project-owned responsive memory-scene image now supports both Web and Compact crops;
- no demo, default password, automatic login, or prefilled administrator username remains in the identity UI;
- offline, server-unreachable, backend-error, banned, loading, and registration-success states are explicit;
- native server origin remains visible in collapsed HTTPS-only advanced settings;
- administrator login now explains session separation and the hard privacy boundary;
- the obsolete user/admin segmented switch was removed;
- `UiInput` label semantics were corrected so password inputs and reveal controls have independent accessible names;
- production build, token guard, visual review, and 11 browser/accessibility tests pass.

Phase 5 passed on 2026-09-23:

- Home now uses an open local-time greeting and editorial content hierarchy instead of a giant statistics Hero;
- Web uses recent Memory plus upcoming reminders as the primary 8/4 composition;
- Compact has an independent content order and responsive layout rather than a shrunken Web grid;
- `/home`, `/spaces`, and `/reminders` load independently with local skeleton, error, retry, and empty states;
- first-use is staged: record the first Memory, then invite an important person;
- Home has purpose-built Memory and Space summary components;
- the Home primary action opens the existing global creation flow;
- Home is now eagerly loaded to remove a verified cold-start blank-route case after login;
- Web light and Compact dark visual reviews passed;
- production build, token guard, and 15 browser/accessibility tests pass.

Phase 6 passed on 2026-09-23:

- `/memories` is now an editorial archive with route-query search, type filters, result count, and distinct loading/error/initial/search/filter states;
- `MemoryCard` exposes type, creator, place, comments, and explicit PRIVATE/RELATIONSHIP/PUBLIC semantics while continuing to use authenticated media requests;
- the creation flow is a wide Web dialog and Compact full-screen sequence with type, story, media, time/place, spaces, visibility, validation, and real upload progress;
- `/memory/:id` now has protected media preview, readable visibility boundaries, spaces, reactions, favorite, comments, report entry, and separate 403/404/network states;
- rejected comments preserve their draft and loaded content; privacy-denied pages do not leak titles or backend detail;
- route transitions are keyed by path so query-only search changes no longer remount the page or briefly expose duplicate interactive controls;
- Web light and Compact dark visual reviews passed;
- production build, token guard, and 19 browser/accessibility tests pass.

Phase 7 passed on 2026-09-24:

- `/photos` is now a chronological authenticated-media archive with reliable month grouping, Web varied-height composition, and Compact two-column presentation;
- Photo empty/header actions open the existing creation flow directly in PHOTO mode without duplicating the form;
- `/calendar` now uses a Web month/detail 8/4 layout and Compact stacked reading order, with complete six-week grids, adjacent dates, distinct today/selected/Memory states, and independent error recovery;
- `/map` now keeps a permanent place-list fallback beside the simplified coordinate canvas, distinguishes current and saved locations by shape and icon, and keeps data usable when location permission is denied;
- the global creation event accepts an optional initial Memory type while retaining TEXT as the default everywhere else;
- Web light Photo/Calendar/Map and Compact dark Map visual reviews passed;
- production build, token guard, and 23 browser/accessibility tests pass.

Phase 8 passed on 2026-09-24:

- `/relationships` now makes the Friend/contact and Relationship/shared-life boundary explicit and keeps invitation separate from custom-category creation;
- category visibility, ordering, hiding, restoration, and detail views preserve every Relationship, shared space, Memory, and media object;
- category detail shows people as an open index and exposes exactly one shared-space entry for each relationship;
- `/relationships/manage` is now a relationship ledger with multi-tag editing, hidden-tag state, one-space language, product-owned archive confirmation, and archived history;
- `/spaces` separates private personal space, active relationship spaces, and archived history without offering invalid empty-space creation;
- `/space/:id` now uses a space cover, chronological Memory timeline, and a relationship rail for members, anniversaries, events, and messages;
- anniversary, appearance, deletion, and archive flows now use accessible product dialogs instead of native confirmation windows;
- `/event/:id` now carries the same editorial event and Memory hierarchy;
- Web Space index/detail and Compact shared-space detail visual reviews passed;
- production build, token guard, and the complete 31-test browser/accessibility suite pass.

Phase 9 passed on 2026-09-25:

- `/friends` now keeps Memo ID discovery, friend requests, the contact list, and relationship invitation as visibly separate tasks;
- the Friend list uses an open contact index instead of a three-column card dashboard;
- Friend removal and blocking use product-owned confirmation dialogs that state that existing relationships and shared spaces remain independent;
- Friend settings and Friend-to-Relationship invitation now use the shared accessible form and dialog primitives;
- `/chat/:friendId` now exposes reconnecting/offline state, preserves failed optimistic messages, and provides an explicit retry action;
- Compact Chat now reserves the actual usable viewport so the composer remains above the bottom navigation even when multiple feedback banners are visible;
- `/notifications` now prioritizes relationship invitations, groups recent activity by time, and keeps report history in a separate view;
- notification rows navigate to their existing product destination without inventing new behavior;
- `/reminders` is now a chronological ledger with explicit ownership, recurrence, due, acceptance, and creator language;
- personal, about-a-friend, assigned-to-a-friend, and relationship reminder modes remain distinct;
- reminder deletion now uses an accessible product dialog instead of a native confirm window;
- Web Friends/Notifications and Compact Chat/Reminders visual reviews passed;
- a Compact reminder-grid issue found during visual review was fixed before phase closure;
- production build, token guard, and the complete 35-test browser/accessibility suite pass.

Phase 10 passed on 2026-09-25:

- `/explore` now keeps public Memory Feed and user search as separate visual and interaction regions;
- Latest and Following retain the existing backend scope and use truthful empty states;
- following a user and requesting a Relationship are visibly separate actions with different consent outcomes;
- `/user/:id` now shares one profile skeleton while presenting distinct self/other actions;
- other profiles render only public Memories available from the existing public feed and never imply private content counts;
- own profile exposes direct Memory, Photo, Space, and Settings entry points;
- Web Feed and Compact other-user profile visual reviews passed;
- production build, token guard, and the complete 39-test browser/accessibility suite pass.

Phase 11 passed on 2026-09-25:

- `/settings` is divided into Identity, Appearance, Security, Connection, and Session groups;
- Web uses a settings rail while Compact behaves as focused grouped subpages;
- self-service password change and native HTTPS server-origin configuration are present again;
- appearance failure restores the last server-backed settings instead of leaving a false saved preview;
- logout uses a product-owned confirmation dialog and states that cloud data remains intact;
- a shared Compact header selector that reversed one-child headers was found by visual review and fixed globally;
- Web Settings and Compact Appearance visual reviews passed;
- production build, token guard, and the complete 42-test browser/accessibility suite pass.

Phase 12 passed on 2026-09-25:

- the administrator experience is now an independent evidence-scoped safety center with no ordinary-member navigation path;
- report handling can dismiss, remove content, warn, mute for seven days, or ban, with exact consequences shown before execution;
- account actions can reset passwords, change Memo IDs, lift restrictions, mute, or ban while preserving the administrator privacy boundary;
- all consequential administrator actions now use product-owned confirmation dialogs and audit records;
- the dashboard was brought into the shared semantic token, typography, control-size, focus, and responsive systems;
- administrator search, status filters, icon actions, pagination, and dialogs were accessibility-hardened;
- production build, token guard, and the complete 44-test browser/accessibility suite pass.

Stage 4 Impeccable audit passed on 2026-09-25:

- audit health score: 18/20;
- all actionable detector warnings were repaired;
- the only remaining detector advisory is a verified false positive: the coordinate grid is on the actual Memory map canvas;
- thick side-stripe state treatments, layout-property animations, indiscriminate reduced-motion overrides, redundant kicker labels, and administrator accessibility gaps were removed;
- production build, design-token guard, and 44/44 browser/accessibility tests pass.

Stage 5 Make Interfaces Feel Better passed on 2026-09-25:

- shared and legacy buttons now use consistent interruptible 0.96 press feedback;
- Compact/coarse-pointer small controls enforce a 44px minimum target;
- macOS font smoothing, balanced headings, pretty body copy, tabular dynamic numbers, scrollbars, caret, and light/dark neutral image outlines are part of the shipped foundation;
- undefined legacy status/radius tokens were replaced with real semantic tokens;
- failed-chat metadata contrast was raised from the discovered 3.91:1 failure to the semantic danger foreground at full opacity;
- bare property transitions were replaced with explicit compositor-friendly transitions;
- production build, token guard, real press checks, coarse-pointer target checks, 10% motion inspection, axe scans, and the complete 47-test browser suite pass.

## Next actions

1. Present the completed Web redesign for product review.
2. After approval, split the work into independent commits and prepare the deployment/release package.
3. Treat physical Android gesture/device verification as a separate later stage; do not reopen the Web information architecture for it.
