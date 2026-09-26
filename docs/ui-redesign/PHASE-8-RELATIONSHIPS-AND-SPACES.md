# Phase 8 Relationships and Shared Spaces

## Relationship information architecture

- Rebuilt `/relationships` around the explicit boundary that Friends are for contact while Relationships are for shared life records.
- System and custom categories remain views over a relationship. Adding multiple category tags never creates duplicate relationships or duplicate shared spaces.
- Visible categories use restrained local accents; hidden categories move to a separate recoverable section.
- Hiding a category explicitly states that the Relationship, Relationship Space, Memories, and media remain untouched.
- Relationship invitation is a staged flow: search a user, select one category, add an optional message, and send for acceptance.
- Custom category creation remains separate from relationship invitation.

## Category detail and relationship management

- Rebuilt `/relationships/category/:id` as an open people index with one explicit shared-space entry per relationship.
- Empty categories route back into the invitation flow with the category preselected.
- Rebuilt `/relationships/manage` as a relationship ledger instead of nested dashboard cards.
- Multiple classification tags, including hidden tags, can be edited without affecting the one shared space.
- Relationship removal uses a product-owned confirmation dialog and explains the archived-history consequence before submission.
- Archived relationships remain available in a quiet, collapsed history section.

## Space index, detail, and events

- Rebuilt `/spaces` into three data boundaries: private personal space, active relationship spaces, and archived spaces.
- The empty shared-space state explains that a space is created by an accepted relationship rather than by creating an unrelated empty container.
- Rebuilt `/space/:id` around a space cover, chronological Memory timeline, and a relationship rail for members, anniversaries, events, and messages.
- Loading, inaccessible-space, archived read-only, feedback, and empty timeline states are explicit.
- Anniversary creation and deletion, appearance editing, and relationship archiving now use accessible product dialogs instead of native confirm windows.
- Background images still use authenticated file access. Brightness and overlay controls remain available without weakening body-text readability.
- Rebuilt `/event/:id` with event time/place context and the existing Memory collection.

## Verification

- Web Space index and shared-space detail were visually reviewed with representative relationship, archive, timeline, anniversary, event, and message data.
- Compact shared-space detail was visually reviewed at 393 × 852.
- Browser tests cover category visibility, invitations, custom categories, the one-space invariant, multi-tag management, archive confirmation, personal/shared/archive grouping, shared-space timeline, members, anniversaries, messages, and events.
- Compact relationship invitation has no serious or critical WCAG A/AA axe violations.
- `npm run lint:tokens`: passed.
- `npm run build`: passed.
- Complete redesign browser suite: 31 passed.

## Boundaries retained

- No Relationship, Space, Anniversary, Event, Message, file, or permission API was changed.
- Category visibility still changes navigation only and never deletes data.
- Docker, backend migrations, Android project configuration, server deployment, and user data were not touched.
