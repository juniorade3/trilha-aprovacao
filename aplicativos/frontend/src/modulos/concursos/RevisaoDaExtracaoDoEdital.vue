<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'

import EditorDaEstruturaDoEdital from './EditorDaEstruturaDoEdital.vue'
import type {
  AlvoDaExtracaoAssistida,
  ConfirmacaoDeCampoDaExtracao,
  ConcursoExtraido,
  DecisoesDaImportacaoDeEdital,
  EditalExtraido,
  ExtracaoEstruturadaDoEdital,
  ExtracaoEstruturadaEditavelDoEdital,
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
  salvandoExtracao?: boolean
  extraindoComIa?: boolean
  erroDaIa?: string
}>()

const emitir = defineEmits<{
  salvar: [decisoes: DecisoesDaImportacaoDeEdital]
  salvarExtracao: [
    extracao: ExtracaoEstruturadaDoEdital,
    confirmacoesDeCampos: ConfirmacaoDeCampoDaExtracao[],
  ]
  extrairComIa: [alvo: AlvoDaExtracaoAssistida]
}>()

const cargoSelecionado = ref('')
const politica = ref<PoliticaDeReutilizacao>('EXIGIR_DECISAO')
const somentePendencias = ref(false)
const rascunhoAlterado = ref(false)
const confirmacoesDeCampos = ref<ConfirmacaoDeCampoDaExtracao[]>([])
const alterada = computed(
  () => rascunhoAlterado.value || confirmacoesDeCampos.value.length > 0,
)
const consentiuComIa = ref(false)
const modoDoAlvoDaIa = ref<'CARGO_EXISTENTE' | 'DESCRICAO'>('DESCRICAO')
const descricaoDoCargoAlvo = ref('')
const editor = ref<InstanceType<typeof EditorDaEstruturaDoEdital>>()

function dado<T>(valor: T | null = null): ValorExtraido<T> {
  return { valor, confianca: 1, fonte: null, inferido: false }
}

function concursoVazio(): ConcursoExtraido {
  return {
    nome: dado<string>(),
    descricao: dado<string>(),
    orgao: dado<string>(),
    banca: dado<string>(),
    dataPrevista: dado<string>(),
  }
}

function editalVazio(): EditalExtraido {
  return {
    titulo: dado<string>(),
    numero: dado<string>(),
    ano: dado<number>(),
    descricao: dado<string>(),
    dataDePublicacao: dado<string>(),
  }
}

function copiar<T>(valor: T): T {
  return JSON.parse(JSON.stringify(valor)) as T
}

function criarRascunho(): ExtracaoEstruturadaEditavelDoEdital {
  const extracao = propriedades.importacao.extracao
  if (!extracao)
    return {
      versaoDoContrato: '1',
      fonte: {
        nomeDoArquivo: propriedades.importacao.nomeDoArquivo,
        sha256: propriedades.importacao.sha256,
        paginas: 1,
      },
      concurso: concursoVazio(),
      edital: editalVazio(),
      cargos: [],
      provas: [],
      materias: [],
      avisos: [],
      incertezas: [],
    }
  const copia = copiar(extracao)
  return {
    ...copia,
    concurso: copia.concurso ?? concursoVazio(),
    edital: copia.edital ?? editalVazio(),
  }
}

const rascunho = ref<ExtracaoEstruturadaEditavelDoEdital>(criarRascunho())

watch(
  () =>
    [
      propriedades.importacao.identificador,
      propriedades.importacao.versaoAtualDaExtracao,
      propriedades.importacao.hashDaExtracaoAtual,
    ] as const,
  () => {
    rascunho.value = criarRascunho()
    rascunhoAlterado.value = false
    confirmacoesDeCampos.value = []
    consentiuComIa.value = false
  },
)

watch(
  () => [
    propriedades.importacao.chaveDoCargoSelecionado,
    propriedades.importacao.politicaDeReutilizacao,
  ],
  () => {
    cargoSelecionado.value =
      propriedades.importacao.chaveDoCargoSelecionado ?? ''
    politica.value =
      propriedades.importacao.politicaDeReutilizacao ?? 'EXIGIR_DECISAO'
    modoDoAlvoDaIa.value = cargoSelecionado.value
      ? 'CARGO_EXISTENTE'
      : 'DESCRICAO'
  },
  { immediate: true },
)

const cargos = computed(() =>
  [...rascunho.value.cargos].sort((a, b) => a.ordem - b.ordem),
)

const avaliacoesPorCargo = computed(
  () =>
    new Map(
      (propriedades.importacao.avaliacoesDosCargos ?? []).map((avaliacao) => [
        avaliacao.chaveDoCargo,
        avaliacao,
      ]),
    ),
)

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

const quantidadeDePendencias = computed(
  () =>
    problemasPorSeveridade.value.BLOQUEANTE.length +
    problemasPorSeveridade.value.EXIGE_DECISAO.length,
)

const podeSalvarSelecao = computed(
  () =>
    Boolean(cargoSelecionado.value) &&
    !alterada.value &&
    !propriedades.salvandoExtracao &&
    !propriedades.extraindoComIa,
)

const podeExtrairComIa = computed(() => {
  if (
    !propriedades.importacao.interpretacaoAssistidaDisponivel ||
    !consentiuComIa.value ||
    propriedades.extraindoComIa ||
    propriedades.salvandoExtracao ||
    propriedades.salvando ||
    alterada.value
  )
    return false
  return modoDoAlvoDaIa.value === 'CARGO_EXISTENTE'
    ? Boolean(cargoSelecionado.value)
    : Boolean(descricaoDoCargoAlvo.value.trim())
})

const rotulosDasSeveridades: Record<SeveridadeDoProblemaDaImportacao, string> =
  {
    BLOQUEANTE: 'Bloqueia a preparação',
    EXIGE_DECISAO: 'Exige sua decisão',
    AVISO: 'Aviso para revisão',
  }

function valor<T>(dadoExtraido?: ValorExtraido<T> | null) {
  return dadoExtraido?.valor ?? undefined
}

function formatarConfianca(confianca: number) {
  return new Intl.NumberFormat('pt-BR', {
    style: 'percent',
    maximumFractionDigits: 0,
  }).format(confianca)
}

function marcarComoAlterada() {
  rascunhoAlterado.value = true
}

function mesmaReferencia(
  primeira: ConfirmacaoDeCampoDaExtracao,
  segunda: ConfirmacaoDeCampoDaExtracao,
) {
  return (
    primeira.tipoDoRecurso === segunda.tipoDoRecurso &&
    primeira.chaveDoRecurso === segunda.chaveDoRecurso &&
    primeira.campo === segunda.campo
  )
}

function atualizarConfirmacao(
  referencia: ConfirmacaoDeCampoDaExtracao,
  confirmada: boolean,
) {
  const atuais = confirmacoesDeCampos.value.filter(
    (item) => !mesmaReferencia(item, referencia),
  )
  confirmacoesDeCampos.value = confirmada ? [...atuais, referencia] : atuais
}

function selecionarCargoAdicionado(chaveDoCargo: string) {
  cargoSelecionado.value = chaveDoCargo
}

function salvarExtracao() {
  if (
    !alterada.value ||
    propriedades.salvandoExtracao ||
    propriedades.salvando ||
    propriedades.extraindoComIa
  )
    return
  emitir(
    'salvarExtracao',
    copiar(rascunho.value),
    copiar(confirmacoesDeCampos.value),
  )
}

function salvarSelecao() {
  if (!podeSalvarSelecao.value) return
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

function extrairComIa() {
  if (!podeExtrairComIa.value) return
  const alvo: AlvoDaExtracaoAssistida =
    modoDoAlvoDaIa.value === 'CARGO_EXISTENTE'
      ? { chaveDoCargoAlvo: cargoSelecionado.value }
      : { descricaoDoCargoAlvo: descricaoDoCargoAlvo.value.trim() }
  emitir('extrairComIa', alvo)
}

async function focarPrimeiraPendencia() {
  somentePendencias.value = true
  await nextTick()
  editor.value?.focarPrimeiraPendencia()
}
</script>

<template>
  <section
    class="card revisao-da-extracao"
    aria-labelledby="titulo-da-revisao-da-extracao"
  >
    <header class="cabecalho-da-revisao">
      <div>
        <p class="sobretitulo-da-pagina">Extração concluída</p>
        <h2 id="titulo-da-revisao-da-extracao">Revisar extração</h2>
        <p>
          Selecione o cargo alvo e corrija somente o necessário. Você pode
          salvar o trabalho mesmo enquanto ainda houver pendências.
        </p>
      </div>
      <div class="filtros-da-revisao">
        <label>
          <input v-model="somentePendencias" type="checkbox" />
          Mostrar somente pendências
        </label>
        <button
          v-if="quantidadeDePendencias"
          class="btn btn-sm btn-outline-secondary"
          type="button"
          @click="focarPrimeiraPendencia"
        >
          Ir para a primeira pendência
        </button>
      </div>
    </header>

    <fieldset class="selecao-do-cargo">
      <legend>Cargo alvo</legend>
      <p v-if="cargos.length === 0" class="alert alert-warning" role="status">
        Nenhum cargo foi encontrado automaticamente. Crie um no editor abaixo ou
        peça a extração assistida informando o cargo desejado.
      </p>
      <article
        v-for="cargo in cargos"
        :key="cargo.chave"
        class="cargo-extraido"
        :class="{ selecionado: cargoSelecionado === cargo.chave }"
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
          <span
            v-if="avaliacoesPorCargo.get(cargo.chave)"
            class="estado-do-cargo"
            :class="{
              pronto: avaliacoesPorCargo.get(cargo.chave)?.pronto,
            }"
          >
            <i
              class="bi"
              :class="
                avaliacoesPorCargo.get(cargo.chave)?.pronto
                  ? 'bi-check-circle'
                  : 'bi-exclamation-triangle'
              "
              aria-hidden="true"
            ></i>
            {{
              avaliacoesPorCargo.get(cargo.chave)?.pronto
                ? 'Pronto'
                : `${avaliacoesPorCargo.get(cargo.chave)?.problemas.length ?? 0} pendência(s)`
            }}
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
      <p
        v-if="cargoSelecionado && !alterada"
        class="orientacao-da-selecao"
        role="status"
      >
        A seleção pode ser salva agora. Pendências de outros cargos não
        impedirão a validação deste cargo.
      </p>
    </fieldset>

    <p v-if="alterada" class="alert alert-warning m-0" role="status">
      Salve as correções antes de validar a seleção ou extrair um cargo com IA.
      Assim, as duas ações usarão a versão mais recente do rascunho.
    </p>

    <section class="extracao-assistida" aria-labelledby="titulo-extracao-ia">
      <div>
        <p class="sobretitulo-da-pagina">Opcional</p>
        <h3 id="titulo-extracao-ia">Extrair este cargo com IA</h3>
        <p>
          Use quando o parser não encontrou a estrutura ou quando o PDF é
          digitalizado. O resultado será uma nova versão revisável; nenhuma
          informação será aplicada ao concurso nesta etapa.
        </p>
      </div>

      <p
        v-if="!importacao.interpretacaoAssistidaDisponivel"
        class="alert alert-secondary"
        role="status"
      >
        Extração com IA indisponível neste ambiente. O editor manual continua
        funcionando normalmente.
      </p>

      <template v-else>
        <fieldset class="alvo-da-ia">
          <legend>Qual cargo deve ser extraído?</legend>
          <label v-if="cargoSelecionado">
            <input
              v-model="modoDoAlvoDaIa"
              type="radio"
              value="CARGO_EXISTENTE"
            />
            Completar o cargo selecionado
          </label>
          <label>
            <input v-model="modoDoAlvoDaIa" type="radio" value="DESCRICAO" />
            Informar outro cargo
          </label>
          <label v-if="modoDoAlvoDaIa === 'DESCRICAO'" class="campo-do-alvo">
            <span>Nome, área ou especialidade do cargo</span>
            <input
              v-model="descricaoDoCargoAlvo"
              type="text"
              placeholder="Ex.: Analista de TI — Engenharia de Dados"
            />
          </label>
        </fieldset>
        <label class="consentimento-da-ia">
          <input v-model="consentiuComIa" type="checkbox" />
          <span>
            Autorizo o envio deste edital ao provedor de IA exclusivamente para
            extrair o cargo informado. Sei que o resultado ainda precisará ser
            revisado.
          </span>
        </label>
        <p v-if="erroDaIa" class="alert alert-warning" role="alert">
          {{ erroDaIa }} O staging não foi alterado; continue pelo editor manual
          ou tente novamente mais tarde.
        </p>
        <button
          class="btn btn-outline-primary align-self-start"
          type="button"
          :disabled="!podeExtrairComIa"
          @click="extrairComIa"
        >
          <span
            v-if="extraindoComIa"
            class="spinner-border spinner-border-sm me-1"
            aria-hidden="true"
          ></span>
          {{
            extraindoComIa
              ? 'Extraindo e verificando…'
              : 'Extrair este cargo com IA'
          }}
        </button>
      </template>
    </section>

    <EditorDaEstruturaDoEdital
      ref="editor"
      v-model="rascunho"
      :problemas="importacao.problemas"
      :somente-pendencias="somentePendencias"
      :confirmacoes-de-campos="confirmacoesDeCampos"
      @alterado="marcarComoAlterada"
      @cargo-adicionado="selecionarCargoAdicionado"
      @confirmacao-alterada="atualizarConfirmacao"
    />

    <section
      v-for="severidade in ['BLOQUEANTE', 'EXIGE_DECISAO', 'AVISO'] as const"
      v-show="problemasPorSeveridade[severidade].length"
      :key="severidade"
      class="grupo-de-problemas"
      :class="`severidade-${severidade.toLocaleLowerCase('pt-BR')}`"
      :aria-labelledby="`titulo-${severidade}`"
    >
      <details :open="severidade !== 'AVISO'">
        <summary>
          <h3 :id="`titulo-${severidade}`">
            {{ rotulosDasSeveridades[severidade] }}
            <span class="badge etiqueta-neutra">
              {{ problemasPorSeveridade[severidade].length }}
            </span>
          </h3>
        </summary>
        <article
          v-for="(problema, indice) in problemasPorSeveridade[severidade]"
          :key="`${problema.codigo}-${problema.chaveDoRecurso ?? problema.caminho ?? indice}`"
        >
          <strong>{{ problema.mensagem }}</strong>
          <small v-if="problema.campo">Campo: {{ problema.campo }}</small>
          <small v-else-if="problema.caminho">
            Local da extração: {{ problema.caminho }}
          </small>
        </article>
      </details>
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
      <span v-if="alterada" class="estado-do-rascunho" role="status">
        Há correções não salvas. Salve-as antes de validar ou usar IA.
      </span>
      <button
        class="btn btn-outline-primary"
        type="button"
        :disabled="salvandoExtracao || salvando || extraindoComIa || !alterada"
        @click="salvarExtracao"
      >
        {{ salvandoExtracao ? 'Salvando…' : 'Salvar correções' }}
      </button>
      <button
        class="btn btn-primary"
        type="button"
        :disabled="salvando || !podeSalvarSelecao"
        @click="salvarSelecao"
      >
        {{ salvando ? 'Validando…' : 'Salvar seleção e validar' }}
      </button>
    </footer>
  </section>
</template>

<style scoped lang="scss">
.revisao-da-extracao {
  display: grid;
  gap: 1.5rem;
  padding: clamp(1.25rem, 3vw, 2rem);
}

.cabecalho-da-revisao {
  align-items: end;
  display: flex;
  gap: 1rem;
  justify-content: space-between;
}

.filtros-da-revisao {
  align-items: end;
  display: grid;
  gap: 0.5rem;
  justify-items: start;
}

.filtros-da-revisao label {
  align-items: center;
  display: flex;
  gap: 0.45rem;
}

.revisao-da-extracao fieldset {
  border: 0;
  margin: 0;
  padding: 0;
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
  margin-bottom: 0.75rem;
  padding: 1rem;
}

.cargo-extraido.selecionado {
  border-color: var(--bs-primary);
  box-shadow: 0 0 0 0.12rem
    color-mix(in srgb, var(--bs-primary) 18%, transparent);
}

.cargo-extraido > label {
  align-items: start;
  cursor: pointer;
  display: flex;
  gap: 0.75rem;
}

.cargo-extraido > label > span:nth-child(2) {
  flex: 1;
}

.cargo-extraido details {
  margin: 0.75rem 0 0 1.75rem;
}

.cargo-extraido small,
.cargo-extraido strong {
  display: block;
}

.estado-do-cargo {
  color: var(--bs-warning-text-emphasis);
  font-size: 0.85rem;
  font-weight: 650;
  white-space: nowrap;
}

.estado-do-cargo.pronto {
  color: var(--bs-success-text-emphasis);
}

.orientacao-da-selecao {
  color: var(--bs-secondary-color);
  font-size: 0.9rem;
}

.extracao-assistida {
  background: color-mix(in srgb, var(--bs-info-bg-subtle) 55%, transparent);
  border: 1px solid var(--bs-info-border-subtle);
  border-radius: 0.8rem;
  display: grid;
  gap: 0.85rem;
  padding: 1rem;
}

.extracao-assistida h3,
.extracao-assistida p {
  margin-bottom: 0.35rem;
}

.alvo-da-ia {
  display: grid;
  gap: 0.5rem;
}

.alvo-da-ia > label:not(.campo-do-alvo),
.consentimento-da-ia {
  align-items: start;
  display: flex;
  gap: 0.5rem;
}

.campo-do-alvo {
  display: grid;
  gap: 0.3rem;
  margin-top: 0.35rem;
}

.campo-do-alvo input,
.politica-de-reutilizacao select {
  border: 1px solid var(--bs-border-color);
  border-radius: 0.45rem;
  min-height: 2.55rem;
  padding: 0.45rem 0.6rem;
  width: 100%;
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

.grupo-de-problemas summary {
  cursor: pointer;
}

.grupo-de-problemas summary h3 {
  display: inline;
}

.grupo-de-problemas article {
  display: grid;
  gap: 0.35rem;
  margin-top: 0.75rem;
}

.revisao-da-extracao footer {
  align-items: center;
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem;
  justify-content: end;
  position: sticky;
  bottom: 0;
  background: color-mix(in srgb, var(--bs-body-bg) 94%, transparent);
  border-top: 1px solid var(--bs-border-color);
  padding-block: 0.8rem;
  z-index: 2;
}

.estado-do-rascunho {
  color: var(--bs-warning-text-emphasis);
  margin-right: auto;
}

@media (max-width: 767px) {
  .cabecalho-da-revisao {
    align-items: stretch;
    flex-direction: column;
  }

  .cargo-extraido > label {
    flex-wrap: wrap;
  }

  .estado-do-cargo {
    margin-left: 1.75rem;
    width: 100%;
  }

  .revisao-da-extracao footer {
    align-items: stretch;
    flex-direction: column;
  }

  .revisao-da-extracao footer .btn {
    width: 100%;
  }
}
</style>
