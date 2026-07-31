import { requisitar } from '@/compartilhado/api/clienteHttp'

export type SituacaoDaRevisaoEspacada =
  'VENCIDA' | 'DEVIDA_HOJE' | 'FUTURA' | 'JA_PLANEJADA'

export interface BlocoAbertoDaRevisao {
  identificador: string
  identificadorDoPlano: string
  dataInicialDoPlano: string
  data: string
  estado: 'PLANEJADO' | 'EM_ANDAMENTO'
}

export interface RevisaoEspacada {
  identificadorDoTopico: string
  nomeDoTopico: string
  identificadorDaMateria: string
  nomeDaMateria: string
  etapa: number
  intervaloEmDias: number
  dataDevida: string
  diasEmAtraso: number
  ultimaRevisao?: string
  ultimaRecordacao?: number
  situacao: SituacaoDaRevisaoEspacada
  blocoAberto?: BlocoAbertoDaRevisao
}

export interface CapacidadeDaFilaDeRevisoes {
  limiteDePrioridades: number
  duracaoEstimadaPorRevisaoEmMinutos: number
}

export interface AgendaDeRevisoesEspacadas {
  dataDeReferencia: string
  ate: string
  capacidadeDaFila?: CapacidadeDaFilaDeRevisoes
  revisoes: RevisaoEspacada[]
}

export function consultarRevisoesEspacadas(
  dataDeReferencia: string,
  ate: string,
  sinal?: AbortSignal,
): Promise<AgendaDeRevisoesEspacadas> {
  const parametros = new URLSearchParams({ dataDeReferencia, ate })
  return requisitar<AgendaDeRevisoesEspacadas>(
    `/v1/revisoes-espacadas?${parametros}`,
    { signal: sinal },
  )
}
