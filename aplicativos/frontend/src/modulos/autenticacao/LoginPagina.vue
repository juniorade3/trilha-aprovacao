<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { requisitar } from '@/compartilhado/api/clienteHttp'
import { usarSessao } from '@/aplicacao/estado/sessao'

const email = ref('')
const senha = ref('')
const erro = ref('')
const enviando = ref(false)
const exibirSenha = ref(false)
const roteador = useRouter()
const sessao = usarSessao()
async function entrar() {
  erro.value = ''
  enviando.value = true
  try {
    await requisitar('/v1/autenticacao/login', {
      method: 'POST',
      body: JSON.stringify({ email: email.value, senha: senha.value }),
    })
    await sessao.atualizar()
    const redirecionar = roteador.currentRoute.value.query.redirecionar
    await roteador.push(
      typeof redirecionar === 'string' ? redirecionar : '/dashboard',
    )
  } catch (causa) {
    erro.value =
      causa instanceof Error ? causa.message : 'Nao foi possivel entrar.'
  } finally {
    enviando.value = false
  }
}
</script>
<template>
  <main class="container py-5">
    <section class="col-md-6 mx-auto bg-white p-4 rounded-4 shadow-sm">
      <h1>Entrar</h1>
      <p v-if="erro" class="alert alert-danger" aria-live="polite">
        {{ erro }}
      </p>
      <form @submit.prevent="entrar">
        <label class="form-label" for="email">E-mail</label
        ><input
          id="email"
          v-model="email"
          class="form-control mb-3"
          type="email"
          required
        /><label class="form-label" for="senha">Senha</label>
        <div class="input-group mb-3">
          <input
            id="senha"
            v-model="senha"
            class="form-control"
            :type="exibirSenha ? 'text' : 'password'"
            required
          />
          <button
            class="btn btn-outline-secondary"
            type="button"
            :aria-label="exibirSenha ? 'Ocultar senha' : 'Mostrar senha'"
            @click="exibirSenha = !exibirSenha"
          >
            <i
              class="bi"
              :class="exibirSenha ? 'bi-eye-slash' : 'bi-eye'"
              aria-hidden="true"
            ></i>
          </button>
        </div>
        <button class="btn btn-primary" :disabled="enviando">
          {{ enviando ? 'Entrando...' : 'Entrar' }}</button
        ><RouterLink class="ms-3" to="/cadastro">Criar conta</RouterLink>
      </form>
    </section>
  </main>
</template>
