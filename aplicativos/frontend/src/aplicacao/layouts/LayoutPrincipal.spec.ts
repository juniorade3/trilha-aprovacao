// @vitest-environment jsdom

import { mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { defineComponent, nextTick } from 'vue'
import { describe, expect, it } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import LayoutPrincipal from './LayoutPrincipal.vue'

const RegistroRapidoStub = defineComponent({
  name: 'RegistroRapidoDeEstudo',
  props: {
    identificadorDaMateriaInicial: { type: String, default: undefined },
    identificadorDoTopicoInicial: { type: String, default: undefined },
    tipoDeEstudoInicial: { type: String, default: undefined },
  },
  template: '<div data-testid="registro-rapido" />',
})

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
        stubs: { RegistroRapidoDeEstudo: RegistroRapidoStub },
      },
    })

    expect(layout.get('.navegacao-principal').text()).toContain('Planejamento')
    expect(layout.get('.navegacao-principal').text()).not.toContain('Histórico')
    expect(layout.get('.navegacao-movel').text()).toContain('Planejar')
    layout.unmount()
  })

  it('transporta o contexto da revisao para o registro rapido', async () => {
    const roteador = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/', component: { template: '<div />' } }],
    })
    await roteador.push('/')
    await roteador.isReady()
    const layout = mount(LayoutPrincipal, {
      global: {
        plugins: [createPinia(), roteador],
        stubs: { RegistroRapidoDeEstudo: RegistroRapidoStub },
      },
    })

    window.dispatchEvent(
      new CustomEvent('abrir-registro-rapido', {
        detail: {
          identificadorDaMateria: 'materia-1',
          identificadorDoTopico: 'topico-1',
          tipoDeEstudo: 'REVISAO',
        },
      }),
    )
    await nextTick()

    const registro = layout.getComponent(RegistroRapidoStub)
    expect(registro.props('identificadorDaMateriaInicial')).toBe('materia-1')
    expect(registro.props('identificadorDoTopicoInicial')).toBe('topico-1')
    expect(registro.props('tipoDeEstudoInicial')).toBe('REVISAO')
    layout.unmount()
  })
})
