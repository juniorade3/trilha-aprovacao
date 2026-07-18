<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { requisitar } from '@/compartilhado/api/clienteHttp'

const nome = ref('')
const email = ref('')
const senha = ref('')
const confirmacao = ref('')
const erro = ref('')
const enviando = ref(false)
const roteador = useRouter()
async function cadastrar() {
  erro.value = ''
  if (senha.value !== confirmacao.value) {
    erro.value = 'As senhas nao conferem.'
    return
  }
  enviando.value = true
  try {
    await requisitar('/v1/autenticacao/cadastro', {
      method: 'POST',
      body: JSON.stringify({
        nome: nome.value,
        email: email.value,
        senha: senha.value,
      }),
    })
    await roteador.push('/login')
  } catch (causa) {
    erro.value =
      causa instanceof Error ? causa.message : 'Nao foi possivel cadastrar.'
  } finally {
    enviando.value = false
  }
}
</script>
<template>
  <section class="cartao-de-autenticacao">
    <div>
      <p class="rotulo-da-autenticacao mb-2">Comece sua trilha</p>
      <h1>Criar conta</h1>
      <p class="text-secondary">Organize sua preparação em poucos minutos.</p>
      <p v-if="erro" class="alert alert-danger" aria-live="polite">
        {{ erro }}
      </p>
      <form @submit.prevent="cadastrar">
        <label class="form-label" for="nome">Nome</label
        ><input
          id="nome"
          v-model="nome"
          class="form-control mb-3"
          required
        /><label class="form-label" for="email">E-mail</label
        ><input
          id="email"
          v-model="email"
          class="form-control mb-3"
          type="email"
          required
        /><label class="form-label" for="senha">Senha</label
        ><input
          id="senha"
          v-model="senha"
          class="form-control mb-3"
          type="password"
          minlength="8"
          required
        /><label class="form-label" for="confirmacao">Confirmar senha</label
        ><input
          id="confirmacao"
          v-model="confirmacao"
          class="form-control mb-3"
          type="password"
          required
        /><button class="btn btn-primary" :disabled="enviando">
          {{ enviando ? 'Cadastrando...' : 'Cadastrar' }}</button
        ><RouterLink class="ms-3" to="/login">Ja tenho conta</RouterLink>
      </form>
    </div>
  </section>
</template>
