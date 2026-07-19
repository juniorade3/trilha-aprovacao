import { requisitar } from '@/compartilhado/api/clienteHttp'
import type { RespostaPaginada } from '@/modulos/materias/apiDeConteudos'

export type TipoDeMaterial = 'AULA' | 'PDF' | 'OUTRO'
export type SituacaoDoRegistroDeEstudo = 'ATIVO' | 'CORRIGIDO' | 'CANCELADO'

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
  dataHora: string
  duracaoEmMinutos: number
  observacao?: string
  situacao: SituacaoDoRegistroDeEstudo
  criadoEm: string
  atualizadoEm: string
  versao: number
}

export type DadosDeRegistroDeEstudo = {
  identificadorDoTopico: string
  identificadorDoMaterial?: string
  dataHora: string
  duracaoEmMinutos: number
  observacao?: string
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
