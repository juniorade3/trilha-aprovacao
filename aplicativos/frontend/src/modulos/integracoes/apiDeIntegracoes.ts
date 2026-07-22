import { ErroDaApi, requisitar } from '@/compartilhado/api/clienteHttp'

export type EstadoDoVinculo = 'PENDENTE' | 'ATIVO' | 'REVOGADO' | 'EXPIRADO'

export type EstadoDaOperacaoAssistida =
  | 'PREPARADA'
  | 'AGUARDANDO_CONFIRMACAO'
  | 'CONFIRMADA'
  | 'APLICADA'
  | 'CANCELADA'
  | 'EXPIRADA'
  | 'FALHOU'

export type VinculoDoTelegram = {
  identificador: string
  canal: 'TELEGRAM'
  estado: EstadoDoVinculo
  identificadorDoBot: number
  identificadorExterno?: number | null
  identificadorDoChat?: number | null
  vinculadoEm?: string | null
  criadoEm: string
  atualizadoEm: string
  revogadoEm?: string | null
}

export type CodigoDeVinculo = {
  codigo: string
  expiraEm: string
  vinculo: VinculoDoTelegram
}

export type ResumoDaOperacaoAssistida = {
  identificador: string
  tipo: string
  estado: EstadoDaOperacaoAssistida
  resumo: string
  expiraEm: string
  criadoEm: string
  atualizadoEm: string
}

export type RespostaPaginadaDeOperacoes = {
  itens: ResumoDaOperacaoAssistida[]
  pagina: number
  tamanho: number
  totalDeItens: number
  totalDePaginas: number
}

export type DetalheDaOperacaoAssistida = ResumoDaOperacaoAssistida & {
  proposta: unknown
  assinatura: string
  versoesConsultadas: unknown
  confirmadaEm?: string | null
  aplicadaEm?: string | null
  canceladaEm?: string | null
  falha?: string | null
  resultado?: unknown
}

export const criarCodigoDeVinculo = () =>
  requisitar<CodigoDeVinculo>('/v1/integracoes/telegram/codigos-de-vinculo', {
    method: 'POST',
  })

export async function obterVinculoDoTelegram(sinal?: AbortSignal) {
  try {
    return await requisitar<VinculoDoTelegram>(
      '/v1/integracoes/telegram/vinculo',
      { signal: sinal },
    )
  } catch (causa) {
    if (
      causa instanceof ErroDaApi &&
      causa.status === 404 &&
      causa.codigo === 'VINCULO_DO_TELEGRAM_NAO_ENCONTRADO'
    )
      return undefined
    throw causa
  }
}

export const revogarVinculoDoTelegram = () =>
  requisitar<void>('/v1/integracoes/telegram/vinculo', { method: 'DELETE' })

export const rotacionarVinculoDoTelegram = () =>
  requisitar<CodigoDeVinculo>('/v1/integracoes/telegram/vinculo/rotacoes', {
    method: 'POST',
  })

export function listarOperacoesAssistidas(
  pagina: number,
  tamanho = 20,
  sinal?: AbortSignal,
) {
  const parametros = new URLSearchParams({
    pagina: String(pagina),
    tamanho: String(tamanho),
  })
  return requisitar<RespostaPaginadaDeOperacoes>(
    `/v1/operacoes-assistidas?${parametros}`,
    { signal: sinal },
  )
}

export const obterOperacaoAssistida = (
  identificador: string,
  sinal?: AbortSignal,
) =>
  requisitar<DetalheDaOperacaoAssistida>(
    `/v1/operacoes-assistidas/${identificador}`,
    { signal: sinal },
  )
