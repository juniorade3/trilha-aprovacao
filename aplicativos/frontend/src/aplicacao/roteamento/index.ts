import { createRouter, createWebHistory } from 'vue-router'

import InicioPagina from '@/modulos/inicio/InicioPagina.vue'
import LoginPagina from '@/modulos/autenticacao/LoginPagina.vue'
import CadastroPagina from '@/modulos/autenticacao/CadastroPagina.vue'

export default createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'inicio', component: InicioPagina },
    { path: '/login', name: 'login', component: LoginPagina },
    { path: '/cadastro', name: 'cadastro', component: CadastroPagina },
  ],
})
