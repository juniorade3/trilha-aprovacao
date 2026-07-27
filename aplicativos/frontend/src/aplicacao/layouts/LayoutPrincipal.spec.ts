// @vitest-environment jsdom

import { flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { defineComponent, nextTick } from 'vue'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

const funcionalidades = vi.hoisted(() => ({
  assistenteTelegramEstaHabilitado: vi.fn(() => true),
}))

vi.mock('@/aplicacao/configuracao/funcionalidades', () => ({
  assistenteTelegramEstaHabilitado:
    funcionalidades.assistenteTelegramEstaHabilitado,
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

afterEach(() => {
  funcionalidades.assistenteTelegramEstaHabilitado.mockReturnValue(true)
  document.body.classList.remove('modal-aberto')
  document.body.innerHTML = ''
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

  it('abre o menu movel como dialogo, contem o foco e restaura o acionador', async () => {
    const roteador = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/', component: { template: '<div />' } }],
    })
    await roteador.push('/')
    await roteador.isReady()
    const layout = mount(LayoutPrincipal, {
      attachTo: document.body,
      global: {
        plugins: [createPinia(), roteador],
        stubs: { RegistroRapidoDeEstudo: RegistroRapidoStub },
      },
    })
    const acionador = layout.get('.navegacao-movel button')
    const elementoAcionador = acionador.element

    expect(document.body.classList.contains('modal-aberto')).toBe(false)
    await acionador.trigger('click')
    await flushPromises()

    const dialogo = layout.get('[role="dialog"]')
    expect(dialogo.attributes('aria-modal')).toBe('true')
    expect(dialogo.attributes('aria-labelledby')).toBe('titulo-do-menu-movel')
    expect(acionador.attributes('aria-expanded')).toBe('true')
    expect(document.body.classList.contains('modal-aberto')).toBe(true)
    expect(document.activeElement?.getAttribute('aria-label')).toBe(
      'Fechar menu',
    )

    const ultimoElemento = layout.get('.menu-movel-mais footer button').element
    if (ultimoElemento instanceof HTMLElement) ultimoElemento.focus()
    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Tab' }))
    expect(document.activeElement?.getAttribute('aria-label')).toBe(
      'Fechar menu',
    )

    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
    await flushPromises()

    expect(layout.find('[role="dialog"]').exists()).toBe(false)
    expect(document.body.classList.contains('modal-aberto')).toBe(false)
    expect(document.activeElement).toBe(elementoAcionador)
    expect(acionador.attributes('aria-expanded')).toBe('false')
    layout.unmount()
  })

  it('fecha o menu movel ao acionar a sobreposicao', async () => {
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

    await layout.get('.navegacao-movel button').trigger('click')
    await flushPromises()
    await layout.get('.sobreposicao-do-menu-movel').trigger('click')
    await flushPromises()

    expect(layout.find('[role="dialog"]').exists()).toBe(false)
    expect(document.body.classList.contains('modal-aberto')).toBe(false)
    layout.unmount()
  })

  it('remove todos os acessos ao Telegram quando a feature flag esta desligada', async () => {
    funcionalidades.assistenteTelegramEstaHabilitado.mockReturnValue(false)
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

    await layout.get('.navegacao-movel button').trigger('click')
    await flushPromises()

    expect(layout.findAll('a[href="/integracoes/telegram"]')).toHaveLength(0)
    layout.unmount()
  })
})
