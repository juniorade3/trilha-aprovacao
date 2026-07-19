import { requisitar } from '@/compartilhado/api/clienteHttp'
import { listarTodasAsMaterias } from '@/modulos/materias/apiDeConteudos'

import type {
  Materia,
  RespostaPaginada,
} from '@/modulos/materias/apiDeConteudos'

export type SituacaoDoConcurso =
  | 'PLANEJADO'
  | 'EDITAL_PUBLICADO'
  | 'INSCRICOES_ABERTAS'
  | 'EM_ANDAMENTO'
  | 'ENCERRADO'
  | 'SUSPENSO'
  | 'CANCELADO'
  | 'ARQUIVADO'

export type NivelDeEscolaridade =
  'FUNDAMENTAL' | 'MEDIO' | 'TECNICO' | 'SUPERIOR' | 'NAO_INFORMADO'

export type TipoDeProva =
  'OBJETIVA' | 'DISCURSIVA' | 'PRATICA' | 'TITULOS' | 'OUTRA'

export type CaraterDaProva =
  | 'ELIMINATORIO'
  | 'CLASSIFICATORIO'
  | 'ELIMINATORIO_E_CLASSIFICATORIO'
  | 'NAO_INFORMADO'

export type Concurso = {
  identificador: string
  nome: string
  descricao?: string
  orgao?: string
  banca?: string
  situacao: SituacaoDoConcurso
  dataPrevistaPrincipal?: string
  ativo: boolean
  criadoEm: string
  atualizadoEm: string
  versao: number
}

export type Edital = {
  identificador: string
  identificadorDoConcurso: string
  titulo: string
  numero?: string
  ano?: number
  descricao?: string
  dataDePublicacao?: string
  enderecoDoDocumento?: string
  principal: boolean
  criadoEm: string
  atualizadoEm: string
  versao: number
}

export type Cargo = {
  identificador: string
  identificadorDoConcurso: string
  nome: string
  area?: string
  especialidade?: string
  nivelDeEscolaridade: NivelDeEscolaridade
  selecionado: boolean
  ordem: number
  criadoEm: string
  atualizadoEm: string
  versao: number
}

export type Prova = {
  identificador: string
  identificadorDoCargo: string
  nome: string
  tipo: TipoDeProva
  carater: CaraterDaProva
  ordem: number
  dataHoraPrevista?: string
  duracaoEmMinutos?: number
  quantidadeDeQuestoes?: number
  pontuacaoMaxima?: number
  pontuacaoMinima?: number
  criadoEm: string
  atualizadoEm: string
  versao: number
}

export type Grupo = {
  identificador: string
  identificadorDaProva: string
  nome: string
  ordem: number
  quantidadeDeQuestoes?: number
  pontuacaoMaxima?: number
  pontuacaoMinima?: number
  criadoEm: string
  atualizadoEm: string
  versao: number
}

export type MateriaDaProva = {
  identificador: string
  identificadorDoGrupoDeConteudo: string
  identificadorDaMateria: string
  nomeDaMateria: string
  ordem: number
  peso?: number
  quantidadeDeQuestoes?: number
  pontuacaoMaxima?: number
  criadoEm: string
  atualizadoEm: string
  versao: number
}

export type DadosDeConcurso = {
  nome: string
  descricao?: string
  orgao?: string
  banca?: string
  situacao: SituacaoDoConcurso
  dataPrevistaPrincipal?: string
}

export type DadosDeEdital = {
  titulo: string
  numero?: string
  ano?: number
  descricao?: string
  dataDePublicacao?: string
  enderecoDoDocumento?: string
}

export type DadosDeCargo = {
  nome: string
  area?: string
  especialidade?: string
  nivelDeEscolaridade: NivelDeEscolaridade
  ordem: number
}

export type DadosDeProva = {
  nome: string
  tipo: TipoDeProva
  carater: CaraterDaProva
  ordem: number
  dataHoraPrevista?: string
  duracaoEmMinutos?: number
  quantidadeDeQuestoes?: number
  pontuacaoMaxima?: number
  pontuacaoMinima?: number
}

export type DadosDeGrupo = {
  nome: string
  ordem: number
  quantidadeDeQuestoes?: number
  pontuacaoMaxima?: number
  pontuacaoMinima?: number
}

export type DadosDeMateriaDaProva = {
  identificadorDaMateria: string
  ordem: number
  peso?: number
  quantidadeDeQuestoes?: number
  pontuacaoMaxima?: number
}

export type AlteracaoDeMateriaDaProva = Omit<
  DadosDeMateriaDaProva,
  'identificadorDaMateria'
>

export function listarConcursos(
  pesquisa: string,
  incluirArquivados: boolean,
  pagina: number,
  sinal?: AbortSignal,
) {
  const parametros = new URLSearchParams({
    pesquisa,
    incluirArquivados: String(incluirArquivados),
    pagina: String(pagina),
    tamanho: '12',
  })
  return requisitar<RespostaPaginada<Concurso>>(`/v1/concursos?${parametros}`, {
    signal: sinal,
  })
}

export const obterConcurso = (identificador: string, sinal?: AbortSignal) =>
  requisitar<Concurso>(`/v1/concursos/${identificador}`, { signal: sinal })

export const criarConcurso = (dados: DadosDeConcurso) =>
  requisitar<Concurso>('/v1/concursos', {
    method: 'POST',
    body: JSON.stringify(dados),
  })

export const alterarConcurso = (
  identificador: string,
  dados: DadosDeConcurso,
) =>
  requisitar<Concurso>(`/v1/concursos/${identificador}`, {
    method: 'PUT',
    body: JSON.stringify(dados),
  })

export const ativarConcurso = (identificador: string) =>
  requisitar<Concurso>(`/v1/concursos/${identificador}/ativacao`, {
    method: 'POST',
  })

export const arquivarConcurso = (identificador: string, arquivado: boolean) =>
  requisitar<Concurso>(`/v1/concursos/${identificador}/arquivamento`, {
    method: 'POST',
    body: JSON.stringify({ arquivado }),
  })

export const excluirConcurso = (identificador: string) =>
  requisitar<void>(`/v1/concursos/${identificador}`, { method: 'DELETE' })

export const listarEditais = (concurso: string, sinal?: AbortSignal) =>
  requisitar<Edital[]>(`/v1/concursos/${concurso}/editais`, { signal: sinal })

export const criarEdital = (concurso: string, dados: DadosDeEdital) =>
  requisitar<Edital>(`/v1/concursos/${concurso}/editais`, {
    method: 'POST',
    body: JSON.stringify(dados),
  })

export const alterarEdital = (identificador: string, dados: DadosDeEdital) =>
  requisitar<Edital>(`/v1/editais/${identificador}`, {
    method: 'PUT',
    body: JSON.stringify(dados),
  })

export const definirEditalPrincipal = (identificador: string) =>
  requisitar<Edital>(`/v1/editais/${identificador}/definicao-como-principal`, {
    method: 'POST',
  })

export const excluirEdital = (identificador: string) =>
  requisitar<void>(`/v1/editais/${identificador}`, { method: 'DELETE' })

export const listarCargos = (concurso: string, sinal?: AbortSignal) =>
  requisitar<Cargo[]>(`/v1/concursos/${concurso}/cargos`, { signal: sinal })

export const criarCargo = (concurso: string, dados: DadosDeCargo) =>
  requisitar<Cargo>(`/v1/concursos/${concurso}/cargos`, {
    method: 'POST',
    body: JSON.stringify(dados),
  })

export const alterarCargo = (identificador: string, dados: DadosDeCargo) =>
  requisitar<Cargo>(`/v1/cargos/${identificador}`, {
    method: 'PUT',
    body: JSON.stringify(dados),
  })

export const selecionarCargo = (identificador: string) =>
  requisitar<Cargo>(`/v1/cargos/${identificador}/selecao`, { method: 'POST' })

export const excluirCargo = (identificador: string) =>
  requisitar<void>(`/v1/cargos/${identificador}`, { method: 'DELETE' })

export const listarProvas = (cargo: string, sinal?: AbortSignal) =>
  requisitar<Prova[]>(`/v1/cargos/${cargo}/provas`, { signal: sinal })

export const criarProva = (cargo: string, dados: DadosDeProva) =>
  requisitar<Prova>(`/v1/cargos/${cargo}/provas`, {
    method: 'POST',
    body: JSON.stringify(dados),
  })

export const alterarProva = (identificador: string, dados: DadosDeProva) =>
  requisitar<Prova>(`/v1/provas/${identificador}`, {
    method: 'PUT',
    body: JSON.stringify(dados),
  })

export const excluirProva = (identificador: string) =>
  requisitar<void>(`/v1/provas/${identificador}`, { method: 'DELETE' })

export const listarGrupos = (prova: string, sinal?: AbortSignal) =>
  requisitar<Grupo[]>(`/v1/provas/${prova}/grupos`, { signal: sinal })

export const criarGrupo = (prova: string, dados: DadosDeGrupo) =>
  requisitar<Grupo>(`/v1/provas/${prova}/grupos`, {
    method: 'POST',
    body: JSON.stringify(dados),
  })

export const alterarGrupo = (identificador: string, dados: DadosDeGrupo) =>
  requisitar<Grupo>(`/v1/grupos-de-conteudo/${identificador}`, {
    method: 'PUT',
    body: JSON.stringify(dados),
  })

export const excluirGrupo = (identificador: string) =>
  requisitar<void>(`/v1/grupos-de-conteudo/${identificador}`, {
    method: 'DELETE',
  })

export const listarMateriasDaProva = (grupo: string, sinal?: AbortSignal) =>
  requisitar<MateriaDaProva[]>(`/v1/grupos-de-conteudo/${grupo}/materias`, {
    signal: sinal,
  })

export const criarMateriaDaProva = (
  grupo: string,
  dados: DadosDeMateriaDaProva,
) =>
  requisitar<MateriaDaProva>(`/v1/grupos-de-conteudo/${grupo}/materias`, {
    method: 'POST',
    body: JSON.stringify(dados),
  })

export const alterarMateriaDaProva = (
  identificador: string,
  dados: AlteracaoDeMateriaDaProva,
) =>
  requisitar<MateriaDaProva>(`/v1/materias-da-prova/${identificador}`, {
    method: 'PUT',
    body: JSON.stringify(dados),
  })

export const excluirMateriaDaProva = (identificador: string) =>
  requisitar<void>(`/v1/materias-da-prova/${identificador}`, {
    method: 'DELETE',
  })

export async function listarMateriasDisponiveis(
  sinal?: AbortSignal,
): Promise<RespostaPaginada<Materia>> {
  const itens = await listarTodasAsMaterias('', false, sinal)
  return {
    itens,
    pagina: 0,
    tamanho: itens.length,
    totalDeItens: itens.length,
    totalDePaginas: itens.length ? 1 : 0,
  }
}
