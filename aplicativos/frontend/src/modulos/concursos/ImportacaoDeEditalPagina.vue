<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { ErroDaApi } from '@/compartilhado/api/clienteHttp'
import { listarConcursos, type Concurso } from './apiDeConcursos'
import {
  obterImportacaoDeEdital,
  obterRelatorioDaImportacao,
  iniciarNovaTentativaDaImportacao,
  prepararImportacaoDeEdital,
  receberArquivoDoEdital,
  receberTextoDoEdital,
  registrarDecisoesDaImportacao,
  type DecisoesDaImportacaoDeEdital,
  type EstadoDaImportacaoDeEdital,
  type ImportacaoDeEdital,
  type ModoDaImportacaoDeEdital,
  type PreviaDaImportacaoDeEdital,
  type RelatorioDaImportacaoDeEdital,
} from './apiDeImportacaoDeEdital'
import PreviaDaImportacaoDeEditalComponente from './PreviaDaImportacaoDeEdital.vue'
import RecebimentoDaImportacaoDeEdital from './RecebimentoDaImportacaoDeEdital.vue'
import RevisaoDaExtracaoDoEdital from './RevisaoDaExtracaoDoEdital.vue'

const rota = useRoute()
const roteador = useRouter()
const importacao = ref<ImportacaoDeEdital>()
const previa = ref<PreviaDaImportacaoDeEdital>()
const relatorio = ref<RelatorioDaImportacaoDeEdital>()
const concursos = ref<Concurso[]>([])
const modo = ref<ModoDaImportacaoDeEdital>('CRIAR_NOVO')
const identificadorDoConcursoExistente = ref<string>()
const ultimasDecisoes = ref<DecisoesDaImportacaoDeEdital>()
const carregando = ref(false)
const carregandoConcursos = ref(false)
const enviando = ref(false)
const salvandoDecisoes = ref(false)
const preparando = ref(false)
const retomando = ref(false)
const carregandoRelatorio = ref(false)
const erro = ref('')
const alertaDeErro = ref<HTMLElement>()
let temporizador: number | undefined
let controleDaConsulta: AbortController | undefined
let desmontada = false

const identificadorDaRota = computed(() => {
  const valor = rota.params.identificador
  return typeof valor === 'string' && valor ? valor : undefined
})

const estadosComConsultaAutomatica = new Set<EstadoDaImportacaoDeEdital>([
  'RECEBIDA',
  'EXTRAINDO',
  'AGUARDANDO_CONFIRMACAO',
  'APLICANDO',
])

const estadosParaRevisar = new Set<EstadoDaImportacaoDeEdital>([
  'EXTRAIDA',
  'AGUARDANDO_SELECAO',
  'AGUARDANDO_CORRECOES',
])

const etapaAtual = computed(() => {
  switch (importacao.value?.estado) {
    case undefined:
    case 'RECEBIDA':
      return 1
    case 'EXTRAINDO':
      return 2
    case 'EXTRAIDA':
    case 'AGUARDANDO_SELECAO':
    case 'AGUARDANDO_CORRECOES':
      return 3
    case 'VALIDADA':
      return importacao.value?.chaveDoCargoSelecionado ? 4 : 3
    case 'AGUARDANDO_CONFIRMACAO':
    case 'APLICANDO':
      return 5
    case 'APLICADA':
      return 6
    default:
      return 2
  }
})

const possuiProblemaBloqueante = computed(() =>
  (importacao.value?.problemas ?? []).some(
    (problema) => problema.severidade === 'BLOQUEANTE',
  ),
)

function registrarErro(causa: unknown, mensagem: string) {
  erro.value = causa instanceof Error ? causa.message : mensagem
  void nextTick(() => alertaDeErro.value?.focus())
}

function limparErro() {
  erro.value = ''
}

async function pesquisarConcursos(pesquisa = '') {
  carregandoConcursos.value = true
  try {
    const resposta = await listarConcursos(pesquisa, false, 0)
    concursos.value = resposta.itens
  } catch (causa) {
    registrarErro(causa, 'Não foi possível buscar concursos existentes.')
  } finally {
    carregandoConcursos.value = false
  }
}

function cancelarConsultaAutomatica() {
  if (temporizador !== undefined) window.clearTimeout(temporizador)
  temporizador = undefined
}

function agendarConsulta() {
  cancelarConsultaAutomatica()
  if (
    !desmontada &&
    importacao.value &&
    estadosComConsultaAutomatica.has(importacao.value.estado)
  )
    temporizador = window.setTimeout(() => void carregarImportacao(), 2_500)
}

function atualizarContextoDaImportacao(valor: ImportacaoDeEdital) {
  const extracaoMudou =
    importacao.value !== undefined &&
    (importacao.value.versaoAtualDaExtracao !== valor.versaoAtualDaExtracao ||
      importacao.value.hashDaExtracaoAtual !== valor.hashDaExtracaoAtual)
  if (extracaoMudou) {
    previa.value = undefined
    ultimasDecisoes.value = undefined
  }
  importacao.value = valor
  if (valor.previa) previa.value = valor.previa
  modo.value = valor.modo ?? modo.value
  identificadorDoConcursoExistente.value =
    valor.identificadorDoConcursoExistente ??
    identificadorDoConcursoExistente.value
}

async function carregarRelatorio() {
  if (!importacao.value || relatorio.value) return
  carregandoRelatorio.value = true
  try {
    relatorio.value = await obterRelatorioDaImportacao(
      importacao.value.identificador,
      controleDaConsulta?.signal,
    )
  } catch (causa) {
    if (causa instanceof DOMException && causa.name === 'AbortError') return
    registrarErro(causa, 'Não foi possível carregar o recibo da importação.')
  } finally {
    carregandoRelatorio.value = false
  }
}

async function carregarImportacao() {
  const identificador =
    importacao.value?.identificador ?? identificadorDaRota.value
  if (!identificador || desmontada) return
  cancelarConsultaAutomatica()
  controleDaConsulta?.abort()
  controleDaConsulta = new AbortController()
  carregando.value = !importacao.value
  limparErro()
  try {
    const valor = await obterImportacaoDeEdital(
      identificador,
      controleDaConsulta.signal,
    )
    atualizarContextoDaImportacao(valor)
    if (valor.estado === 'APLICADA') await carregarRelatorio()
  } catch (causa) {
    if (causa instanceof DOMException && causa.name === 'AbortError') return
    registrarErro(causa, 'Não foi possível consultar a importação.')
  } finally {
    carregando.value = false
    agendarConsulta()
  }
}

async function receber(dados: {
  origem: 'ARQUIVO' | 'TEXTO'
  arquivo?: File
  texto?: string
  nomeDaFonte?: string
  modo: ModoDaImportacaoDeEdital
  identificadorDoConcursoExistente?: string
}) {
  enviando.value = true
  limparErro()
  modo.value = dados.modo
  identificadorDoConcursoExistente.value =
    dados.identificadorDoConcursoExistente
  try {
    const destino = {
      modo: dados.modo,
      identificadorDoConcursoExistente: dados.identificadorDoConcursoExistente,
    }
    const valor =
      dados.origem === 'ARQUIVO' && dados.arquivo
        ? await receberArquivoDoEdital(dados.arquivo, destino)
        : await receberTextoDoEdital(
            dados.texto ?? '',
            dados.nomeDaFonte ?? 'texto-colado.txt',
            destino,
          )
    atualizarContextoDaImportacao(valor)
    await roteador.replace({
      name: 'importacao-de-edital',
      params: { identificador: valor.identificador },
    })
    agendarConsulta()
  } catch (causa) {
    registrarErro(causa, 'Não foi possível receber o edital.')
  } finally {
    enviando.value = false
  }
}

async function salvarDecisoes(decisoes: DecisoesDaImportacaoDeEdital) {
  if (!importacao.value) return
  salvandoDecisoes.value = true
  limparErro()
  try {
    const atualizada = await registrarDecisoesDaImportacao(
      importacao.value.identificador,
      decisoes,
    )
    ultimasDecisoes.value = decisoes
    atualizarContextoDaImportacao(atualizada)
    if (atualizada.identificador !== identificadorDaRota.value) {
      await roteador.replace({
        name: 'importacao-de-edital',
        params: { identificador: atualizada.identificador },
      })
    }
  } catch (causa) {
    registrarErro(causa, 'Não foi possível validar as decisões.')
  } finally {
    salvandoDecisoes.value = false
  }
}

function decisoesParaPreparar(): DecisoesDaImportacaoDeEdital | undefined {
  if (!importacao.value?.chaveDoCargoSelecionado) return undefined
  return (
    ultimasDecisoes.value ?? {
      chaveDoCargoSelecionado: importacao.value.chaveDoCargoSelecionado,
      modo: importacao.value.modo ?? modo.value,
      identificadorDoConcursoExistente:
        importacao.value.identificadorDoConcursoExistente ??
        identificadorDoConcursoExistente.value,
      politicaDeReutilizacao:
        importacao.value.politicaDeReutilizacao ?? 'EXIGIR_DECISAO',
      versaoDaExtracao: importacao.value.versaoAtualDaExtracao,
      decisoesHumanas: {},
    }
  )
}

async function preparar() {
  if (!importacao.value || possuiProblemaBloqueante.value) return
  const decisoes = decisoesParaPreparar()
  if (!decisoes) {
    erro.value = 'Selecione e valide o cargo antes de preparar a importação.'
    return
  }
  preparando.value = true
  limparErro()
  try {
    const resposta = await prepararImportacaoDeEdital(
      importacao.value.identificador,
      decisoes,
    )
    atualizarContextoDaImportacao(resposta.importacao)
    previa.value = resposta.previa
    agendarConsulta()
  } catch (causa) {
    if (
      causa instanceof ErroDaApi &&
      causa.status === 409 &&
      [
        'EXTRACAO_DA_IMPORTACAO_DESATUALIZADA',
        'PREVIA_DA_IMPORTACAO_DESATUALIZADA',
      ].includes(causa.codigo ?? '')
    ) {
      previa.value = undefined
      ultimasDecisoes.value = undefined
      await carregarImportacao()
      erro.value =
        'A extração mudou. Revise novamente o cargo e as decisões antes de preparar.'
      await nextTick()
      alertaDeErro.value?.focus()
    } else registrarErro(causa, 'Não foi possível preparar a importação.')
  } finally {
    preparando.value = false
  }
}

async function iniciarNovaTentativa() {
  if (!importacao.value) return
  retomando.value = true
  limparErro()
  try {
    const atualizada = await iniciarNovaTentativaDaImportacao(
      importacao.value.identificador,
    )
    previa.value = undefined
    atualizarContextoDaImportacao(atualizada)
  } catch (causa) {
    registrarErro(
      causa,
      'A operação ainda está vigente ou não pode ser retomada.',
    )
  } finally {
    retomando.value = false
  }
}

function formatarRotulo(valor: string) {
  const texto = valor.replace(/_/g, ' ').toLocaleLowerCase('pt-BR')
  return texto.charAt(0).toLocaleUpperCase('pt-BR') + texto.slice(1)
}

onMounted(async () => {
  if (identificadorDaRota.value) await carregarImportacao()
  else await pesquisarConcursos()
})

onBeforeUnmount(() => {
  desmontada = true
  cancelarConsultaAutomatica()
  controleDaConsulta?.abort()
})
</script>

<template>
  <main class="pagina-da-jornada pagina-da-importacao-de-edital">
    <button
      class="btn btn-link px-0 mb-3 text-decoration-none"
      type="button"
      @click="roteador.push('/concursos')"
    >
      <i class="bi bi-arrow-left me-1" aria-hidden="true"></i>
      Voltar para concursos
    </button>

    <header class="cabecalho-da-pagina">
      <div>
        <p class="sobretitulo-da-pagina">Importação rastreável</p>
        <h1>Importar edital</h1>
        <p>
          Extraia, escolha o cargo e revise toda a proposta antes de alterar o
          concurso.
        </p>
      </div>
    </header>

    <ol class="passos-do-assistente" aria-label="Etapas da importação">
      <li
        v-for="(nome, indice) in [
          'Receber',
          'Extrair',
          'Cargo',
          'Prévia',
          'Confirmar',
          'Concluir',
        ]"
        :key="nome"
        :class="{
          concluido: indice + 1 < etapaAtual,
          atual: indice + 1 === etapaAtual,
        }"
        :aria-current="indice + 1 === etapaAtual ? 'step' : undefined"
      >
        <i>
          <span
            v-if="indice + 1 < etapaAtual"
            class="bi bi-check2"
            aria-hidden="true"
          ></span>
          <span v-else>{{ indice + 1 }}</span>
        </i>
        <span
          ><b>{{ nome }}</b></span
        >
      </li>
    </ol>

    <p
      v-if="erro"
      ref="alertaDeErro"
      class="alert alert-danger"
      role="alert"
      tabindex="-1"
    >
      {{ erro }}
    </p>

    <div v-if="carregando" class="estado-da-importacao" role="status">
      <span class="spinner-border" aria-hidden="true"></span>
      Consultando importação…
    </div>

    <RecebimentoDaImportacaoDeEdital
      v-else-if="!importacao"
      :concursos="concursos"
      :enviando="enviando"
      :carregando-concursos="carregandoConcursos"
      @enviar="receber"
      @pesquisar-concursos="pesquisarConcursos"
    />

    <section
      v-else-if="['RECEBIDA', 'EXTRAINDO'].includes(importacao.estado)"
      class="card estado-da-importacao"
      role="status"
      aria-live="polite"
    >
      <span class="spinner-border" aria-hidden="true"></span>
      <h2>Extraindo dados do edital</h2>
      <p>
        Fonte: {{ importacao.nomeDoArquivo }}. Arquivo permanece isolado e nada
        será cadastrado nesta etapa.
      </p>
      <button
        class="btn btn-outline-primary"
        type="button"
        @click="carregarImportacao"
      >
        Atualizar agora
      </button>
    </section>

    <RevisaoDaExtracaoDoEdital
      v-else-if="
        estadosParaRevisar.has(importacao.estado) ||
        (importacao.estado === 'VALIDADA' &&
          !importacao.chaveDoCargoSelecionado)
      "
      :importacao="importacao"
      :modo="importacao.modo ?? modo"
      :identificador-do-concurso-existente="
        importacao.identificadorDoConcursoExistente ??
        identificadorDoConcursoExistente
      "
      :salvando="salvandoDecisoes"
      @salvar="salvarDecisoes"
    />

    <template v-else-if="importacao.estado === 'VALIDADA'">
      <PreviaDaImportacaoDeEditalComponente v-if="previa" :previa="previa" />
      <section
        class="card preparacao-da-importacao"
        aria-labelledby="titulo-preparacao-importacao"
      >
        <p class="sobretitulo-da-pagina">Dados validados</p>
        <h2 id="titulo-preparacao-importacao">
          {{ previa ? 'Prévia segura gerada' : 'Gerar prévia segura' }}
        </h2>
        <p>
          Servidor verificará duplicidades, reutilizações, versão da extração e
          campos ausentes. Nenhum cadastro será alterado pela página.
        </p>
        <p v-if="previa" class="alert alert-info" role="status">
          Para criar a operação reforçada, peça ao assistente no Telegram para
          importar o staging <code>{{ importacao.identificador }}</code
          >. O arquivo bruto não é enviado ao modelo.
        </p>
        <button
          class="btn btn-primary"
          type="button"
          :disabled="preparando || possuiProblemaBloqueante"
          @click="preparar"
        >
          {{ preparando ? 'Gerando…' : 'Gerar prévia da importação' }}
        </button>
      </section>
    </template>

    <template
      v-else-if="
        ['AGUARDANDO_CONFIRMACAO', 'APLICANDO'].includes(importacao.estado)
      "
    >
      <PreviaDaImportacaoDeEditalComponente v-if="previa" :previa="previa" />
      <section v-else class="card estado-da-importacao" role="status">
        <h2>Operação aguardando confirmação</h2>
        <p>Consulte o assistente no Telegram para revisar e confirmar.</p>
      </section>
      <section
        v-if="importacao.estado === 'APLICANDO'"
        class="alert alert-info"
        role="status"
      >
        Aplicação transacional em andamento. Aguarde o recibo.
      </section>
      <button
        class="btn btn-outline-primary"
        type="button"
        @click="carregarImportacao"
      >
        Atualizar status
      </button>
      <button
        v-if="importacao.estado === 'AGUARDANDO_CONFIRMACAO'"
        class="btn btn-outline-secondary"
        type="button"
        :disabled="retomando"
        @click="iniciarNovaTentativa"
      >
        {{
          retomando ? 'Retomando…' : 'Retomar após expiração ou cancelamento'
        }}
      </button>
    </template>

    <section
      v-else-if="importacao.estado === 'APLICADA'"
      class="card recibo-da-importacao"
      aria-labelledby="titulo-recibo-importacao"
    >
      <p class="sobretitulo-da-pagina">Importação concluída</p>
      <h2 id="titulo-recibo-importacao">Estrutura criada com atomicidade</h2>
      <p v-if="carregandoRelatorio" role="status">Carregando recibo…</p>
      <template v-else-if="relatorio">
        <p>
          Concurso permanece
          <strong>{{ formatarRotulo(relatorio.situacaoDoConcurso) }}</strong
          >.
        </p>
        <dl class="contagens-do-recibo">
          <div v-for="(quantidade, nome) in relatorio.contagens" :key="nome">
            <dt>{{ formatarRotulo(String(nome)) }}</dt>
            <dd>{{ quantidade }}</dd>
          </div>
        </dl>
        <section v-if="relatorio.pendencias.length">
          <h3>Pendências</h3>
          <ul>
            <li v-for="pendencia in relatorio.pendencias" :key="pendencia">
              {{ pendencia }}
            </li>
          </ul>
        </section>
        <section v-if="relatorio.incertezas.length">
          <h3>Incertezas preservadas</h3>
          <ul>
            <li v-for="incerteza in relatorio.incertezas" :key="incerteza">
              {{ incerteza }}
            </li>
          </ul>
        </section>
        <RouterLink
          class="btn btn-primary align-self-start"
          :to="`/concursos/${relatorio.identificadorDoConcurso}?foco=mapeamentos`"
        >
          Revisar estrutura e mapeamentos
        </RouterLink>
      </template>
    </section>

    <section
      v-else-if="['FALHOU', 'CANCELADA'].includes(importacao.estado)"
      class="card estado-da-importacao"
      role="alert"
    >
      <h2>
        {{
          importacao.estado === 'FALHOU'
            ? 'Importação falhou'
            : 'Importação cancelada'
        }}
      </h2>
      <p>Nenhuma estrutura parcial foi aplicada.</p>
      <RouterLink class="btn btn-primary" to="/concursos/importar">
        Iniciar nova importação
      </RouterLink>
    </section>
  </main>
</template>

<style scoped lang="scss">
.pagina-da-importacao-de-edital {
  max-width: 74rem;
}

.passos-do-assistente {
  grid-template-columns: repeat(6, minmax(0, 1fr));
}

.estado-da-importacao,
.preparacao-da-importacao,
.recibo-da-importacao {
  padding: clamp(1.5rem, 4vw, 3rem);
  display: grid;
  justify-items: start;
  gap: 0.75rem;
}

.contagens-do-recibo {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(8rem, 1fr));
  gap: 0.75rem;
  width: 100%;
}

.contagens-do-recibo div {
  border: 1px solid var(--bs-border-color);
  border-radius: 0.75rem;
  padding: 0.75rem;
}

.contagens-do-recibo dd {
  font-size: 1.4rem;
  font-weight: 700;
  margin: 0;
}

@media (max-width: 767px) {
  .passos-do-assistente {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}
</style>
