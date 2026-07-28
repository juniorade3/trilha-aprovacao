<script setup lang="ts">
import {
  computed,
  nextTick,
  onBeforeUnmount,
  onMounted,
  reactive,
  ref,
} from 'vue'

import ModalDaAplicacao from '@/compartilhado/componentes/ModalDaAplicacao.vue'
import CamposDeEvidencia from './CamposDeEvidencia.vue'
import {
  listarTodasAsMaterias,
  listarTodosOsTopicos,
  type Materia,
  type Topico,
} from '@/modulos/materias/apiDeConteudos'
import {
  listarCoberturas,
  listarTodosOsMateriaisDeEstudo,
  registrarEstudo,
  paraEvidencia,
  type CoberturaDeTopico,
  type MaterialDeEstudo,
  type ModeloDeEvidencia,
  type TipoDeEstudo,
} from './apiDeEstudos'

const propriedades = defineProps<{
  identificadorDaMateriaInicial?: string
  identificadorDoTopicoInicial?: string
  tipoDeEstudoInicial?: TipoDeEstudo
}>()

const emitir = defineEmits<{
  fechar: []
  registrado: []
}>()

const materias = ref<Materia[]>([])
const topicos = ref<Topico[]>([])
const materiais = ref<MaterialDeEstudo[]>([])
const coberturas = ref<CoberturaDeTopico[]>([])
const carregando = ref(true)
const salvando = ref(false)
const erro = ref('')
const erroDeCarregamento = ref('')
const botaoDeRepetir = ref<HTMLButtonElement>()
const campoDoTipo = ref<HTMLSelectElement>()
const cancelamento = new AbortController()
const duracoesRapidas = [30, 50, 60, 90]
const tiposDeEstudo: Array<{ valor: TipoDeEstudo; rotulo: string }> = [
  { valor: 'TEORIA', rotulo: 'Teoria' },
  { valor: 'QUESTOES', rotulo: 'Questões' },
  { valor: 'REVISAO', rotulo: 'Revisão' },
  { valor: 'CADERNO_DE_ERROS', rotulo: 'Caderno de erros' },
  { valor: 'SIMULADO', rotulo: 'Simulado' },
  { valor: 'DISCURSIVA', rotulo: 'Discursiva' },
  { valor: 'OUTRA', rotulo: 'Outra' },
]

const formulario = reactive({
  identificadorDaMateria: propriedades.identificadorDaMateriaInicial ?? '',
  identificadorDoTopico: propriedades.identificadorDoTopicoInicial ?? '',
  identificadorDoMaterial: '',
  dataHora: dataHoraLocalAtual(),
  duracaoEmMinutos: 50,
  observacao: '',
  tipoDeEstudo: propriedades.tipoDeEstudoInicial ?? ('TEORIA' as TipoDeEstudo),
  evidencia: { padroesDeErro: [] } as ModeloDeEvidencia,
})

const topicosDaMateria = computed(() =>
  topicos.value.filter(
    (topico) =>
      topico.identificadorDaMateria === formulario.identificadorDaMateria,
  ),
)

const materiaisDoTopico = computed(() => {
  const identificadores = new Set(
    coberturas.value
      .filter(
        (cobertura) =>
          cobertura.identificadorDoTopico === formulario.identificadorDoTopico,
      )
      .map((cobertura) => cobertura.identificadorDoMaterial),
  )
  return materiais.value.filter((material) =>
    identificadores.has(material.identificador),
  )
})

function dataHoraLocalAtual() {
  const partes = new Intl.DateTimeFormat('en-US', {
    timeZone: 'America/Sao_Paulo',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23',
  }).formatToParts(new Date())
  const valor = (tipo: Intl.DateTimeFormatPartTypes) =>
    partes.find((parte) => parte.type === tipo)?.value ?? ''
  return `${valor('year')}-${valor('month')}-${valor('day')}T${valor('hour')}:${valor('minute')}`
}

function paraInstanteDeSaoPaulo(valor: string) {
  const correspondencia = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})$/.exec(
    valor,
  )
  if (!correspondencia) throw new Error('Informe uma data e horario validos.')
  const desejado = correspondencia.slice(1).map(Number)
  let instante = Date.UTC(
    desejado[0]!,
    desejado[1]! - 1,
    desejado[2]!,
    desejado[3]!,
    desejado[4]!,
  )
  const formatador = new Intl.DateTimeFormat('en-US', {
    timeZone: 'America/Sao_Paulo',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23',
  })
  for (let tentativa = 0; tentativa < 2; tentativa += 1) {
    const partes = formatador.formatToParts(new Date(instante))
    const numero = (tipo: Intl.DateTimeFormatPartTypes) =>
      Number(partes.find((parte) => parte.type === tipo)?.value)
    const exibidoComoUtc = Date.UTC(
      numero('year'),
      numero('month') - 1,
      numero('day'),
      numero('hour'),
      numero('minute'),
    )
    const desejadoComoUtc = Date.UTC(
      desejado[0]!,
      desejado[1]! - 1,
      desejado[2]!,
      desejado[3]!,
      desejado[4]!,
    )
    instante += desejadoComoUtc - exibidoComoUtc
  }
  return new Date(instante).toISOString()
}

async function carregar() {
  carregando.value = true
  erroDeCarregamento.value = ''
  materias.value = []
  topicos.value = []
  materiais.value = []
  coberturas.value = []
  try {
    const [materiasObtidas, materiaisObtidos] = await Promise.all([
      listarTodasAsMaterias('', false, cancelamento.signal),
      listarTodosOsMateriaisDeEstudo('', false, cancelamento.signal),
    ])
    materias.value = materiasObtidas
    materiais.value = materiaisObtidos
    const [respostasDeTopicos, respostasDeCoberturas] = await Promise.all([
      Promise.all(
        materias.value.map((materia) =>
          listarTodosOsTopicos(
            materia.identificador,
            false,
            cancelamento.signal,
          ),
        ),
      ),
      Promise.all(
        materiais.value.map((material) =>
          listarCoberturas(material.identificador, cancelamento.signal),
        ),
      ),
    ])
    topicos.value = respostasDeTopicos.flat()
    coberturas.value = respostasDeCoberturas.flat()
  } catch (causa) {
    if (causa instanceof DOMException && causa.name === 'AbortError') return
    erroDeCarregamento.value =
      causa instanceof Error
        ? causa.message
        : 'Não foi possível preparar o registro.'
  } finally {
    carregando.value = false
  }
}

async function repetirCarregamento() {
  await carregar()
  await nextTick()
  if (erroDeCarregamento.value) botaoDeRepetir.value?.focus()
  else campoDoTipo.value?.focus()
}

function ajustarTopico() {
  formulario.identificadorDoTopico = ''
  formulario.identificadorDoMaterial = ''
}

function ajustarMaterial() {
  formulario.identificadorDoMaterial = ''
}

async function salvar() {
  salvando.value = true
  erro.value = ''
  try {
    await registrarEstudo({
      identificadorDoTopico: formulario.identificadorDoTopico,
      identificadorDoMaterial: formulario.identificadorDoMaterial || undefined,
      dataHora: paraInstanteDeSaoPaulo(formulario.dataHora),
      duracaoEmMinutos: formulario.duracaoEmMinutos,
      observacao: formulario.observacao || undefined,
      tipoDeEstudo: formulario.tipoDeEstudo,
      evidencia: paraEvidencia(formulario.evidencia),
    })
    emitir('registrado')
  } catch (causa) {
    erro.value =
      causa instanceof Error
        ? causa.message
        : 'Não foi possível registrar o estudo.'
  } finally {
    salvando.value = false
  }
}

onMounted(() => carregar())
onBeforeUnmount(() => cancelamento.abort())
</script>

<template>
  <ModalDaAplicacao
    etiqueta="Registro rápido"
    titulo="Registrar estudo"
    descricao="Guarde o fato estudado. O progresso dos concursos será atualizado automaticamente."
    @fechar="emitir('fechar')"
  >
    <div class="registro-estudo-moderno">
      <div
        v-if="carregando"
        class="estado-do-modal registro-estudo-moderno__estado"
        aria-live="polite"
      >
        <span class="registro-estudo-moderno__icone-de-estado">
          <span
            class="spinner-border spinner-border-sm"
            aria-hidden="true"
          ></span>
        </span>
        <div>
          <strong>Preparando seu registro</strong>
          <span>Carregando matérias, tópicos e materiais...</span>
        </div>
      </div>

      <div
        v-else-if="erroDeCarregamento"
        class="estado-do-modal registro-estudo-moderno__estado registro-estudo-moderno__estado--erro"
        role="alert"
      >
        <span class="registro-estudo-moderno__icone-de-estado">
          <i class="bi bi-cloud-slash" aria-hidden="true"></i>
        </span>
        <div>
          <strong>Não foi possível preparar o registro</strong>
          <p>{{ erroDeCarregamento }}</p>
          <button
            ref="botaoDeRepetir"
            class="btn btn-outline-primary"
            type="button"
            @click="repetirCarregamento"
          >
            Tentar novamente
          </button>
        </div>
      </div>

      <form
        v-else
        id="registro-rapido-de-estudo"
        class="registro-estudo-moderno__formulario"
        @submit.prevent="salvar"
      >
        <p v-if="erro" class="alert alert-danger" role="alert">{{ erro }}</p>
        <div
          v-if="materias.length === 0"
          class="nota-contextual registro-estudo-moderno__aviso"
        >
          <i class="bi bi-journal-plus" aria-hidden="true"></i>
          <p>
            <strong>Cadastre uma matéria e um tópico primeiro.</strong>
            <span
              >O estudo precisa apontar para um tópico do seu catálogo.</span
            >
          </p>
        </div>

        <div class="formulario-da-aplicacao">
          <section
            class="registro-estudo-moderno__secao registro-estudo-moderno__secao--principal"
            aria-labelledby="titulo-contexto-do-estudo"
          >
            <header class="registro-estudo-moderno__cabecalho-de-secao">
              <span
                class="registro-estudo-moderno__icone-de-secao"
                aria-hidden="true"
              >
                <i class="bi bi-book"></i>
              </span>
              <div>
                <span class="registro-estudo-moderno__etapa"
                  >01 · Contexto</span
                >
                <h3 id="titulo-contexto-do-estudo">O que você estudou?</h3>
                <p>Localize a sessão no seu catálogo de conteúdos.</p>
              </div>
            </header>

            <div
              class="registro-estudo-moderno__grade registro-estudo-moderno__grade--duas-colunas"
            >
              <label>
                <span>Tipo de estudo</span>
                <select
                  ref="campoDoTipo"
                  v-model="formulario.tipoDeEstudo"
                  required
                >
                  <option
                    v-for="tipo in tiposDeEstudo"
                    :key="tipo.valor"
                    :value="tipo.valor"
                  >
                    {{ tipo.rotulo }}
                  </option>
                </select>
              </label>
              <label>
                <span>Matéria</span>
                <select
                  v-model="formulario.identificadorDaMateria"
                  required
                  @change="ajustarTopico"
                >
                  <option value="" disabled>Selecione a matéria</option>
                  <option
                    v-for="materia in materias"
                    :key="materia.identificador"
                    :value="materia.identificador"
                  >
                    {{ materia.nome }}
                  </option>
                </select>
              </label>
            </div>

            <label>
              <span>Tópico estudado</span>
              <select
                v-model="formulario.identificadorDoTopico"
                :disabled="!formulario.identificadorDaMateria"
                required
                @change="ajustarMaterial"
              >
                <option value="" disabled>Selecione o tópico</option>
                <option
                  v-for="topico in topicosDaMateria"
                  :key="topico.identificador"
                  :value="topico.identificador"
                >
                  {{ topico.nome }}
                </option>
              </select>
            </label>

            <label>
              <span>Material utilizado <em>opcional</em></span>
              <select
                v-model="formulario.identificadorDoMaterial"
                :disabled="!formulario.identificadorDoTopico"
              >
                <option value="">Sem material</option>
                <option
                  v-for="material in materiaisDoTopico"
                  :key="material.identificador"
                  :value="material.identificador"
                >
                  {{ material.titulo }}
                </option>
              </select>
            </label>
          </section>

          <section
            class="registro-estudo-moderno__secao"
            aria-labelledby="titulo-sessao-de-estudo"
          >
            <header class="registro-estudo-moderno__cabecalho-de-secao">
              <span
                class="registro-estudo-moderno__icone-de-secao"
                aria-hidden="true"
              >
                <i class="bi bi-clock"></i>
              </span>
              <div>
                <span class="registro-estudo-moderno__etapa">02 · Sessão</span>
                <h3 id="titulo-sessao-de-estudo">Quando e por quanto tempo?</h3>
                <p>Informe o momento e a duração real da atividade.</p>
              </div>
            </header>

            <div class="duas-colunas-do-formulario">
              <label>
                <span>Data e horário</span>
                <input
                  v-model="formulario.dataHora"
                  type="datetime-local"
                  required
                />
              </label>
              <label>
                <span>Duração em minutos</span>
                <input
                  v-model.number="formulario.duracaoEmMinutos"
                  type="number"
                  min="1"
                  max="1440"
                  required
                />
              </label>
            </div>

            <fieldset class="registro-estudo-moderno__duracoes">
              <legend>Atalhos de duração</legend>
              <div class="duracoes-rapidas">
                <button
                  v-for="duracao in duracoesRapidas"
                  :key="duracao"
                  class="botao-de-duracao"
                  :class="{
                    ativo: formulario.duracaoEmMinutos === duracao,
                  }"
                  :aria-pressed="formulario.duracaoEmMinutos === duracao"
                  type="button"
                  @click="formulario.duracaoEmMinutos = duracao"
                >
                  {{ duracao }} min
                </button>
              </div>
            </fieldset>
          </section>

          <CamposDeEvidencia
            v-model="formulario.evidencia"
            :tipo="formulario.tipoDeEstudo"
            :identificador-do-topico="formulario.identificadorDoTopico"
          />

          <section
            class="registro-estudo-moderno__secao registro-estudo-moderno__secao--observacao"
            aria-labelledby="titulo-anotacoes-do-estudo"
          >
            <header class="registro-estudo-moderno__cabecalho-de-secao">
              <span
                class="registro-estudo-moderno__icone-de-secao"
                aria-hidden="true"
              >
                <i class="bi bi-chat-square-text"></i>
              </span>
              <div>
                <span class="registro-estudo-moderno__etapa"
                  >04 · Anotações</span
                >
                <h3 id="titulo-anotacoes-do-estudo">
                  Deixe um lembrete para depois
                </h3>
                <p>Registre uma descoberta, dúvida ou próximo passo.</p>
              </div>
            </header>

            <label>
              <span>Observação <em>opcional</em></span>
              <textarea
                v-model="formulario.observacao"
                rows="3"
                placeholder="O que você estudou ou precisa revisar depois?"
              ></textarea>
            </label>
          </section>
        </div>
      </form>
    </div>

    <template #rodape>
      <button
        class="btn btn-link text-secondary text-decoration-none registro-estudo-moderno__cancelar"
        type="button"
        @click="emitir('fechar')"
      >
        Cancelar
      </button>
      <button
        class="btn btn-primary px-4 registro-estudo-moderno__salvar"
        type="submit"
        form="registro-rapido-de-estudo"
        :disabled="carregando || salvando || materias.length === 0"
      >
        <i
          :class="salvando ? 'bi bi-arrow-repeat' : 'bi bi-check2'"
          class="me-2"
          aria-hidden="true"
        ></i>
        {{ salvando ? 'Salvando...' : 'Salvar estudo' }}
      </button>
    </template>
  </ModalDaAplicacao>
</template>
