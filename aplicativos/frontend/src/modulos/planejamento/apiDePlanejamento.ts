import { requisitar } from '@/compartilhado/api/clienteHttp'

export type EstadoDoPlanoSemanal =
  'RASCUNHO' | 'ATIVO' | 'ENCERRADO' | 'CANCELADO'

export interface DisponibilidadeDoDia {
  identificador: string
  data: string
  minutosDisponiveis: number
  atualizadoEm: string
  versao: number
}

export interface PlanoSemanal {
  identificador: string
  dataInicial: string
  dataFinal: string
  estado: EstadoDoPlanoSemanal
  disponibilidades: DisponibilidadeDoDia[]
  blocos: BlocoDeEstudo[]
  totalDeMinutosDisponiveis: number
  totalDeMinutosPlanejados: number
  quantidadeDeBlocos: number
  possuiExcesso: boolean
  resumosDosDias: ResumoDoDiaPlanejado[]
  criadoEm: string
  atualizadoEm: string
  versao: number
}

export interface ResumoDoDiaPlanejado {
  data: string
  minutosDisponiveis: number
  minutosPlanejados: number
  saldoEmMinutos: number
  possuiExcesso: boolean
}

export type TipoDeAtividade =
  | 'TEORIA'
  | 'QUESTOES'
  | 'REVISAO'
  | 'CADERNO_DE_ERROS'
  | 'SIMULADO'
  | 'DISCURSIVA'
  | 'OUTRA'

export interface BlocoDeEstudo {
  identificador: string
  identificadorDoPlano: string
  identificadorDaMateria?: string
  identificadorDoTopico?: string
  titulo: string
  tipoDeAtividade: TipoDeAtividade
  data: string
  duracaoPrevistaEmMinutos: number
  ordem: number
  horarioPrevisto?: string
  observacao?: string
  estado: 'PLANEJADO'
  criadoEm: string
  atualizadoEm: string
  versao: number
}

export interface DadosDoBlocoDeEstudo {
  identificadorDaMateria?: string
  identificadorDoTopico?: string
  titulo: string
  tipoDeAtividade: TipoDeAtividade
  data: string
  duracaoPrevistaEmMinutos: number
  ordem: number
  horarioPrevisto?: string
  observacao?: string
}

export type EstadoDoPlanejamentoDeHoje =
  'SEM_PLANO' | 'PLANO_EM_RASCUNHO' | 'DIA_SEM_BLOCOS' | 'DIA_PLANEJADO'

export interface PlanejamentoDeHoje {
  estado: EstadoDoPlanejamentoDeHoje
  data: string
  identificadorDoPlano?: string
  dataInicialDoPlano?: string
  minutosDisponiveis: number
  minutosPlanejados: number
  quantidadeDeBlocos: number
  proximoBloco?: BlocoDeEstudo
  sequencia: BlocoDeEstudo[]
  atrasados: BlocoDeEstudo[]
  realizados: BlocoDeEstudo[]
}

export interface DisponibilidadeInformada {
  data: string
  minutosDisponiveis: number
}

export function criarPlanoSemanal(dataInicial: string): Promise<PlanoSemanal> {
  return requisitar<PlanoSemanal>('/v1/planos-semanais', {
    method: 'POST',
    body: JSON.stringify({ dataInicial }),
  })
}

export function obterPlanoSemanal(dataInicial: string): Promise<PlanoSemanal> {
  const parametros = new URLSearchParams({ dataInicial })
  return requisitar<PlanoSemanal>(`/v1/planos-semanais?${parametros}`)
}

export function obterPlanejamentoDeHoje(
  data: string,
  sinal?: AbortSignal,
): Promise<PlanejamentoDeHoje> {
  const parametros = new URLSearchParams({ data })
  return requisitar<PlanejamentoDeHoje>(`/v1/planejamento/hoje?${parametros}`, {
    signal: sinal,
  })
}

export function alterarDisponibilidades(
  identificador: string,
  disponibilidades: DisponibilidadeInformada[],
): Promise<PlanoSemanal> {
  return requisitar<PlanoSemanal>(
    `/v1/planos-semanais/${identificador}/disponibilidades`,
    {
      method: 'PUT',
      body: JSON.stringify({ disponibilidades }),
    },
  )
}

export function ativarPlanoSemanal(
  identificador: string,
): Promise<PlanoSemanal> {
  return requisitar<PlanoSemanal>(
    `/v1/planos-semanais/${identificador}/ativacao`,
    { method: 'POST' },
  )
}

export function adicionarBloco(
  plano: string,
  dados: DadosDoBlocoDeEstudo,
): Promise<BlocoDeEstudo> {
  return requisitar<BlocoDeEstudo>(`/v1/planos-semanais/${plano}/blocos`, {
    method: 'POST',
    body: JSON.stringify(dados),
  })
}

export function alterarBloco(
  identificador: string,
  dados: DadosDoBlocoDeEstudo,
): Promise<BlocoDeEstudo> {
  return requisitar<BlocoDeEstudo>(`/v1/blocos-de-estudo/${identificador}`, {
    method: 'PUT',
    body: JSON.stringify(dados),
  })
}

export function excluirBloco(identificador: string): Promise<void> {
  return requisitar<void>(`/v1/blocos-de-estudo/${identificador}`, {
    method: 'DELETE',
  })
}

export function reordenarBlocos(
  plano: string,
  data: string,
  identificadoresOrdenados: string[],
): Promise<PlanoSemanal> {
  return requisitar<PlanoSemanal>(
    `/v1/planos-semanais/${plano}/ordem-dos-blocos`,
    {
      method: 'PUT',
      body: JSON.stringify({ data, identificadoresOrdenados }),
    },
  )
}
