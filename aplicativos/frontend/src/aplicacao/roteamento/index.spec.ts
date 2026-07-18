// @vitest-environment jsdom

import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const { requisitar } = vi.hoisted(() => ({ requisitar: vi.fn() }))

vi.mock('@/compartilhado/api/clienteHttp', () => ({ requisitar }))

import { protegerRotas } from './index'

describe('protegerRotas', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    requisitar.mockReset()
  })

  it('restaura uma sessao valida antes de liberar a rota', async () => {
    requisitar.mockResolvedValue({
      autenticada: true,
      usuario: {
        identificador: 'usuario-1',
        nome: 'Pessoa',
        email: 'pessoa@example.com',
      },
    })

    const resultado = await protegerRotas({
      meta: { requerAutenticacao: true },
      fullPath: '/dashboard',
    })

    expect(resultado).toBe(true)
    expect(requisitar).toHaveBeenCalledWith('/v1/autenticacao/sessao')
  })

  it('redireciona para o login quando a sessao expirou', async () => {
    requisitar.mockRejectedValue(new Error('Sessao expirada'))

    const resultado = await protegerRotas({
      meta: { requerAutenticacao: true },
      fullPath: '/dashboard',
    })

    expect(resultado).toEqual({
      name: 'login',
      query: { redirecionar: '/dashboard' },
    })
  })
})
