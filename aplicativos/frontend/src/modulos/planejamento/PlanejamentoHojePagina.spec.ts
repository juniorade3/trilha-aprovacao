// @vitest-environment jsdom

import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const chamadas = vi.hoisted(() => ({ obterPlanejamentoDeHoje: vi.fn() }))

vi.mock('./apiDePlanejamento', () => ({
  obterPlanejamentoDeHoje: chamadas.obterPlanejamentoDeHoje,
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
    global: { plugins: [roteador] },
  })
  await flushPromises()
  return pagina
}

describe('PlanejamentoHojePagina', () => {
  beforeEach(() => vi.clearAllMocks())

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
    expect(
      pagina
        .findAll('a[href="/planejamento/semana"]')
        .some((link) => link.text().includes('Planejar minha semana')),
    ).toBe(true)
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
    expect(
      pagina.get('a[href="/planejamento/semana?inicio=2026-07-20"]'),
    ).toBeDefined()
  })

  it('destaca o proximo bloco e apresenta a sequencia vertical', async () => {
    chamadas.obterPlanejamentoDeHoje.mockResolvedValue({
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
    })

    const pagina = await montar()

    expect(pagina.get('.proximo-bloco-do-dia').text()).toContain(
      'Primeiro bloco',
    )
    expect(pagina.get('.sequencia-do-dia').text()).toContain('Segundo bloco')
    expect(pagina.text()).toContain('180 min')
    expect(pagina.text()).toContain('120 min')
    expect(pagina.get('nav').text()).toContain('Hoje')
    expect(pagina.get('nav').text()).toContain('Semana')
    expect(pagina.get('nav').text()).toContain('Histórico')
  })

  it('informa quando o dia ativo nao possui blocos', async () => {
    chamadas.obterPlanejamentoDeHoje.mockResolvedValue({
      estado: 'DIA_SEM_BLOCOS',
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

    expect(pagina.text()).toContain('Hoje não há blocos planejados')
    expect(pagina.text()).not.toContain('recomend')
  })

  it('separa blocos atrasados da sequencia de hoje', async () => {
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

    const pagina = await montar()

    expect(pagina.text()).toContain('Pendentes de dias anteriores')
    expect(pagina.text()).toContain('Bloco pendente')
    expect(pagina.text()).toContain('Hoje não há blocos planejados')
  })

  it('permite tentar novamente depois de erro', async () => {
    chamadas.obterPlanejamentoDeHoje
      .mockRejectedValueOnce(new Error('Serviço indisponível'))
      .mockResolvedValueOnce({
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
    expect(pagina.text()).toContain('Serviço indisponível')
    await pagina.get('button').trigger('click')
    await flushPromises()

    expect(chamadas.obterPlanejamentoDeHoje).toHaveBeenCalledTimes(2)
    expect(pagina.text()).toContain('Você ainda não planejou')
  })
})
