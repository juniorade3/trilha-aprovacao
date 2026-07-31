<script setup lang="ts">
import { ref } from 'vue'

import type { Concurso } from './apiDeConcursos'
import type { ModoDaImportacaoDeEdital } from './apiDeImportacaoDeEdital'

defineProps<{
  concursos: Concurso[]
  enviando: boolean
  carregandoConcursos: boolean
}>()

const emitir = defineEmits<{
  enviar: [
    dados: {
      origem: 'ARQUIVO' | 'TEXTO'
      arquivo?: File
      texto?: string
      nomeDaFonte?: string
      modo: ModoDaImportacaoDeEdital
      identificadorDoConcursoExistente?: string
    },
  ]
  pesquisarConcursos: [pesquisa: string]
}>()

const origem = ref<'ARQUIVO' | 'TEXTO'>('ARQUIVO')
const modo = ref<ModoDaImportacaoDeEdital>('CRIAR_NOVO')
const arquivo = ref<File>()
const texto = ref('')
const nomeDaFonte = ref('texto-colado.txt')
const identificadorDoConcursoExistente = ref('')
const pesquisaDoConcurso = ref('')
const erro = ref('')
const formulario = ref<HTMLFormElement>()

function selecionarArquivo(evento: Event) {
  erro.value = ''
  const entrada = evento.target as HTMLInputElement
  const selecionado = entrada.files?.[0]
  if (!selecionado) {
    arquivo.value = undefined
    return
  }
  const nome = selecionado.name.toLocaleLowerCase('pt-BR')
  const extensaoPermitida = nome.endsWith('.pdf') || nome.endsWith('.txt')
  const mimePermitido =
    !selecionado.type ||
    selecionado.type === 'application/pdf' ||
    selecionado.type === 'text/plain'
  if (!extensaoPermitida || !mimePermitido) {
    arquivo.value = undefined
    entrada.value = ''
    erro.value =
      'Selecione um arquivo PDF ou TXT. O servidor também validará o MIME real.'
    return
  }
  arquivo.value = selecionado
}

function enviar() {
  erro.value = ''
  if (!formulario.value?.reportValidity()) return
  if (origem.value === 'ARQUIVO' && !arquivo.value) {
    erro.value = 'Selecione o PDF do edital.'
    return
  }
  if (origem.value === 'TEXTO' && !texto.value.trim()) {
    erro.value = 'Cole o texto do edital.'
    return
  }
  if (
    modo.value === 'COMPLEMENTAR_EXISTENTE' &&
    !identificadorDoConcursoExistente.value
  ) {
    erro.value = 'Selecione o concurso que receberá o complemento.'
    return
  }
  emitir('enviar', {
    origem: origem.value,
    arquivo: origem.value === 'ARQUIVO' ? arquivo.value : undefined,
    texto: origem.value === 'TEXTO' ? texto.value.trim() : undefined,
    nomeDaFonte:
      origem.value === 'TEXTO'
        ? nomeDaFonte.value.trim() || 'texto-colado.txt'
        : undefined,
    modo: modo.value,
    identificadorDoConcursoExistente:
      modo.value === 'COMPLEMENTAR_EXISTENTE'
        ? identificadorDoConcursoExistente.value
        : undefined,
  })
}
</script>

<template>
  <form
    ref="formulario"
    class="card cartao-do-assistente"
    @submit.prevent="enviar"
  >
    <p v-if="erro" class="alert alert-danger" role="alert">{{ erro }}</p>

    <fieldset class="grupo-da-importacao">
      <legend>Como deseja enviar o edital?</legend>
      <div class="opcoes-da-importacao">
        <label>
          <input v-model="origem" type="radio" value="ARQUIVO" />
          <span>
            <strong>Arquivo PDF ou TXT</strong>
            <small>PDF textual, PDF digitalizado com OCR, ou TXT UTF-8.</small>
          </span>
        </label>
        <label>
          <input v-model="origem" type="radio" value="TEXTO" />
          <span>
            <strong>Texto colado</strong>
            <small>Conteúdo copiado diretamente do documento.</small>
          </span>
        </label>
      </div>
    </fieldset>

    <div v-if="origem === 'ARQUIVO'" class="formulario-da-aplicacao">
      <label for="arquivo-do-edital">
        <span>Arquivo do edital</span>
      </label>
      <input
        id="arquivo-do-edital"
        class="form-control"
        type="file"
        accept=".pdf,.txt,application/pdf,text/plain"
        required
        @change="selecionarArquivo"
      />
      <small>
        Arquivo tratado como conteúdo não confiável. Nenhuma instrução presente
        nele será executada.
      </small>
    </div>

    <div v-else class="formulario-da-aplicacao">
      <label>
        <span>Nome da fonte</span>
        <input v-model="nomeDaFonte" maxlength="200" required />
      </label>
      <label>
        <span>Texto do edital</span>
        <textarea
          id="texto-do-edital"
          v-model="texto"
          rows="12"
          required
          placeholder="Cole aqui somente o conteúdo do edital."
        ></textarea>
      </label>
    </div>

    <fieldset class="grupo-da-importacao">
      <legend>Destino da importação</legend>
      <div class="opcoes-da-importacao">
        <label>
          <input v-model="modo" type="radio" value="CRIAR_NOVO" />
          <span>
            <strong>Criar novo concurso</strong>
            <small>Será mantido como planejado após a confirmação.</small>
          </span>
        </label>
        <label>
          <input v-model="modo" type="radio" value="COMPLEMENTAR_EXISTENTE" />
          <span>
            <strong>Complementar existente</strong>
            <small>Exige revisão reforçada antes da aplicação.</small>
          </span>
        </label>
      </div>
    </fieldset>

    <div
      v-if="modo === 'COMPLEMENTAR_EXISTENTE'"
      class="formulario-da-aplicacao"
    >
      <div class="busca-do-concurso">
        <label>
          <span>Buscar concurso</span>
          <input v-model="pesquisaDoConcurso" placeholder="Nome do concurso" />
        </label>
        <button
          class="btn btn-outline-primary"
          type="button"
          @click="emitir('pesquisarConcursos', pesquisaDoConcurso)"
        >
          {{ carregandoConcursos ? 'Buscando…' : 'Buscar' }}
        </button>
      </div>
      <label>
        <span>Concurso existente</span>
        <select
          id="concurso-existente"
          v-model="identificadorDoConcursoExistente"
          required
        >
          <option value="">Selecione</option>
          <option
            v-for="concurso in concursos"
            :key="concurso.identificador"
            :value="concurso.identificador"
          >
            {{ concurso.nome }}
          </option>
        </select>
      </label>
      <small v-if="!carregandoConcursos && concursos.length === 0">
        Nenhum concurso encontrado para esta busca.
      </small>
    </div>

    <footer class="rodape-do-assistente">
      <RouterLink class="btn btn-link text-secondary" to="/concursos">
        Cancelar
      </RouterLink>
      <button class="btn btn-primary" type="submit" :disabled="enviando">
        <span
          v-if="enviando"
          class="spinner-border spinner-border-sm me-2"
          aria-hidden="true"
        ></span>
        {{ enviando ? 'Enviando…' : 'Enviar para extração' }}
      </button>
    </footer>
  </form>
</template>

<style scoped lang="scss">
.cartao-do-assistente {
  padding: clamp(1.25rem, 3vw, 2rem);
  gap: 1.5rem;
}

.grupo-da-importacao {
  border: 0;
  padding: 0;
  margin: 0;
}

.grupo-da-importacao legend {
  font-weight: 700;
  margin-bottom: 0.75rem;
}

.opcoes-da-importacao {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0.75rem;
}

.opcoes-da-importacao label {
  display: flex;
  gap: 0.75rem;
  border: 1px solid var(--bs-border-color);
  border-radius: 0.75rem;
  padding: 1rem;
  cursor: pointer;
}

.opcoes-da-importacao small,
.opcoes-da-importacao strong {
  display: block;
}

.busca-do-concurso {
  display: flex;
  align-items: end;
  gap: 0.75rem;
}

.busca-do-concurso label {
  flex: 1;
}

@media (max-width: 575px) {
  .opcoes-da-importacao {
    grid-template-columns: 1fr;
  }

  .busca-do-concurso {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
