// @vitest-environment jsdom

import { beforeEach, describe, expect, it, vi } from 'vitest'

import { criarRequisicaoCancelavel, ErroDaApi, requisitar } from './clienteHttp'

describe('clienteHttp', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  it('envia credenciais e CSRF em requisicoes mutaveis', async () => {
    const buscar = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(
        new Response(JSON.stringify({ token: 'csrf-seguro' }), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        }),
      )
      .mockResolvedValueOnce(
        new Response(JSON.stringify({ autenticada: true }), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        }),
      )

    await requisitar('/v1/autenticacao/login', {
      method: 'POST',
      body: JSON.stringify({
        email: 'pessoa@example.com',
        senha: 'senha-segura',
      }),
    })

    expect(buscar).toHaveBeenCalledTimes(2)
    const opcoes = buscar.mock.calls[1]?.[1]
    expect(opcoes?.credentials).toBe('include')
    expect(new Headers(opcoes?.headers).get('X-XSRF-TOKEN')).toBe('csrf-seguro')
  })

  it('avisa a aplicacao quando uma sessao autenticada expira', async () => {
    const aoExpirar = vi.fn()
    window.addEventListener('sessao-expirada', aoExpirar, { once: true })
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(
      new Response(JSON.stringify({ mensagem: 'Faca login para continuar.' }), {
        status: 401,
        headers: { 'Content-Type': 'application/json' },
      }),
    )

    await expect(requisitar('/v1/autenticacao/sessao')).rejects.toEqual(
      new ErroDaApi(401, 'Faca login para continuar.'),
    )
    expect(aoExpirar).toHaveBeenCalledOnce()
  })

  it('permite cancelar uma requisicao em andamento', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(
      (_entrada, opcoes) =>
        new Promise((_resolver, rejeitar) => {
          opcoes?.signal?.addEventListener('abort', () =>
            rejeitar(new DOMException('Cancelada', 'AbortError')),
          )
        }),
    )

    const requisicao = criarRequisicaoCancelavel('/v1/dashboard')
    requisicao.cancelar()

    await expect(requisicao.promessa).rejects.toMatchObject({
      name: 'AbortError',
    })
  })
})
