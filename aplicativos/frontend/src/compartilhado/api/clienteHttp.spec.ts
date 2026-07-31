// @vitest-environment jsdom

import { beforeEach, describe, expect, it, vi } from 'vitest'

import { criarRequisicaoCancelavel, ErroDaApi, requisitar } from './clienteHttp'

describe('clienteHttp', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    vi.unstubAllGlobals()
  })

  it('renova o CSRF depois que a sessão expira', async () => {
    const buscar = vi
      .fn()
      .mockResolvedValueOnce(
        new Response(JSON.stringify({ token: 'csrf-antigo' }), { status: 200 }),
      )
      .mockResolvedValueOnce(
        new Response(JSON.stringify({ mensagem: 'Sessão expirada' }), {
          status: 401,
        }),
      )
      .mockResolvedValueOnce(
        new Response(JSON.stringify({ token: 'csrf-novo' }), { status: 200 }),
      )
      .mockResolvedValueOnce(
        new Response(JSON.stringify({ identificador: 'estudo-1' }), {
          status: 201,
        }),
      )
      .mockResolvedValueOnce(
        new Response(JSON.stringify({ mensagem: 'Sessão expirada' }), {
          status: 401,
        }),
      )
    vi.stubGlobal('fetch', buscar)
    await expect(
      requisitar('/v1/estudos', { method: 'POST', body: '{}' }),
    ).rejects.toMatchObject({ status: 401 })

    await requisitar('/v1/estudos', { method: 'POST', body: '{}' })

    expect(buscar).toHaveBeenNthCalledWith(
      3,
      '/api/v1/autenticacao/csrf',
      expect.objectContaining({ credentials: 'include' }),
    )
    expect(
      new Headers(buscar.mock.calls[3]?.[1]?.headers).get('X-XSRF-TOKEN'),
    ).toBe('csrf-novo')
    await expect(requisitar('/v1/autenticacao/sessao')).rejects.toMatchObject({
      status: 401,
    })
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

  it('deixa o navegador definir o boundary de formularios multipart', async () => {
    const buscar = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(
      new Response(JSON.stringify({ identificador: 'importacao-1' }), {
        status: 202,
        headers: { 'Content-Type': 'application/json' },
      }),
    )
    const formulario = new FormData()
    formulario.append('arquivo', new Blob(['edital']), 'edital.pdf')

    await requisitar('/v1/importacoes-de-edital', {
      method: 'POST',
      body: formulario,
    })

    const opcoes = buscar.mock.calls[0]?.[1]
    expect(opcoes?.body).toBe(formulario)
    expect(new Headers(opcoes?.headers).has('Content-Type')).toBe(false)
    expect(new Headers(opcoes?.headers).get('X-XSRF-TOKEN')).toBe('csrf-seguro')
    expect(opcoes?.credentials).toBe('include')
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

  it('usa a mensagem padronizada quando a resposta de erro nao possui JSON', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(
      new Response(null, { status: 503 }),
    )

    await expect(requisitar('/v1/planejamento/semanas')).rejects.toEqual(
      new ErroDaApi(503, 'Nao foi possivel concluir a operacao.'),
    )
  })

  it('propaga erro de rede para o tratamento recuperavel da operacao', async () => {
    const erroDeRede = new TypeError('Falha de rede.')
    vi.spyOn(globalThis, 'fetch').mockRejectedValueOnce(erroDeRede)

    await expect(requisitar('/v1/planejamento/semanas')).rejects.toBe(
      erroDeRede,
    )
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
