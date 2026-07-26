<script setup lang="ts">
import { computed, ref, watch } from 'vue'

import type {
  DecisoesDaImportacaoDeEdital,
  ImportacaoDeEdital,
  ModoDaImportacaoDeEdital,
  PoliticaDeReutilizacao,
  ProblemaDaImportacao,
  SeveridadeDoProblemaDaImportacao,
  ValorExtraido,
} from './apiDeImportacaoDeEdital'

const propriedades = defineProps<{
  importacao: ImportacaoDeEdital
  modo: ModoDaImportacaoDeEdital
  identificadorDoConcursoExistente?: string
  salvando: boolean
}>()

const emitir = defineEmits<{
  salvar: [decisoes: DecisoesDaImportacaoDeEdital]
}>()

const cargoSelecionado = ref('')
const politica = ref<PoliticaDeReutilizacao>('EXIGIR_DECISAO')

watch(
  () => propriedades.importacao,
  (importacao) => {
    cargoSelecionado.value = importacao.chaveDoCargoSelecionado ?? ''
    politica.value = importacao.politicaDeReutilizacao ?? 'EXIGIR_DECISAO'
  },
  { immediate: true },
)

const cargos = computed(() => propriedades.importacao.extracao?.cargos ?? [])
const problemasPorSeveridade = computed(() => {
  const grupos: Record<
    SeveridadeDoProblemaDaImportacao,
    ProblemaDaImportacao[]
  > = {
    BLOQUEANTE: [],
    EXIGE_DECISAO: [],
    AVISO: [],
  }
  for (const problema of propriedades.importacao.problemas ?? [])
    grupos[problema.severidade].push(problema)
  return grupos
})

const problemasQueExigemCorrecao = computed(() =>
  problemasPorSeveridade.value.EXIGE_DECISAO.filter(
    (problema) => problema.codigo !== 'SELECAO_DE_CARGO_OBRIGATORIA',
  ),
)

const podeSalvar = computed(
  () =>
    Boolean(cargoSelecionado.value) &&
    problemasPorSeveridade.value.BLOQUEANTE.length === 0 &&
    problemasQueExigemCorrecao.value.length === 0,
)

const rotulosDasSeveridades: Record<SeveridadeDoProblemaDaImportacao, string> =
  {
    BLOQUEANTE: 'Bloqueia a preparação',
    EXIGE_DECISAO: 'Exige sua decisão',
    AVISO: 'Aviso para revisão',
  }

function valor<T>(dado?: ValorExtraido<T> | null) {
  return dado?.valor ?? undefined
}

function formatarConfianca(confianca: number) {
  return new Intl.NumberFormat('pt-BR', {
    style: 'percent',
    maximumFractionDigits: 0,
  }).format(confianca)
}

function salvar() {
  if (!podeSalvar.value) return
  emitir('salvar', {
    chaveDoCargoSelecionado: cargoSelecionado.value,
    modo: propriedades.modo,
    identificadorDoConcursoExistente:
      propriedades.modo === 'COMPLEMENTAR_EXISTENTE'
        ? propriedades.identificadorDoConcursoExistente
        : undefined,
    politicaDeReutilizacao: politica.value,
    versaoDaExtracao: propriedades.importacao.versaoAtualDaExtracao,
    decisoesHumanas: {},
  })
}
</script>

<template>
  <section
    class="card revisao-da-extracao"
    aria-labelledby="titulo-da-selecao-do-cargo"
  >
    <header>
      <p class="sobretitulo-da-pagina">Extração concluída</p>
      <h2 id="titulo-da-selecao-do-cargo">Selecione o cargo correto</h2>
      <p>
        Conteúdos de cargos diferentes nunca serão misturados. Confira também
        alertas e decisões antes de preparar a importação.
      </p>
    </header>

    <fieldset>
      <legend>Cargo alvo</legend>
      <p v-if="cargos.length === 0" class="alert alert-danger" role="alert">
        Nenhum cargo válido foi encontrado na extração.
      </p>
      <article
        v-for="cargo in cargos"
        :key="cargo.chave"
        class="cargo-extraido"
      >
        <label>
          <input v-model="cargoSelecionado" type="radio" :value="cargo.chave" />
          <span>
            <strong>{{ valor(cargo.nome) || 'Nome não identificado' }}</strong>
            <small>
              {{ valor(cargo.area) || 'Área não informada' }}
              <template v-if="valor(cargo.especialidade)">
                · {{ valor(cargo.especialidade) }}
              </template>
            </small>
            <small>
              Confiança do nome: {{ formatarConfianca(cargo.nome.confianca) }}
              <template v-if="cargo.nome.inferido"> · dado inferido</template>
            </small>
          </span>
        </label>
        <details v-if="cargo.nome.fonte">
          <summary>Ver fonte</summary>
          <dl>
            <div v-if="cargo.nome.fonte.pagina">
              <dt>Página</dt>
              <dd>{{ cargo.nome.fonte.pagina }}</dd>
            </div>
            <div v-if="cargo.nome.fonte.secao">
              <dt>Seção</dt>
              <dd>{{ cargo.nome.fonte.secao }}</dd>
            </div>
            <div v-if="cargo.nome.fonte.trecho">
              <dt>Trecho</dt>
              <dd>{{ cargo.nome.fonte.trecho }}</dd>
            </div>
          </dl>
        </details>
      </article>
    </fieldset>

    <section
      v-for="severidade in ['BLOQUEANTE', 'EXIGE_DECISAO', 'AVISO'] as const"
      v-show="problemasPorSeveridade[severidade].length"
      :key="severidade"
      class="grupo-de-problemas"
      :class="`severidade-${severidade.toLocaleLowerCase('pt-BR')}`"
      :aria-labelledby="`titulo-${severidade}`"
    >
      <h3 :id="`titulo-${severidade}`">
        {{ rotulosDasSeveridades[severidade] }}
        <span class="badge text-bg-light">
          {{ problemasPorSeveridade[severidade].length }}
        </span>
      </h3>
      <article
        v-for="(problema, indice) in problemasPorSeveridade[severidade]"
        :key="`${problema.codigo}-${indice}`"
      >
        <strong>{{ problema.mensagem }}</strong>
        <small v-if="problema.caminho">Campo: {{ problema.caminho }}</small>
        <small
          v-if="
            severidade === 'EXIGE_DECISAO' &&
            problema.codigo !== 'SELECAO_DE_CARGO_OBRIGATORIA'
          "
        >
          Corrija a extração versionada antes de continuar. Texto livre não
          substitui o dado extraído.
        </small>
      </article>
    </section>

    <label class="politica-de-reutilizacao">
      <span>Política para matérias e tópicos equivalentes</span>
      <select v-model="politica">
        <option value="EXIGIR_DECISAO">Exigir decisão em cada conflito</option>
        <option value="REUTILIZAR_COMPATIVEIS">
          Reutilizar somente quando compatível
        </option>
        <option value="CRIAR_SEPARADO">Criar separado</option>
      </select>
    </label>

    <footer>
      <button
        class="btn btn-primary"
        type="button"
        :disabled="salvando || !podeSalvar"
        @click="salvar"
      >
        {{ salvando ? 'Validando…' : 'Salvar seleção e validar' }}
      </button>
    </footer>
  </section>
</template>

<style scoped lang="scss">
.revisao-da-extracao {
  padding: clamp(1.25rem, 3vw, 2rem);
  display: grid;
  gap: 1.5rem;
}

.revisao-da-extracao fieldset {
  border: 0;
  padding: 0;
  margin: 0;
}

.revisao-da-extracao legend,
.politica-de-reutilizacao > span {
  display: block;
  font-weight: 700;
  margin-bottom: 0.75rem;
}

.cargo-extraido {
  border: 1px solid var(--bs-border-color);
  border-radius: 0.75rem;
  padding: 1rem;
  margin-bottom: 0.75rem;
}

.cargo-extraido > label {
  display: flex;
  align-items: start;
  gap: 0.75rem;
  cursor: pointer;
}

.cargo-extraido details {
  margin: 0.75rem 0 0 1.75rem;
}

.cargo-extraido small,
.cargo-extraido strong {
  display: block;
}

.grupo-de-problemas {
  border-inline-start: 0.3rem solid var(--bs-secondary);
  padding-inline-start: 1rem;
}

.grupo-de-problemas.severidade-bloqueante {
  border-color: var(--bs-danger);
}

.grupo-de-problemas.severidade-exige_decisao {
  border-color: var(--bs-warning);
}

.grupo-de-problemas article {
  display: grid;
  gap: 0.35rem;
  margin-top: 0.75rem;
}

.grupo-de-problemas label {
  margin-top: 0.5rem;
}

.politica-de-reutilizacao select {
  width: 100%;
}

.revisao-da-extracao footer {
  display: flex;
  justify-content: end;
}
</style>
