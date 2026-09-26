# Phase 4 Identity and First-use Experience

## Implemented

- Rebuilt `/login` and `/register` around the Living Memory Atlas direction instead of the previous gradient split-screen treatment.
- Added a project-owned responsive hero photograph with safe Web and Compact crops; it contains no people, readable text, logos, or personal information.
- Web uses a full-height memory scene with a focused 456px account panel; Compact keeps the same brand language but uses a 30dvh visual region and solid bottom form panel.
- Native keyboard-open state compresses the visual region to 88px so fields remain usable above the software keyboard.
- Login/register switching now uses a restrained route transition and inline account link instead of a prominent user/admin segmented control.
- Removed the obsolete `LoginModeSwitch` component.

## Account and connection states

- No demo username, default password, or administrator username is prefilled.
- Local validation covers required nickname/username/password, registration username format, and minimum password length.
- Backend errors keep entered values intact and appear in an accessible banner.
- Offline, unreachable-server, banned-account, loading, and registration-success states have distinct feedback.
- Native server configuration remains available in a collapsed advanced section, always shows the current origin, and continues to require HTTPS.
- Registration uses `new-password`; login and administrator login use `current-password`.

## Administrator boundary

- `/admin/login` is now visually separate from the consumer identity flow without adopting a hacker-console aesthetic.
- The page states that administrator sessions are independent and that ordinary users remain protected by backend authorization.
- The privacy boundary is explicit: administrators cannot browse unreported private Memories, images, or shared-space content.
- Administrator credentials start empty; the normal user session remains untouched.

## Accessibility and component correction

- `UiInput` now uses a dedicated `<label for>` instead of wrapping the password-reveal button inside the label. This keeps the input and reveal-button accessible names separate.
- The Compact identity page has no horizontal overflow at 393px.
- Mobile banned-account and administrator-login pages pass WCAG A/AA axe checks with no serious or critical violations.

## Verification

- `npm run lint:tokens`: passed.
- `npm run build`: passed.
- `npm run test:a11y:design-system`: 11 passed.
- Browser tests verify blank credentials, absence of demo/default-password copy, local registration validation, exact registration payload, backend error preservation, banned state, session separation, privacy copy, responsive overflow, and accessibility.
- Web login, Compact login, and Web administrator login were visually reviewed after route transitions completed.
