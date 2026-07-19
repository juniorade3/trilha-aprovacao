<script setup lang="ts">
withDefaults(
  defineProps<{
    titulo: string
    descricao?: string
    icone?: string
    carregando?: boolean
  }>(),
  {
    descricao: '',
    icone: 'bi-inbox',
    carregando: false,
  },
)
</script>

<template>
  <div
    class="estado-do-catalogo"
    :aria-live="carregando ? 'polite' : undefined"
  >
    <span
      v-if="carregando"
      class="spinner-border spinner-border-sm"
      aria-hidden="true"
    ></span>
    <i v-else class="bi" :class="icone" aria-hidden="true"></i>
    <strong>{{ titulo }}</strong>
    <span v-if="descricao">{{ descricao }}</span>
    <slot />
  </div>
</template>
