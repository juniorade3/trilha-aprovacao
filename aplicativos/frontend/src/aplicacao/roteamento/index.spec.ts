// @vitest-environment jsdom

import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const { requisitar } = vi.hoisted(() => ({ requisitar: vi.fn() }))

vi.mock('@/compartilhado/api/clienteHttp', () => ({ requisitar }))

import roteador, { protegerRotas } from './index'

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

  it('oferece todas as rotas publicas e autenticadas previstas', () => {
    expect(roteador.resolve('/login').matched).not.toHaveLength(0)
    expect(roteador.resolve('/cadastro').matched).not.toHaveLength(0)
    expect(roteador.resolve('/dashboard').matched).not.toHaveLength(0)
    expect(roteador.resolve('/concursos/novo').matched).not.toHaveLength(0)
    expect(roteador.resolve('/concursos/concurso-1').matched).not.toHaveLength(
      0,
    )
    expect(roteador.resolve('/materias/materia-1').matched).not.toHaveLength(0)
    expect(roteador.resolve('/materiais/material-1').matched).not.toHaveLength(
      0,
    )
    expect(
      roteador.getRoutes().find((rota) => rota.path === '/planejamento')
        ?.redirect,
    ).toBe('/planejamento/hoje')
    expect(roteador.resolve('/planejamento/hoje').matched).not.toHaveLength(0)
    expect(roteador.resolve('/planejamento/semana').matched).not.toHaveLength(0)
    expect(
      roteador.resolve('/planejamento/prioridades').matched,
    ).not.toHaveLength(0)
    expect(roteador.resolve('/integracoes/telegram').matched).not.toHaveLength(
      0,
    )
    const rotaDeNovoEstudo = roteador.resolve('/estudos/novo')
    expect(rotaDeNovoEstudo.matched).not.toHaveLength(0)
    expect(
      rotaDeNovoEstudo.matched[rotaDeNovoEstudo.matched.length - 1]?.props
        .default,
    ).toEqual({ abrirRegistroRapidoAoEntrar: true })
  })
})
