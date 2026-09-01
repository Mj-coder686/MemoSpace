import { createApp } from 'vue'
import { createPinia } from 'pinia'
import './styles/main.css'
import App from './App.vue'
import router from './router'
import { applyCachedAppearance } from './utils/appearance'
import { initializeNativeRuntime } from './utils/nativeRuntime'

const savedMode = localStorage.getItem('memospace_mode')
if (savedMode && savedMode !== 'system') document.documentElement.dataset.mode = savedMode
applyCachedAppearance()

createApp(App).use(createPinia()).use(router).mount('#app')
router.isReady().then(() => initializeNativeRuntime(router))
