<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'

import {
  aderirATrilhaPublicada,
  atualizarAcompanhamentoDaTarefa,
  obterTrilhaPublicada,
  type SituacaoDoAcompanhamentoDaTarefa,
  type TarefaDaTrilha,
  type DetalheDaTrilhaPublicada,
} from './apiDeTrilhas'
import BarraDeProgresso from '@/compartilhado/componentes/BarraDeProgresso.vue'

const rota = useRoute()
const detalhe = ref<DetalheDaTrilhaPublicada>()
const carregando = ref(true)
const aderindo = ref(false)
const atualizando = ref<string>()
const erro = ref('')
let cancelamento: AbortController | undefined

const trilha = computed(() => detalhe.value?.trilha)

const porcentagemDeConclusao = computed(() => {
  if (!trilha.value?.quantidadeDeTarefas) return 0
  return (
    (trilha.value.quantidadeDeTarefasConcluidas /
      trilha.value.quantidadeDeTarefas) *
    100
  )
})

const rotulosDeSituacao: Record<SituacaoDoAcompanhamentoDaTarefa, string> = {
  PENDENTE: 'Pendente',
  EM_ANDAMENTO: 'Em andamento',
  CONCLUIDA: 'Concluída',
  PULADA: 'Pulei por agora',
}

async function carregar() {
  const identificador = String(rota.params.identificador)
  cancelamento?.abort()
  cancelamento = new AbortController()
  carregando.value = true
  erro.value = ''
  try {
    detalhe.value = await obterTrilhaPublicada(
      identificador,
      cancelamento.signal,
    )
  } catch (causa) {
    if (causa instanceof DOMException && causa.name === 'AbortError') return
    erro.value =
      causa instanceof Error
        ? causa.message
        : 'Não foi possível carregar a trilha.'
  } finally {
    carregando.value = false
  }
}

async function aderir() {
  if (!trilha.value) return
  aderindo.value = true
  erro.value = ''
  try {
    await aderirATrilhaPublicada(trilha.value.identificador)
    await carregar()
  } catch (causa) {
    erro.value =
      causa instanceof Error
        ? causa.message
        : 'Não foi possível iniciar a trilha.'
  } finally {
    aderindo.value = false
  }
}

async function atualizarTarefa(tarefa: TarefaDaTrilha, evento: Event) {
  if (!trilha.value) return
  const situacao = (evento.target as HTMLSelectElement)
    .value as SituacaoDoAcompanhamentoDaTarefa
  atualizando.value = tarefa.identificador
  erro.value = ''
  try {
    const estavaConcluida = tarefa.situacao === 'CONCLUIDA'
    const atualizada = await atualizarAcompanhamentoDaTarefa(
      trilha.value.identificador,
      tarefa.identificador,
      situacao,
    )
    Object.assign(tarefa, atualizada)
    if (!estavaConcluida && atualizada.situacao === 'CONCLUIDA')
      trilha.value.quantidadeDeTarefasConcluidas += 1
    if (estavaConcluida && atualizada.situacao !== 'CONCLUIDA')
      trilha.value.quantidadeDeTarefasConcluidas -= 1
  } catch (causa) {
    erro.value =
      causa instanceof Error
        ? causa.message
        : 'Não foi possível atualizar a tarefa.'
    await carregar()
  } finally {
    atualizando.value = undefined
  }
}

onMounted(carregar)
onBeforeUnmount(() => cancelamento?.abort())
</script>

<template>
  <main class="pagina-da-jornada pagina-da-trilha-publicada">
    <RouterLink class="link-voltar mb-4 d-inline-flex" to="/trilhas">
      <i class="bi bi-arrow-left me-2" aria-hidden="true"></i>
      Todas as trilhas
    </RouterLink>

    <div v-if="carregando" class="estado-do-catalogo" aria-live="polite">
      <span class="spinner-border" aria-hidden="true"></span>
      Carregando trilha...
    </div>
    <template v-else-if="trilha">
      <header class="cabecalho-da-pagina">
        <div>
          <p class="sobretitulo-da-pagina">
            Trilha publicada · versão {{ trilha.versaoPublicada }}
          </p>
          <h1>{{ trilha.nome }}</h1>
          <p>{{ trilha.descricao }}</p>
        </div>
        <button
          v-if="!trilha.aderida"
          class="btn btn-primary"
          type="button"
          :disabled="aderindo"
          @click="aderir"
        >
          <span
            v-if="aderindo"
            class="spinner-border spinner-border-sm me-2"
            aria-hidden="true"
          ></span>
          {{ aderindo ? 'Iniciando...' : 'Iniciar minha trilha' }}
        </button>
      </header>

      <p v-if="erro" class="alert alert-danger" role="alert">{{ erro }}</p>

      <section
        v-if="trilha.aderida"
        class="card p-4 mb-4"
        aria-label="Progresso da trilha"
      >
        <div class="d-flex justify-content-between gap-3 mb-2">
          <strong>Seu progresso</strong>
          <span
            >{{ trilha.quantidadeDeTarefasConcluidas }} de
            {{ trilha.quantidadeDeTarefas }} tarefas</span
          >
        </div>
        <BarraDeProgresso
          :valor="porcentagemDeConclusao"
          :rotulo="`Progresso em ${trilha.nome}`"
        />
      </section>
      <p v-else class="alert alert-info" role="status">
        Inicie a trilha para registrar o seu progresso. As tarefas e o catálogo
        público não serão alterados.
      </p>

      <section class="d-grid gap-3" aria-label="Disciplinas da trilha">
        <details
          v-for="disciplina in detalhe?.disciplinas"
          :key="disciplina.identificador"
          class="card p-0"
          open
        >
          <summary
            class="p-3 d-flex justify-content-between align-items-center gap-3"
          >
            <strong>{{ disciplina.nome }}</strong>
            <span class="badge etiqueta-neutra"
              >{{ disciplina.tarefas.length }} tarefas</span
            >
          </summary>
          <ol class="list-group list-group-flush list-unstyled mb-0">
            <li
              v-for="tarefa in disciplina.tarefas"
              :key="tarefa.identificador"
              class="list-group-item p-3"
            >
              <div
                class="d-flex flex-column flex-lg-row justify-content-between gap-3"
              >
                <div>
                  <div class="d-flex flex-wrap align-items-center gap-2 mb-1">
                    <span class="badge etiqueta-neutra"
                      >Tarefa {{ tarefa.numero }}</span
                    >
                    <span class="small text-secondary">{{
                      tarefa.tipoDeAtividade
                    }}</span>
                    <span v-if="tarefa.aula" class="small text-secondary">{{
                      tarefa.aula
                    }}</span>
                  </div>
                  <strong>{{ tarefa.titulo }}</strong>
                  <p v-if="tarefa.orientacao" class="mb-1 mt-2 text-secondary">
                    {{ tarefa.orientacao }}
                  </p>
                  <a
                    v-if="tarefa.enderecoDoMaterial"
                    class="link-primary small d-inline-block mt-1"
                    :href="tarefa.enderecoDoMaterial"
                    target="_blank"
                    rel="noopener noreferrer"
                  >
                    Abrir material
                    <i
                      class="bi bi-box-arrow-up-right ms-1"
                      aria-hidden="true"
                    ></i>
                  </a>
                </div>
                <div v-if="trilha.aderida" class="flex-shrink-0">
                  <label
                    class="visually-hidden"
                    :for="`situacao-${tarefa.identificador}`"
                  >
                    Situação da tarefa {{ tarefa.numero }}
                  </label>
                  <select
                    :id="`situacao-${tarefa.identificador}`"
                    class="form-select"
                    :value="tarefa.situacao"
                    :disabled="atualizando === tarefa.identificador"
                    @change="atualizarTarefa(tarefa, $event)"
                  >
                    <option
                      v-for="(rotulo, situacao) in rotulosDeSituacao"
                      :key="situacao"
                      :value="situacao"
                    >
                      {{ rotulo }}
                    </option>
                  </select>
                </div>
              </div>
            </li>
          </ol>
        </details>
      </section>
    </template>
    <p v-else class="alert alert-danger" role="alert">
      {{ erro || 'Trilha não encontrada.' }}
    </p>
  </main>
</template>
