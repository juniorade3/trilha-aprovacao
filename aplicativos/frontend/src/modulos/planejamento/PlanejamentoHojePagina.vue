<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'

import { ErroDaApi } from '@/compartilhado/api/clienteHttp'
import CabecalhoDaPagina from '@/compartilhado/componentes/CabecalhoDaPagina.vue'
import EstadoDaPagina from '@/compartilhado/componentes/EstadoDaPagina.vue'
import ModalDaAplicacao from '@/compartilhado/componentes/ModalDaAplicacao.vue'
import NavegacaoDoPlanejamento from './NavegacaoDoPlanejamento.vue'
import {
  concluirBloco,
  iniciarBloco,
  interromperBloco,
  listarTopicosParaRegistro,
  obterExecucaoDoBloco,
  obterExecucaoEmAndamento,
  registrarExecucaoNoHistorico,
  obterPlanejamentoDeHoje,
  cancelarBloco,
  corrigirExecucao,
  reagendarBloco,
  type BlocoDeEstudo,
  type PlanejamentoDeHoje,
  type ResultadoDaExecucaoDoBloco,
  type TopicoParaRegistro,
} from './apiDePlanejamento'

const planejamento = ref<PlanejamentoDeHoje>()
const execucaoAtual = ref<ResultadoDaExecucaoDoBloco>()
const carregando = ref(true)
const processando = ref(false)
const erro = ref('')
const aviso = ref('')
const acaoDeFinalizacao = ref<'CONCLUIR' | 'INTERROMPER'>()
const duracaoExecutada = ref(1)
const observacaoDaExecucao = ref('')
const topicosParaRegistro = ref<TopicoParaRegistro[]>([])
const identificadorDoTopico = ref('')
const ultimoResultado = ref<ResultadoDaExecucaoDoBloco>()
const execucoesRealizadas = ref<Record<string, ResultadoDaExecucaoDoBloco>>({})
const blocoParaHistorico = ref<BlocoDeEstudo>()
const registrandoHistorico = ref(false)
const blocoParaReagendar = ref<BlocoDeEstudo>()
const blocoParaCancelar = ref<BlocoDeEstudo>()
const dataDoReagendamento = ref('')
const horarioDoReagendamento = ref('')
const ordemDoReagendamento = ref(1)
const execucaoParaCorrigir = ref<ResultadoDaExecucaoDoBloco>()
const resultadoCorrigido = ref<'CONCLUIDO' | 'PARCIALMENTE_CONCLUIDO'>(
  'CONCLUIDO',
)
const duracaoCorrigida = ref(1)
const observacaoCorrigida = ref('')
const agora = ref(Date.now())
let cancelamento: AbortController | undefined
let temporizador: number | undefined

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

const datasDaSemana = computed(() => {
  if (!planejamento.value?.dataInicialDoPlano) return []
  return Array.from({ length: 7 }, (_, indice) => {
    const data = new Date(`${planejamento.value!.dataInicialDoPlano}T12:00:00`)
    data.setDate(data.getDate() + indice)
    return data.toISOString().slice(0, 10)
  })
})

const segundosDecorridos = computed(() => {
  if (!execucaoAtual.value) return 0
  return Math.max(
    0,
    Math.floor(
      (agora.value -
        new Date(execucaoAtual.value.execucao.iniciadaEm).getTime()) /
        1000,
    ),
  )
})

const cronometro = computed(() => {
  const total = segundosDecorridos.value
  const horas = Math.floor(total / 3600)
  const minutos = Math.floor((total % 3600) / 60)
  const segundos = total % 60
  return [horas, minutos, segundos]
    .map((valor) => String(valor).padStart(2, '0'))
    .join(':')
})

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
    try {
      execucaoAtual.value = await obterExecucaoEmAndamento()
    } catch (causa) {
      if (causa instanceof ErroDaApi && causa.status === 404)
        execucaoAtual.value = undefined
      else throw causa
    }
    const pares = await Promise.all(
      planejamento.value.realizados.map(async (bloco) => {
        try {
          return [
            bloco.identificador,
            await obterExecucaoDoBloco(bloco.identificador),
          ] as const
        } catch {
          return undefined
        }
      }),
    )
    execucoesRealizadas.value = Object.fromEntries(
      pares.filter(
        (par): par is readonly [string, ResultadoDaExecucaoDoBloco] => !!par,
      ),
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

async function iniciar(bloco: BlocoDeEstudo) {
  processando.value = true
  erro.value = ''
  aviso.value = ''
  try {
    execucaoAtual.value = await iniciarBloco(
      bloco.identificador,
      dataConsultada,
    )
    agora.value = Date.now()
    aviso.value =
      'Bloco iniciado. O cronômetro continuará mesmo após recarregar.'
    await carregar()
  } catch (causa) {
    erro.value =
      causa instanceof Error
        ? causa.message
        : 'Não foi possível iniciar o bloco.'
  } finally {
    processando.value = false
  }
}

async function abrirFinalizacao(acao: 'CONCLUIR' | 'INTERROMPER') {
  acaoDeFinalizacao.value = acao
  topicosParaRegistro.value = []
  identificadorDoTopico.value = ''
  const bloco = execucaoAtual.value?.bloco
  if (bloco?.identificadorDaMateria && !bloco.identificadorDoTopico) {
    topicosParaRegistro.value = await listarTopicosParaRegistro(
      bloco.identificador,
    )
  }
  duracaoExecutada.value = Math.max(
    1,
    Math.round(segundosDecorridos.value / 60),
  )
  observacaoDaExecucao.value = ''
}

async function finalizar() {
  if (!execucaoAtual.value || !acaoDeFinalizacao.value) return
  processando.value = true
  erro.value = ''
  try {
    const identificador = execucaoAtual.value.bloco.identificador
    let resultado: ResultadoDaExecucaoDoBloco
    if (acaoDeFinalizacao.value === 'CONCLUIR')
      resultado = await concluirBloco(
        identificador,
        duracaoExecutada.value,
        observacaoDaExecucao.value || undefined,
        identificadorDoTopico.value || undefined,
      )
    else
      resultado = await interromperBloco(
        identificador,
        duracaoExecutada.value,
        observacaoDaExecucao.value || undefined,
        identificadorDoTopico.value || undefined,
      )
    ultimoResultado.value = resultado
    aviso.value = resultado.estudo
      ? 'Bloco finalizado e estudo registrado no Histórico.'
      : acaoDeFinalizacao.value === 'CONCLUIR'
        ? 'Bloco concluído.'
        : 'Bloco encerrado como parcialmente concluído.'
    execucaoAtual.value = undefined
    acaoDeFinalizacao.value = undefined
    await carregar()
  } catch (causa) {
    erro.value =
      causa instanceof Error
        ? causa.message
        : 'Não foi possível finalizar o bloco.'
  } finally {
    processando.value = false
  }
}

async function abrirRegistroNoHistorico(bloco: BlocoDeEstudo) {
  blocoParaHistorico.value = bloco
  identificadorDoTopico.value = ''
  topicosParaRegistro.value = await listarTopicosParaRegistro(
    bloco.identificador,
  )
}

async function registrarNoHistorico() {
  if (!blocoParaHistorico.value) return
  const resultado =
    execucoesRealizadas.value[blocoParaHistorico.value.identificador]
  if (!resultado) return
  registrandoHistorico.value = true
  erro.value = ''
  try {
    const vinculado = await registrarExecucaoNoHistorico(
      resultado.execucao.identificador,
      identificadorDoTopico.value || undefined,
    )
    ultimoResultado.value = vinculado
    aviso.value = 'Estudo registrado no Histórico.'
    blocoParaHistorico.value = undefined
    await carregar()
  } catch (causa) {
    erro.value =
      causa instanceof Error
        ? causa.message
        : 'Não foi possível registrar o estudo no Histórico.'
  } finally {
    registrandoHistorico.value = false
  }
}

function abrirReagendamento(bloco: BlocoDeEstudo) {
  blocoParaReagendar.value = bloco
  dataDoReagendamento.value = bloco.data
  horarioDoReagendamento.value = bloco.horarioPrevisto?.slice(0, 5) ?? ''
  ordemDoReagendamento.value = bloco.ordem
}

async function confirmarReagendamento() {
  if (!blocoParaReagendar.value) return
  processando.value = true
  erro.value = ''
  try {
    await reagendarBloco(
      blocoParaReagendar.value.identificador,
      dataDoReagendamento.value,
      horarioDoReagendamento.value || undefined,
      Number(ordemDoReagendamento.value),
    )
    blocoParaReagendar.value = undefined
    aviso.value = 'Bloco reagendado dentro desta semana.'
    await carregar()
  } catch (causa) {
    erro.value =
      causa instanceof Error ? causa.message : 'Não foi possível reagendar.'
  } finally {
    processando.value = false
  }
}

async function confirmarCancelamento() {
  if (!blocoParaCancelar.value) return
  processando.value = true
  erro.value = ''
  try {
    await cancelarBloco(blocoParaCancelar.value.identificador)
    blocoParaCancelar.value = undefined
    aviso.value = 'Bloco cancelado e preservado no planejamento.'
    await carregar()
  } catch (causa) {
    erro.value =
      causa instanceof Error ? causa.message : 'Não foi possível cancelar.'
  } finally {
    processando.value = false
  }
}

function abrirCorrecao(bloco: BlocoDeEstudo) {
  const resultado = execucoesRealizadas.value[bloco.identificador]
  if (!resultado) return
  execucaoParaCorrigir.value = resultado
  resultadoCorrigido.value = resultado.execucao.resultado ?? 'CONCLUIDO'
  duracaoCorrigida.value = resultado.execucao.duracaoExecutadaEmMinutos ?? 1
  observacaoCorrigida.value = resultado.execucao.observacao ?? ''
}

async function confirmarCorrecao() {
  if (!execucaoParaCorrigir.value) return
  processando.value = true
  erro.value = ''
  try {
    ultimoResultado.value = await corrigirExecucao(
      execucaoParaCorrigir.value.execucao.identificador,
      resultadoCorrigido.value,
      Number(duracaoCorrigida.value),
      observacaoCorrigida.value || undefined,
    )
    execucaoParaCorrigir.value = undefined
    aviso.value =
      'Execução corrigida; o Histórico foi atualizado quando vinculado.'
    await carregar()
  } catch (causa) {
    erro.value =
      causa instanceof Error
        ? causa.message
        : 'Não foi possível corrigir a execução.'
  } finally {
    processando.value = false
  }
}

onMounted(() => {
  carregar()
  temporizador = window.setInterval(() => {
    agora.value = Date.now()
  }, 1000)
})

onBeforeUnmount(() => {
  cancelamento?.abort()
  window.clearInterval(temporizador)
})
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

    <div v-if="erro" class="alert alert-danger" role="alert">{{ erro }}</div>
    <div v-if="aviso" class="alert alert-success" role="status">
      {{ aviso }}
      <RouterLink
        v-if="ultimoResultado?.estudo"
        class="alert-link ms-2"
        to="/estudos"
      >
        Ver no Histórico
      </RouterLink>
    </div>

    <EstadoDaPagina
      v-if="carregando"
      titulo="Carregando seu dia"
      descricao="Buscando os blocos planejados para hoje."
      :carregando="true"
    />

    <EstadoDaPagina
      v-else-if="!planejamento"
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
      v-else-if="planejamento.estado === 'SEM_PLANO'"
      titulo="Você ainda não planejou esta semana"
      descricao="Abra a Semana para informar sua disponibilidade e organizar os blocos."
      icone="bi-calendar-plus"
    >
      <RouterLink class="btn btn-primary mt-3" :to="linkDaSemana"
        >Planejar minha semana</RouterLink
      >
    </EstadoDaPagina>

    <EstadoDaPagina
      v-else-if="planejamento.estado === 'PLANO_EM_RASCUNHO'"
      titulo="Seu plano ainda precisa ser ativado"
      descricao="Revise a disponibilidade e os blocos na Semana antes de começar."
      icone="bi-pencil-square"
    >
      <RouterLink class="btn btn-primary mt-3" :to="linkDaSemana"
        >Revisar e ativar plano</RouterLink
      >
    </EstadoDaPagina>

    <EstadoDaPagina
      v-else-if="planejamento.estado === 'PLANO_ENCERRADO'"
      titulo="Esta semana foi encerrada"
      descricao="Consulte na Semana os blocos realizados e os que ficaram não realizados."
      icone="bi-calendar-check"
    >
      <RouterLink class="btn btn-primary mt-3" :to="linkDaSemana">
        Ver semana encerrada
      </RouterLink>
    </EstadoDaPagina>

    <EstadoDaPagina
      v-else-if="planejamento.estado === 'PLANO_CANCELADO'"
      titulo="Este plano foi cancelado"
      descricao="Execuções e estudos realizados antes do cancelamento foram preservados."
      icone="bi-calendar-x"
    >
      <RouterLink class="btn btn-outline-primary mt-3" :to="linkDaSemana">
        Ver plano cancelado
      </RouterLink>
    </EstadoDaPagina>

    <template v-else>
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

      <section
        v-if="execucaoAtual"
        class="card proximo-bloco-do-dia bloco-em-andamento"
        aria-live="polite"
      >
        <p class="sobretitulo-da-pagina">Em andamento</p>
        <h2>{{ execucaoAtual.bloco.titulo }}</h2>
        <p>
          {{ rotuloDoTipo(execucaoAtual.bloco) }} ·
          {{ execucaoAtual.bloco.duracaoPrevistaEmMinutos }} min planejados
        </p>
        <strong class="cronometro-da-execucao" aria-label="Tempo decorrido">{{
          cronometro
        }}</strong>
        <div class="d-flex flex-wrap gap-2 mt-3">
          <button
            class="btn btn-primary"
            type="button"
            :disabled="processando"
            @click="abrirFinalizacao('CONCLUIR')"
          >
            Concluir
          </button>
          <button
            class="btn btn-outline-primary"
            type="button"
            :disabled="processando"
            @click="abrirFinalizacao('INTERROMPER')"
          >
            Interromper
          </button>
        </div>
      </section>

      <EstadoDaPagina
        v-if="planejamento.estado === 'DIA_SEM_BLOCOS' && !execucaoAtual"
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
              <strong>{{ bloco.titulo }}</strong
              ><small
                >{{ bloco.data }} ·
                {{ bloco.duracaoPrevistaEmMinutos }} min</small
              >
            </div>
            <button
              class="btn btn-sm btn-outline-primary"
              type="button"
              :disabled="processando || !!execucaoAtual"
              @click="iniciar(bloco)"
            >
              Iniciar
            </button>
            <button
              class="btn btn-sm btn-outline-secondary"
              type="button"
              :disabled="processando"
              :aria-label="`Reagendar ${bloco.titulo}`"
              @click="abrirReagendamento(bloco)"
            >
              Reagendar
            </button>
            <button
              class="btn btn-sm btn-outline-danger"
              type="button"
              :disabled="processando"
              :aria-label="`Cancelar ${bloco.titulo}`"
              @click="blocoParaCancelar = bloco"
            >
              Cancelar
            </button>
          </li>
        </ol>
      </section>

      <div
        v-if="planejamento.estado === 'DIA_PLANEJADO'"
        class="conteudo-do-planejamento-de-hoje"
      >
        <section
          v-if="planejamento.proximoBloco && !execucaoAtual"
          class="card proximo-bloco-do-dia"
        >
          <p class="sobretitulo-da-pagina">Próximo bloco</p>
          <h2>{{ planejamento.proximoBloco.titulo }}</h2>
          <p>
            {{ rotuloDoTipo(planejamento.proximoBloco) }} ·
            {{ planejamento.proximoBloco.duracaoPrevistaEmMinutos }} min
          </p>
          <button
            class="btn btn-primary mt-3"
            type="button"
            :disabled="processando"
            @click="iniciar(planejamento.proximoBloco)"
          >
            Iniciar estudo
          </button>
          <div class="d-flex flex-wrap gap-2 mt-2">
            <button
              class="btn btn-sm btn-outline-secondary"
              type="button"
              :aria-label="`Reagendar ${planejamento.proximoBloco.titulo}`"
              @click="abrirReagendamento(planejamento.proximoBloco)"
            >
              Reagendar
            </button>
            <button
              class="btn btn-sm btn-outline-danger"
              type="button"
              :aria-label="`Cancelar ${planejamento.proximoBloco.titulo}`"
              @click="blocoParaCancelar = planejamento.proximoBloco"
            >
              Cancelar
            </button>
          </div>
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
                <strong>{{ bloco.titulo }}</strong
                ><small
                  >{{ rotuloDoTipo(bloco) }} ·
                  {{ bloco.duracaoPrevistaEmMinutos }} min</small
                >
              </div>
              <div class="d-flex gap-2">
                <button
                  class="btn btn-sm btn-outline-secondary"
                  type="button"
                  :aria-label="`Reagendar ${bloco.titulo}`"
                  @click="abrirReagendamento(bloco)"
                >
                  Reagendar
                </button>
                <button
                  class="btn btn-sm btn-outline-danger"
                  type="button"
                  :aria-label="`Cancelar ${bloco.titulo}`"
                  @click="blocoParaCancelar = bloco"
                >
                  Cancelar
                </button>
              </div>
            </li>
          </ol>
        </section>
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
              <strong>{{ bloco.titulo }}</strong
              ><small>{{
                bloco.estado === 'CONCLUIDO'
                  ? 'Concluído'
                  : 'Parcialmente concluído'
              }}</small>
            </div>
            <button
              v-if="
                execucoesRealizadas[bloco.identificador] &&
                !execucoesRealizadas[bloco.identificador]?.execucao
                  .identificadorDoRegistroDeEstudo &&
                (bloco.identificadorDoTopico || bloco.identificadorDaMateria)
              "
              class="btn btn-sm btn-outline-primary"
              type="button"
              @click="abrirRegistroNoHistorico(bloco)"
            >
              Registrar no Histórico
            </button>
            <button
              v-if="execucoesRealizadas[bloco.identificador]"
              class="btn btn-sm btn-outline-secondary"
              type="button"
              :aria-label="`Corrigir execução de ${bloco.titulo}`"
              @click="abrirCorrecao(bloco)"
            >
              Corrigir execução
            </button>
          </li>
        </ol>
      </section>
    </template>

    <ModalDaAplicacao
      v-if="acaoDeFinalizacao && execucaoAtual"
      :titulo="
        acaoDeFinalizacao === 'CONCLUIR'
          ? 'Concluir bloco?'
          : 'Interromper bloco?'
      "
      etiqueta="Registrar execução"
      descricao="Ao concluir, o estudo será registrado no Histórico quando houver um tópico."
      @fechar="acaoDeFinalizacao = undefined"
    >
      <div
        v-if="execucaoAtual.bloco.identificadorDoTopico"
        class="alert alert-info"
      >
        O tópico planejado será usado no Histórico.
      </div>
      <div v-else-if="execucaoAtual.bloco.identificadorDaMateria" class="mb-3">
        <label class="form-label" for="topico-da-execucao"
          >Tópico estudado</label
        >
        <select
          id="topico-da-execucao"
          v-model="identificadorDoTopico"
          class="form-select"
        >
          <option value="">Concluir sem registrar no Histórico</option>
          <option
            v-for="topico in topicosParaRegistro"
            :key="topico.identificador"
            :value="topico.identificador"
          >
            {{ topico.nome }}
          </option>
        </select>
      </div>
      <div v-else class="alert alert-secondary">
        Esta atividade livre será concluída sem registro no Histórico.
      </div>
      <div class="mb-3">
        <label class="form-label" for="duracao-executada"
          >Duração realizada em minutos</label
        >
        <input
          id="duracao-executada"
          v-model.number="duracaoExecutada"
          class="form-control"
          type="number"
          min="1"
          max="1440"
          required
        />
      </div>
      <div>
        <label class="form-label" for="observacao-execucao"
          >Observação opcional</label
        >
        <textarea
          id="observacao-execucao"
          v-model="observacaoDaExecucao"
          class="form-control"
          maxlength="2000"
          rows="3"
        ></textarea>
      </div>
      <template #rodape>
        <button
          class="btn btn-outline-secondary"
          type="button"
          @click="acaoDeFinalizacao = undefined"
        >
          Voltar
        </button>
        <button
          class="btn btn-primary"
          type="button"
          :disabled="
            processando || duracaoExecutada < 1 || duracaoExecutada > 1440
          "
          @click="finalizar"
        >
          Registrar
        </button>
      </template>
    </ModalDaAplicacao>

    <ModalDaAplicacao
      v-if="blocoParaHistorico"
      titulo="Registrar no Histórico?"
      etiqueta="Execução concluída"
      descricao="Escolha o tópico estudado para criar um único registro no Histórico."
      @fechar="blocoParaHistorico = undefined"
    >
      <div
        v-if="blocoParaHistorico.identificadorDoTopico"
        class="alert alert-info"
      >
        O tópico planejado será usado automaticamente.
      </div>
      <div v-else class="mb-3">
        <label class="form-label" for="topico-do-historico"
          >Tópico estudado</label
        >
        <select
          id="topico-do-historico"
          v-model="identificadorDoTopico"
          class="form-select"
          required
        >
          <option value="" disabled>Selecione</option>
          <option
            v-for="topico in topicosParaRegistro"
            :key="topico.identificador"
            :value="topico.identificador"
          >
            {{ topico.nome }}
          </option>
        </select>
      </div>
      <template #rodape>
        <button
          class="btn btn-outline-secondary"
          type="button"
          @click="blocoParaHistorico = undefined"
        >
          Voltar
        </button>
        <button
          class="btn btn-primary"
          type="button"
          :disabled="
            registrandoHistorico ||
            (!blocoParaHistorico.identificadorDoTopico &&
              !identificadorDoTopico)
          "
          @click="registrarNoHistorico"
        >
          Registrar no Histórico
        </button>
      </template>
    </ModalDaAplicacao>

    <ModalDaAplicacao
      v-if="blocoParaReagendar"
      titulo="Reagendar bloco"
      etiqueta="Ajustar compromisso"
      descricao="O bloco permanecerá nesta mesma semana."
      @fechar="blocoParaReagendar = undefined"
    >
      <div class="mb-3">
        <label class="form-label" for="data-reagendamento-hoje"
          >Nova data</label
        >
        <select
          id="data-reagendamento-hoje"
          v-model="dataDoReagendamento"
          class="form-select"
        >
          <option v-for="data in datasDaSemana" :key="data" :value="data">
            {{ data }}
          </option>
        </select>
      </div>
      <div class="row g-3">
        <div class="col-sm-6">
          <label class="form-label" for="horario-reagendamento-hoje"
            >Horário opcional</label
          >
          <input
            id="horario-reagendamento-hoje"
            v-model="horarioDoReagendamento"
            class="form-control"
            type="time"
          />
        </div>
        <div class="col-sm-6">
          <label class="form-label" for="ordem-reagendamento-hoje"
            >Ordem no dia</label
          >
          <input
            id="ordem-reagendamento-hoje"
            v-model.number="ordemDoReagendamento"
            class="form-control"
            type="number"
            min="1"
          />
        </div>
      </div>
      <template #rodape>
        <button
          class="btn btn-outline-secondary"
          type="button"
          @click="blocoParaReagendar = undefined"
        >
          Voltar
        </button>
        <button
          class="btn btn-primary"
          type="button"
          :disabled="processando"
          @click="confirmarReagendamento"
        >
          Confirmar reagendamento
        </button>
      </template>
    </ModalDaAplicacao>

    <ModalDaAplicacao
      v-if="blocoParaCancelar"
      titulo="Cancelar bloco?"
      etiqueta="Confirmar cancelamento"
      :descricao="`O bloco ${blocoParaCancelar.titulo} ficará registrado como cancelado.`"
      @fechar="blocoParaCancelar = undefined"
    >
      <p class="mb-0">A ordem dos blocos restantes será ajustada.</p>
      <template #rodape>
        <button
          class="btn btn-outline-secondary"
          type="button"
          @click="blocoParaCancelar = undefined"
        >
          Manter bloco
        </button>
        <button
          class="btn btn-danger"
          type="button"
          :disabled="processando"
          @click="confirmarCancelamento"
        >
          Cancelar bloco
        </button>
      </template>
    </ModalDaAplicacao>

    <ModalDaAplicacao
      v-if="execucaoParaCorrigir"
      titulo="Corrigir execução"
      etiqueta="Ajustar fato registrado"
      descricao="Se houver estudo vinculado, o Histórico manterá a versão anterior como corrigida."
      @fechar="execucaoParaCorrigir = undefined"
    >
      <div class="mb-3">
        <label class="form-label" for="resultado-corrigido">Resultado</label>
        <select
          id="resultado-corrigido"
          v-model="resultadoCorrigido"
          class="form-select"
        >
          <option value="CONCLUIDO">Concluído</option>
          <option value="PARCIALMENTE_CONCLUIDO">Parcialmente concluído</option>
        </select>
      </div>
      <div class="mb-3">
        <label class="form-label" for="duracao-corrigida"
          >Duração em minutos</label
        >
        <input
          id="duracao-corrigida"
          v-model.number="duracaoCorrigida"
          class="form-control"
          type="number"
          min="1"
          max="1440"
        />
      </div>
      <div>
        <label class="form-label" for="observacao-corrigida"
          >Observação opcional</label
        >
        <textarea
          id="observacao-corrigida"
          v-model="observacaoCorrigida"
          class="form-control"
          maxlength="2000"
          rows="3"
        ></textarea>
      </div>
      <template #rodape>
        <button
          class="btn btn-outline-secondary"
          type="button"
          @click="execucaoParaCorrigir = undefined"
        >
          Voltar
        </button>
        <button
          class="btn btn-primary"
          type="button"
          :disabled="
            processando || duracaoCorrigida < 1 || duracaoCorrigida > 1440
          "
          @click="confirmarCorrecao"
        >
          Salvar correção
        </button>
      </template>
    </ModalDaAplicacao>
  </main>
</template>
