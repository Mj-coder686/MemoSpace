# Phase 1 Foundations

## Implemented

- Primitive color, spacing, radius, border, control, container, breakpoint, z-index, and safe-area tokens.
- Semantic light and dark colors for canvas, surfaces, text, borders, focus, actions, emotional accents, and states.
- Typography scale using local system stacks; the runtime Google Fonts dependency was removed.
- Motion durations and easing, including reduced-motion handling.
- Relationship theme seeds that affect only bounded covers and accents.
- Reset, base typography, keyboard focus, layout containers, safe-area, and surface utilities.
- Legacy semantic aliases so current pages remain functional while they are migrated phase by phase.
- A development-only `/__design-system` route that is absent from production builds.
- A diff-based token guard that rejects newly added raw colors, raw shadows, and `!important` page overrides.

## Verification

- `npm run lint:tokens`: passed.
- `npm run build`: passed.
- 1440px light-mode playground: visually checked.
- Pixel 7 dark-mode playground: visually checked.
- Keyboard focus: 3px visible external ring with 3px offset.
- Reduced motion: route transitions collapse to effectively zero duration.
- Remote font imports: none remain in `frontend/src` or `frontend/index.html`.
- Docker, backend services, database, volumes, and existing Android/server changes were not touched.

## Design judgment

The warm Stone palette is treated as a neutral canvas, not a nostalgic decoration. Ink Violet supplies product structure, Dust Rose is limited to emotional emphasis, and serif display type is limited to memorable statements. This keeps the selected Living Memory Atlas direction from becoming a generic warm-paper diary or template-like lifestyle UI.

## Migration rule

The large legacy stylesheet remains temporarily to prevent page regressions. Its rules will be removed only when the corresponding shell, component, or page has migrated to the new system.
