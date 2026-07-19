import {
  createRouter,
  createWebHistory,
  type RouteLocationNormalized,
  type RouteLocationRaw,
} from 'vue-router'

import { usarSessao } from '@/aplicacao/estado/sessao'
import LayoutDeAutenticacao from '@/aplicacao/layouts/LayoutDeAutenticacao.vue'
import LayoutPrincipal from '@/aplicacao/layouts/LayoutPrincipal.vue'
import InicioPagina from '@/modulos/inicio/InicioPagina.vue'
import LoginPagina from '@/modulos/autenticacao/LoginPagina.vue'
import CadastroPagina from '@/modulos/autenticacao/CadastroPagina.vue'
import ConcursoDetalhePagina from '@/modulos/concursos/ConcursoDetalhePagina.vue'
import ConcursoNovoPagina from '@/modulos/concursos/ConcursoNovoPagina.vue'
import ConcursosPagina from '@/modulos/concursos/ConcursosPagina.vue'
import MateriasPagina from '@/modulos/materias/MateriasPagina.vue'
import MateriaisDeEstudoPagina from '@/modulos/estudos/MateriaisDeEstudoPagina.vue'
import EstudosPagina from '@/modulos/estudos/EstudosPagina.vue'

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
  scrollBehavior: (_destino, _origem, posicaoSalva) =>
    posicaoSalva ?? { top: 0 },
  routes: [
    {
      path: '/',
      component: LayoutPrincipal,
      meta: { requerAutenticacao: true },
      children: [
        { path: '', redirect: '/dashboard' },
        { path: 'dashboard', name: 'dashboard', component: InicioPagina },
        { path: 'concursos', name: 'concursos', component: ConcursosPagina },
        {
          path: 'concursos/novo',
          name: 'concurso-novo',
          component: ConcursoNovoPagina,
        },
        {
          path: 'concursos/:identificador',
          name: 'concurso-detalhe',
          component: ConcursoDetalhePagina,
        },
        { path: 'materias', name: 'materias', component: MateriasPagina },
        {
          path: 'materiais',
          name: 'materiais-de-estudo',
          component: MateriaisDeEstudoPagina,
        },
        { path: 'estudos', name: 'estudos', component: EstudosPagina },
        {
          path: 'materiais/:identificador',
          name: 'material-detalhe',
          component: MateriaisDeEstudoPagina,
        },
        {
          path: 'estudos/novo',
          name: 'estudo-novo',
          component: EstudosPagina,
          props: { abrirRegistroRapidoAoEntrar: true },
        },
        {
          path: 'materias/:identificador',
          name: 'materia-detalhe',
          component: MateriasPagina,
          props: true,
        },
      ],
    },
    {
      path: '/',
      component: LayoutDeAutenticacao,
      children: [
        { path: 'login', name: 'login', component: LoginPagina },
        { path: 'cadastro', name: 'cadastro', component: CadastroPagina },
      ],
    },
  ],
})

roteador.beforeEach(protegerRotas)

export default roteador
