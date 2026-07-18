<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'

import {
  ativarConcurso,
  arquivarConcurso,
  excluirConcurso,
  listarConcursos,
  type Concurso,
} from './apiDeConcursos'

const concursos = ref<Concurso[]>([])
const pesquisa = ref('')
const incluirArquivados = ref(false)
const carregando = ref(true)
const erro = ref('')
const pagina = ref(0)
const totalDePaginas = ref(0)
const totalDeItens = ref(0)
let cancelamento: AbortController | undefined

const rotulosDeSituacao: Record<string, string> = {
  PLANEJADO: 'Planejado',
  EDITAL_PUBLICADO: 'Edital publicado',
  INSCRICOES_ABERTAS: 'Inscricoes abertas',
  EM_ANDAMENTO: 'Em andamento',
  ENCERRADO: 'Encerrado',
  SUSPENSO: 'Suspenso',
  CANCELADO: 'Cancelado',
  ARQUIVADO: 'Arquivado',
}

async function carregar(novaPagina = pagina.value) {
  cancelamento?.abort()
  const requisicaoAtual = new AbortController()
  cancelamento = requisicaoAtual
  carregando.value = true
  erro.value = ''
  try {
    const resposta = await listarConcursos(
      pesquisa.value,
      incluirArquivados.value,
      novaPagina,
      requisicaoAtual.signal,
    )
    concursos.value = resposta.itens
    pagina.value = resposta.pagina
    totalDePaginas.value = resposta.totalDePaginas
    totalDeItens.value = resposta.totalDeItens
  } catch (causa) {
    if (causa instanceof DOMException && causa.name === 'AbortError') return
    erro.value =
      causa instanceof Error
        ? causa.message
        : 'Nao foi possivel carregar os concursos.'
  } finally {
    if (cancelamento === requisicaoAtual) carregando.value = false
  }
}

async function ativar(concurso: Concurso) {
  erro.value = ''
  try {
    await ativarConcurso(concurso.identificador)
    await carregar(pagina.value)
  } catch (causa) {
    erro.value =
      causa instanceof Error ? causa.message : 'Nao foi possivel ativar.'
  }
}

async function alternarArquivamento(concurso: Concurso) {
  erro.value = ''
  try {
    await arquivarConcurso(
      concurso.identificador,
      concurso.situacao !== 'ARQUIVADO',
    )
    await carregar(pagina.value)
  } catch (causa) {
    erro.value =
      causa instanceof Error ? causa.message : 'Nao foi possivel arquivar.'
  }
}

async function excluir(concurso: Concurso) {
  if (!window.confirm(`Excluir o concurso "${concurso.nome}"?`)) return
  erro.value = ''
  try {
    await excluirConcurso(concurso.identificador)
    await carregar(pagina.value)
  } catch (causa) {
    erro.value =
      causa instanceof Error ? causa.message : 'Nao foi possivel excluir.'
  }
}

onMounted(() => carregar())
onBeforeUnmount(() => cancelamento?.abort())
</script>

<template>
  <main class="container py-4 py-md-5">
    <header class="d-flex flex-wrap justify-content-between gap-3 mb-4">
      <div>
        <p class="text-uppercase fw-semibold text-success mb-1">
          Estrutura de estudos
        </p>
        <h1 class="mb-1">Concursos</h1>
        <p class="text-secondary mb-0">
          Construa cada concurso gradualmente, do edital as materias.
        </p>
      </div>
      <RouterLink class="btn btn-primary align-self-start" to="/concursos/novo">
        <i class="bi bi-plus-lg me-1" aria-hidden="true"></i>
        Novo concurso
      </RouterLink>
    </header>

    <p
      v-if="erro"
      class="alert alert-danger"
      role="alert"
      aria-live="assertive"
    >
      {{ erro }}
    </p>

    <form
      class="card card-body border-0 shadow-sm mb-4"
      @submit.prevent="carregar(0)"
    >
      <div class="row g-2 align-items-end">
        <div class="col-md">
          <label class="form-label" for="pesquisa-concurso">Pesquisar</label>
          <input
            id="pesquisa-concurso"
            v-model="pesquisa"
            class="form-control"
            placeholder="Nome do concurso"
          />
        </div>
        <div class="col-md-auto">
          <div class="form-check mb-2">
            <input
              id="incluir-concursos-arquivados"
              v-model="incluirArquivados"
              class="form-check-input"
              type="checkbox"
              @change="carregar(0)"
            />
            <label class="form-check-label" for="incluir-concursos-arquivados">
              Incluir arquivados
            </label>
          </div>
        </div>
        <div class="col-md-auto">
          <button class="btn btn-outline-primary w-100">Buscar</button>
        </div>
      </div>
    </form>

    <div v-if="carregando" class="text-center py-5" aria-live="polite">
      <div class="spinner-border text-primary mb-3" role="status">
        <span class="visually-hidden">Carregando concursos</span>
      </div>
      <p>Carregando concursos...</p>
    </div>

    <section
      v-else-if="concursos.length === 0"
      class="card card-body border-0 shadow-sm text-center py-5"
    >
      <i class="bi bi-trophy fs-1 text-success" aria-hidden="true"></i>
      <h2 class="h4 mt-3">Nenhum concurso encontrado</h2>
      <p class="text-secondary">
        Cadastre os dados gerais agora e complete a estrutura aos poucos.
      </p>
      <RouterLink class="btn btn-primary mx-auto" to="/concursos/novo">
        Adicionar meu primeiro concurso
      </RouterLink>
    </section>

    <section v-else class="row g-3" aria-label="Lista de concursos">
      <article
        v-for="concurso in concursos"
        :key="concurso.identificador"
        class="col-md-6 col-xl-4"
      >
        <div class="card border-0 shadow-sm h-100">
          <div class="card-body d-flex flex-column">
            <div class="d-flex justify-content-between gap-2">
              <span class="badge text-bg-light">
                {{ rotulosDeSituacao[concurso.situacao] }}
              </span>
              <span v-if="concurso.ativo" class="badge text-bg-success">
                Ativo
              </span>
            </div>
            <h2 class="h5 mt-3 mb-1">{{ concurso.nome }}</h2>
            <p class="small text-secondary mb-1">
              {{ concurso.orgao || 'Orgao nao informado' }}
              <span v-if="concurso.banca"> · {{ concurso.banca }}</span>
            </p>
            <p class="text-secondary flex-grow-1">
              {{ concurso.descricao || 'Sem descricao.' }}
            </p>
            <div class="d-flex flex-wrap gap-2">
              <RouterLink
                class="btn btn-primary btn-sm"
                :to="`/concursos/${concurso.identificador}`"
              >
                Abrir estrutura
              </RouterLink>
              <button
                v-if="concurso.situacao !== 'ARQUIVADO'"
                class="btn btn-outline-success btn-sm"
                type="button"
                :disabled="concurso.ativo"
                @click="ativar(concurso)"
              >
                {{ concurso.ativo ? 'Concurso ativo' : 'Ativar' }}
              </button>
              <button
                class="btn btn-outline-secondary btn-sm"
                type="button"
                @click="alternarArquivamento(concurso)"
              >
                {{
                  concurso.situacao === 'ARQUIVADO' ? 'Restaurar' : 'Arquivar'
                }}
              </button>
              <button
                class="btn btn-outline-danger btn-sm"
                type="button"
                @click="excluir(concurso)"
              >
                Excluir
              </button>
            </div>
          </div>
        </div>
      </article>
    </section>

    <nav
      v-if="totalDePaginas > 1"
      class="d-flex justify-content-between align-items-center mt-4"
      aria-label="Paginacao de concursos"
    >
      <button
        class="btn btn-outline-primary"
        :disabled="pagina === 0"
        @click="carregar(pagina - 1)"
      >
        Anterior
      </button>
      <span>
        Pagina {{ pagina + 1 }} de {{ totalDePaginas }} ·
        {{ totalDeItens }} concursos
      </span>
      <button
        class="btn btn-outline-primary"
        :disabled="pagina + 1 >= totalDePaginas"
        @click="carregar(pagina + 1)"
      >
        Proxima
      </button>
    </nav>
  </main>
</template>
