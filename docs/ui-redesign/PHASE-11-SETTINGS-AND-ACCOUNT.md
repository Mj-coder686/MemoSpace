# Phase 11 Settings, Appearance, and Account

## Information architecture

- Rebuilt `/settings` into Identity, Appearance, Security, Connection, and Session groups.
- Web uses a persistent group rail and focused form column.
- Compact uses the same brand system but behaves as grouped subpages, showing one settings group at a time.
- Memo ID is a restrained identity row rather than an oversized decorative card.

## Identity and appearance

- Profile and avatar updates retain the existing authenticated upload flow.
- Display mode applies immediately on the device; account background changes save explicitly.
- Background image, color, brightness, overlay, automatic luminance balancing, and removal remain available.
- Failed appearance saves restore the last server-backed values instead of leaving a false saved preview.

## Security, connection, and session

- Restored the existing self-service password API with current/new/confirmation fields and clear success copy.
- Native builds retain HTTPS server-origin configuration and explain that API, media, and realtime messages share it.
- Logout now uses a product-owned confirmation dialog and states that cloud data remains intact.

## Verification

- Web full Settings and Compact Appearance were visually reviewed.
- Visual review found and fixed a shared single-column header selector that reversed heading content on Compact pages.
- Browser tests cover profile payloads, password payloads, logout consequences, appearance failure rollback, Compact grouped navigation, header order, overflow, and Compact WCAG A/AA checks.
- `npm run lint:tokens`: passed.
- `npm run build`: passed.
- Complete redesign browser suite: 42 passed.

## Boundaries retained

- No Profile, Appearance, Password, file, server-origin, authentication, or realtime API was changed.
- Admins still cannot read a user's old password or private content.
- Docker, backend migrations, Android project configuration, server deployment, and user data were not touched.
