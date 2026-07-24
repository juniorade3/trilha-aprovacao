// @vitest-environment jsdom

import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

const chamadas = vi.hoisted(() => ({
  adicionarCobertura: vi.fn(),
  alterarMaterialDeEstudo: vi.fn(),
  criarMaterialDeEstudo: vi.fn(),
  definirArquivamentoDoMaterial: vi.fn(),
  excluirMaterialDeEstudo: vi.fn(),
  listarCoberturas: vi.fn(),
  listarTodosOsMateriaisDeEstudo: vi.fn(),
  removerCobertura: vi.fn(),
  listarTodasAsMaterias: vi.fn(),
  listarTodosOsTopicos: vi.fn(),
}))

vi.mock('./apiDeEstudos', () => ({
  adicionarCobertura: chamadas.adicionarCobertura,
  alterarMaterialDeEstudo: chamadas.alterarMaterialDeEstudo,
  criarMaterialDeEstudo: chamadas.criarMaterialDeEstudo,
  definirArquivamentoDoMaterial: chamadas.definirArquivamentoDoMaterial,
  excluirMaterialDeEstudo: chamadas.excluirMaterialDeEstudo,
  listarCoberturas: chamadas.listarCoberturas,
  listarTodosOsMateriaisDeEstudo: chamadas.listarTodosOsMateriaisDeEstudo,
  removerCobertura: chamadas.removerCobertura,
}))
vi.mock('@/modulos/materias/apiDeConteudos', () => ({
  listarTodasAsMaterias: chamadas.listarTodasAsMaterias,
  listarTodosOsTopicos: chamadas.listarTodosOsTopicos,
}))

import MateriaisDeEstudoPagina from './MateriaisDeEstudoPagina.vue'

const material = {
  identificador: 'material-1',
  titulo: 'Curso de Constitucional',
  tipo: 'AULA',
  descricao: 'Curso completo',
  arquivado: false,
  criadoEm: '2026-07-18T10:00:00Z',
  atualizadoEm: '2026-07-18T10:00:00Z',
  versao: 0,
}
const materialEmPdf = {
  identificador: 'material-2',
  titulo: 'Apostila de Administrativo',
  tipo: 'PDF',
  descricao: 'Apostila completa',
  fonte: 'Editora Exemplo',
  arquivado: false,
  criadoEm: '2026-07-17T10:00:00Z',
  atualizadoEm: '2026-07-17T10:00:00Z',
  versao: 0,
}
const materia = {
  identificador: 'materia-1',
  nome: 'Direito Constitucional',
  arquivada: false,
}
const topico = {
  identificador: 'topico-1',
  identificadorDaMateria: 'materia-1',
  nome: 'Direitos fundamentais',
  arquivado: false,
}

async function montarPagina(caminho = '/materiais') {
  const roteador = createRouter({
    history: createMemoryHistory(),
    routes: [
      {
        path: '/materiais',
        component: MateriaisDeEstudoPagina,
      },
      {
        path: '/materiais/:identificador',
        component: MateriaisDeEstudoPagina,
      },
    ],
  })
  await roteador.push(caminho)
  await roteador.isReady()
  const pagina = mount(MateriaisDeEstudoPagina, {
    global: {
      plugins: [roteador],
      stubs: { teleport: true },
    },
  })
  await flushPromises()
  return pagina
}

function criarPromessaControlada<T>() {
  let resolver!: (valor: T) => void
  const promessa = new Promise<T>((concluir) => {
    resolver = concluir
  })
  return { promessa, resolver }
}

describe('MateriaisDeEstudoPagina', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    chamadas.listarTodosOsMateriaisDeEstudo.mockResolvedValue([material])
    chamadas.listarTodasAsMaterias.mockResolvedValue([materia])
    chamadas.listarCoberturas.mockResolvedValue([])
    chamadas.listarTodosOsTopicos.mockResolvedValue([topico])
    chamadas.criarMaterialDeEstudo.mockResolvedValue(material)
    chamadas.adicionarCobertura.mockResolvedValue({
      identificador: 'cobertura-1',
      identificadorDoMaterial: 'material-1',
      identificadorDoTopico: 'topico-1',
      nomeDoTopico: topico.nome,
    })
  })

  it('lista e cadastra um material de estudo', async () => {
    const pagina = await montarPagina()

    expect(pagina.text()).toContain('Curso de Constitucional')
    await pagina
      .findAll('button')
      .find((botao) => botao.text().includes('Novo material'))!
      .trigger('click')
    await pagina.get('#titulo-material').setValue('Novo PDF')
    await pagina.get('#tipo-material').setValue('PDF')
    await pagina
      .get('#endereco-material')
      .setValue('https://exemplo.test/material.pdf')
    await pagina.get('#formulario-material').trigger('submit')
    await flushPromises()

    expect(chamadas.criarMaterialDeEstudo).toHaveBeenCalledWith(
      expect.objectContaining({
        titulo: 'Novo PDF',
        tipo: 'PDF',
        endereco: 'https://exemplo.test/material.pdf',
      }),
    )
  })

  it('vincula um topico coberto pelo material selecionado', async () => {
    const pagina = await montarPagina()

    await pagina
      .findAll('button')
      .find((botao) => botao.text().includes('Ver cobertura'))!
      .trigger('click')
    await flushPromises()
    await pagina.get('#materia-cobertura').setValue('materia-1')
    await flushPromises()
    await pagina.get('#topico-cobertura').setValue('topico-1')
    await pagina
      .get('#topico-cobertura')
      .element.closest('form')!
      .dispatchEvent(new Event('submit'))
    await flushPromises()

    expect(chamadas.adicionarCobertura).toHaveBeenCalledWith(
      'material-1',
      'topico-1',
    )
  })

  it('filtra por tipo, ordena os materiais e mostra a atualizacao', async () => {
    chamadas.listarTodosOsMateriaisDeEstudo.mockResolvedValue([
      material,
      materialEmPdf,
    ])
    const pagina = await montarPagina()

    expect(pagina.text()).toContain('Atualizado em')
    expect(
      pagina.findAll('.cartao-do-material h2').map((titulo) => titulo.text()),
    ).toEqual(['Curso de Constitucional', 'Apostila de Administrativo'])

    await pagina.get('#ordenacao-materiais').setValue('TITULO')
    expect(
      pagina.findAll('.cartao-do-material h2').map((titulo) => titulo.text()),
    ).toEqual(['Apostila de Administrativo', 'Curso de Constitucional'])

    await pagina.get('#tipo-material-filtro').setValue('AULA')
    expect(pagina.findAll('.cartao-do-material')).toHaveLength(1)
    expect(pagina.text()).toContain('Curso de Constitucional')
    expect(pagina.text()).not.toContain('Apostila de Administrativo')

    expect(pagina.get('#pesquisa-material').attributes('type')).toBe('search')
    expect(pagina.get('#materiais-arquivados').attributes('id')).toBe(
      'materiais-arquivados',
    )
  })

  it('abre a cobertura indicada pela rota direta', async () => {
    chamadas.listarTodosOsMateriaisDeEstudo.mockResolvedValue([
      material,
      materialEmPdf,
    ])

    const pagina = await montarPagina('/materiais/material-2')

    expect(pagina.get('[role="dialog"]').text()).toContain(
      'Apostila de Administrativo',
    )
    expect(chamadas.listarTodosOsMateriaisDeEstudo).toHaveBeenCalledWith(
      '',
      true,
      expect.any(AbortSignal),
      undefined,
    )
  })

  it('filtra pelo topico vindo do planejamento e abre o unico material', async () => {
    const pagina = await montarPagina('/materiais?topico=topico-1')

    expect(chamadas.listarTodosOsMateriaisDeEstudo).toHaveBeenCalledWith(
      '',
      false,
      expect.any(AbortSignal),
      'topico-1',
    )
    expect(pagina.text()).toContain(
      'Mostrando somente os materiais ativos que cobrem o tópico',
    )
    expect(pagina.get('[role="dialog"]').text()).toContain(
      'Curso de Constitucional',
    )
  })

  it('descarta uma cobertura antiga quando outra selecao termina primeiro', async () => {
    chamadas.listarTodosOsMateriaisDeEstudo.mockResolvedValue([
      material,
      materialEmPdf,
    ])
    const pagina = await montarPagina()
    chamadas.listarCoberturas.mockReset()

    const coberturaDoPrimeiro =
      criarPromessaControlada<
        Awaited<ReturnType<typeof chamadas.listarCoberturas>>
      >()
    const coberturaDoSegundo =
      criarPromessaControlada<
        Awaited<ReturnType<typeof chamadas.listarCoberturas>>
      >()
    chamadas.listarCoberturas.mockImplementation((identificador: string) =>
      identificador === material.identificador
        ? coberturaDoPrimeiro.promessa
        : coberturaDoSegundo.promessa,
    )

    await pagina
      .get(`[aria-label="Ver cobertura de ${material.titulo}"]`)
      .trigger('click')
    await pagina
      .get(`[aria-label="Ver cobertura de ${materialEmPdf.titulo}"]`)
      .trigger('click')

    coberturaDoSegundo.resolver([
      {
        identificador: 'cobertura-2',
        identificadorDoMaterial: materialEmPdf.identificador,
        identificadorDoTopico: 'topico-2',
        nomeDoTopico: 'Licitações',
        criadoEm: '2026-07-19T10:00:00Z',
      },
    ])
    await flushPromises()
    expect(pagina.get('[role="dialog"]').text()).toContain('Licitações')

    coberturaDoPrimeiro.resolver([
      {
        identificador: 'cobertura-1',
        identificadorDoMaterial: material.identificador,
        identificadorDoTopico: 'topico-1',
        nomeDoTopico: 'Direitos fundamentais',
        criadoEm: '2026-07-18T10:00:00Z',
      },
    ])
    await flushPromises()
    expect(pagina.get('[role="dialog"]').text()).toContain('Licitações')
    expect(pagina.get('[role="dialog"]').text()).not.toContain(
      'Direitos fundamentais',
    )
  })
})
