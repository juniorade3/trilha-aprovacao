<script setup lang="ts">
import { useRouter } from 'vue-router'

import { usarSessao } from '@/aplicacao/estado/sessao'
import { requisitar } from '@/compartilhado/api/clienteHttp'

const sessao = usarSessao()
const roteador = useRouter()

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
    <nav class="navbar navbar-expand-lg navbar-dark bg-primary shadow-sm">
      <div class="container">
        <RouterLink class="navbar-brand fw-semibold" to="/dashboard">
          Trilha da Aprovacao
        </RouterLink>
        <div class="d-flex align-items-center gap-2 gap-md-3">
          <RouterLink class="nav-link text-white" to="/dashboard">
            Inicio
          </RouterLink>
          <RouterLink class="nav-link text-white" to="/materias">
            Materias
          </RouterLink>
          <span class="text-white-50 d-none d-md-inline">
            {{ sessao.usuario?.nome }}
          </span>
          <button
            class="btn btn-outline-light btn-sm"
            type="button"
            @click="sair"
          >
            Sair
          </button>
        </div>
      </div>
    </nav>
    <RouterView />
  </div>
</template>
