<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { ErroDaApi } from '@/compartilhado/api/clienteHttp'
import CabecalhoDaPagina from '@/compartilhado/componentes/CabecalhoDaPagina.vue'
import EstadoDaPagina from '@/compartilhado/componentes/EstadoDaPagina.vue'
import ModalDaAplicacao from '@/compartilhado/componentes/ModalDaAplicacao.vue'
import {
  listarTodasAsMaterias,
  listarTodosOsTopicos,
  type Materia,
  type Topico,
} from '@/modulos/materias/apiDeConteudos'
import EditorDeBloco from './EditorDeBloco.vue'
import GavetaDeGeracaoDeterministica from './GavetaDeGeracaoDeterministica.vue'
import NavegacaoDoPlanejamento from './NavegacaoDoPlanejamento.vue'
import {
  adicionarBloco,
  alterarBloco,
  alterarDisponibilidades,
  ativarPlanoSemanal,
  criarPlanoSemanal,
  excluirBloco,
  cancelarBloco,
  reagendarBloco,
  encerrarPlanoSemanal,
  cancelarPlanoSemanal,
  obterPlanoSemanal,
  reordenarBlocos,
  type BlocoDeEstudo,
  type DadosDoBlocoDeEstudo,
  type PlanoSemanal,
} from './apiDePlanejamento'

const rota = useRoute()
const roteador = useRouter()
const plano = ref<PlanoSemanal>()
const carregando = ref(true)
const salvando = ref(false)
const criando = ref(false)
const erro = ref('')
const aviso = ref('')
const conflito = ref(false)
const formulario = reactive<Record<string, number>>({})
const materias = ref<Materia[]>([])
const topicos = ref<Topico[]>([])
const editorAberto = ref(false)
const blocoEmEdicao = ref<BlocoDeEstudo>()
const blocoParaExcluir = ref<BlocoDeEstudo>()
const blocoParaReagendar = ref<BlocoDeEstudo>()
const reagendamento = reactive({ data: '', horarioPrevisto: '', ordem: 1 })
const acaoDoPlano = ref<'ENCERRAR' | 'CANCELAR'>()
const dataSugerida = ref('')
const salvandoBloco = ref(false)
const excluindoBloco = ref(false)
const erroDoEditor = ref('')
const confirmacaoDeAtivacaoAberta = ref(false)
const ativando = ref(false)
const geracaoAberta = ref(false)

const nomesDosDias = [
  'Segunda-feira',
  'Terça-feira',
  'Quarta-feira',
  'Quinta-feira',
  'Sexta-feira',
  'Sábado',
  'Domingo',
]

function paraIso(data: Date) {
  const ano = data.getFullYear()
  const mes = String(data.getMonth() + 1).padStart(2, '0')
  const dia = String(data.getDate()).padStart(2, '0')
  return `${ano}-${mes}-${dia}`
}

function inicioDaSemanaAtual() {
  const hoje = new Date()
  const deslocamento = hoje.getDay() === 0 ? -6 : 1 - hoje.getDay()
  hoje.setDate(hoje.getDate() + deslocamento)
  return paraIso(hoje)
}

function adicionarDias(dataIso: string, quantidade: number) {
  const data = new Date(`${dataIso}T12:00:00`)
  data.setDate(data.getDate() + quantidade)
  return paraIso(data)
}

function inicioValido(valor: unknown): valor is string {
  if (typeof valor !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(valor))
    return false
  const data = new Date(`${valor}T12:00:00`)
  return !Number.isNaN(data.getTime()) && data.getDay() === 1
}

const dataInicial = computed(() => {
  const valor = rota.query.inicio
  return inicioValido(valor) ? valor : inicioDaSemanaAtual()
})

const datasDaSemana = computed(() =>
  Array.from({ length: 7 }, (_, indice) =>
    adicionarDias(dataInicial.value, indice),
  ),
)

const totalDeMinutos = computed(() =>
  datasDaSemana.value.reduce(
    (total, data) => total + Number(formulario[data] ?? 0),
    0,
  ),
)

const totalFormatado = computed(() => {
  const horas = Math.floor(totalDeMinutos.value / 60)
  const minutos = totalDeMinutos.value % 60
  if (horas === 0) return `${minutos} min`
  if (minutos === 0) return `${horas}h`
  return `${horas}h ${minutos}min`
})

const quantidadesPorData = computed(() =>
  Object.fromEntries(
    datasDaSemana.value.map((data) => [data, blocosDaData(data).length]),
  ),
)

const periodo = computed(() => {
  const formatador = new Intl.DateTimeFormat('pt-BR', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
  })
  return `${formatador.format(new Date(`${dataInicial.value}T12:00:00`))} a ${formatador.format(new Date(`${adicionarDias(dataInicial.value, 6)}T12:00:00`))}`
})

const pendenciasDaAtivacao = computed(() => {
  if (!plano.value) return []
  const pendencias: string[] = []
  if (plano.value.totalDeMinutosDisponiveis === 0)
    pendencias.push('Informe disponibilidade em pelo menos um dia.')
  if (plano.value.quantidadeDeBlocos === 0)
    pendencias.push('Adicione pelo menos um bloco de estudo.')
  if (plano.value.possuiExcesso)
    pendencias.push('Corrija os dias em que a carga supera a disponibilidade.')
  return pendencias
})

function formatarData(dataIso: string) {
  return new Intl.DateTimeFormat('pt-BR', {
    day: '2-digit',
    month: 'long',
  }).format(new Date(`${dataIso}T12:00:00`))
}

function preencherFormulario(planoObtido: PlanoSemanal) {
  for (const chave of Object.keys(formulario)) delete formulario[chave]
  for (const disponibilidade of planoObtido.disponibilidades)
    formulario[disponibilidade.data] = disponibilidade.minutosDisponiveis
}

async function carregarConteudos() {
  try {
    materias.value = await listarTodasAsMaterias('', false)
    const grupos = await Promise.all(
      materias.value.map((materia) =>
        listarTodosOsTopicos(materia.identificador, false),
      ),
    )
    topicos.value = grupos.flat()
  } catch {
    materias.value = []
    topicos.value = []
  }
}

async function carregar() {
  carregando.value = true
  erro.value = ''
  aviso.value = ''
  conflito.value = false
  plano.value = undefined
  try {
    const planoObtido = await obterPlanoSemanal(dataInicial.value)
    plano.value = planoObtido
    preencherFormulario(planoObtido)
    await carregarConteudos()
  } catch (causa) {
    if (!(causa instanceof ErroDaApi && causa.status === 404)) {
      erro.value =
        causa instanceof Error
          ? causa.message
          : 'Não foi possível carregar o planejamento.'
    }
  } finally {
    carregando.value = false
  }
}

function blocosDaData(data: string) {
  return (plano.value?.blocos ?? [])
    .filter((bloco) => bloco.data === data)
    .sort((primeiro, segundo) => primeiro.ordem - segundo.ordem)
}

function cargaPlanejada(data: string) {
  return blocosDaData(data).reduce(
    (total, bloco) => total + bloco.duracaoPrevistaEmMinutos,
    0,
  )
}

function excessoDoDia(data: string) {
  return Math.max(0, cargaPlanejada(data) - Number(formulario[data] ?? 0))
}

function nomeDaMateria(bloco: BlocoDeEstudo) {
  return materias.value.find(
    (materia) => materia.identificador === bloco.identificadorDaMateria,
  )?.nome
}

function nomeDoTopico(bloco: BlocoDeEstudo) {
  return topicos.value.find(
    (topico) => topico.identificador === bloco.identificadorDoTopico,
  )?.nome
}

function rotuloDoTipo(tipo: BlocoDeEstudo['tipoDeAtividade']) {
  return {
    TEORIA: 'Teoria',
    QUESTOES: 'Questões',
    REVISAO: 'Revisão',
    CADERNO_DE_ERROS: 'Caderno de erros',
    SIMULADO: 'Simulado',
    DISCURSIVA: 'Discursiva',
    OUTRA: 'Outra',
  }[tipo]
}

function abrirNovoBloco(data: string) {
  blocoEmEdicao.value = undefined
  dataSugerida.value = data
  erroDoEditor.value = ''
  editorAberto.value = true
}

function abrirEdicaoDoBloco(bloco: BlocoDeEstudo) {
  blocoEmEdicao.value = bloco
  dataSugerida.value = bloco.data
  erroDoEditor.value = ''
  editorAberto.value = true
}

function abrirReagendamento(bloco: BlocoDeEstudo) {
  blocoParaReagendar.value = bloco
  reagendamento.data = bloco.data
  reagendamento.horarioPrevisto = bloco.horarioPrevisto?.slice(0, 5) ?? ''
  reagendamento.ordem = bloco.ordem
}

async function confirmarReagendamento() {
  if (!blocoParaReagendar.value) return
  salvandoBloco.value = true
  erro.value = ''
  try {
    await reagendarBloco(
      blocoParaReagendar.value.identificador,
      reagendamento.data,
      reagendamento.horarioPrevisto || undefined,
      Number(reagendamento.ordem),
    )
    await atualizarPlano()
    blocoParaReagendar.value = undefined
    aviso.value = 'Bloco reagendado e ordem dos dias atualizada.'
  } catch (causa) {
    conflito.value = causa instanceof ErroDaApi && causa.status === 409
    erro.value =
      causa instanceof Error ? causa.message : 'Não foi possível reagendar.'
  } finally {
    salvandoBloco.value = false
  }
}

async function atualizarPlano() {
  const atualizado = await obterPlanoSemanal(dataInicial.value)
  plano.value = atualizado
  preencherFormulario(atualizado)
}

async function salvarBloco(dados: DadosDoBlocoDeEstudo) {
  if (!plano.value) return
  salvandoBloco.value = true
  erroDoEditor.value = ''
  try {
    if (blocoEmEdicao.value)
      await alterarBloco(blocoEmEdicao.value.identificador, dados)
    else await adicionarBloco(plano.value.identificador, dados)
    await atualizarPlano()
    editorAberto.value = false
    aviso.value = blocoEmEdicao.value
      ? 'Bloco atualizado.'
      : 'Bloco adicionado.'
  } catch (causa) {
    erroDoEditor.value =
      causa instanceof Error
        ? causa.message
        : 'Não foi possível salvar o bloco.'
  } finally {
    salvandoBloco.value = false
  }
}

async function confirmarExclusao() {
  if (!blocoParaExcluir.value) return
  excluindoBloco.value = true
  erro.value = ''
  try {
    if (plano.value?.estado === 'ATIVO') {
      await cancelarBloco(blocoParaExcluir.value.identificador)
      aviso.value = 'Bloco cancelado e ordem do dia atualizada.'
    } else {
      await excluirBloco(blocoParaExcluir.value.identificador)
      aviso.value = 'Bloco excluído e ordem do dia atualizada.'
    }
    await atualizarPlano()
    blocoParaExcluir.value = undefined
  } catch (causa) {
    erro.value =
      causa instanceof Error
        ? causa.message
        : 'Não foi possível excluir o bloco.'
    blocoParaExcluir.value = undefined
  } finally {
    excluindoBloco.value = false
  }
}

async function moverBloco(bloco: BlocoDeEstudo, deslocamento: number) {
  if (!plano.value) return
  const ordenados = blocosDaData(bloco.data)
  const indice = ordenados.findIndex(
    (item) => item.identificador === bloco.identificador,
  )
  const destino = indice + deslocamento
  if (indice < 0 || destino < 0 || destino >= ordenados.length) return
  ;[ordenados[indice], ordenados[destino]] = [
    ordenados[destino]!,
    ordenados[indice]!,
  ]
  erro.value = ''
  try {
    const atualizado = await reordenarBlocos(
      plano.value.identificador,
      bloco.data,
      ordenados.map((item) => item.identificador),
    )
    plano.value = atualizado
    preencherFormulario(atualizado)
    aviso.value = 'Ordem dos blocos atualizada.'
  } catch (causa) {
    erro.value =
      causa instanceof Error
        ? causa.message
        : 'Não foi possível reordenar os blocos.'
  }
}

async function criar() {
  criando.value = true
  erro.value = ''
  try {
    const planoCriado = await criarPlanoSemanal(dataInicial.value)
    plano.value = planoCriado
    preencherFormulario(planoCriado)
    aviso.value = 'Plano semanal criado em rascunho.'
  } catch (causa) {
    if (causa instanceof ErroDaApi && causa.status === 409) {
      await carregar()
      return
    }
    erro.value =
      causa instanceof Error ? causa.message : 'Não foi possível criar o plano.'
  } finally {
    criando.value = false
  }
}

async function salvar() {
  if (!plano.value) return
  salvando.value = true
  erro.value = ''
  aviso.value = ''
  conflito.value = false
  try {
    const planoAtualizado = await alterarDisponibilidades(
      plano.value.identificador,
      datasDaSemana.value.map((data) => ({
        data,
        minutosDisponiveis: Number(formulario[data] ?? 0),
      })),
    )
    plano.value = planoAtualizado
    preencherFormulario(planoAtualizado)
    aviso.value = 'Disponibilidade salva.'
  } catch (causa) {
    conflito.value = causa instanceof ErroDaApi && causa.status === 409
    erro.value =
      causa instanceof Error
        ? causa.message
        : 'Não foi possível salvar a disponibilidade.'
  } finally {
    salvando.value = false
  }
}

async function ativar() {
  if (!plano.value) return
  ativando.value = true
  erro.value = ''
  conflito.value = false
  try {
    plano.value = await ativarPlanoSemanal(plano.value.identificador)
    preencherFormulario(plano.value)
    aviso.value = 'Plano ativado. A semana agora está pronta para execução.'
    confirmacaoDeAtivacaoAberta.value = false
  } catch (causa) {
    conflito.value = causa instanceof ErroDaApi && causa.status === 409
    erro.value =
      causa instanceof Error
        ? causa.message
        : 'Não foi possível ativar o plano.'
    confirmacaoDeAtivacaoAberta.value = false
  } finally {
    ativando.value = false
  }
}

async function encerrarPlano() {
  if (!plano.value) return
  erro.value = ''
  conflito.value = false
  try {
    plano.value = await encerrarPlanoSemanal(plano.value.identificador)
    aviso.value =
      'Semana encerrada. Blocos pendentes foram preservados como não realizados.'
    acaoDoPlano.value = undefined
  } catch (causa) {
    conflito.value = causa instanceof ErroDaApi && causa.status === 409
    erro.value =
      causa instanceof Error ? causa.message : 'Não foi possível encerrar.'
  }
}

async function cancelarPlano() {
  if (!plano.value) return
  erro.value = ''
  conflito.value = false
  try {
    plano.value = await cancelarPlanoSemanal(plano.value.identificador)
    aviso.value = 'Plano cancelado. Execuções e estudos foram preservados.'
    acaoDoPlano.value = undefined
  } catch (causa) {
    conflito.value = causa instanceof ErroDaApi && causa.status === 409
    erro.value =
      causa instanceof Error ? causa.message : 'Não foi possível cancelar.'
  }
}

async function navegar(quantidadeDeDias: number) {
  await roteador.push({
    path: '/planejamento/semana',
    query: { inicio: adicionarDias(dataInicial.value, quantidadeDeDias) },
  })
}

async function irParaSemanaAtual() {
  await roteador.push({
    path: '/planejamento/semana',
    query: { inicio: inicioDaSemanaAtual() },
  })
}

watch(
  () => rota.query.inicio,
  async (inicio) => {
    if (!inicioValido(inicio)) {
      await roteador.replace({
        path: '/planejamento/semana',
        query: { inicio: inicioDaSemanaAtual() },
      })
      return
    }
    await carregar()
  },
  { immediate: true },
)
</script>

<template>
  <main class="pagina-comum pagina-de-planejamento">
    <NavegacaoDoPlanejamento />

    <CabecalhoDaPagina
      etiqueta="Planejamento manual"
      titulo="Sua semana"
      :descricao="
        plano?.estado === 'ATIVO'
          ? 'Consulte o compromisso de estudo ativo desta semana.'
          : 'Defina quanto tempo você tem disponível em cada dia.'
      "
    />

    <section class="seletor-da-semana" aria-label="Selecionar semana">
      <button
        class="btn btn-outline-primary"
        type="button"
        aria-label="Semana anterior"
        @click="navegar(-7)"
      >
        <i class="bi bi-chevron-left" aria-hidden="true"></i>
      </button>
      <div>
        <span>Semana selecionada</span>
        <strong>{{ periodo }}</strong>
      </div>
      <button
        class="btn btn-sm btn-link"
        type="button"
        @click="irParaSemanaAtual"
      >
        Semana atual
      </button>
      <button
        class="btn btn-outline-primary"
        type="button"
        aria-label="Próxima semana"
        @click="navegar(7)"
      >
        <i class="bi bi-chevron-right" aria-hidden="true"></i>
      </button>
    </section>

    <div v-if="erro && plano" class="alert alert-danger" role="alert">
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
    </div>

    <EstadoDaPagina
      v-if="carregando"
      titulo="Carregando planejamento"
      descricao="Buscando os dados desta semana."
      :carregando="true"
    />

    <EstadoDaPagina
      v-else-if="erro && !plano"
      titulo="Não foi possível carregar o planejamento"
      :descricao="erro"
      icone="bi-exclamation-circle"
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
      v-else-if="!plano && !erro"
      titulo="Esta semana ainda não tem um plano"
      descricao="Crie um rascunho para informar sua disponibilidade diária."
      icone="bi-calendar-plus"
    >
      <button
        class="btn btn-primary mt-3"
        type="button"
        :disabled="criando"
        @click="criar"
      >
        <span
          v-if="criando"
          class="spinner-border spinner-border-sm me-2"
          aria-hidden="true"
        ></span>
        Criar plano desta semana
      </button>
    </EstadoDaPagina>

    <form
      v-else-if="plano"
      class="formulario-de-disponibilidade"
      @submit.prevent="salvar"
    >
      <div class="resumo-do-plano-semanal">
        <div>
          <span class="rotulo-do-resumo">Estado</span>
          <strong
            class="badge"
            :class="
              plano.estado === 'ATIVO' ? 'text-bg-success' : 'text-bg-light'
            "
            >{{
              plano.estado === 'ATIVO'
                ? 'Ativo'
                : plano.estado === 'RASCUNHO'
                  ? 'Rascunho'
                  : plano.estado === 'ENCERRADO'
                    ? 'Encerrado'
                    : 'Cancelado'
            }}</strong
          >
        </div>
        <div>
          <span class="rotulo-do-resumo">Total disponível</span>
          <strong>{{ totalFormatado }}</strong>
        </div>
        <div>
          <span class="rotulo-do-resumo">Total planejado</span>
          <strong>{{ plano.totalDeMinutosPlanejados }} min</strong>
        </div>
        <div
          v-if="plano.estado === 'ATIVO'"
          class="acoes-do-plano-semanal d-flex gap-2"
        >
          <button
            class="btn btn-outline-primary"
            type="button"
            @click="acaoDoPlano = 'ENCERRAR'"
          >
            Encerrar semana
          </button>
          <button
            class="btn btn-outline-danger"
            type="button"
            @click="acaoDoPlano = 'CANCELAR'"
          >
            Cancelar plano
          </button>
        </div>
        <div
          v-if="plano.estado === 'RASCUNHO'"
          class="acoes-do-plano-semanal d-flex gap-2"
        >
          <button
            class="btn btn-outline-primary"
            type="button"
            @click="geracaoAberta = true"
          >
            <i class="bi bi-stars me-2" aria-hidden="true"></i>
            Gerar semana
          </button>
          <button
            class="acao-principal-do-plano btn btn-primary"
            type="button"
            @click="confirmacaoDeAtivacaoAberta = true"
          >
            <i class="bi bi-check2-circle me-2" aria-hidden="true"></i>
            Ativar plano
          </button>
        </div>
      </div>

      <fieldset
        :disabled="
          salvando ||
          plano.estado === 'ENCERRADO' ||
          plano.estado === 'CANCELADO'
        "
      >
        <legend class="visually-hidden">Disponibilidade por dia</legend>
        <div class="grade-de-disponibilidades grade-dos-dias-planejados">
          <article
            v-for="(data, indice) in datasDaSemana"
            :key="data"
            class="cartao-de-disponibilidade"
          >
            <header class="cabecalho-do-dia-planejado">
              <div>
                <strong class="dia-da-disponibilidade">{{
                  nomesDosDias[indice]
                }}</strong>
                <span class="data-da-disponibilidade">{{
                  formatarData(data)
                }}</span>
              </div>
              <button
                v-if="plano.estado === 'RASCUNHO'"
                class="btn btn-sm btn-outline-primary"
                type="button"
                :aria-label="`Adicionar bloco em ${nomesDosDias[indice]}`"
                @click="abrirNovoBloco(data)"
              >
                <i class="bi bi-plus-lg" aria-hidden="true"></i>
                Bloco
              </button>
            </header>

            <label
              class="disponibilidade-do-dia"
              :for="`disponibilidade-${data}`"
            >
              <span>Disponível</span>
              <span class="campo-de-minutos">
                <input
                  v-if="plano.estado === 'RASCUNHO' || plano.estado === 'ATIVO'"
                  :id="`disponibilidade-${data}`"
                  v-model.number="formulario[data]"
                  class="form-control"
                  type="number"
                  inputmode="numeric"
                  min="0"
                  max="1440"
                  step="1"
                  required
                />
                <strong v-else>{{ formulario[data] ?? 0 }}</strong>
                <span>min</span>
              </span>
            </label>

            <div
              class="resumo-da-carga-do-dia"
              :class="{ 'com-excesso': excessoDoDia(data) > 0 }"
            >
              <span
                >{{ cargaPlanejada(data) }} planejados /
                {{ formulario[data] ?? 0 }} disponíveis</span
              >
              <strong v-if="excessoDoDia(data) > 0"
                >Excesso de {{ excessoDoDia(data) }} min</strong
              >
            </div>

            <ol v-if="blocosDaData(data).length" class="lista-de-blocos-do-dia">
              <li
                v-for="(bloco, posicao) in blocosDaData(data)"
                :key="bloco.identificador"
              >
                <div class="conteudo-do-bloco-planejado">
                  <span class="ordem-do-bloco">{{ bloco.ordem }}</span>
                  <div>
                    <strong>{{ bloco.titulo }}</strong>
                    <span>
                      {{ rotuloDoTipo(bloco.tipoDeAtividade) }} ·
                      {{ bloco.duracaoPrevistaEmMinutos }} min
                      <template v-if="bloco.horarioPrevisto">
                        · {{ bloco.horarioPrevisto.slice(0, 5) }}</template
                      >
                    </span>
                    <small v-if="nomeDaMateria(bloco)">
                      {{ nomeDaMateria(bloco)
                      }}<template v-if="nomeDoTopico(bloco)">
                        — {{ nomeDoTopico(bloco) }}</template
                      >
                    </small>
                    <small v-if="bloco.quantidadeDeReagendamentos">
                      Reagendado {{ bloco.quantidadeDeReagendamentos }}
                      {{
                        bloco.quantidadeDeReagendamentos === 1 ? 'vez' : 'vezes'
                      }}
                    </small>
                    <small
                      v-if="
                        plano.estado === 'ENCERRADO' &&
                        bloco.estado === 'PLANEJADO'
                      "
                      class="text-danger"
                    >
                      Não realizado
                    </small>
                  </div>
                </div>
                <div
                  v-if="
                    (plano.estado === 'RASCUNHO' || plano.estado === 'ATIVO') &&
                    bloco.estado === 'PLANEJADO'
                  "
                  class="acoes-do-bloco-planejado"
                >
                  <button
                    v-if="plano.estado === 'RASCUNHO'"
                    class="botao-de-icone"
                    type="button"
                    :disabled="posicao === 0"
                    :aria-label="`Mover ${bloco.titulo} para cima`"
                    @click="moverBloco(bloco, -1)"
                  >
                    <i class="bi bi-arrow-up" aria-hidden="true"></i>
                  </button>
                  <button
                    v-if="plano.estado === 'RASCUNHO'"
                    class="botao-de-icone"
                    type="button"
                    :disabled="posicao === blocosDaData(data).length - 1"
                    :aria-label="`Mover ${bloco.titulo} para baixo`"
                    @click="moverBloco(bloco, 1)"
                  >
                    <i class="bi bi-arrow-down" aria-hidden="true"></i>
                  </button>
                  <button
                    class="botao-de-icone"
                    type="button"
                    :aria-label="`Editar ${bloco.titulo}`"
                    @click="abrirEdicaoDoBloco(bloco)"
                  >
                    <i class="bi bi-pencil" aria-hidden="true"></i>
                  </button>
                  <button
                    v-if="plano.estado === 'ATIVO'"
                    class="botao-de-icone"
                    type="button"
                    :aria-label="`Reagendar ${bloco.titulo}`"
                    @click="abrirReagendamento(bloco)"
                  >
                    <i class="bi bi-calendar-event" aria-hidden="true"></i>
                  </button>
                  <button
                    class="botao-de-icone text-danger"
                    type="button"
                    :aria-label="`${
                      plano.estado === 'ATIVO' ? 'Cancelar' : 'Excluir'
                    } ${bloco.titulo}`"
                    @click="blocoParaExcluir = bloco"
                  >
                    <i class="bi bi-trash" aria-hidden="true"></i>
                  </button>
                </div>
              </li>
            </ol>
            <p v-else class="dia-sem-blocos">
              Nenhum bloco planejado neste dia.
            </p>
          </article>
        </div>
      </fieldset>

      <div
        v-if="plano.estado === 'RASCUNHO' || plano.estado === 'ATIVO'"
        class="acoes-da-disponibilidade"
      >
        <p>
          Você pode ajustar os minutos; em plano ativo, a carga ainda planejada
          precisa caber.
        </p>
        <button class="btn btn-primary" type="submit" :disabled="salvando">
          <span
            v-if="salvando"
            class="spinner-border spinner-border-sm me-2"
            aria-hidden="true"
          ></span>
          Salvar disponibilidade
        </button>
      </div>
    </form>

    <GavetaDeGeracaoDeterministica
      v-if="geracaoAberta && plano"
      :identificador-do-plano="plano.identificador"
      @fechar="geracaoAberta = false"
    />

    <EditorDeBloco
      v-if="
        editorAberto &&
        (plano?.estado === 'RASCUNHO' || plano?.estado === 'ATIVO')
      "
      :bloco="blocoEmEdicao"
      :data-inicial="plano.dataInicial"
      :datas-da-semana="datasDaSemana"
      :data-sugerida="dataSugerida"
      :quantidades-por-data="quantidadesPorData"
      :materias="materias"
      :topicos="topicos"
      :salvando="salvandoBloco"
      :edicao-de-plano-ativo="plano.estado === 'ATIVO'"
      :erro="erroDoEditor"
      @fechar="editorAberto = false"
      @salvar="salvarBloco"
    />

    <ModalDaAplicacao
      v-if="confirmacaoDeAtivacaoAberta && plano"
      titulo="Ativar plano semanal?"
      etiqueta="Confirmar compromisso"
      descricao="Depois de ativado, os blocos planejados ainda poderão ser editados, reagendados ou cancelados."
      @fechar="confirmacaoDeAtivacaoAberta = false"
    >
      <div v-if="pendenciasDaAtivacao.length" class="alert alert-warning mb-0">
        <strong>Antes de ativar:</strong>
        <ul class="mb-0 mt-2">
          <li v-for="pendencia in pendenciasDaAtivacao" :key="pendencia">
            {{ pendencia }}
          </li>
        </ul>
      </div>
      <p v-else class="mb-0">
        O plano ficará disponível para execução e continuará visível na Semana.
      </p>
      <template #rodape>
        <button
          class="btn btn-outline-secondary"
          type="button"
          @click="confirmacaoDeAtivacaoAberta = false"
        >
          Voltar
        </button>
        <button
          v-if="!pendenciasDaAtivacao.length"
          class="btn btn-primary"
          type="button"
          :disabled="ativando"
          @click="ativar"
        >
          Confirmar ativação
        </button>
      </template>
    </ModalDaAplicacao>

    <ModalDaAplicacao
      v-if="blocoParaExcluir"
      :titulo="plano?.estado === 'ATIVO' ? 'Cancelar bloco?' : 'Excluir bloco?'"
      :etiqueta="
        plano?.estado === 'ATIVO'
          ? 'Confirmar cancelamento'
          : 'Confirmar exclusão'
      "
      :descricao="
        plano?.estado === 'ATIVO'
          ? `O bloco ${blocoParaExcluir.titulo} ficará registrado como cancelado.`
          : `O bloco ${blocoParaExcluir.titulo} será removido do rascunho.`
      "
      @fechar="blocoParaExcluir = undefined"
    >
      <p>A ordem dos demais blocos do dia será ajustada automaticamente.</p>
      <template #rodape>
        <button
          class="btn btn-outline-secondary"
          type="button"
          @click="blocoParaExcluir = undefined"
        >
          Manter bloco
        </button>
        <button
          class="btn btn-danger"
          type="button"
          :disabled="excluindoBloco"
          @click="confirmarExclusao"
        >
          {{ plano?.estado === 'ATIVO' ? 'Cancelar bloco' : 'Excluir bloco' }}
        </button>
      </template>
    </ModalDaAplicacao>

    <ModalDaAplicacao
      v-if="blocoParaReagendar"
      titulo="Reagendar bloco"
      etiqueta="Ajustar compromisso"
      descricao="Escolha outra posição dentro da mesma semana."
      @fechar="blocoParaReagendar = undefined"
    >
      <div class="mb-3">
        <label class="form-label" for="data-reagendamento">Nova data</label>
        <select
          id="data-reagendamento"
          v-model="reagendamento.data"
          class="form-select"
        >
          <option v-for="data in datasDaSemana" :key="data" :value="data">
            {{ data }}
          </option>
        </select>
      </div>
      <div class="row g-3">
        <div class="col-sm-6">
          <label class="form-label" for="horario-reagendamento"
            >Horário opcional</label
          >
          <input
            id="horario-reagendamento"
            v-model="reagendamento.horarioPrevisto"
            class="form-control"
            type="time"
          />
        </div>
        <div class="col-sm-6">
          <label class="form-label" for="ordem-reagendamento"
            >Ordem no dia</label
          >
          <input
            id="ordem-reagendamento"
            v-model.number="reagendamento.ordem"
            class="form-control"
            type="number"
            min="1"
            :max="(quantidadesPorData[reagendamento.data] ?? 0) + 1"
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
          :disabled="salvandoBloco"
          @click="confirmarReagendamento"
        >
          Confirmar reagendamento
        </button>
      </template>
    </ModalDaAplicacao>

    <ModalDaAplicacao
      v-if="acaoDoPlano"
      :titulo="
        acaoDoPlano === 'ENCERRAR' ? 'Encerrar semana?' : 'Cancelar plano?'
      "
      etiqueta="Confirmar mudança de estado"
      :descricao="
        acaoDoPlano === 'ENCERRAR'
          ? 'Os blocos pendentes serão preservados como não realizados e o plano ficará somente para leitura.'
          : 'Os blocos planejados serão cancelados; execuções e estudos serão preservados.'
      "
      @fechar="acaoDoPlano = undefined"
    >
      <p class="mb-0">Esta ação não poderá ser desfeita nesta versão.</p>
      <template #rodape>
        <button
          class="btn btn-outline-secondary"
          type="button"
          @click="acaoDoPlano = undefined"
        >
          Voltar
        </button>
        <button
          class="btn"
          :class="acaoDoPlano === 'ENCERRAR' ? 'btn-primary' : 'btn-danger'"
          type="button"
          @click="
            acaoDoPlano === 'ENCERRAR' ? encerrarPlano() : cancelarPlano()
          "
        >
          {{
            acaoDoPlano === 'ENCERRAR' ? 'Encerrar semana' : 'Cancelar plano'
          }}
        </button>
      </template>
    </ModalDaAplicacao>
  </main>
</template>
