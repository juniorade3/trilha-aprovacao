<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'

import { ErroDaApi } from '@/compartilhado/api/clienteHttp'
import GavetaLateral from '@/compartilhado/componentes/GavetaLateral.vue'
import {
  aplicarReplanejamento,
  gerarPreviaDoReplanejamento,
  type PreviaDoReplanejamento,
  type ResultadoDaAplicacaoDoReplanejamento,
} from './apiDePlanejamento'

const propriedades = defineProps<{
  identificadorDoPlano: string
  dataDeReferencia: string
}>()

const emitir = defineEmits<{
  fechar: []
  aplicado: [resultado: ResultadoDaAplicacaoDoReplanejamento]
}>()

const previa = ref<PreviaDoReplanejamento>()
const ignoradas = ref<string[]>([])
const confirmadas = ref<string[]>([])
const carregando = ref(false)
const aplicando = ref(false)
const erro = ref('')
const aviso = ref('')
const confirmacaoFinal = ref(false)

const podeAplicar = computed(
  () =>
    Boolean(previa.value?.resumo.minutosAlocados) &&
    (previa.value?.pendencias ?? [])
      .filter((item) => item.exigeConfirmacao)
      .every((item) => confirmadas.value.includes(item.identificadorDoBloco)),
)

async function recalcular(mensagem = '') {
  carregando.value = true
  erro.value = ''
  aviso.value = mensagem
  confirmacaoFinal.value = false
  try {
    previa.value = await gerarPreviaDoReplanejamento(
      propriedades.identificadorDoPlano,
      propriedades.dataDeReferencia,
      ignoradas.value,
    )
    confirmadas.value = confirmadas.value.filter((id) =>
      previa.value?.pendencias.some(
        (item) => item.identificadorDoBloco === id && item.exigeConfirmacao,
      ),
    )
  } catch (causa) {
    erro.value =
      causa instanceof Error
        ? causa.message
        : 'Não foi possível calcular o replanejamento.'
  } finally {
    carregando.value = false
  }
}

async function remover(identificador: string) {
  ignoradas.value = [...ignoradas.value, identificador]
  await recalcular(
    'Pendência removida; a proposta foi recalculada no servidor.',
  )
}

function alternarConfirmacao(identificador: string, marcado: boolean) {
  confirmadas.value = marcado
    ? [...confirmadas.value, identificador]
    : confirmadas.value.filter((id) => id !== identificador)
}

async function aplicar() {
  if (!previa.value || !confirmacaoFinal.value || !podeAplicar.value) return
  aplicando.value = true
  erro.value = ''
  try {
    const resultado = await aplicarReplanejamento(
      propriedades.identificadorDoPlano,
      propriedades.dataDeReferencia,
      ignoradas.value,
      confirmadas.value,
      previa.value.assinaturaDaPrevia,
    )
    emitir('aplicado', resultado)
  } catch (causa) {
    if (
      causa instanceof ErroDaApi &&
      causa.status === 409 &&
      causa.codigo === 'PREVIA_DE_REPLANEJAMENTO_DESATUALIZADA'
    ) {
      await recalcular(
        'A semana mudou. Revise a nova proposta; nada foi aplicado automaticamente.',
      )
    } else {
      erro.value =
        causa instanceof Error
          ? causa.message
          : 'Não foi possível aplicar o replanejamento.'
    }
  } finally {
    aplicando.value = false
  }
}

onMounted(() => recalcular())
</script>

<template>
  <GavetaLateral
    titulo="Replanejar pendências"
    etiqueta="Replanejamento determinístico"
    descricao="Transfira somente o que cabe entre a data de referência e domingo."
    larga
    @fechar="emitir('fechar')"
  >
    <div v-if="erro" class="alert alert-danger" role="alert">
      {{ erro }}
      <button
        class="btn btn-sm btn-outline-danger ms-2"
        type="button"
        @click="recalcular()"
      >
        Tentar novamente
      </button>
    </div>
    <div v-if="aviso" class="alert alert-info" role="status">{{ aviso }}</div>
    <div
      v-if="carregando"
      class="d-flex align-items-center gap-2"
      role="status"
    >
      <span class="spinner-border spinner-border-sm" aria-hidden="true"></span>
      Recalculando proposta…
    </div>

    <template v-else-if="previa">
      <dl class="resumo-do-replanejamento">
        <div>
          <dt>Pendentes</dt>
          <dd>{{ previa.resumo.minutosPendentes }} min</dd>
        </div>
        <div>
          <dt>Alocados</dt>
          <dd>{{ previa.resumo.minutosAlocados }} min</dd>
        </div>
        <div>
          <dt>Sem capacidade</dt>
          <dd>{{ previa.resumo.minutosNaoAlocados }} min</dd>
        </div>
      </dl>

      <h3 class="h5 mt-4">Capacidade até domingo</h3>
      <ul class="list-group mb-4">
        <li
          v-for="dia in previa.capacidadesPorDia"
          :key="dia.data"
          class="list-group-item d-flex justify-content-between gap-3"
        >
          <strong>{{ dia.data }}</strong>
          <span
            >{{ dia.minutosAlocados }} min novos ·
            {{ dia.minutosRestantes }} min livres</span
          >
        </li>
      </ul>

      <h3 class="h5">Proposta</h3>
      <p v-if="!previa.pendencias.length" class="text-body-secondary">
        Não há pendências elegíveis nesta data.
      </p>
      <article
        v-for="pendencia in previa.pendencias.filter(
          (item) => item.decisao !== 'IGNORAR',
        )"
        :key="pendencia.identificadorDoBloco"
        class="cartao-de-pendencia"
      >
        <header>
          <div>
            <strong>{{ pendencia.titulo }}</strong>
            <span
              >{{ pendencia.minutosPendentes }} min ·
              {{ pendencia.decisao.replace(/_/g, ' ') }}</span
            >
          </div>
          <button
            class="btn btn-sm btn-outline-secondary"
            type="button"
            :aria-label="`Remover ${pendencia.titulo} desta proposta`"
            @click="remover(pendencia.identificadorDoBloco)"
          >
            Remover
          </button>
        </header>
        <p>{{ pendencia.justificativa }}</p>
        <ul v-if="pendencia.fragmentos.length" class="mb-2">
          <li
            v-for="fragmento in pendencia.fragmentos"
            :key="fragmento.sequencia"
          >
            {{ fragmento.data }} — {{ fragmento.duracaoEmMinutos }} min
          </li>
        </ul>
        <div
          v-if="pendencia.exigeConfirmacao"
          class="form-check alert alert-warning mb-0"
        >
          <input
            :id="`confirmar-${pendencia.identificadorDoBloco}`"
            class="form-check-input"
            type="checkbox"
            :checked="confirmadas.includes(pendencia.identificadorDoBloco)"
            @change="
              alternarConfirmacao(
                pendencia.identificadorDoBloco,
                ($event.target as HTMLInputElement).checked,
              )
            "
          />
          <label
            class="form-check-label"
            :for="`confirmar-${pendencia.identificadorDoBloco}`"
          >
            Confirmo este terceiro reagendamento.
          </label>
        </div>
      </article>

      <div
        v-if="previa.resumo.minutosAlocados"
        class="confirmacao-final form-check mt-4"
      >
        <input
          id="confirmacao-final"
          v-model="confirmacaoFinal"
          class="form-check-input"
          type="checkbox"
        />
        <label class="form-check-label" for="confirmacao-final">
          Revisei a proposta e confirmo a criação dos novos blocos.
        </label>
      </div>
      <div class="d-flex flex-wrap justify-content-end gap-2 mt-4">
        <button
          class="btn btn-outline-secondary"
          type="button"
          @click="emitir('fechar')"
        >
          Voltar
        </button>
        <button
          class="btn btn-primary"
          type="button"
          :disabled="!podeAplicar || !confirmacaoFinal || aplicando"
          @click="aplicar"
        >
          Aplicar replanejamento
        </button>
      </div>
    </template>
  </GavetaLateral>
</template>

<style scoped lang="scss">
.resumo-do-replanejamento {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 0.75rem;
  margin: 0;
  div {
    padding: 0.75rem;
    border-radius: 0.75rem;
    background: var(--bs-light);
  }
  dt {
    color: var(--bs-secondary-color);
    font-size: 0.85rem;
  }
  dd {
    margin: 0.2rem 0 0;
    font-weight: 700;
  }
}
.cartao-de-pendencia {
  border: 1px solid var(--bs-border-color);
  border-radius: 0.75rem;
  padding: 1rem;
  margin-top: 0.75rem;
  header {
    display: flex;
    justify-content: space-between;
    gap: 1rem;
  }
  header div,
  header span {
    display: block;
  }
  p {
    margin: 0.75rem 0;
    color: var(--bs-secondary-color);
  }
}
@media (max-width: 575.98px) {
  .resumo-do-replanejamento {
    grid-template-columns: 1fr;
  }
  .cartao-de-pendencia header {
    align-items: flex-start;
  }
}
</style>
