<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'

import { usarSessao } from '@/aplicacao/estado/sessao'
import { requisitar } from '@/compartilhado/api/clienteHttp'

const sessao = usarSessao()
const roteador = useRouter()
const menuAberto = ref(false)

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
</script>

<template>
  <div class="min-vh-100">
    <nav class="navbar navbar-expand-lg navbar-dark barra-principal">
      <div class="container">
        <RouterLink
          class="navbar-brand marca-da-aplicacao"
          to="/dashboard"
          @click="fecharMenu"
        >
          <span class="simbolo-da-marca" aria-hidden="true">
            <i class="bi bi-check2"></i>
          </span>
          <span>Trilha da Aprovação</span>
        </RouterLink>
        <button
          class="navbar-toggler border-0"
          type="button"
          aria-controls="navegacao-principal"
          :aria-expanded="menuAberto"
          aria-label="Alternar navegação"
          @click="menuAberto = !menuAberto"
        >
          <span class="navbar-toggler-icon"></span>
        </button>
        <div
          id="navegacao-principal"
          class="collapse navbar-collapse"
          :class="{ show: menuAberto }"
        >
          <div class="navbar-nav navegacao-do-produto mx-lg-auto">
            <RouterLink class="nav-link" to="/dashboard" @click="fecharMenu">
              Painel
            </RouterLink>
            <RouterLink class="nav-link" to="/concursos" @click="fecharMenu">
              Concursos
            </RouterLink>
            <RouterLink class="nav-link" to="/materias" @click="fecharMenu">
              Matérias
            </RouterLink>
            <RouterLink class="nav-link" to="/materiais" @click="fecharMenu">
              Materiais
            </RouterLink>
            <RouterLink class="nav-link" to="/estudos" @click="fecharMenu">
              Estudos
            </RouterLink>
          </div>
          <div class="usuario-da-navegacao">
            <span class="avatar-do-usuario" aria-hidden="true">
              {{ sessao.usuario?.nome?.charAt(0).toUpperCase() || 'U' }}
            </span>
            <span class="nome-do-usuario">{{ sessao.usuario?.nome }}</span>
            <button
              class="btn botao-sair"
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
    </nav>
    <RouterView />
  </div>
</template>
