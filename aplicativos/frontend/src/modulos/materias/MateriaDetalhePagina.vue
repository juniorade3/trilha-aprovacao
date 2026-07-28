<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import ArvoreDeTopicos from './ArvoreDeTopicos.vue'
import {
  alterarTopico,
  arquivarMateria,
  arquivarTopico,
  consultarUsoDaMateria,
  criarTopico,
  excluirTopico,
  listarTopicos,
  obterMateria,
  type Materia,
  type Topico,
  type UsoDaMateria,
} from './apiDeConteudos'

const rota = useRoute()
const roteador = useRouter()
const identificadorDaMateria = String(rota.params.identificador)
const materia = ref<Materia>()
const topicos = ref<Topico[]>([])
const uso = ref<UsoDaMateria>({
  materiais: [],
  estudosRecentes: [],
  concursos: [],
})
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
    const [materiaObtida, respostaDeTopicos, usoObtido] = await Promise.all([
      obterMateria(identificadorDaMateria, cancelamento.signal),
      listarTopicos(identificadorDaMateria, true, cancelamento.signal),
      consultarUsoDaMateria(identificadorDaMateria, cancelamento.signal),
    ])
    materia.value = materiaObtida
    topicos.value = respostaDeTopicos.itens
    uso.value = usoObtido
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

function dataHoraLegivel(valor: string) {
  return new Intl.DateTimeFormat('pt-BR', {
    dateStyle: 'short',
    timeStyle: 'short',
  }).format(new Date(valor))
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
  <main class="pagina-da-jornada pagina-de-detalhe-da-materia">
    <button
      class="btn btn-link botao-de-voltar-do-catalogo"
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

    <div
      v-if="carregando"
      class="estado-do-catalogo carregamento-do-detalhe-da-materia"
      aria-live="polite"
    >
      <div class="spinner-border text-primary mb-3" role="status">
        <span class="visually-hidden">Carregando materia</span>
      </div>
      <p>Carregando materia...</p>
    </div>

    <template v-else-if="materia">
      <header
        class="card hero-do-detalhe-da-materia"
        :style="{
          '--cor-identificadora-da-materia':
            materia.cor ?? 'var(--cor-violeta)',
        }"
      >
        <span
          class="marca-de-cor marca-de-cor-grande"
          aria-hidden="true"
        ></span>
        <div class="identidade-do-detalhe-da-materia">
          <div class="rotulos-do-detalhe-da-materia">
            <span class="sobretitulo-da-pagina">Materia</span>
            <span class="badge etiqueta-neutra">
              {{ materia.arquivada ? 'Arquivada' : 'Ativa' }}
            </span>
          </div>
          <h1>{{ materia.nome }}</h1>
          <p>{{ materia.descricao || 'Sem descricao.' }}</p>
        </div>
        <button
          class="btn btn-outline-secondary"
          type="button"
          @click="alternarArquivamentoDaMateria"
        >
          {{ materia.arquivada ? 'Restaurar materia' : 'Arquivar materia' }}
        </button>
      </header>

      <div class="grade-do-detalhe-da-materia">
        <section
          class="coluna-principal-do-detalhe"
          aria-labelledby="titulo-topicos"
        >
          <section class="card painel-da-arvore-completa">
            <header class="cabecalho-do-cartao-da-jornada">
              <div>
                <span class="rotulo-discreto">Estrutura de estudo</span>
                <h2 id="titulo-topicos">Arvore de topicos</h2>
                <p>
                  {{ topicos.length }} topico{{
                    topicos.length === 1 ? '' : 's'
                  }}
                </p>
              </div>
              <span class="badge etiqueta-neutra">
                {{ topicos.length }}
              </span>
            </header>

            <div
              v-if="topicos.length === 0"
              class="estado-do-catalogo estado-vazio-com-borda"
            >
              <i class="bi bi-diagram-3" aria-hidden="true"></i>
              <h3>Nenhum topico cadastrado</h3>
              <p>Adicione o primeiro topico desta materia.</p>
            </div>
            <ArvoreDeTopicos
              v-else
              :topicos="topicosOrdenados"
              @editar="editar"
              @arquivar="alternarArquivamentoDoTopico"
              @excluir="excluir"
            />
          </section>

          <section
            class="grade-de-uso-da-materia"
            aria-label="Uso desta matéria"
          >
            <article class="card cartao-de-uso-da-materia">
              <span class="icone-do-uso-da-materia">
                <i class="bi bi-collection" aria-hidden="true"></i>
              </span>
              <div>
                <h2>Materiais relacionados</h2>
                <p v-if="uso.materiais.length === 0">
                  Nenhum material cobre os tópicos desta matéria.
                </p>
                <ul v-else>
                  <li
                    v-for="material in uso.materiais"
                    :key="material.identificador"
                  >
                    <RouterLink :to="`/materiais/${material.identificador}`">
                      {{ material.titulo }}
                    </RouterLink>
                    <span class="badge etiqueta-neutra">{{
                      material.tipo
                    }}</span>
                  </li>
                </ul>
              </div>
            </article>

            <article class="card cartao-de-uso-da-materia">
              <span class="icone-do-uso-da-materia">
                <i class="bi bi-clock-history" aria-hidden="true"></i>
              </span>
              <div>
                <h2>Estudos recentes</h2>
                <p v-if="uso.estudosRecentes.length === 0">
                  Nenhum estudo ativo nesta matéria.
                </p>
                <ul v-else>
                  <li
                    v-for="estudo in uso.estudosRecentes"
                    :key="estudo.identificador"
                  >
                    <strong>{{ estudo.nomeDoTopico }}</strong>
                    <span>
                      {{ estudo.duracaoEmMinutos }} min ·
                      {{ dataHoraLegivel(estudo.dataHora) }}
                    </span>
                  </li>
                </ul>
              </div>
            </article>

            <article class="card cartao-de-uso-da-materia">
              <span class="icone-do-uso-da-materia">
                <i class="bi bi-bullseye" aria-hidden="true"></i>
              </span>
              <div>
                <h2>Concursos relacionados</h2>
                <p v-if="uso.concursos.length === 0">
                  Esta matéria ainda não é exigida em um concurso.
                </p>
                <ul v-else>
                  <li
                    v-for="concurso in uso.concursos"
                    :key="concurso.identificador"
                  >
                    <RouterLink :to="`/concursos/${concurso.identificador}`">
                      {{ concurso.nome }}
                    </RouterLink>
                    <span v-if="concurso.ativo" class="badge text-bg-success">
                      Ativo
                    </span>
                  </li>
                </ul>
              </div>
            </article>
          </section>
        </section>

        <aside class="coluna-do-editor-de-topico">
          <form
            id="formulario-topico"
            class="card formulario-da-aplicacao formulario-de-topico-completo"
            @submit.prevent="salvar"
          >
            <header>
              <span class="rotulo-discreto">Organizar conteúdo</span>
              <h2>
                {{ identificadorEmEdicao ? 'Editar topico' : 'Novo topico' }}
              </h2>
              <p>
                Defina nome, hierarquia e ordem sem sair da árvore da matéria.
              </p>
            </header>
            <p v-if="materia.arquivada" class="alert alert-warning">
              Restaure a materia para alterar seus topicos.
            </p>
            <fieldset
              class="campos-do-editor-de-topico"
              :disabled="materia.arquivada || salvando"
            >
              <label>
                <span>Nome</span>
                <input
                  id="nome-topico"
                  v-model="formulario.nome"
                  class="form-control"
                  maxlength="160"
                  required
                />
              </label>
              <label>
                <span>Descricao</span>
                <textarea
                  id="descricao-topico"
                  v-model="formulario.descricao"
                  class="form-control"
                  maxlength="1000"
                  rows="3"
                ></textarea>
              </label>
              <label>
                <span>Topico-pai</span>
                <select
                  id="pai-topico"
                  v-model="formulario.identificadorDoTopicoPai"
                  class="form-select"
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
              </label>
              <label>
                <span>Ordem</span>
                <input
                  id="ordem-topico"
                  v-model.number="formulario.ordem"
                  class="form-control"
                  type="number"
                  min="1"
                  required
                />
              </label>
              <div class="acoes-do-editor-de-topico">
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
