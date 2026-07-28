// @vitest-environment jsdom

import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const chamadas = vi.hoisted(() => ({
  alterarCargo: vi.fn(),
  alterarConcurso: vi.fn(),
  alterarEdital: vi.fn(),
  alterarGrupo: vi.fn(),
  alterarMateriaDaProva: vi.fn(),
  alterarProva: vi.fn(),
  arquivarConcurso: vi.fn(),
  criarCargo: vi.fn(),
  criarEdital: vi.fn(),
  criarGrupo: vi.fn(),
  criarMateriaDaProva: vi.fn(),
  criarProva: vi.fn(),
  definirEditalPrincipal: vi.fn(),
  excluirCargo: vi.fn(),
  excluirEdital: vi.fn(),
  excluirGrupo: vi.fn(),
  excluirMateriaDaProva: vi.fn(),
  excluirProva: vi.fn(),
  listarCargos: vi.fn(),
  listarEditais: vi.fn(),
  listarGrupos: vi.fn(),
  listarMateriasDaProva: vi.fn(),
  listarMateriasDisponiveis: vi.fn(),
  listarProvas: vi.fn(),
  obterConcurso: vi.fn(),
  selecionarCargo: vi.fn(),
}))

const chamadasConteudo = vi.hoisted(() => ({
  alterarItemDoEdital: vi.fn(),
  criarItemDoEdital: vi.fn(),
  criarMapeamentoDoItem: vi.fn(),
  excluirItemDoEdital: vi.fn(),
  excluirMapeamentoDoItem: vi.fn(),
  listarItensDoEdital: vi.fn(),
  listarMapeamentosDoItem: vi.fn(),
  listarTopicosDisponiveis: vi.fn(),
}))

vi.mock('./apiDeConcursos', () => chamadas)
vi.mock('./apiDeConteudoProgramatico', () => chamadasConteudo)

import ConcursoDetalhePagina from './ConcursoDetalhePagina.vue'

const paginasMontadas: ReturnType<typeof mount>[] = []

const concurso = {
  identificador: 'concurso-1',
  nome: 'Receita Federal',
  situacao: 'PLANEJADO',
  ativo: true,
  criadoEm: '2026-07-18T10:00:00Z',
  atualizadoEm: '2026-07-18T10:00:00Z',
  versao: 0,
}

function criarRoteador() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      {
        path: '/concursos/:identificador',
        component: ConcursoDetalhePagina,
      },
      { path: '/concursos', component: { template: '<div />' } },
      { path: '/dashboard', component: { template: '<div />' } },
    ],
  })
}

async function montar(caminho = '/concursos/concurso-1') {
  const roteador = criarRoteador()
  await roteador.push(caminho)
  await roteador.isReady()
  const pagina = mount(ConcursoDetalhePagina, {
    attachTo: document.body,
    global: { plugins: [roteador] },
  })
  paginasMontadas.push(pagina)
  return pagina
}

describe('ConcursoDetalhePagina', () => {
  afterEach(() => {
    for (const pagina of paginasMontadas.splice(0)) pagina.unmount()
  })

  beforeEach(() => {
    vi.clearAllMocks()
    chamadas.obterConcurso.mockResolvedValue(concurso)
    chamadas.listarEditais.mockResolvedValue([
      {
        identificador: 'edital-1',
        identificadorDoConcurso: 'concurso-1',
        titulo: 'Edital RFB',
        principal: true,
      },
    ])
    chamadas.listarCargos.mockResolvedValue([
      {
        identificador: 'cargo-1',
        identificadorDoConcurso: 'concurso-1',
        nome: 'Auditor',
        nivelDeEscolaridade: 'SUPERIOR',
        selecionado: true,
        ordem: 1,
      },
    ])
    chamadas.listarMateriasDisponiveis.mockResolvedValue({
      itens: [
        { identificador: 'materia-1', nome: 'Direito', arquivada: false },
      ],
    })
    chamadas.listarProvas.mockResolvedValue([
      {
        identificador: 'prova-1',
        identificadorDoCargo: 'cargo-1',
        nome: 'Objetiva',
        tipo: 'OBJETIVA',
        carater: 'ELIMINATORIO',
        ordem: 1,
        dataHoraPrevista: '2026-07-18T15:30:00Z',
      },
    ])
    chamadas.listarGrupos.mockResolvedValue([
      {
        identificador: 'grupo-1',
        identificadorDaProva: 'prova-1',
        nome: 'Conhecimentos especificos',
        ordem: 1,
      },
    ])
    chamadas.listarMateriasDaProva.mockResolvedValue([
      {
        identificador: 'vinculo-1',
        identificadorDoGrupoDeConteudo: 'grupo-1',
        identificadorDaMateria: 'materia-1',
        nomeDaMateria: 'Direito',
        ordem: 1,
      },
    ])
    chamadasConteudo.listarItensDoEdital.mockResolvedValue([
      {
        identificador: 'item-1',
        identificadorDoEdital: 'edital-1',
        identificadorDaMateriaDaProva: 'vinculo-1',
        descricaoOriginal: 'Direitos fundamentais.',
        ordem: 1,
      },
    ])
    chamadasConteudo.listarMapeamentosDoItem.mockResolvedValue([
      {
        identificador: 'mapeamento-1',
        identificadorDoItemDoEdital: 'item-1',
        identificadorDoTopicoDaMateria: 'topico-1',
        nomeDoTopico: 'Direitos fundamentais',
        confirmado: true,
      },
    ])
    chamadasConteudo.listarTopicosDisponiveis.mockResolvedValue({
      itens: [
        {
          identificador: 'topico-1',
          identificadorDaMateria: 'materia-1',
          nome: 'Direitos fundamentais',
          ordem: 1,
          arquivado: false,
        },
      ],
    })
  })

  it('apresenta o resumo, a aba de conteudo e a hierarquia na gaveta', async () => {
    const pagina = await montar()
    await flushPromises()

    expect(pagina.get('h1').text()).toBe('Receita Federal')
    expect(pagina.text()).toContain('Planejado')
    expect(pagina.text()).not.toContain('PLANEJADO')
    expect(pagina.text()).toContain('Como o concurso está organizado')
    expect(pagina.text()).toContain('100%')

    await pagina
      .findAll('.abas-do-concurso button')
      .find((botao) => botao.text().includes('Conteúdo programático'))!
      .trigger('click')

    expect(pagina.get('.itens-oficiais-do-concurso').text()).toContain(
      'Direitos fundamentais.',
    )
    expect(pagina.get('.itens-oficiais-do-concurso').text()).toContain(
      'Mapeado para: Direitos fundamentais',
    )

    await pagina
      .findAll('button')
      .find((botao) => botao.text().trim() === 'Editar estrutura')!
      .trigger('click')

    expect(pagina.get('[role="dialog"]').attributes('aria-label')).toBe(
      'Estrutura do concurso',
    )
    expect(pagina.text()).toContain('3. Auditor')
    expect(pagina.text()).toContain('4. Objetiva')
    expect(pagina.text()).toContain('5. Conhecimentos especificos')
    expect(pagina.text()).toContain('6. Direito')
    expect(pagina.text()).toContain('7. Direitos fundamentais.')
    expect(pagina.text()).toContain('Confirmado')
    expect(pagina.text()).toContain('Direitos fundamentais')
  })

  it('permite navegar entre as abas pelo teclado', async () => {
    const pagina = await montar()
    await flushPromises()

    const abaDaVisao = pagina.get('#aba-do-concurso-visao')
    await abaDaVisao.trigger('keydown', { key: 'ArrowRight' })
    await flushPromises()

    const abaDoConteudo = pagina.get('#aba-do-concurso-conteudo')
    expect(abaDoConteudo.attributes('aria-selected')).toBe('true')
    expect(document.activeElement).toBe(abaDoConteudo.element)
    expect(pagina.get('[role="tabpanel"]').attributes('id')).toBe(
      'painel-do-conteudo-programatico',
    )
  })

  it('prioriza os gaps e mapeia para topico da materia correspondente', async () => {
    chamadasConteudo.listarItensDoEdital.mockResolvedValue([
      {
        identificador: 'item-1',
        identificadorDoEdital: 'edital-1',
        identificadorDaMateriaDaProva: 'vinculo-1',
        descricaoOriginal: 'Direitos fundamentais.',
        ordem: 1,
      },
      {
        identificador: 'item-2',
        identificadorDoEdital: 'edital-1',
        identificadorDaMateriaDaProva: 'vinculo-1',
        descricaoOriginal: 'Poder de policia.',
        ordem: 2,
      },
    ])
    chamadasConteudo.listarMapeamentosDoItem.mockImplementation(
      (identificador: string) =>
        Promise.resolve(
          identificador === 'item-1'
            ? [
                {
                  identificador: 'mapeamento-1',
                  identificadorDoItemDoEdital: 'item-1',
                  identificadorDoTopicoDaMateria: 'topico-1',
                  nomeDoTopico: 'Direitos fundamentais',
                  confirmado: true,
                },
              ]
            : [],
        ),
    )
    chamadasConteudo.criarMapeamentoDoItem.mockResolvedValue({})
    const pagina = await montar()
    await flushPromises()

    await pagina
      .findAll('button')
      .find((botao) => botao.text().trim().startsWith('Mapear itens'))!
      .trigger('click')
    expect(
      pagina.findAll('#item-do-mapeamento option').map((opcao) => opcao.text()),
    ).toEqual(['Poder de policia.'])
    expect(
      pagina.get<HTMLSelectElement>('#item-do-mapeamento').element.value,
    ).toBe('item-2')
    await pagina.get('#topico-do-mapeamento').setValue('topico-1')
    await pagina.get('#formulario-mapeamento form').trigger('submit')
    await flushPromises()

    expect(chamadasConteudo.criarMapeamentoDoItem).toHaveBeenCalledWith(
      'item-2',
      'topico-1',
    )
    expect(chamadasConteudo.listarTopicosDisponiveis).toHaveBeenCalledWith(
      'materia-1',
      expect.any(AbortSignal),
    )
  })

  it('mantem o formulario preenchido quando a API rejeita a criacao', async () => {
    chamadas.criarEdital.mockRejectedValue(new Error('Edital invalido.'))
    const pagina = await montar()
    await flushPromises()

    await pagina
      .findAll('button')
      .find((botao) => botao.text().trim() === 'Editar estrutura')!
      .trigger('click')
    await pagina.get('#titulo-edital').setValue('Edital com erro')
    await pagina.get('#formulario-edital form').trigger('submit')
    await flushPromises()

    expect(pagina.get('[role="alert"]').text()).toContain('Edital invalido.')
    expect(pagina.get<HTMLInputElement>('#titulo-edital').element.value).toBe(
      'Edital com erro',
    )
  })

  it('bloqueia os formularios quando o concurso esta arquivado', async () => {
    chamadas.obterConcurso.mockResolvedValue({
      ...concurso,
      situacao: 'ARQUIVADO',
      ativo: false,
    })
    const pagina = await montar()
    await flushPromises()

    expect(pagina.text()).toContain(
      'Restaure o concurso para alterar sua estrutura.',
    )
    await pagina
      .findAll('button')
      .find((botao) => botao.text().trim() === 'Editar estrutura')!
      .trigger('click')
    expect(
      pagina.get('#formulario-edital fieldset').attributes('disabled'),
    ).toBeDefined()
    expect(
      pagina.get('#formulario-item-do-edital fieldset').attributes('disabled'),
    ).toBeDefined()
  })

  it('confirma a criacao somente para a query concluida', async () => {
    const pagina = await montar('/concursos/concurso-1?novo=concluido')
    await flushPromises()

    expect(pagina.get('[role="status"]').text()).toContain('Concurso criado.')

    pagina.unmount()
    const paginaComQueryAntiga = await montar('/concursos/concurso-1?novo=true')
    await flushPromises()

    expect(paginaComQueryAntiga.find('[role="status"]').exists()).toBe(false)
  })

  it('edita a data da prova no horario local e envia o instante correto', async () => {
    chamadas.alterarProva.mockResolvedValue({})
    const pagina = await montar()
    await flushPromises()

    await pagina
      .findAll('button')
      .find((botao) => botao.text().trim() === 'Editar estrutura')!
      .trigger('click')
    const linhaDaProva = pagina
      .findAll('.item-da-estrutura')
      .find(
        (linha) =>
          linha.find('strong').exists() &&
          linha.find('strong').text() === '4. Objetiva',
      )!
    await linhaDaProva
      .findAll('button')
      .find((botao) => botao.text().trim() === 'Editar')!
      .trigger('click')

    const instanteRecebido = new Date('2026-07-18T15:30:00Z')
    instanteRecebido.setMinutes(
      instanteRecebido.getMinutes() - instanteRecebido.getTimezoneOffset(),
    )
    expect(pagina.get<HTMLInputElement>('#data-prova').element.value).toBe(
      instanteRecebido.toISOString().slice(0, 16),
    )

    await pagina.get('#data-prova').setValue('2026-07-20T09:45')
    await pagina.get('#formulario-prova form').trigger('submit')
    await flushPromises()

    expect(chamadas.alterarProva).toHaveBeenCalledWith(
      'prova-1',
      expect.objectContaining({
        dataHoraPrevista: new Date('2026-07-20T09:45').toISOString(),
      }),
    )
  })
})
