<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

import { usarSessao } from '@/aplicacao/estado/sessao'
import { assistenteTelegramEstaHabilitado } from '@/aplicacao/configuracao/funcionalidades'
import { requisitar } from '@/compartilhado/api/clienteHttp'
import { usarDialogoAcessivel } from '@/compartilhado/componentes/usarDialogoAcessivel'
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
const botaoDoMenu = ref<HTMLButtonElement>()
const registroRapidoAberto = ref(false)
const dadosIniciaisDoRegistroRapido = ref<DadosIniciaisDoRegistroRapido>()
const aviso = ref('')
let temporizadorDoAviso: number | undefined

const { raizDoDialogo: raizDoMenu } = usarDialogoAcessivel(
  fecharMenu,
  menuAberto,
)

function fecharMenu() {
  menuAberto.value = false
}

function alternarMenu() {
  if (!menuAberto.value) botaoDoMenu.value?.focus()
  menuAberto.value = !menuAberto.value
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
    <a class="atalho-para-conteudo" href="#conteudo-principal">
      Ir para o conteúdo
    </a>

    <aside class="barra-lateral-da-aplicacao" aria-label="Menu da aplicação">
      <RouterLink class="marca-da-aplicacao" to="/dashboard">
        <svg class="simbolo-da-marca" viewBox="0 0 48 36" aria-hidden="true">
          <path d="M4 28 14 19l7 5L34 8" />
          <path d="M8 10c5 3 9 3 13-1M27 28c6 0 11-3 17-9" />
        </svg>
        <span>Trilha da Aprovação</span>
      </RouterLink>

      <nav class="navegacao-principal" aria-label="Navegação principal">
        <p class="rotulo-da-navegacao">Sua trilha</p>
        <RouterLink to="/dashboard">
          <i class="bi bi-grid-1x2" aria-hidden="true"></i>
          <span>Visão geral</span>
        </RouterLink>
        <RouterLink to="/planejamento/hoje">
          <i class="bi bi-sun" aria-hidden="true"></i>
          <span>Hoje</span>
        </RouterLink>
        <RouterLink to="/planejamento/semana">
          <i class="bi bi-calendar-week" aria-hidden="true"></i>
          <span>Planejamento</span>
        </RouterLink>
        <RouterLink to="/planejamento/prioridades">
          <i class="bi bi-list-ol" aria-hidden="true"></i>
          <span>Prioridades</span>
        </RouterLink>
        <RouterLink to="/estudos">
          <i class="bi bi-clock-history" aria-hidden="true"></i>
          <span>Histórico</span>
        </RouterLink>

        <p class="rotulo-da-navegacao">Organização</p>
        <RouterLink to="/materias">
          <i class="bi bi-journal-text" aria-hidden="true"></i>
          <span>Conteúdos</span>
        </RouterLink>
        <RouterLink to="/materiais">
          <i class="bi bi-collection" aria-hidden="true"></i>
          <span>Materiais</span>
        </RouterLink>
        <RouterLink to="/concursos">
          <i class="bi bi-bullseye" aria-hidden="true"></i>
          <span>Meu concurso</span>
        </RouterLink>
        <RouterLink
          v-if="assistenteTelegramHabilitado"
          to="/integracoes/telegram"
        >
          <i class="bi bi-telegram" aria-hidden="true"></i>
          <span>Integração Telegram</span>
        </RouterLink>
      </nav>
    </aside>

    <header class="topo-da-aplicacao" aria-label="Ações da conta">
      <div class="topo-da-aplicacao-interno">
        <RouterLink
          class="marca-da-aplicacao d-lg-none"
          to="/dashboard"
          aria-label="Trilha da Aprovação — início"
          @click="fecharMenu"
        >
          <svg class="simbolo-da-marca" viewBox="0 0 48 36" aria-hidden="true">
            <path d="M4 28 14 19l7 5L34 8" />
            <path d="M8 10c5 3 9 3 13-1M27 28c6 0 11-3 17-9" />
          </svg>
          <span>Trilha da Aprovação</span>
        </RouterLink>

        <div class="acoes-do-topo d-flex align-items-center gap-3 ms-auto">
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
      </div>
    </header>

    <div id="conteudo-principal" class="conteudo-principal" tabindex="-1">
      <RouterView />
    </div>

    <nav class="navegacao-movel" aria-label="Navegação principal">
      <RouterLink to="/dashboard">
        <i class="bi bi-house" aria-hidden="true"></i>
        <span>Início</span>
      </RouterLink>
      <RouterLink to="/planejamento/hoje">
        <i class="bi bi-sun" aria-hidden="true"></i>
        <span>Hoje</span>
      </RouterLink>
      <RouterLink to="/planejamento/semana">
        <i class="bi bi-calendar-week" aria-hidden="true"></i>
        <span>Planejar</span>
      </RouterLink>
      <RouterLink to="/materias">
        <i class="bi bi-book" aria-hidden="true"></i>
        <span>Conteúdos</span>
      </RouterLink>
      <button
        ref="botaoDoMenu"
        type="button"
        :aria-expanded="menuAberto"
        aria-haspopup="dialog"
        aria-controls="menu-movel-mais"
        @click="alternarMenu"
      >
        <i class="bi bi-grid" aria-hidden="true"></i>
        <span>Mais</span>
      </button>
    </nav>

    <div
      v-if="menuAberto"
      id="menu-movel-mais"
      ref="raizDoMenu"
      class="sobreposicao-do-menu-movel"
      @click.self="fecharMenu"
    >
      <aside
        class="menu-movel-mais"
        role="dialog"
        aria-modal="true"
        aria-labelledby="titulo-do-menu-movel"
      >
        <header>
          <div>
            <span class="rotulo-da-navegacao">Navegação</span>
            <h2 id="titulo-do-menu-movel">Mais opções</h2>
          </div>
          <button
            class="botao-de-icone"
            type="button"
            aria-label="Fechar menu"
            autofocus
            @click="fecharMenu"
          >
            <i class="bi bi-x-lg" aria-hidden="true"></i>
          </button>
        </header>
        <nav aria-label="Outros destinos" @click="fecharMenu">
          <RouterLink to="/concursos">
            <i class="bi bi-bullseye" aria-hidden="true"></i>Meu concurso
          </RouterLink>
          <RouterLink to="/materiais">
            <i class="bi bi-collection" aria-hidden="true"></i>Materiais
          </RouterLink>
          <RouterLink to="/estudos">
            <i class="bi bi-clock-history" aria-hidden="true"></i>Histórico
          </RouterLink>
          <RouterLink to="/planejamento/prioridades">
            <i class="bi bi-list-ol" aria-hidden="true"></i>Prioridades
          </RouterLink>
          <RouterLink
            v-if="assistenteTelegramHabilitado"
            to="/integracoes/telegram"
          >
            <i class="bi bi-telegram" aria-hidden="true"></i>Telegram
          </RouterLink>
        </nav>
        <footer>
          <span>{{ sessao.usuario?.nome }}</span>
          <button type="button" @click="sair">
            <i class="bi bi-box-arrow-right" aria-hidden="true"></i>Sair
          </button>
        </footer>
      </aside>
    </div>

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
