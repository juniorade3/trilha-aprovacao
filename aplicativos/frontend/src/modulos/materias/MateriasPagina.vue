<script setup lang="ts">
import { onBeforeUnmount, onMounted, reactive, ref } from 'vue'

import {
  alterarMateria,
  arquivarMateria,
  criarMateria,
  excluirMateria,
  listarMaterias,
  type Materia,
} from './apiDeConteudos'

const materias = ref<Materia[]>([])
const pesquisa = ref('')
const incluirArquivadas = ref(false)
const carregando = ref(true)
const salvando = ref(false)
const erro = ref('')
const pagina = ref(0)
const totalDePaginas = ref(0)
const totalDeItens = ref(0)
const identificadorEmEdicao = ref<string>()
const formulario = reactive({ nome: '', descricao: '', cor: '#0E8F87' })
let cancelamento: AbortController | undefined

async function carregar(novaPagina = pagina.value) {
  cancelamento?.abort()
  const requisicaoAtual = new AbortController()
  cancelamento = requisicaoAtual
  carregando.value = true
  erro.value = ''
  try {
    const resposta = await listarMaterias(
      pesquisa.value,
      incluirArquivadas.value,
      novaPagina,
      requisicaoAtual.signal,
    )
    materias.value = resposta.itens
    pagina.value = resposta.pagina
    totalDePaginas.value = resposta.totalDePaginas
    totalDeItens.value = resposta.totalDeItens
  } catch (causa) {
    if (causa instanceof DOMException && causa.name === 'AbortError') return
    erro.value =
      causa instanceof Error
        ? causa.message
        : 'Nao foi possivel carregar as materias.'
  } finally {
    if (cancelamento === requisicaoAtual) {
      carregando.value = false
    }
  }
}

function limparFormulario() {
  identificadorEmEdicao.value = undefined
  formulario.nome = ''
  formulario.descricao = ''
  formulario.cor = '#0E8F87'
}

function editar(materia: Materia) {
  identificadorEmEdicao.value = materia.identificador
  formulario.nome = materia.nome
  formulario.descricao = materia.descricao ?? ''
  formulario.cor = materia.cor ?? ''
  document.querySelector('#formulario-materia')?.scrollIntoView({
    behavior: 'smooth',
  })
}

async function salvar() {
  salvando.value = true
  erro.value = ''
  const dados = {
    nome: formulario.nome,
    descricao: formulario.descricao || undefined,
    cor: formulario.cor || undefined,
  }
  try {
    if (identificadorEmEdicao.value) {
      await alterarMateria(identificadorEmEdicao.value, dados)
    } else {
      await criarMateria(dados)
    }
    limparFormulario()
    await carregar(0)
  } catch (causa) {
    erro.value =
      causa instanceof Error ? causa.message : 'Nao foi possivel salvar.'
  } finally {
    salvando.value = false
  }
}

async function alternarArquivamento(materia: Materia) {
  erro.value = ''
  try {
    await arquivarMateria(materia.identificador, !materia.arquivada)
    await carregar(pagina.value)
  } catch (causa) {
    erro.value =
      causa instanceof Error ? causa.message : 'Nao foi possivel arquivar.'
  }
}

async function excluir(materia: Materia) {
  if (!window.confirm(`Excluir a materia "${materia.nome}"?`)) return
  erro.value = ''
  try {
    await excluirMateria(materia.identificador)
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
          Catalogo pessoal
        </p>
        <h1 class="mb-1">Materias</h1>
        <p class="text-secondary mb-0">
          Organize conteudos reutilizaveis entre seus concursos.
        </p>
      </div>
      <span class="badge text-bg-light align-self-start fs-6">
        {{ totalDeItens }} materia{{ totalDeItens === 1 ? '' : 's' }}
      </span>
    </header>

    <p
      v-if="erro"
      class="alert alert-danger"
      role="alert"
      aria-live="assertive"
    >
      {{ erro }}
    </p>

    <div class="row g-4">
      <section class="col-lg-8" aria-labelledby="titulo-lista-materias">
        <h2 id="titulo-lista-materias" class="visually-hidden">
          Lista de materias
        </h2>
        <form
          class="card card-body border-0 shadow-sm mb-3"
          @submit.prevent="carregar(0)"
        >
          <div class="row g-2 align-items-end">
            <div class="col-md">
              <label class="form-label" for="pesquisa">Pesquisar</label>
              <input
                id="pesquisa"
                v-model="pesquisa"
                class="form-control"
                placeholder="Nome da materia"
              />
            </div>
            <div class="col-md-auto">
              <div class="form-check mb-2">
                <input
                  id="incluir-arquivadas"
                  v-model="incluirArquivadas"
                  class="form-check-input"
                  type="checkbox"
                  @change="carregar(0)"
                />
                <label class="form-check-label" for="incluir-arquivadas">
                  Incluir arquivadas
                </label>
              </div>
            </div>
            <div class="col-md-auto">
              <button class="btn btn-outline-primary w-100" type="submit">
                Buscar
              </button>
            </div>
          </div>
        </form>

        <div
          v-if="carregando"
          class="card card-body border-0 shadow-sm text-center py-5"
          aria-live="polite"
        >
          <div class="spinner-border text-primary mx-auto mb-3" role="status">
            <span class="visually-hidden">Carregando materias</span>
          </div>
          Carregando materias...
        </div>

        <div
          v-else-if="materias.length === 0"
          class="card card-body border-0 shadow-sm text-center py-5"
        >
          <i
            class="bi bi-journal-plus fs-1 text-success"
            aria-hidden="true"
          ></i>
          <h2 class="h4 mt-3">Nenhuma materia encontrada</h2>
          <p class="text-secondary mb-0">
            Cadastre sua primeira materia usando o formulario.
          </p>
        </div>

        <div v-else class="vstack gap-3">
          <article
            v-for="materia in materias"
            :key="materia.identificador"
            class="card border-0 shadow-sm"
          >
            <div class="card-body d-flex flex-wrap gap-3 align-items-start">
              <span
                class="marca-de-cor"
                :style="{ backgroundColor: materia.cor ?? '#6c757d' }"
                aria-hidden="true"
              ></span>
              <div class="flex-grow-1">
                <div class="d-flex align-items-center gap-2">
                  <h2 class="h5 mb-1">{{ materia.nome }}</h2>
                  <span
                    v-if="materia.arquivada"
                    class="badge text-bg-secondary"
                  >
                    Arquivada
                  </span>
                </div>
                <p class="text-secondary mb-0">
                  {{ materia.descricao || 'Sem descricao.' }}
                </p>
              </div>
              <div class="d-flex flex-wrap gap-2">
                <RouterLink
                  class="btn btn-primary btn-sm"
                  :to="`/materias/${materia.identificador}`"
                >
                  Abrir
                </RouterLink>
                <button
                  class="btn btn-outline-primary btn-sm"
                  type="button"
                  :disabled="materia.arquivada"
                  @click="editar(materia)"
                >
                  Editar
                </button>
                <button
                  class="btn btn-outline-secondary btn-sm"
                  type="button"
                  @click="alternarArquivamento(materia)"
                >
                  {{ materia.arquivada ? 'Restaurar' : 'Arquivar' }}
                </button>
                <button
                  class="btn btn-outline-danger btn-sm"
                  type="button"
                  @click="excluir(materia)"
                >
                  Excluir
                </button>
              </div>
            </div>
          </article>
        </div>

        <nav
          v-if="totalDePaginas > 1"
          class="d-flex justify-content-between align-items-center mt-3"
          aria-label="Paginacao de materias"
        >
          <button
            class="btn btn-outline-primary"
            type="button"
            :disabled="pagina === 0"
            @click="carregar(pagina - 1)"
          >
            Anterior
          </button>
          <span>Pagina {{ pagina + 1 }} de {{ totalDePaginas }}</span>
          <button
            class="btn btn-outline-primary"
            type="button"
            :disabled="pagina + 1 >= totalDePaginas"
            @click="carregar(pagina + 1)"
          >
            Proxima
          </button>
        </nav>
      </section>

      <aside class="col-lg-4">
        <form
          id="formulario-materia"
          class="card card-body border-0 shadow-sm sticky-lg-top"
          @submit.prevent="salvar"
        >
          <h2 class="h4">
            {{ identificadorEmEdicao ? 'Editar materia' : 'Nova materia' }}
          </h2>
          <label class="form-label" for="nome-materia">Nome</label>
          <input
            id="nome-materia"
            v-model="formulario.nome"
            class="form-control mb-3"
            maxlength="120"
            required
          />
          <label class="form-label" for="descricao-materia">Descricao</label>
          <textarea
            id="descricao-materia"
            v-model="formulario.descricao"
            class="form-control mb-3"
            maxlength="1000"
            rows="4"
          ></textarea>
          <label class="form-label" for="cor-materia">Cor</label>
          <input
            id="cor-materia"
            v-model="formulario.cor"
            class="form-control form-control-color mb-3"
            type="color"
            title="Escolha a cor da materia"
          />
          <div class="d-flex gap-2">
            <button class="btn btn-primary flex-grow-1" :disabled="salvando">
              {{ salvando ? 'Salvando...' : 'Salvar' }}
            </button>
            <button
              v-if="identificadorEmEdicao"
              class="btn btn-outline-secondary"
              type="button"
              @click="limparFormulario"
            >
              Cancelar
            </button>
          </div>
        </form>
      </aside>
    </div>
  </main>
</template>
