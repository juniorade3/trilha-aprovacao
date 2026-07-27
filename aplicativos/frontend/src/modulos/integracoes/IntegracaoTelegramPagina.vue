<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'

import { ErroDaApi } from '@/compartilhado/api/clienteHttp'
import CabecalhoDaPagina from '@/compartilhado/componentes/CabecalhoDaPagina.vue'
import EstadoDaPagina from '@/compartilhado/componentes/EstadoDaPagina.vue'
import GavetaLateral from '@/compartilhado/componentes/GavetaLateral.vue'
import {
  cancelarOperacaoAssistida,
  confirmarOperacaoAssistidaPelaWeb,
  criarCodigoDeVinculo,
  listarOperacoesAssistidas,
  obterOperacaoAssistida,
  obterVinculoDoTelegram,
  revogarVinculoDoTelegram,
  rotacionarVinculoDoTelegram,
  type CodigoDeVinculo,
  type DetalheDaOperacaoAssistida,
  type EstadoDaOperacaoAssistida,
  type EstadoDoVinculo,
  type ResumoDaOperacaoAssistida,
  type VinculoDoTelegram,
} from './apiDeIntegracoes'

const vinculo = ref<VinculoDoTelegram>()
const codigoDeVinculo = ref<CodigoDeVinculo>()
const operacoes = ref<ResumoDaOperacaoAssistida[]>([])
const pagina = ref(0)
const totalDePaginas = ref(0)
const totalDeItens = ref(0)
const detalheSelecionado = ref<ResumoDaOperacaoAssistida>()
const detalhe = ref<DetalheDaOperacaoAssistida>()
const carregando = ref(true)
const carregandoHistorico = ref(false)
const carregandoDetalhe = ref(false)
const criandoCodigo = ref(false)
const revogando = ref(false)
const rotacionando = ref(false)
const sessaoExpirada = ref(false)
const erro = ref('')
const erroDoHistorico = ref('')
const erroDoDetalhe = ref('')
const erroDaRotacao = ref('')
const erroDaAcao = ref('')
const aviso = ref('')
const avisoDaCopia = ref('')
const botaoDeAtualizar = ref<HTMLButtonElement>()
const botaoDeCriarCodigo = ref<HTMLButtonElement>()
const botaoDeRevogar = ref<HTMLButtonElement>()
const botaoDeRotacionar = ref<HTMLButtonElement>()
const botaoDeVerificar = ref<HTMLButtonElement>()
const codigoExibido = ref<HTMLElement>()
const alertaDaAcao = ref<HTMLElement>()
const acaoDaOperacao = ref<'confirmacao' | 'cancelamento'>()
let cancelamento: AbortController | undefined
let cancelamentoDoHistorico: AbortController | undefined
let cancelamentoDoDetalhe: AbortController | undefined

const vinculoAtivo = computed(() => vinculo.value?.estado === 'ATIVO')
const comandoDeVinculo = computed(() =>
  codigoDeVinculo.value ? `/conectar ${codigoDeVinculo.value.codigo}` : '',
)

const rotulosDosEstadosDoVinculo: Record<EstadoDoVinculo, string> = {
  PENDENTE: 'Aguardando conexão',
  ATIVO: 'Conectado',
  REVOGADO: 'Revogado',
  EXPIRADO: 'Expirado',
}

const rotulosDosEstadosDaOperacao: Record<EstadoDaOperacaoAssistida, string> = {
  PREPARADA: 'Preparada',
  AGUARDANDO_CONFIRMACAO: 'Aguardando confirmação',
  CONFIRMADA: 'Confirmada',
  APLICADA: 'Aplicada',
  CANCELADA: 'Cancelada',
  EXPIRADA: 'Expirada',
  FALHOU: 'Falhou',
}

const tiposDeConfirmacaoReforcada = new Set([
  'ATIVACAO_DO_CONCURSO',
  'ARQUIVAMENTO_DO_CONCURSO',
  'CANCELAMENTO_DO_CONCURSO',
])

function mensagemDeErro(causa: unknown, padrao: string) {
  return causa instanceof Error ? causa.message : padrao
}

function tratarSessaoExpirada(causa: unknown) {
  if (!(causa instanceof ErroDaApi) || causa.status !== 401) return false
  sessaoExpirada.value = true
  detalheSelecionado.value = undefined
  detalhe.value = undefined
  return true
}

function requisicaoFoiCancelada(causa: unknown) {
  return causa instanceof DOMException && causa.name === 'AbortError'
}

function formatarDataHora(valor?: string | null) {
  if (!valor) return '—'
  const data = new Date(valor)
  if (Number.isNaN(data.getTime())) return '—'
  return new Intl.DateTimeFormat('pt-BR', {
    dateStyle: 'medium',
    timeStyle: 'short',
    timeZone: 'America/Sao_Paulo',
  }).format(data)
}

function formatarTipo(valor: string) {
  return valor
    .toLocaleLowerCase('pt-BR')
    .replace(/_/g, ' ')
    .replace(/^./, (letra) => letra.toLocaleUpperCase('pt-BR'))
}

function formatarEstrutura(valor: unknown) {
  if (valor == null) return 'Nenhum dado registrado.'
  return JSON.stringify(valor, null, 2)
}

function classeDoEstado(estado: EstadoDaOperacaoAssistida) {
  if (estado === 'APLICADA') return 'text-bg-success'
  if (estado === 'FALHOU' || estado === 'CANCELADA') return 'text-bg-danger'
  if (estado === 'EXPIRADA') return 'text-bg-secondary'
  if (estado === 'AGUARDANDO_CONFIRMACAO') return 'text-bg-warning'
  return 'text-bg-light'
}

function podeSerConfirmadaPelaWeb(operacao: DetalheDaOperacaoAssistida) {
  return (
    operacao.estado === 'AGUARDANDO_CONFIRMACAO' &&
    !tiposDeConfirmacaoReforcada.has(operacao.tipo) &&
    new Date(operacao.expiraEm).getTime() > Date.now()
  )
}

function podeSerCanceladaPelaWeb(operacao: DetalheDaOperacaoAssistida) {
  return operacao.estado === 'AGUARDANDO_CONFIRMACAO'
}

async function carregarTudo() {
  cancelamento?.abort()
  cancelamentoDoHistorico?.abort()
  const requisicao = new AbortController()
  cancelamento = requisicao
  carregando.value = true
  sessaoExpirada.value = false
  erro.value = ''
  erroDoHistorico.value = ''
  erroDaRotacao.value = ''

  try {
    const [resultadoDoVinculo, resultadoDoHistorico] = await Promise.allSettled(
      [
        obterVinculoDoTelegram(requisicao.signal),
        listarOperacoesAssistidas(0, 20, requisicao.signal),
      ],
    )

    const falhas = [resultadoDoVinculo, resultadoDoHistorico].filter(
      (resultado) => resultado.status === 'rejected',
    )
    if (falhas.some((resultado) => tratarSessaoExpirada(resultado.reason)))
      return

    if (resultadoDoVinculo.status === 'fulfilled') {
      vinculo.value = resultadoDoVinculo.value
    } else if (!requisicaoFoiCancelada(resultadoDoVinculo.reason)) {
      erro.value = mensagemDeErro(
        resultadoDoVinculo.reason,
        'Não foi possível carregar a integração com o Telegram.',
      )
    }

    if (resultadoDoHistorico.status === 'fulfilled') {
      const historico = resultadoDoHistorico.value
      operacoes.value = historico.itens
      pagina.value = historico.pagina
      totalDePaginas.value = historico.totalDePaginas
      totalDeItens.value = historico.totalDeItens
    } else if (!requisicaoFoiCancelada(resultadoDoHistorico.reason)) {
      erroDoHistorico.value = mensagemDeErro(
        resultadoDoHistorico.reason,
        'Não foi possível carregar o histórico.',
      )
    }
  } finally {
    if (cancelamento === requisicao) carregando.value = false
  }
}

async function repetirAposErro() {
  await carregarTudo()
  await nextTick()
  botaoDeAtualizar.value?.focus()
}

async function carregarPagina(novaPagina: number) {
  cancelamentoDoHistorico?.abort()
  const requisicao = new AbortController()
  cancelamentoDoHistorico = requisicao
  carregandoHistorico.value = true
  erroDoHistorico.value = ''
  try {
    const historico = await listarOperacoesAssistidas(
      novaPagina,
      20,
      requisicao.signal,
    )
    operacoes.value = historico.itens
    pagina.value = historico.pagina
    totalDePaginas.value = historico.totalDePaginas
    totalDeItens.value = historico.totalDeItens
  } catch (causa) {
    if (causa instanceof DOMException && causa.name === 'AbortError') return
    if (!tratarSessaoExpirada(causa))
      erroDoHistorico.value = mensagemDeErro(
        causa,
        'Não foi possível carregar o histórico.',
      )
  } finally {
    if (cancelamentoDoHistorico === requisicao)
      carregandoHistorico.value = false
  }
}

async function criarCodigo() {
  criandoCodigo.value = true
  erro.value = ''
  aviso.value = ''
  avisoDaCopia.value = ''
  try {
    codigoDeVinculo.value = await criarCodigoDeVinculo()
    vinculo.value = codigoDeVinculo.value.vinculo
    await nextTick()
    codigoExibido.value?.focus()
  } catch (causa) {
    if (!tratarSessaoExpirada(causa))
      erro.value = mensagemDeErro(
        causa,
        'Não foi possível criar o código de conexão.',
      )
  } finally {
    criandoCodigo.value = false
  }
}

async function copiarComando() {
  avisoDaCopia.value = ''
  try {
    if (!navigator.clipboard)
      throw new Error('A cópia automática não está disponível.')
    await navigator.clipboard.writeText(comandoDeVinculo.value)
    avisoDaCopia.value = 'Comando copiado.'
  } catch {
    avisoDaCopia.value = 'Selecione e copie o comando manualmente.'
  }
}

async function atualizarSituacaoDoVinculo() {
  erro.value = ''
  try {
    vinculo.value = await obterVinculoDoTelegram()
    if (vinculoAtivo.value) {
      codigoDeVinculo.value = undefined
      aviso.value = 'Telegram conectado com sucesso.'
    } else {
      aviso.value = 'O código ainda não foi confirmado no Telegram.'
    }
    await nextTick()
    if (vinculoAtivo.value) botaoDeRevogar.value?.focus()
    else botaoDeVerificar.value?.focus()
  } catch (causa) {
    if (!tratarSessaoExpirada(causa))
      erro.value = mensagemDeErro(
        causa,
        'Não foi possível atualizar a situação do vínculo.',
      )
  }
}

async function revogarVinculo() {
  if (
    !window.confirm(
      'Revogar o acesso deste Telegram? O agente perderá o acesso imediatamente.',
    )
  )
    return
  revogando.value = true
  erro.value = ''
  erroDaRotacao.value = ''
  aviso.value = ''
  try {
    await revogarVinculoDoTelegram()
    vinculo.value = undefined
    codigoDeVinculo.value = undefined
    aviso.value = 'A integração com o Telegram foi revogada.'
    await nextTick()
    botaoDeCriarCodigo.value?.focus()
  } catch (causa) {
    if (!tratarSessaoExpirada(causa))
      erro.value = mensagemDeErro(
        causa,
        'Não foi possível revogar a integração.',
      )
  } finally {
    revogando.value = false
  }
}

async function rotacionarVinculo() {
  if (
    !window.confirm(
      'Reconectar este Telegram? O acesso atual será revogado imediatamente e você precisará enviar um novo comando /conectar ao bot.',
    )
  )
    return
  rotacionando.value = true
  erroDaRotacao.value = ''
  aviso.value = ''
  avisoDaCopia.value = ''
  try {
    codigoDeVinculo.value = await rotacionarVinculoDoTelegram()
    vinculo.value = codigoDeVinculo.value.vinculo
    aviso.value =
      'O acesso anterior foi revogado. Envie o novo comando ao bot para concluir a reconexão.'
    await nextTick()
    codigoExibido.value?.focus()
  } catch (causa) {
    if (!tratarSessaoExpirada(causa)) {
      erroDaRotacao.value = mensagemDeErro(
        causa,
        'Não foi possível iniciar a reconexão com o Telegram.',
      )
      rotacionando.value = false
      await nextTick()
      botaoDeRotacionar.value?.focus()
    }
  } finally {
    rotacionando.value = false
  }
}

async function abrirDetalhe(operacao: ResumoDaOperacaoAssistida) {
  cancelamentoDoDetalhe?.abort()
  const requisicao = new AbortController()
  cancelamentoDoDetalhe = requisicao
  detalheSelecionado.value = operacao
  detalhe.value = undefined
  erroDoDetalhe.value = ''
  erroDaAcao.value = ''
  carregandoDetalhe.value = true
  try {
    detalhe.value = await obterOperacaoAssistida(
      operacao.identificador,
      requisicao.signal,
    )
  } catch (causa) {
    if (causa instanceof DOMException && causa.name === 'AbortError') return
    if (!tratarSessaoExpirada(causa))
      erroDoDetalhe.value = mensagemDeErro(
        causa,
        'Não foi possível carregar os detalhes da operação.',
      )
  } finally {
    if (cancelamentoDoDetalhe === requisicao) carregandoDetalhe.value = false
  }
}

function fecharDetalhe() {
  cancelamentoDoDetalhe?.abort()
  detalheSelecionado.value = undefined
  detalhe.value = undefined
  erroDoDetalhe.value = ''
  erroDaAcao.value = ''
  acaoDaOperacao.value = undefined
}

function atualizarOperacaoNoHistorico(atualizada: DetalheDaOperacaoAssistida) {
  const resumo: ResumoDaOperacaoAssistida = {
    identificador: atualizada.identificador,
    tipo: atualizada.tipo,
    estado: atualizada.estado,
    resumo: atualizada.resumo,
    expiraEm: atualizada.expiraEm,
    criadoEm: atualizada.criadoEm,
    atualizadoEm: atualizada.atualizadoEm,
  }
  operacoes.value = operacoes.value.map((operacao) =>
    operacao.identificador === atualizada.identificador ? resumo : operacao,
  )
  detalheSelecionado.value = resumo
  detalhe.value = atualizada
}

async function atualizarDepoisDeConflito() {
  const selecionada = detalheSelecionado.value
  await carregarPagina(pagina.value)
  if (selecionada) await abrirDetalhe(selecionada)
}

async function confirmarOperacaoPelaWeb() {
  if (!detalhe.value || !podeSerConfirmadaPelaWeb(detalhe.value)) return
  if (
    !window.confirm(
      'Aplicar esta operação? A Trilha verificará a prévia novamente antes de alterar seus dados.',
    )
  )
    return
  acaoDaOperacao.value = 'confirmacao'
  erroDaAcao.value = ''
  aviso.value = ''
  try {
    const atualizada = await confirmarOperacaoAssistidaPelaWeb(
      detalhe.value.identificador,
    )
    atualizarOperacaoNoHistorico(atualizada)
    aviso.value = 'Operação confirmada e aplicada.'
  } catch (causa) {
    if (!tratarSessaoExpirada(causa)) {
      const mensagem = mensagemDeErro(
        causa,
        'Não foi possível confirmar a operação.',
      )
      erroDaAcao.value = mensagem
      if (causa instanceof ErroDaApi && causa.status === 409)
        await atualizarDepoisDeConflito()
      erroDaAcao.value = mensagem
      await nextTick()
      alertaDaAcao.value?.focus()
    }
  } finally {
    acaoDaOperacao.value = undefined
  }
}

async function cancelarOperacaoPelaWeb() {
  if (!detalhe.value || !podeSerCanceladaPelaWeb(detalhe.value)) return
  if (
    !window.confirm(
      'Cancelar esta operação? A prévia será descartada e nada será aplicado.',
    )
  )
    return
  acaoDaOperacao.value = 'cancelamento'
  erroDaAcao.value = ''
  aviso.value = ''
  try {
    const atualizada = await cancelarOperacaoAssistida(
      detalhe.value.identificador,
    )
    atualizarOperacaoNoHistorico(atualizada)
    aviso.value = 'Operação cancelada. Nenhuma alteração foi aplicada.'
  } catch (causa) {
    if (!tratarSessaoExpirada(causa)) {
      const mensagem = mensagemDeErro(
        causa,
        'Não foi possível cancelar a operação.',
      )
      erroDaAcao.value = mensagem
      if (causa instanceof ErroDaApi && causa.status === 409)
        await atualizarDepoisDeConflito()
      erroDaAcao.value = mensagem
      await nextTick()
      alertaDaAcao.value?.focus()
    }
  } finally {
    acaoDaOperacao.value = undefined
  }
}

onMounted(() => void carregarTudo())
onBeforeUnmount(() => {
  cancelamento?.abort()
  cancelamentoDoHistorico?.abort()
  cancelamentoDoDetalhe?.abort()
})
</script>

<template>
  <main class="pagina-da-jornada pagina-da-integracao-telegram">
    <CabecalhoDaPagina
      etiqueta="Integrações"
      titulo="Assistente no Telegram"
      descricao="Vincule sua conta com segurança, acompanhe as operações preparadas pelo assistente e revogue o acesso quando quiser."
    >
      <template #acoes>
        <button
          ref="botaoDeAtualizar"
          class="btn btn-outline-primary"
          type="button"
          :disabled="carregando"
          @click="carregarTudo"
        >
          <i class="bi bi-arrow-clockwise me-2" aria-hidden="true"></i>
          Atualizar
        </button>
      </template>
    </CabecalhoDaPagina>

    <EstadoDaPagina
      v-if="carregando"
      titulo="Carregando integração"
      descricao="Consultando o vínculo e o histórico de operações."
      carregando
    />

    <EstadoDaPagina
      v-else-if="sessaoExpirada"
      titulo="Sua sessão expirou"
      descricao="Entre novamente para administrar a integração com o Telegram."
      icone="bi-person-lock"
      role="alert"
    >
      <RouterLink
        class="btn btn-primary mt-3"
        :to="{
          name: 'login',
          query: {
            redirecionar: '/integracoes/telegram',
            sessao: 'expirada',
          },
        }"
      >
        Entrar novamente
      </RouterLink>
    </EstadoDaPagina>

    <EstadoDaPagina
      v-else-if="erro"
      titulo="Não foi possível carregar a integração"
      :descricao="erro"
      icone="bi-cloud-slash"
      role="alert"
    >
      <button
        class="btn btn-outline-primary mt-3"
        type="button"
        @click="repetirAposErro"
      >
        Tentar novamente
      </button>
    </EstadoDaPagina>

    <template v-else>
      <p v-if="aviso" class="alert alert-success" role="status">
        {{ aviso }}
      </p>

      <div class="grade-da-integracao-telegram">
        <section
          class="card cartao-do-vinculo-telegram"
          aria-labelledby="titulo-vinculo"
        >
          <header>
            <div>
              <p class="sobretitulo-da-pagina">Conexão privada</p>
              <h2 id="titulo-vinculo">Sua conta no Telegram</h2>
            </div>
            <span
              v-if="vinculo"
              class="badge"
              :class="vinculoAtivo ? 'text-bg-success' : 'text-bg-warning'"
            >
              {{ rotulosDosEstadosDoVinculo[vinculo.estado] }}
            </span>
          </header>

          <template v-if="vinculoAtivo && vinculo">
            <p class="text-body-secondary">
              Este Telegram pode consultar seus dados usando uma credencial
              exclusiva. O agente nunca recebe seu identificador de usuário.
            </p>
            <dl class="dados-do-vinculo-telegram">
              <div>
                <dt>Telegram</dt>
                <dd>{{ vinculo.identificadorExterno }}</dd>
              </div>
              <div>
                <dt>Conversa</dt>
                <dd>{{ vinculo.identificadorDoChat }}</dd>
              </div>
              <div>
                <dt>Vinculado em</dt>
                <dd>{{ formatarDataHora(vinculo.vinculadoEm) }}</dd>
              </div>
              <div>
                <dt>Última atualização</dt>
                <dd>{{ formatarDataHora(vinculo.atualizadoEm) }}</dd>
              </div>
            </dl>

            <div class="acoes-do-vinculo-telegram">
              <p
                v-if="erroDaRotacao"
                class="alert alert-danger w-100"
                role="alert"
              >
                {{ erroDaRotacao }}
              </p>
              <button
                ref="botaoDeRotacionar"
                class="btn btn-outline-primary"
                type="button"
                :disabled="rotacionando || revogando"
                @click="rotacionarVinculo"
              >
                <span
                  v-if="rotacionando"
                  class="spinner-border spinner-border-sm me-2"
                  aria-hidden="true"
                ></span>
                Reconectar Telegram
              </button>
              <button
                ref="botaoDeRevogar"
                class="btn btn-outline-danger"
                type="button"
                :disabled="revogando || rotacionando"
                @click="revogarVinculo"
              >
                <span
                  v-if="revogando"
                  class="spinner-border spinner-border-sm me-2"
                  aria-hidden="true"
                ></span>
                Revogar acesso
              </button>
            </div>
          </template>

          <template v-else>
            <p class="text-body-secondary">
              Gere um código temporário e envie o comando em uma conversa direta
              com o bot oficial. O código funciona uma única vez.
            </p>

            <div v-if="codigoDeVinculo" class="codigo-do-vinculo-telegram">
              <span>Envie este comando ao bot</span>
              <output ref="codigoExibido" tabindex="-1">
                {{ comandoDeVinculo }}
              </output>
              <small>
                Expira em {{ formatarDataHora(codigoDeVinculo.expiraEm) }}.
              </small>
              <div>
                <button
                  class="btn btn-primary"
                  type="button"
                  @click="copiarComando"
                >
                  <i class="bi bi-copy me-2" aria-hidden="true"></i>
                  Copiar comando
                </button>
                <button
                  ref="botaoDeVerificar"
                  class="btn btn-outline-primary"
                  type="button"
                  @click="atualizarSituacaoDoVinculo"
                >
                  Já enviei, verificar
                </button>
              </div>
              <p
                v-if="avisoDaCopia"
                class="mb-0 text-body-secondary"
                role="status"
              >
                {{ avisoDaCopia }}
              </p>
            </div>

            <div class="acoes-do-vinculo-telegram">
              <button
                ref="botaoDeCriarCodigo"
                class="btn btn-primary"
                type="button"
                :disabled="criandoCodigo"
                @click="criarCodigo"
              >
                <span
                  v-if="criandoCodigo"
                  class="spinner-border spinner-border-sm me-2"
                  aria-hidden="true"
                ></span>
                {{
                  codigoDeVinculo
                    ? 'Gerar outro código'
                    : 'Gerar código de conexão'
                }}
              </button>
            </div>
          </template>
        </section>

        <aside class="card cartao-de-privacidade-do-telegram">
          <i class="bi bi-shield-lock" aria-hidden="true"></i>
          <div>
            <h2>Controle permanece com você</h2>
            <p>
              Consultas são automáticas. Alterações futuras exigirão uma prévia
              e uma confirmação associada à sua conta e à operação correta.
            </p>
          </div>
          <ul>
            <li>Somente conversas diretas vinculadas</li>
            <li>Credencial individual e revogável</li>
            <li>Nenhum acesso direto ao banco ou ao navegador</li>
          </ul>
        </aside>
      </div>

      <section
        class="historico-de-operacoes-assistidas"
        aria-labelledby="titulo-operacoes"
      >
        <header>
          <div>
            <p class="sobretitulo-da-pagina">Auditoria pessoal</p>
            <h2 id="titulo-operacoes">Histórico de operações</h2>
            <p>
              Consulte o que foi preparado, confirmado, aplicado ou recusado
              pelo assistente. Abra uma prévia pendente para aceitar ou cancelar
              a operação.
            </p>
          </div>
          <span
            >{{ totalDeItens }}
            {{ totalDeItens === 1 ? 'operação' : 'operações' }}</span
          >
        </header>

        <p v-if="erroDoHistorico" class="alert alert-danger" role="alert">
          {{ erroDoHistorico }}
          <button
            class="btn btn-sm btn-outline-danger ms-2"
            type="button"
            @click="carregarPagina(pagina)"
          >
            Tentar novamente
          </button>
        </p>

        <EstadoDaPagina
          v-if="carregandoHistorico"
          titulo="Carregando histórico"
          carregando
        />
        <EstadoDaPagina
          v-else-if="!erroDoHistorico && operacoes.length === 0"
          titulo="Nenhuma operação registrada"
          descricao="As prévias e ações do assistente aparecerão aqui."
          icone="bi-clock-history"
        />
        <ul v-else-if="!erroDoHistorico" class="lista-de-operacoes-assistidas">
          <li v-for="operacao in operacoes" :key="operacao.identificador">
            <button
              type="button"
              :aria-label="`Ver detalhes: ${operacao.resumo}`"
              @click="abrirDetalhe(operacao)"
            >
              <span class="icone-da-operacao-assistida" aria-hidden="true">
                <i class="bi bi-stars"></i>
              </span>
              <span>
                <small>{{ formatarTipo(operacao.tipo) }}</small>
                <strong>{{ operacao.resumo }}</strong>
                <time :datetime="operacao.criadoEm">
                  {{ formatarDataHora(operacao.criadoEm) }}
                </time>
              </span>
              <span class="badge" :class="classeDoEstado(operacao.estado)">
                {{ rotulosDosEstadosDaOperacao[operacao.estado] }}
              </span>
              <i class="bi bi-chevron-right" aria-hidden="true"></i>
            </button>
          </li>
        </ul>

        <nav
          v-if="!erroDoHistorico && totalDePaginas > 1"
          class="paginacao-das-operacoes-assistidas"
          aria-label="Paginação do histórico de operações"
        >
          <button
            class="btn btn-outline-primary"
            type="button"
            :disabled="pagina === 0 || carregandoHistorico"
            @click="carregarPagina(pagina - 1)"
          >
            Anterior
          </button>
          <span>Página {{ pagina + 1 }} de {{ totalDePaginas }}</span>
          <button
            class="btn btn-outline-primary"
            type="button"
            :disabled="pagina + 1 >= totalDePaginas || carregandoHistorico"
            @click="carregarPagina(pagina + 1)"
          >
            Próxima
          </button>
        </nav>
      </section>
    </template>
  </main>

  <GavetaLateral
    v-if="detalheSelecionado"
    etiqueta="Operação assistida"
    :titulo="formatarTipo(detalheSelecionado.tipo)"
    :descricao="detalheSelecionado.resumo"
    @fechar="fecharDetalhe"
  >
    <EstadoDaPagina
      v-if="carregandoDetalhe"
      titulo="Carregando detalhes"
      carregando
    />

    <EstadoDaPagina
      v-else-if="erroDoDetalhe"
      titulo="Não foi possível carregar a operação"
      :descricao="erroDoDetalhe"
      icone="bi-cloud-slash"
      role="alert"
    >
      <button
        class="btn btn-outline-primary mt-3"
        type="button"
        @click="abrirDetalhe(detalheSelecionado)"
      >
        Tentar novamente
      </button>
    </EstadoDaPagina>

    <template v-else-if="detalhe">
      <dl class="detalhes-da-operacao-assistida">
        <div>
          <dt>Estado</dt>
          <dd>
            <span class="badge" :class="classeDoEstado(detalhe.estado)">
              {{ rotulosDosEstadosDaOperacao[detalhe.estado] }}
            </span>
          </dd>
        </div>
        <div>
          <dt>Criada em</dt>
          <dd>{{ formatarDataHora(detalhe.criadoEm) }}</dd>
        </div>
        <div>
          <dt>Expira em</dt>
          <dd>{{ formatarDataHora(detalhe.expiraEm) }}</dd>
        </div>
        <div v-if="detalhe.confirmadaEm">
          <dt>Confirmada em</dt>
          <dd>{{ formatarDataHora(detalhe.confirmadaEm) }}</dd>
        </div>
        <div v-if="detalhe.aplicadaEm">
          <dt>Aplicada em</dt>
          <dd>{{ formatarDataHora(detalhe.aplicadaEm) }}</dd>
        </div>
        <div v-if="detalhe.canceladaEm">
          <dt>Cancelada em</dt>
          <dd>{{ formatarDataHora(detalhe.canceladaEm) }}</dd>
        </div>
      </dl>

      <p v-if="detalhe.falha" class="alert alert-danger" role="alert">
        {{ detalhe.falha }}
      </p>

      <section
        v-if="
          podeSerConfirmadaPelaWeb(detalhe) || podeSerCanceladaPelaWeb(detalhe)
        "
        class="acoes-da-operacao-assistida"
        aria-label="Decisão da operação"
      >
        <h3>Decidir esta operação</h3>
        <p>
          Revise a proposta antes de decidir. A confirmação pela web verifica
          novamente se os dados continuam atuais.
        </p>
        <p
          v-if="
            !podeSerConfirmadaPelaWeb(detalhe) &&
            tiposDeConfirmacaoReforcada.has(detalhe.tipo)
          "
          class="alert alert-warning"
        >
          Esta é uma operação crítica e exige a confirmação reforçada pelo
          Telegram. Você ainda pode cancelá-la por aqui se ela estiver pendente.
        </p>
        <p
          v-if="erroDaAcao"
          ref="alertaDaAcao"
          class="alert alert-danger"
          role="alert"
          tabindex="-1"
        >
          {{ erroDaAcao }}
        </p>
        <div>
          <button
            v-if="podeSerConfirmadaPelaWeb(detalhe)"
            class="btn btn-primary"
            type="button"
            :disabled="acaoDaOperacao != null"
            @click="confirmarOperacaoPelaWeb"
          >
            <span
              v-if="acaoDaOperacao === 'confirmacao'"
              class="spinner-border spinner-border-sm me-2"
              aria-hidden="true"
            ></span>
            Aceitar e aplicar
          </button>
          <button
            v-if="podeSerCanceladaPelaWeb(detalhe)"
            class="btn btn-outline-danger"
            type="button"
            :disabled="acaoDaOperacao != null"
            @click="cancelarOperacaoPelaWeb"
          >
            <span
              v-if="acaoDaOperacao === 'cancelamento'"
              class="spinner-border spinner-border-sm me-2"
              aria-hidden="true"
            ></span>
            Cancelar operação
          </button>
        </div>
      </section>

      <section class="estrutura-da-operacao-assistida">
        <h3>Proposta registrada</h3>
        <pre>{{ formatarEstrutura(detalhe.proposta) }}</pre>
      </section>
      <section class="estrutura-da-operacao-assistida">
        <h3>Versões consultadas</h3>
        <pre>{{ formatarEstrutura(detalhe.versoesConsultadas) }}</pre>
      </section>
      <section
        v-if="detalhe.resultado != null"
        class="estrutura-da-operacao-assistida"
      >
        <h3>Resultado aplicado</h3>
        <pre>{{ formatarEstrutura(detalhe.resultado) }}</pre>
      </section>
      <details class="assinatura-da-operacao-assistida">
        <summary>Ver assinatura da prévia</summary>
        <code>{{ detalhe.assinatura }}</code>
      </details>
    </template>
  </GavetaLateral>
</template>
