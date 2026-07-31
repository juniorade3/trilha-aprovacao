import { requisitar } from '@/compartilhado/api/clienteHttp'

export type EstadoDaImportacaoDeEdital =
  | 'RECEBIDA'
  | 'EXTRAINDO'
  | 'EXTRAIDA'
  | 'AGUARDANDO_SELECAO'
  | 'AGUARDANDO_CORRECOES'
  | 'VALIDADA'
  | 'AGUARDANDO_CONFIRMACAO'
  | 'APLICANDO'
  | 'APLICADA'
  | 'FALHOU'
  | 'CANCELADA'

export type ModoDaImportacaoDeEdital = 'CRIAR_NOVO' | 'COMPLEMENTAR_EXISTENTE'

export type PoliticaDeReutilizacao =
  'REUTILIZAR_COMPATIVEIS' | 'EXIGIR_DECISAO' | 'CRIAR_SEPARADO'

export type SeveridadeDoProblemaDaImportacao =
  'BLOQUEANTE' | 'EXIGE_DECISAO' | 'AVISO'

export type ProvenienciaDoDado = {
  pagina?: number | null
  secao?: string | null
  trecho?: string | null
}

export type NivelDeEscolaridade =
  'FUNDAMENTAL' | 'MEDIO' | 'TECNICO' | 'SUPERIOR' | 'NAO_INFORMADO'

export type TipoDeProva =
  'OBJETIVA' | 'DISCURSIVA' | 'PRATICA' | 'TITULOS' | 'OUTRA'

export type CaraterDaProva =
  | 'ELIMINATORIO'
  | 'CLASSIFICATORIO'
  | 'ELIMINATORIO_E_CLASSIFICATORIO'
  | 'NAO_INFORMADO'

export type ValorExtraido<T> = {
  valor?: T | null
  confianca: number
  fonte?: ProvenienciaDoDado | null
  inferido: boolean
}

export type ConcursoExtraido = {
  nome: ValorExtraido<string>
  descricao: ValorExtraido<string>
  orgao: ValorExtraido<string>
  banca: ValorExtraido<string>
  dataPrevista: ValorExtraido<string>
}

export type EditalExtraido = {
  titulo: ValorExtraido<string>
  numero: ValorExtraido<string>
  ano: ValorExtraido<number>
  descricao: ValorExtraido<string>
  dataDePublicacao: ValorExtraido<string>
}

export type CargoExtraido = {
  chave: string
  nome: ValorExtraido<string>
  area: ValorExtraido<string>
  especialidade: ValorExtraido<string>
  nivelDeEscolaridade: ValorExtraido<NivelDeEscolaridade>
  ordem: number
}

export type GrupoExtraido = {
  chave: string
  nome: ValorExtraido<string>
  ordem: number
  quantidadeDeQuestoes: ValorExtraido<number>
  pontuacaoMaxima: ValorExtraido<number>
  pontuacaoMinima: ValorExtraido<number>
}

export type ProvaExtraida = {
  chave: string
  chaveDoCargo: string
  nome: ValorExtraido<string>
  tipo: ValorExtraido<TipoDeProva>
  carater: ValorExtraido<CaraterDaProva>
  ordem: number
  dataHora: ValorExtraido<string>
  duracaoEmMinutos: ValorExtraido<number>
  quantidadeDeQuestoes: ValorExtraido<number>
  pontuacaoMaxima: ValorExtraido<number>
  pontuacaoMinima: ValorExtraido<number>
  grupos: GrupoExtraido[]
}

export type TopicoExtraido = {
  chave: string
  chaveDoPai?: string | null
  numeroOficial: ValorExtraido<string>
  nome: ValorExtraido<string>
  descricao: ValorExtraido<string>
  ordem: number
}

export type ItemExtraidoDoEdital = {
  chave: string
  chaveDoPai?: string | null
  numeroOficial: ValorExtraido<string>
  descricaoLiteral: ValorExtraido<string>
  nomeNormalizado: string
  ordem: number
  chaveDoTopicoSugerido?: string | null
}

export type MateriaExtraida = {
  chave: string
  chaveDoCargo: string
  chaveDaProva: string
  chaveDoGrupo: string
  nome: ValorExtraido<string>
  descricao: ValorExtraido<string>
  ordem: number
  peso: ValorExtraido<number>
  quantidadeDeQuestoes: ValorExtraido<number>
  pontuacaoMaxima: ValorExtraido<number>
  topicos: TopicoExtraido[]
  itensDoEdital: ItemExtraidoDoEdital[]
}

export type ExtracaoEstruturadaDoEdital = {
  versaoDoContrato: '1'
  fonte: {
    nomeDoArquivo: string
    sha256: string
    paginas: number
  }
  concurso?: ConcursoExtraido | null
  edital?: EditalExtraido | null
  cargos: CargoExtraido[]
  provas: ProvaExtraida[]
  materias: MateriaExtraida[]
  avisos: string[]
  incertezas: string[]
}

export type ExtracaoEstruturadaEditavelDoEdital = Omit<
  ExtracaoEstruturadaDoEdital,
  'concurso' | 'edital'
> & {
  concurso: ConcursoExtraido
  edital: EditalExtraido
}

export type ConfirmacaoDeCampoDaExtracao = {
  tipoDoRecurso: string
  chaveDoRecurso: string
  campo: string
}

export type ProblemaDaImportacao = {
  severidade: SeveridadeDoProblemaDaImportacao
  codigo: string
  mensagem: string
  caminho?: string | null
  tipoDoRecurso?: string | null
  chaveDoRecurso?: string | null
  campo?: string | null
  referencia?: {
    tipoDoRecurso?: string | null
    chaveDoRecurso?: string | null
    campo?: string | null
  } | null
  opcoes?: Array<{ valor: string; rotulo: string }>
}

export type AvaliacaoDoCargo = {
  chaveDoCargo: string
  pronto: boolean
  problemas: ProblemaDaImportacao[]
}

export type ItemDaPreviaDaImportacao = {
  tipo: string
  nome: string
  chave?: string | null
  identificadorExistente?: string | null
  contexto?: string | null
  quantidade?: number | null
}

export type PreviaDaImportacaoDeEdital = {
  resumo: string
  contagens: Record<string, number>
  itensACriar: ItemDaPreviaDaImportacao[]
  itensAReutilizar: ItemDaPreviaDaImportacao[]
  conflitos: ProblemaDaImportacao[]
  incertezas: string[]
  camposAusentes: string[]
  nadaFoiAlterado: boolean
  identificadorDaOperacao?: string | null
  fraseDeConfirmacao?: string | null
  expiraEm?: string | null
  confirmacaoReforcada?: boolean
  exigeConfirmacaoReforcada?: boolean
  etapaAtualDaConfirmacao?: number | null
}

export type ImportacaoDeEdital = {
  identificador: string
  estado: EstadoDaImportacaoDeEdital
  tipoDaFonte: 'TEXTO' | 'PDF_TEXTUAL' | 'PDF_DIGITALIZADO'
  nomeDoArquivo: string
  tipoMime: string
  sha256: string
  tamanhoEmBytes: number
  modo?: ModoDaImportacaoDeEdital | null
  identificadorDoConcursoExistente?: string | null
  politicaDeReutilizacao?: PoliticaDeReutilizacao | null
  versaoAtualDaExtracao: number
  hashDaExtracaoAtual?: string | null
  chaveDoCargoSelecionado?: string | null
  criadoEm: string
  atualizadoEm: string
  extracao?: ExtracaoEstruturadaDoEdital | null
  problemas: ProblemaDaImportacao[]
  interpretacaoAssistidaDisponivel?: boolean
  avaliacoesDosCargos?: AvaliacaoDoCargo[]
  previa?: PreviaDaImportacaoDeEdital | null
}

export type DecisoesDaImportacaoDeEdital = {
  chaveDoCargoSelecionado: string
  modo: ModoDaImportacaoDeEdital
  identificadorDoConcursoExistente?: string
  politicaDeReutilizacao: PoliticaDeReutilizacao
  versaoDaExtracao: number
  decisoesHumanas: Record<string, string>
  recursosParaReutilizar?: Record<string, string>
  definirEditalComoPrincipal?: boolean
  selecionarCargoCriado?: boolean
}

export type RespostaDaPreparacaoDaImportacao = {
  importacao: ImportacaoDeEdital
  previa: PreviaDaImportacaoDeEdital
}

export type RelatorioDaImportacaoDeEdital = {
  identificadorDaImportacao: string
  identificadorDoConcurso: string
  situacaoDoConcurso: string
  contagens: Record<string, number>
  identificadoresCriados: Record<string, string[]>
  reutilizacoes: string[]
  pendencias: string[]
  incertezas: string[]
  sugestoesDeMapeamento: number
  aplicadoEm: string
}

export type DadosDoRecebimentoDaImportacao = {
  modo: ModoDaImportacaoDeEdital
  identificadorDoConcursoExistente?: string
}

export function receberArquivoDoEdital(
  arquivo: File,
  dados: DadosDoRecebimentoDaImportacao,
) {
  const formulario = new FormData()
  formulario.append('arquivo', arquivo)
  formulario.append('modo', dados.modo)
  if (dados.identificadorDoConcursoExistente)
    formulario.append(
      'identificadorDoConcursoExistente',
      dados.identificadorDoConcursoExistente,
    )
  return requisitar<ImportacaoDeEdital>('/v1/importacoes-de-edital', {
    method: 'POST',
    body: formulario,
  })
}

export const receberTextoDoEdital = (
  texto: string,
  nomeDaFonte: string,
  dados: DadosDoRecebimentoDaImportacao,
) =>
  requisitar<ImportacaoDeEdital>('/v1/importacoes-de-edital/textos', {
    method: 'POST',
    body: JSON.stringify({ texto, nomeDaFonte, ...dados }),
  })

export const obterImportacaoDeEdital = (
  identificador: string,
  sinal?: AbortSignal,
) =>
  requisitar<ImportacaoDeEdital>(`/v1/importacoes-de-edital/${identificador}`, {
    signal: sinal,
  })

export const registrarDecisoesDaImportacao = (
  identificador: string,
  decisoes: DecisoesDaImportacaoDeEdital,
) =>
  requisitar<ImportacaoDeEdital>(
    `/v1/importacoes-de-edital/${identificador}/decisoes`,
    {
      method: 'PUT',
      body: JSON.stringify(decisoes),
    },
  )

export const prepararImportacaoDeEdital = (
  identificador: string,
  decisoes: DecisoesDaImportacaoDeEdital,
) =>
  requisitar<RespostaDaPreparacaoDaImportacao>(
    `/v1/importacoes-de-edital/${identificador}/preparacao`,
    {
      method: 'POST',
      body: JSON.stringify(decisoes),
    },
  )

export const obterRelatorioDaImportacao = (
  identificador: string,
  sinal?: AbortSignal,
) =>
  requisitar<RelatorioDaImportacaoDeEdital>(
    `/v1/importacoes-de-edital/${identificador}/relatorio`,
    { signal: sinal },
  )

export const iniciarNovaTentativaDaImportacao = (identificador: string) =>
  requisitar<ImportacaoDeEdital>(
    `/v1/importacoes-de-edital/${identificador}/nova-tentativa`,
    { method: 'POST' },
  )

export const corrigirExtracaoDaImportacao = (
  identificador: string,
  versaoEsperada: number,
  extracao: ExtracaoEstruturadaDoEdital,
  confirmacoesDeCampos: ConfirmacaoDeCampoDaExtracao[] = [],
) =>
  requisitar<ImportacaoDeEdital>(
    `/v1/importacoes-de-edital/${identificador}/extracao`,
    {
      method: 'PUT',
      body: JSON.stringify({
        versaoEsperada,
        extracao,
        confirmacoesDeCampos,
      }),
    },
  )

export type AlvoDaExtracaoAssistida =
  | { chaveDoCargoAlvo: string; descricaoDoCargoAlvo?: never }
  | { chaveDoCargoAlvo?: never; descricaoDoCargoAlvo: string }

export const extrairCargoComInterpretacaoAssistida = (
  identificador: string,
  versaoEsperada: number,
  alvo: AlvoDaExtracaoAssistida,
) =>
  requisitar<ImportacaoDeEdital>(
    `/v1/importacoes-de-edital/${identificador}/extracao-assistida`,
    {
      method: 'POST',
      body: JSON.stringify({ versaoEsperada, ...alvo }),
    },
  )
