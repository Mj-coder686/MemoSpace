# Phase 0 Baseline

## Source of truth

- UI specification: `MemoSpace-UI-Redesign-Spec.md`
- Code commit: `c13019c`
- Scope: UI information architecture, visual system, components, states, responsive behavior, and interaction presentation only.

## Route inventory

The existing router contains the required standalone and application routes:

- Identity: `/login`, `/register`, `/admin/login`
- Admin: `/admin`
- Home: `/home`
- Memory: `/memories`, `/memory/:id`, `/photos`, `/calendar`, `/map`
- Relationship and spaces: `/spaces`, `/space/:id`, `/event/:id`, `/relationships`, `/relationships/category/:id`, `/relationships/manage`
- Social and communication: `/friends`, `/chat/:friendId`, `/explore`, `/user/:id`
- Personal: `/reminders`, `/notifications`, `/settings`

All routes are lazy loaded. Route paths and parameters must remain unchanged.

## Existing behavior that must not regress

- Normal and administrator sessions remain isolated.
- Native server configuration continues using the existing production HTTPS fallback and advanced override behavior.
- Android runtime, safe-area, keyboard, location, camera, and system-back integrations remain in place.
- PRIVATE, RELATIONSHIP, PUBLIC, and CUSTOM Memory visibility stays enforced by the backend.
- Private media continues loading through authenticated APIs and object URLs.
- Friend, relationship, shared-space, report, moderation, reminder, notification, and chat semantics stay unchanged.

## Current UI debt

- One 55 KB, 403-line global stylesheet owns tokens, foundations, shell, components, and page rules.
- Remote Google Fonts are imported at startup.
- Legacy variables combine visual primitives and semantic meanings.
- Dark mode contains `!important` form overrides and decorative radial gradients.
- The shell duplicates navigation through desktop nav, mobile nav, a More sheet, FAB, and Quick Dock.
- Desktop and mobile navigation do not match the new information architecture.
- Router scroll behavior always forces the page to the top.
- Headings and section eyebrows overuse serif type and English uppercase labels.
- Panels and pages overuse large radii, shadows, gradients, and nested card treatment.

## Baseline verification

- `npm run build`: passed.
- Browser E2E: not run because `localhost:3000`, `5173`, `18081`, and `8080` were offline. No Docker services were started or changed.
- Source changes during Phase 0: documentation only.

## Visual direction comparison

### A. Quiet Archive

Chronological editorial pages, image-led records, date markers, narrow reading columns, and open separators. Strong for Memory browsing, but risks becoming a generic warm-paper diary if used everywhere.

### B. Relationship Rooms

Each important relationship feels like a distinct room through cover imagery, local theme accents, member presence, and shared milestones. Strong for shared spaces, but too much theming would fragment the product and weaken accessibility.

### C. Living Memory Atlas (selected)

Time, people, place, and media form the product's navigation language. Memory pages use editorial rhythm, relationship spaces use restrained room-like covers, and action surfaces stay modern and functional. This direction best supports the whole product without inventing unsupported map or AI features.

The selected direction combines the archive discipline of A with the bounded relationship expression of B, under the structural model of C.

