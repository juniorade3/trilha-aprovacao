<script setup lang="ts">
import { computed } from 'vue'

const propriedades = withDefaults(
  defineProps<{
    valor: number
    rotulo: string
    cor?: string
  }>(),
  { cor: undefined },
)

const valorSeguro = computed(() =>
  Math.min(Math.max(Math.round(propriedades.valor), 0), 100),
)
</script>

<template>
  <span
    class="barra-da-jornada"
    role="progressbar"
    :aria-label="rotulo"
    :aria-valuenow="valorSeguro"
    aria-valuemin="0"
    aria-valuemax="100"
  >
    <i
      :style="{
        width: `${valorSeguro}%`,
        background: cor,
      }"
    ></i>
  </span>
</template>
