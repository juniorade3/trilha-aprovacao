import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'

const seletoresFocaveis = [
  'a[href]',
  'button:not([disabled])',
  'input:not([disabled])',
  'select:not([disabled])',
  'textarea:not([disabled])',
  '[tabindex]:not([tabindex="-1"])',
].join(',')

const pilhaDeDialogos: symbol[] = []

export function usarDialogoAcessivel(fechar: () => void) {
  const raizDoDialogo = ref<HTMLElement>()
  const identificadorDoDialogo = Symbol('dialogo')
  let focoAnterior: HTMLElement | null = null

  function elementosFocaveis() {
    return Array.from(
      raizDoDialogo.value?.querySelectorAll<HTMLElement>(seletoresFocaveis) ??
        [],
    )
  }

  function aoPressionarTecla(evento: KeyboardEvent) {
    if (pilhaDeDialogos[pilhaDeDialogos.length - 1] !== identificadorDoDialogo)
      return
    if (evento.key === 'Escape') {
      evento.preventDefault()
      fechar()
      return
    }
    if (evento.key !== 'Tab') return
    const elementos = elementosFocaveis()
    if (elementos.length === 0) {
      evento.preventDefault()
      return
    }
    const primeiro = elementos[0]!
    const ultimo = elementos[elementos.length - 1]!
    if (evento.shiftKey && document.activeElement === primeiro) {
      evento.preventDefault()
      ultimo.focus()
    } else if (!evento.shiftKey && document.activeElement === ultimo) {
      evento.preventDefault()
      primeiro.focus()
    }
  }

  onMounted(async () => {
    focoAnterior =
      document.activeElement instanceof HTMLElement
        ? document.activeElement
        : null
    document.body.classList.add('modal-aberto')
    pilhaDeDialogos.push(identificadorDoDialogo)
    window.addEventListener('keydown', aoPressionarTecla)
    await nextTick()
    const focoInicial =
      raizDoDialogo.value?.querySelector<HTMLElement>('[autofocus]') ??
      elementosFocaveis()[0]
    focoInicial?.focus()
  })

  onBeforeUnmount(() => {
    const indice = pilhaDeDialogos.lastIndexOf(identificadorDoDialogo)
    if (indice >= 0) pilhaDeDialogos.splice(indice, 1)
    if (pilhaDeDialogos.length === 0)
      document.body.classList.remove('modal-aberto')
    window.removeEventListener('keydown', aoPressionarTecla)
    focoAnterior?.focus()
  })

  return { raizDoDialogo }
}
