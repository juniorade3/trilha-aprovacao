import { requisitar } from '@/compartilhado/api/clienteHttp'

export type ConcursoAtivoDoDashboard = {
  identificador: string
  nome: string
  orgao?: string
  banca?: string
  situacao: string
  identificadorDoCargoSelecionado?: string
  nomeDoCargoSelecionado?: string
}

export type AtividadeRecenteDoDashboard = {
  identificador: string
  identificadorDoTopico: string
  nomeDoTopico: string
  tituloDoMaterial?: string
  dataHora: string
  duracaoEmMinutos: number
}

export type AlertaDoDashboard = {
  codigo: string
  titulo: string
  mensagem: string
  nivel: 'ATENCAO'
}

export type Dashboard = {
  concursoAtivo?: ConcursoAtivoDoDashboard
  dataDaProximaProva?: string
  diasAteAProva?: number
  tempoEstudadoNaSemanaEmMinutos: number
  quantidadeDeMaterias: number
  quantidadeDeTopicosExigidos: number
  quantidadeDeTopicosComEstudo: number
  quantidadeDeItensMapeados: number
  quantidadeDeItensSemMapeamento: number
  atividadeRecente: AtividadeRecenteDoDashboard[]
  alertas: AlertaDoDashboard[]
}

export function obterDashboard(sinal?: AbortSignal) {
  return requisitar<Dashboard>('/v1/dashboard', { signal: sinal })
}
