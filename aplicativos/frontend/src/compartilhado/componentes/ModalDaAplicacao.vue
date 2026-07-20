<script setup lang="ts">
import { usarDialogoAcessivel } from './usarDialogoAcessivel'

const propriedades = defineProps<{
  titulo: string
  etiqueta: string
  descricao?: string
  sobreGaveta?: boolean
}>()

const emitir = defineEmits<{
  fechar: []
}>()

const { raizDoDialogo } = usarDialogoAcessivel(() => emitir('fechar'))
</script>

<template>
  <Teleport to="body">
    <div
      ref="raizDoDialogo"
      class="sobreposicao-da-aplicacao"
      :class="{
        'sobreposicao-da-aplicacao-sobre-gaveta': propriedades.sobreGaveta,
      }"
      @mousedown.self="emitir('fechar')"
    >
      <section
        class="modal-da-aplicacao"
        role="dialog"
        aria-modal="true"
        :aria-label="propriedades.titulo"
      >
        <header class="cabecalho-do-modal">
          <div>
            <p class="sobretitulo-da-pagina mb-2">{{ etiqueta }}</p>
            <h2>{{ titulo }}</h2>
            <p v-if="descricao">{{ descricao }}</p>
          </div>
          <button
            class="botao-de-icone"
            type="button"
            aria-label="Fechar"
            @click="emitir('fechar')"
          >
            <i class="bi bi-x-lg" aria-hidden="true"></i>
          </button>
        </header>
        <div class="corpo-do-modal">
          <slot />
        </div>
        <footer v-if="$slots.rodape" class="rodape-do-modal">
          <slot name="rodape" />
        </footer>
      </section>
    </div>
  </Teleport>
</template>
