import { createRouter, createWebHistory } from 'vue-router'

const AuthView = () => import('../views/AuthView.vue')
const HomeView = () => import('../views/HomeView.vue')
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

const router = createRouter({
  history: createWebHistory(),
  scrollBehavior: () => ({ top: 0 }),
  routes: [
    { path: '/', redirect: '/home' },
    { path: '/login', component: AuthView, meta: { public: true } },
    { path: '/register', component: AuthView, meta: { public: true } },
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
    { path: '/user/:id', component: UserView }
  ]
})

router.beforeEach((to) => {
  const token = localStorage.getItem('memospace_token')
  if (!to.meta.public && !token) return '/login'
  if (to.meta.public && token) return '/home'
})

export default router
