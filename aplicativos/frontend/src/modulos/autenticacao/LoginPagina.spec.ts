// @vitest-environment jsdom

import { mount, flushPromises } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { defineComponent } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const { requisitar } = vi.hoisted(() => ({ requisitar: vi.fn() }))

vi.mock('@/compartilhado/api/clienteHttp', () => ({ requisitar }))

import LoginPagina from './LoginPagina.vue'

const paginaVazia = defineComponent({ template: '<div />' })

function criarRoteador() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/login', name: 'login', component: LoginPagina },
      { path: '/dashboard', name: 'dashboard', component: paginaVazia },
    ],
  })
}

describe('LoginPagina', () => {
  beforeEach(() => {
    requisitar.mockReset()
  })

  it('permite mostrar e ocultar a senha', async () => {
    const roteador = criarRoteador()
    await roteador.push('/login')
    await roteador.isReady()
    const pagina = mount(LoginPagina, {
      global: { plugins: [createPinia(), roteador] },
    })
    const senha = pagina.get<HTMLInputElement>('#senha')

    expect(senha.attributes('type')).toBe('password')
    await pagina.get('button[aria-label="Mostrar senha"]').trigger('click')
    expect(senha.attributes('type')).toBe('text')
  })

  it('explica quando a sessao expirou sem perder o redirecionamento', async () => {
    const roteador = criarRoteador()
    await roteador.push(
      '/login?sessao=expirada&redirecionar=/materiais/material-1',
    )
    await roteador.isReady()
    const pagina = mount(LoginPagina, {
      global: { plugins: [createPinia(), roteador] },
    })

    expect(pagina.text()).toContain('Sua sessão expirou')
    expect(roteador.currentRoute.value.query.redirecionar).toBe(
      '/materiais/material-1',
    )
  })

  it('autentica, restaura a sessao e respeita o redirecionamento', async () => {
    requisitar
      .mockResolvedValueOnce({ autenticada: true })
      .mockResolvedValueOnce({
        autenticada: true,
        usuario: {
          identificador: 'usuario-1',
          nome: 'Pessoa',
          email: 'pessoa@example.com',
        },
      })
    const roteador = criarRoteador()
    await roteador.push('/login?redirecionar=/dashboard')
    await roteador.isReady()
    const pagina = mount(LoginPagina, {
      global: { plugins: [createPinia(), roteador] },
    })

    await pagina.get('#email').setValue('pessoa@example.com')
    await pagina.get('#senha').setValue('senha-segura')
    await pagina.get('form').trigger('submit')
    await flushPromises()

    expect(requisitar).toHaveBeenNthCalledWith(
      1,
      '/v1/autenticacao/login',
      expect.objectContaining({ method: 'POST' }),
    )
    expect(requisitar).toHaveBeenNthCalledWith(2, '/v1/autenticacao/sessao')
    expect(roteador.currentRoute.value.fullPath).toBe('/dashboard')
  })
})
