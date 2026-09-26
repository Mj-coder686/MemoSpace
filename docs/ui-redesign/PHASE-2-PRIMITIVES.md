# Phase 2 UI Primitives

## Component matrix

### Controls

- `UiButton`: primary, secondary, tonal, ghost, danger, link; sm/md/lg; loading, disabled, block.
- `UiIconButton`: sm/md/lg; secondary, ghost, tonal; stable `99+` badge and accessible label.
- `UiInput`: label, helper/error association, required, disabled, trailing content, password reveal.
- `UiTextarea`: label, helper/error, counter, automatic growth with capped internal scrolling.
- `UiSelect`: label, helper/error, native keyboard behavior.
- `UiCheckbox`, `UiRadio`, `UiSwitch`: full-row targets, descriptions, disabled behavior.
- `UiChip`, `UiBadge`, `UiAvatar`, `UiDivider`, `UiTooltip`.

### Feedback and loading

- `UiBanner`: info, success, warning, danger.
- `UiToastViewport` and `useToast`: up to three visible messages, queued overflow, optional retry/action.
- `EmptyState`: empty, search, permission, error, and archived semantics.
- `UiSkeleton`: reduced-motion fallback.
- `UiProgress`, `UiPagination`, `UiLoadMore`.

### Overlays

- `UiDialog`: focus entry/trap/restore, Escape, optional safe backdrop close, busy protection.
- `UiDrawer`: Expanded detail inspection and Compact full-width behavior.
- `UiBottomSheet`: safe-area padding, dismissal protection, close action, and enlarged swipe-down handle.

## Verification

- `npm run lint:tokens`: passed.
- `npm run build`: passed.
- `npm run test:a11y:design-system`: 3 passed.
- axe: no serious or critical violations in 1440px light, 320px dark, or open-dialog scenarios.
- 320px document width equals viewport width.
- Dialog focus wraps from the final action back to Close.
- Dialog and Drawer close with Escape; Bottom Sheet closes through its explicit action.
- Toast, checkbox, and pagination interactions were exercised in Chromium.
- Light Expanded and dark Compact screenshots were visually reviewed.

## Accessibility correction discovered by testing

The initial light tertiary text was below the 4.5:1 requirement for 12px helper text. The semantic token was raised from Stone 500 to Stone 600 rather than hiding or excluding the failure. A prohibited ARIA label on the skeleton group was also replaced with a valid status region.
