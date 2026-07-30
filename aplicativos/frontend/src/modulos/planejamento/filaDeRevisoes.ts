import type {
  CapacidadeDaFilaDeRevisoes,
  RevisaoEspacada,
} from './apiDeRevisoesEspacadas'

export interface FilaDeRevisoesDeHoje {
  revisoesDeHoje: RevisaoEspacada[]
  prioridades: RevisaoEspacada[]
  filaDeRecuperacao: RevisaoEspacada[]
  planejadas: RevisaoEspacada[]
  quantidadeVencidas: number
  quantidadeDevidasHoje: number
}

export function organizarFilaDeRevisoesDeHoje(
  revisoes: RevisaoEspacada[],
  dataDeReferencia: string,
  capacidade?: CapacidadeDaFilaDeRevisoes,
): FilaDeRevisoesDeHoje {
  const revisoesDeHoje = revisoes.filter(
    (revisao) =>
      revisao.situacao !== 'FUTURA' && revisao.dataDevida <= dataDeReferencia,
  )
  const planejadas = revisoesDeHoje.filter(
    (revisao) => revisao.situacao === 'JA_PLANEJADA' || revisao.blocoAberto,
  )
  const elegiveis = revisoesDeHoje.filter(
    (revisao) =>
      !revisao.blocoAberto &&
      revisao.situacao !== 'JA_PLANEJADA' &&
      ['VENCIDA', 'DEVIDA_HOJE'].includes(revisao.situacao),
  )
  const limite = Math.max(
    0,
    capacidade?.limiteDePrioridades ?? Number.MAX_SAFE_INTEGER,
  )

  return {
    revisoesDeHoje,
    prioridades: elegiveis.slice(0, limite),
    filaDeRecuperacao: elegiveis.slice(limite),
    planejadas,
    quantidadeVencidas: revisoesDeHoje.filter(
      (revisao) => revisao.dataDevida < dataDeReferencia,
    ).length,
    quantidadeDevidasHoje: revisoesDeHoje.filter(
      (revisao) => revisao.dataDevida === dataDeReferencia,
    ).length,
  }
}
