import { createApp } from 'vue'
import { createPinia } from 'pinia'
import './styles/main.css'
import App from './App.vue'
import router from './router'

const savedMode = localStorage.getItem('memospace_mode')
if (savedMode && savedMode !== 'system') document.documentElement.dataset.mode = savedMode

createApp(App).use(createPinia()).use(router).mount('#app')
