<script setup lang="ts">
import { usarDialogoAcessivel } from './usarDialogoAcessivel'

const propriedades = defineProps<{
  titulo: string
  etiqueta: string
  descricao?: string
  larga?: boolean
}>()

const emitir = defineEmits<{
  fechar: []
}>()

const { raizDoDialogo } = usarDialogoAcessivel(() => emitir('fechar'))
</script>

<template>
  <div
    ref="raizDoDialogo"
    class="sobreposicao-da-gaveta"
    @mousedown.self="emitir('fechar')"
  >
    <aside
      class="gaveta-da-aplicacao"
      :class="{ 'gaveta-da-aplicacao-larga': propriedades.larga }"
      role="dialog"
      aria-modal="true"
      :aria-label="propriedades.titulo"
    >
      <header class="cabecalho-da-gaveta">
        <div>
          <p class="sobretitulo-da-pagina">{{ etiqueta }}</p>
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
      <div class="corpo-da-gaveta">
        <slot />
      </div>
    </aside>
  </div>
</template>
