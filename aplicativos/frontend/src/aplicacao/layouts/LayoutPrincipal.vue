<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

import { usarSessao } from '@/aplicacao/estado/sessao'
import { assistenteTelegramEstaHabilitado } from '@/aplicacao/configuracao/funcionalidades'
import { requisitar } from '@/compartilhado/api/clienteHttp'
import RegistroRapidoDeEstudo from '@/modulos/estudos/RegistroRapidoDeEstudo.vue'
import type { TipoDeEstudo } from '@/modulos/estudos/apiDeEstudos'

interface DadosIniciaisDoRegistroRapido {
  identificadorDaMateria?: string
  identificadorDoTopico?: string
  tipoDeEstudo?: TipoDeEstudo
}

const sessao = usarSessao()
const roteador = useRouter()
const assistenteTelegramHabilitado = assistenteTelegramEstaHabilitado()
const menuAberto = ref(false)
const registroRapidoAberto = ref(false)
const dadosIniciaisDoRegistroRapido = ref<DadosIniciaisDoRegistroRapido>()
const aviso = ref('')
let temporizadorDoAviso: number | undefined

function fecharMenu() {
  menuAberto.value = false
}

async function sair() {
  try {
    await requisitar<void>('/v1/autenticacao/logout', { method: 'POST' })
  } finally {
    sessao.limpar()
    await roteador.push('/login')
  }
}

function estudoRegistrado() {
  registroRapidoAberto.value = false
  dadosIniciaisDoRegistroRapido.value = undefined
  aviso.value = 'Estudo registrado. Seu progresso foi atualizado.'
  window.dispatchEvent(new CustomEvent('estudo-registrado'))
  window.clearTimeout(temporizadorDoAviso)
  temporizadorDoAviso = window.setTimeout(() => {
    aviso.value = ''
  }, 3500)
}

function abrirRegistroRapido(evento?: Event) {
  dadosIniciaisDoRegistroRapido.value =
    evento instanceof CustomEvent
      ? (evento.detail as DadosIniciaisDoRegistroRapido | undefined)
      : undefined
  registroRapidoAberto.value = true
}

function fecharRegistroRapido() {
  registroRapidoAberto.value = false
  dadosIniciaisDoRegistroRapido.value = undefined
}

onMounted(() => {
  window.addEventListener('abrir-registro-rapido', abrirRegistroRapido)
})

onBeforeUnmount(() => {
  window.clearTimeout(temporizadorDoAviso)
  window.removeEventListener('abrir-registro-rapido', abrirRegistroRapido)
})
</script>

<template>
  <div class="aplicacao-autenticada">
    <header class="topo-da-aplicacao">
      <div class="topo-da-aplicacao-interno">
        <RouterLink
          class="marca-da-aplicacao"
          to="/dashboard"
          @click="fecharMenu"
        >
          <svg class="simbolo-da-marca" viewBox="0 0 48 36" aria-hidden="true">
            <path d="M4 28 14 19l7 5L34 8" />
            <path d="M8 10c5 3 9 3 13-1M27 28c6 0 11-3 17-9" />
          </svg>
          <span>Trilha da Aprovação</span>
        </RouterLink>

        <nav class="navegacao-principal" aria-label="Navegação principal">
          <RouterLink to="/dashboard">Visão geral</RouterLink>
          <RouterLink to="/concursos">Meu concurso</RouterLink>
          <RouterLink to="/materias">Conteúdos</RouterLink>
          <RouterLink to="/materiais">Materiais</RouterLink>
          <RouterLink to="/planejamento/semana">Planejamento</RouterLink>
        </nav>

        <button
          class="acao-global-de-estudo"
          type="button"
          @click="abrirRegistroRapido()"
        >
          <i class="bi bi-pencil-square" aria-hidden="true"></i>
          Registrar estudo
        </button>

        <div class="perfil-da-aplicacao">
          <span class="avatar-do-usuario" aria-hidden="true">
            {{ sessao.usuario?.nome?.charAt(0).toUpperCase() || 'U' }}
          </span>
          <span class="nome-do-usuario">{{ sessao.usuario?.nome }}</span>
          <RouterLink
            v-if="assistenteTelegramHabilitado"
            class="botao-de-icone"
            to="/integracoes/telegram"
            aria-label="Integração com o Telegram"
            title="Integração com o Telegram"
          >
            <i class="bi bi-robot" aria-hidden="true"></i>
          </RouterLink>
          <button
            class="botao-de-icone"
            type="button"
            aria-label="Sair da conta"
            title="Sair"
            @click="sair"
          >
            <i class="bi bi-box-arrow-right" aria-hidden="true"></i>
          </button>
        </div>
      </div>
    </header>

    <RouterView />

    <nav class="navegacao-movel" aria-label="Navegação principal">
      <RouterLink to="/dashboard">
        <i class="bi bi-house" aria-hidden="true"></i>
        <span>Visão</span>
      </RouterLink>
      <RouterLink to="/concursos">
        <i class="bi bi-bullseye" aria-hidden="true"></i>
        <span>Concurso</span>
      </RouterLink>
      <RouterLink to="/materias">
        <i class="bi bi-book" aria-hidden="true"></i>
        <span>Conteúdos</span>
      </RouterLink>
      <RouterLink to="/materiais">
        <i class="bi bi-file-earmark-text" aria-hidden="true"></i>
        <span>Materiais</span>
      </RouterLink>
      <RouterLink to="/planejamento/semana">
        <i class="bi bi-calendar-week" aria-hidden="true"></i>
        <span>Planejar</span>
      </RouterLink>
    </nav>

    <button
      class="acao-flutuante-de-estudo"
      type="button"
      aria-label="Registrar estudo"
      title="Registrar estudo"
      @click="abrirRegistroRapido()"
    >
      <i class="bi bi-plus-lg" aria-hidden="true"></i>
    </button>

    <RegistroRapidoDeEstudo
      v-if="registroRapidoAberto"
      :identificador-da-materia-inicial="
        dadosIniciaisDoRegistroRapido?.identificadorDaMateria
      "
      :identificador-do-topico-inicial="
        dadosIniciaisDoRegistroRapido?.identificadorDoTopico
      "
      :tipo-de-estudo-inicial="dadosIniciaisDoRegistroRapido?.tipoDeEstudo"
      @fechar="fecharRegistroRapido"
      @registrado="estudoRegistrado"
    />

    <Transition name="aviso">
      <div v-if="aviso" class="aviso-da-aplicacao" role="status">
        <span><i class="bi bi-check2" aria-hidden="true"></i></span>
        {{ aviso }}
      </div>
    </Transition>
  </div>
</template>
