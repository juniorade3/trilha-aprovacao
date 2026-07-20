// @vitest-environment jsdom

import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const chamadas = vi.hoisted(() => ({
  obterPlanejamentoDeHoje: vi.fn(),
  obterExecucaoEmAndamento: vi.fn(),
  iniciarBloco: vi.fn(),
  concluirBloco: vi.fn(),
  interromperBloco: vi.fn(),
}))

vi.mock('./apiDePlanejamento', () => ({
  obterPlanejamentoDeHoje: chamadas.obterPlanejamentoDeHoje,
  obterExecucaoEmAndamento: chamadas.obterExecucaoEmAndamento,
  iniciarBloco: chamadas.iniciarBloco,
  concluirBloco: chamadas.concluirBloco,
  interromperBloco: chamadas.interromperBloco,
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
    estado: 'PLANEJADO',
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
    global: {
      plugins: [roteador],
      stubs: { teleport: true },
    },
  })
  await flushPromises()
  return pagina
}

describe('PlanejamentoHojePagina', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    chamadas.obterExecucaoEmAndamento.mockResolvedValue(undefined)
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
    )
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
})
