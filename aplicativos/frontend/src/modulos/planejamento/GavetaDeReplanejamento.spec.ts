// @vitest-environment jsdom

import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { ErroDaApi } from '@/compartilhado/api/clienteHttp'

const chamadas = vi.hoisted(() => ({
  gerar: vi.fn(),
  aplicar: vi.fn(),
}))

vi.mock('./apiDePlanejamento', () => ({
  gerarPreviaDoReplanejamento: chamadas.gerar,
  aplicarReplanejamento: chamadas.aplicar,
}))

import GavetaDeReplanejamento from './GavetaDeReplanejamento.vue'

const montadas: ReturnType<typeof mount>[] = []

function previa() {
  return {
    identificadorDoPlano: 'plano-1',
    dataDeReferencia: '2026-07-22',
    dataFinal: '2026-07-26',
    assinaturaDaPrevia: 'assinatura-1',
    resumo: {
      quantidadeDePendencias: 1,
      quantidadeDeFragmentos: 1,
      minutosPendentes: 50,
      minutosAlocados: 50,
      minutosNaoAlocados: 0,
      confirmacoesExigidas: 1,
    },
    capacidadesPorDia: [
      {
        data: '2026-07-23',
        minutosDisponiveis: 100,
        minutosOcupados: 0,
        minutosAlocados: 50,
        minutosRestantes: 50,
        quantidadeDeMaterias: 1,
      },
    ],
    blocosPreservados: [],
    pendencias: [
      {
        identificadorDoBloco: 'bloco-1',
        titulo: 'Banco de dados',
        dataOriginal: '2026-07-20',
        minutosPrevistos: 50,
        minutosExecutados: 0,
        minutosPendentes: 50,
        quantidadeDeReagendamentos: 3,
        prioridade: 'ALTA',
        motivo: 'NAO_INICIADO',
        decisao: 'ADIAR',
        exigeConfirmacao: true,
        minutosNaoAlocados: 0,
        justificativa: 'Cabe integralmente na quinta-feira.',
        fragmentos: [
          { data: '2026-07-23', duracaoEmMinutos: 50, sequencia: 1 },
        ],
        sugestoesManuais: [],
      },
    ],
  }
}

async function montar() {
  const componente = mount(GavetaDeReplanejamento, {
    attachTo: document.body,
    props: {
      identificadorDoPlano: 'plano-1',
      dataDeReferencia: '2026-07-22',
    },
  })
  montadas.push(componente)
  await flushPromises()
  return componente
}

describe('GavetaDeReplanejamento', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    chamadas.gerar.mockResolvedValue(previa())
    chamadas.aplicar.mockResolvedValue({
      identificadorDoReplanejamento: 'replanejamento-1',
      aplicadoEm: '2026-07-22T12:00:00-03:00',
      planoAtualizado: {},
      quantidadeDePendenciasTransferidas: 1,
      quantidadeDeFragmentosCriados: 1,
    })
  })

  afterEach(() => {
    for (const componente of montadas.splice(0)) componente.unmount()
  })

  it('remove uma pendencia e recalcula no backend', async () => {
    const componente = await montar()
    await componente
      .get('button[aria-label="Remover Banco de dados desta proposta"]')
      .trigger('click')
    await flushPromises()

    expect(chamadas.gerar).toHaveBeenLastCalledWith('plano-1', '2026-07-22', [
      'bloco-1',
    ])
    expect(componente.text()).toContain('recalculada no servidor')
  })

  it('exige confirmacao individual e final antes de aplicar', async () => {
    const componente = await montar()
    const aplicar = componente
      .findAll('button')
      .find((botao) => botao.text().includes('Aplicar replanejamento'))!
    expect(aplicar.attributes('disabled')).toBeDefined()

    await componente.get('#confirmar-bloco-1').setValue(true)
    await componente.get('#confirmacao-final').setValue(true)
    expect(aplicar.attributes('disabled')).toBeUndefined()
    await aplicar.trigger('click')
    await flushPromises()

    expect(chamadas.aplicar).toHaveBeenCalledWith(
      'plano-1',
      '2026-07-22',
      [],
      ['bloco-1'],
      'assinatura-1',
    )
    expect(componente.emitted('aplicado')).toHaveLength(1)
  })

  it('recalcula sem aplicar silenciosamente quando a previa fica desatualizada', async () => {
    chamadas.aplicar.mockRejectedValueOnce(
      new ErroDaApi(
        409,
        'Previa desatualizada.',
        'PREVIA_DE_REPLANEJAMENTO_DESATUALIZADA',
      ),
    )
    const componente = await montar()
    await componente.get('#confirmar-bloco-1').setValue(true)
    await componente.get('#confirmacao-final').setValue(true)
    const aplicar = componente
      .findAll('button')
      .find((botao) => botao.text().includes('Aplicar replanejamento'))!
    await aplicar.trigger('click')
    await flushPromises()

    expect(chamadas.gerar).toHaveBeenCalledTimes(2)
    expect(componente.text()).toContain('nada foi aplicado automaticamente')
    expect(componente.emitted('aplicado')).toBeUndefined()
  })

  it('fecha por Escape e mantem o dialogo modal', async () => {
    const componente = await montar()
    expect(componente.get('[role="dialog"]').attributes('aria-modal')).toBe(
      'true',
    )
    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
    await flushPromises()
    expect(componente.emitted('fechar')).toHaveLength(1)
  })
})
