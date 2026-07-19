<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'

import CabecalhoDaPagina from '@/compartilhado/componentes/CabecalhoDaPagina.vue'
import EstadoDaPagina from '@/compartilhado/componentes/EstadoDaPagina.vue'
import NavegacaoDoPlanejamento from './NavegacaoDoPlanejamento.vue'
import {
  obterPlanejamentoDeHoje,
  type BlocoDeEstudo,
  type PlanejamentoDeHoje,
} from './apiDePlanejamento'

const planejamento = ref<PlanejamentoDeHoje>()
const carregando = ref(true)
const erro = ref('')
let cancelamento: AbortController | undefined

function dataLocalAtual() {
  const data = new Date()
  const ano = data.getFullYear()
  const mes = String(data.getMonth() + 1).padStart(2, '0')
  const dia = String(data.getDate()).padStart(2, '0')
  return `${ano}-${mes}-${dia}`
}

const dataConsultada = dataLocalAtual()
const dataFormatada = new Intl.DateTimeFormat('pt-BR', {
  weekday: 'long',
  day: '2-digit',
  month: 'long',
  year: 'numeric',
}).format(new Date(`${dataConsultada}T12:00:00`))

const linkDaSemana = computed(() => ({
  path: '/planejamento/semana',
  query: planejamento.value?.dataInicialDoPlano
    ? { inicio: planejamento.value.dataInicialDoPlano }
    : undefined,
}))

async function carregar() {
  cancelamento?.abort()
  const requisicao = new AbortController()
  cancelamento = requisicao
  carregando.value = true
  erro.value = ''
  try {
    planejamento.value = await obterPlanejamentoDeHoje(
      dataConsultada,
      requisicao.signal,
    )
  } catch (causa) {
    if (causa instanceof DOMException && causa.name === 'AbortError') return
    erro.value =
      causa instanceof Error
        ? causa.message
        : 'Não foi possível consultar o planejamento de hoje.'
  } finally {
    if (cancelamento === requisicao) carregando.value = false
  }
}

function rotuloDoTipo(bloco: BlocoDeEstudo) {
  return {
    TEORIA: 'Teoria',
    QUESTOES: 'Questões',
    REVISAO: 'Revisão',
    CADERNO_DE_ERROS: 'Caderno de erros',
    SIMULADO: 'Simulado',
    DISCURSIVA: 'Discursiva',
    OUTRA: 'Outra',
  }[bloco.tipoDeAtividade]
}

onMounted(carregar)
onBeforeUnmount(() => cancelamento?.abort())
</script>

<template>
  <main
    class="pagina-comum pagina-de-planejamento pagina-do-planejamento-de-hoje"
  >
    <NavegacaoDoPlanejamento />

    <CabecalhoDaPagina
      etiqueta="Planejamento diário"
      titulo="Hoje"
      :descricao="dataFormatada"
    >
      <template #acoes>
        <RouterLink class="btn btn-outline-primary" :to="linkDaSemana">
          <i class="bi bi-calendar-week me-2" aria-hidden="true"></i>
          Ver Semana
        </RouterLink>
      </template>
    </CabecalhoDaPagina>

    <EstadoDaPagina
      v-if="carregando"
      titulo="Carregando seu dia"
      descricao="Buscando os blocos planejados para hoje."
      :carregando="true"
    />

    <EstadoDaPagina
      v-else-if="erro"
      titulo="Não foi possível carregar seu dia"
      :descricao="erro"
      icone="bi-cloud-slash"
    >
      <button
        class="btn btn-outline-primary mt-3"
        type="button"
        @click="carregar"
      >
        Tentar novamente
      </button>
    </EstadoDaPagina>

    <EstadoDaPagina
      v-else-if="planejamento?.estado === 'SEM_PLANO'"
      titulo="Você ainda não planejou esta semana"
      descricao="Abra a Semana para informar sua disponibilidade e organizar os blocos."
      icone="bi-calendar-plus"
    >
      <RouterLink class="btn btn-primary mt-3" :to="linkDaSemana">
        Planejar minha semana
      </RouterLink>
    </EstadoDaPagina>

    <EstadoDaPagina
      v-else-if="planejamento?.estado === 'PLANO_EM_RASCUNHO'"
      titulo="Seu plano ainda precisa ser ativado"
      descricao="Revise a disponibilidade e os blocos na Semana antes de começar."
      icone="bi-pencil-square"
    >
      <RouterLink class="btn btn-primary mt-3" :to="linkDaSemana">
        Revisar e ativar plano
      </RouterLink>
    </EstadoDaPagina>

    <template v-else-if="planejamento">
      <section
        class="resumo-do-planejamento-de-hoje"
        aria-label="Resumo de hoje"
      >
        <div>
          <span>Disponível</span
          ><strong>{{ planejamento.minutosDisponiveis }} min</strong>
        </div>
        <div>
          <span>Planejado</span
          ><strong>{{ planejamento.minutosPlanejados }} min</strong>
        </div>
        <div>
          <span>Blocos</span
          ><strong>{{ planejamento.quantidadeDeBlocos }}</strong>
        </div>
      </section>

      <EstadoDaPagina
        v-if="planejamento.estado === 'DIA_SEM_BLOCOS'"
        titulo="Hoje não há blocos planejados"
        descricao="Sua semana está ativa, mas este dia ficou livre."
        icone="bi-cup-hot"
      />

      <section
        v-if="planejamento.atrasados.length"
        class="card sequencia-do-dia blocos-atrasados-do-dia"
      >
        <header>
          <p class="sobretitulo-da-pagina">Atenção</p>
          <h2>Pendentes de dias anteriores</h2>
        </header>
        <ol>
          <li
            v-for="bloco in planejamento.atrasados"
            :key="bloco.identificador"
          >
            <span><i class="bi bi-clock-history" aria-hidden="true"></i></span>
            <div>
              <strong>{{ bloco.titulo }}</strong>
              <small
                >{{ bloco.data }} ·
                {{ bloco.duracaoPrevistaEmMinutos }} min</small
              >
            </div>
          </li>
        </ol>
      </section>

      <div
        v-if="planejamento.estado === 'DIA_PLANEJADO'"
        class="conteudo-do-planejamento-de-hoje"
      >
        <section
          v-if="planejamento.proximoBloco"
          class="card proximo-bloco-do-dia"
        >
          <p class="sobretitulo-da-pagina">Próximo bloco</p>
          <h2>{{ planejamento.proximoBloco.titulo }}</h2>
          <p>
            {{ rotuloDoTipo(planejamento.proximoBloco) }} ·
            {{ planejamento.proximoBloco.duracaoPrevistaEmMinutos }} min
            <template v-if="planejamento.proximoBloco.horarioPrevisto">
              · {{ planejamento.proximoBloco.horarioPrevisto.slice(0, 5) }}
            </template>
          </p>
        </section>

        <section
          v-if="planejamento.sequencia.length"
          class="card sequencia-do-dia"
        >
          <header>
            <p class="sobretitulo-da-pagina">Depois</p>
            <h2>Sequência do dia</h2>
          </header>
          <ol>
            <li
              v-for="bloco in planejamento.sequencia"
              :key="bloco.identificador"
            >
              <span>{{ bloco.ordem }}</span>
              <div>
                <strong>{{ bloco.titulo }}</strong>
                <small
                  >{{ rotuloDoTipo(bloco) }} ·
                  {{ bloco.duracaoPrevistaEmMinutos }} min</small
                >
              </div>
            </li>
          </ol>
        </section>

        <RouterLink class="link-para-editar-a-semana" :to="linkDaSemana">
          Consultar a semana completa
          <i class="bi bi-arrow-right" aria-hidden="true"></i>
        </RouterLink>
      </div>

      <section
        v-if="planejamento.realizados.length"
        class="card sequencia-do-dia blocos-realizados-do-dia"
      >
        <header>
          <p class="sobretitulo-da-pagina">Progresso</p>
          <h2>Realizados hoje</h2>
        </header>
        <ol>
          <li
            v-for="bloco in planejamento.realizados"
            :key="bloco.identificador"
          >
            <span><i class="bi bi-check2" aria-hidden="true"></i></span>
            <div>
              <strong>{{ bloco.titulo }}</strong>
              <small>{{ bloco.duracaoPrevistaEmMinutos }} min planejados</small>
            </div>
          </li>
        </ol>
      </section>
    </template>
  </main>
</template>
