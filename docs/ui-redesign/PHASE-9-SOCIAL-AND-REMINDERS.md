# Phase 9 Social, Notifications, Chat, and Reminders

## Friend contact boundary

- Rebuilt `/friends` around Memo ID discovery, incoming requests, and an open contact index.
- Friend/contact and Relationship/shared-life remain separate concepts and separate API outcomes.
- Friend settings, reminder permission, chat mute, relationship invitation, removal, and blocking use accessible product dialogs.
- Destructive copy states that removing a Friend does not silently delete an existing Relationship or shared space.

## Realtime conversation

- Rebuilt `/chat/:friendId` as a focused conversation rather than a generic panel.
- Historical messages remain readable while the realtime connection is recovering.
- Failed optimistic messages remain visible and provide an explicit retry action.
- Compact Chat reserves the usable viewport so the composer stays above the persistent bottom navigation.
- Context actions link to reminders and relationship binding without merging those concepts into chat.

## Notifications and invitations

- Rebuilt `/notifications` into priority invitations, grouped recent activity, and a separate report-history view.
- Invitation consequences are stated before acceptance: an accepted relationship creates or reuses one shared space.
- Normal notifications are grouped into Today and Earlier and navigate to their existing product outcome when one is available.
- Unread and pending counts are summaries, not dashboard cards.
- Loading, error/retry, empty notification, and empty report states use the shared feedback language.

## Reminder time model

- Rebuilt `/reminders` as a chronological ledger with ownership, recurrence, due state, acceptance state, and creator context.
- Personal, about-a-friend, assigned-to-a-friend, and relationship reminders remain distinct creation modes.
- Incoming reminders expose accept/reject actions; active reminders expose complete/snooze actions.
- Reminder removal now uses a product-owned confirmation dialog rather than `window.confirm`.
- Web uses a horizontal timeline rhythm; Compact uses a dedicated readable vertical layout instead of shrinking the Web composition.
- Optional reminder images continue through authenticated file upload and media display.

## Verification

- Web Friends and Notifications were visually reviewed with requests, contacts, invitations, and activity data.
- Compact Chat and Reminders were visually reviewed at 393 × 852.
- A visual-review-only Compact grid issue that compressed reminder copy into an implicit column was found and fixed with explicit content/action placement.
- Browser tests cover Friend/Relationship boundaries, relationship invitation, removal consequence, failed-message retry, invitation acceptance, assigned reminder creation, incoming reminder acceptance, safe deletion, and Compact overflow.
- Compact Chat has no serious or critical WCAG A/AA axe violations.
- `npm run lint:tokens`: passed.
- `npm run build`: passed.
- Complete redesign browser suite: 35 passed.

## Boundaries retained

- No Friend, Relationship, Message, Notification, Report, Reminder, file, permission, or WebSocket API was changed.
- Failed realtime content is never presented as successfully delivered.
- Friend removal does not imply Relationship or shared-space deletion.
- Docker, backend migrations, Android project configuration, server deployment, and user data were not touched.
