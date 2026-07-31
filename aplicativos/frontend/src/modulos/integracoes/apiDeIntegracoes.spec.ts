import { beforeEach, describe, expect, it, vi } from 'vitest'

const { requisitar } = vi.hoisted(() => ({ requisitar: vi.fn() }))

vi.mock('@/compartilhado/api/clienteHttp', async (importarOriginal) => ({
  ...(await importarOriginal()),
  requisitar,
}))

import { ErroDaApi } from '@/compartilhado/api/clienteHttp'
import {
  cancelarOperacaoAssistida,
  confirmarOperacaoAssistidaPelaWeb,
  criarCodigoDeVinculo,
  listarOperacoesAssistidas,
  obterOperacaoAssistida,
  obterVinculoDoTelegram,
  revogarVinculoDoTelegram,
  rotacionarVinculoDoTelegram,
} from './apiDeIntegracoes'

describe('apiDeIntegracoes', () => {
  beforeEach(() => {
    requisitar.mockReset()
  })

  it('usa os contratos web de vinculo', async () => {
    requisitar.mockResolvedValue({})

    await criarCodigoDeVinculo()
    await obterVinculoDoTelegram()
    await revogarVinculoDoTelegram()
    await rotacionarVinculoDoTelegram()

    expect(requisitar).toHaveBeenNthCalledWith(
      1,
      '/v1/integracoes/telegram/codigos-de-vinculo',
      { method: 'POST' },
    )
    expect(requisitar).toHaveBeenNthCalledWith(
      2,
      '/v1/integracoes/telegram/vinculo',
      { signal: undefined },
    )
    expect(requisitar).toHaveBeenNthCalledWith(
      3,
      '/v1/integracoes/telegram/vinculo',
      { method: 'DELETE' },
    )
    expect(requisitar).toHaveBeenNthCalledWith(
      4,
      '/v1/integracoes/telegram/vinculo/rotacoes',
      { method: 'POST' },
    )
  })

  it('interpreta a ausencia esperada de vinculo como estado vazio', async () => {
    requisitar.mockRejectedValue(
      new ErroDaApi(
        404,
        'Nenhum vínculo encontrado.',
        'VINCULO_DO_TELEGRAM_NAO_ENCONTRADO',
      ),
    )

    await expect(obterVinculoDoTelegram()).resolves.toBeUndefined()
  })

  it('preserva outros erros ao consultar o vinculo', async () => {
    const erro = new ErroDaApi(503, 'Serviço indisponível.')
    requisitar.mockRejectedValue(erro)

    await expect(obterVinculoDoTelegram()).rejects.toBe(erro)
  })

  it('pagina e detalha as operacoes assistidas', async () => {
    requisitar.mockResolvedValue({})
    const sinal = new AbortController().signal

    await listarOperacoesAssistidas(2, 15, sinal)
    await obterOperacaoAssistida('operacao-1', sinal)
    await confirmarOperacaoAssistidaPelaWeb('operacao-1')
    await cancelarOperacaoAssistida('operacao-2')

    expect(requisitar).toHaveBeenNthCalledWith(
      1,
      '/v1/operacoes-assistidas?pagina=2&tamanho=15',
      { signal: sinal },
    )
    expect(requisitar).toHaveBeenNthCalledWith(
      2,
      '/v1/operacoes-assistidas/operacao-1',
      { signal: sinal },
    )
    expect(requisitar).toHaveBeenNthCalledWith(
      3,
      '/v1/operacoes-assistidas/operacao-1/confirmacao-web',
      { method: 'POST' },
    )
    expect(requisitar).toHaveBeenNthCalledWith(
      4,
      '/v1/operacoes-assistidas/operacao-2/cancelamento',
      { method: 'POST' },
    )
  })
})
