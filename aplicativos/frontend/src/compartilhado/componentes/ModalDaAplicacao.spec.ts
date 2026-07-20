// @vitest-environment jsdom

import { flushPromises, mount } from '@vue/test-utils'
import { defineComponent, ref } from 'vue'
import { afterEach, describe, expect, it } from 'vitest'

import ModalDaAplicacao from './ModalDaAplicacao.vue'
import GavetaLateral from './GavetaLateral.vue'

const ComponenteDeTeste = defineComponent({
  components: { GavetaLateral, ModalDaAplicacao },
  props: {
    iniciarAninhado: { type: Boolean, default: false },
  },
  setup(propriedades) {
    const aberto = ref(false)
    const gavetaAberta = ref(propriedades.iniciarAninhado)
    const modalAberto = ref(propriedades.iniciarAninhado)
    return { aberto, gavetaAberta, modalAberto }
  },
  template: `
    <button v-if="!iniciarAninhado" id="abrir-dialogo" type="button"
      @click="aberto = true">Abrir</button>
    <ModalDaAplicacao
      v-if="!iniciarAninhado && aberto"
      etiqueta="Teste"
      titulo="Dialogo acessivel"
      @fechar="aberto = false"
    >
      <input id="campo-do-dialogo" autofocus />
      <button id="salvar-dialogo" type="button">Salvar</button>
    </ModalDaAplicacao>
    <GavetaLateral v-if="iniciarAninhado && gavetaAberta" titulo="Gaveta" etiqueta="Teste"
      @fechar="gavetaAberta = false">
      <button type="button">Ação da gaveta</button>
    </GavetaLateral>
    <ModalDaAplicacao v-if="iniciarAninhado && modalAberto" titulo="Modal" etiqueta="Teste"
      sobre-gaveta
      @fechar="modalAberto = false">
      <button type="button">Ação do modal</button>
    </ModalDaAplicacao>
  `,
})

afterEach(() => {
  document.body.innerHTML = ''
  document.body.classList.remove('modal-aberto')
})

describe('ModalDaAplicacao', () => {
  it('fecha com Escape e devolve o foco ao elemento que abriu', async () => {
    const componente = mount(ComponenteDeTeste, { attachTo: document.body })
    const abertura = componente.get('#abrir-dialogo')
    const elementoDeAbertura = abertura.element
    if (elementoDeAbertura instanceof HTMLElement) elementoDeAbertura.focus()
    await abertura.trigger('click')
    await flushPromises()

    expect(document.activeElement?.id).toBe('campo-do-dialogo')
    expect(document.body.classList.contains('modal-aberto')).toBe(true)

    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
    await flushPromises()

    expect(document.querySelector('[role="dialog"]')).toBeNull()
    expect(document.activeElement?.id).toBe('abrir-dialogo')
    componente.unmount()
  })

  it('mantem a navegacao por Tab dentro do dialogo', async () => {
    const componente = mount(ComponenteDeTeste, { attachTo: document.body })
    await componente.get('#abrir-dialogo').trigger('click')
    await flushPromises()

    const ultimo = document.querySelector<HTMLElement>('#salvar-dialogo')!
    ultimo.focus()
    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Tab' }))

    expect(document.activeElement?.getAttribute('aria-label')).toBe('Fechar')
    componente.unmount()
  })

  it('fecha somente o dialogo superior e mantem o bloqueio do inferior', async () => {
    const componente = mount(ComponenteDeTeste, {
      attachTo: document.body,
      props: { iniciarAninhado: true },
    })
    await flushPromises()

    expect(
      document.querySelector('.sobreposicao-da-aplicacao-sobre-gaveta'),
    ).not.toBeNull()

    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
    await flushPromises()

    expect(document.querySelectorAll('[role="dialog"]')).toHaveLength(1)
    expect(document.body.classList.contains('modal-aberto')).toBe(true)

    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
    await flushPromises()
    expect(document.querySelector('[role="dialog"]')).toBeNull()
    expect(document.body.classList.contains('modal-aberto')).toBe(false)
    componente.unmount()
  })
})
