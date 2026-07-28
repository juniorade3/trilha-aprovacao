<script setup lang="ts">
import { computed, nextTick, reactive, ref, watch } from 'vue'
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
import {
  listarMateriaisRelacionadosAosTopicos,
  type MaterialRelacionadoAoTopico,
} from '@/modulos/estudos/apiDeEstudos'
import EditorDeBloco from './EditorDeBloco.vue'
import GavetaDeGeracaoDeterministica from './GavetaDeGeracaoDeterministica.vue'
import GavetaDeReplanejamento from './GavetaDeReplanejamento.vue'
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
  obterHistoricoSemanal,
  reordenarBlocos,
  type BlocoDeEstudo,
  type DadosDoBlocoDeEstudo,
  type PlanoSemanal,
  type ResultadoDaAplicacaoDaGeracao,
  type ResultadoDaAplicacaoDoReplanejamento,
  type HistoricoSemanal,
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
const materiaisPorTopico = ref<Record<string, MaterialRelacionadoAoTopico[]>>(
  {},
)
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
const blocoDaJustificativa = ref<BlocoDeEstudo>()
const replanejamentoAberto = ref(false)
const historico = ref<HistoricoSemanal>()
const botaoDoReplanejamento = ref<HTMLButtonElement>()
const botaoDaGeracao = ref<HTMLButtonElement>()

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

const dataDeReferenciaDoReplanejamento = computed(() => {
  const hoje = paraIso(new Date())
  if (hoje < dataInicial.value) return dataInicial.value
  const domingo = adicionarDias(dataInicial.value, 6)
  return hoje > domingo ? domingo : hoje
})

const quantidadeDeBlocosGerados = computed(
  () =>
    plano.value?.blocos.filter(
      (bloco) =>
        bloco.origem === 'GERADO_DETERMINISTICAMENTE' &&
        bloco.data >= dataDeReferenciaDoReplanejamento.value,
    ).length ?? 0,
)

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

async function carregarAtalhosDosMateriais(planoObtido: PlanoSemanal) {
  materiaisPorTopico.value = {}
  const identificadores = [
    ...new Set(
      planoObtido.blocos
        .map((bloco) => bloco.identificadorDoTopico)
        .filter((identificador): identificador is string =>
          Boolean(identificador),
        ),
    ),
  ]
  if (identificadores.length === 0) return
  try {
    const relacionados =
      await listarMateriaisRelacionadosAosTopicos(identificadores)
    materiaisPorTopico.value = relacionados.reduce<
      Record<string, MaterialRelacionadoAoTopico[]>
    >((agrupados, item) => {
      const materiais = agrupados[item.identificadorDoTopico] ?? []
      materiais.push(item)
      agrupados[item.identificadorDoTopico] = materiais
      return agrupados
    }, {})
  } catch {
    materiaisPorTopico.value = {}
  }
}

async function carregar() {
  carregando.value = true
  erro.value = ''
  aviso.value = ''
  conflito.value = false
  plano.value = undefined
  historico.value = undefined
  materiaisPorTopico.value = {}
  try {
    const planoObtido = await obterPlanoSemanal(dataInicial.value)
    plano.value = planoObtido
    preencherFormulario(planoObtido)
    const [, historicoObtido] = await Promise.all([
      Promise.all([
        carregarConteudos(),
        carregarAtalhosDosMateriais(planoObtido),
      ]),
      obterHistoricoSemanal(planoObtido.identificador, dataInicial.value),
    ])
    historico.value = historicoObtido
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
  await focarBlocoSolicitado()
}

async function focarBlocoSolicitado() {
  const identificador = rota.query.foco
  if (
    typeof identificador !== 'string' ||
    !plano.value?.blocos.some((bloco) => bloco.identificador === identificador)
  )
    return
  await nextTick()
  document
    .getElementById(`bloco-planejado-${identificador}`)
    ?.focus({ preventScroll: false })
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

function materiaisDoBloco(bloco: BlocoDeEstudo) {
  return bloco.identificadorDoTopico
    ? (materiaisPorTopico.value[bloco.identificadorDoTopico] ?? [])
    : []
}

function possuiMaterial(bloco: BlocoDeEstudo) {
  return materiaisDoBloco(bloco).length > 0
}

function abrirMaterialDoBloco(bloco: BlocoDeEstudo, evento?: MouseEvent) {
  if (
    evento?.target instanceof Element &&
    evento.target.closest('a, button, input, select, textarea, summary')
  )
    return

  const relacionados = materiaisDoBloco(bloco)
  if (relacionados.length === 0 || !bloco.identificadorDoTopico) return
  if (relacionados.length === 1) {
    void roteador.push({
      name: 'material-detalhe',
      params: { identificador: relacionados[0]!.identificadorDoMaterial },
    })
    return
  }
  void roteador.push({
    name: 'materiais-de-estudo',
    query: { topico: bloco.identificadorDoTopico },
  })
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

function rotuloDaOrigem(bloco: BlocoDeEstudo) {
  return {
    MANUAL: 'Manual',
    GERADO_DETERMINISTICAMENTE: 'Gerado',
    GERADO_AJUSTADO_MANUALMENTE: 'Gerado e ajustado',
    REPLANEJADO: 'Replanejado',
    REPLANEJADO_AJUSTADO_MANUALMENTE: 'Replanejado e ajustado',
  }[bloco.origem]
}

function classeDaOrigem(bloco: BlocoDeEstudo) {
  return bloco.origem === 'MANUAL'
    ? 'etiqueta-neutra'
    : bloco.origem === 'GERADO_DETERMINISTICAMENTE'
      ? 'text-bg-primary'
      : bloco.origem === 'GERADO_AJUSTADO_MANUALMENTE'
        ? 'text-bg-warning'
        : 'text-bg-info'
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
  historico.value = await obterHistoricoSemanal(
    atualizado.identificador,
    dataInicial.value,
  )
}

async function concluirAplicacaoDaGeracao(
  resultado: ResultadoDaAplicacaoDaGeracao,
) {
  plano.value = resultado.plano
  preencherFormulario(resultado.plano)
  geracaoAberta.value = false
  const resumo = resultado.resumo
  aviso.value = `${resumo.quantidadeDeBlocosCriados} bloco(s) aplicado(s). ${resumo.quantidadeDeBlocosSubstituidos} substituído(s) e ${resumo.quantidadeDeBlocosPreservados} preservado(s).`
  await nextTick()
  botaoDaGeracao.value?.focus()
}

async function fecharGeracao() {
  geracaoAberta.value = false
  await nextTick()
  botaoDaGeracao.value?.focus()
}

async function concluirReplanejamento(
  resultado: ResultadoDaAplicacaoDoReplanejamento,
) {
  plano.value = resultado.planoAtualizado
  preencherFormulario(resultado.planoAtualizado)
  replanejamentoAberto.value = false
  historico.value = await obterHistoricoSemanal(
    resultado.planoAtualizado.identificador,
    dataInicial.value,
  )
  aviso.value = `${resultado.quantidadeDePendenciasTransferidas} pendência(s) transferida(s) em ${resultado.quantidadeDeFragmentosCriados} novo(s) bloco(s).`
  await nextTick()
  botaoDoReplanejamento.value?.focus()
}

function abrirReplanejamento() {
  replanejamentoAberto.value = true
}

async function fecharReplanejamento() {
  replanejamentoAberto.value = false
  await nextTick()
  botaoDoReplanejamento.value?.focus()
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
    historico.value = undefined
    preencherFormulario(planoCriado)
    aviso.value =
      'Novo plano semanal criado em rascunho. O plano cancelado foi preservado no histórico.'
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
    if (rota.name !== 'planejamento-semana') return
    geracaoAberta.value = false
    replanejamentoAberto.value = false
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

watch(() => rota.query.foco, focarBlocoSolicitado, { flush: 'post' })
</script>

<template>
  <main class="pagina-comum pagina-de-planejamento">
    <div class="topo-da-area-de-planejamento">
      <CabecalhoDaPagina
        etiqueta="Planejamento manual"
        titulo="Sua semana"
        :descricao="
          plano?.estado === 'ATIVO'
            ? 'Consulte o compromisso de estudo ativo desta semana.'
            : 'Defina quanto tempo você tem disponível em cada dia.'
        "
      />

      <NavegacaoDoPlanejamento />
    </div>

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
              plano.estado === 'ATIVO' ? 'text-bg-success' : 'etiqueta-neutra'
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
            ref="botaoDoReplanejamento"
            class="btn btn-primary"
            type="button"
            @click="abrirReplanejamento"
          >
            <i class="bi bi-calendar2-range me-2" aria-hidden="true"></i>
            Replanejar pendências
          </button>
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
            ref="botaoDaGeracao"
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
        <div v-if="plano.estado === 'CANCELADO'" class="acoes-do-plano-semanal">
          <button
            class="btn btn-primary"
            type="button"
            :disabled="criando"
            @click="criar"
          >
            <span
              v-if="criando"
              class="spinner-border spinner-border-sm me-2"
              aria-hidden="true"
            ></span>
            Criar novo plano desta semana
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
                :id="`bloco-planejado-${bloco.identificador}`"
                :key="bloco.identificador"
                :class="{ 'bloco-com-material': possuiMaterial(bloco) }"
                :tabindex="possuiMaterial(bloco) ? 0 : -1"
                :role="possuiMaterial(bloco) ? 'link' : undefined"
                :aria-label="
                  possuiMaterial(bloco)
                    ? `${bloco.titulo}. Abrir material correspondente`
                    : undefined
                "
                @click="abrirMaterialDoBloco(bloco, $event)"
                @keydown.enter.self.prevent="abrirMaterialDoBloco(bloco)"
                @keydown.space.self.prevent="abrirMaterialDoBloco(bloco)"
              >
                <div class="conteudo-do-bloco-planejado">
                  <span class="ordem-do-bloco">{{ bloco.ordem }}</span>
                  <div>
                    <strong>{{ bloco.titulo }}</strong>
                    <span
                      class="d-flex flex-wrap align-items-center gap-2 my-1"
                    >
                      <span class="badge" :class="classeDaOrigem(bloco)">
                        {{ rotuloDaOrigem(bloco) }}
                      </span>
                      <button
                        v-if="bloco.justificativaDaGeracao"
                        class="btn btn-sm btn-link p-0"
                        type="button"
                        :aria-label="`Ver justificativa de ${bloco.titulo}`"
                        @click="blocoDaJustificativa = bloco"
                      >
                        Por que este bloco?
                      </button>
                    </span>
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
                    <span
                      v-if="possuiMaterial(bloco)"
                      class="atalho-do-material"
                      aria-hidden="true"
                    >
                      <i class="bi bi-book" aria-hidden="true"></i>
                      Abrir material
                    </span>
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

    <section
      v-if="plano && historico"
      class="historico-objetivo-da-semana"
      aria-labelledby="titulo-historico-semanal"
    >
      <div>
        <p class="sobretitulo-da-pagina">Histórico objetivo</p>
        <h2 id="titulo-historico-semanal" class="h4">
          Plano e execução da semana
        </h2>
      </div>
      <dl class="grade-do-historico-semanal">
        <div>
          <dt>Planejado originalmente</dt>
          <dd>{{ historico.resumo.minutosPlanejados }} min</dd>
        </div>
        <div>
          <dt>Executado</dt>
          <dd>{{ historico.resumo.minutosExecutados }} min</dd>
        </div>
        <div>
          <dt>Interrompido</dt>
          <dd>{{ historico.resumo.minutosInterrompidos }} min</dd>
        </div>
        <div>
          <dt>Pendente</dt>
          <dd>{{ historico.resumo.minutosPendentes }} min</dd>
        </div>
        <div>
          <dt>Taxa executada / planejada</dt>
          <dd>{{ historico.resumo.taxaExecutadaSobrePlanejada }}%</dd>
        </div>
        <div>
          <dt>Reagendados</dt>
          <dd>{{ historico.resumo.blocosReagendados }}</dd>
        </div>
      </dl>
      <p
        v-if="historico.transferencias.length"
        class="mb-0 text-body-secondary"
      >
        {{ historico.transferencias.length }} fragmento(s) transferido(s) por
        replanejamento.
      </p>
    </section>

    <GavetaDeGeracaoDeterministica
      v-if="geracaoAberta && plano"
      :key="`${plano.identificador}-${dataDeReferenciaDoReplanejamento}`"
      :identificador-do-plano="plano.identificador"
      :data-de-referencia="dataDeReferenciaDoReplanejamento"
      :quantidade-de-blocos-gerados="quantidadeDeBlocosGerados"
      @fechar="fecharGeracao"
      @aplicado="concluirAplicacaoDaGeracao"
    />

    <GavetaDeReplanejamento
      v-if="replanejamentoAberto && plano?.estado === 'ATIVO'"
      :key="`${plano.identificador}-${dataDeReferenciaDoReplanejamento}`"
      :identificador-do-plano="plano.identificador"
      :data-de-referencia="dataDeReferenciaDoReplanejamento"
      @fechar="fecharReplanejamento"
      @aplicado="concluirReplanejamento"
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
      v-if="blocoDaJustificativa"
      titulo="Por que este bloco foi sugerido?"
      etiqueta="Justificativa da geração"
      :descricao="blocoDaJustificativa.titulo"
      @fechar="blocoDaJustificativa = undefined"
    >
      <p class="mb-0 text-break">
        {{ blocoDaJustificativa.justificativaDaGeracao }}
      </p>
      <template #rodape>
        <button
          class="btn btn-primary"
          type="button"
          @click="blocoDaJustificativa = undefined"
        >
          Entendi
        </button>
      </template>
    </ModalDaAplicacao>

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

<style scoped lang="scss">
.historico-objetivo-da-semana {
  margin-top: 1.5rem;
  padding: clamp(1rem, 3vw, 1.5rem);
  border: 1px solid var(--bs-border-color);
  border-radius: 1rem;
  background: var(--cor-papel);
}

.lista-de-blocos-do-dia > li:focus-visible {
  outline: 3px solid var(--cor-destaque);
  outline-offset: 3px;
}

.lista-de-blocos-do-dia > li.bloco-com-material {
  cursor: pointer;
  transition:
    border-color 0.15s ease,
    box-shadow 0.15s ease,
    transform 0.15s ease;

  &:hover {
    border-color: var(--cor-destaque);
    box-shadow: var(--sombra-suave);
    transform: translateY(-1px);
  }
}

.atalho-do-material {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  margin-top: 0.55rem;
  color: var(--bs-primary);
  font-size: 0.82rem;
  font-weight: 700;
}

.grade-do-historico-semanal {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 0.75rem;
  margin: 1rem 0;

  div {
    padding: 0.75rem;
    border-radius: 0.75rem;
    background: var(--cor-superficie-secundaria);
  }
  dt {
    color: var(--bs-secondary-color);
    font-size: 0.82rem;
  }
  dd {
    margin: 0.2rem 0 0;
    font-weight: 700;
  }
}

@media (max-width: 991.98px) {
  .resumo-do-plano-semanal {
    display: grid;
    gap: 1rem;
    grid-template-columns: repeat(3, minmax(0, 1fr));

    > div {
      min-width: 0;
    }

    > div:not(.acoes-do-plano-semanal) strong {
      overflow-wrap: anywhere;
    }
  }

  .acoes-do-plano-semanal {
    flex-wrap: wrap;
    grid-column: 1 / -1;
    width: 100%;

    .btn {
      flex: 1 1 12rem;
      min-width: 0;
    }
  }
}

@media (max-width: 575.98px) {
  .acoes-do-plano-semanal {
    align-items: stretch;
    flex-direction: column;

    .btn {
      flex: 0 0 auto;
      width: 100%;
    }
  }
}

@media (max-width: 767.98px) {
  .grade-do-historico-semanal {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 399.98px) {
  .grade-do-historico-semanal {
    grid-template-columns: 1fr;
  }
}
</style>
