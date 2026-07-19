<script setup lang="ts">
defineProps<{
  etiqueta: string
  titulo: string
  descricao: string
}>()
</script>

<template>
  <header class="cabecalho-da-pagina">
    <div>
      <p class="sobretitulo-da-pagina">{{ etiqueta }}</p>
      <h1>{{ titulo }}</h1>
      <p>{{ descricao }}</p>
    </div>
    <div v-if="$slots.acoes" class="acoes-do-cabecalho">
      <slot name="acoes" />
    </div>
  </header>
</template>
