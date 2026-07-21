<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { requisitar } from '@/compartilhado/api/clienteHttp'
import { usarSessao } from '@/aplicacao/estado/sessao'

const email = ref('')
const senha = ref('')
const erro = ref('')
const enviando = ref(false)
const exibirSenha = ref(false)
const lembrar = ref(false)
const campoDeEmail = ref<HTMLInputElement>()
const rota = useRoute()
const roteador = useRouter()
const sessao = usarSessao()
const avisoDeSessao = computed(() =>
  rota.query.sessao === 'expirada'
    ? 'Sua sessão expirou. Entre novamente para continuar de onde parou.'
    : '',
)
onMounted(() => campoDeEmail.value?.focus())

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
  <section class="cartao-de-autenticacao">
    <div>
      <p class="rotulo-da-autenticacao mb-2">Bem-vindo de volta</p>
      <h1>Entrar</h1>
      <p class="text-secondary">Continue sua trilha de preparação.</p>
      <p v-if="avisoDeSessao" class="alert alert-info" aria-live="polite">
        {{ avisoDeSessao }}
      </p>
      <p v-if="erro" class="alert alert-danger" aria-live="polite">
        {{ erro }}
      </p>
      <form @submit.prevent="entrar">
        <label class="form-label" for="email">E-mail</label
        ><input
          id="email"
          ref="campoDeEmail"
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
        <div class="form-check mb-4">
          <input
            id="lembrar"
            v-model="lembrar"
            class="form-check-input"
            type="checkbox"
          />
          <label class="form-check-label" for="lembrar">
            Lembrar meu e-mail neste dispositivo
          </label>
          <div class="form-text">
            Preferência apenas visual nesta versão; nenhum dado será salvo.
          </div>
        </div>
        <button class="btn btn-primary" type="submit" :disabled="enviando">
          {{ enviando ? 'Entrando...' : 'Entrar' }}</button
        ><RouterLink class="ms-3" to="/cadastro">Criar conta</RouterLink>
      </form>
    </div>
  </section>
</template>
