<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'

import {
  listarMaterias,
  listarTopicos,
  type Materia,
  type Topico,
} from '@/modulos/materias/apiDeConteudos'
import {
  cancelarEstudo,
  corrigirEstudo,
  listarCoberturas,
  listarEstudos,
  listarMateriaisDeEstudo,
  registrarEstudo,
  type CoberturaDeTopico,
  type MaterialDeEstudo,
  type RegistroDeEstudo,
} from './apiDeEstudos'

const registros = ref<RegistroDeEstudo[]>([])
const materias = ref<Materia[]>([])
const topicos = ref<Topico[]>([])
const materiais = ref<MaterialDeEstudo[]>([])
const coberturas = ref<CoberturaDeTopico[]>([])
const carregando = ref(true)
const salvando = ref(false)
const erro = ref('')
const identificadorEmCorrecao = ref<string>()
const formulario = reactive({
  identificadorDaMateria: '',
  identificadorDoTopico: '',
  identificadorDoMaterial: '',
  dataHora: dataHoraLocalAtual(),
  duracaoEmMinutos: 60,
  observacao: '',
})
let cancelamento: AbortController | undefined

const topicosDaMateria = computed(() =>
  topicos.value.filter(
    (topico) =>
      topico.identificadorDaMateria === formulario.identificadorDaMateria,
  ),
)
const materiaisDoTopico = computed(() => {
  const identificadores = new Set(
    coberturas.value
      .filter(
        (cobertura) =>
          cobertura.identificadorDoTopico === formulario.identificadorDoTopico,
      )
      .map((cobertura) => cobertura.identificadorDoMaterial),
  )
  return materiais.value.filter((material) =>
    identificadores.has(material.identificador),
  )
})

function dataHoraLocalAtual() {
  const agora = new Date()
  agora.setMinutes(agora.getMinutes() - agora.getTimezoneOffset())
  return agora.toISOString().slice(0, 16)
}

async function carregar() {
  cancelamento?.abort()
  const requisicao = new AbortController()
  cancelamento = requisicao
  carregando.value = true
  erro.value = ''
  try {
    const [respostaDeEstudos, respostaDeMaterias, respostaDeMateriais] =
      await Promise.all([
        listarEstudos(requisicao.signal),
        listarMaterias('', false, 0, requisicao.signal, 100),
        listarMateriaisDeEstudo('', false, requisicao.signal),
      ])
    registros.value = respostaDeEstudos.itens
    materias.value = respostaDeMaterias.itens
    materiais.value = respostaDeMateriais.itens
    const respostasDeTopicos = await Promise.all(
      materias.value.map((materia) =>
        listarTopicos(materia.identificador, false, requisicao.signal),
      ),
    )
    topicos.value = respostasDeTopicos.flatMap((resposta) => resposta.itens)
    const respostasDeCoberturas = await Promise.all(
      materiais.value.map((material) =>
        listarCoberturas(material.identificador, requisicao.signal),
      ),
    )
    coberturas.value = respostasDeCoberturas.flat()
  } catch (causa) {
    if (causa instanceof DOMException && causa.name === 'AbortError') return
    erro.value =
      causa instanceof Error
        ? causa.message
        : 'Nao foi possivel carregar os estudos.'
  } finally {
    if (cancelamento === requisicao) carregando.value = false
  }
}

function limparFormulario() {
  identificadorEmCorrecao.value = undefined
  Object.assign(formulario, {
    identificadorDaMateria: '',
    identificadorDoTopico: '',
    identificadorDoMaterial: '',
    dataHora: dataHoraLocalAtual(),
    duracaoEmMinutos: 60,
    observacao: '',
  })
}

function ajustarTopico() {
  formulario.identificadorDoTopico = ''
  formulario.identificadorDoMaterial = ''
}

function ajustarMaterial() {
  formulario.identificadorDoMaterial = ''
}

function dadosDoFormulario() {
  return {
    identificadorDoTopico: formulario.identificadorDoTopico,
    identificadorDoMaterial: formulario.identificadorDoMaterial || undefined,
    dataHora: new Date(formulario.dataHora).toISOString(),
    duracaoEmMinutos: formulario.duracaoEmMinutos,
    observacao: formulario.observacao || undefined,
  }
}

async function salvar() {
  salvando.value = true
  erro.value = ''
  try {
    if (identificadorEmCorrecao.value) {
      await corrigirEstudo(identificadorEmCorrecao.value, dadosDoFormulario())
    } else {
      await registrarEstudo(dadosDoFormulario())
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

function corrigir(registro: RegistroDeEstudo) {
  const topico = topicos.value.find(
    (item) => item.identificador === registro.identificadorDoTopico,
  )
  identificadorEmCorrecao.value = registro.identificador
  Object.assign(formulario, {
    identificadorDaMateria: topico?.identificadorDaMateria ?? '',
    identificadorDoTopico: registro.identificadorDoTopico,
    identificadorDoMaterial: registro.identificadorDoMaterial ?? '',
    dataHora: registro.dataHora.slice(0, 16),
    duracaoEmMinutos: registro.duracaoEmMinutos,
    observacao: registro.observacao ?? '',
  })
  document
    .querySelector('#formulario-estudo')
    ?.scrollIntoView({ behavior: 'smooth' })
}

async function cancelar(registro: RegistroDeEstudo) {
  if (!window.confirm('Cancelar este registro de estudo?')) return
  erro.value = ''
  try {
    await cancelarEstudo(registro.identificador)
    await carregar()
  } catch (causa) {
    erro.value =
      causa instanceof Error ? causa.message : 'Nao foi possivel cancelar.'
  }
}

function formatarData(dataHora: string) {
  return new Intl.DateTimeFormat('pt-BR', {
    dateStyle: 'short',
    timeStyle: 'short',
  }).format(new Date(dataHora))
}

onMounted(() => carregar())
onBeforeUnmount(() => cancelamento?.abort())
</script>

<template>
  <main class="container py-4 py-md-5">
    <header class="mb-4">
      <p class="text-uppercase fw-semibold text-success mb-1">
        Historico confiavel
      </p>
      <h1 class="mb-1">Estudos</h1>
      <p class="text-secondary mb-0">
        Registre o tempo dedicado a cada topico e corrija sem perder o
        historico.
      </p>
    </header>

    <p v-if="erro" class="alert alert-danger" role="alert">{{ erro }}</p>

    <div class="row g-4">
      <section class="col-xl-8" aria-labelledby="titulo-historico">
        <h2 id="titulo-historico" class="h4 mb-3">Historico de estudos</h2>
        <div
          v-if="carregando"
          class="card card-body border-0 shadow-sm text-center py-5"
        >
          Carregando estudos...
        </div>
        <div
          v-else-if="registros.length === 0"
          class="card card-body border-0 shadow-sm text-center py-5"
        >
          <h3 class="h4">Nenhum estudo registrado</h3>
          <p class="text-secondary mb-0">
            Use o formulario para registrar sua sessao.
          </p>
        </div>
        <div v-else class="vstack gap-3">
          <article
            v-for="registro in registros"
            :key="registro.identificador"
            class="card border-0 shadow-sm"
          >
            <div
              class="card-body d-flex flex-wrap justify-content-between gap-3"
            >
              <div>
                <div class="d-flex flex-wrap align-items-center gap-2">
                  <h3 class="h5 mb-0">{{ registro.nomeDoTopico }}</h3>
                  <span
                    class="badge"
                    :class="
                      registro.situacao === 'ATIVO'
                        ? 'text-bg-success'
                        : 'text-bg-secondary'
                    "
                  >
                    {{ registro.situacao }}
                  </span>
                </div>
                <p class="mb-1 mt-2">
                  {{ formatarData(registro.dataHora) }} ·
                  {{ registro.duracaoEmMinutos }} minutos
                </p>
                <p v-if="registro.tituloDoMaterial" class="text-secondary mb-1">
                  Material: {{ registro.tituloDoMaterial }}
                </p>
                <p v-if="registro.observacao" class="text-secondary mb-0">
                  {{ registro.observacao }}
                </p>
              </div>
              <div
                v-if="registro.situacao === 'ATIVO'"
                class="d-flex gap-2 align-self-start"
              >
                <button
                  class="btn btn-outline-primary btn-sm"
                  type="button"
                  @click="corrigir(registro)"
                >
                  Corrigir
                </button>
                <button
                  class="btn btn-outline-danger btn-sm"
                  type="button"
                  @click="cancelar(registro)"
                >
                  Cancelar
                </button>
              </div>
            </div>
          </article>
        </div>
      </section>

      <aside class="col-xl-4">
        <form
          id="formulario-estudo"
          class="card card-body border-0 shadow-sm position-sticky formulario-lateral"
          @submit.prevent="salvar"
        >
          <h2 class="h4">
            {{
              identificadorEmCorrecao ? 'Corrigir estudo' : 'Registrar estudo'
            }}
          </h2>
          <label class="form-label" for="materia-estudo">Materia</label>
          <select
            id="materia-estudo"
            v-model="formulario.identificadorDaMateria"
            class="form-select mb-3"
            required
            @change="ajustarTopico"
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
          <label class="form-label" for="topico-estudo">Topico</label>
          <select
            id="topico-estudo"
            v-model="formulario.identificadorDoTopico"
            class="form-select mb-3"
            required
            @change="ajustarMaterial"
          >
            <option value="">Selecione</option>
            <option
              v-for="topico in topicosDaMateria"
              :key="topico.identificador"
              :value="topico.identificador"
            >
              {{ topico.nome }}
            </option>
          </select>
          <label class="form-label" for="material-estudo"
            >Material (opcional)</label
          >
          <select
            id="material-estudo"
            v-model="formulario.identificadorDoMaterial"
            class="form-select mb-3"
          >
            <option value="">Sem material</option>
            <option
              v-for="material in materiaisDoTopico"
              :key="material.identificador"
              :value="material.identificador"
            >
              {{ material.titulo }}
            </option>
          </select>
          <label class="form-label" for="data-estudo">Data e hora</label>
          <input
            id="data-estudo"
            v-model="formulario.dataHora"
            class="form-control mb-3"
            type="datetime-local"
            required
          />
          <label class="form-label" for="duracao-estudo"
            >Duracao (minutos)</label
          >
          <input
            id="duracao-estudo"
            v-model.number="formulario.duracaoEmMinutos"
            class="form-control mb-3"
            type="number"
            min="1"
            max="1440"
            required
          />
          <label class="form-label" for="observacao-estudo">Observacao</label>
          <textarea
            id="observacao-estudo"
            v-model="formulario.observacao"
            class="form-control mb-3"
            rows="3"
          ></textarea>
          <div class="d-flex gap-2">
            <button
              class="btn btn-primary flex-grow-1"
              type="submit"
              :disabled="salvando"
            >
              {{ salvando ? 'Salvando...' : 'Salvar' }}
            </button>
            <button
              v-if="identificadorEmCorrecao"
              class="btn btn-outline-secondary"
              type="button"
              @click="limparFormulario"
            >
              Desistir
            </button>
          </div>
        </form>
      </aside>
    </div>
  </main>
</template>
