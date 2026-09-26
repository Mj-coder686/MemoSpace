# Phase 3 Cross-platform Shell

## Implemented

- `WebTopBar`: brand, Home/Memory/Space/Relationship/Feed navigation, global Memory search, reminders, friend/chat state, notifications, and user menu.
- `CompactTopBar`: current page title, contextual back, expandable Memory search, notifications, and Compact personal sheet.
- `BottomNavigation`: Home / Memory / Create / Relationship / Feed with combined Space + Relationship current state.
- `SecondaryNavigation`: Timeline/Photos/Calendar/Map and Categories/Spaces/Friends as real routes.
- `UserMenu`: profile, reminders, settings, and logout; Web popover and Compact Bottom Sheet share outcomes.
- Web create FAB condenses after scrolling and expands on hover/focus.
- Quick Dock and the old mobile More sheet were removed from the DOM and their obsolete global CSS was deleted.

## Navigation and runtime behavior

- Router scroll behavior now restores browser back/forward positions, honors anchors, preserves scroll for same-path query changes, and only moves new routes to the top.
- Android system back first closes the top registered overlay, then navigates, then exits only at the root.
- Default appearance background follows the semantic canvas, so dark mode no longer inherits a hard-coded light background.
- Native status bar switches icon style and background with explicit/system theme.
- Keyboard show/hide updates the keyboard offset, hides Bottom Navigation, and repositions Compact Toasts.
- Toasts move above the Compact bottom navigation rather than staying at the desktop top-right position.

## Verification

- `npm run lint:tokens`: passed.
- `npm run build`: passed.
- `npm run test:a11y:design-system`: 7 passed.
- 840px uses the Web shell; 839px uses Compact without duplicate navigation.
- 840px and 839px have no horizontal overflow.
- Compact navigation changes `/memories` to `/relationships` without a document reload.
- Back navigation restores the prior long-page scroll position.
- Compact personal Bottom Sheet has no serious or critical axe violations.
- Light Web and dark Compact screenshots were visually reviewed.
- The development shell and design-system routes are excluded from production output.

## Route reachability

- Primary navigation: Home, Memory, Space, Relationship, Feed.
- Memory secondary navigation: Memories, Photos, Calendar, Map.
- Relationship secondary navigation: Categories, Spaces, Friends.
- Personal menu/actions: User, Reminders, Notifications, Settings.
- Detail routes remain contextual: Memory, Space, Event, Relationship category/manage, Chat.
- Identity and Admin routes remain standalone and session-isolated.
