import { createApp } from 'vue'
import App from './App.vue'
import AdminApp from './AdminApp.vue'
import './style.css'

createApp(window.location.pathname.startsWith('/admin') ? AdminApp : App).mount('#app')
