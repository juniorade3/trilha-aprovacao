// @vitest-environment jsdom

import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import { ErroDaApi } from '@/compartilhado/api/clienteHttp'

const chamadas = vi.hoisted(() => ({
  adicionarBloco: vi.fn(),
  alterarBloco: vi.fn(),
  alterarDisponibilidades: vi.fn(),
  ativarPlanoSemanal: vi.fn(),
  criarPlanoSemanal: vi.fn(),
  excluirBloco: vi.fn(),
  listarTodasAsMaterias: vi.fn(),
  listarTodosOsTopicos: vi.fn(),
  obterPlanoSemanal: vi.fn(),
  reordenarBlocos: vi.fn(),
}))

vi.mock('./apiDePlanejamento', () => ({
  adicionarBloco: chamadas.adicionarBloco,
  alterarBloco: chamadas.alterarBloco,
  alterarDisponibilidades: chamadas.alterarDisponibilidades,
  ativarPlanoSemanal: chamadas.ativarPlanoSemanal,
  criarPlanoSemanal: chamadas.criarPlanoSemanal,
  excluirBloco: chamadas.excluirBloco,
  obterPlanoSemanal: chamadas.obterPlanoSemanal,
  reordenarBlocos: chamadas.reordenarBlocos,
}))

vi.mock('@/modulos/materias/apiDeConteudos', () => ({
  listarTodasAsMaterias: chamadas.listarTodasAsMaterias,
  listarTodosOsTopicos: chamadas.listarTodosOsTopicos,
}))

import PlanejamentoSemanaPagina from './PlanejamentoSemanaPagina.vue'

const inicio = '2026-07-20'
const paginasMontadas: ReturnType<typeof mount>[] = []

function planoSemanal(
  minutosDaSegunda = 0,
  blocos: object[] = [],
  estado = 'RASCUNHO',
) {
  const planejados = blocos.reduce(
    (total, bloco) =>
      total +
      Number(
        (bloco as { duracaoPrevistaEmMinutos?: number })
          .duracaoPrevistaEmMinutos ?? 0,
      ),
    0,
  )
  return {
    identificador: 'plano-1',
    dataInicial: inicio,
    dataFinal: '2026-07-26',
    estado,
    disponibilidades: Array.from({ length: 7 }, (_, indice) => ({
      identificador: `dia-${indice}`,
      data: `2026-07-${String(20 + indice).padStart(2, '0')}`,
      minutosDisponiveis: indice === 0 ? minutosDaSegunda : 0,
      atualizadoEm: '2026-07-19T12:00:00Z',
      versao: 0,
    })),
    blocos,
    totalDeMinutosDisponiveis: minutosDaSegunda,
    totalDeMinutosPlanejados: planejados,
    quantidadeDeBlocos: blocos.length,
    possuiExcesso: planejados > minutosDaSegunda,
    resumosDosDias: [],
    criadoEm: '2026-07-19T12:00:00Z',
    atualizadoEm: '2026-07-19T12:00:00Z',
    versao: 0,
  }
}

function blocoDeEstudo(
  identificador = 'bloco-1',
  titulo = 'Banco de dados',
  ordem = 1,
) {
  return {
    identificador,
    identificadorDoPlano: 'plano-1',
    titulo,
    tipoDeAtividade: 'TEORIA',
    data: inicio,
    duracaoPrevistaEmMinutos: 120,
    ordem,
    estado: 'PLANEJADO',
    criadoEm: '2026-07-19T12:00:00Z',
    atualizadoEm: '2026-07-19T12:00:00Z',
    versao: 0,
  }
}

async function montarPagina() {
  const roteador = createRouter({
    history: createMemoryHistory(),
    routes: [
      {
        path: '/planejamento/semana',
        component: PlanejamentoSemanaPagina,
      },
      { path: '/estudos', component: { template: '<div>Histórico</div>' } },
    ],
  })
  await roteador.push(`/planejamento/semana?inicio=${inicio}`)
  await roteador.isReady()
  const pagina = mount(PlanejamentoSemanaPagina, {
    attachTo: document.body,
    global: {
      plugins: [roteador],
      stubs: { teleport: true },
    },
  })
  paginasMontadas.push(pagina)
  await flushPromises()
  return { pagina, roteador }
}

describe('PlanejamentoSemanaPagina', () => {
  afterEach(() => {
    for (const pagina of paginasMontadas.splice(0)) pagina.unmount()
  })

  beforeEach(() => {
    vi.clearAllMocks()
    chamadas.obterPlanoSemanal.mockResolvedValue(planoSemanal())
    chamadas.criarPlanoSemanal.mockResolvedValue(planoSemanal())
    chamadas.alterarDisponibilidades.mockResolvedValue(planoSemanal(180))
    chamadas.ativarPlanoSemanal.mockResolvedValue(planoSemanal())
    chamadas.listarTodasAsMaterias.mockResolvedValue([
      {
        identificador: 'materia-1',
        nome: 'Banco de dados',
        arquivada: false,
      },
    ])
    chamadas.listarTodosOsTopicos.mockResolvedValue([
      {
        identificador: 'topico-1',
        identificadorDaMateria: 'materia-1',
        nome: 'Modelagem relacional',
        arquivado: false,
      },
    ])
    chamadas.adicionarBloco.mockResolvedValue(blocoDeEstudo())
    chamadas.alterarBloco.mockResolvedValue(blocoDeEstudo())
    chamadas.excluirBloco.mockResolvedValue(undefined)
    chamadas.reordenarBlocos.mockResolvedValue(planoSemanal())
  })

  it('carrega e salva os sete dias da semana', async () => {
    const { pagina } = await montarPagina()

    expect(chamadas.obterPlanoSemanal).toHaveBeenCalledWith(inicio)
    expect(pagina.findAll('.cartao-de-disponibilidade')).toHaveLength(7)
    expect(pagina.text()).toContain('Histórico')
    await pagina.get('#disponibilidade-2026-07-20').setValue('180')
    await pagina.get('form').trigger('submit')
    await flushPromises()

    expect(chamadas.alterarDisponibilidades).toHaveBeenCalledWith(
      'plano-1',
      expect.arrayContaining([
        { data: '2026-07-20', minutosDisponiveis: 180 },
        { data: '2026-07-26', minutosDisponiveis: 0 },
      ]),
    )
    expect(chamadas.alterarDisponibilidades.mock.calls[0]?.[1]).toHaveLength(7)
    expect(pagina.text()).toContain('Disponibilidade salva')
  })

  it('oferece criar um rascunho quando a semana ainda nao possui plano', async () => {
    chamadas.obterPlanoSemanal.mockRejectedValue(
      new ErroDaApi(404, 'Plano não encontrado.'),
    )
    const { pagina } = await montarPagina()

    expect(pagina.text()).toContain('Esta semana ainda não tem um plano')
    await pagina
      .findAll('button')
      .find((botao) => botao.text().includes('Criar plano'))!
      .trigger('click')
    await flushPromises()

    expect(chamadas.criarPlanoSemanal).toHaveBeenCalledWith(inicio)
    expect(pagina.findAll('.cartao-de-disponibilidade')).toHaveLength(7)
  })

  it('navega entre semanas mantendo a selecao na URL', async () => {
    const { pagina, roteador } = await montarPagina()

    await pagina.get('[aria-label="Próxima semana"]').trigger('click')
    await flushPromises()

    expect(roteador.currentRoute.value.query.inicio).toBe('2026-07-27')
    expect(chamadas.obterPlanoSemanal).toHaveBeenLastCalledWith('2026-07-27')
  })

  it('permite recarregar depois de conflito ao salvar', async () => {
    chamadas.alterarDisponibilidades.mockRejectedValue(
      new ErroDaApi(409, 'O registro foi alterado por outra operação.'),
    )
    const { pagina } = await montarPagina()

    await pagina.get('form').trigger('submit')
    await flushPromises()
    expect(pagina.text()).toContain('Recarregar dados')

    await pagina
      .findAll('button')
      .find((botao) => botao.text().includes('Recarregar dados'))!
      .trigger('click')
    await flushPromises()
    expect(chamadas.obterPlanoSemanal).toHaveBeenCalledTimes(2)
  })

  it('adiciona um bloco livre e recarrega os dados persistidos', async () => {
    const { pagina } = await montarPagina()
    const bloco = blocoDeEstudo()

    await pagina
      .get('[aria-label="Adicionar bloco em Segunda-feira"]')
      .trigger('click')
    await pagina.get('#titulo-bloco').setValue('Leitura livre')
    await pagina.get('#duracao-bloco').setValue('45')
    chamadas.obterPlanoSemanal.mockResolvedValue(planoSemanal(0, [bloco]))
    await pagina.get('#formulario-bloco').trigger('submit')
    await flushPromises()

    expect(chamadas.adicionarBloco).toHaveBeenCalledWith(
      'plano-1',
      expect.objectContaining({
        titulo: 'Leitura livre',
        data: inicio,
        duracaoPrevistaEmMinutos: 45,
        ordem: 1,
      }),
    )
    expect(chamadas.obterPlanoSemanal).toHaveBeenCalledTimes(2)
    expect(pagina.text()).toContain('Bloco adicionado')
  })

  it('vincula materia e topico e sugere o titulo do topico', async () => {
    const { pagina } = await montarPagina()

    await pagina
      .get('[aria-label="Adicionar bloco em Segunda-feira"]')
      .trigger('click')
    await pagina.get('#materia-bloco').setValue('materia-1')
    await pagina.get('#topico-bloco').setValue('topico-1')
    await flushPromises()

    expect(
      (pagina.get('#titulo-bloco').element as HTMLInputElement).value,
    ).toBe('Modelagem relacional')
    await pagina.get('#formulario-bloco').trigger('submit')
    await flushPromises()
    expect(chamadas.adicionarBloco).toHaveBeenCalledWith(
      'plano-1',
      expect.objectContaining({
        identificadorDaMateria: 'materia-1',
        identificadorDoTopico: 'topico-1',
        titulo: 'Modelagem relacional',
      }),
    )
  })

  it('edita um bloco existente', async () => {
    const bloco = blocoDeEstudo()
    chamadas.obterPlanoSemanal.mockResolvedValue(planoSemanal(180, [bloco]))
    const { pagina } = await montarPagina()

    await pagina.get('[aria-label="Editar Banco de dados"]').trigger('click')
    await pagina.get('#titulo-bloco').setValue('SQL e modelagem')
    await pagina.get('#duracao-bloco').setValue('90')
    await pagina.get('#formulario-bloco').trigger('submit')
    await flushPromises()

    expect(chamadas.alterarBloco).toHaveBeenCalledWith(
      'bloco-1',
      expect.objectContaining({
        titulo: 'SQL e modelagem',
        duracaoPrevistaEmMinutos: 90,
      }),
    )
    expect(pagina.text()).toContain('Bloco atualizado')
  })

  it('confirma a exclusao em modal e atualiza o plano', async () => {
    const bloco = blocoDeEstudo()
    chamadas.obterPlanoSemanal.mockResolvedValue(planoSemanal(180, [bloco]))
    const { pagina } = await montarPagina()

    await pagina.get('[aria-label="Excluir Banco de dados"]').trigger('click')
    expect(pagina.text()).toContain('A ordem dos demais blocos')
    await pagina
      .findAll('button')
      .find((botao) => botao.text() === 'Excluir bloco')!
      .trigger('click')
    await flushPromises()

    expect(chamadas.excluirBloco).toHaveBeenCalledWith('bloco-1')
    expect(pagina.text()).toContain('Bloco excluído')
  })

  it('reordena blocos com botoes acessiveis', async () => {
    const primeiro = blocoDeEstudo('bloco-1', 'Primeiro bloco', 1)
    const segundo = blocoDeEstudo('bloco-2', 'Segundo bloco', 2)
    chamadas.obterPlanoSemanal.mockResolvedValue(
      planoSemanal(240, [primeiro, segundo]),
    )
    chamadas.reordenarBlocos.mockResolvedValue(
      planoSemanal(240, [
        { ...segundo, ordem: 1 },
        { ...primeiro, ordem: 2 },
      ]),
    )
    const { pagina } = await montarPagina()

    await pagina
      .get('[aria-label="Mover Segundo bloco para cima"]')
      .trigger('click')
    await flushPromises()

    expect(chamadas.reordenarBlocos).toHaveBeenCalledWith('plano-1', inicio, [
      'bloco-2',
      'bloco-1',
    ])
    expect(pagina.text()).toContain('Ordem dos blocos atualizada')
  })

  it('destaca quando a carga planejada excede a disponibilidade', async () => {
    chamadas.obterPlanoSemanal.mockResolvedValue(
      planoSemanal(100, [blocoDeEstudo()]),
    )
    const { pagina } = await montarPagina()

    expect(pagina.text()).toContain('120 planejados / 100 disponíveis')
    expect(pagina.text()).toContain('Excesso de 20 min')
    expect(pagina.get('.resumo-da-carga-do-dia').classes()).toContain(
      'com-excesso',
    )
  })

  it('fecha a gaveta com escape e devolve o foco ao botao de origem', async () => {
    const { pagina } = await montarPagina()
    const botao = pagina.get('[aria-label="Adicionar bloco em Segunda-feira"]')
    ;(botao.element as HTMLButtonElement).focus()
    await botao.trigger('click')
    await flushPromises()
    expect(pagina.find('[role="dialog"]').exists()).toBe(true)

    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
    await flushPromises()

    expect(pagina.find('[role="dialog"]').exists()).toBe(false)
    expect(document.activeElement).toBe(botao.element)
  })

  it('mostra pendencias antes de permitir a ativacao', async () => {
    const { pagina } = await montarPagina()

    await pagina
      .findAll('button')
      .find((botao) => botao.text().includes('Ativar plano'))!
      .trigger('click')

    expect(pagina.text()).toContain('Informe disponibilidade')
    expect(pagina.text()).toContain('Adicione pelo menos um bloco')
    expect(
      pagina
        .findAll('button')
        .some((botao) => botao.text().includes('Confirmar ativação')),
    ).toBe(false)
  })

  it('ativa um rascunho valido pelo modal', async () => {
    const bloco = blocoDeEstudo()
    const rascunho = planoSemanal(180, [bloco])
    const ativo = planoSemanal(180, [bloco], 'ATIVO')
    chamadas.obterPlanoSemanal.mockResolvedValue(rascunho)
    chamadas.ativarPlanoSemanal.mockResolvedValue(ativo)
    const { pagina } = await montarPagina()

    await pagina
      .findAll('button')
      .find((botao) => botao.text().includes('Ativar plano'))!
      .trigger('click')
    await pagina
      .findAll('button')
      .find((botao) => botao.text().includes('Confirmar ativação'))!
      .trigger('click')
    await flushPromises()

    expect(chamadas.ativarPlanoSemanal).toHaveBeenCalledWith('plano-1')
    expect(pagina.text()).toContain('Plano ativado')
    expect(pagina.text()).toContain('Ativo')
    expect(pagina.find('#disponibilidade-2026-07-20').exists()).toBe(false)
    expect(pagina.find('[aria-label="Editar Banco de dados"]').exists()).toBe(
      false,
    )
  })

  it('destaca excesso como pendencia de ativacao', async () => {
    chamadas.obterPlanoSemanal.mockResolvedValue(
      planoSemanal(100, [blocoDeEstudo()]),
    )
    const { pagina } = await montarPagina()

    await pagina
      .findAll('button')
      .find((botao) => botao.text().includes('Ativar plano'))!
      .trigger('click')

    expect(pagina.text()).toContain('Corrija os dias')
    expect(pagina.text()).toContain('Excesso de 20 min')
  })
})
