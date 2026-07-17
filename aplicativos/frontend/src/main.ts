import 'bootstrap-icons/font/bootstrap-icons.css'
import 'bootstrap/dist/css/bootstrap.min.css'
import '@/compartilhado/estilos/principal.scss'

import { createApp } from 'vue'
import { createPinia } from 'pinia'

import Aplicacao from './Aplicacao.vue'
import roteador from './aplicacao/roteamento'

createApp(Aplicacao).use(createPinia()).use(roteador).mount('#aplicacao')
