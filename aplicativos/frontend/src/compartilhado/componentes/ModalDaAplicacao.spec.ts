// @vitest-environment jsdom

import { flushPromises, mount } from '@vue/test-utils'
import { defineComponent, ref } from 'vue'
import { afterEach, describe, expect, it } from 'vitest'

import ModalDaAplicacao from './ModalDaAplicacao.vue'

const ComponenteDeTeste = defineComponent({
  components: { ModalDaAplicacao },
  setup() {
    const aberto = ref(false)
    return { aberto }
  },
  template: `
    <button id="abrir-dialogo" type="button" @click="aberto = true">Abrir</button>
    <ModalDaAplicacao
      v-if="aberto"
      etiqueta="Teste"
      titulo="Dialogo acessivel"
      @fechar="aberto = false"
    >
      <input id="campo-do-dialogo" autofocus />
      <button id="salvar-dialogo" type="button">Salvar</button>
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
})
