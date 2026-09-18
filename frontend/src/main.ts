import { createApp } from 'vue'
import { createPinia } from 'pinia'
import './styles/main.css'
import App from './App.vue'
import router from './router'
import { applyCachedAppearance } from './utils/appearance'
import { initializeNativeRuntime } from './utils/nativeRuntime'

// V1.6 removes the public demo login. Sign out only legacy demo sessions once,
// while keeping every real user's existing session untouched.
const demoSessionCleanupKey = 'memospace_demo_session_cleanup_v1'
if (!localStorage.getItem(demoSessionCleanupKey)) {
  try {
    const cachedUser = JSON.parse(localStorage.getItem('memospace_user') || 'null')
    if (cachedUser && ['demo', 'mia'].includes(cachedUser.username)) {
      localStorage.removeItem('memospace_token')
      localStorage.removeItem('memospace_user')
    }
  } catch {
    localStorage.removeItem('memospace_user')
  }
  localStorage.setItem(demoSessionCleanupKey, '1')
}

const savedMode = localStorage.getItem('memospace_mode')
if (savedMode && savedMode !== 'system') document.documentElement.dataset.mode = savedMode
applyCachedAppearance()

createApp(App).use(createPinia()).use(router).mount('#app')
router.isReady().then(() => initializeNativeRuntime(router))
