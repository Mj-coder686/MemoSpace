import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '../views/HomeView.vue'

const AuthView = () => import('../views/AuthView.vue')
const MemoriesView = () => import('../views/MemoriesView.vue')
const MemoryDetailView = () => import('../views/MemoryDetailView.vue')
const PhotosView = () => import('../views/PhotosView.vue')
const CalendarView = () => import('../views/CalendarView.vue')
const MapView = () => import('../views/MapView.vue')
const SpacesView = () => import('../views/SpacesView.vue')
const SpaceDetailView = () => import('../views/SpaceDetailView.vue')
const ExploreView = () => import('../views/ExploreView.vue')
const NotificationsView = () => import('../views/NotificationsView.vue')
const SettingsView = () => import('../views/SettingsView.vue')
const UserView = () => import('../views/UserView.vue')
const EventView = () => import('../views/EventView.vue')
const RelationshipCategoriesView = () => import('../views/RelationshipCategoriesView.vue')
const RelationshipCategoryView = () => import('../views/RelationshipCategoryView.vue')
const RelationshipManageView = () => import('../views/RelationshipManageView.vue')
const FriendsView = () => import('../views/FriendsView.vue')
const ChatView = () => import('../views/ChatView.vue')
const RemindersView = () => import('../views/RemindersView.vue')
const AdminLoginView = () => import('../views/AdminLoginView.vue')
const AdminDashboardView = () => import('../views/AdminDashboardView.vue')
const developmentRoutes = import.meta.env.DEV
  ? [
      { path: '/__design-system', component: () => import('../views/DesignSystemPlaygroundView.vue'), meta: { public: true, designSystem: true } },
      { path: '/__shell', component: () => import('../views/DesignShellPlaygroundView.vue'), meta: { designSystem: true } },
    ]
  : []

const router = createRouter({
  history: createWebHistory(),
  scrollBehavior: (to, from, savedPosition) => {
    if (savedPosition) return savedPosition
    if (to.hash) return { el: to.hash, behavior: 'smooth' }
    if (to.path === from.path) return false
    return { top: 0 }
  },
  routes: [
    { path: '/', redirect: '/home' },
    { path: '/login', component: AuthView, meta: { public: true } },
    { path: '/register', component: AuthView, meta: { public: true } },
    { path: '/admin/login', component: AdminLoginView, meta: { admin: true, adminPublic: true } },
    { path: '/admin', component: AdminDashboardView, meta: { admin: true } },
    { path: '/home', component: HomeView },
    { path: '/memories', component: MemoriesView },
    { path: '/memory/:id', component: MemoryDetailView },
    { path: '/photos', component: PhotosView },
    { path: '/calendar', component: CalendarView },
    { path: '/map', component: MapView },
    { path: '/spaces', component: SpacesView },
    { path: '/space/:id', component: SpaceDetailView },
    { path: '/relationships', component: RelationshipCategoriesView },
    { path: '/relationships/manage', component: RelationshipManageView },
    { path: '/relationships/category/:id', component: RelationshipCategoryView },
    { path: '/friends', component: FriendsView },
    { path: '/chat/:friendId', component: ChatView },
    { path: '/reminders', component: RemindersView },
    { path: '/event/:id', component: EventView },
    { path: '/explore', component: ExploreView },
    { path: '/notifications', component: NotificationsView },
    { path: '/settings', component: SettingsView },
    { path: '/user/:id', component: UserView },
    ...developmentRoutes
  ]
})

const chunkRecoveryKey = 'memospace_route_chunk_recovery'

router.onError((error, to) => {
  const message = error instanceof Error ? error.message : String(error)
  if (!/failed to fetch dynamically imported module|loading chunk|importing a module script failed/i.test(message)) return
  if (sessionStorage.getItem(chunkRecoveryKey) === to.fullPath) return
  sessionStorage.setItem(chunkRecoveryKey, to.fullPath)
  window.location.assign(to.fullPath)
})

router.afterEach(() => sessionStorage.removeItem(chunkRecoveryKey))

router.beforeEach((to) => {
  if (to.meta.designSystem) return
  if (to.meta.admin) {
    const adminToken = localStorage.getItem('memospace_admin_token')
    if (!to.meta.adminPublic && !adminToken) return '/admin/login'
    if (to.meta.adminPublic && adminToken) return '/admin'
    return
  }
  const token = localStorage.getItem('memospace_token')
  if (!to.meta.public && !token) return '/login'
  if (to.meta.public && token) return '/home'
})

export default router
