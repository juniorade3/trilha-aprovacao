<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

import { ErroDaApi } from '@/compartilhado/api/clienteHttp'
import CabecalhoDaPagina from '@/compartilhado/componentes/CabecalhoDaPagina.vue'
import EstadoDaPagina from '@/compartilhado/componentes/EstadoDaPagina.vue'
import ModalDaAplicacao from '@/compartilhado/componentes/ModalDaAplicacao.vue'
import CamposDeEvidencia from '@/modulos/estudos/CamposDeEvidencia.vue'
import {
  paraEvidencia,
  type ModeloDeEvidencia,
} from '@/modulos/estudos/apiDeEstudos'
import NavegacaoDoPlanejamento from './NavegacaoDoPlanejamento.vue'
import {
  consultarRevisoesEspacadas,
  type AgendaDeRevisoesEspacadas,
  type RevisaoEspacada,
} from './apiDeRevisoesEspacadas'
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
const roteador = useRouter()
const agendaDeRevisoes = ref<AgendaDeRevisoesEspacadas>()
const execucaoAtual = ref<ResultadoDaExecucaoDoBloco>()
const carregando = ref(true)
const carregandoRevisoes = ref(true)
const processando = ref(false)
const erro = ref('')
const erroDasRevisoes = ref('')
const contextoDasRevisoesIncompleto = ref(false)
const tituloDasRevisoes = ref<HTMLElement>()
const botaoDeRepetirRevisoes = ref<HTMLButtonElement>()
const conflito = ref(false)
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
const evidenciaDaExecucao = ref<ModeloDeEvidencia>({ padroesDeErro: [] })
const evidenciaDaCorrecao = ref<ModeloDeEvidencia>({ padroesDeErro: [] })
const agora = ref(Date.now())
const pausado = ref(false)
const pausaIniciadaEm = ref<number>()
const milissegundosPausados = ref(0)
let cancelamento: AbortController | undefined
let cancelamentoDasRevisoes: AbortController | undefined
let temporizador: number | undefined
const chaveDaPausa = 'trilha:planejamento:pausa'

function dataLocalAtual() {
  const partes = new Intl.DateTimeFormat('en-US', {
    timeZone: 'America/Sao_Paulo',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).formatToParts(new Date())
  const valor = (tipo: Intl.DateTimeFormatPartTypes) =>
    partes.find((parte) => parte.type === tipo)?.value ?? ''
  return `${valor('year')}-${valor('month')}-${valor('day')}`
}

const dataConsultada = dataLocalAtual()
const dataFormatada = new Intl.DateTimeFormat('pt-BR', {
  weekday: 'long',
  day: '2-digit',
  month: 'long',
  year: 'numeric',
  timeZone: 'America/Sao_Paulo',
}).format(new Date(`${dataConsultada}T12:00:00-03:00`))

const revisoesDeHoje = computed(() =>
  (agendaDeRevisoes.value?.revisoes ?? []).filter(
    (revisao) => revisao.situacao !== 'FUTURA',
  ),
)

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
  const fimDoIntervaloAtual = pausado.value
    ? (pausaIniciadaEm.value ?? agora.value)
    : agora.value
  return Math.max(
    0,
    Math.floor(
      (fimDoIntervaloAtual -
        new Date(execucaoAtual.value.execucao.iniciadaEm).getTime() -
        milissegundosPausados.value) /
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

const tipoExigeEvidencia = computed(() => {
  const tipo = execucaoAtual.value?.bloco.tipoDeAtividade
  return ['QUESTOES', 'SIMULADO', 'CADERNO_DE_ERROS', 'REVISAO'].includes(
    tipo ?? '',
  )
})

const finalizacaoValida = computed(() => {
  const bloco = execucaoAtual.value?.bloco
  if (!bloco) return true
  if (!bloco.identificadorDaMateria && !bloco.identificadorDoTopico) return true
  if (
    bloco.identificadorDaMateria &&
    !bloco.identificadorDoTopico &&
    (paraEvidencia(evidenciaDaExecucao.value) ||
      (acaoDeFinalizacao.value === 'CONCLUIR' && tipoExigeEvidencia.value)) &&
    !identificadorDoTopico.value
  )
    return false
  if (acaoDeFinalizacao.value === 'INTERROMPER') return true
  if (
    ['QUESTOES', 'SIMULADO', 'CADERNO_DE_ERROS'].includes(bloco.tipoDeAtividade)
  ) {
    const questoes = evidenciaDaExecucao.value.quantidadeDeQuestoes
    const acertos = evidenciaDaExecucao.value.quantidadeDeAcertos
    return (
      questoes != null &&
      questoes > 0 &&
      acertos != null &&
      acertos >= 0 &&
      acertos <= questoes
    )
  }
  return (
    bloco.tipoDeAtividade !== 'REVISAO' ||
    (evidenciaDaExecucao.value.nivelDeRecordacao ?? 0) >= 1
  )
})

const correcaoValida = computed(() => {
  if (!execucaoParaCorrigir.value) return true
  const bloco = execucaoParaCorrigir.value.bloco
  if (!bloco.identificadorDaMateria && !bloco.identificadorDoTopico) return true
  const tipo = bloco.tipoDeAtividade
  if (
    bloco.identificadorDaMateria &&
    !bloco.identificadorDoTopico &&
    !execucaoParaCorrigir.value.estudo?.identificadorDoTopico &&
    (paraEvidencia(evidenciaDaCorrecao.value) ||
      (resultadoCorrigido.value === 'CONCLUIDO' &&
        ['QUESTOES', 'SIMULADO', 'CADERNO_DE_ERROS', 'REVISAO'].includes(
          tipo,
        ))) &&
    !identificadorDoTopico.value
  )
    return false
  if (resultadoCorrigido.value === 'PARCIALMENTE_CONCLUIDO') return true
  if (['QUESTOES', 'SIMULADO', 'CADERNO_DE_ERROS'].includes(tipo)) {
    const questoes = evidenciaDaCorrecao.value.quantidadeDeQuestoes
    const acertos = evidenciaDaCorrecao.value.quantidadeDeAcertos
    return (
      questoes != null &&
      questoes > 0 &&
      acertos != null &&
      acertos >= 0 &&
      acertos <= questoes
    )
  }
  return (
    tipo !== 'REVISAO' ||
    (evidenciaDaCorrecao.value.nivelDeRecordacao ?? 0) >= 1
  )
})

async function carregar() {
  cancelamento?.abort()
  const requisicao = new AbortController()
  cancelamento = requisicao
  carregando.value = true
  erro.value = ''
  conflito.value = false
  try {
    planejamento.value = await obterPlanejamentoDeHoje(
      dataConsultada,
      requisicao.signal,
    )
    try {
      execucaoAtual.value = await obterExecucaoEmAndamento()
      restaurarPausa()
    } catch (causa) {
      if (causa instanceof ErroDaApi && causa.status === 404) {
        execucaoAtual.value = undefined
        limparPausa()
      } else throw causa
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

async function carregarRevisoes() {
  cancelamentoDasRevisoes?.abort()
  const requisicao = new AbortController()
  cancelamentoDasRevisoes = requisicao
  carregandoRevisoes.value = true
  erroDasRevisoes.value = ''
  contextoDasRevisoesIncompleto.value = false
  try {
    agendaDeRevisoes.value = await consultarRevisoesEspacadas(
      dataConsultada,
      dataConsultada,
      requisicao.signal,
    )
  } catch (causa) {
    if (causa instanceof DOMException && causa.name === 'AbortError') return
    agendaDeRevisoes.value = undefined
    if (causa instanceof ErroDaApi && causa.status === 422) {
      contextoDasRevisoesIncompleto.value = true
      return
    }
    erroDasRevisoes.value =
      causa instanceof ErroDaApi && causa.status === 401
        ? 'Sua sessão expirou. Entre novamente para consultar as revisões.'
        : causa instanceof Error
          ? causa.message
          : 'Não foi possível consultar as revisões de hoje.'
  } finally {
    if (cancelamentoDasRevisoes === requisicao) carregandoRevisoes.value = false
  }
}

async function recarregarPlanejamentoERevisoes() {
  await Promise.all([carregar(), carregarRevisoes()])
}

async function atualizarRevisoesAposEstudo() {
  await carregarRevisoes()
  await nextTick()
  tituloDasRevisoes.value?.focus()
}

async function repetirConsultaDasRevisoes() {
  await carregarRevisoes()
  await nextTick()
  if (erroDasRevisoes.value) botaoDeRepetirRevisoes.value?.focus()
  else tituloDasRevisoes.value?.focus()
}

function formatarDataCurta(data: string) {
  return new Intl.DateTimeFormat('pt-BR', {
    day: '2-digit',
    month: 'short',
    timeZone: 'America/Sao_Paulo',
  }).format(new Date(`${data}T12:00:00-03:00`))
}

function descricaoDaSituacaoDaRevisao(revisao: RevisaoEspacada) {
  if (revisao.situacao === 'JA_PLANEJADA')
    return revisao.blocoAberto
      ? `Já planejada para ${formatarDataCurta(revisao.blocoAberto.data)}`
      : 'Já planejada'
  if (revisao.situacao === 'VENCIDA')
    return `Vencida há ${revisao.diasEmAtraso} ${
      revisao.diasEmAtraso === 1 ? 'dia' : 'dias'
    }`
  return 'Devida hoje'
}

function abrirRegistroDaRevisao(revisao: RevisaoEspacada) {
  window.dispatchEvent(
    new CustomEvent('abrir-registro-rapido', {
      detail: {
        identificadorDaMateria: revisao.identificadorDaMateria,
        identificadorDoTopico: revisao.identificadorDoTopico,
        tipoDeEstudo: 'REVISAO',
      },
    }),
  )
}

async function irParaBlocoDaRevisao(revisao: RevisaoEspacada) {
  if (!revisao.blocoAberto) return
  await roteador.push({
    path: '/planejamento/semana',
    query: {
      inicio: revisao.blocoAberto.dataInicialDoPlano,
      foco: revisao.blocoAberto.identificador,
    },
  })
}

function registrarErro(causa: unknown, mensagemPadrao: string) {
  conflito.value = causa instanceof ErroDaApi && causa.status === 409
  erro.value = causa instanceof Error ? causa.message : mensagemPadrao
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
  conflito.value = false
  aviso.value = ''
  try {
    execucaoAtual.value = await iniciarBloco(
      bloco.identificador,
      dataConsultada,
    )
    limparPausa()
    agora.value = Date.now()
    aviso.value =
      'Bloco iniciado. O cronômetro continuará mesmo após recarregar.'
    await recarregarPlanejamentoERevisoes()
  } catch (causa) {
    registrarErro(causa, 'Não foi possível iniciar o bloco.')
  } finally {
    processando.value = false
  }
}

function persistirPausa() {
  const identificador = execucaoAtual.value?.execucao.identificador
  if (!identificador) return
  localStorage.setItem(
    chaveDaPausa,
    JSON.stringify({
      identificadorDaExecucao: identificador,
      pausado: pausado.value,
      pausaIniciadaEm: pausaIniciadaEm.value,
      milissegundosPausados: milissegundosPausados.value,
    }),
  )
}

function limparPausa() {
  pausado.value = false
  pausaIniciadaEm.value = undefined
  milissegundosPausados.value = 0
  localStorage.removeItem(chaveDaPausa)
}

function restaurarPausa() {
  const identificador = execucaoAtual.value?.execucao.identificador
  const pausaPersistida = localStorage.getItem(chaveDaPausa)
  pausado.value = false
  pausaIniciadaEm.value = undefined
  milissegundosPausados.value = 0
  if (!identificador || !pausaPersistida) return
  try {
    const pausa = JSON.parse(pausaPersistida) as {
      identificadorDaExecucao?: string
      pausado?: boolean
      pausaIniciadaEm?: number
      milissegundosPausados?: number
    }
    if (pausa.identificadorDaExecucao !== identificador) {
      localStorage.removeItem(chaveDaPausa)
      return
    }
    pausado.value = pausa.pausado === true
    pausaIniciadaEm.value =
      typeof pausa.pausaIniciadaEm === 'number'
        ? pausa.pausaIniciadaEm
        : undefined
    milissegundosPausados.value = Math.max(0, pausa.milissegundosPausados ?? 0)
  } catch {
    limparPausa()
  }
}

function alternarPausa() {
  if (!execucaoAtual.value) return
  const instante = Date.now()
  agora.value = instante
  if (pausado.value) {
    milissegundosPausados.value += Math.max(
      0,
      instante - (pausaIniciadaEm.value ?? instante),
    )
    pausaIniciadaEm.value = undefined
    pausado.value = false
    aviso.value = 'Cronômetro retomado.'
  } else {
    pausaIniciadaEm.value = instante
    pausado.value = true
    aviso.value = 'Cronômetro pausado.'
  }
  persistirPausa()
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
  evidenciaDaExecucao.value = { padroesDeErro: [] }
}

async function finalizar() {
  if (!execucaoAtual.value || !acaoDeFinalizacao.value) return
  processando.value = true
  erro.value = ''
  conflito.value = false
  try {
    const identificador = execucaoAtual.value.bloco.identificador
    const evidencia = paraEvidencia(evidenciaDaExecucao.value)
    let resultado: ResultadoDaExecucaoDoBloco
    const argumentos = [
      identificador,
      duracaoExecutada.value,
      observacaoDaExecucao.value || undefined,
      identificadorDoTopico.value || undefined,
    ] as const
    if (acaoDeFinalizacao.value === 'CONCLUIR')
      resultado = evidencia
        ? await concluirBloco(...argumentos, evidencia)
        : await concluirBloco(...argumentos)
    else
      resultado = evidencia
        ? await interromperBloco(...argumentos, evidencia)
        : await interromperBloco(...argumentos)
    ultimoResultado.value = resultado
    aviso.value = resultado.estudo
      ? 'Bloco finalizado e estudo registrado no Histórico.'
      : acaoDeFinalizacao.value === 'CONCLUIR'
        ? 'Bloco concluído.'
        : 'Bloco encerrado como parcialmente concluído.'
    execucaoAtual.value = undefined
    limparPausa()
    acaoDeFinalizacao.value = undefined
    await recarregarPlanejamentoERevisoes()
  } catch (causa) {
    registrarErro(causa, 'Não foi possível finalizar o bloco.')
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
  conflito.value = false
  try {
    const vinculado = await registrarExecucaoNoHistorico(
      resultado.execucao.identificador,
      identificadorDoTopico.value || undefined,
    )
    ultimoResultado.value = vinculado
    aviso.value = 'Estudo registrado no Histórico.'
    blocoParaHistorico.value = undefined
    await recarregarPlanejamentoERevisoes()
  } catch (causa) {
    registrarErro(causa, 'Não foi possível registrar o estudo no Histórico.')
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
  conflito.value = false
  try {
    await reagendarBloco(
      blocoParaReagendar.value.identificador,
      dataDoReagendamento.value,
      horarioDoReagendamento.value || undefined,
      Number(ordemDoReagendamento.value),
    )
    blocoParaReagendar.value = undefined
    aviso.value = 'Bloco reagendado dentro desta semana.'
    await recarregarPlanejamentoERevisoes()
  } catch (causa) {
    registrarErro(causa, 'Não foi possível reagendar.')
  } finally {
    processando.value = false
  }
}

async function confirmarCancelamento() {
  if (!blocoParaCancelar.value) return
  processando.value = true
  erro.value = ''
  conflito.value = false
  try {
    await cancelarBloco(blocoParaCancelar.value.identificador)
    blocoParaCancelar.value = undefined
    aviso.value = 'Bloco cancelado e preservado no planejamento.'
    await recarregarPlanejamentoERevisoes()
  } catch (causa) {
    registrarErro(causa, 'Não foi possível cancelar.')
  } finally {
    processando.value = false
  }
}

async function abrirCorrecao(bloco: BlocoDeEstudo) {
  const resultado = execucoesRealizadas.value[bloco.identificador]
  if (!resultado) return
  topicosParaRegistro.value = []
  identificadorDoTopico.value =
    resultado.estudo?.identificadorDoTopico ?? bloco.identificadorDoTopico ?? ''
  if (
    bloco.identificadorDaMateria &&
    !bloco.identificadorDoTopico &&
    !resultado.estudo?.identificadorDoTopico
  ) {
    topicosParaRegistro.value = await listarTopicosParaRegistro(
      bloco.identificador,
    )
  }
  execucaoParaCorrigir.value = resultado
  resultadoCorrigido.value = resultado.execucao.resultado ?? 'CONCLUIDO'
  duracaoCorrigida.value = resultado.execucao.duracaoExecutadaEmMinutos ?? 1
  observacaoCorrigida.value = resultado.execucao.observacao ?? ''
  evidenciaDaCorrecao.value = {
    quantidadeDeQuestoes:
      resultado.evidencia?.resultadoDeQuestoes?.quantidadeDeQuestoes,
    quantidadeDeAcertos:
      resultado.evidencia?.resultadoDeQuestoes?.quantidadeDeAcertos,
    nivelDeRecordacao: resultado.evidencia?.nivelDeRecordacao,
    dificuldadePercebida: resultado.evidencia?.dificuldadePercebida,
    padroesDeErro:
      resultado.evidencia?.padroesDeErro?.map((padrao) => ({ ...padrao })) ??
      [],
  }
}

async function confirmarCorrecao() {
  if (!execucaoParaCorrigir.value) return
  processando.value = true
  erro.value = ''
  conflito.value = false
  try {
    const argumentos = [
      execucaoParaCorrigir.value.execucao.identificador,
      resultadoCorrigido.value,
      Number(duracaoCorrigida.value),
      observacaoCorrigida.value || undefined,
    ] as const
    const evidencia = paraEvidencia(evidenciaDaCorrecao.value)
    ultimoResultado.value = await corrigirExecucao(
      ...argumentos,
      identificadorDoTopico.value || undefined,
      evidencia,
    )
    execucaoParaCorrigir.value = undefined
    aviso.value =
      'Execução corrigida; o Histórico foi atualizado quando vinculado.'
    await recarregarPlanejamentoERevisoes()
  } catch (causa) {
    registrarErro(causa, 'Não foi possível corrigir a execução.')
  } finally {
    processando.value = false
  }
}

onMounted(() => {
  carregar()
  carregarRevisoes()
  window.addEventListener('estudo-registrado', atualizarRevisoesAposEstudo)
  temporizador = window.setInterval(() => {
    agora.value = Date.now()
  }, 1000)
})

onBeforeUnmount(() => {
  cancelamento?.abort()
  cancelamentoDasRevisoes?.abort()
  window.removeEventListener('estudo-registrado', atualizarRevisoesAposEstudo)
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

    <div v-if="erro" class="alert alert-danger" role="alert">
      {{ erro }}
      <button
        v-if="conflito"
        class="btn btn-sm btn-outline-danger ms-2"
        type="button"
        @click="carregar"
      >
        Recarregar dados
      </button>
    </div>
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

    <section
      class="card fila-de-revisoes-de-hoje"
      aria-labelledby="titulo-das-revisoes-de-hoje"
      aria-live="polite"
    >
      <header>
        <div>
          <p class="sobretitulo-da-pagina">Memória em dia</p>
          <h2
            id="titulo-das-revisoes-de-hoje"
            ref="tituloDasRevisoes"
            tabindex="-1"
          >
            Revisões de hoje
          </h2>
        </div>
        <span v-if="!carregandoRevisoes" class="quantidade-de-revisoes">
          {{ revisoesDeHoje.length }}
        </span>
      </header>

      <p v-if="carregandoRevisoes" class="estado-das-revisoes">
        <span
          class="spinner-border spinner-border-sm"
          aria-hidden="true"
        ></span>
        Calculando sua fila...
      </p>

      <div v-else-if="erroDasRevisoes" class="alert alert-danger mb-0">
        {{ erroDasRevisoes }}
        <button
          v-if="!erroDasRevisoes.includes('sessão expirou')"
          ref="botaoDeRepetirRevisoes"
          class="btn btn-sm btn-outline-danger ms-2"
          type="button"
          @click="repetirConsultaDasRevisoes"
        >
          Tentar novamente
        </button>
      </div>

      <div
        v-else-if="contextoDasRevisoesIncompleto"
        class="estado-das-revisoes"
      >
        <i class="bi bi-bullseye" aria-hidden="true"></i>
        <p>
          <strong>Complete o contexto do concurso.</strong>
          <span>
            Ative um concurso, selecione o cargo e defina o edital principal
            para calcular as revisões exigidas.
          </span>
        </p>
        <RouterLink class="btn btn-sm btn-outline-primary" to="/concursos">
          Revisar concurso
        </RouterLink>
      </div>

      <p v-else-if="revisoesDeHoje.length === 0" class="estado-das-revisoes">
        <i class="bi bi-check2-circle" aria-hidden="true"></i>
        Nenhuma revisão vencida ou devida hoje.
      </p>

      <ol v-else class="lista-de-revisoes-de-hoje">
        <li
          v-for="revisao in revisoesDeHoje"
          :key="revisao.identificadorDoTopico"
        >
          <div class="identificacao-da-revisao">
            <span
              class="situacao-da-revisao"
              :class="`situacao-${revisao.situacao.toLowerCase()}`"
            >
              {{ descricaoDaSituacaoDaRevisao(revisao) }}
            </span>
            <strong>{{ revisao.nomeDoTopico }}</strong>
            <small>
              {{ revisao.nomeDaMateria }} · Etapa {{ revisao.etapa }} ·
              intervalo de {{ revisao.intervaloEmDias }} dias
            </small>
            <small v-if="revisao.ultimaRecordacao">
              Última recordação: {{ revisao.ultimaRecordacao }}/5
            </small>
          </div>
          <button
            v-if="!revisao.blocoAberto"
            class="btn btn-sm btn-primary"
            type="button"
            :aria-label="`Revisar agora: ${revisao.nomeDoTopico}`"
            @click="abrirRegistroDaRevisao(revisao)"
          >
            Revisar agora
          </button>
          <button
            v-else
            class="btn btn-sm btn-outline-primary"
            type="button"
            :aria-label="`Ir para o bloco: ${revisao.nomeDoTopico}`"
            @click="irParaBlocoDaRevisao(revisao)"
          >
            Ir para o bloco
          </button>
        </li>
      </ol>
    </section>

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
        <span v-if="pausado" class="mt-2">Cronômetro pausado</span>
        <div class="d-flex flex-wrap gap-2 mt-3">
          <button
            class="btn btn-outline-light"
            type="button"
            :disabled="processando"
            @click="alternarPausa"
          >
            <i
              class="bi"
              :class="pausado ? 'bi-play-fill' : 'bi-pause-fill'"
              aria-hidden="true"
            ></i>
            {{ pausado ? 'Retomar' : 'Pausar' }}
          </button>
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
          <option
            value=""
            :disabled="acaoDeFinalizacao === 'CONCLUIR' && tipoExigeEvidencia"
          >
            {{
              acaoDeFinalizacao === 'CONCLUIR' && tipoExigeEvidencia
                ? 'Selecione o tópico para registrar o resultado'
                : 'Concluir sem registrar no Histórico'
            }}
          </option>
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
      <CamposDeEvidencia
        v-if="
          execucaoAtual.bloco.identificadorDaMateria ||
          execucaoAtual.bloco.identificadorDoTopico
        "
        v-model="evidenciaDaExecucao"
        :tipo="execucaoAtual.bloco.tipoDeAtividade"
        :identificador-do-topico="
          execucaoAtual.bloco.identificadorDoTopico || identificadorDoTopico
        "
        :interrupcao="acaoDeFinalizacao === 'INTERROMPER'"
      />
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
            processando ||
            duracaoExecutada < 1 ||
            duracaoExecutada > 1440 ||
            !finalizacaoValida
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
      <div
        v-if="
          execucaoParaCorrigir.bloco.identificadorDaMateria &&
          !execucaoParaCorrigir.bloco.identificadorDoTopico &&
          !execucaoParaCorrigir.estudo?.identificadorDoTopico
        "
        class="mb-3"
      >
        <label class="form-label" for="topico-da-correcao"
          >Tópico estudado</label
        >
        <select
          id="topico-da-correcao"
          v-model="identificadorDoTopico"
          class="form-select"
        >
          <option value="">Corrigir sem registrar no Histórico</option>
          <option
            v-for="topico in topicosParaRegistro"
            :key="topico.identificador"
            :value="topico.identificador"
          >
            {{ topico.nome }}
          </option>
        </select>
      </div>
      <div
        v-else-if="
          !execucaoParaCorrigir.bloco.identificadorDaMateria &&
          !execucaoParaCorrigir.bloco.identificadorDoTopico
        "
        class="alert alert-secondary"
      >
        Esta atividade livre será corrigida sem evidência por tópico.
      </div>
      <CamposDeEvidencia
        v-if="
          execucaoParaCorrigir.bloco.identificadorDaMateria ||
          execucaoParaCorrigir.bloco.identificadorDoTopico
        "
        v-model="evidenciaDaCorrecao"
        :tipo="execucaoParaCorrigir.bloco.tipoDeAtividade"
        :identificador-do-topico="
          execucaoParaCorrigir.estudo?.identificadorDoTopico ||
          execucaoParaCorrigir.bloco.identificadorDoTopico ||
          identificadorDoTopico
        "
        :interrupcao="resultadoCorrigido === 'PARCIALMENTE_CONCLUIDO'"
      />
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
            processando ||
            duracaoCorrigida < 1 ||
            duracaoCorrigida > 1440 ||
            !correcaoValida
          "
          @click="confirmarCorrecao"
        >
          Salvar correção
        </button>
      </template>
    </ModalDaAplicacao>
  </main>
</template>
