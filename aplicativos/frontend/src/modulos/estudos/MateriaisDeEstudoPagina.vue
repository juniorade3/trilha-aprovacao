<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'

import {
  listarMaterias,
  listarTopicos,
  type Materia,
  type Topico,
} from '@/modulos/materias/apiDeConteudos'
import {
  adicionarCobertura,
  alterarMaterialDeEstudo,
  criarMaterialDeEstudo,
  definirArquivamentoDoMaterial,
  excluirMaterialDeEstudo,
  listarCoberturas,
  listarMateriaisDeEstudo,
  removerCobertura,
  type CoberturaDeTopico,
  type MaterialDeEstudo,
  type TipoDeMaterial,
} from './apiDeEstudos'

const materiais = ref<MaterialDeEstudo[]>([])
const materias = ref<Materia[]>([])
const topicos = ref<Topico[]>([])
const coberturas = ref<CoberturaDeTopico[]>([])
const pesquisa = ref('')
const incluirArquivados = ref(false)
const carregando = ref(true)
const salvando = ref(false)
const erro = ref('')
const identificadorEmEdicao = ref<string>()
const materialSelecionado = ref<MaterialDeEstudo>()
const identificadorDaMateria = ref('')
const identificadorDoTopico = ref('')
const formulario = reactive({
  titulo: '',
  tipo: 'AULA' as TipoDeMaterial,
  descricao: '',
  fonte: '',
  endereco: '',
  duracaoEstimadaEmMinutos: undefined as number | undefined,
})
let cancelamento: AbortController | undefined

const topicosDisponiveis = computed(() => {
  const vinculados = new Set(
    coberturas.value.map((item) => item.identificadorDoTopico),
  )
  return topicos.value.filter((topico) => !vinculados.has(topico.identificador))
})

async function carregar() {
  cancelamento?.abort()
  const requisicao = new AbortController()
  cancelamento = requisicao
  carregando.value = true
  erro.value = ''
  try {
    const [respostaDeMateriais, respostaDeMaterias] = await Promise.all([
      listarMateriaisDeEstudo(
        pesquisa.value,
        incluirArquivados.value,
        requisicao.signal,
      ),
      listarMaterias('', false, 0, requisicao.signal, 100),
    ])
    materiais.value = respostaDeMateriais.itens
    materias.value = respostaDeMaterias.itens
    if (materialSelecionado.value) {
      materialSelecionado.value = materiais.value.find(
        (item) =>
          item.identificador === materialSelecionado.value?.identificador,
      )
      if (materialSelecionado.value) await carregarCoberturas()
    }
  } catch (causa) {
    if (causa instanceof DOMException && causa.name === 'AbortError') return
    erro.value =
      causa instanceof Error
        ? causa.message
        : 'Nao foi possivel carregar os materiais.'
  } finally {
    if (cancelamento === requisicao) carregando.value = false
  }
}

function limparFormulario() {
  identificadorEmEdicao.value = undefined
  Object.assign(formulario, {
    titulo: '',
    tipo: 'AULA',
    descricao: '',
    fonte: '',
    endereco: '',
    duracaoEstimadaEmMinutos: undefined,
  })
}

function editar(material: MaterialDeEstudo) {
  identificadorEmEdicao.value = material.identificador
  Object.assign(formulario, {
    titulo: material.titulo,
    tipo: material.tipo,
    descricao: material.descricao ?? '',
    fonte: material.fonte ?? '',
    endereco: material.endereco ?? '',
    duracaoEstimadaEmMinutos: material.duracaoEstimadaEmMinutos,
  })
  document
    .querySelector('#formulario-material')
    ?.scrollIntoView({ behavior: 'smooth' })
}

async function salvar() {
  salvando.value = true
  erro.value = ''
  const dados = {
    titulo: formulario.titulo,
    tipo: formulario.tipo,
    descricao: formulario.descricao || undefined,
    fonte: formulario.fonte || undefined,
    endereco: formulario.endereco || undefined,
    duracaoEstimadaEmMinutos: formulario.duracaoEstimadaEmMinutos || undefined,
  }
  try {
    if (identificadorEmEdicao.value) {
      await alterarMaterialDeEstudo(identificadorEmEdicao.value, dados)
    } else {
      await criarMaterialDeEstudo(dados)
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

async function alternarArquivamento(material: MaterialDeEstudo) {
  erro.value = ''
  try {
    await definirArquivamentoDoMaterial(
      material.identificador,
      !material.arquivado,
    )
    await carregar()
  } catch (causa) {
    erro.value =
      causa instanceof Error ? causa.message : 'Nao foi possivel arquivar.'
  }
}

async function excluir(material: MaterialDeEstudo) {
  if (!window.confirm(`Excluir o material "${material.titulo}"?`)) return
  erro.value = ''
  try {
    await excluirMaterialDeEstudo(material.identificador)
    if (materialSelecionado.value?.identificador === material.identificador) {
      materialSelecionado.value = undefined
      coberturas.value = []
    }
    await carregar()
  } catch (causa) {
    erro.value =
      causa instanceof Error ? causa.message : 'Nao foi possivel excluir.'
  }
}

async function selecionarMaterial(material: MaterialDeEstudo) {
  materialSelecionado.value = material
  identificadorDaMateria.value = ''
  identificadorDoTopico.value = ''
  topicos.value = []
  await carregarCoberturas()
}

async function carregarCoberturas() {
  if (!materialSelecionado.value) return
  coberturas.value = await listarCoberturas(
    materialSelecionado.value.identificador,
  )
}

async function carregarTopicos() {
  identificadorDoTopico.value = ''
  topicos.value = []
  if (!identificadorDaMateria.value) return
  try {
    const resposta = await listarTopicos(identificadorDaMateria.value, false)
    topicos.value = resposta.itens
  } catch (causa) {
    erro.value =
      causa instanceof Error
        ? causa.message
        : 'Nao foi possivel listar topicos.'
  }
}

async function vincularTopico() {
  if (!materialSelecionado.value || !identificadorDoTopico.value) return
  erro.value = ''
  try {
    await adicionarCobertura(
      materialSelecionado.value.identificador,
      identificadorDoTopico.value,
    )
    identificadorDoTopico.value = ''
    await carregarCoberturas()
  } catch (causa) {
    erro.value =
      causa instanceof Error ? causa.message : 'Nao foi possivel vincular.'
  }
}

async function desvincularTopico(cobertura: CoberturaDeTopico) {
  if (!materialSelecionado.value) return
  erro.value = ''
  try {
    await removerCobertura(
      materialSelecionado.value.identificador,
      cobertura.identificadorDoTopico,
    )
    await carregarCoberturas()
  } catch (causa) {
    erro.value =
      causa instanceof Error ? causa.message : 'Nao foi possivel desvincular.'
  }
}

onMounted(() => carregar())
onBeforeUnmount(() => cancelamento?.abort())
</script>

<template>
  <main class="container py-4 py-md-5">
    <header class="mb-4">
      <p class="text-uppercase fw-semibold text-success mb-1">
        Biblioteca pessoal
      </p>
      <h1 class="mb-1">Materiais de estudo</h1>
      <p class="text-secondary mb-0">
        Cadastre aulas, PDFs e outras fontes e indique os topicos que cada
        material cobre.
      </p>
    </header>

    <p v-if="erro" class="alert alert-danger" role="alert">{{ erro }}</p>

    <div class="row g-4">
      <section class="col-xl-8">
        <form
          class="card card-body border-0 shadow-sm mb-3"
          @submit.prevent="carregar"
        >
          <div class="row g-2 align-items-end">
            <div class="col-md">
              <label class="form-label" for="pesquisa-material"
                >Pesquisar</label
              >
              <input
                id="pesquisa-material"
                v-model="pesquisa"
                class="form-control"
                placeholder="Titulo do material"
              />
            </div>
            <div class="col-md-auto">
              <div class="form-check mb-2">
                <input
                  id="materiais-arquivados"
                  v-model="incluirArquivados"
                  class="form-check-input"
                  type="checkbox"
                  @change="carregar"
                />
                <label class="form-check-label" for="materiais-arquivados">
                  Incluir arquivados
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
        >
          Carregando materiais...
        </div>
        <div
          v-else-if="materiais.length === 0"
          class="card card-body border-0 shadow-sm text-center py-5"
        >
          <h2 class="h4">Nenhum material encontrado</h2>
          <p class="text-secondary mb-0">
            Cadastre o primeiro material ao lado.
          </p>
        </div>
        <div v-else class="vstack gap-3">
          <article
            v-for="material in materiais"
            :key="material.identificador"
            class="card border-0 shadow-sm"
          >
            <div class="card-body">
              <div class="d-flex flex-wrap justify-content-between gap-3">
                <div>
                  <div class="d-flex align-items-center gap-2">
                    <h2 class="h5 mb-1">{{ material.titulo }}</h2>
                    <span class="badge text-bg-light">{{ material.tipo }}</span>
                    <span
                      v-if="material.arquivado"
                      class="badge text-bg-secondary"
                    >
                      Arquivado
                    </span>
                  </div>
                  <p class="text-secondary mb-1">
                    {{ material.descricao || 'Sem descricao.' }}
                  </p>
                  <a
                    v-if="material.endereco"
                    :href="material.endereco"
                    target="_blank"
                    rel="noreferrer"
                  >
                    Abrir material
                  </a>
                </div>
                <div class="d-flex flex-wrap gap-2 align-self-start">
                  <button
                    class="btn btn-primary btn-sm"
                    type="button"
                    :disabled="material.arquivado"
                    @click="selecionarMaterial(material)"
                  >
                    Cobertura
                  </button>
                  <button
                    class="btn btn-outline-primary btn-sm"
                    type="button"
                    :disabled="material.arquivado"
                    @click="editar(material)"
                  >
                    Editar
                  </button>
                  <button
                    class="btn btn-outline-secondary btn-sm"
                    type="button"
                    @click="alternarArquivamento(material)"
                  >
                    {{ material.arquivado ? 'Reativar' : 'Arquivar' }}
                  </button>
                  <button
                    class="btn btn-outline-danger btn-sm"
                    type="button"
                    @click="excluir(material)"
                  >
                    Excluir
                  </button>
                </div>
              </div>
            </div>
          </article>
        </div>

        <section
          v-if="materialSelecionado"
          class="card border-0 shadow-sm mt-4"
          aria-labelledby="titulo-cobertura"
        >
          <div class="card-body">
            <h2 id="titulo-cobertura" class="h4">
              Cobertura de {{ materialSelecionado.titulo }}
            </h2>
            <form
              class="row g-2 align-items-end mb-3"
              @submit.prevent="vincularTopico"
            >
              <div class="col-md-5">
                <label class="form-label" for="materia-cobertura"
                  >Materia</label
                >
                <select
                  id="materia-cobertura"
                  v-model="identificadorDaMateria"
                  class="form-select"
                  required
                  @change="carregarTopicos"
                >
                  <option value="">Selecione</option>
                  <option
                    v-for="materia in materias"
                    :key="materia.identificador"
                    :value="materia.identificador"
                  >
                    {{ materia.nome }}
                  </option>
                </select>
              </div>
              <div class="col-md-5">
                <label class="form-label" for="topico-cobertura">Topico</label>
                <select
                  id="topico-cobertura"
                  v-model="identificadorDoTopico"
                  class="form-select"
                  required
                >
                  <option value="">Selecione</option>
                  <option
                    v-for="topico in topicosDisponiveis"
                    :key="topico.identificador"
                    :value="topico.identificador"
                  >
                    {{ topico.nome }}
                  </option>
                </select>
              </div>
              <div class="col-md-2">
                <button class="btn btn-success w-100" type="submit">
                  Vincular
                </button>
              </div>
            </form>
            <p v-if="coberturas.length === 0" class="text-secondary mb-0">
              Nenhum topico vinculado.
            </p>
            <ul v-else class="list-group list-group-flush">
              <li
                v-for="cobertura in coberturas"
                :key="cobertura.identificador"
                class="list-group-item px-0 d-flex justify-content-between align-items-center"
              >
                {{ cobertura.nomeDoTopico }}
                <button
                  class="btn btn-outline-danger btn-sm"
                  type="button"
                  @click="desvincularTopico(cobertura)"
                >
                  Remover
                </button>
              </li>
            </ul>
          </div>
        </section>
      </section>

      <aside class="col-xl-4">
        <form
          id="formulario-material"
          class="card card-body border-0 shadow-sm position-sticky formulario-lateral"
          @submit.prevent="salvar"
        >
          <h2 class="h4">
            {{ identificadorEmEdicao ? 'Editar material' : 'Novo material' }}
          </h2>
          <label class="form-label" for="titulo-material">Titulo</label>
          <input
            id="titulo-material"
            v-model="formulario.titulo"
            class="form-control mb-3"
            required
          />
          <label class="form-label" for="tipo-material">Tipo</label>
          <select
            id="tipo-material"
            v-model="formulario.tipo"
            class="form-select mb-3"
          >
            <option value="AULA">Aula</option>
            <option value="PDF">PDF</option>
            <option value="OUTRO">Outro</option>
          </select>
          <label class="form-label" for="descricao-material">Descricao</label>
          <textarea
            id="descricao-material"
            v-model="formulario.descricao"
            class="form-control mb-3"
            rows="3"
          ></textarea>
          <label class="form-label" for="fonte-material">Fonte</label>
          <input
            id="fonte-material"
            v-model="formulario.fonte"
            class="form-control mb-3"
          />
          <label class="form-label" for="endereco-material">Endereco web</label>
          <input
            id="endereco-material"
            v-model="formulario.endereco"
            class="form-control mb-3"
            type="url"
          />
          <label class="form-label" for="duracao-material"
            >Duracao estimada (minutos)</label
          >
          <input
            id="duracao-material"
            v-model.number="formulario.duracaoEstimadaEmMinutos"
            class="form-control mb-3"
            type="number"
            min="1"
          />
          <div class="d-flex gap-2">
            <button
              class="btn btn-primary flex-grow-1"
              type="submit"
              :disabled="salvando"
            >
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
