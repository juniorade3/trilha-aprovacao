import { requisitar } from '@/compartilhado/api/clienteHttp'

export type GrupoDaPriorizacao = 'LACUNA' | 'FRAQUEZA' | 'CONSOLIDADO'

export type FaixaDaPriorizacao =
  | 'SEM_ESTUDO'
  | 'SEM_EVIDENCIA'
  | 'EVIDENCIA_DESATUALIZADA'
  | 'DADOS_INSUFICIENTES'
  | 'PRECISA_REFORCO'
  | 'DESEMPENHO_PARCIAL'
  | 'CONSOLIDADO'

export type AcaoSugeridaDaPriorizacao = 'TEORIA' | 'QUESTOES'

export type ReferenciaDoContextoOficial = {
  identificador: string
  nome: string
}

export type ContextoOficialDaPriorizacao = {
  concurso: ReferenciaDoContextoOficial
  cargo: ReferenciaDoContextoOficial
  edital: ReferenciaDoContextoOficial
  dataReferencia: string
  inicioJanelaRecente: string
}

export type ResumoDaPriorizacao = {
  itensOficiais: number
  itensSemMapeamento: number
  topicosExigidos: number
  lacunas: number
  fraquezas: number
  consolidados: number
}

export type ItemSemMapeamentoDaPriorizacao = {
  id: string
  descricao: string
  idMateria?: string
  nomeMateria?: string
  ordem: number
}

export type IndicadoresDaPriorizacao = {
  estudos: number
  evidencias: number
  questoesRecentes: number
  acertosRecentes: number
  errosRecentes: number
  percentual?: number | null
  ultimaRecordacao?: number | null
  ultimaDificuldade?: number | null
  ultimaEvidencia?: string | null
  quantidadePadroesRepetidos: number
  ultimaOcorrenciaPadraoRepetido?: string | null
}

export type TopicoPriorizado = {
  id: string
  nome: string
  grupo: GrupoDaPriorizacao
  faixa: FaixaDaPriorizacao
  posicaoNoGrupo: number
  acaoSugerida: AcaoSugeridaDaPriorizacao
  possuiMaterial: boolean
  quantidadeItensOficiais: number
  indicadores: IndicadoresDaPriorizacao
  justificativas: string[]
}

export type MateriaPriorizada = {
  id: string
  nome: string
  topicos: TopicoPriorizado[]
}

export type RespostaDePriorizacaoDeTopicos = {
  contexto: ContextoOficialDaPriorizacao
  resumo: ResumoDaPriorizacao
  itensSemMapeamento: ItemSemMapeamentoDaPriorizacao[]
  materias: MateriaPriorizada[]
}

export function consultarPriorizacaoDeTopicos(
  dataDeReferencia: string,
  identificadorDaMateria?: string,
  sinal?: AbortSignal,
) {
  const parametros = new URLSearchParams({ dataDeReferencia })
  if (identificadorDaMateria)
    parametros.set('identificadorDaMateria', identificadorDaMateria)

  return requisitar<RespostaDePriorizacaoDeTopicos>(
    `/v1/priorizacao-de-topicos?${parametros}`,
    { signal: sinal },
  )
}
