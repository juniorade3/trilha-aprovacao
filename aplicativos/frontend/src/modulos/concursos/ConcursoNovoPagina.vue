<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'

import {
  criarConcurso,
  type DadosDeConcurso,
  type SituacaoDoConcurso,
} from './apiDeConcursos'

const roteador = useRouter()
const salvando = ref(false)
const erro = ref('')
const formulario = reactive<DadosDeConcurso>({
  nome: '',
  descricao: '',
  orgao: '',
  banca: '',
  situacao: 'PLANEJADO',
  dataPrevistaPrincipal: '',
})

const situacoes: { valor: SituacaoDoConcurso; rotulo: string }[] = [
  { valor: 'PLANEJADO', rotulo: 'Planejado' },
  { valor: 'EDITAL_PUBLICADO', rotulo: 'Edital publicado' },
  { valor: 'INSCRICOES_ABERTAS', rotulo: 'Inscricoes abertas' },
  { valor: 'EM_ANDAMENTO', rotulo: 'Em andamento' },
  { valor: 'ENCERRADO', rotulo: 'Encerrado' },
  { valor: 'SUSPENSO', rotulo: 'Suspenso' },
  { valor: 'CANCELADO', rotulo: 'Cancelado' },
]

async function salvar() {
  salvando.value = true
  erro.value = ''
  try {
    const concurso = await criarConcurso({
      ...formulario,
      descricao: formulario.descricao || undefined,
      orgao: formulario.orgao || undefined,
      banca: formulario.banca || undefined,
      dataPrevistaPrincipal: formulario.dataPrevistaPrincipal || undefined,
    })
    await roteador.push(`/concursos/${concurso.identificador}?novo=true`)
  } catch (causa) {
    erro.value =
      causa instanceof Error ? causa.message : 'Nao foi possivel salvar.'
  } finally {
    salvando.value = false
  }
}
</script>

<template>
  <main class="container py-4 py-md-5">
    <button
      class="btn btn-link px-0 mb-3"
      type="button"
      @click="roteador.push('/concursos')"
    >
      <i class="bi bi-arrow-left" aria-hidden="true"></i>
      Voltar para concursos
    </button>

    <div class="row g-4">
      <section class="col-lg-8">
        <header class="mb-4">
          <p class="text-uppercase fw-semibold text-success mb-1">
            Etapa 1 de 6
          </p>
          <h1>Novo concurso</h1>
          <p class="text-secondary">
            Comece pelos dados gerais. Edital, cargo, prova, grupos e materias
            podem ser adicionados depois.
          </p>
        </header>

        <p
          v-if="erro"
          class="alert alert-danger"
          role="alert"
          aria-live="assertive"
        >
          {{ erro }}
        </p>

        <form
          class="card card-body border-0 shadow-sm"
          @submit.prevent="salvar"
        >
          <fieldset :disabled="salvando">
            <label class="form-label" for="nome-concurso">Nome</label>
            <input
              id="nome-concurso"
              v-model="formulario.nome"
              class="form-control mb-3"
              maxlength="160"
              required
            />
            <div class="row g-3">
              <div class="col-md-6">
                <label class="form-label" for="orgao-concurso">Orgao</label>
                <input
                  id="orgao-concurso"
                  v-model="formulario.orgao"
                  class="form-control"
                  maxlength="160"
                />
              </div>
              <div class="col-md-6">
                <label class="form-label" for="banca-concurso">Banca</label>
                <input
                  id="banca-concurso"
                  v-model="formulario.banca"
                  class="form-control"
                  maxlength="160"
                />
              </div>
              <div class="col-md-6">
                <label class="form-label" for="situacao-concurso"
                  >Situacao</label
                >
                <select
                  id="situacao-concurso"
                  v-model="formulario.situacao"
                  class="form-select"
                >
                  <option
                    v-for="situacao in situacoes"
                    :key="situacao.valor"
                    :value="situacao.valor"
                  >
                    {{ situacao.rotulo }}
                  </option>
                </select>
              </div>
              <div class="col-md-6">
                <label class="form-label" for="data-concurso">
                  Data principal prevista
                </label>
                <input
                  id="data-concurso"
                  v-model="formulario.dataPrevistaPrincipal"
                  class="form-control"
                  type="date"
                />
              </div>
            </div>
            <label class="form-label mt-3" for="descricao-concurso">
              Descricao
            </label>
            <textarea
              id="descricao-concurso"
              v-model="formulario.descricao"
              class="form-control mb-4"
              maxlength="1000"
              rows="4"
            ></textarea>
            <button class="btn btn-primary" :disabled="salvando">
              {{ salvando ? 'Salvando...' : 'Salvar e montar estrutura' }}
            </button>
          </fieldset>
        </form>
      </section>

      <aside class="col-lg-4">
        <div class="card card-body border-0 shadow-sm">
          <h2 class="h5">Etapas da estrutura</h2>
          <ol class="lista-de-etapas mb-0">
            <li class="fw-semibold text-success">Dados gerais</li>
            <li>Edital</li>
            <li>Cargo</li>
            <li>Prova</li>
            <li>Grupos</li>
            <li>Materias</li>
          </ol>
        </div>
      </aside>
    </div>
  </main>
</template>
