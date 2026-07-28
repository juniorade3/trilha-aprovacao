<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'

import BarraDeProgresso from '@/compartilhado/componentes/BarraDeProgresso.vue'
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

const quantidadeDeTopicosPendentes = computed(() =>
  Math.max(
    (dashboard.value?.quantidadeDeTopicosExigidos ?? 0) -
      (dashboard.value?.quantidadeDeTopicosComEstudo ?? 0),
    0,
  ),
)

const etapasDaJornada = computed(() => {
  const dados = dashboard.value
  const etapaAtual = !dados?.quantidadeDeTopicosExigidos
    ? 0
    : dados.quantidadeDeItensSemMapeamento > 0
      ? 1
      : coberturaDeEstudo.value < 70
        ? 2
        : 3
  return [
    { nome: 'Objetivo', detalhe: 'Concurso e cargo' },
    { nome: 'Base do edital', detalhe: 'Estrutura e vínculos' },
    { nome: 'Consolidação', detalhe: 'Estudo consistente' },
    { nome: 'Reta final', detalhe: 'Cobertura avançada' },
  ].map((etapa, indice) => ({
    ...etapa,
    situacao:
      indice < etapaAtual ? 'concluida' : indice === etapaAtual ? 'atual' : '',
  }))
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

function abrirRegistroRapido() {
  window.dispatchEvent(new CustomEvent('abrir-registro-rapido'))
}

onMounted(() => {
  void carregar()
  window.addEventListener('estudo-registrado', carregar)
})
onBeforeUnmount(() => {
  cancelamento?.abort()
  window.removeEventListener('estudo-registrado', carregar)
})
</script>

<template>
  <main class="painel-da-jornada dashboard-moderno">
    <div class="pagina-da-jornada pagina-do-dashboard-moderno">
      <section
        v-if="carregando"
        class="dashboard-carregando"
        aria-live="polite"
        aria-label="Carregando dashboard"
      >
        <div class="esqueleto-do-cabecalho placeholder-glow">
          <span class="placeholder"></span>
          <span class="placeholder"></span>
          <span class="placeholder"></span>
        </div>
        <div class="esqueleto-do-hero card placeholder-glow">
          <div>
            <span class="placeholder"></span>
            <span class="placeholder"></span>
            <span class="placeholder"></span>
          </div>
          <span class="placeholder"></span>
        </div>
        <div class="grade-de-resumo">
          <div v-for="indice in 3" :key="indice" class="card placeholder-glow">
            <span class="placeholder"></span>
            <span class="placeholder"></span>
            <span class="placeholder"></span>
          </div>
        </div>
      </section>

      <section
        v-else-if="erro"
        class="estado-de-dashboard estado-de-dashboard-moderno card"
        role="alert"
      >
        <span class="icone-de-estado icone-de-estado-erro">
          <i class="bi bi-cloud-slash" aria-hidden="true"></i>
        </span>
        <p class="sobretitulo-da-pagina">Conexão interrompida</p>
        <h1 class="titulo-editorial">Seu painel não pôde ser carregado</h1>
        <p class="text-secondary">{{ erro }}</p>
        <button class="btn btn-primary" type="button" @click="carregar">
          <i class="bi bi-arrow-clockwise" aria-hidden="true"></i>
          Tentar novamente
        </button>
      </section>

      <section
        v-else-if="!dashboard?.concursoAtivo"
        class="estado-de-dashboard estado-sem-concurso"
      >
        <span
          class="circulo-decorativo circulo-decorativo-um"
          aria-hidden="true"
        ></span>
        <span
          class="circulo-decorativo circulo-decorativo-dois"
          aria-hidden="true"
        ></span>
        <div class="conteudo-do-estado-vazio">
          <span class="icone-de-estado">
            <i class="bi bi-compass" aria-hidden="true"></i>
          </span>
          <p class="sobretitulo-da-pagina">Seu ponto de partida</p>
          <h1>Comece escolhendo seu concurso ativo</h1>
          <p class="lead text-secondary">
            Reúna edital, conteúdos e estudos em torno de um objetivo claro.
            Cadastre um concurso ou retome um que já existe.
          </p>
          <div class="acoes-do-estado-vazio">
            <RouterLink class="btn btn-primary btn-lg" to="/concursos/novo">
              <i class="bi bi-plus-lg" aria-hidden="true"></i>
              Criar concurso
            </RouterLink>
            <RouterLink class="btn btn-outline-primary btn-lg" to="/concursos">
              Ver meus concursos
            </RouterLink>
          </div>
        </div>
      </section>

      <template v-else>
        <header class="cabecalho-moderno-do-dashboard">
          <div>
            <p class="sobretitulo-da-pagina">Sua jornada de hoje</p>
            <h1>Você está avançando</h1>
            <p>Visualize o que importa agora e mantenha o ritmo até a prova.</p>
          </div>
          <div class="acoes-do-dashboard">
            <RouterLink class="btn btn-outline-primary" to="/planejamento/hoje">
              <i class="bi bi-sun" aria-hidden="true"></i>
              Ver plano de hoje
            </RouterLink>
            <button
              class="btn btn-primary acao-principal-do-dashboard"
              type="button"
              @click="abrirRegistroRapido"
            >
              <i class="bi bi-pencil-square" aria-hidden="true"></i>
              Registrar estudo
            </button>
          </div>
        </header>

        <section class="card resumo-principal-da-jornada hero-do-dashboard">
          <div class="corpo-do-hero-do-dashboard">
            <div class="objetivo-da-jornada objetivo-do-dashboard">
              <div class="cabecalho-do-objetivo-do-dashboard">
                <span class="icone-redondo-da-jornada">
                  <i class="bi bi-bullseye" aria-hidden="true"></i>
                </span>
                <span class="estado-do-objetivo-do-dashboard">
                  <i aria-hidden="true"></i>
                  Objetivo ativo
                </span>
              </div>

              <div>
                <span class="rotulo-discreto">Concurso em foco</span>
                <h2>{{ dashboard.concursoAtivo.nome }}</h2>
                <p class="cargo-do-objetivo">
                  {{
                    dashboard.concursoAtivo.nomeDoCargoSelecionado ||
                    'Cargo ainda não selecionado'
                  }}
                </p>
              </div>

              <div class="metadados-do-objetivo">
                <span v-if="dashboard.dataDaProximaProva">
                  <i class="bi bi-calendar3" aria-hidden="true"></i>
                  {{ formatarData(dashboard.dataDaProximaProva) }}
                  <b>{{ rotuloDosDias(dashboard.diasAteAProva) }}</b>
                </span>
                <span class="tempo-da-jornada">
                  <i class="bi bi-clock-history" aria-hidden="true"></i>
                  <strong>{{
                    formatarTempo(dashboard.tempoEstudadoNaSemanaEmMinutos)
                  }}</strong>
                  nesta semana
                </span>
              </div>
            </div>

            <aside class="painel-de-foco-do-dashboard">
              <div class="medidor-da-jornada cobertura-do-dashboard">
                <div
                  class="anel-da-cobertura-do-dashboard"
                  :style="{
                    '--progresso-da-cobertura': `${coberturaDeEstudo}%`,
                  }"
                  aria-hidden="true"
                >
                  <strong>{{ coberturaDeEstudo }}%</strong>
                  <span>coberto</span>
                </div>
                <div>
                  <span class="rotulo-discreto">Cobertura do edital</span>
                  <p>
                    <strong>{{
                      dashboard.quantidadeDeTopicosComEstudo
                    }}</strong>
                    de {{ dashboard.quantidadeDeTopicosExigidos }} tópicos com
                    estudo
                  </p>
                  <BarraDeProgresso
                    :valor="coberturaDeEstudo"
                    rotulo="Cobertura dos tópicos exigidos"
                  />
                  <small>
                    {{ quantidadeDeTopicosPendentes }}
                    {{
                      quantidadeDeTopicosPendentes === 1
                        ? 'tópico ainda pede atenção'
                        : 'tópicos ainda pedem atenção'
                    }}
                  </small>
                </div>
              </div>

              <RouterLink
                class="proxima-acao-do-dashboard"
                to="/planejamento/hoje"
              >
                <span>
                  <i class="bi bi-play-fill" aria-hidden="true"></i>
                </span>
                <span>
                  <small>Próxima ação</small>
                  <strong>Continue pelo plano de hoje</strong>
                  <em>Veja o próximo bloco e comece a estudar.</em>
                </span>
                <i class="bi bi-arrow-up-right" aria-hidden="true"></i>
              </RouterLink>
            </aside>
          </div>

          <div class="etapas-da-jornada" aria-label="Etapas da preparação">
            <div
              v-for="etapa in etapasDaJornada"
              :key="etapa.nome"
              :class="etapa.situacao"
            >
              <i>
                <span v-if="etapa.situacao === 'concluida'">
                  <i class="bi bi-check2" aria-hidden="true"></i>
                </span>
              </i>
              <b>{{ etapa.nome }}</b>
              <small>{{ etapa.detalhe }}</small>
            </div>
          </div>
        </section>

        <section class="grade-de-resumo" aria-label="Resumo da trilha">
          <article class="card indicador-resumido indicador-de-materias">
            <span class="mini-icone-da-jornada">
              <i class="bi bi-journal-bookmark" aria-hidden="true"></i>
            </span>
            <div>
              <span>Matérias ativas</span>
              <strong>{{ dashboard.quantidadeDeMaterias }}</strong>
              <small>na trilha deste concurso</small>
            </div>
          </article>
          <article class="card indicador-resumido indicador-de-mapeamento">
            <span class="mini-icone-da-jornada">
              <i class="bi bi-diagram-3" aria-hidden="true"></i>
            </span>
            <div>
              <span>Edital organizado</span>
              <strong>{{ dashboard.quantidadeDeItensMapeados }}</strong>
              <small>itens oficiais mapeados</small>
            </div>
          </article>
          <article class="card indicador-resumido indicador-de-tempo">
            <span class="mini-icone-da-jornada">
              <i class="bi bi-stopwatch" aria-hidden="true"></i>
            </span>
            <div>
              <span>Ritmo semanal</span>
              <strong>{{
                formatarTempo(dashboard.tempoEstudadoNaSemanaEmMinutos)
              }}</strong>
              <small>tempo real de estudo</small>
            </div>
          </article>
        </section>

        <div class="grade-dos-destaques grade-de-conteudo-do-dashboard">
          <article class="card atividade-da-jornada">
            <header class="cabecalho-do-cartao-da-jornada">
              <div>
                <span class="rotulo-discreto">Últimos registros</span>
                <h2>Atividade recente</h2>
                <p>Seu histórico mais recente, sem sair do foco de hoje.</p>
              </div>
              <RouterLink to="/estudos">
                Ver histórico completo
                <i class="bi bi-arrow-right" aria-hidden="true"></i>
              </RouterLink>
            </header>
            <div
              v-if="dashboard.atividadeRecente.length === 0"
              class="estado-vazio-da-jornada"
            >
              <span class="mini-icone-da-jornada">
                <i class="bi bi-book" aria-hidden="true"></i>
              </span>
              <strong>Seu primeiro registro aparecerá aqui</strong>
              <small>
                Registre uma sessão para começar a acompanhar seu ritmo.
              </small>
            </div>
            <div v-else class="lista-de-atividade-da-jornada">
              <div
                v-for="atividade in dashboard.atividadeRecente.slice(0, 3)"
                :key="atividade.identificador"
              >
                <span class="mini-icone-da-jornada">
                  <i class="bi bi-book" aria-hidden="true"></i>
                </span>
                <p>
                  <b>{{ atividade.nomeDoTopico }}</b>
                  <small>
                    {{ atividade.tituloDoMaterial || 'Sem material' }}
                  </small>
                </p>
                <span class="duracao-da-atividade-recente">
                  {{ formatarTempo(atividade.duracaoEmMinutos) }}
                </span>
                <time>{{ formatarDataHora(atividade.dataHora) }}</time>
              </div>
            </div>
          </article>

          <aside class="coluna-lateral-do-dashboard">
            <RouterLink
              class="card alerta-de-lacunas"
              :to="`/concursos/${dashboard.concursoAtivo.identificador}?foco=mapeamentos`"
            >
              <span class="icone-do-alerta-de-lacuna">
                <i class="bi bi-diagram-2" aria-hidden="true"></i>
              </span>
              <span>
                <small>Base do edital</small>
                <strong>
                  {{ dashboard.quantidadeDeItensSemMapeamento }}
                  {{
                    dashboard.quantidadeDeItensSemMapeamento === 1
                      ? 'item pendente'
                      : 'itens pendentes'
                  }}
                </strong>
                <b>
                  {{
                    dashboard.quantidadeDeItensSemMapeamento
                      ? 'Revise os vínculos para deixar o plano mais preciso.'
                      : 'Seu mapeamento está em dia.'
                  }}
                </b>
                <em v-if="dashboard.alertas[0]">
                  {{ dashboard.alertas[0].titulo }}
                </em>
              </span>
              <i class="bi bi-arrow-up-right" aria-hidden="true"></i>
            </RouterLink>

            <section class="card atalhos-do-dashboard">
              <header>
                <div>
                  <span class="rotulo-discreto">Acesso rápido</span>
                  <h2>Continue sua trilha</h2>
                </div>
                <i class="bi bi-lightning-charge" aria-hidden="true"></i>
              </header>
              <nav aria-label="Atalhos do dashboard">
                <RouterLink to="/planejamento/semana">
                  <i class="bi bi-calendar-week" aria-hidden="true"></i>
                  <span>Planejar semana</span>
                </RouterLink>
                <RouterLink to="/materias">
                  <i class="bi bi-journal-text" aria-hidden="true"></i>
                  <span>Ver conteúdos</span>
                </RouterLink>
                <RouterLink to="/materiais">
                  <i class="bi bi-collection" aria-hidden="true"></i>
                  <span>Biblioteca</span>
                </RouterLink>
                <RouterLink to="/planejamento/prioridades">
                  <i class="bi bi-bar-chart" aria-hidden="true"></i>
                  <span>Ver lacunas</span>
                </RouterLink>
              </nav>
            </section>
          </aside>
        </div>
      </template>
    </div>
  </main>
</template>
