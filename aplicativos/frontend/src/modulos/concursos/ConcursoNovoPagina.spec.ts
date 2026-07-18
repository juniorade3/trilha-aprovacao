// @vitest-environment jsdom

import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const { criarConcurso } = vi.hoisted(() => ({ criarConcurso: vi.fn() }))

vi.mock('./apiDeConcursos', () => ({ criarConcurso }))

import ConcursoNovoPagina from './ConcursoNovoPagina.vue'

function criarRoteador() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/concursos/novo', component: ConcursoNovoPagina },
      {
        path: '/concursos/:identificador',
        component: { template: '<div />' },
      },
      { path: '/concursos', component: { template: '<div />' } },
    ],
  })
}

describe('ConcursoNovoPagina', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('salva apenas os dados gerais e segue para a montagem gradual', async () => {
    criarConcurso.mockResolvedValue({ identificador: 'concurso-1' })
    const roteador = criarRoteador()
    await roteador.push('/concursos/novo')
    await roteador.isReady()
    const pagina = mount(ConcursoNovoPagina, {
      global: { plugins: [roteador] },
    })

    await pagina.get('#nome-concurso').setValue('Receita Federal')
    await pagina.get('#orgao-concurso').setValue('RFB')
    await pagina.get('form').trigger('submit')
    await flushPromises()

    expect(criarConcurso).toHaveBeenCalledWith(
      expect.objectContaining({
        nome: 'Receita Federal',
        orgao: 'RFB',
        situacao: 'PLANEJADO',
      }),
    )
    expect(roteador.currentRoute.value.fullPath).toBe(
      '/concursos/concurso-1?novo=true',
    )
  })
})
