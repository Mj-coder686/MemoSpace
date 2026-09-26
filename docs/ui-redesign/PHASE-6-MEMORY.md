# Phase 6 Memory Library, Creation, and Detail

## Implemented

- Rebuilt `/memories` as an editorial archive with route-query search, readable result counts, type filters, local loading/error states, and distinct initial/search/filter empty states.
- Rebuilt `MemoryCard` around actual Memory metadata: date, creator, place, comments, type, and explicit PRIVATE/RELATIONSHIP/PUBLIC visibility language.
- Media remains loaded through authenticated `/files/:id/content` requests. No visual placeholder is used to disguise a failed media request.
- Rebuilt the global creation flow as a wide Web dialog and a Compact full-screen flow with a consistent sequence: type, story, media, time/place, spaces, and visibility.
- Preserved media type and 30MB validation, relationship-space rules, manual location fallback, and real aggregate upload progress.
- Successful creation waits for the server and routes to the new Memory detail page.
- Rebuilt `/memory/:id` as an 8/4 Web reading layout and one-column Compact layout with protected media preview, visibility explanation, spaces, reactions, favorites, comments, and low-weight reporting.

## Permission and failure behavior

- 403 uses a privacy-preserving message and does not expose a Memory title or backend detail.
- 404 is distinguished from temporary network/server failure.
- A failed reaction, favorite, or comment does not replace the loaded Memory with an empty page.
- A rejected comment preserves the user's draft and shows the backend reason, including mute information when supplied.
- Creation cannot select relationship visibility when no active relationship space exists.

## Navigation correction

- Query-only changes such as Memory search no longer remount the route or render two interactive copies during page transitions.
- Route transitions are keyed by path; each page is still animated when navigating to a different route.

## Verification

- Web light archive and detail layouts were visually reviewed with representative protected media and visibility states.
- Compact dark creation flow was visually reviewed at 393 × 852.
- Browser tests cover archive search/filtering, create validation, protected media loading and preview, comment failure recovery, privacy-safe 403 handling, Compact overflow, full-screen dialog sizing, and accessibility.
- Compact Memory flow has no serious or critical WCAG A/AA axe violations.
- `npm run lint:tokens`: passed.
- `npm run build`: passed.
- `npm run test:a11y:design-system`: 19 passed.

## Boundaries retained

- No Memory API, DTO, visibility rule, media authorization path, or reporting boundary was changed.
- Docker, Android project configuration, backend migrations, and user data were not touched in this phase.
