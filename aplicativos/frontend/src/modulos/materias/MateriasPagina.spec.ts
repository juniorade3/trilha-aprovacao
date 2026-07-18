// @vitest-environment jsdom

import { flushPromises, mount } from '@vue/test-utils'
import { defineComponent } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const chamadas = vi.hoisted(() => ({
  alterarMateria: vi.fn(),
  arquivarMateria: vi.fn(),
  criarMateria: vi.fn(),
  excluirMateria: vi.fn(),
  listarMaterias: vi.fn(),
}))

vi.mock('./apiDeConteudos', () => chamadas)

import MateriasPagina from './MateriasPagina.vue'

const RouterLink = defineComponent({
  props: { to: { type: String, required: true } },
  template: '<a :href="to"><slot /></a>',
})

describe('MateriasPagina', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('apresenta os estados de carregamento e lista vazia', async () => {
    let concluir!: (valor: object) => void
    chamadas.listarMaterias.mockReturnValue(
      new Promise((resolver) => {
        concluir = resolver
      }),
    )

    const pagina = mount(MateriasPagina, {
      global: { components: { RouterLink } },
    })

    expect(pagina.text()).toContain('Carregando materias...')

    concluir({
      itens: [],
      pagina: 0,
      tamanho: 12,
      totalDeItens: 0,
      totalDePaginas: 0,
    })
    await flushPromises()

    expect(pagina.text()).toContain('Nenhuma materia encontrada')
  })

  it('cadastra uma materia e atualiza a lista', async () => {
    chamadas.listarMaterias
      .mockResolvedValueOnce({
        itens: [],
        pagina: 0,
        tamanho: 12,
        totalDeItens: 0,
        totalDePaginas: 0,
      })
      .mockResolvedValueOnce({
        itens: [
          {
            identificador: 'materia-1',
            nome: 'Direito Constitucional',
            cor: '#0E8F87',
            arquivada: false,
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
    chamadas.criarMateria.mockResolvedValue({})
    const pagina = mount(MateriasPagina, {
      global: { components: { RouterLink } },
    })
    await flushPromises()

    await pagina.get('#nome-materia').setValue('Direito Constitucional')
    await pagina.get('#descricao-materia').setValue('Materia principal')
    await pagina.get('#formulario-materia').trigger('submit')
    await flushPromises()

    expect(chamadas.criarMateria).toHaveBeenCalledWith({
      nome: 'Direito Constitucional',
      descricao: 'Materia principal',
      cor: '#0E8F87',
    })
    expect(pagina.text()).toContain('Direito Constitucional')
    expect(pagina.get('a').attributes('href')).toBe('/materias/materia-1')
  })

  it('apresenta o erro recebido da API', async () => {
    chamadas.listarMaterias.mockRejectedValue(
      new Error('Nao foi possivel consultar as materias.'),
    )

    const pagina = mount(MateriasPagina, {
      global: { components: { RouterLink } },
    })
    await flushPromises()

    expect(pagina.get('[role="alert"]').text()).toContain(
      'Nao foi possivel consultar as materias.',
    )
  })
})
