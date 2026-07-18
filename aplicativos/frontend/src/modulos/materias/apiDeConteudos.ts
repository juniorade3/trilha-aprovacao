import { requisitar } from '@/compartilhado/api/clienteHttp'

export type RespostaPaginada<T> = {
  itens: T[]
  pagina: number
  tamanho: number
  totalDeItens: number
  totalDePaginas: number
}

export type Materia = {
  identificador: string
  nome: string
  descricao?: string
  cor?: string
  arquivada: boolean
  criadoEm: string
  atualizadoEm: string
  versao: number
}

export type Topico = {
  identificador: string
  identificadorDaMateria: string
  identificadorDoTopicoPai?: string
  nome: string
  descricao?: string
  ordem: number
  arquivado: boolean
  criadoEm: string
  atualizadoEm: string
  versao: number
}

export type DadosDeMateria = {
  nome: string
  descricao?: string
  cor?: string
}

export type DadosDeTopico = {
  nome: string
  descricao?: string
  identificadorDoTopicoPai?: string
  ordem: number
}

export function listarMaterias(
  pesquisa: string,
  incluirArquivadas: boolean,
  pagina: number,
  sinal?: AbortSignal,
  tamanho = 12,
) {
  const parametros = new URLSearchParams({
    pesquisa,
    incluirArquivadas: String(incluirArquivadas),
    pagina: String(pagina),
    tamanho: String(tamanho),
  })
  return requisitar<RespostaPaginada<Materia>>(`/v1/materias?${parametros}`, {
    signal: sinal,
  })
}

export function obterMateria(identificador: string, sinal?: AbortSignal) {
  return requisitar<Materia>(`/v1/materias/${identificador}`, {
    signal: sinal,
  })
}

export function criarMateria(dados: DadosDeMateria) {
  return requisitar<Materia>('/v1/materias', {
    method: 'POST',
    body: JSON.stringify(dados),
  })
}

export function alterarMateria(identificador: string, dados: DadosDeMateria) {
  return requisitar<Materia>(`/v1/materias/${identificador}`, {
    method: 'PUT',
    body: JSON.stringify(dados),
  })
}

export function arquivarMateria(identificador: string, arquivada: boolean) {
  return requisitar<Materia>(`/v1/materias/${identificador}/arquivamento`, {
    method: 'POST',
    body: JSON.stringify({ arquivada }),
  })
}

export function excluirMateria(identificador: string) {
  return requisitar<void>(`/v1/materias/${identificador}`, {
    method: 'DELETE',
  })
}

export function listarTopicos(
  identificadorDaMateria: string,
  incluirArquivados = true,
  sinal?: AbortSignal,
) {
  const parametros = new URLSearchParams({
    incluirArquivados: String(incluirArquivados),
    tamanho: '100',
  })
  return requisitar<RespostaPaginada<Topico>>(
    `/v1/materias/${identificadorDaMateria}/topicos?${parametros}`,
    { signal: sinal },
  )
}

export function criarTopico(
  identificadorDaMateria: string,
  dados: DadosDeTopico,
) {
  return requisitar<Topico>(`/v1/materias/${identificadorDaMateria}/topicos`, {
    method: 'POST',
    body: JSON.stringify(dados),
  })
}

export function alterarTopico(identificador: string, dados: DadosDeTopico) {
  return requisitar<Topico>(`/v1/topicos/${identificador}`, {
    method: 'PUT',
    body: JSON.stringify(dados),
  })
}

export function arquivarTopico(identificador: string, arquivado: boolean) {
  return requisitar<Topico>(`/v1/topicos/${identificador}/arquivamento`, {
    method: 'POST',
    body: JSON.stringify({ arquivada: arquivado }),
  })
}

export function excluirTopico(identificador: string) {
  return requisitar<void>(`/v1/topicos/${identificador}`, {
    method: 'DELETE',
  })
}
