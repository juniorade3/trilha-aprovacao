import {
  nextTick,
  onBeforeUnmount,
  onMounted,
  ref,
  toValue,
  watch,
  type MaybeRefOrGetter,
  type WatchStopHandle,
} from 'vue'

const seletoresFocaveis = [
  'a[href]',
  'button:not([disabled])',
  'input:not([disabled])',
  'select:not([disabled])',
  'textarea:not([disabled])',
  '[tabindex]:not([tabindex="-1"])',
].join(',')

const pilhaDeDialogos: symbol[] = []

export function usarDialogoAcessivel(
  fechar: () => void,
  aberto?: MaybeRefOrGetter<boolean>,
) {
  const raizDoDialogo = ref<HTMLElement>()
  const identificadorDoDialogo = Symbol('dialogo')
  let focoAnterior: HTMLElement | null = null
  let dialogoAtivo = false
  let pararDeObservar: WatchStopHandle | undefined

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

  async function ativarDialogo() {
    if (dialogoAtivo) return
    dialogoAtivo = true
    focoAnterior =
      document.activeElement instanceof HTMLElement
        ? document.activeElement
        : null
    document.body.classList.add('modal-aberto')
    pilhaDeDialogos.push(identificadorDoDialogo)
    window.addEventListener('keydown', aoPressionarTecla)
    await nextTick()
    if (!dialogoAtivo) return
    const focoInicial =
      raizDoDialogo.value?.querySelector<HTMLElement>('[autofocus]') ??
      elementosFocaveis()[0]
    focoInicial?.focus()
  }

  function desativarDialogo() {
    if (!dialogoAtivo) return
    dialogoAtivo = false
    const indice = pilhaDeDialogos.lastIndexOf(identificadorDoDialogo)
    if (indice >= 0) pilhaDeDialogos.splice(indice, 1)
    if (pilhaDeDialogos.length === 0)
      document.body.classList.remove('modal-aberto')
    window.removeEventListener('keydown', aoPressionarTecla)
    focoAnterior?.focus()
    focoAnterior = null
  }

  onMounted(() => {
    if (aberto === undefined) {
      void ativarDialogo()
      return
    }
    pararDeObservar = watch(
      () => toValue(aberto),
      (deveEstarAtivo) => {
        if (deveEstarAtivo) void ativarDialogo()
        else desativarDialogo()
      },
      { flush: 'post', immediate: true },
    )
  })

  onBeforeUnmount(() => {
    pararDeObservar?.()
    desativarDialogo()
  })

  return { raizDoDialogo }
}
