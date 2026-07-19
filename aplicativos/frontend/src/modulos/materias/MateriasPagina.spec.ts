// @vitest-environment jsdom

import { flushPromises, mount } from '@vue/test-utils'
import { defineComponent } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const chamadas = vi.hoisted(() => ({
  alterarMateria: vi.fn(),
  alterarTopico: vi.fn(),
  arquivarMateria: vi.fn(),
  criarMateria: vi.fn(),
  criarTopico: vi.fn(),
  excluirMateria: vi.fn(),
  listarMaterias: vi.fn(),
  listarTodosOsTopicos: vi.fn(),
  listarTodosOsEstudos: vi.fn(),
  obterMateria: vi.fn(),
}))

vi.mock('./apiDeConteudos', () => ({
  alterarMateria: chamadas.alterarMateria,
  alterarTopico: chamadas.alterarTopico,
  arquivarMateria: chamadas.arquivarMateria,
  criarMateria: chamadas.criarMateria,
  criarTopico: chamadas.criarTopico,
  excluirMateria: chamadas.excluirMateria,
  listarMaterias: chamadas.listarMaterias,
  listarTodosOsTopicos: chamadas.listarTodosOsTopicos,
  obterMateria: chamadas.obterMateria,
}))
vi.mock('@/modulos/estudos/apiDeEstudos', () => ({
  listarTodosOsEstudos: chamadas.listarTodosOsEstudos,
}))

import MateriasPagina from './MateriasPagina.vue'

const RouterLink = defineComponent({
  props: { to: { type: String, required: true } },
  template: '<a :href="to"><slot /></a>',
})

describe('MateriasPagina', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    chamadas.listarTodosOsEstudos.mockResolvedValue([])
    chamadas.listarTodosOsTopicos.mockResolvedValue([])
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

    expect(pagina.text()).toContain('Carregando matérias...')

    concluir({
      itens: [],
      pagina: 0,
      tamanho: 12,
      totalDeItens: 0,
      totalDePaginas: 0,
    })
    await flushPromises()

    expect(pagina.text()).toContain('Nenhuma matéria encontrada')
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
    chamadas.criarMateria.mockResolvedValue({
      identificador: 'materia-1',
      nome: 'Direito Constitucional',
      cor: '#128F83',
      arquivada: false,
    })
    const pagina = mount(MateriasPagina, {
      global: { components: { RouterLink } },
    })
    await flushPromises()

    await pagina
      .findAll('button')
      .find((botao) => botao.text().includes('Nova matéria'))!
      .trigger('click')
    await pagina.get('#nome-materia').setValue('Direito Constitucional')
    await pagina.get('#descricao-materia').setValue('Materia principal')
    await pagina.get('#formulario-materia').trigger('submit')
    await flushPromises()

    expect(chamadas.criarMateria).toHaveBeenCalledWith({
      nome: 'Direito Constitucional',
      descricao: 'Materia principal',
      cor: '#128F83',
    })
    expect(pagina.text()).toContain('Direito Constitucional')
    expect(
      pagina
        .findAll('a')
        .some((link) =>
          link.attributes('href')?.startsWith('/materias/materia-1'),
        ),
    ).toBe(true)
  })

  it('cadastra um topico no contexto da materia selecionada', async () => {
    const materia = {
      identificador: 'materia-1',
      nome: 'Direito Constitucional',
      cor: '#0E8F87',
      arquivada: false,
      criadoEm: '2026-07-18T10:00:00Z',
      atualizadoEm: '2026-07-18T10:00:00Z',
      versao: 0,
    }
    chamadas.listarMaterias.mockResolvedValue({
      itens: [materia],
      pagina: 0,
      tamanho: 12,
      totalDeItens: 1,
      totalDePaginas: 1,
    })
    chamadas.listarTodosOsTopicos
      .mockResolvedValueOnce([])
      .mockResolvedValueOnce([
        {
          identificador: 'topico-1',
          identificadorDaMateria: 'materia-1',
          nome: 'Direitos fundamentais',
          ordem: 1,
          arquivado: false,
          criadoEm: '2026-07-18T10:00:00Z',
          atualizadoEm: '2026-07-18T10:00:00Z',
          versao: 0,
        },
      ])
    chamadas.criarTopico.mockResolvedValue({
      identificador: 'topico-1',
      identificadorDaMateria: 'materia-1',
      nome: 'Direitos fundamentais',
      ordem: 1,
      arquivado: false,
    })

    const pagina = mount(MateriasPagina, {
      global: {
        components: { RouterLink },
        stubs: { Teleport: true },
      },
    })
    await flushPromises()

    await pagina
      .findAll('button')
      .find((botao) => botao.text().includes('Novo tópico'))!
      .trigger('click')
    await pagina
      .get('#nome-topico-contextual')
      .setValue('Direitos fundamentais')
    await pagina.get('#formulario-topico-contextual').trigger('submit')
    await flushPromises()

    expect(chamadas.criarTopico).toHaveBeenCalledWith('materia-1', {
      nome: 'Direitos fundamentais',
      descricao: undefined,
      identificadorDoTopicoPai: undefined,
      ordem: 1,
    })
    expect(pagina.text()).toContain('Direitos fundamentais')
  })

  it('abre a materia indicada por uma rota interna recarregada', async () => {
    chamadas.listarMaterias.mockResolvedValue({
      itens: [],
      pagina: 0,
      tamanho: 12,
      totalDeItens: 0,
      totalDePaginas: 0,
    })
    chamadas.obterMateria.mockResolvedValue({
      identificador: 'materia-direta',
      nome: 'Administração Pública',
      cor: '#0E8F87',
      arquivada: false,
      criadoEm: '2026-07-18T10:00:00Z',
      atualizadoEm: '2026-07-18T10:00:00Z',
      versao: 0,
    })

    const pagina = mount(MateriasPagina, {
      props: { identificador: 'materia-direta' },
      global: { components: { RouterLink } },
    })
    await flushPromises()

    expect(chamadas.obterMateria).toHaveBeenCalledWith(
      'materia-direta',
      expect.any(AbortSignal),
    )
    expect(pagina.text()).toContain('Administração Pública')
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
