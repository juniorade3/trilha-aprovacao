<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'

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

const concursoAtivo = computed(() =>
  concursos.value.find((concurso) => concurso.ativo),
)
const outrosConcursos = computed(() =>
  concursos.value.filter((concurso) => !concurso.ativo),
)

const rotulosDeSituacao: Record<string, string> = {
  PLANEJADO: 'Planejado',
  EDITAL_PUBLICADO: 'Edital publicado',
  INSCRICOES_ABERTAS: 'Inscrições abertas',
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
        : 'Não foi possível carregar os concursos.'
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
      causa instanceof Error ? causa.message : 'Não foi possível ativar.'
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
      causa instanceof Error ? causa.message : 'Não foi possível arquivar.'
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
      causa instanceof Error ? causa.message : 'Não foi possível excluir.'
  }
}

onMounted(() => carregar())
onBeforeUnmount(() => cancelamento?.abort())
</script>

<template>
  <main class="pagina-da-jornada pagina-dos-concursos">
    <header class="cabecalho-da-pagina">
      <div>
        <p class="sobretitulo-da-pagina">Seu objetivo em foco</p>
        <h1>Meu concurso</h1>
        <p>
          Acompanhe o objetivo ativo e mantenha outras oportunidades organizadas
          sem misturar suas estruturas.
        </p>
      </div>
      <div class="acoes-do-cabecalho">
        <RouterLink class="btn btn-primary" to="/concursos/novo">
          <i class="bi bi-plus-lg me-2" aria-hidden="true"></i>
          Novo concurso
        </RouterLink>
      </div>
    </header>

    <p v-if="erro" class="alert alert-danger" role="alert">{{ erro }}</p>

    <form class="card barra-da-biblioteca" @submit.prevent="carregar(0)">
      <div class="campo-de-busca">
        <i class="bi bi-search" aria-hidden="true"></i>
        <input
          id="pesquisa-concurso"
          v-model="pesquisa"
          aria-label="Buscar concursos pelo nome"
          placeholder="Buscar pelo nome do concurso"
        />
      </div>
      <label class="filtro-da-biblioteca">
        <input
          v-model="incluirArquivados"
          type="checkbox"
          @change="carregar(0)"
        />
        Incluir arquivados
      </label>
      <button class="btn btn-outline-primary">Buscar</button>
    </form>

    <div v-if="carregando" class="estado-do-catalogo" aria-live="polite">
      <span class="spinner-border" aria-hidden="true"></span>
      Carregando concursos...
    </div>
    <section v-else-if="concursos.length === 0" class="card estado-do-catalogo">
      <i class="bi bi-bullseye" aria-hidden="true"></i>
      <strong>Nenhum concurso encontrado</strong>
      <span>Crie seu primeiro objetivo em quatro etapas guiadas.</span>
      <RouterLink class="btn btn-primary mt-2" to="/concursos/novo">
        Criar meu primeiro concurso
      </RouterLink>
    </section>
    <template v-else>
      <section v-if="concursoAtivo" class="objetivo-ativo-em-destaque">
        <div>
          <p class="sobretitulo-da-pagina">Concurso ativo</p>
          <span class="selo-de-objetivo-ativo mt-2">
            <i aria-hidden="true"></i>
            {{ rotulosDeSituacao[concursoAtivo.situacao] }}
          </span>
          <h2>{{ concursoAtivo.nome }}</h2>
          <p>
            {{
              [concursoAtivo.orgao, concursoAtivo.banca]
                .filter(Boolean)
                .join(' · ') || 'Objetivo em construção'
            }}
          </p>
          <div>
            <RouterLink
              class="btn botao-de-contraste"
              :to="`/concursos/${concursoAtivo.identificador}`"
            >
              Continuar configuração
              <i class="bi bi-arrow-right ms-2" aria-hidden="true"></i>
            </RouterLink>
            <RouterLink class="btn botao-transparente" to="/dashboard">
              Ver jornada
            </RouterLink>
          </div>
        </div>
        <span class="icone-do-objetivo-ativo" aria-hidden="true">
          <i class="bi bi-trophy"></i>
        </span>
      </section>

      <section v-if="outrosConcursos.length" class="mt-4">
        <div class="titulo-da-secao-dos-concursos">
          <div>
            <p class="sobretitulo-da-pagina">Outras oportunidades</p>
            <h2 class="titulo-editorial">Seus concursos planejados</h2>
          </div>
          <span>{{ totalDeItens }} no total</span>
        </div>
        <div class="grade-dos-concursos">
          <article
            v-for="concurso in outrosConcursos"
            :key="concurso.identificador"
            class="card cartao-do-concurso"
          >
            <div>
              <span class="badge etiqueta-neutra">
                {{ rotulosDeSituacao[concurso.situacao] }}
              </span>
              <h3>{{ concurso.nome }}</h3>
              <p>
                {{ concurso.orgao || 'Órgão não informado' }}
                <span v-if="concurso.banca"> · {{ concurso.banca }}</span>
              </p>
              <small>{{ concurso.descricao || 'Sem descrição.' }}</small>
            </div>
            <footer>
              <RouterLink
                class="btn btn-sm btn-outline-primary"
                :to="`/concursos/${concurso.identificador}`"
              >
                Abrir estrutura
              </RouterLink>
              <button
                v-if="concurso.situacao !== 'ARQUIVADO'"
                class="btn btn-sm btn-primary"
                type="button"
                @click="ativar(concurso)"
              >
                Tornar ativo
              </button>
              <details class="acoes-do-material">
                <summary class="botao-de-icone" aria-label="Outras ações">
                  <i class="bi bi-three-dots" aria-hidden="true"></i>
                </summary>
                <div class="menu-de-acoes-do-material">
                  <button type="button" @click="alternarArquivamento(concurso)">
                    {{
                      concurso.situacao === 'ARQUIVADO'
                        ? 'Restaurar'
                        : 'Arquivar'
                    }}
                  </button>
                  <button
                    class="text-danger"
                    type="button"
                    @click="excluir(concurso)"
                  >
                    Excluir
                  </button>
                </div>
              </details>
            </footer>
          </article>
        </div>
      </section>
    </template>

    <nav
      v-if="totalDePaginas > 1"
      class="paginacao-dos-concursos"
      aria-label="Paginação de concursos"
    >
      <button
        class="btn btn-outline-primary"
        type="button"
        :disabled="pagina === 0"
        @click="carregar(pagina - 1)"
      >
        Anterior
      </button>
      <span>Página {{ pagina + 1 }} de {{ totalDePaginas }}</span>
      <button
        class="btn btn-outline-primary"
        type="button"
        :disabled="pagina + 1 >= totalDePaginas"
        @click="carregar(pagina + 1)"
      >
        Próxima
      </button>
    </nav>
  </main>
</template>
