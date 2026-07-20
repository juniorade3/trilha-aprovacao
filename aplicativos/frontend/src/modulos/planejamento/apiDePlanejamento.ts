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

export type EstadoDoBlocoDeEstudo =
  | 'PLANEJADO'
  | 'EM_ANDAMENTO'
  | 'CONCLUIDO'
  | 'PARCIALMENTE_CONCLUIDO'
  | 'CANCELADO'

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
  estado: EstadoDoBlocoDeEstudo
  quantidadeDeReagendamentos: number
  reagendadoEm?: string
  criadoEm: string
  atualizadoEm: string
  versao: number
}

export interface ExecucaoDoBloco {
  identificador: string
  identificadorDoBloco: string
  iniciadaEm: string
  encerradaEm?: string
  duracaoExecutadaEmMinutos?: number
  resultado?: 'CONCLUIDO' | 'PARCIALMENTE_CONCLUIDO'
  observacao?: string
  identificadorDoRegistroDeEstudo?: string
  criadoEm: string
  atualizadoEm: string
  versao: number
}

export interface RegistroDeEstudoDaExecucao {
  identificador: string
  identificadorDoTopico: string
  dataHora: string
  duracaoEmMinutos: number
}

export interface TopicoParaRegistro {
  identificador: string
  nome: string
}

export interface ResultadoDaExecucaoDoBloco {
  bloco: BlocoDeEstudo
  execucao: ExecucaoDoBloco
  estudo?: RegistroDeEstudoDaExecucao
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
  | 'SEM_PLANO'
  | 'PLANO_EM_RASCUNHO'
  | 'PLANO_ENCERRADO'
  | 'PLANO_CANCELADO'
  | 'DIA_SEM_BLOCOS'
  | 'DIA_PLANEJADO'

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

export type PrioridadeDaMateriaNoPlano =
  'ALTA' | 'NORMAL' | 'BAIXA' | 'NAO_INCLUIR'

export interface MateriaParaGeracao {
  identificadorDaMateria: string
  nome: string
  ordemEstavel: number
  prioridade: PrioridadeDaMateriaNoPlano
}

export interface JustificativaDaGeracao {
  codigo: string
  mensagem: string
}

export interface BlocoPreservadoNaPrevia {
  identificador: string
  identificadorDaMateria?: string
  nomeDaMateria?: string
  titulo: string
  tipoDeAtividade: TipoDeAtividade
  duracaoEmMinutos: number
  ordem: number
}

export interface BlocoSugeridoNaPrevia {
  identificadorDaMateria?: string
  nomeDaMateria?: string
  titulo: string
  tipoDeAtividade: TipoDeAtividade
  duracaoEmMinutos: number
  justificativas: JustificativaDaGeracao[]
}

export interface DiaDaPreviaDaGeracao {
  data: string
  capacidade: {
    minutosDisponiveis: number
    minutosPreservados: number
    minutosSugeridos: number
    minutosLivres: number
  }
  blocosPreservados: BlocoPreservadoNaPrevia[]
  blocosSugeridos: BlocoSugeridoNaPrevia[]
  avisos: JustificativaDaGeracao[]
}

export interface PreviaDaGeracao {
  identificadorDoPlano: string
  dias: DiaDaPreviaDaGeracao[]
  avisos: JustificativaDaGeracao[]
  aplicada: false
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

export function obterExecucaoEmAndamento(): Promise<ResultadoDaExecucaoDoBloco> {
  return requisitar<ResultadoDaExecucaoDoBloco>(
    '/v1/planejamento/execucao-em-andamento',
  )
}

export function iniciarBloco(
  identificador: string,
  dataDeReferencia: string,
): Promise<ResultadoDaExecucaoDoBloco> {
  return requisitar<ResultadoDaExecucaoDoBloco>(
    `/v1/blocos-de-estudo/${identificador}/inicio`,
    {
      method: 'POST',
      body: JSON.stringify({ dataDeReferencia }),
    },
  )
}

export function concluirBloco(
  identificador: string,
  duracaoExecutadaEmMinutos: number,
  observacao?: string,
  identificadorDoTopico?: string,
): Promise<ResultadoDaExecucaoDoBloco> {
  return requisitar<ResultadoDaExecucaoDoBloco>(
    `/v1/blocos-de-estudo/${identificador}/conclusao`,
    {
      method: 'POST',
      body: JSON.stringify({
        duracaoExecutadaEmMinutos,
        observacao,
        identificadorDoTopico,
      }),
    },
  )
}

export function interromperBloco(
  identificador: string,
  duracaoExecutadaEmMinutos: number,
  observacao?: string,
  identificadorDoTopico?: string,
): Promise<ResultadoDaExecucaoDoBloco> {
  return requisitar<ResultadoDaExecucaoDoBloco>(
    `/v1/blocos-de-estudo/${identificador}/interrupcao`,
    {
      method: 'POST',
      body: JSON.stringify({
        duracaoExecutadaEmMinutos,
        observacao,
        identificadorDoTopico,
      }),
    },
  )
}

export function obterExecucaoDoBloco(
  identificadorDoBloco: string,
): Promise<ResultadoDaExecucaoDoBloco> {
  return requisitar<ResultadoDaExecucaoDoBloco>(
    `/v1/blocos-de-estudo/${identificadorDoBloco}/execucao`,
  )
}

export function listarTopicosParaRegistro(
  identificadorDoBloco: string,
): Promise<TopicoParaRegistro[]> {
  return requisitar<TopicoParaRegistro[]>(
    `/v1/blocos-de-estudo/${identificadorDoBloco}/topicos-para-registro`,
  )
}

export function registrarExecucaoNoHistorico(
  identificadorDaExecucao: string,
  identificadorDoTopico?: string,
): Promise<ResultadoDaExecucaoDoBloco> {
  return requisitar<ResultadoDaExecucaoDoBloco>(
    `/v1/execucoes-de-bloco/${identificadorDaExecucao}/registro-de-estudo`,
    { method: 'POST', body: JSON.stringify({ identificadorDoTopico }) },
  )
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

export async function listarMateriasParaGeracao(
  identificadorDoPlano: string,
): Promise<MateriaParaGeracao[]> {
  const resposta = await requisitar<{ materias: MateriaParaGeracao[] }>(
    `/v1/planos-semanais/${identificadorDoPlano}/materias-para-geracao`,
  )
  return resposta.materias
}

export async function substituirPrioridadesDeMaterias(
  identificadorDoPlano: string,
  prioridades: Pick<
    MateriaParaGeracao,
    'identificadorDaMateria' | 'prioridade'
  >[],
): Promise<MateriaParaGeracao[]> {
  const resposta = await requisitar<{ materias: MateriaParaGeracao[] }>(
    `/v1/planos-semanais/${identificadorDoPlano}/prioridades-de-materias`,
    { method: 'PUT', body: JSON.stringify({ prioridades }) },
  )
  return resposta.materias
}

export function gerarPreviaDeterministica(
  identificadorDoPlano: string,
  duracaoPadraoDoBlocoPrincipalEmMinutos: number,
  duracaoDoBlocoDeRevisaoEmMinutos: number,
): Promise<PreviaDaGeracao> {
  return requisitar<PreviaDaGeracao>(
    `/v1/planos-semanais/${identificadorDoPlano}/geracao-deterministica/previa`,
    {
      method: 'POST',
      body: JSON.stringify({
        duracaoPadraoDoBlocoPrincipalEmMinutos,
        duracaoDoBlocoDeRevisaoEmMinutos,
      }),
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

export function reagendarBloco(
  identificador: string,
  data: string,
  horarioPrevisto: string | undefined,
  ordem: number,
): Promise<BlocoDeEstudo> {
  return requisitar<BlocoDeEstudo>(
    `/v1/blocos-de-estudo/${identificador}/reagendamento`,
    {
      method: 'POST',
      body: JSON.stringify({ data, horarioPrevisto, ordem }),
    },
  )
}

export function cancelarBloco(identificador: string): Promise<BlocoDeEstudo> {
  return requisitar<BlocoDeEstudo>(
    `/v1/blocos-de-estudo/${identificador}/cancelamento`,
    { method: 'POST' },
  )
}

export function encerrarPlanoSemanal(
  identificador: string,
): Promise<PlanoSemanal> {
  return requisitar<PlanoSemanal>(
    `/v1/planos-semanais/${identificador}/encerramento`,
    { method: 'POST' },
  )
}

export function cancelarPlanoSemanal(
  identificador: string,
): Promise<PlanoSemanal> {
  return requisitar<PlanoSemanal>(
    `/v1/planos-semanais/${identificador}/cancelamento`,
    { method: 'POST' },
  )
}

export function corrigirExecucao(
  identificador: string,
  resultado: 'CONCLUIDO' | 'PARCIALMENTE_CONCLUIDO',
  duracaoExecutadaEmMinutos: number,
  observacao?: string,
): Promise<ResultadoDaExecucaoDoBloco> {
  return requisitar<ResultadoDaExecucaoDoBloco>(
    `/v1/execucoes-de-bloco/${identificador}/correcao`,
    {
      method: 'PUT',
      body: JSON.stringify({
        resultado,
        duracaoExecutadaEmMinutos,
        observacao,
      }),
    },
  )
}
