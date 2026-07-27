// @vitest-environment jsdom

import { mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { defineComponent, nextTick } from 'vue'
import { describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

vi.mock('@/aplicacao/configuracao/funcionalidades', () => ({
  assistenteTelegramEstaHabilitado: () => true,
}))

import LayoutPrincipal from './LayoutPrincipal.vue'

const RegistroRapidoStub = defineComponent({
  name: 'RegistroRapidoDeEstudo',
  props: {
    identificadorDaMateriaInicial: { type: String, default: undefined },
    identificadorDoTopicoInicial: { type: String, default: undefined },
    tipoDeEstudoInicial: { type: String, default: undefined },
  },
  emits: ['fechar', 'registrado'],
  template: '<div data-testid="registro-rapido" />',
})

describe('LayoutPrincipal', () => {
  it('prioriza hoje e historico no menu principal e oferece cinco destinos moveis', async () => {
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
    expect(layout.get('.navegacao-principal').text()).toContain('Hoje')
    expect(layout.get('.navegacao-principal').text()).toContain('Histórico')
    expect(layout.get('.navegacao-movel').text()).toContain('Planejar')
    expect(layout.get('.navegacao-movel').text()).toContain('Hoje')
    expect(layout.get('.navegacao-movel').text()).toContain('Mais')
    expect(layout.findAll('.navegacao-movel > *')).toHaveLength(5)
    expect(
      layout
        .get('a[aria-label="Integração com o Telegram"]')
        .attributes('href'),
    ).toBe('/integracoes/telegram')
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

  it('publica a nova evidência para os consumidores após o registro', async () => {
    vi.useFakeTimers()
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
    const aoRegistrar = vi.fn()
    window.addEventListener('estudo-registrado', aoRegistrar, { once: true })

    await layout.get('.acao-global-de-estudo').trigger('click')
    layout.getComponent(RegistroRapidoStub).vm.$emit('registrado')
    await nextTick()

    expect(aoRegistrar).toHaveBeenCalledTimes(1)
    expect(layout.findComponent(RegistroRapidoStub).exists()).toBe(false)
    expect(layout.text()).toContain('Seu progresso foi atualizado')
    layout.unmount()
    vi.useRealTimers()
  })
})
