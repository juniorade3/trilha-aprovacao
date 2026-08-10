import { requisitar } from '@/compartilhado/api/clienteHttp'

export type SituacaoDoAcompanhamentoDaTarefa =
  'PENDENTE' | 'EM_ANDAMENTO' | 'CONCLUIDA' | 'PULADA'

export type TipoDeAtividadeDaTrilha =
  | 'TEORIA'
  | 'QUESTOES'
  | 'REVISAO'
  | 'CADERNO_DE_ERROS'
  | 'SIMULADO'
  | 'DISCURSIVA'
  | 'OUTRA'

export type TrilhaPublicada = {
  identificador: string
  codigo: string
  nome: string
  versaoPublicada: string
  descricao?: string
  quantidadeDeDisciplinas: number
  quantidadeDeTarefas: number
  quantidadeDeTarefasConcluidas: number
  aderida: boolean
}

export type TarefaDaTrilha = {
  identificador: string
  numero: number
  titulo: string
  aula?: string
  tipoDeAtividade: TipoDeAtividadeDaTrilha
  enderecoDoMaterial?: string
  orientacao?: string
  situacao: SituacaoDoAcompanhamentoDaTarefa
  observacao?: string
  concluidaEm?: string
}

export type DisciplinaDaTrilha = {
  identificador: string
  nome: string
  ordem: number
  tarefas: TarefaDaTrilha[]
}

export type DetalheDaTrilhaPublicada = {
  trilha: TrilhaPublicada
  disciplinas: DisciplinaDaTrilha[]
}

export const listarTrilhasPublicadas = (sinal?: AbortSignal) =>
  requisitar<TrilhaPublicada[]>('/v1/trilhas', { signal: sinal })

export const obterTrilhaPublicada = (
  identificador: string,
  sinal?: AbortSignal,
) =>
  requisitar<DetalheDaTrilhaPublicada>(`/v1/trilhas/${identificador}`, {
    signal: sinal,
  })

export const aderirATrilhaPublicada = (identificador: string) =>
  requisitar<TrilhaPublicada>(`/v1/trilhas/${identificador}/adesao`, {
    method: 'POST',
  })

export const atualizarAcompanhamentoDaTarefa = (
  trilha: string,
  tarefa: string,
  situacao: SituacaoDoAcompanhamentoDaTarefa,
) =>
  requisitar<TarefaDaTrilha>(
    `/v1/trilhas/${trilha}/tarefas/${tarefa}/acompanhamento`,
    {
      method: 'PUT',
      body: JSON.stringify({ situacao }),
    },
  )
