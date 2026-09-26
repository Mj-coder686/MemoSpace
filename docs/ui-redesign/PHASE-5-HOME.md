# Phase 5 Home and First Meaningful Use

## Implemented

- Replaced the statistics-heavy Hero with an open greeting area based on the user's local time, local date, name, and one primary `记录此刻` action.
- Web Home now follows an editorial 8/4 composition: recent Memory on the left and upcoming reminders on the right.
- Compact Home has its own reading order: greeting and quick record, recent Memory, reminders, spaces, On This Day, and people-you-care-about feed.
- Added purpose-built Home components for featured Memory, compact Memory rows, and Space summaries instead of reusing dashboard-style cards.
- The Web floating create button is hidden only on Home because the greeting already provides the same primary action; other routes retain the global create action.

## First-use state

- A user with no Memories sees one staged onboarding surface, not four simultaneous empty cards.
- Step 1 is recording the first Memory.
- Step 2, inviting an important person, is visible as the next locked-in-progress step rather than competing with the first action.
- After the first Memory, users without a shared space receive a compact invitation step above their existing personal space.

## Resilience

- `/home`, `/spaces`, and `/reminders` load independently.
- A failed Memory summary no longer blanks spaces or reminders.
- Each failed section has its own retry action and preserves the rest of Home.
- Loading skeletons are local to each module.
- Empty reminders use a compact informative state rather than a full-page empty panel.

## Cold-start correction

- Browser verification exposed a first-process cold-load case where the shell appeared before the lazy Home module resolved, leaving the route outlet blank.
- Home is now part of the core application entry instead of a lazy route, preventing the post-login empty first screen.
- Other routes remain lazy and keep the existing chunk-recovery protection.

## Verification

- Web light and Compact dark layouts were visually reviewed with representative Memory, Space, reminder, anniversary, and feed data.
- Compact width has no horizontal overflow.
- Compact Home has no serious or critical WCAG A/AA axe violations.
- Browser tests cover the primary create action, staged first-use state, independent partial failure, Compact overflow, and accessibility.
- `npm run lint:tokens`: passed.
- `npm run build`: passed.
- `npm run test:a11y:design-system`: 15 passed.
- The design browser test server now uses port 4173 because Windows reserved the previous 5173 range; this does not affect production or Docker.
