// @vitest-environment jsdom

import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { ErroDaApi } from '@/compartilhado/api/clienteHttp'

const chamadas = vi.hoisted(() => ({
  obterPlanejamentoDeHoje: vi.fn(),
  obterExecucaoEmAndamento: vi.fn(),
  iniciarBloco: vi.fn(),
  concluirBloco: vi.fn(),
  interromperBloco: vi.fn(),
  listarTopicosParaRegistro: vi.fn(),
  obterExecucaoDoBloco: vi.fn(),
  registrarExecucaoNoHistorico: vi.fn(),
  cancelarBloco: vi.fn(),
  reagendarBloco: vi.fn(),
  corrigirExecucao: vi.fn(),
  consultarRevisoesEspacadas: vi.fn(),
}))

vi.mock('./apiDePlanejamento', () => ({
  obterPlanejamentoDeHoje: chamadas.obterPlanejamentoDeHoje,
  obterExecucaoEmAndamento: chamadas.obterExecucaoEmAndamento,
  iniciarBloco: chamadas.iniciarBloco,
  concluirBloco: chamadas.concluirBloco,
  interromperBloco: chamadas.interromperBloco,
  listarTopicosParaRegistro: chamadas.listarTopicosParaRegistro,
  obterExecucaoDoBloco: chamadas.obterExecucaoDoBloco,
  registrarExecucaoNoHistorico: chamadas.registrarExecucaoNoHistorico,
  cancelarBloco: chamadas.cancelarBloco,
  reagendarBloco: chamadas.reagendarBloco,
  corrigirExecucao: chamadas.corrigirExecucao,
}))

vi.mock('./apiDeRevisoesEspacadas', () => ({
  consultarRevisoesEspacadas: chamadas.consultarRevisoesEspacadas,
}))

import PlanejamentoHojePagina from './PlanejamentoHojePagina.vue'

function bloco(identificador: string, titulo: string, ordem: number) {
  return {
    identificador,
    identificadorDoPlano: 'plano-1',
    titulo,
    tipoDeAtividade: 'TEORIA',
    data: '2026-07-20',
    duracaoPrevistaEmMinutos: 60,
    ordem,
    origem: 'MANUAL',
    estado: 'PLANEJADO',
    quantidadeDeReagendamentos: 0,
    criadoEm: '2026-07-19T12:00:00Z',
    atualizadoEm: '2026-07-19T12:00:00Z',
    versao: 0,
  }
}

function diaPlanejado() {
  return {
    estado: 'DIA_PLANEJADO',
    data: '2026-07-20',
    identificadorDoPlano: 'plano-1',
    dataInicialDoPlano: '2026-07-20',
    minutosDisponiveis: 180,
    minutosPlanejados: 120,
    quantidadeDeBlocos: 2,
    proximoBloco: bloco('bloco-1', 'Primeiro bloco', 1),
    sequencia: [bloco('bloco-2', 'Segundo bloco', 2)],
    atrasados: [],
    realizados: [],
  }
}

function revisaoEspacada(
  situacao: 'VENCIDA' | 'DEVIDA_HOJE' | 'JA_PLANEJADA' = 'DEVIDA_HOJE',
) {
  return {
    identificadorDoTopico: 'topico-1',
    nomeDoTopico: 'Direitos fundamentais',
    identificadorDaMateria: 'materia-1',
    nomeDaMateria: 'Direito Constitucional',
    etapa: 2,
    intervaloEmDias: 7,
    dataDevida: '2026-07-21',
    diasEmAtraso: situacao === 'VENCIDA' ? 3 : 0,
    ultimaRevisao: '2026-07-14T10:00:00-03:00',
    ultimaRecordacao: 3,
    situacao,
  }
}

const paginasMontadas: ReturnType<typeof mount>[] = []

async function montar() {
  const roteador = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/planejamento/hoje', component: PlanejamentoHojePagina },
      { path: '/planejamento/semana', component: { template: '<div />' } },
      { path: '/estudos', component: { template: '<div />' } },
    ],
  })
  await roteador.push('/planejamento/hoje')
  await roteador.isReady()
  const pagina = mount(PlanejamentoHojePagina, {
    attachTo: document.body,
    global: {
      plugins: [roteador],
      stubs: { teleport: true },
    },
  })
  paginasMontadas.push(pagina)
  await flushPromises()
  return pagina
}

describe('PlanejamentoHojePagina', () => {
  afterEach(() => {
    for (const pagina of paginasMontadas.splice(0)) pagina.unmount()
    vi.useRealTimers()
  })

  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
    chamadas.obterExecucaoEmAndamento.mockResolvedValue(undefined)
    chamadas.obterExecucaoDoBloco.mockRejectedValue(new Error('sem execucao'))
    chamadas.listarTopicosParaRegistro.mockResolvedValue([])
    chamadas.cancelarBloco.mockResolvedValue(undefined)
    chamadas.reagendarBloco.mockResolvedValue(undefined)
    chamadas.corrigirExecucao.mockResolvedValue(undefined)
    chamadas.consultarRevisoesEspacadas.mockResolvedValue({
      dataDeReferencia: '2026-07-21',
      ate: '2026-07-21',
      revisoes: [],
    })
  })

  it('mostra a fila vazia sem interferir no planejamento', async () => {
    chamadas.obterPlanejamentoDeHoje.mockResolvedValue(diaPlanejado())

    const pagina = await montar()

    expect(pagina.text()).toContain('Revisões de hoje')
    expect(pagina.text()).toContain('Nenhuma revisão vencida ou devida hoje')
    expect(pagina.text()).toContain('Primeiro bloco')
    expect(chamadas.consultarRevisoesEspacadas).toHaveBeenCalledWith(
      expect.any(String),
      expect.any(String),
      expect.any(AbortSignal),
    )
    const [referencia, ate] = chamadas.consultarRevisoesEspacadas.mock.calls[0]!
    expect(ate).toBe(referencia)
  })

  it('usa a data civil de Sao Paulo perto da virada em UTC', async () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-07-22T02:30:00Z'))
    chamadas.obterPlanejamentoDeHoje.mockResolvedValue(diaPlanejado())

    await montar()

    expect(chamadas.consultarRevisoesEspacadas).toHaveBeenCalledWith(
      '2026-07-21',
      '2026-07-21',
      expect.any(AbortSignal),
    )
  })

  it('abre o registro rapido preenchido para revisao vencida ou devida', async () => {
    chamadas.obterPlanejamentoDeHoje.mockResolvedValue(diaPlanejado())
    chamadas.consultarRevisoesEspacadas.mockResolvedValue({
      dataDeReferencia: '2026-07-21',
      ate: '2026-07-21',
      revisoes: [
        revisaoEspacada('VENCIDA'),
        {
          ...revisaoEspacada(),
          identificadorDoTopico: 'topico-2',
          nomeDoTopico: 'Controle de constitucionalidade',
        },
      ],
    })
    const detalhes: unknown[] = []
    const aoAbrir = (evento: Event) =>
      detalhes.push((evento as CustomEvent).detail)
    window.addEventListener('abrir-registro-rapido', aoAbrir, { once: true })

    const pagina = await montar()

    expect(pagina.text()).toContain('Vencida há 3 dias')
    expect(pagina.text()).toContain('Devida hoje')
    expect(
      pagina.get('[aria-label="Revisar agora: Direitos fundamentais"]'),
    ).toBeTruthy()
    await pagina
      .findAll('button')
      .find((item) => item.text() === 'Revisar agora')!
      .trigger('click')

    expect(detalhes).toEqual([
      {
        identificadorDaMateria: 'materia-1',
        identificadorDoTopico: 'topico-1',
        tipoDeEstudo: 'REVISAO',
      },
    ])
  })

  it('navega para a semana e informa o bloco que deve receber foco', async () => {
    chamadas.obterPlanejamentoDeHoje.mockResolvedValue(diaPlanejado())
    chamadas.consultarRevisoesEspacadas.mockResolvedValue({
      dataDeReferencia: '2026-07-21',
      ate: '2026-07-21',
      revisoes: [
        {
          ...revisaoEspacada('JA_PLANEJADA'),
          blocoAberto: {
            identificador: 'bloco-revisao',
            identificadorDoPlano: 'plano-1',
            dataInicialDoPlano: '2026-07-20',
            data: '2026-07-23',
            estado: 'PLANEJADO',
          },
        },
      ],
    })
    const pagina = await montar()

    expect(pagina.text()).toContain('Já planejada para 23 de jul.')
    expect(
      pagina.get('[aria-label="Ir para o bloco: Direitos fundamentais"]'),
    ).toBeTruthy()
    await pagina
      .findAll('button')
      .find((item) => item.text() === 'Ir para o bloco')!
      .trigger('click')
    await flushPromises()

    expect(pagina.vm.$router.currentRoute.value.query).toEqual({
      inicio: '2026-07-20',
      foco: 'bloco-revisao',
    })
  })

  it('isola contexto incompleto e erro de rede do planejamento principal', async () => {
    chamadas.obterPlanejamentoDeHoje.mockResolvedValue(diaPlanejado())
    chamadas.consultarRevisoesEspacadas.mockRejectedValueOnce(
      new ErroDaApi(422, 'Contexto incompleto.'),
    )
    const incompleto = await montar()

    expect(incompleto.text()).toContain('Complete o contexto do concurso')
    expect(incompleto.text()).toContain('Primeiro bloco')
    incompleto.unmount()

    chamadas.consultarRevisoesEspacadas
      .mockRejectedValueOnce(new Error('Agenda indisponível'))
      .mockResolvedValueOnce({
        dataDeReferencia: '2026-07-21',
        ate: '2026-07-21',
        revisoes: [],
      })
    const rede = await montar()
    expect(rede.text()).toContain('Agenda indisponível')
    expect(rede.text()).toContain('Primeiro bloco')
    await rede
      .findAll('button')
      .find((item) => item.text() === 'Tentar novamente')!
      .trigger('click')
    await flushPromises()

    expect(rede.text()).toContain('Nenhuma revisão vencida ou devida hoje')
    expect(document.activeElement).toBe(
      rede.get('#titulo-das-revisoes-de-hoje').element,
    )
  })

  it('explica a sessao expirada e recalcula depois de um estudo registrado', async () => {
    chamadas.obterPlanejamentoDeHoje.mockResolvedValue(diaPlanejado())
    chamadas.consultarRevisoesEspacadas.mockRejectedValueOnce(
      new ErroDaApi(401, 'Não autenticado.'),
    )
    const expirada = await montar()
    expect(expirada.text()).toContain('Sua sessão expirou')
    expect(
      expirada
        .findAll('button')
        .some((item) => item.text() === 'Tentar novamente'),
    ).toBe(false)
    expirada.unmount()

    chamadas.consultarRevisoesEspacadas
      .mockResolvedValueOnce({
        dataDeReferencia: '2026-07-21',
        ate: '2026-07-21',
        revisoes: [revisaoEspacada()],
      })
      .mockResolvedValueOnce({
        dataDeReferencia: '2026-07-21',
        ate: '2026-07-21',
        revisoes: [],
      })
    const atualizada = await montar()
    const botaoRemovido = atualizada.get(
      '[aria-label="Revisar agora: Direitos fundamentais"]',
    )
    ;(botaoRemovido.element as HTMLButtonElement).focus()
    window.dispatchEvent(new CustomEvent('estudo-registrado'))
    await flushPromises()

    expect(chamadas.consultarRevisoesEspacadas).toHaveBeenCalledTimes(3)
    expect(
      atualizada
        .find('[aria-label="Revisar agora: Direitos fundamentais"]')
        .exists(),
    ).toBe(false)
    expect(document.activeElement).toBe(
      atualizada.get('#titulo-das-revisoes-de-hoje').element,
    )
  })

  it('orienta a planejar quando nao existe plano', async () => {
    chamadas.obterPlanejamentoDeHoje.mockResolvedValue({
      estado: 'SEM_PLANO',
      data: '2026-07-20',
      minutosDisponiveis: 0,
      minutosPlanejados: 0,
      quantidadeDeBlocos: 0,
      sequencia: [],
      atrasados: [],
      realizados: [],
    })

    const pagina = await montar()

    expect(pagina.text()).toContain('Você ainda não planejou esta semana')
    expect(pagina.find('a[href="/planejamento/semana"]').exists()).toBe(true)
  })

  it('orienta a ativar quando a semana esta em rascunho', async () => {
    chamadas.obterPlanejamentoDeHoje.mockResolvedValue({
      estado: 'PLANO_EM_RASCUNHO',
      data: '2026-07-20',
      identificadorDoPlano: 'plano-1',
      dataInicialDoPlano: '2026-07-20',
      minutosDisponiveis: 0,
      minutosPlanejados: 0,
      quantidadeDeBlocos: 0,
      sequencia: [],
      atrasados: [],
      realizados: [],
    })

    const pagina = await montar()
    expect(pagina.text()).toContain('Seu plano ainda precisa ser ativado')
  })

  it('apresenta plano encerrado como somente leitura', async () => {
    chamadas.obterPlanejamentoDeHoje.mockResolvedValue({
      estado: 'PLANO_ENCERRADO',
      data: '2026-07-20',
      identificadorDoPlano: 'plano-1',
      dataInicialDoPlano: '2026-07-20',
      minutosDisponiveis: 0,
      minutosPlanejados: 0,
      quantidadeDeBlocos: 0,
      sequencia: [],
      atrasados: [],
      realizados: [],
    })

    const pagina = await montar()
    expect(pagina.text()).toContain('Esta semana foi encerrada')
    expect(pagina.text()).toContain('Ver semana encerrada')
  })

  it('apresenta plano cancelado e dia ativo sem blocos', async () => {
    chamadas.obterPlanejamentoDeHoje.mockResolvedValueOnce({
      estado: 'PLANO_CANCELADO',
      data: '2026-07-20',
      identificadorDoPlano: 'plano-1',
      dataInicialDoPlano: '2026-07-20',
      minutosDisponiveis: 0,
      minutosPlanejados: 0,
      quantidadeDeBlocos: 0,
      sequencia: [],
      atrasados: [],
      realizados: [],
    })
    const cancelado = await montar()
    expect(cancelado.text()).toContain('Este plano foi cancelado')
    cancelado.unmount()

    chamadas.obterPlanejamentoDeHoje.mockResolvedValueOnce({
      estado: 'DIA_SEM_BLOCOS',
      data: '2026-07-20',
      identificadorDoPlano: 'plano-1',
      dataInicialDoPlano: '2026-07-20',
      minutosDisponiveis: 60,
      minutosPlanejados: 0,
      quantidadeDeBlocos: 0,
      sequencia: [],
      atrasados: [],
      realizados: [],
    })
    const vazio = await montar()
    expect(vazio.text()).toContain('Hoje não há blocos planejados')
  })

  it('permite tentar novamente quando a consulta falha', async () => {
    chamadas.obterPlanejamentoDeHoje
      .mockRejectedValueOnce(new Error('API indisponível'))
      .mockResolvedValueOnce(diaPlanejado())

    const pagina = await montar()
    expect(pagina.text()).toContain('Não foi possível carregar seu dia')
    await pagina
      .findAll('button')
      .find((botao) => botao.text() === 'Tentar novamente')!
      .trigger('click')
    await flushPromises()

    expect(chamadas.obterPlanejamentoDeHoje).toHaveBeenCalledTimes(2)
    expect(pagina.text()).toContain('Primeiro bloco')
  })

  it('inicia o proximo bloco', async () => {
    chamadas.obterPlanejamentoDeHoje.mockResolvedValue(diaPlanejado())
    chamadas.iniciarBloco.mockResolvedValue({
      bloco: {
        ...bloco('bloco-1', 'Primeiro bloco', 1),
        estado: 'EM_ANDAMENTO',
      },
      execucao: {
        identificador: 'execucao-1',
        identificadorDoBloco: 'bloco-1',
        iniciadaEm: new Date().toISOString(),
        criadoEm: new Date().toISOString(),
        atualizadoEm: new Date().toISOString(),
        versao: 0,
      },
    })

    const pagina = await montar()
    await pagina.get('.proximo-bloco-do-dia button').trigger('click')
    await flushPromises()

    expect(chamadas.iniciarBloco).toHaveBeenCalledWith(
      'bloco-1',
      expect.any(String),
    )
    expect(chamadas.consultarRevisoesEspacadas).toHaveBeenCalledTimes(2)
  })

  it('oferece recarregar os dados depois de conflito', async () => {
    chamadas.obterPlanejamentoDeHoje.mockResolvedValue(diaPlanejado())
    chamadas.iniciarBloco.mockRejectedValue(
      new ErroDaApi(409, 'O bloco foi alterado por outra operação.'),
    )
    const pagina = await montar()

    await pagina.get('.proximo-bloco-do-dia button').trigger('click')
    await flushPromises()
    expect(pagina.text()).toContain('Recarregar dados')

    await pagina
      .findAll('button')
      .find((botao) => botao.text() === 'Recarregar dados')!
      .trigger('click')
    await flushPromises()
    expect(chamadas.obterPlanejamentoDeHoje).toHaveBeenCalledTimes(2)
  })

  it('recupera uma execucao aberta e mostra o cronometro', async () => {
    chamadas.obterPlanejamentoDeHoje.mockResolvedValue(diaPlanejado())
    chamadas.obterExecucaoEmAndamento.mockResolvedValue({
      bloco: {
        ...bloco('bloco-1', 'Primeiro bloco', 1),
        estado: 'EM_ANDAMENTO',
      },
      execucao: {
        identificador: 'execucao-1',
        identificadorDoBloco: 'bloco-1',
        iniciadaEm: new Date(Date.now() - 65_000).toISOString(),
        criadoEm: new Date().toISOString(),
        atualizadoEm: new Date().toISOString(),
        versao: 0,
      },
    })

    const pagina = await montar()

    expect(pagina.get('.bloco-em-andamento').text()).toContain('Primeiro bloco')
    expect(pagina.get('.cronometro-da-execucao').text()).toMatch(/00:01:0[4-6]/)
  })

  it('pausa e retoma o cronometro sem contar o intervalo pausado', async () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-07-24T12:00:00Z'))
    chamadas.obterPlanejamentoDeHoje.mockResolvedValue(diaPlanejado())
    chamadas.obterExecucaoEmAndamento.mockResolvedValue({
      bloco: {
        ...bloco('bloco-1', 'Primeiro bloco', 1),
        estado: 'EM_ANDAMENTO',
      },
      execucao: {
        identificador: 'execucao-1',
        identificadorDoBloco: 'bloco-1',
        iniciadaEm: '2026-07-24T11:59:00Z',
        criadoEm: '2026-07-24T11:59:00Z',
        atualizadoEm: '2026-07-24T11:59:00Z',
        versao: 0,
      },
    })

    const pagina = await montar()
    expect(pagina.get('.cronometro-da-execucao').text()).toBe('00:01:00')

    await pagina
      .findAll('.bloco-em-andamento button')
      .find((botao) => botao.text().includes('Pausar'))!
      .trigger('click')
    vi.advanceTimersByTime(30_000)
    await pagina.vm.$nextTick()

    expect(pagina.text()).toContain('Cronômetro pausado')
    expect(pagina.get('.cronometro-da-execucao').text()).toBe('00:01:00')

    await pagina
      .findAll('.bloco-em-andamento button')
      .find((botao) => botao.text().includes('Retomar'))!
      .trigger('click')
    vi.advanceTimersByTime(10_000)
    await pagina.vm.$nextTick()

    expect(pagina.get('.cronometro-da-execucao').text()).toBe('00:01:10')
  })

  it('mantem o cronometro pausado depois de recarregar a pagina', async () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-07-24T12:00:00Z'))
    chamadas.obterPlanejamentoDeHoje.mockResolvedValue(diaPlanejado())
    chamadas.obterExecucaoEmAndamento.mockResolvedValue({
      bloco: {
        ...bloco('bloco-1', 'Primeiro bloco', 1),
        estado: 'EM_ANDAMENTO',
      },
      execucao: {
        identificador: 'execucao-1',
        identificadorDoBloco: 'bloco-1',
        iniciadaEm: '2026-07-24T11:59:00Z',
        criadoEm: '2026-07-24T11:59:00Z',
        atualizadoEm: '2026-07-24T11:59:00Z',
        versao: 0,
      },
    })

    const pagina = await montar()
    await pagina
      .findAll('.bloco-em-andamento button')
      .find((botao) => botao.text().includes('Pausar'))!
      .trigger('click')
    pagina.unmount()
    vi.advanceTimersByTime(30_000)

    const recarregada = await montar()

    expect(recarregada.text()).toContain('Cronômetro pausado')
    expect(recarregada.get('.cronometro-da-execucao').text()).toBe('00:01:00')
  })

  it('conclui uma execucao informando a duracao', async () => {
    chamadas.obterPlanejamentoDeHoje.mockResolvedValue(diaPlanejado())
    chamadas.obterExecucaoEmAndamento.mockResolvedValue({
      bloco: {
        ...bloco('bloco-1', 'Primeiro bloco', 1),
        estado: 'EM_ANDAMENTO',
      },
      execucao: {
        identificador: 'execucao-1',
        identificadorDoBloco: 'bloco-1',
        iniciadaEm: new Date(Date.now() - 120_000).toISOString(),
        criadoEm: new Date().toISOString(),
        atualizadoEm: new Date().toISOString(),
        versao: 0,
      },
    })
    chamadas.concluirBloco.mockResolvedValue({})

    const pagina = await montar()
    await pagina.get('.bloco-em-andamento .btn-primary').trigger('click')
    await pagina.get('#duracao-executada').setValue(15)
    await pagina.get('.rodape-do-modal .btn-primary').trigger('click')
    await flushPromises()

    expect(chamadas.concluirBloco).toHaveBeenCalledWith(
      'bloco-1',
      15,
      undefined,
      undefined,
    )
    expect(chamadas.consultarRevisoesEspacadas).toHaveBeenCalledTimes(2)
  })

  it('recalcula as revisoes depois de interromper uma execucao', async () => {
    chamadas.obterPlanejamentoDeHoje.mockResolvedValue(diaPlanejado())
    chamadas.obterExecucaoEmAndamento.mockResolvedValue({
      bloco: {
        ...bloco('bloco-1', 'Primeiro bloco', 1),
        estado: 'EM_ANDAMENTO',
      },
      execucao: {
        identificador: 'execucao-1',
        identificadorDoBloco: 'bloco-1',
        iniciadaEm: new Date(Date.now() - 120_000).toISOString(),
        criadoEm: new Date().toISOString(),
        atualizadoEm: new Date().toISOString(),
        versao: 0,
      },
    })
    chamadas.interromperBloco.mockResolvedValue({})

    const pagina = await montar()
    await pagina
      .findAll('.bloco-em-andamento button')
      .find((botao) => botao.text() === 'Interromper')!
      .trigger('click')
    await pagina.get('#duracao-executada').setValue(10)
    await pagina
      .findAll('.rodape-do-modal button')
      .find((botao) => botao.text() === 'Registrar')!
      .trigger('click')
    await flushPromises()

    expect(chamadas.interromperBloco).toHaveBeenCalledWith(
      'bloco-1',
      10,
      undefined,
      undefined,
    )
    expect(chamadas.consultarRevisoesEspacadas).toHaveBeenCalledTimes(2)
  })

  it('separa blocos atrasados e permite inicia-los', async () => {
    chamadas.obterPlanejamentoDeHoje.mockResolvedValue({
      estado: 'DIA_SEM_BLOCOS',
      data: '2026-07-21',
      identificadorDoPlano: 'plano-1',
      dataInicialDoPlano: '2026-07-20',
      minutosDisponiveis: 0,
      minutosPlanejados: 0,
      quantidadeDeBlocos: 0,
      sequencia: [],
      atrasados: [bloco('bloco-1', 'Bloco pendente', 1)],
      realizados: [],
    })
    chamadas.iniciarBloco.mockResolvedValue({
      bloco: {
        ...bloco('bloco-1', 'Bloco pendente', 1),
        estado: 'EM_ANDAMENTO',
      },
      execucao: {
        identificador: 'execucao-1',
        identificadorDoBloco: 'bloco-1',
        iniciadaEm: new Date().toISOString(),
        criadoEm: new Date().toISOString(),
        atualizadoEm: new Date().toISOString(),
        versao: 0,
      },
    })

    const pagina = await montar()
    expect(pagina.text()).toContain('Pendentes de dias anteriores')
    await pagina.get('.blocos-atrasados-do-dia button').trigger('click')
    await flushPromises()
    expect(chamadas.iniciarBloco).toHaveBeenCalled()
  })

  it('reagenda e cancela o proximo bloco com confirmacao', async () => {
    chamadas.obterPlanejamentoDeHoje.mockResolvedValue(diaPlanejado())
    const pagina = await montar()

    await pagina.get('[aria-label="Reagendar Primeiro bloco"]').trigger('click')
    await pagina.get('#data-reagendamento-hoje').setValue('2026-07-21')
    await pagina
      .findAll('button')
      .find((botao) => botao.text() === 'Confirmar reagendamento')!
      .trigger('click')
    await flushPromises()
    expect(chamadas.reagendarBloco).toHaveBeenCalledWith(
      'bloco-1',
      '2026-07-21',
      undefined,
      1,
    )

    await pagina.get('[aria-label="Cancelar Primeiro bloco"]').trigger('click')
    await pagina
      .findAll('button')
      .find((botao) => botao.text() === 'Cancelar bloco')!
      .trigger('click')
    await flushPromises()
    expect(chamadas.cancelarBloco).toHaveBeenCalledWith('bloco-1')
    expect(chamadas.consultarRevisoesEspacadas).toHaveBeenCalledTimes(3)
  })

  it('corrige uma execucao finalizada', async () => {
    const realizado = {
      ...bloco('bloco-1', 'Primeiro bloco', 1),
      estado: 'CONCLUIDO',
    }
    chamadas.obterPlanejamentoDeHoje.mockResolvedValue({
      ...diaPlanejado(),
      proximoBloco: undefined,
      sequencia: [],
      realizados: [realizado],
    })
    chamadas.obterExecucaoDoBloco.mockResolvedValue({
      bloco: realizado,
      execucao: {
        identificador: 'execucao-1',
        identificadorDoBloco: 'bloco-1',
        iniciadaEm: '2026-07-20T10:00:00Z',
        encerradaEm: '2026-07-20T10:30:00Z',
        duracaoExecutadaEmMinutos: 30,
        resultado: 'CONCLUIDO',
        criadoEm: '2026-07-20T10:00:00Z',
        atualizadoEm: '2026-07-20T10:30:00Z',
        versao: 0,
      },
    })
    const pagina = await montar()

    await pagina
      .get('[aria-label="Corrigir execução de Primeiro bloco"]')
      .trigger('click')
    await pagina.get('#resultado-corrigido').setValue('PARCIALMENTE_CONCLUIDO')
    await pagina.get('#duracao-corrigida').setValue(20)
    await pagina
      .findAll('button')
      .find((botao) => botao.text() === 'Salvar correção')!
      .trigger('click')
    await flushPromises()

    expect(chamadas.corrigirExecucao).toHaveBeenCalledWith(
      'execucao-1',
      'PARCIALMENTE_CONCLUIDO',
      20,
      undefined,
      undefined,
      undefined,
    )
    expect(chamadas.consultarRevisoesEspacadas).toHaveBeenCalledTimes(2)
  })
})
