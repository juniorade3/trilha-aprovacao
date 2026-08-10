<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'

import { listarTrilhasPublicadas, type TrilhaPublicada } from './apiDeTrilhas'
import BarraDeProgresso from '@/compartilhado/componentes/BarraDeProgresso.vue'

const trilhas = ref<TrilhaPublicada[]>([])
const carregando = ref(true)
const erro = ref('')
let cancelamento: AbortController | undefined

function porcentagemDeConclusao(trilha: TrilhaPublicada) {
  if (trilha.quantidadeDeTarefas === 0) return 0
  return (
    (trilha.quantidadeDeTarefasConcluidas / trilha.quantidadeDeTarefas) * 100
  )
}

async function carregar() {
  cancelamento?.abort()
  cancelamento = new AbortController()
  carregando.value = true
  erro.value = ''
  try {
    trilhas.value = await listarTrilhasPublicadas(cancelamento.signal)
  } catch (causa) {
    if (causa instanceof DOMException && causa.name === 'AbortError') return
    erro.value =
      causa instanceof Error
        ? causa.message
        : 'Não foi possível carregar as trilhas disponíveis.'
  } finally {
    carregando.value = false
  }
}

onMounted(carregar)
onBeforeUnmount(() => cancelamento?.abort())
</script>

<template>
  <main class="pagina-da-jornada pagina-das-trilhas-publicadas">
    <header class="cabecalho-da-pagina">
      <div>
        <p class="sobretitulo-da-pagina">Catálogo permanente</p>
        <h1>Trilhas de estudo</h1>
        <p>
          Escolha uma trilha publicada e acompanhe as tarefas no seu próprio
          ritmo.
        </p>
      </div>
    </header>

    <p v-if="erro" class="alert alert-danger" role="alert">{{ erro }}</p>

    <div v-if="carregando" class="estado-do-catalogo" aria-live="polite">
      <span class="spinner-border" aria-hidden="true"></span>
      Carregando trilhas...
    </div>
    <section v-else-if="trilhas.length === 0" class="card estado-do-catalogo">
      <i class="bi bi-signpost-split" aria-hidden="true"></i>
      <strong>Nenhuma trilha foi publicada ainda</strong>
      <span>Quando uma curadoria for publicada, ela aparecerá aqui.</span>
    </section>
    <section v-else class="row g-3" aria-label="Trilhas disponíveis">
      <article
        v-for="trilha in trilhas"
        :key="trilha.identificador"
        class="col-12 col-lg-6"
      >
        <div class="card h-100 p-4 d-flex flex-column gap-3">
          <div class="d-flex justify-content-between align-items-start gap-3">
            <div>
              <span class="badge etiqueta-neutra"
                >Versão {{ trilha.versaoPublicada }}</span
              >
              <h2 class="h4 mt-2 mb-1">{{ trilha.nome }}</h2>
              <p class="mb-0 text-secondary">
                {{
                  trilha.descricao ||
                  'Trilha publicada para organização dos estudos.'
                }}
              </p>
            </div>
            <i class="bi bi-signpost-2 icone-da-pagina" aria-hidden="true"></i>
          </div>

          <div class="d-flex flex-wrap gap-3 small text-secondary">
            <span>{{ trilha.quantidadeDeDisciplinas }} disciplinas</span>
            <span>{{ trilha.quantidadeDeTarefas }} tarefas</span>
            <span v-if="trilha.aderida">
              {{ trilha.quantidadeDeTarefasConcluidas }} concluídas
            </span>
          </div>
          <BarraDeProgresso
            v-if="trilha.aderida"
            :valor="porcentagemDeConclusao(trilha)"
            :rotulo="`Progresso em ${trilha.nome}`"
          />
          <footer class="mt-auto">
            <RouterLink
              class="btn btn-primary"
              :to="`/trilhas/${trilha.identificador}`"
            >
              {{ trilha.aderida ? 'Continuar trilha' : 'Conhecer trilha' }}
              <i class="bi bi-arrow-right ms-2" aria-hidden="true"></i>
            </RouterLink>
          </footer>
        </div>
      </article>
    </section>
  </main>
</template>
