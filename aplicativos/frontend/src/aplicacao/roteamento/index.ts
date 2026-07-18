import {
  createRouter,
  createWebHistory,
  type RouteLocationNormalized,
  type RouteLocationRaw,
} from 'vue-router'

import { usarSessao } from '@/aplicacao/estado/sessao'
import InicioPagina from '@/modulos/inicio/InicioPagina.vue'
import LoginPagina from '@/modulos/autenticacao/LoginPagina.vue'
import CadastroPagina from '@/modulos/autenticacao/CadastroPagina.vue'

export async function protegerRotas(
  destino: Pick<RouteLocationNormalized, 'meta' | 'fullPath'>,
): Promise<true | RouteLocationRaw> {
  if (!destino.meta.requerAutenticacao) return true

  const sessao = usarSessao()
  if (sessao.autenticada) return true

  try {
    await sessao.atualizar()
    return true
  } catch {
    sessao.limpar()
    return {
      name: 'login',
      query: { redirecionar: destino.fullPath },
    }
  }
}

const roteador = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/dashboard' },
    {
      path: '/dashboard',
      name: 'dashboard',
      component: InicioPagina,
      meta: { requerAutenticacao: true },
    },
    { path: '/login', name: 'login', component: LoginPagina },
    { path: '/cadastro', name: 'cadastro', component: CadastroPagina },
  ],
})

roteador.beforeEach(protegerRotas)

export default roteador
