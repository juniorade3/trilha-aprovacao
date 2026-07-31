<script setup lang="ts">
import { computed } from 'vue'

import type {
  ConfirmacaoDeCampoDaExtracao,
  ProblemaDaImportacao,
  ValorExtraido,
} from './apiDeImportacaoDeEdital'

const propriedades = withDefaults(
  defineProps<{
    dado?: ValorExtraido<string | number> | null
    problemas: ProblemaDaImportacao[]
    tipoDoRecurso: string
    chaveDoRecurso: string
    campo: string
    confirmada: boolean
    identificador?: string
    permitirConfirmacao?: boolean
    permitirConfirmacaoSemValor?: boolean
  }>(),
  {
    dado: undefined,
    identificador: undefined,
    permitirConfirmacao: true,
    permitirConfirmacaoSemValor: false,
  },
)

const emitir = defineEmits<{
  alterarConfirmacao: [
    referencia: ConfirmacaoDeCampoDaExtracao,
    confirmada: boolean,
  ]
}>()

const possuiValor = computed(() => {
  const valor = propriedades.dado?.valor
  return typeof valor === 'string' ? Boolean(valor.trim()) : valor != null
})

const exigeConfirmacao = computed(() =>
  propriedades.problemas.some(
    (problema) =>
      propriedades.permitirConfirmacao !== false &&
      problema.codigo === 'EVIDENCIA_ASSISTIDA_NAO_VERIFICADA',
  ),
)

const dadoInferido = computed(() => Boolean(propriedades.dado?.inferido))

const confirmacaoDisponivel = computed(
  () => possuiValor.value || propriedades.permitirConfirmacaoSemValor,
)

const referencia = computed<ConfirmacaoDeCampoDaExtracao>(() => ({
  tipoDoRecurso: propriedades.tipoDoRecurso,
  chaveDoRecurso: propriedades.chaveDoRecurso,
  campo: propriedades.campo,
}))

function alterar() {
  if (!confirmacaoDisponivel.value) return
  emitir('alterarConfirmacao', referencia.value, !propriedades.confirmada)
}
</script>

<template>
  <div
    v-if="problemas.length || dadoInferido"
    :id="identificador"
    class="situacao-do-campo situacao-moderna-do-campo-extraido"
    :data-com-pendencia="problemas.length ? 'true' : undefined"
    tabindex="-1"
  >
    <span
      v-for="problema in problemas"
      :key="`${problema.codigo}-${problema.mensagem}`"
      class="mensagem-do-campo"
      role="alert"
    >
      {{ problema.mensagem }}
    </span>
    <span
      v-if="exigeConfirmacao && confirmacaoDisponivel"
      class="confirmacao-do-campo"
      role="checkbox"
      :aria-checked="confirmada"
      tabindex="0"
      @click.stop.prevent="alterar"
      @keydown.enter.stop.prevent="alterar"
      @keydown.space.stop.prevent="alterar"
    >
      <i
        class="bi"
        :class="confirmada ? 'bi-check-square' : 'bi-square'"
        aria-hidden="true"
      ></i>
      <span>{{
        confirmada
          ? permitirConfirmacaoSemValor
            ? 'Decisão confirmada'
            : 'Valor confirmado'
          : permitirConfirmacaoSemValor
            ? 'Confirmar esta decisão'
            : 'Confirmar este valor'
      }}</span>
    </span>
    <small v-else-if="exigeConfirmacao" class="confirmacao-indisponivel">
      Preencha o campo antes de confirmar este valor.
    </small>
    <small v-else-if="dadoInferido" class="dado-inferido">
      Dado inferido: revise ou corrija o valor antes de continuar.
    </small>
  </div>
</template>

<style scoped lang="scss">
.situacao-do-campo {
  display: grid;
  gap: 0.3rem;
}

.mensagem-do-campo,
.confirmacao-indisponivel {
  color: var(--bs-danger-text-emphasis);
}

.dado-inferido {
  color: var(--bs-warning-text-emphasis);
}

.confirmacao-do-campo {
  align-items: center;
  background: var(--bs-warning-bg-subtle);
  border: 1px solid var(--bs-warning-border-subtle);
  border-radius: 0.4rem;
  color: var(--bs-warning-text-emphasis);
  display: flex;
  font-size: 0.88rem;
  gap: 0.4rem;
  padding: 0.4rem 0.5rem;
  width: fit-content;
}
</style>
