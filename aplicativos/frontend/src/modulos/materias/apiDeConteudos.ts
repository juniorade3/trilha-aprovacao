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

export type UsoDaMateria = {
  materiais: {
    identificador: string
    titulo: string
    tipo: string
  }[]
  estudosRecentes: {
    identificador: string
    nomeDoTopico: string
    dataHora: string
    duracaoEmMinutos: number
  }[]
  concursos: {
    identificador: string
    nome: string
    ativo: boolean
  }[]
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

export async function listarTodasAsMaterias(
  pesquisa = '',
  incluirArquivadas = false,
  sinal?: AbortSignal,
) {
  const primeiraPagina = await listarMaterias(
    pesquisa,
    incluirArquivadas,
    0,
    sinal,
    100,
  )
  if (primeiraPagina.totalDePaginas <= 1) return primeiraPagina.itens
  const demaisPaginas = await Promise.all(
    Array.from({ length: primeiraPagina.totalDePaginas - 1 }, (_, indice) =>
      listarMaterias(pesquisa, incluirArquivadas, indice + 1, sinal, 100),
    ),
  )
  return [
    ...primeiraPagina.itens,
    ...demaisPaginas.flatMap((pagina) => pagina.itens),
  ]
}

export function obterMateria(identificador: string, sinal?: AbortSignal) {
  return requisitar<Materia>(`/v1/materias/${identificador}`, {
    signal: sinal,
  })
}

export function consultarUsoDaMateria(
  identificador: string,
  sinal?: AbortSignal,
) {
  return requisitar<UsoDaMateria>(`/v1/materias/${identificador}/uso`, {
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
  pagina = 0,
  tamanho = 100,
) {
  const parametros = new URLSearchParams({
    incluirArquivados: String(incluirArquivados),
    pagina: String(pagina),
    tamanho: String(tamanho),
  })
  return requisitar<RespostaPaginada<Topico>>(
    `/v1/materias/${identificadorDaMateria}/topicos?${parametros}`,
    { signal: sinal },
  )
}

export async function listarTodosOsTopicos(
  identificadorDaMateria: string,
  incluirArquivados = true,
  sinal?: AbortSignal,
) {
  const primeiraPagina = await listarTopicos(
    identificadorDaMateria,
    incluirArquivados,
    sinal,
    0,
    100,
  )
  if (primeiraPagina.totalDePaginas <= 1) return primeiraPagina.itens
  const demaisPaginas = await Promise.all(
    Array.from({ length: primeiraPagina.totalDePaginas - 1 }, (_, indice) =>
      listarTopicos(
        identificadorDaMateria,
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
