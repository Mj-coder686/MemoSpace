import type { RouteLocationNormalizedLoaded } from 'vue-router'

export type NavigationItem = { label: string; to: string; domain: 'home' | 'memory' | 'space' | 'relationship' | 'feed' }

export const webPrimaryNavigation: NavigationItem[] = [
  { label: '首页', to: '/home', domain: 'home' },
  { label: '记忆', to: '/memories', domain: 'memory' },
  { label: '空间', to: '/spaces', domain: 'space' },
  { label: '关系', to: '/relationships', domain: 'relationship' },
  { label: '动态', to: '/explore', domain: 'feed' },
]

export const compactPrimaryNavigation = webPrimaryNavigation.filter((item) => item.domain !== 'space')

export const memorySecondaryNavigation = [
  { label: '时间轴', to: '/memories' },
  { label: '相册', to: '/photos' },
  { label: '日历', to: '/calendar' },
  { label: '地图', to: '/map' },
]

export const relationshipSecondaryNavigation = [
  { label: '分类', to: '/relationships' },
  { label: '空间', to: '/spaces' },
  { label: '好友', to: '/friends' },
]

const domainMatchers: Record<NavigationItem['domain'], RegExp> = {
  home: /^\/home(?:\/|$)/,
  memory: /^\/(?:memories|memory\/|photos|calendar|map)(?:\/|$)?/,
  space: /^\/(?:spaces|space\/|event\/)(?:\/|$)?/,
  relationship: /^\/(?:relationships|friends|chat\/)(?:\/|$)?/,
  feed: /^\/(?:explore|user\/)(?:\/|$)?/,
}

export const isDomainActive = (path: string, domain: NavigationItem['domain']) => domainMatchers[domain].test(path)
export const isCompactNavigationActive = (path: string, domain: NavigationItem['domain']) => domain === 'relationship'
  ? isDomainActive(path, 'relationship') || isDomainActive(path, 'space')
  : isDomainActive(path, domain)

export const secondaryNavigationFor = (path: string) => {
  if (/^\/(?:memories|photos|calendar|map)$/.test(path)) return memorySecondaryNavigation
  if (/^\/(?:relationships|spaces|friends)$/.test(path)) return relationshipSecondaryNavigation
  return []
}

const pageTitles: Array<[RegExp, string]> = [
  [/^\/home$/, '首页'],
  [/^\/memories$/, '记忆'],
  [/^\/memory\//, '记忆详情'],
  [/^\/photos$/, '相册'],
  [/^\/calendar$/, '日历'],
  [/^\/map$/, '地图'],
  [/^\/spaces$/, '共同空间'],
  [/^\/space\//, '空间详情'],
  [/^\/event\//, '共同事件'],
  [/^\/relationships$/, '关系分类'],
  [/^\/relationships\/category\//, '分类详情'],
  [/^\/relationships\/manage$/, '关系管理'],
  [/^\/friends$/, '好友'],
  [/^\/chat\//, '私聊'],
  [/^\/explore$/, '动态'],
  [/^\/user\//, '个人主页'],
  [/^\/reminders$/, '重要提醒'],
  [/^\/notifications$/, '通知'],
  [/^\/settings$/, '设置'],
]

export const pageTitleFor = (route: Pick<RouteLocationNormalizedLoaded, 'path'>) => pageTitles.find(([pattern]) => pattern.test(route.path))?.[1] || '拾光空间'

export const compactBackFallback = (path: string) => {
  if (/^\/memory\//.test(path)) return '/memories'
  if (/^\/(?:space|event)\//.test(path)) return '/spaces'
  if (/^\/relationships\/(?:category|manage)/.test(path)) return '/relationships'
  if (/^\/chat\//.test(path)) return '/friends'
  if (/^\/user\//.test(path)) return '/explore'
  return ''
}
