# Phase 7 Memory Companion Views

## Photo archive

- Rebuilt `/photos` as a chronological visual archive grouped by reliable `occurred_at` month data.
- Web uses a varied-height grid without reordering the DOM; Compact uses a predictable two-column image grid.
- Every image and video remains sourced from the authenticated file endpoint through `PrivateMedia`.
- Video Memories retain an explicit play mark; title, date, and optional place are readable without permanently covering the whole image.
- Empty-state and header actions open the existing creation flow directly in PHOTO mode.

## Calendar

- Rebuilt `/calendar` as an 8/4 Web composition with the month on the left and a sticky selected-day reader on the right.
- Compact keeps the month full width and places the selected-day list directly below it.
- The grid always contains six complete weeks. Adjacent-month dates remain readable and selectable.
- Today, selection, focus, and Memory count use separate visual and semantic states.
- Previous month, next month, today, and every date have explicit accessible names.
- Month failure and selected-day failure are independent and recoverable.

## Memory map

- Rebuilt `/map` around a permanent place-list fallback plus a simplified coordinate canvas.
- Current location is visually and semantically different from a saved Memory place; the distinction does not rely on color alone.
- Location access only starts after a user action, and the page explains that it is a one-time read rather than background tracking.
- A denied or failed location request leaves the saved-place list fully usable.
- Selecting either a list item or marker reveals the title, date, place, and a direct Memory-detail link.
- Compact presents the canvas first and the place list as a sheet-like continuation below it.

## Creation integration

- The global creation event now accepts an optional existing Memory type.
- Photo and map empty states can start PHOTO or LOCATION creation without introducing a second form or API.
- All other create entry points continue to default to TEXT.

## Verification

- Web light Photo, Calendar, and Map layouts and Compact dark Map were visually reviewed with representative data.
- Browser tests cover month grouping, direct PHOTO creation, calendar selection and accessible navigation, place selection, location denial recovery, Compact overflow, and accessibility.
- Compact companion views have no serious or critical WCAG A/AA axe violations.
- `npm run lint:tokens`: passed.
- `npm run build`: passed.
- `npm run test:a11y:design-system`: 23 passed.

## Boundaries retained

- No Calendar, Map, Memory, file, or geolocation API was changed.
- The map remains a simplified project-owned canvas; it does not add a third-party map dependency or continuous tracking.
- Docker, backend migrations, Android project configuration, and user data were not touched.
