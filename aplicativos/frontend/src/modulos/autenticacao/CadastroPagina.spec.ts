// @vitest-environment jsdom

import { mount, flushPromises } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const { requisitar } = vi.hoisted(() => ({ requisitar: vi.fn() }))

vi.mock('@/compartilhado/api/clienteHttp', () => ({ requisitar }))

import CadastroPagina from './CadastroPagina.vue'

function criarRoteador() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/cadastro', component: CadastroPagina },
      { path: '/login', component: { template: '<div />' } },
    ],
  })
}

describe('CadastroPagina', () => {
  beforeEach(() => {
    requisitar.mockReset()
  })

  it('nao envia senhas com confirmacao diferente', async () => {
    const roteador = criarRoteador()
    await roteador.push('/cadastro')
    await roteador.isReady()
    const pagina = mount(CadastroPagina, {
      global: { plugins: [roteador] },
    })

    await pagina.get('#senha').setValue('senha-segura')
    await pagina.get('#confirmacao').setValue('outra-senha')
    await pagina.get('form').trigger('submit')

    expect(pagina.text()).toContain('As senhas nao conferem.')
    expect(requisitar).not.toHaveBeenCalled()
  })

  it('cadastra a conta e encaminha para o login', async () => {
    requisitar.mockResolvedValue({ identificador: 'usuario-1' })
    const roteador = criarRoteador()
    await roteador.push('/cadastro')
    await roteador.isReady()
    const pagina = mount(CadastroPagina, {
      global: { plugins: [roteador] },
    })

    await pagina.get('#nome').setValue('Pessoa')
    await pagina.get('#email').setValue('pessoa@example.com')
    await pagina.get('#senha').setValue('senha-segura')
    await pagina.get('#confirmacao').setValue('senha-segura')
    await pagina.get('form').trigger('submit')
    await flushPromises()

    expect(requisitar).toHaveBeenCalledWith(
      '/v1/autenticacao/cadastro',
      expect.objectContaining({ method: 'POST' }),
    )
    expect(roteador.currentRoute.value.path).toBe('/login')
  })
})
