import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import './styles/base.css'
import './styles/dashboard.css'

createApp(App).use(router).mount('#app')
