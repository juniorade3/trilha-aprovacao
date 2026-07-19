// @vitest-environment jsdom

import { mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { describe, expect, it } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import LayoutPrincipal from './LayoutPrincipal.vue'

describe('LayoutPrincipal', () => {
  it('oferece planejamento no menu principal sem duplicar historico', async () => {
    const roteador = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/', component: { template: '<div />' } }],
    })
    await roteador.push('/')
    await roteador.isReady()
    const layout = mount(LayoutPrincipal, {
      global: {
        plugins: [createPinia(), roteador],
        stubs: { RegistroRapidoDeEstudo: true },
      },
    })

    expect(layout.get('.navegacao-principal').text()).toContain('Planejamento')
    expect(layout.get('.navegacao-principal').text()).not.toContain('Histórico')
    expect(layout.get('.navegacao-movel').text()).toContain('Planejar')
  })
})
