<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'

import BarraDeProgresso from '@/compartilhado/componentes/BarraDeProgresso.vue'
import CabecalhoDaPagina from '@/compartilhado/componentes/CabecalhoDaPagina.vue'
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
  <main class="painel-da-jornada">
    <div class="pagina-da-jornada">
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
        <div class="grade-de-resumo">
          <div v-for="indice in 3" :key="indice" class="card p-4">
            <span class="placeholder col-4 mb-3"></span>
            <span class="placeholder col-8 placeholder-lg"></span>
            <span class="placeholder col-6 mt-3"></span>
          </div>
        </div>
      </section>

      <section
        v-else-if="erro"
        class="estado-de-dashboard card mx-auto text-center"
        role="alert"
      >
        <span class="icone-de-estado icone-de-estado-erro mx-auto mb-4">
          <i class="bi bi-cloud-slash" aria-hidden="true"></i>
        </span>
        <p class="sobretitulo-da-pagina text-danger mb-2">
          Conexão interrompida
        </p>
        <h1 class="titulo-editorial mb-3">Seu painel não pôde ser carregado</h1>
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
          <p class="sobretitulo-da-pagina mb-2">Seu ponto de partida</p>
          <h1 class="display-5 mb-3">Comece escolhendo seu concurso ativo</h1>
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
        <CabecalhoDaPagina
          etiqueta="Sua jornada de hoje"
          titulo="Você está avançando"
          descricao="Mantenha o ritmo e siga firme até a prova. Consistência hoje, aprovação amanhã."
        >
          <template #acoes>
            <button
              class="btn btn-primary"
              type="button"
              @click="abrirRegistroRapido"
            >
              <i class="bi bi-pencil-square me-2" aria-hidden="true"></i>
              Registrar estudo
            </button>
            <RouterLink
              class="btn btn-outline-primary"
              :to="`/concursos/${dashboard.concursoAtivo.identificador}?foco=mapeamentos`"
            >
              <i class="bi bi-bar-chart me-2" aria-hidden="true"></i>
              Ver lacunas
            </RouterLink>
          </template>
        </CabecalhoDaPagina>

        <section class="card resumo-principal-da-jornada">
          <div class="objetivo-da-jornada">
            <span class="icone-redondo-da-jornada">
              <i class="bi bi-building" aria-hidden="true"></i>
            </span>
            <div>
              <span class="rotulo-discreto">Concurso ativo</span>
              <h2>{{ dashboard.concursoAtivo.nome }}</h2>
              <p>
                {{
                  dashboard.concursoAtivo.nomeDoCargoSelecionado ||
                  'Cargo ainda não selecionado'
                }}
              </p>
              <small v-if="dashboard.dataDaProximaProva">
                <i class="bi bi-calendar3" aria-hidden="true"></i>
                {{ formatarData(dashboard.dataDaProximaProva) }} ·
                {{ rotuloDosDias(dashboard.diasAteAProva) }}
              </small>
            </div>
          </div>

          <div class="medidor-da-jornada">
            <p>
              <strong>{{ dashboard.quantidadeDeTopicosComEstudo }}</strong>
              de {{ dashboard.quantidadeDeTopicosExigidos }} tópicos com estudo
            </p>
            <div>
              <BarraDeProgresso
                :valor="coberturaDeEstudo"
                rotulo="Cobertura dos tópicos exigidos"
              />
              <b>{{ coberturaDeEstudo }}%</b>
            </div>
            <small>
              {{
                Math.max(
                  dashboard.quantidadeDeTopicosExigidos -
                    dashboard.quantidadeDeTopicosComEstudo,
                  0,
                )
              }}
              tópicos ainda precisam de atenção
            </small>
          </div>

          <div class="tempo-da-jornada">
            <i class="bi bi-clock" aria-hidden="true"></i>
            <div>
              <strong>{{
                formatarTempo(dashboard.tempoEstudadoNaSemanaEmMinutos)
              }}</strong>
              <span>estudadas nesta semana</span>
            </div>
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

        <div class="grade-dos-destaques">
          <RouterLink
            class="card alerta-de-lacunas"
            :to="`/concursos/${dashboard.concursoAtivo.identificador}?foco=mapeamentos`"
          >
            <span class="icone-do-alerta-de-lacuna">
              <i class="bi bi-exclamation-triangle" aria-hidden="true"></i>
            </span>
            <span>
              <strong>
                {{ dashboard.quantidadeDeItensSemMapeamento }}
                {{
                  dashboard.quantidadeDeItensSemMapeamento === 1
                    ? 'item do edital'
                    : 'itens do edital'
                }}
              </strong>
              <b>
                {{
                  dashboard.quantidadeDeItensSemMapeamento
                    ? 'ainda sem mapeamento'
                    : 'com mapeamento concluído'
                }}
              </b>
              <small v-if="dashboard.alertas[0]">
                {{ dashboard.alertas[0].titulo }}
              </small>
            </span>
            <i class="bi bi-arrow-right" aria-hidden="true"></i>
          </RouterLink>

          <article class="card atividade-da-jornada">
            <header class="cabecalho-do-cartao-da-jornada">
              <div>
                <span class="rotulo-discreto">Últimos registros</span>
                <h2>Atividade recente</h2>
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
              Seu primeiro registro aparecerá aqui.
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
                    {{ atividade.tituloDoMaterial || 'Sem material' }} ·
                    {{ formatarTempo(atividade.duracaoEmMinutos) }}
                  </small>
                </p>
                <time>{{ formatarDataHora(atividade.dataHora) }}</time>
              </div>
            </div>
          </article>
        </div>

        <section class="grade-de-resumo" aria-label="Resumo da trilha">
          <article class="card indicador-resumido">
            <span class="mini-icone-da-jornada">
              <i class="bi bi-book" aria-hidden="true"></i>
            </span>
            <div>
              <strong>{{ dashboard.quantidadeDeMaterias }}</strong>
              <small>matérias na trilha ativa</small>
            </div>
          </article>
          <article class="card indicador-resumido">
            <span class="mini-icone-da-jornada">
              <i class="bi bi-diagram-3" aria-hidden="true"></i>
            </span>
            <div>
              <strong>{{ dashboard.quantidadeDeItensMapeados }}</strong>
              <small>itens oficiais mapeados</small>
            </div>
          </article>
          <article class="card indicador-resumido">
            <span class="mini-icone-da-jornada">
              <i class="bi bi-stopwatch" aria-hidden="true"></i>
            </span>
            <div>
              <strong>{{
                formatarTempo(dashboard.tempoEstudadoNaSemanaEmMinutos)
              }}</strong>
              <small>tempo real nesta semana</small>
            </div>
          </article>
        </section>
      </template>
    </div>
  </main>
</template>
