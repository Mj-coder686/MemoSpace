# Phase 10 Public Feed and User Profiles

## Public activity

- Rebuilt `/explore` as a public activity surface rather than a mixed search/dashboard page.
- Latest and Following use the existing feed scope and remain separate from people search.
- Search results stay in their own people index while the public Memory feed remains visible below.
- Follow is a secondary public-feed action; relationship binding remains an explicit consent-based flow.
- Empty Following state recommends people search without implying access to private content.
- Feed Memories continue to use the shared authenticated `MemoryCard` and existing public visibility rules.

## User profile

- Rebuilt `/user/:id` with one shared profile skeleton and distinct self/other actions.
- Other profiles expose Follow and Relationship actions separately and show only public Memories returned by the existing public feed.
- The page does not render hidden placeholders or imply the amount of private content.
- Own profile exposes clear Memory, Photo, Space, and Settings entry points and may show the signed-in user's own recent archive.
- Memo ID copying, location, biography, and follower statistics remain available.

## Verification

- Web public Feed and Compact other-user profile were visually reviewed with representative search, follow, and public Memory data.
- Missing avatars now use a consistent circular identity treatment rather than a floating initial.
- Browser tests cover search/Feed separation, Follow scope, relationship consent routing, truthful Following empty state, public-only profile filtering, self-profile shortcuts, Compact overflow, and Compact WCAG A/AA checks.
- `npm run lint:tokens`: passed.
- `npm run build`: passed.
- Complete redesign browser suite: 39 passed.

## Boundaries retained

- No Feed, User, Follow, Relationship, Memory, visibility, or media API was changed.
- Private Memory counts or placeholders are not exposed to another user's profile.
- Docker, backend migrations, Android project configuration, server deployment, and user data were not touched.
