import { requisitar } from '@/compartilhado/api/clienteHttp'
import type { RespostaPaginada } from '@/modulos/materias/apiDeConteudos'

export type TipoDeMaterial = 'AULA' | 'PDF' | 'OUTRO'
export type SituacaoDoRegistroDeEstudo = 'ATIVO' | 'CORRIGIDO' | 'CANCELADO'
export type TipoDeEstudo =
  | 'TEORIA'
  | 'QUESTOES'
  | 'REVISAO'
  | 'CADERNO_DE_ERROS'
  | 'SIMULADO'
  | 'DISCURSIVA'
  | 'OUTRA'

export type PadraoDeErroInformado = {
  descricao: string
  quantidadeDeOcorrencias: number
}

export type EvidenciaDeAprendizagem = {
  identificador?: string
  resultadoDeQuestoes?: {
    quantidadeDeQuestoes: number
    quantidadeDeAcertos: number
  }
  quantidadeDeErros?: number
  nivelDeRecordacao?: number
  dificuldadePercebida?: number
  resultadoDaRevisao?: 'PRECISA_REFORCO' | 'PARCIAL' | 'CONSOLIDADA'
  padroesDeErro?: PadraoDeErroInformado[]
}

export type ModeloDeEvidencia = {
  quantidadeDeQuestoes?: number
  quantidadeDeAcertos?: number
  nivelDeRecordacao?: number
  dificuldadePercebida?: number
  padroesDeErro: PadraoDeErroInformado[]
}

export function paraEvidencia(
  modelo: ModeloDeEvidencia,
): EvidenciaDeAprendizagem | undefined {
  const possuiResultado =
    modelo.quantidadeDeQuestoes != null ||
    modelo.quantidadeDeAcertos != null ||
    modelo.nivelDeRecordacao != null ||
    modelo.dificuldadePercebida != null
  if (!possuiResultado) return undefined
  return {
    resultadoDeQuestoes:
      modelo.quantidadeDeQuestoes == null && modelo.quantidadeDeAcertos == null
        ? undefined
        : {
            quantidadeDeQuestoes: modelo.quantidadeDeQuestoes!,
            quantidadeDeAcertos: modelo.quantidadeDeAcertos!,
          },
    nivelDeRecordacao: modelo.nivelDeRecordacao,
    dificuldadePercebida: modelo.dificuldadePercebida,
    padroesDeErro: modelo.padroesDeErro.filter((padrao) =>
      padrao.descricao.trim(),
    ),
  }
}

export type MaterialDeEstudo = {
  identificador: string
  titulo: string
  tipo: TipoDeMaterial
  descricao?: string
  fonte?: string
  endereco?: string
  duracaoEstimadaEmMinutos?: number
  arquivado: boolean
  criadoEm: string
  atualizadoEm: string
  versao: number
}

export type DadosDeMaterial = {
  titulo: string
  tipo: TipoDeMaterial
  descricao?: string
  fonte?: string
  endereco?: string
  duracaoEstimadaEmMinutos?: number
}

export type CoberturaDeTopico = {
  identificador: string
  identificadorDoMaterial: string
  identificadorDoTopico: string
  nomeDoTopico: string
  criadoEm: string
}

export type RegistroDeEstudo = {
  identificador: string
  identificadorDoTopico: string
  nomeDoTopico: string
  identificadorDoMaterial?: string
  tituloDoMaterial?: string
  identificadorDoRegistroDeOrigem?: string
  tipoDeEstudo: TipoDeEstudo
  dataHora: string
  duracaoEmMinutos: number
  observacao?: string
  situacao: SituacaoDoRegistroDeEstudo
  criadoEm: string
  atualizadoEm: string
  versao: number
  evidencia?: EvidenciaDeAprendizagem
}

export type DadosDeRegistroDeEstudo = {
  identificadorDoTopico: string
  identificadorDoMaterial?: string
  dataHora: string
  duracaoEmMinutos: number
  observacao?: string
  tipoDeEstudo?: TipoDeEstudo
  evidencia?: EvidenciaDeAprendizagem
}

export type DiagnosticoDeTopico = {
  identificadorDoTopico: string
  nomeDoTopico: string
  identificadorDaMateria: string
  nomeDaMateria: string
  exigidoNoConcursoAtivo: boolean
  quantidadeDeEvidencias: number
  totaisHistoricos: TotaisDeQuestoes
  totaisDosUltimosTrintaDias: TotaisDeQuestoes
  percentualRecenteDeAcertos?: number
  ultimaRecordacao?: number
  mediaRecenteDeRecordacao?: number
  ultimaDificuldade?: number
  mediaRecenteDeDificuldade?: number
  resultadoDaUltimaRevisao?: 'PRECISA_REFORCO' | 'PARCIAL' | 'CONSOLIDADA'
  ultimaEvidenciaEm?: string
  padroesDeErroRepetidos: Array<{
    identificador: string
    descricao: string
    quantidadeDeEvidencias: number
    quantidadeDeOcorrencias: number
  }>
}

export type TotaisDeQuestoes = {
  questoes: number
  acertos: number
  erros: number
}

export function listarMateriaisDeEstudo(
  pesquisa = '',
  incluirArquivados = false,
  sinal?: AbortSignal,
  pagina = 0,
  tamanho = 100,
) {
  const parametros = new URLSearchParams({
    pesquisa,
    incluirArquivados: String(incluirArquivados),
    pagina: String(pagina),
    tamanho: String(tamanho),
  })
  return requisitar<RespostaPaginada<MaterialDeEstudo>>(
    `/v1/materiais?${parametros}`,
    { signal: sinal },
  )
}

export async function listarTodosOsMateriaisDeEstudo(
  pesquisa = '',
  incluirArquivados = false,
  sinal?: AbortSignal,
) {
  const primeiraPagina = await listarMateriaisDeEstudo(
    pesquisa,
    incluirArquivados,
    sinal,
    0,
    100,
  )
  if (primeiraPagina.totalDePaginas <= 1) return primeiraPagina.itens
  const demaisPaginas = await Promise.all(
    Array.from({ length: primeiraPagina.totalDePaginas - 1 }, (_, indice) =>
      listarMateriaisDeEstudo(
        pesquisa,
        incluirArquivados,
        sinal,
        indice + 1,
        100,
      ),
    ),
  )
  return [
    ...primeiraPagina.itens,
    ...demaisPaginas.flatMap((pagina) => pagina.itens),
  ]
}

export function criarMaterialDeEstudo(dados: DadosDeMaterial) {
  return requisitar<MaterialDeEstudo>('/v1/materiais', {
    method: 'POST',
    body: JSON.stringify(dados),
  })
}

export function alterarMaterialDeEstudo(
  identificador: string,
  dados: DadosDeMaterial,
) {
  return requisitar<MaterialDeEstudo>(`/v1/materiais/${identificador}`, {
    method: 'PUT',
    body: JSON.stringify(dados),
  })
}

export function definirArquivamentoDoMaterial(
  identificador: string,
  arquivado: boolean,
) {
  return requisitar<MaterialDeEstudo>(
    `/v1/materiais/${identificador}/arquivamento`,
    {
      method: 'POST',
      body: JSON.stringify({ arquivado }),
    },
  )
}

export function excluirMaterialDeEstudo(identificador: string) {
  return requisitar<void>(`/v1/materiais/${identificador}`, {
    method: 'DELETE',
  })
}

export function listarCoberturas(
  identificadorDoMaterial: string,
  sinal?: AbortSignal,
) {
  return requisitar<CoberturaDeTopico[]>(
    `/v1/materiais/${identificadorDoMaterial}/topicos`,
    { signal: sinal },
  )
}

export function adicionarCobertura(
  identificadorDoMaterial: string,
  identificadorDoTopico: string,
) {
  return requisitar<CoberturaDeTopico>(
    `/v1/materiais/${identificadorDoMaterial}/topicos`,
    {
      method: 'POST',
      body: JSON.stringify({ identificadorDoTopico }),
    },
  )
}

export function removerCobertura(
  identificadorDoMaterial: string,
  identificadorDoTopico: string,
) {
  return requisitar<void>(
    `/v1/materiais/${identificadorDoMaterial}/topicos/${identificadorDoTopico}`,
    { method: 'DELETE' },
  )
}

export function listarEstudos(sinal?: AbortSignal, pagina = 0, tamanho = 100) {
  return requisitar<RespostaPaginada<RegistroDeEstudo>>(
    `/v1/estudos?pagina=${pagina}&tamanho=${tamanho}`,
    { signal: sinal },
  )
}

export async function listarTodosOsEstudos(sinal?: AbortSignal) {
  const primeiraPagina = await listarEstudos(sinal, 0, 100)
  if (primeiraPagina.totalDePaginas <= 1) return primeiraPagina.itens
  const demaisPaginas = await Promise.all(
    Array.from({ length: primeiraPagina.totalDePaginas - 1 }, (_, indice) =>
      listarEstudos(sinal, indice + 1, 100),
    ),
  )
  return [
    ...primeiraPagina.itens,
    ...demaisPaginas.flatMap((pagina) => pagina.itens),
  ]
}

export function registrarEstudo(dados: DadosDeRegistroDeEstudo) {
  return requisitar<RegistroDeEstudo>('/v1/estudos', {
    method: 'POST',
    body: JSON.stringify(dados),
  })
}

export function corrigirEstudo(
  identificador: string,
  dados: DadosDeRegistroDeEstudo,
) {
  return requisitar<RegistroDeEstudo>(`/v1/estudos/${identificador}/correcao`, {
    method: 'PUT',
    body: JSON.stringify(dados),
  })
}

export function cancelarEstudo(identificador: string) {
  return requisitar<RegistroDeEstudo>(
    `/v1/estudos/${identificador}/cancelamento`,
    { method: 'POST' },
  )
}

export function sugerirPadroesDeErro(
  identificadorDoTopico: string,
  pesquisa = '',
  sinal?: AbortSignal,
) {
  const parametros = new URLSearchParams({ identificadorDoTopico, pesquisa })
  return requisitar<string[]>(`/v1/evidencias/padroes-de-erro?${parametros}`, {
    signal: sinal,
  })
}

export function consultarDiagnosticoDeTopicos(
  dataDeReferencia: string,
  identificadorDaMateria?: string,
  somenteExigidosNoConcursoAtivo = false,
  sinal?: AbortSignal,
) {
  const parametros = new URLSearchParams({
    dataDeReferencia,
    somenteExigidosNoConcursoAtivo: String(somenteExigidosNoConcursoAtivo),
  })
  if (identificadorDaMateria)
    parametros.set('identificadorDaMateria', identificadorDaMateria)
  return requisitar<DiagnosticoDeTopico[]>(
    `/v1/evidencias/diagnostico-de-topicos?${parametros}`,
    { signal: sinal },
  )
}
