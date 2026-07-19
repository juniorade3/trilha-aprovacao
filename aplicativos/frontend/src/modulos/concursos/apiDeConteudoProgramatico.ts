import { requisitar } from '@/compartilhado/api/clienteHttp'
import { listarTodosOsTopicos } from '@/modulos/materias/apiDeConteudos'

import type {
  RespostaPaginada,
  Topico,
} from '@/modulos/materias/apiDeConteudos'

export type ItemDoEdital = {
  identificador: string
  identificadorDoEdital: string
  identificadorDaMateriaDaProva: string
  descricaoOriginal: string
  identificadorDoItemPai?: string
  ordem: number
  criadoEm: string
  atualizadoEm: string
  versao: number
}

export type MapeamentoDeItem = {
  identificador: string
  identificadorDoItemDoEdital: string
  identificadorDoTopicoDaMateria: string
  nomeDoTopico: string
  confirmado: boolean
  criadoEm: string
}

export type DadosDeItem = {
  identificadorDoEdital: string
  descricaoOriginal: string
  identificadorDoItemPai?: string
  ordem: number
}

export type AlteracaoDeItem = Omit<DadosDeItem, 'identificadorDoEdital'>

export const listarItensDoEdital = (
  materiaDaProva: string,
  sinal?: AbortSignal,
) =>
  requisitar<ItemDoEdital[]>(`/v1/materias-da-prova/${materiaDaProva}/itens`, {
    signal: sinal,
  })

export const criarItemDoEdital = (materiaDaProva: string, dados: DadosDeItem) =>
  requisitar<ItemDoEdital>(`/v1/materias-da-prova/${materiaDaProva}/itens`, {
    method: 'POST',
    body: JSON.stringify(dados),
  })

export const alterarItemDoEdital = (
  identificador: string,
  dados: AlteracaoDeItem,
) =>
  requisitar<ItemDoEdital>(`/v1/itens-do-edital/${identificador}`, {
    method: 'PUT',
    body: JSON.stringify(dados),
  })

export const excluirItemDoEdital = (identificador: string) =>
  requisitar<void>(`/v1/itens-do-edital/${identificador}`, {
    method: 'DELETE',
  })

export const listarMapeamentosDoItem = (item: string, sinal?: AbortSignal) =>
  requisitar<MapeamentoDeItem[]>(`/v1/itens-do-edital/${item}/mapeamentos`, {
    signal: sinal,
  })

export const criarMapeamentoDoItem = (item: string, topico: string) =>
  requisitar<MapeamentoDeItem>(`/v1/itens-do-edital/${item}/mapeamentos`, {
    method: 'POST',
    body: JSON.stringify({ identificadorDoTopicoDaMateria: topico }),
  })

export const excluirMapeamentoDoItem = (item: string, topico: string) =>
  requisitar<void>(`/v1/itens-do-edital/${item}/mapeamentos/${topico}`, {
    method: 'DELETE',
  })

export async function listarTopicosDisponiveis(
  materia: string,
  sinal?: AbortSignal,
): Promise<RespostaPaginada<Topico>> {
  const itens = await listarTodosOsTopicos(materia, false, sinal)
  return {
    itens,
    pagina: 0,
    tamanho: itens.length,
    totalDeItens: itens.length,
    totalDePaginas: itens.length ? 1 : 0,
  }
}
