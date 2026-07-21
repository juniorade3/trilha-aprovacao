<script setup lang="ts">
import { computed, ref, watch } from 'vue'

import {
  sugerirPadroesDeErro,
  type ModeloDeEvidencia,
  type TipoDeEstudo,
} from './apiDeEstudos'

const propriedades = defineProps<{
  tipo: TipoDeEstudo
  identificadorDoTopico?: string
  interrupcao?: boolean
}>()
const modelo = defineModel<ModeloDeEvidencia>({ required: true })

const sugestoes = ref<string[]>([])
const erroDasSugestoes = ref('')

const exigeQuestoes = computed(() =>
  ['QUESTOES', 'SIMULADO', 'CADERNO_DE_ERROS'].includes(propriedades.tipo),
)
const exigeRecordacao = computed(() => propriedades.tipo === 'REVISAO')
const erros = computed(() =>
  Math.max(
    0,
    (modelo.value.quantidadeDeQuestoes ?? 0) -
      (modelo.value.quantidadeDeAcertos ?? 0),
  ),
)

watch(
  () => propriedades.tipo,
  (tipo) => {
    if (!['QUESTOES', 'SIMULADO', 'CADERNO_DE_ERROS'].includes(tipo)) {
      modelo.value.quantidadeDeQuestoes = undefined
      modelo.value.quantidadeDeAcertos = undefined
      modelo.value.padroesDeErro = []
    }
    if (tipo !== 'REVISAO') modelo.value.nivelDeRecordacao = undefined
  },
)

watch(erros, (quantidade) => {
  if (quantidade === 0) modelo.value.padroesDeErro = []
})

async function carregarSugestoes() {
  if (!propriedades.identificadorDoTopico) return
  erroDasSugestoes.value = ''
  try {
    sugestoes.value = await sugerirPadroesDeErro(
      propriedades.identificadorDoTopico,
    )
  } catch {
    erroDasSugestoes.value = 'As sugestões não puderam ser carregadas.'
  }
}

function adicionarPadrao(descricao = '') {
  modelo.value.padroesDeErro.push({
    descricao,
    quantidadeDeOcorrencias: 1,
  })
}
</script>

<template>
  <fieldset class="campos-de-evidencia">
    <legend>Resultado da aprendizagem</legend>
    <p v-if="interrupcao" class="form-text">
      Em uma interrupção, deixe estes campos vazios se ainda não houve resultado
      avaliável.
    </p>
    <div v-if="exigeQuestoes" class="duas-colunas-do-formulario">
      <label>
        <span>Questões realizadas</span>
        <input
          v-model.number="modelo.quantidadeDeQuestoes"
          type="number"
          min="1"
          :required="!interrupcao"
        />
      </label>
      <label>
        <span>Acertos</span>
        <input
          v-model.number="modelo.quantidadeDeAcertos"
          type="number"
          min="0"
          :max="modelo.quantidadeDeQuestoes"
          :required="!interrupcao"
        />
      </label>
      <p class="form-text" aria-live="polite">
        Erros calculados: <strong>{{ erros }}</strong>
      </p>
    </div>
    <label v-if="exigeRecordacao">
      <span>Nível de recordação (1 a 5)</span>
      <input
        v-model.number="modelo.nivelDeRecordacao"
        type="number"
        min="1"
        max="5"
        :required="!interrupcao"
      />
    </label>
    <label>
      <span>Dificuldade percebida (1 a 5) <em>opcional</em></span>
      <input
        v-model.number="modelo.dificuldadePercebida"
        type="number"
        min="1"
        max="5"
      />
    </label>
    <div
      v-if="exigeQuestoes && erros > 0"
      class="padroes-de-erro-do-formulario"
    >
      <div class="d-flex align-items-center justify-content-between gap-2">
        <span>Padrões de erro <em>opcional</em></span>
        <button
          class="btn btn-sm btn-outline-secondary"
          type="button"
          @click="adicionarPadrao()"
        >
          Adicionar padrão
        </button>
      </div>
      <datalist :id="`sugestoes-padroes-${identificadorDoTopico}`">
        <option
          v-for="sugestao in sugestoes"
          :key="sugestao"
          :value="sugestao"
        />
      </datalist>
      <div
        v-for="(padrao, indice) in modelo.padroesDeErro"
        :key="indice"
        class="duas-colunas-do-formulario"
      >
        <label>
          <span>Descrição curta</span>
          <input
            v-model="padrao.descricao"
            :list="`sugestoes-padroes-${identificadorDoTopico}`"
            maxlength="200"
            @focus="carregarSugestoes"
          />
        </label>
        <label>
          <span>Ocorrências</span>
          <input
            v-model.number="padrao.quantidadeDeOcorrencias"
            type="number"
            min="1"
            :max="erros"
          />
        </label>
        <button
          class="btn btn-sm btn-link text-danger"
          type="button"
          :aria-label="`Remover padrão ${indice + 1}`"
          @click="modelo.padroesDeErro.splice(indice, 1)"
        >
          Remover
        </button>
      </div>
      <small v-if="erroDasSugestoes" class="text-danger" role="status">{{
        erroDasSugestoes
      }}</small>
    </div>
  </fieldset>
</template>
