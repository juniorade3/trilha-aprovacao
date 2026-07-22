import { requisitar } from '@/compartilhado/api/clienteHttp'
import type { EvidenciaDeAprendizagem } from '@/modulos/estudos/apiDeEstudos'
import type {
  FaixaDaPriorizacao,
  GrupoDaPriorizacao,
} from './apiDePriorizacaoDeTopicos'

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

export type OrigemDoBlocoDeEstudo =
  | 'MANUAL'
  | 'GERADO_DETERMINISTICAMENTE'
  | 'GERADO_AJUSTADO_MANUALMENTE'
  | 'REPLANEJADO'
  | 'REPLANEJADO_AJUSTADO_MANUALMENTE'

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
  origem: OrigemDoBlocoDeEstudo
  justificativaDaGeracao?: string
  justificativaDoReplanejamento?: string
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
  evidencia?: EvidenciaDeAprendizagem
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
  identificadorDaMateria?: string | null
  nomeDaMateria?: string | null
  titulo: string
  tipoDeAtividade: TipoDeAtividade
  duracaoEmMinutos: number
  ordem: number
}

export interface BlocoSugeridoNaPrevia {
  identificadorDaMateria?: string | null
  nomeDaMateria?: string | null
  identificadorDoTopico?: string | null
  nomeDoTopico?: string | null
  grupoDaPriorizacao?: GrupoDaPriorizacao | null
  faixaDaPriorizacao?: FaixaDaPriorizacao | null
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
  assinaturaDaPrevia: string
  dias: DiaDaPreviaDaGeracao[]
  avisos: JustificativaDaGeracao[]
  aplicada: false
}

export interface ResultadoDaAplicacaoDaGeracao {
  plano: PlanoSemanal
  resumo: {
    quantidadeDeBlocosCriados: number
    quantidadeDeBlocosSubstituidos: number
    quantidadeDeBlocosPreservados: number
  }
}

export interface PreviaDoReplanejamento {
  identificadorDoPlano: string
  dataDeReferencia: string
  dataFinal: string
  assinaturaDaPrevia: string
  resumo: {
    quantidadeDePendencias: number
    quantidadeDeFragmentos: number
    minutosPendentes: number
    minutosAlocados: number
    minutosNaoAlocados: number
    confirmacoesExigidas: number
  }
  capacidadesPorDia: Array<{
    data: string
    minutosDisponiveis: number
    minutosOcupados: number
    minutosAlocados: number
    minutosRestantes: number
    quantidadeDeMaterias: number
  }>
  blocosPreservados: Array<{
    identificador: string
    titulo: string
    data: string
    duracaoEmMinutos: number
    estado: EstadoDoBlocoDeEstudo
  }>
  pendencias: Array<{
    identificadorDoBloco: string
    titulo: string
    dataOriginal: string
    minutosPrevistos: number
    minutosExecutados: number
    minutosPendentes: number
    quantidadeDeReagendamentos: number
    prioridade: PrioridadeDaMateriaNoPlano
    motivo: string
    decisao:
      'ADIAR' | 'DIVIDIR' | 'DECIDIR_MANUALMENTE' | 'SEM_CAPACIDADE' | 'IGNORAR'
    exigeConfirmacao: boolean
    minutosNaoAlocados: number
    justificativa: string
    fragmentos: Array<{
      data: string
      duracaoEmMinutos: number
      sequencia: number
    }>
    sugestoesManuais: string[]
  }>
}

export interface ResultadoDaAplicacaoDoReplanejamento {
  identificadorDoReplanejamento: string
  aplicadoEm: string
  planoAtualizado: PlanoSemanal
  quantidadeDePendenciasTransferidas: number
  quantidadeDeFragmentosCriados: number
}

export interface HistoricoSemanal {
  identificadorDoPlano: string
  dataDeReferencia: string
  estadoDoPlano: EstadoDoPlanoSemanal
  resumo: {
    minutosPlanejados: number
    minutosExecutados: number
    minutosConcluidos: number
    minutosInterrompidos: number
    minutosPendentes: number
    blocosConcluidos: number
    blocosParciais: number
    blocosNaoIniciados: number
    blocosReagendados: number
    taxaExecutadaSobrePlanejada: number
  }
  transferencias: Array<{
    identificadorDoReplanejamento: string
    aplicadoEm: string
    identificadorDoBlocoOriginal: string
    identificadorDoBlocoCriado: string
    data: string
    duracaoEmMinutos: number
  }>
  observacaoDoSnapshot: string
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
  evidencia?: EvidenciaDeAprendizagem,
): Promise<ResultadoDaExecucaoDoBloco> {
  return requisitar<ResultadoDaExecucaoDoBloco>(
    `/v1/blocos-de-estudo/${identificador}/conclusao`,
    {
      method: 'POST',
      body: JSON.stringify({
        duracaoExecutadaEmMinutos,
        observacao,
        identificadorDoTopico,
        evidencia,
      }),
    },
  )
}

export function interromperBloco(
  identificador: string,
  duracaoExecutadaEmMinutos: number,
  observacao?: string,
  identificadorDoTopico?: string,
  evidencia?: EvidenciaDeAprendizagem,
): Promise<ResultadoDaExecucaoDoBloco> {
  return requisitar<ResultadoDaExecucaoDoBloco>(
    `/v1/blocos-de-estudo/${identificador}/interrupcao`,
    {
      method: 'POST',
      body: JSON.stringify({
        duracaoExecutadaEmMinutos,
        observacao,
        identificadorDoTopico,
        evidencia,
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
  dataDeReferencia: string,
  duracaoDoBlocoPrincipalEmMinutos: number,
): Promise<PreviaDaGeracao> {
  return requisitar<PreviaDaGeracao>(
    `/v1/planos-semanais/${identificadorDoPlano}/geracao-deterministica/previa`,
    {
      method: 'POST',
      body: JSON.stringify({
        dataDeReferencia,
        duracaoDoBlocoPrincipalEmMinutos,
      }),
    },
  )
}

export function aplicarGeracaoDeterministica(
  identificadorDoPlano: string,
  dataDeReferencia: string,
  duracaoDoBlocoPrincipalEmMinutos: number,
  substituirBlocosGerados: boolean,
  assinaturaDaPrevia: string,
): Promise<ResultadoDaAplicacaoDaGeracao> {
  return requisitar<ResultadoDaAplicacaoDaGeracao>(
    `/v1/planos-semanais/${identificadorDoPlano}/geracao-deterministica`,
    {
      method: 'POST',
      body: JSON.stringify({
        dataDeReferencia,
        duracaoDoBlocoPrincipalEmMinutos,
        substituirBlocosGerados,
        assinaturaDaPrevia,
      }),
    },
  )
}

export function gerarPreviaDoReplanejamento(
  identificadorDoPlano: string,
  dataDeReferencia: string,
  identificadoresDasPendenciasIgnoradas: string[],
): Promise<PreviaDoReplanejamento> {
  return requisitar<PreviaDoReplanejamento>(
    `/v1/planos-semanais/${identificadorDoPlano}/replanejamento/previa`,
    {
      method: 'POST',
      body: JSON.stringify({
        dataDeReferencia,
        identificadoresDasPendenciasIgnoradas,
      }),
    },
  )
}

export function aplicarReplanejamento(
  identificadorDoPlano: string,
  dataDeReferencia: string,
  identificadoresDasPendenciasIgnoradas: string[],
  identificadoresDasConfirmacoesDoLimite: string[],
  assinaturaDaPrevia: string,
): Promise<ResultadoDaAplicacaoDoReplanejamento> {
  return requisitar<ResultadoDaAplicacaoDoReplanejamento>(
    `/v1/planos-semanais/${identificadorDoPlano}/replanejamento`,
    {
      method: 'POST',
      body: JSON.stringify({
        dataDeReferencia,
        identificadoresDasPendenciasIgnoradas,
        identificadoresDasConfirmacoesDoLimite,
        assinaturaDaPrevia,
      }),
    },
  )
}

export function obterHistoricoSemanal(
  identificadorDoPlano: string,
  dataDeReferencia: string,
): Promise<HistoricoSemanal> {
  const parametros = new URLSearchParams({ dataDeReferencia })
  return requisitar<HistoricoSemanal>(
    `/v1/planos-semanais/${identificadorDoPlano}/historico-semanal?${parametros}`,
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
  identificadorDoTopico?: string,
  evidencia?: EvidenciaDeAprendizagem,
): Promise<ResultadoDaExecucaoDoBloco> {
  return requisitar<ResultadoDaExecucaoDoBloco>(
    `/v1/execucoes-de-bloco/${identificador}/correcao`,
    {
      method: 'PUT',
      body: JSON.stringify({
        resultado,
        duracaoExecutadaEmMinutos,
        observacao,
        identificadorDoTopico,
        evidencia,
      }),
    },
  )
}
