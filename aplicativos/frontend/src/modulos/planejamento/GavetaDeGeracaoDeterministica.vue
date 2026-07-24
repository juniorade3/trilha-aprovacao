<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'

import { ErroDaApi } from '@/compartilhado/api/clienteHttp'
import GavetaLateral from '@/compartilhado/componentes/GavetaLateral.vue'
import ModalDaAplicacao from '@/compartilhado/componentes/ModalDaAplicacao.vue'
import {
  aplicarGeracaoDeterministica,
  gerarPreviaDeterministica,
  listarMateriasParaGeracao,
  substituirPrioridadesDeMaterias,
  type MateriaParaGeracao,
  type PreviaDaGeracao,
  type PrioridadeDaMateriaNoPlano,
  type ResultadoDaAplicacaoDaGeracao,
} from './apiDePlanejamento'

const propriedades = defineProps<{
  identificadorDoPlano: string
  dataDeReferencia: string
  quantidadeDeBlocosGerados: number
}>()
const emitir = defineEmits<{
  fechar: []
  aplicado: [resultado: ResultadoDaAplicacaoDaGeracao]
}>()

const etapa = ref<'PRIORIDADES' | 'CONFIGURACAO' | 'PREVIA'>('PRIORIDADES')
const materias = ref<MateriaParaGeracao[]>([])
const previa = ref<PreviaDaGeracao>()
const duracaoPrincipal = ref(50)
const quantidadeDeMateriasPorDia = ref(3)
const carregando = ref(true)
const processando = ref(false)
const erro = ref('')
const avisoDePreviaRecalculada = ref('')
const confirmacaoDeRegeneracaoAberta = ref(false)
const assinaturaLocalDaPrevia = ref('')
const assinaturaDasPrioridadesSalvas = ref('')
const substituirNaUltimaAplicacao = ref(false)
const botaoDeAplicacao = ref<HTMLButtonElement>()
const alertaDeErro = ref<HTMLDivElement>()
const operacaoParaTentarNovamente = ref<
  | 'CARREGAR_MATERIAS'
  | 'SALVAR_PRIORIDADES'
  | 'CALCULAR_PREVIA'
  | 'RECALCULAR_PREVIA_DESATUALIZADA'
  | 'APLICAR'
>()

const rotulos: Record<PrioridadeDaMateriaNoPlano, string> = {
  ALTA: 'Alta',
  NORMAL: 'Normal',
  BAIXA: 'Baixa',
  NAO_INCLUIR: 'Não incluir',
}

const rotulosDosTipos = {
  TEORIA: 'Teoria',
  QUESTOES: 'Questões',
  REVISAO: 'Revisão',
  CADERNO_DE_ERROS: 'Caderno de erros',
  SIMULADO: 'Simulado',
  DISCURSIVA: 'Discursiva',
  OUTRA: 'Outra',
} as const

const rotulosDosGrupos = {
  LACUNA: 'Lacuna',
  FRAQUEZA: 'Fraqueza',
  CONSOLIDADO: 'Consolidado',
} as const

const rotulosDasFaixas = {
  SEM_ESTUDO: 'Sem estudo',
  SEM_EVIDENCIA: 'Sem evidência',
  EVIDENCIA_DESATUALIZADA: 'Evidência desatualizada',
  DADOS_INSUFICIENTES: 'Dados insuficientes',
  PRECISA_REFORCO: 'Precisa de reforço',
  DESEMPENHO_PARCIAL: 'Desempenho parcial',
  CONSOLIDADO: 'Consolidado',
} as const

function prioridadesOrdenadas() {
  return materias.value
    .map((materia) => ({
      identificadorDaMateria: materia.identificadorDaMateria,
      prioridade: materia.prioridade,
    }))
    .sort((primeira, segunda) =>
      primeira.identificadorDaMateria.localeCompare(
        segunda.identificadorDaMateria,
      ),
    )
}

function criarAssinaturaDasPrioridades() {
  return JSON.stringify(prioridadesOrdenadas())
}

function criarAssinaturaDaPrevia() {
  return JSON.stringify({
    dataDeReferencia: propriedades.dataDeReferencia,
    duracaoPrincipal: Number(duracaoPrincipal.value),
    quantidadeDeMateriasPorDia: Number(quantidadeDeMateriasPorDia.value),
    prioridades: prioridadesOrdenadas(),
  })
}

const configuracaoInvalida = computed(() => {
  const duracao = Number(duracaoPrincipal.value)
  const quantidade = Number(quantidadeDeMateriasPorDia.value)
  return (
    !Number.isInteger(duracao) ||
    duracao < 25 ||
    duracao > 180 ||
    !Number.isInteger(quantidade) ||
    quantidade < 1 ||
    quantidade > 20
  )
})

const prioridadesNaoSalvas = computed(
  () =>
    assinaturaDasPrioridadesSalvas.value !== criarAssinaturaDasPrioridades(),
)

const previaDesatualizada = computed(
  () =>
    Boolean(previa.value) &&
    assinaturaLocalDaPrevia.value !== criarAssinaturaDaPrevia(),
)

function limparErro() {
  erro.value = ''
  operacaoParaTentarNovamente.value = undefined
}

function registrarErro(
  causa: unknown,
  mensagemPadrao: string,
  operacao:
    | 'CARREGAR_MATERIAS'
    | 'SALVAR_PRIORIDADES'
    | 'CALCULAR_PREVIA'
    | 'RECALCULAR_PREVIA_DESATUALIZADA'
    | 'APLICAR',
) {
  erro.value = causa instanceof Error ? causa.message : mensagemPadrao
  operacaoParaTentarNovamente.value = operacao
}

async function carregarMaterias() {
  carregando.value = true
  limparErro()
  try {
    materias.value = await listarMateriasParaGeracao(
      propriedades.identificadorDoPlano,
    )
    assinaturaDasPrioridadesSalvas.value = criarAssinaturaDasPrioridades()
  } catch (causa) {
    registrarErro(
      causa,
      'Não foi possível carregar as matérias elegíveis.',
      'CARREGAR_MATERIAS',
    )
  } finally {
    carregando.value = false
  }
}

async function salvarPrioridades() {
  processando.value = true
  limparErro()
  try {
    materias.value = await substituirPrioridadesDeMaterias(
      propriedades.identificadorDoPlano,
      materias.value.map((materia) => ({
        identificadorDaMateria: materia.identificadorDaMateria,
        prioridade: materia.prioridade,
      })),
    )
    assinaturaDasPrioridadesSalvas.value = criarAssinaturaDasPrioridades()
    etapa.value = 'CONFIGURACAO'
  } catch (causa) {
    registrarErro(
      causa,
      'Não foi possível salvar as prioridades.',
      'SALVAR_PRIORIDADES',
    )
  } finally {
    processando.value = false
  }
}

async function solicitarPrevia(mensagemAposCalculo = '') {
  const duracaoPrincipalSolicitada = Number(duracaoPrincipal.value)
  const quantidadeDeMateriasSolicitada = Number(
    quantidadeDeMateriasPorDia.value,
  )
  const assinaturaSolicitada = criarAssinaturaDaPrevia()
  previa.value = await gerarPreviaDeterministica(
    propriedades.identificadorDoPlano,
    propriedades.dataDeReferencia,
    duracaoPrincipalSolicitada,
    quantidadeDeMateriasSolicitada,
  )
  assinaturaLocalDaPrevia.value = assinaturaSolicitada
  avisoDePreviaRecalculada.value = mensagemAposCalculo
  etapa.value = 'PREVIA'
}

async function calcularPrevia() {
  if (prioridadesNaoSalvas.value) {
    erro.value = 'Salve as prioridades antes de calcular uma nova prévia.'
    operacaoParaTentarNovamente.value = undefined
    return
  }
  processando.value = true
  limparErro()
  avisoDePreviaRecalculada.value = ''
  try {
    await solicitarPrevia()
  } catch (causa) {
    registrarErro(
      causa,
      'Não foi possível calcular a prévia.',
      'CALCULAR_PREVIA',
    )
  } finally {
    processando.value = false
  }
}

function invalidarPrevia() {
  previa.value = undefined
  assinaturaLocalDaPrevia.value = ''
  avisoDePreviaRecalculada.value = ''
  etapa.value = 'CONFIGURACAO'
}

async function recalcularPreviaDesatualizada() {
  invalidarPrevia()
  processando.value = true
  limparErro()
  let recalculada = false
  try {
    materias.value = await listarMateriasParaGeracao(
      propriedades.identificadorDoPlano,
    )
    assinaturaDasPrioridadesSalvas.value = criarAssinaturaDasPrioridades()
    await solicitarPrevia(
      'A prévia mudou porque os dados do planejamento foram atualizados. Revise a nova proposta e confirme novamente para aplicar.',
    )
    recalculada = true
  } catch (causa) {
    registrarErro(
      causa,
      'Não foi possível recalcular a prévia atualizada.',
      'RECALCULAR_PREVIA_DESATUALIZADA',
    )
  } finally {
    processando.value = false
    await nextTick()
    if (recalculada) botaoDeAplicacao.value?.focus()
    else alertaDeErro.value?.focus()
  }
}

async function aplicar(substituirBlocosGerados: boolean) {
  if (!previa.value || previaDesatualizada.value) {
    erro.value = 'Recalcule a prévia antes de aplicar.'
    operacaoParaTentarNovamente.value = 'CALCULAR_PREVIA'
    return
  }
  processando.value = true
  limparErro()
  substituirNaUltimaAplicacao.value = substituirBlocosGerados
  try {
    const resultado = await aplicarGeracaoDeterministica(
      propriedades.identificadorDoPlano,
      propriedades.dataDeReferencia,
      Number(duracaoPrincipal.value),
      substituirBlocosGerados,
      previa.value.assinaturaDaPrevia,
      Number(quantidadeDeMateriasPorDia.value),
    )
    confirmacaoDeRegeneracaoAberta.value = false
    emitir('aplicado', resultado)
  } catch (causa) {
    if (
      causa instanceof ErroDaApi &&
      causa.status === 409 &&
      causa.codigo === 'GERACAO_DETERMINISTICA_JA_APLICADA'
    ) {
      confirmacaoDeRegeneracaoAberta.value = true
    } else if (
      causa instanceof ErroDaApi &&
      causa.status === 409 &&
      causa.codigo === 'PREVIA_DA_GERACAO_DESATUALIZADA'
    ) {
      confirmacaoDeRegeneracaoAberta.value = false
      await recalcularPreviaDesatualizada()
    } else {
      confirmacaoDeRegeneracaoAberta.value = false
      registrarErro(causa, 'Não foi possível aplicar a geração.', 'APLICAR')
    }
  } finally {
    processando.value = false
  }
}

async function tentarNovamente() {
  switch (operacaoParaTentarNovamente.value) {
    case 'CARREGAR_MATERIAS':
      await carregarMaterias()
      break
    case 'SALVAR_PRIORIDADES':
      await salvarPrioridades()
      break
    case 'CALCULAR_PREVIA':
      await calcularPrevia()
      break
    case 'RECALCULAR_PREVIA_DESATUALIZADA':
      await recalcularPreviaDesatualizada()
      break
    case 'APLICAR':
      await aplicar(substituirNaUltimaAplicacao.value)
      break
    default:
      await carregarMaterias()
  }
}

function solicitarAplicacao() {
  if (propriedades.quantidadeDeBlocosGerados > 0) {
    confirmacaoDeRegeneracaoAberta.value = true
    return
  }
  void aplicar(false)
}

function formatarData(data: string) {
  return new Intl.DateTimeFormat('pt-BR', {
    weekday: 'long',
    day: '2-digit',
    month: 'short',
  }).format(new Date(`${data}T12:00:00`))
}

onMounted(carregarMaterias)
</script>

<template>
  <GavetaLateral
    titulo="Gerar semana"
    etiqueta="Geração determinística"
    descricao="Ajuste a estratégia e confira o resultado antes de alterar o plano."
    :larga="true"
    @fechar="emitir('fechar')"
  >
    <nav class="etapas-da-geracao" aria-label="Etapas da geração">
      <button
        v-for="item in ['PRIORIDADES', 'CONFIGURACAO', 'PREVIA'] as const"
        :key="item"
        class="etapa-da-geracao"
        :class="{ ativa: etapa === item }"
        type="button"
        :disabled="
          (item === 'PREVIA' && !previa) ||
          (item === 'CONFIGURACAO' && prioridadesNaoSalvas)
        "
        @click="etapa = item"
      >
        {{
          item === 'PRIORIDADES'
            ? '1. Prioridades'
            : item === 'CONFIGURACAO'
              ? '2. Configuração'
              : '3. Prévia'
        }}
      </button>
    </nav>

    <div
      v-if="erro"
      ref="alertaDeErro"
      class="alert alert-danger"
      role="alert"
      tabindex="-1"
    >
      {{ erro }}
      <button
        v-if="operacaoParaTentarNovamente"
        class="btn btn-sm btn-outline-danger ms-2"
        type="button"
        @click="tentarNovamente"
      >
        Tentar novamente
      </button>
    </div>

    <div v-if="carregando" class="geracao-carregando" role="status">
      <span class="spinner-border spinner-border-sm" aria-hidden="true"></span>
      Carregando matérias do concurso…
    </div>

    <section
      v-else-if="etapa === 'PRIORIDADES' && materias.length"
      aria-labelledby="titulo-prioridades"
    >
      <h3 id="titulo-prioridades">Prioridades desta semana</h3>
      <p>Matérias ausentes de configuração permanecem com prioridade Normal.</p>
      <div class="lista-de-prioridades">
        <label
          v-for="materia in materias"
          :key="materia.identificadorDaMateria"
        >
          <span>{{ materia.nome }}</span>
          <select v-model="materia.prioridade" class="form-select">
            <option
              v-for="(rotulo, valor) in rotulos"
              :key="valor"
              :value="valor"
            >
              {{ rotulo }}
            </option>
          </select>
        </label>
      </div>
      <button
        class="btn btn-primary w-100"
        type="button"
        :disabled="processando"
        @click="salvarPrioridades"
      >
        {{ processando ? 'Salvando…' : 'Salvar prioridades e continuar' }}
      </button>
    </section>

    <section
      v-else-if="etapa === 'CONFIGURACAO' && materias.length"
      aria-labelledby="titulo-configuracao"
    >
      <h3 id="titulo-configuracao">Configuração dos blocos</h3>
      <p>
        O cálculo respeita a disponibilidade, os blocos já existentes e reserva
        automaticamente as revisões devidas em blocos de 20 minutos.
      </p>
      <div class="grade-da-configuracao">
        <label>
          <span>Duração principal</span>
          <input
            v-model.number="duracaoPrincipal"
            class="form-control"
            type="number"
            min="25"
            max="180"
            required
          />
          <small>Entre 25 e 180 minutos.</small>
        </label>
        <label>
          <span>Matérias por dia</span>
          <input
            v-model.number="quantidadeDeMateriasPorDia"
            class="form-control"
            type="number"
            min="1"
            max="20"
            step="1"
            required
          />
          <small>
            Entre 1 e 20. O cálculo poderá usar menos se faltar capacidade ou
            matéria elegível.
          </small>
        </label>
      </div>
      <div class="d-flex gap-2">
        <button
          class="btn btn-outline-primary"
          type="button"
          @click="etapa = 'PRIORIDADES'"
        >
          Voltar
        </button>
        <button
          class="btn btn-primary flex-grow-1"
          type="button"
          :disabled="processando || configuracaoInvalida"
          @click="calcularPrevia()"
        >
          {{ processando ? 'Calculando…' : 'Calcular prévia' }}
        </button>
      </div>
    </section>

    <section
      v-else-if="etapa === 'PREVIA' && previa"
      aria-labelledby="titulo-previa"
    >
      <div class="cabecalho-da-previa">
        <div>
          <h3 id="titulo-previa">Prévia da semana</h3>
          <p>Nada foi aplicado ao seu plano. Você pode ajustar e recalcular.</p>
        </div>
        <button
          class="btn btn-outline-primary"
          type="button"
          @click="etapa = 'PRIORIDADES'"
        >
          Ajustar
        </button>
      </div>
      <div
        v-if="previaDesatualizada"
        class="alert alert-warning"
        role="status"
        aria-live="polite"
      >
        <strong>Prévia desatualizada.</strong> Prioridades ou a configuração
        mudaram. Recalcule antes de aplicar.
      </div>
      <div
        v-if="avisoDePreviaRecalculada"
        class="alert alert-warning"
        role="status"
        aria-live="assertive"
      >
        <strong>Prévia recalculada.</strong>
        {{ avisoDePreviaRecalculada }}
      </div>
      <div
        v-for="aviso in previa.avisos"
        :key="aviso.codigo"
        class="alert alert-warning py-2"
      >
        {{ aviso.mensagem }}
      </div>
      <div class="dias-da-previa">
        <article
          v-for="dia in previa.dias"
          :key="dia.data"
          class="dia-da-previa"
        >
          <header>
            <strong>{{ formatarData(dia.data) }}</strong>
            <span
              >{{ dia.capacidade.minutosSugeridos }} sugeridos ·
              {{ dia.capacidade.minutosLivres }} livres</span
            >
          </header>
          <div class="barra-de-capacidade" aria-hidden="true">
            <span
              :style="{
                width: `${dia.capacidade.minutosDisponiveis ? Math.min(100, ((dia.capacidade.minutosPreservados + dia.capacidade.minutosSugeridos) / dia.capacidade.minutosDisponiveis) * 100) : 0}%`,
              }"
            ></span>
          </div>
          <div
            v-for="bloco in dia.blocosPreservados"
            :key="bloco.identificador"
            class="bloco-da-previa preservado"
          >
            <span
              >Preservado · {{ rotulosDosTipos[bloco.tipoDeAtividade] }}</span
            ><strong>{{ bloco.titulo }}</strong
            ><small class="duracao-do-bloco"
              >{{ bloco.duracaoEmMinutos }} min</small
            >
          </div>
          <div
            v-for="(bloco, indice) in dia.blocosSugeridos"
            :key="`${dia.data}-${indice}`"
            class="bloco-da-previa sugerido"
          >
            <span>{{ rotulosDosTipos[bloco.tipoDeAtividade] }}</span>
            <strong>{{ bloco.nomeDoTopico || bloco.titulo }}</strong
            ><small class="duracao-do-bloco"
              >{{ bloco.duracaoEmMinutos }} min</small
            >
            <div class="contexto-do-bloco">
              <span v-if="bloco.nomeDaMateria">{{ bloco.nomeDaMateria }}</span>
              <span v-if="bloco.grupoDaPriorizacao" class="badge text-bg-light">
                {{ rotulosDosGrupos[bloco.grupoDaPriorizacao] }}
              </span>
              <span v-if="bloco.faixaDaPriorizacao" class="badge text-bg-light">
                {{ rotulosDasFaixas[bloco.faixaDaPriorizacao] }}
              </span>
            </div>
            <ul class="justificativas-do-bloco">
              <li v-for="motivo in bloco.justificativas" :key="motivo.codigo">
                {{ motivo.mensagem }}
              </li>
            </ul>
          </div>
          <p
            v-for="aviso in dia.avisos"
            :key="aviso.codigo"
            class="aviso-do-dia"
          >
            <i class="bi bi-info-circle" aria-hidden="true"></i>
            {{ aviso.mensagem }}
          </p>
        </article>
      </div>
      <div class="acoes-da-aplicacao-da-geracao">
        <p class="mb-0">
          O servidor recalculará esta proposta antes de salvar os blocos.
        </p>
        <button
          ref="botaoDeAplicacao"
          class="btn btn-primary"
          type="button"
          :disabled="processando || previaDesatualizada"
          @click="solicitarAplicacao"
        >
          {{
            previaDesatualizada
              ? 'Recalcular prévia'
              : processando
                ? 'Aplicando…'
                : 'Aplicar à semana'
          }}
        </button>
      </div>
    </section>
  </GavetaLateral>

  <ModalDaAplicacao
    v-if="confirmacaoDeRegeneracaoAberta"
    titulo="Substituir geração anterior?"
    etiqueta="Confirmar regeneração"
    sobre-gaveta
    :descricao="
      propriedades.quantidadeDeBlocosGerados > 0
        ? `${propriedades.quantidadeDeBlocosGerados} bloco(s) gerado(s) a partir da data de referência serão substituídos.`
        : 'O servidor encontrou uma geração anterior. Os blocos puramente gerados a partir da data de referência serão substituídos.'
    "
    @fechar="confirmacaoDeRegeneracaoAberta = false"
  >
    <p class="mb-0">
      Blocos anteriores à data de referência, blocos manuais e blocos gerados
      que você já ajustou serão preservados.
    </p>
    <template #rodape>
      <button
        class="btn btn-outline-secondary"
        type="button"
        @click="confirmacaoDeRegeneracaoAberta = false"
      >
        Manter geração atual
      </button>
      <button
        class="btn btn-primary"
        type="button"
        :disabled="processando"
        @click="aplicar(true)"
      >
        {{ processando ? 'Regenerando…' : 'Substituir e aplicar' }}
      </button>
    </template>
  </ModalDaAplicacao>
</template>

<style scoped lang="scss">
.etapas-da-geracao {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 0.5rem;
  margin-bottom: 1.5rem;
}
.etapa-da-geracao {
  border: 0;
  border-bottom: 3px solid #dce5e2;
  background: transparent;
  padding: 0.75rem 0.4rem;
  color: #60716c;
  font-weight: 700;
}
.etapa-da-geracao.ativa {
  border-color: var(--bs-primary);
  color: var(--bs-primary);
}
.geracao-carregando {
  display: flex;
  justify-content: center;
  gap: 0.75rem;
  padding: 3rem;
}
.lista-de-prioridades {
  display: grid;
  gap: 0.75rem;
  margin: 1.25rem 0;
}
.lista-de-prioridades label {
  display: grid;
  grid-template-columns: 1fr minmax(140px, 190px);
  align-items: center;
  gap: 1rem;
  border: 1px solid #e0e6e3;
  border-radius: 0.75rem;
  padding: 0.85rem 1rem;
}
.grade-da-configuracao {
  display: grid;
  grid-template-columns: minmax(0, 28rem);
  gap: 1rem;
  margin: 1.5rem 0;
}
.grade-da-configuracao label {
  display: grid;
  gap: 0.4rem;
}
.cabecalho-da-previa,
.dia-da-previa header {
  display: flex;
  justify-content: space-between;
  gap: 1rem;
}
.dias-da-previa {
  display: grid;
  gap: 1rem;
  margin: 1rem 0;
}
.dia-da-previa {
  border: 1px solid #dfe7e4;
  border-radius: 1rem;
  padding: 1rem;
  background: #fff;
}
.dia-da-previa header span {
  color: #60716c;
  font-size: 0.875rem;
}
.barra-de-capacidade {
  height: 6px;
  background: #edf2f0;
  border-radius: 999px;
  margin: 0.75rem 0;
  overflow: hidden;
}
.barra-de-capacidade span {
  display: block;
  height: 100%;
  background: var(--bs-primary);
}
.bloco-da-previa {
  display: grid;
  grid-template-columns: 90px 1fr auto;
  gap: 0.5rem;
  padding: 0.65rem 0.75rem;
  border-radius: 0.65rem;
  margin-top: 0.5rem;
}
.bloco-da-previa.preservado {
  background: #f1f3f4;
}
.bloco-da-previa.sugerido {
  background: #eaf7f3;
}
.bloco-da-previa > span {
  color: #60716c;
  font-size: 0.75rem;
  text-transform: uppercase;
  font-weight: 700;
}
.contexto-do-bloco,
.justificativas-do-bloco {
  grid-column: 2 / -1;
}
.contexto-do-bloco {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.35rem;
  color: #60716c;
  font-size: 0.8rem;
}
.justificativas-do-bloco {
  margin: 0;
  padding-left: 1rem;
  color: #60716c;
  font-size: 0.8rem;
}
.aviso-do-dia {
  color: #725b16;
  font-size: 0.85rem;
  margin: 0.6rem 0 0;
}
.acoes-da-aplicacao-da-geracao {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  border: 1px solid #b9ddd3;
  border-radius: 0.85rem;
  background: #eef9f6;
  padding: 1rem;
}
@media (max-width: 576px) {
  .etapas-da-geracao {
    grid-template-columns: 1fr;
  }
  .lista-de-prioridades label,
  .grade-da-configuracao {
    grid-template-columns: 1fr;
  }
  .cabecalho-da-previa,
  .dia-da-previa header {
    flex-direction: column;
  }
  .bloco-da-previa {
    grid-template-columns: 1fr auto;
  }
  .bloco-da-previa > span {
    grid-column: 1 / -1;
  }
  .contexto-do-bloco,
  .justificativas-do-bloco {
    grid-column: 1 / -1;
  }
  .acoes-da-aplicacao-da-geracao {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
