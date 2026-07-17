import { createRouter, createWebHistory } from 'vue-router'

import InicioPagina from '@/modulos/inicio/InicioPagina.vue'

export default createRouter({
  history: createWebHistory(),
  routes: [{ path: '/', name: 'inicio', component: InicioPagina }],
})
