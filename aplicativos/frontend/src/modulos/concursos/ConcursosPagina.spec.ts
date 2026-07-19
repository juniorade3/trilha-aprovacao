// @vitest-environment jsdom

import { flushPromises, mount } from '@vue/test-utils'
import { defineComponent } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const chamadas = vi.hoisted(() => ({
  ativarConcurso: vi.fn(),
  arquivarConcurso: vi.fn(),
  excluirConcurso: vi.fn(),
  listarConcursos: vi.fn(),
}))

vi.mock('./apiDeConcursos', () => chamadas)

import ConcursosPagina from './ConcursosPagina.vue'

const RouterLink = defineComponent({
  props: { to: { type: String, required: true } },
  template: '<a :href="to"><slot /></a>',
})

describe('ConcursosPagina', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('apresenta carregamento e estado vazio', async () => {
    let concluir!: (valor: object) => void
    chamadas.listarConcursos.mockReturnValue(
      new Promise((resolver) => {
        concluir = resolver
      }),
    )
    const pagina = mount(ConcursosPagina, {
      global: { components: { RouterLink } },
    })

    expect(pagina.text()).toContain('Carregando concursos...')
    concluir({
      itens: [],
      pagina: 0,
      tamanho: 12,
      totalDeItens: 0,
      totalDePaginas: 0,
    })
    await flushPromises()

    expect(pagina.text()).toContain('Nenhum concurso encontrado')
  })

  it('lista e ativa um concurso', async () => {
    chamadas.listarConcursos.mockResolvedValue({
      itens: [
        {
          identificador: 'concurso-1',
          nome: 'Receita Federal',
          situacao: 'PLANEJADO',
          ativo: false,
          criadoEm: '2026-07-18T10:00:00Z',
          atualizadoEm: '2026-07-18T10:00:00Z',
          versao: 0,
        },
      ],
      pagina: 0,
      tamanho: 12,
      totalDeItens: 1,
      totalDePaginas: 1,
    })
    chamadas.ativarConcurso.mockResolvedValue({})
    const pagina = mount(ConcursosPagina, {
      global: { components: { RouterLink } },
    })
    await flushPromises()

    expect(pagina.text()).toContain('Receita Federal')
    expect(
      pagina.get('a[href="/concursos/concurso-1"]').attributes('href'),
    ).toBe('/concursos/concurso-1')
    await pagina
      .findAll('button')
      .find((botao) => botao.text() === 'Tornar ativo')!
      .trigger('click')
    await flushPromises()

    expect(chamadas.ativarConcurso).toHaveBeenCalledWith('concurso-1')
  })
})
