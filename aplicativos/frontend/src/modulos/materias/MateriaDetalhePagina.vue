<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import ArvoreDeTopicos from './ArvoreDeTopicos.vue'
import {
  alterarTopico,
  arquivarMateria,
  arquivarTopico,
  criarTopico,
  excluirTopico,
  listarTopicos,
  obterMateria,
  type Materia,
  type Topico,
} from './apiDeConteudos'

const rota = useRoute()
const roteador = useRouter()
const identificadorDaMateria = String(rota.params.identificador)
const materia = ref<Materia>()
const topicos = ref<Topico[]>([])
const carregando = ref(true)
const salvando = ref(false)
const erro = ref('')
const identificadorEmEdicao = ref<string>()
const formulario = reactive({
  nome: '',
  descricao: '',
  identificadorDoTopicoPai: '',
  ordem: 1,
})
const cancelamento = new AbortController()

const topicosOrdenados = computed(() =>
  [...topicos.value].sort(
    (primeiro, segundo) =>
      primeiro.ordem - segundo.ordem ||
      primeiro.nome.localeCompare(segundo.nome),
  ),
)

async function carregar() {
  carregando.value = true
  erro.value = ''
  try {
    const [materiaObtida, respostaDeTopicos] = await Promise.all([
      obterMateria(identificadorDaMateria, cancelamento.signal),
      listarTopicos(identificadorDaMateria, true, cancelamento.signal),
    ])
    materia.value = materiaObtida
    topicos.value = respostaDeTopicos.itens
  } catch (causa) {
    if (causa instanceof DOMException && causa.name === 'AbortError') return
    erro.value =
      causa instanceof Error
        ? causa.message
        : 'Nao foi possivel carregar a materia.'
  } finally {
    carregando.value = false
  }
}

function limparFormulario() {
  identificadorEmEdicao.value = undefined
  formulario.nome = ''
  formulario.descricao = ''
  formulario.identificadorDoTopicoPai = ''
  formulario.ordem = Math.max(topicos.value.length + 1, 1)
}

function editar(topico: Topico) {
  identificadorEmEdicao.value = topico.identificador
  formulario.nome = topico.nome
  formulario.descricao = topico.descricao ?? ''
  formulario.identificadorDoTopicoPai = topico.identificadorDoTopicoPai ?? ''
  formulario.ordem = topico.ordem
  document.querySelector('#formulario-topico')?.scrollIntoView({
    behavior: 'smooth',
  })
}

async function salvar() {
  salvando.value = true
  erro.value = ''
  const dados = {
    nome: formulario.nome,
    descricao: formulario.descricao || undefined,
    identificadorDoTopicoPai: formulario.identificadorDoTopicoPai || undefined,
    ordem: Number(formulario.ordem),
  }
  try {
    if (identificadorEmEdicao.value) {
      await alterarTopico(identificadorEmEdicao.value, dados)
    } else {
      await criarTopico(identificadorDaMateria, dados)
    }
    limparFormulario()
    await carregar()
  } catch (causa) {
    erro.value =
      causa instanceof Error ? causa.message : 'Nao foi possivel salvar.'
  } finally {
    salvando.value = false
  }
}

async function alternarArquivamentoDoTopico(topico: Topico) {
  erro.value = ''
  try {
    await arquivarTopico(topico.identificador, !topico.arquivado)
    await carregar()
  } catch (causa) {
    erro.value =
      causa instanceof Error ? causa.message : 'Nao foi possivel arquivar.'
  }
}

async function excluir(topico: Topico) {
  if (!window.confirm(`Excluir o topico "${topico.nome}"?`)) return
  erro.value = ''
  try {
    await excluirTopico(topico.identificador)
    await carregar()
  } catch (causa) {
    erro.value =
      causa instanceof Error ? causa.message : 'Nao foi possivel excluir.'
  }
}

async function alternarArquivamentoDaMateria() {
  if (!materia.value) return
  erro.value = ''
  try {
    materia.value = await arquivarMateria(
      materia.value.identificador,
      !materia.value.arquivada,
    )
  } catch (causa) {
    erro.value =
      causa instanceof Error ? causa.message : 'Nao foi possivel arquivar.'
  }
}

onMounted(async () => {
  limparFormulario()
  await carregar()
})
onBeforeUnmount(() => cancelamento.abort())
</script>

<template>
  <main class="container py-4 py-md-5">
    <button
      class="btn btn-link px-0 mb-3"
      type="button"
      @click="roteador.push('/materias')"
    >
      <i class="bi bi-arrow-left" aria-hidden="true"></i>
      Voltar para materias
    </button>

    <p
      v-if="erro"
      class="alert alert-danger"
      role="alert"
      aria-live="assertive"
    >
      {{ erro }}
    </p>

    <div v-if="carregando" class="text-center py-5" aria-live="polite">
      <div class="spinner-border text-primary mb-3" role="status">
        <span class="visually-hidden">Carregando materia</span>
      </div>
      <p>Carregando materia...</p>
    </div>

    <template v-else-if="materia">
      <header class="card border-0 shadow-sm mb-4">
        <div class="card-body d-flex flex-wrap gap-3 align-items-center">
          <span
            class="marca-de-cor marca-de-cor-grande"
            :style="{ backgroundColor: materia.cor ?? '#6c757d' }"
            aria-hidden="true"
          ></span>
          <div class="flex-grow-1">
            <p class="text-uppercase fw-semibold text-success mb-1">Materia</p>
            <h1 class="mb-1">{{ materia.nome }}</h1>
            <p class="text-secondary mb-0">
              {{ materia.descricao || 'Sem descricao.' }}
            </p>
          </div>
          <button
            class="btn btn-outline-secondary"
            type="button"
            @click="alternarArquivamentoDaMateria"
          >
            {{ materia.arquivada ? 'Restaurar materia' : 'Arquivar materia' }}
          </button>
        </div>
      </header>

      <div class="row g-4">
        <section class="col-lg-8" aria-labelledby="titulo-topicos">
          <div class="card border-0 shadow-sm">
            <div class="card-body">
              <div
                class="d-flex justify-content-between align-items-center mb-3"
              >
                <div>
                  <h2 id="titulo-topicos" class="h4 mb-1">Arvore de topicos</h2>
                  <p class="text-secondary mb-0">
                    {{ topicos.length }} topico{{
                      topicos.length === 1 ? '' : 's'
                    }}
                  </p>
                </div>
              </div>
              <div
                v-if="topicos.length === 0"
                class="text-center bg-body-tertiary rounded-3 p-5"
              >
                <i
                  class="bi bi-diagram-3 fs-1 text-success"
                  aria-hidden="true"
                ></i>
                <h3 class="h5 mt-3">Nenhum topico cadastrado</h3>
                <p class="text-secondary mb-0">
                  Adicione o primeiro topico desta materia.
                </p>
              </div>
              <ArvoreDeTopicos
                v-else
                :topicos="topicosOrdenados"
                @editar="editar"
                @arquivar="alternarArquivamentoDoTopico"
                @excluir="excluir"
              />
            </div>
          </div>

          <div class="row g-3 mt-1">
            <div class="col-md-4">
              <section class="card card-body border-0 shadow-sm h-100">
                <h2 class="h6">Materiais relacionados</h2>
                <p class="small text-secondary mb-0">Disponivel na Sprint 6.</p>
              </section>
            </div>
            <div class="col-md-4">
              <section class="card card-body border-0 shadow-sm h-100">
                <h2 class="h6">Estudos recentes</h2>
                <p class="small text-secondary mb-0">Disponivel na Sprint 6.</p>
              </section>
            </div>
            <div class="col-md-4">
              <section class="card card-body border-0 shadow-sm h-100">
                <h2 class="h6">Concursos relacionados</h2>
                <p class="small text-secondary mb-0">Disponivel na Sprint 4.</p>
              </section>
            </div>
          </div>
        </section>

        <aside class="col-lg-4">
          <form
            id="formulario-topico"
            class="card card-body border-0 shadow-sm sticky-lg-top"
            @submit.prevent="salvar"
          >
            <h2 class="h4">
              {{ identificadorEmEdicao ? 'Editar topico' : 'Novo topico' }}
            </h2>
            <p v-if="materia.arquivada" class="alert alert-warning">
              Restaure a materia para alterar seus topicos.
            </p>
            <fieldset :disabled="materia.arquivada || salvando">
              <label class="form-label" for="nome-topico">Nome</label>
              <input
                id="nome-topico"
                v-model="formulario.nome"
                class="form-control mb-3"
                maxlength="160"
                required
              />
              <label class="form-label" for="descricao-topico">Descricao</label>
              <textarea
                id="descricao-topico"
                v-model="formulario.descricao"
                class="form-control mb-3"
                maxlength="1000"
                rows="3"
              ></textarea>
              <label class="form-label" for="pai-topico">Topico-pai</label>
              <select
                id="pai-topico"
                v-model="formulario.identificadorDoTopicoPai"
                class="form-select mb-3"
              >
                <option value="">Nenhum: topico raiz</option>
                <option
                  v-for="opcao in topicosOrdenados"
                  :key="opcao.identificador"
                  :value="opcao.identificador"
                  :disabled="
                    opcao.identificador === identificadorEmEdicao ||
                    opcao.arquivado
                  "
                >
                  {{ opcao.nome }}
                </option>
              </select>
              <label class="form-label" for="ordem-topico">Ordem</label>
              <input
                id="ordem-topico"
                v-model.number="formulario.ordem"
                class="form-control mb-3"
                type="number"
                min="1"
                required
              />
              <div class="d-flex gap-2">
                <button class="btn btn-primary flex-grow-1">
                  {{ salvando ? 'Salvando...' : 'Salvar topico' }}
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
            </fieldset>
          </form>
        </aside>
      </div>
    </template>
  </main>
</template>
