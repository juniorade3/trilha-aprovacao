<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'

import { obterDashboard, type Dashboard } from './apiDoDashboard'

const dashboard = ref<Dashboard>()
const carregando = ref(true)
const erro = ref('')
let cancelamento: AbortController | undefined

const coberturaDeEstudo = computed(() => {
  if (!dashboard.value?.quantidadeDeTopicosExigidos) return 0
  return Math.round(
    (dashboard.value.quantidadeDeTopicosComEstudo /
      dashboard.value.quantidadeDeTopicosExigidos) *
      100,
  )
})

async function carregar() {
  cancelamento?.abort()
  const requisicao = new AbortController()
  cancelamento = requisicao
  carregando.value = true
  erro.value = ''
  try {
    dashboard.value = await obterDashboard(requisicao.signal)
  } catch (causa) {
    if (causa instanceof DOMException && causa.name === 'AbortError') return
    erro.value =
      causa instanceof Error
        ? causa.message
        : 'Não foi possível carregar seu painel.'
  } finally {
    if (cancelamento === requisicao) carregando.value = false
  }
}

function formatarTempo(minutos: number) {
  const horas = Math.floor(minutos / 60)
  const restante = minutos % 60
  if (horas === 0) return `${restante}min`
  return `${horas}h ${restante.toString().padStart(2, '0')}min`
}

function formatarData(data: string) {
  return new Intl.DateTimeFormat('pt-BR', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
  }).format(new Date(`${data}T12:00:00`))
}

function formatarDataHora(dataHora: string) {
  return new Intl.DateTimeFormat('pt-BR', {
    day: '2-digit',
    month: 'short',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(dataHora))
}

function rotuloDosDias(dias?: number) {
  if (dias === undefined) return ''
  if (dias === 0) return 'É hoje'
  if (dias === 1) return 'Falta 1 dia'
  return `Faltam ${dias} dias`
}

onMounted(() => carregar())
onBeforeUnmount(() => cancelamento?.abort())
</script>

<template>
  <main class="painel-dashboard py-4 py-lg-5">
    <div class="container">
      <section
        v-if="carregando"
        class="dashboard-carregando"
        aria-live="polite"
        aria-label="Carregando dashboard"
      >
        <div class="placeholder-glow mb-4">
          <span class="placeholder col-2 rounded-pill"></span>
          <span class="placeholder col-7 d-block mt-3 placeholder-lg"></span>
        </div>
        <div class="row g-3">
          <div v-for="indice in 4" :key="indice" class="col-md-6 col-xl-3">
            <div class="card border-0 shadow-sm p-4">
              <span class="placeholder col-4 mb-3"></span>
              <span class="placeholder col-8 placeholder-lg"></span>
              <span class="placeholder col-6 mt-3"></span>
            </div>
          </div>
        </div>
        <span class="visually-hidden">Carregando seu painel</span>
      </section>

      <section
        v-else-if="erro"
        class="estado-de-dashboard card border-0 shadow-sm mx-auto text-center"
        role="alert"
      >
        <span class="icone-de-estado icone-de-estado-erro mx-auto mb-4">
          <i class="bi bi-cloud-slash" aria-hidden="true"></i>
        </span>
        <p class="text-uppercase fw-semibold text-danger mb-2">
          Conexão interrompida
        </p>
        <h1 class="h2 mb-3">Seu painel não pôde ser carregado</h1>
        <p class="text-secondary mb-4">{{ erro }}</p>
        <button
          class="btn btn-primary align-self-center px-4"
          type="button"
          @click="carregar"
        >
          <i class="bi bi-arrow-clockwise me-2" aria-hidden="true"></i>
          Tentar novamente
        </button>
      </section>

      <section
        v-else-if="!dashboard?.concursoAtivo"
        class="estado-de-dashboard estado-sem-concurso position-relative overflow-hidden"
      >
        <span
          class="circulo-decorativo circulo-decorativo-um"
          aria-hidden="true"
        ></span>
        <span
          class="circulo-decorativo circulo-decorativo-dois"
          aria-hidden="true"
        ></span>
        <div class="conteudo-do-estado-vazio position-relative">
          <span class="icone-de-estado mb-4">
            <i class="bi bi-compass" aria-hidden="true"></i>
          </span>
          <p class="text-uppercase fw-semibold text-success mb-2">
            Seu ponto de partida
          </p>
          <h1 class="display-5 fw-bold mb-3">
            Comece escolhendo seu concurso ativo
          </h1>
          <p class="lead text-secondary mb-4">
            O painel reúne provas, conteúdos e estudos do objetivo que está em
            foco. Cadastre um concurso ou ative um que já existe.
          </p>
          <div class="d-flex flex-wrap gap-2">
            <RouterLink
              class="btn btn-primary btn-lg px-4"
              to="/concursos/novo"
            >
              <i class="bi bi-plus-lg me-2" aria-hidden="true"></i>
              Criar concurso
            </RouterLink>
            <RouterLink
              class="btn btn-outline-primary btn-lg px-4"
              to="/concursos"
            >
              Ver meus concursos
            </RouterLink>
          </div>
        </div>
      </section>

      <template v-else>
        <section
          class="cabecalho-do-dashboard mb-4 position-relative overflow-hidden"
        >
          <span
            class="orbita-decorativa orbita-decorativa-um"
            aria-hidden="true"
          ></span>
          <span
            class="orbita-decorativa orbita-decorativa-dois"
            aria-hidden="true"
          ></span>
          <div class="row g-4 align-items-center position-relative">
            <div class="col-lg-8">
              <div class="d-flex flex-wrap align-items-center gap-2 mb-3">
                <span class="selo-do-dashboard">
                  <span class="ponto-ativo" aria-hidden="true"></span>
                  Concurso ativo
                </span>
                <span
                  class="badge rounded-pill text-bg-light text-primary px-3 py-2"
                >
                  {{ dashboard.concursoAtivo.situacao.replace(/_/g, ' ') }}
                </span>
              </div>
              <h1 class="display-5 fw-bold mb-2">
                {{ dashboard.concursoAtivo.nome }}
              </h1>
              <p class="lead text-white-50 mb-3">
                {{
                  [dashboard.concursoAtivo.orgao, dashboard.concursoAtivo.banca]
                    .filter(Boolean)
                    .join(' · ') || 'Objetivo em construção'
                }}
              </p>
              <div class="d-flex flex-wrap gap-2">
                <span
                  v-if="dashboard.concursoAtivo.nomeDoCargoSelecionado"
                  class="informacao-do-concurso"
                >
                  <i class="bi bi-person-badge" aria-hidden="true"></i>
                  {{ dashboard.concursoAtivo.nomeDoCargoSelecionado }}
                </span>
                <RouterLink
                  class="informacao-do-concurso informacao-do-concurso-link"
                  :to="`/concursos/${dashboard.concursoAtivo.identificador}`"
                >
                  <i class="bi bi-sliders" aria-hidden="true"></i>
                  Ajustar estrutura
                </RouterLink>
              </div>
            </div>
            <div class="col-lg-4">
              <div class="cartao-da-prova">
                <template v-if="dashboard.dataDaProximaProva">
                  <span class="rotulo-claro">Próxima prova</span>
                  <strong class="numero-da-prova">
                    {{ dashboard.diasAteAProva }}
                  </strong>
                  <span class="texto-da-prova">{{
                    rotuloDosDias(dashboard.diasAteAProva)
                  }}</span>
                  <span class="data-da-prova">
                    <i class="bi bi-calendar3 me-2" aria-hidden="true"></i>
                    {{ formatarData(dashboard.dataDaProximaProva) }}
                  </span>
                </template>
                <template v-else>
                  <i
                    class="bi bi-calendar2-plus fs-2 mb-3"
                    aria-hidden="true"
                  ></i>
                  <strong class="h5">Data ainda não definida</strong>
                  <span class="text-white-50"
                    >Inclua a previsão na prova do cargo.</span
                  >
                </template>
              </div>
            </div>
          </div>
        </section>

        <section class="row g-3 mb-4" aria-label="Resumo objetivo">
          <div class="col-md-6 col-xl-3">
            <article class="cartao-de-indicador h-100">
              <div class="icone-do-indicador icone-do-indicador-verde">
                <i class="bi bi-check2-circle" aria-hidden="true"></i>
              </div>
              <span class="rotulo-do-indicador">Tópicos com estudo</span>
              <div class="valor-do-indicador">
                {{ dashboard.quantidadeDeTopicosComEstudo }}
                <small>de {{ dashboard.quantidadeDeTopicosExigidos }}</small>
              </div>
              <div
                class="progress progresso-objetivo mt-3"
                role="progressbar"
                aria-label="Cobertura objetiva de tópicos com estudo"
                :aria-valuenow="coberturaDeEstudo"
                aria-valuemin="0"
                aria-valuemax="100"
              >
                <div
                  class="progress-bar"
                  :style="{ width: `${coberturaDeEstudo}%` }"
                ></div>
              </div>
              <span class="legenda-do-indicador"
                >{{ coberturaDeEstudo }}% com registro ativo</span
              >
            </article>
          </div>
          <div class="col-md-6 col-xl-3">
            <article class="cartao-de-indicador h-100">
              <div class="icone-do-indicador icone-do-indicador-azul">
                <i class="bi bi-stopwatch" aria-hidden="true"></i>
              </div>
              <span class="rotulo-do-indicador">Tempo nesta semana</span>
              <div class="valor-do-indicador">
                {{ formatarTempo(dashboard.tempoEstudadoNaSemanaEmMinutos) }}
              </div>
              <span class="legenda-do-indicador"
                >Somente estudos ativos da trilha</span
              >
            </article>
          </div>
          <div class="col-md-6 col-xl-3">
            <article class="cartao-de-indicador h-100">
              <div class="icone-do-indicador icone-do-indicador-roxo">
                <i class="bi bi-collection" aria-hidden="true"></i>
              </div>
              <span class="rotulo-do-indicador">Matérias na trilha</span>
              <div class="valor-do-indicador">
                {{ dashboard.quantidadeDeMaterias }}
              </div>
              <span class="legenda-do-indicador">Do cargo selecionado</span>
            </article>
          </div>
          <div class="col-md-6 col-xl-3">
            <article class="cartao-de-indicador h-100">
              <div class="icone-do-indicador icone-do-indicador-ambar">
                <i class="bi bi-diagram-3" aria-hidden="true"></i>
              </div>
              <span class="rotulo-do-indicador">Itens a mapear</span>
              <div class="valor-do-indicador">
                {{ dashboard.quantidadeDeItensSemMapeamento }}
              </div>
              <span class="legenda-do-indicador">
                {{ dashboard.quantidadeDeItensMapeados }} já mapeado{{
                  dashboard.quantidadeDeItensMapeados === 1 ? '' : 's'
                }}
              </span>
            </article>
          </div>
        </section>

        <div class="row g-4 mb-4">
          <section class="col-lg-7" aria-labelledby="titulo-atividade">
            <div class="bloco-do-dashboard h-100">
              <div class="cabecalho-do-bloco">
                <div>
                  <span class="subtitulo-do-bloco">Ritmo de preparação</span>
                  <h2 id="titulo-atividade" class="h4 mb-0">
                    Atividade recente
                  </h2>
                </div>
                <RouterLink
                  class="btn btn-sm btn-outline-primary"
                  to="/estudos"
                >
                  Ver histórico
                </RouterLink>
              </div>
              <div
                v-if="dashboard.atividadeRecente.length === 0"
                class="estado-vazio-do-bloco"
              >
                <span class="icone-vazio-menor">
                  <i class="bi bi-journal-check" aria-hidden="true"></i>
                </span>
                <div>
                  <h3 class="h6 mb-1">
                    Sua linha do tempo começa no primeiro estudo
                  </h3>
                  <p class="text-secondary mb-0">
                    Registre uma sessão em um tópico exigido pelo concurso.
                  </p>
                </div>
              </div>
              <ol v-else class="linha-do-tempo">
                <li
                  v-for="atividade in dashboard.atividadeRecente"
                  :key="atividade.identificador"
                  class="item-da-linha-do-tempo"
                >
                  <span class="marcador-da-linha" aria-hidden="true">
                    <i class="bi bi-lightning-charge-fill"></i>
                  </span>
                  <div class="flex-grow-1">
                    <div class="d-flex flex-wrap justify-content-between gap-2">
                      <h3 class="h6 mb-1">{{ atividade.nomeDoTopico }}</h3>
                      <span class="duracao-da-atividade">
                        {{ atividade.duracaoEmMinutos }} min
                      </span>
                    </div>
                    <p class="text-secondary small mb-0">
                      {{ formatarDataHora(atividade.dataHora) }}
                      <template v-if="atividade.tituloDoMaterial">
                        · {{ atividade.tituloDoMaterial }}
                      </template>
                    </p>
                  </div>
                </li>
              </ol>
            </div>
          </section>

          <section class="col-lg-5" aria-labelledby="titulo-alertas">
            <div class="bloco-do-dashboard h-100">
              <div class="cabecalho-do-bloco">
                <div>
                  <span class="subtitulo-do-bloco">Qualidade da estrutura</span>
                  <h2 id="titulo-alertas" class="h4 mb-0">Pontos de atenção</h2>
                </div>
                <span class="contador-de-alertas">
                  {{ dashboard.alertas.length }}
                </span>
              </div>
              <div v-if="dashboard.alertas.length === 0" class="estado-pronto">
                <span class="icone-pronto">
                  <i class="bi bi-shield-check" aria-hidden="true"></i>
                </span>
                <h3 class="h5 mt-3">Trilha consistente</h3>
                <p class="text-secondary mb-0">
                  Nenhuma pendência estrutural foi encontrada para este
                  concurso.
                </p>
              </div>
              <div v-else class="vstack gap-3">
                <article
                  v-for="alerta in dashboard.alertas"
                  :key="alerta.codigo"
                  class="cartao-de-alerta"
                >
                  <span class="icone-do-alerta">
                    <i class="bi bi-exclamation-diamond" aria-hidden="true"></i>
                  </span>
                  <div>
                    <h3 class="h6 mb-1">{{ alerta.titulo }}</h3>
                    <p class="small text-secondary mb-0">
                      {{ alerta.mensagem }}
                    </p>
                  </div>
                </article>
              </div>
            </div>
          </section>
        </div>

        <section class="atalhos-do-dashboard" aria-labelledby="titulo-atalhos">
          <div>
            <span class="subtitulo-do-bloco">Continue avançando</span>
            <h2 id="titulo-atalhos" class="h4 mb-0">Ações rápidas</h2>
          </div>
          <div class="grade-de-atalhos">
            <RouterLink class="atalho-do-dashboard" to="/estudos">
              <span class="icone-do-atalho"
                ><i class="bi bi-play-fill"></i
              ></span>
              <span
                ><strong>Registrar estudo</strong
                ><small>Atualize seu ritmo</small></span
              >
              <i class="bi bi-arrow-right" aria-hidden="true"></i>
            </RouterLink>
            <RouterLink class="atalho-do-dashboard" to="/materiais">
              <span class="icone-do-atalho"><i class="bi bi-book"></i></span>
              <span
                ><strong>Organizar materiais</strong
                ><small>Amplie a cobertura</small></span
              >
              <i class="bi bi-arrow-right" aria-hidden="true"></i>
            </RouterLink>
            <RouterLink
              class="atalho-do-dashboard"
              :to="`/concursos/${dashboard.concursoAtivo.identificador}`"
            >
              <span class="icone-do-atalho"
                ><i class="bi bi-list-check"></i
              ></span>
              <span
                ><strong>Revisar edital</strong
                ><small>Mapeie as pendências</small></span
              >
              <i class="bi bi-arrow-right" aria-hidden="true"></i>
            </RouterLink>
          </div>
        </section>
      </template>
    </div>
  </main>
</template>
