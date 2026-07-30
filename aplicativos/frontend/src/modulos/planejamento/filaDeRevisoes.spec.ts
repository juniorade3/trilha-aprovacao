import { describe, expect, it } from 'vitest'

import type { RevisaoEspacada } from './apiDeRevisoesEspacadas'
import { organizarFilaDeRevisoesDeHoje } from './filaDeRevisoes'

const capacidade = {
  limiteDePrioridades: 3,
  duracaoEstimadaPorRevisaoEmMinutos: 20,
}

function revisao(
  identificador: number,
  situacao: RevisaoEspacada['situacao'] = 'DEVIDA_HOJE',
): RevisaoEspacada {
  return {
    identificadorDoTopico: `topico-${identificador}`,
    nomeDoTopico: `Tópico ${identificador}`,
    identificadorDaMateria: 'materia-1',
    nomeDaMateria: 'Matéria',
    etapa: 0,
    intervaloEmDias: 1,
    dataDevida:
      situacao === 'FUTURA'
        ? '2026-07-22'
        : situacao === 'VENCIDA'
          ? '2026-07-20'
          : '2026-07-21',
    diasEmAtraso: situacao === 'VENCIDA' ? identificador : 0,
    situacao,
  }
}

describe('organizarFilaDeRevisoesDeHoje', () => {
  it('prioriza somente tres revisoes elegiveis e mantem as demais visiveis no backlog', () => {
    const revisoes = Array.from({ length: 14 }, (_, indice) =>
      revisao(indice + 1, indice < 8 ? 'VENCIDA' : 'DEVIDA_HOJE'),
    )

    const fila = organizarFilaDeRevisoesDeHoje(
      revisoes,
      '2026-07-21',
      capacidade,
    )

    expect(fila.prioridades.map((item) => item.identificadorDoTopico)).toEqual([
      'topico-1',
      'topico-2',
      'topico-3',
    ])
    expect(
      fila.filaDeRecuperacao.map((item) => item.identificadorDoTopico),
    ).toEqual([
      'topico-4',
      'topico-5',
      'topico-6',
      'topico-7',
      'topico-8',
      'topico-9',
      'topico-10',
      'topico-11',
      'topico-12',
      'topico-13',
      'topico-14',
    ])
    expect(fila.quantidadeVencidas).toBe(8)
    expect(fila.quantidadeDevidasHoje).toBe(6)
  })

  it('separa revisoes ja planejadas sem retira-las da contagem da agenda', () => {
    const planejada = {
      ...revisao(1, 'JA_PLANEJADA'),
      dataDevida: '2026-07-20',
      diasEmAtraso: 1,
      blocoAberto: {
        identificador: 'bloco-1',
        identificadorDoPlano: 'plano-1',
        dataInicialDoPlano: '2026-07-20',
        data: '2026-07-22',
        estado: 'PLANEJADO' as const,
      },
    }

    const fila = organizarFilaDeRevisoesDeHoje(
      [planejada, revisao(2, 'VENCIDA'), revisao(3)],
      '2026-07-21',
      capacidade,
    )

    expect(fila.revisoesDeHoje).toHaveLength(3)
    expect(fila.planejadas).toEqual([planejada])
    expect(fila.quantidadeVencidas).toBe(2)
    expect(fila.quantidadeDevidasHoje).toBe(1)
    expect(fila.prioridades.map((item) => item.identificadorDoTopico)).toEqual([
      'topico-2',
      'topico-3',
    ])
  })

  it('nao inclui revisoes futuras na fila de hoje', () => {
    const fila = organizarFilaDeRevisoesDeHoje(
      [revisao(1, 'FUTURA'), revisao(2)],
      '2026-07-21',
      capacidade,
    )

    expect(
      fila.revisoesDeHoje.map((item) => item.identificadorDoTopico),
    ).toEqual(['topico-2'])
    expect(fila.prioridades.map((item) => item.identificadorDoTopico)).toEqual([
      'topico-2',
    ])
  })

  it('mantem a agenda completa se a capacidade ainda nao vier do backend', () => {
    const fila = organizarFilaDeRevisoesDeHoje(
      [revisao(1), revisao(2), revisao(3), revisao(4)],
      '2026-07-21',
    )

    expect(fila.prioridades).toHaveLength(4)
    expect(fila.filaDeRecuperacao).toHaveLength(0)
  })
})
