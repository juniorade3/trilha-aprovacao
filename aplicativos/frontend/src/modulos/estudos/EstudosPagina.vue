<script setup lang="ts">
import {
  computed,
  nextTick,
  onBeforeUnmount,
  onMounted,
  reactive,
  ref,
} from 'vue'

import BarraDeProgresso from '@/compartilhado/componentes/BarraDeProgresso.vue'
import CabecalhoDaPagina from '@/compartilhado/componentes/CabecalhoDaPagina.vue'
import EstadoDaPagina from '@/compartilhado/componentes/EstadoDaPagina.vue'
import ModalDaAplicacao from '@/compartilhado/componentes/ModalDaAplicacao.vue'
import CamposDeEvidencia from './CamposDeEvidencia.vue'
import {
  listarTodasAsMaterias,
  listarTodosOsTopicos,
  type Materia,
  type Topico,
} from '@/modulos/materias/apiDeConteudos'
import {
  cancelarEstudo,
  consultarDiagnosticoDeTopicos,
  corrigirEstudo,
  listarCoberturas,
  listarTodosOsEstudos,
  listarTodosOsMateriaisDeEstudo,
  registrarEstudo,
  paraEvidencia,
  type CoberturaDeTopico,
  type MaterialDeEstudo,
  type DiagnosticoDeTopico,
  type ModeloDeEvidencia,
  type RegistroDeEstudo,
  type TipoDeEstudo,
} from './apiDeEstudos'

const propriedades = withDefaults(
  defineProps<{
    abrirRegistroRapidoAoEntrar?: boolean
  }>(),
  {
    abrirRegistroRapidoAoEntrar: false,
  },
)

type PeriodoDoHistorico = 'TODOS' | 'SETE_DIAS' | 'TRINTA_DIAS'

const registros = ref<RegistroDeEstudo[]>([])
const materias = ref<Materia[]>([])
const topicos = ref<Topico[]>([])
const materiais = ref<MaterialDeEstudo[]>([])
const coberturas = ref<CoberturaDeTopico[]>([])
const diagnosticos = ref<DiagnosticoDeTopico[]>([])
const carregandoDiagnostico = ref(true)
const erroDoDiagnostico = ref('')
const materiaDoDiagnostico = ref('')
const somenteExigidos = ref(false)
const carregando = ref(true)
const salvando = ref(false)
const erro = ref('')
const identificadorEmCorrecao = ref<string>()
const formularioAberto = ref(false)
const periodoSelecionado = ref<PeriodoDoHistorico>('TODOS')
const formulario = reactive({
  identificadorDaMateria: '',
  identificadorDoTopico: '',
  identificadorDoMaterial: '',
  dataHora: dataHoraLocalAtual(),
  duracaoEmMinutos: 60,
  observacao: '',
  tipoDeEstudo: 'TEORIA' as TipoDeEstudo,
  evidencia: { padroesDeErro: [] } as ModeloDeEvidencia,
})
const tiposDeEstudo: Array<{ valor: TipoDeEstudo; rotulo: string }> = [
  { valor: 'TEORIA', rotulo: 'Teoria' },
  { valor: 'QUESTOES', rotulo: 'Questões' },
  { valor: 'REVISAO', rotulo: 'Revisão' },
  { valor: 'CADERNO_DE_ERROS', rotulo: 'Caderno de erros' },
  { valor: 'SIMULADO', rotulo: 'Simulado' },
  { valor: 'DISCURSIVA', rotulo: 'Discursiva' },
  { valor: 'OUTRA', rotulo: 'Outra' },
]
let cancelamento: AbortController | undefined

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

const registrosAtivos = computed(() =>
  registros.value.filter((registro) => registro.situacao === 'ATIVO'),
)

const registrosFiltrados = computed(() => {
  if (periodoSelecionado.value === 'TODOS') return registros.value

  const quantidadeDeDias = periodoSelecionado.value === 'SETE_DIAS' ? 7 : 30
  const inicioDoPeriodo = new Date()
  inicioDoPeriodo.setHours(0, 0, 0, 0)
  inicioDoPeriodo.setDate(inicioDoPeriodo.getDate() - quantidadeDeDias + 1)

  return registros.value.filter(
    (registro) => new Date(registro.dataHora) >= inicioDoPeriodo,
  )
})

const registrosAgrupados = computed(() => {
  const grupos = new Map<string, RegistroDeEstudo[]>()
  for (const registro of registrosFiltrados.value) {
    const chave = new Intl.DateTimeFormat('pt-BR', {
      dateStyle: 'full',
    }).format(new Date(registro.dataHora))
    grupos.set(chave, [...(grupos.get(chave) ?? []), registro])
  }
  return [...grupos.entries()]
})

const inicioDaSemana = computed(() => {
  const agora = new Date()
  const dia = agora.getDay() || 7
  const inicio = new Date(agora)
  inicio.setHours(0, 0, 0, 0)
  inicio.setDate(agora.getDate() - dia + 1)
  return inicio
})

const registrosDaSemana = computed(() =>
  registrosAtivos.value.filter(
    (registro) => new Date(registro.dataHora) >= inicioDaSemana.value,
  ),
)

const minutosDaSemana = computed(() =>
  registrosDaSemana.value.reduce(
    (total, registro) => total + registro.duracaoEmMinutos,
    0,
  ),
)

const diasComEstudo = computed(
  () =>
    new Set(
      registrosDaSemana.value.map((registro) =>
        new Date(registro.dataHora).toDateString(),
      ),
    ).size,
)

const materiasDaSemana = computed(
  () =>
    new Set(
      registrosDaSemana.value
        .map((registro) =>
          topicos.value.find(
            (topico) => topico.identificador === registro.identificadorDoTopico,
          ),
        )
        .map((topico) => topico?.identificadorDaMateria)
        .filter(Boolean),
    ).size,
)

const ritmoDaSemana = computed(() =>
  Array.from({ length: 7 }, (_, indice) => {
    const data = new Date(inicioDaSemana.value)
    data.setDate(data.getDate() + indice)
    const minutos = registrosDaSemana.value
      .filter(
        (registro) =>
          new Date(registro.dataHora).toDateString() === data.toDateString(),
      )
      .reduce((total, registro) => total + registro.duracaoEmMinutos, 0)
    return {
      dia: ['S', 'T', 'Q', 'Q', 'S', 'S', 'D'][indice],
      minutos,
    }
  }),
)

const maiorRitmo = computed(() =>
  Math.max(...ritmoDaSemana.value.map((dia) => dia.minutos), 1),
)

function dataHoraLocalAtual() {
  return dataHoraParaCampoLocal(new Date().toISOString())
}

function dataHoraParaCampoLocal(dataHora: string) {
  const data = new Date(dataHora)
  data.setMinutes(data.getMinutes() - data.getTimezoneOffset())
  return data.toISOString().slice(0, 16)
}

async function carregar() {
  cancelamento?.abort()
  const requisicao = new AbortController()
  cancelamento = requisicao
  carregando.value = true
  erro.value = ''
  try {
    const [estudosObtidos, materiasObtidas, materiaisObtidos] =
      await Promise.all([
        listarTodosOsEstudos(requisicao.signal),
        listarTodasAsMaterias('', false, requisicao.signal),
        listarTodosOsMateriaisDeEstudo('', false, requisicao.signal),
      ])
    registros.value = estudosObtidos
    materias.value = materiasObtidas
    materiais.value = materiaisObtidos
    const respostasDeTopicos = await Promise.all(
      materias.value.map((materia) =>
        listarTodosOsTopicos(materia.identificador, false, requisicao.signal),
      ),
    )
    topicos.value = respostasDeTopicos.flat()
    const respostasDeCoberturas = await Promise.all(
      materiais.value.map((material) =>
        listarCoberturas(material.identificador, requisicao.signal),
      ),
    )
    coberturas.value = respostasDeCoberturas.flat()
    await carregarDiagnostico(requisicao.signal)
  } catch (causa) {
    if (causa instanceof DOMException && causa.name === 'AbortError') return
    erro.value =
      causa instanceof Error
        ? causa.message
        : 'Não foi possível carregar os estudos.'
  } finally {
    if (cancelamento === requisicao) carregando.value = false
  }
}

function dataLocalAtual() {
  const agora = new Date()
  agora.setMinutes(agora.getMinutes() - agora.getTimezoneOffset())
  return agora.toISOString().slice(0, 10)
}

async function carregarDiagnostico(sinal?: AbortSignal) {
  carregandoDiagnostico.value = true
  erroDoDiagnostico.value = ''
  try {
    diagnosticos.value = await consultarDiagnosticoDeTopicos(
      dataLocalAtual(),
      materiaDoDiagnostico.value || undefined,
      somenteExigidos.value,
      sinal,
    )
  } catch (causa) {
    if (causa instanceof DOMException && causa.name === 'AbortError') return
    erroDoDiagnostico.value =
      causa instanceof Error
        ? causa.message
        : 'Não foi possível carregar o diagnóstico.'
  } finally {
    carregandoDiagnostico.value = false
  }
}

function limparFormulario() {
  identificadorEmCorrecao.value = undefined
  Object.assign(formulario, {
    identificadorDaMateria: '',
    identificadorDoTopico: '',
    identificadorDoMaterial: '',
    dataHora: dataHoraLocalAtual(),
    duracaoEmMinutos: 60,
    observacao: '',
    tipoDeEstudo: 'TEORIA',
    evidencia: { padroesDeErro: [] },
  })
}

function ajustarTopico() {
  formulario.identificadorDoTopico = ''
  formulario.identificadorDoMaterial = ''
}

function ajustarMaterial() {
  formulario.identificadorDoMaterial = ''
}

function dadosDoFormulario() {
  return {
    identificadorDoTopico: formulario.identificadorDoTopico,
    identificadorDoMaterial: formulario.identificadorDoMaterial || undefined,
    dataHora: new Date(formulario.dataHora).toISOString(),
    duracaoEmMinutos: formulario.duracaoEmMinutos,
    observacao: formulario.observacao || undefined,
    tipoDeEstudo: formulario.tipoDeEstudo,
    evidencia: paraEvidencia(formulario.evidencia),
  }
}

async function salvar() {
  salvando.value = true
  erro.value = ''
  try {
    if (identificadorEmCorrecao.value) {
      await corrigirEstudo(identificadorEmCorrecao.value, dadosDoFormulario())
    } else {
      await registrarEstudo(dadosDoFormulario())
    }
    limparFormulario()
    formularioAberto.value = false
    await carregar()
  } catch (causa) {
    erro.value =
      causa instanceof Error ? causa.message : 'Não foi possível salvar.'
  } finally {
    salvando.value = false
  }
}

function corrigir(registro: RegistroDeEstudo) {
  const topico = topicos.value.find(
    (item) => item.identificador === registro.identificadorDoTopico,
  )
  identificadorEmCorrecao.value = registro.identificador
  Object.assign(formulario, {
    identificadorDaMateria: topico?.identificadorDaMateria ?? '',
    identificadorDoTopico: registro.identificadorDoTopico,
    identificadorDoMaterial: registro.identificadorDoMaterial ?? '',
    dataHora: dataHoraParaCampoLocal(registro.dataHora),
    duracaoEmMinutos: registro.duracaoEmMinutos,
    observacao: registro.observacao ?? '',
    tipoDeEstudo: registro.tipoDeEstudo ?? 'OUTRA',
    evidencia: {
      quantidadeDeQuestoes:
        registro.evidencia?.resultadoDeQuestoes?.quantidadeDeQuestoes,
      quantidadeDeAcertos:
        registro.evidencia?.resultadoDeQuestoes?.quantidadeDeAcertos,
      nivelDeRecordacao: registro.evidencia?.nivelDeRecordacao,
      dificuldadePercebida: registro.evidencia?.dificuldadePercebida,
      padroesDeErro:
        registro.evidencia?.padroesDeErro?.map((padrao) => ({ ...padrao })) ??
        [],
    },
  })
  formularioAberto.value = true
}

async function cancelar(registro: RegistroDeEstudo) {
  if (!window.confirm('Cancelar este registro de estudo?')) return
  erro.value = ''
  try {
    await cancelarEstudo(registro.identificador)
    await carregar()
  } catch (causa) {
    erro.value =
      causa instanceof Error ? causa.message : 'Não foi possível cancelar.'
  }
}

function formatarHora(dataHora: string) {
  return new Intl.DateTimeFormat('pt-BR', {
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(dataHora))
}

function formatarTempo(minutos: number) {
  const horas = Math.floor(minutos / 60)
  const restante = minutos % 60
  if (!horas) return `${restante}min`
  return `${horas}h${restante ? ` ${restante}min` : ''}`
}

function nomeDaMateria(registro: RegistroDeEstudo) {
  const topico = topicos.value.find(
    (item) => item.identificador === registro.identificadorDoTopico,
  )
  return (
    materias.value.find(
      (materia) => materia.identificador === topico?.identificadorDaMateria,
    )?.nome ?? 'Matéria não encontrada'
  )
}

function rotuloDaSituacao(registro: RegistroDeEstudo) {
  const rotulos: Record<RegistroDeEstudo['situacao'], string> = {
    ATIVO: 'Ativo',
    CORRIGIDO: 'Corrigido',
    CANCELADO: 'Cancelado',
  }
  return rotulos[registro.situacao]
}

function rotuloDaRevisao(
  resultado?: DiagnosticoDeTopico['resultadoDaUltimaRevisao'],
) {
  if (!resultado) return 'Sem revisão avaliada'
  return {
    PRECISA_REFORCO: 'Precisa reforço',
    PARCIAL: 'Parcial',
    CONSOLIDADA: 'Consolidada',
  }[resultado]
}

function abrirRegistroRapido() {
  window.dispatchEvent(new CustomEvent('abrir-registro-rapido'))
}

onMounted(async () => {
  void carregar()
  window.addEventListener('estudo-registrado', carregar)
  if (propriedades.abrirRegistroRapidoAoEntrar) {
    await nextTick()
    abrirRegistroRapido()
  }
})
onBeforeUnmount(() => {
  cancelamento?.abort()
  window.removeEventListener('estudo-registrado', carregar)
})
</script>

<template>
  <main class="pagina-da-jornada pagina-do-historico">
    <CabecalhoDaPagina
      etiqueta="Evidências da sua caminhada"
      titulo="Histórico de estudos"
      descricao="Consulte o que foi estudado, corrija registros e acompanhe sua constância sem perder a rastreabilidade."
    >
      <template #acoes>
        <button
          class="btn btn-primary"
          type="button"
          @click="abrirRegistroRapido"
        >
          <i class="bi bi-pencil-square me-2" aria-hidden="true"></i>
          Registrar estudo
        </button>
      </template>
    </CabecalhoDaPagina>

    <p v-if="erro" class="alert alert-danger" role="alert">{{ erro }}</p>

    <section class="resumo-do-historico" aria-label="Resumo da semana">
      <article class="card">
        <span class="mini-icone-da-jornada">
          <i class="bi bi-clock" aria-hidden="true"></i>
        </span>
        <div>
          <strong>{{ formatarTempo(minutosDaSemana) }}</strong>
          <small>nesta semana</small>
        </div>
      </article>
      <article class="card">
        <span class="mini-icone-da-jornada">
          <i class="bi bi-calendar-check" aria-hidden="true"></i>
        </span>
        <div>
          <strong>
            {{ diasComEstudo }} {{ diasComEstudo === 1 ? 'dia' : 'dias' }}
          </strong>
          <small>com estudo na semana</small>
        </div>
      </article>
      <article class="card">
        <span class="mini-icone-da-jornada">
          <i class="bi bi-book" aria-hidden="true"></i>
        </span>
        <div>
          <strong>
            {{ materiasDaSemana }}
            {{ materiasDaSemana === 1 ? 'matéria' : 'matérias' }}
          </strong>
          <small>estudadas na semana</small>
        </div>
      </article>
    </section>

    <section class="card mb-4" aria-labelledby="titulo-diagnostico-de-topicos">
      <header class="cabecalho-do-cartao-da-jornada">
        <div>
          <span class="rotulo-discreto">Evidências objetivas</span>
          <h2 id="titulo-diagnostico-de-topicos">Diagnóstico por tópico</h2>
          <p class="mb-0">
            A janela recente inclui hoje e os 29 dias anteriores. Registros
            corrigidos ou cancelados não entram nos indicadores.
          </p>
        </div>
      </header>
      <form
        class="d-flex flex-wrap align-items-end gap-3 mb-3"
        @submit.prevent="carregarDiagnostico()"
      >
        <label class="form-label mb-0">
          <span>Matéria</span>
          <select v-model="materiaDoDiagnostico" class="form-select">
            <option value="">Todas as matérias</option>
            <option
              v-for="materia in materias"
              :key="materia.identificador"
              :value="materia.identificador"
            >
              {{ materia.nome }}
            </option>
          </select>
        </label>
        <label class="form-check mb-2">
          <input
            v-model="somenteExigidos"
            class="form-check-input"
            type="checkbox"
          />
          <span class="form-check-label">Somente edital confirmado</span>
        </label>
        <button
          class="btn btn-outline-primary"
          :disabled="carregandoDiagnostico"
        >
          Aplicar filtros
        </button>
      </form>
      <p v-if="erroDoDiagnostico" class="alert alert-danger" role="alert">
        {{ erroDoDiagnostico }}
        <button
          class="btn btn-sm btn-outline-danger ms-2"
          type="button"
          @click="carregarDiagnostico()"
        >
          Tentar novamente
        </button>
      </p>
      <EstadoDaPagina
        v-else-if="carregandoDiagnostico"
        titulo="Calculando diagnóstico..."
        carregando
      />
      <EstadoDaPagina
        v-else-if="diagnosticos.length === 0"
        titulo="Nenhum tópico neste filtro"
        descricao="Cadastre tópicos ativos ou remova o filtro de edital confirmado."
        icone="bi-clipboard-data"
      />
      <div v-else class="table-responsive">
        <table class="table align-middle">
          <caption class="visually-hidden">
            Indicadores objetivos por tópico
          </caption>
          <thead>
            <tr>
              <th scope="col">Tópico</th>
              <th scope="col">Últimos 30 dias</th>
              <th scope="col">Recordação / dificuldade</th>
              <th scope="col">Última revisão</th>
              <th scope="col">Erros repetidos</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in diagnosticos" :key="item.identificadorDoTopico">
              <th scope="row">
                <span class="d-block">{{ item.nomeDoTopico }}</span>
                <small>{{ item.nomeDaMateria }}</small>
                <span
                  v-if="item.exigidoNoConcursoAtivo"
                  class="badge text-bg-light ms-2"
                  >Edital</span
                >
              </th>
              <td>
                <strong v-if="item.percentualRecenteDeAcertos != null"
                  >{{ item.percentualRecenteDeAcertos }}% de acertos</strong
                >
                <span v-else>Sem questões</span>
                <small class="d-block">
                  {{ item.totaisDosUltimosTrintaDias.questoes }} questões ·
                  {{ item.totaisDosUltimosTrintaDias.erros }} erros
                </small>
              </td>
              <td>
                <span
                  >{{ item.ultimaRecordacao ?? '—' }} /
                  {{ item.ultimaDificuldade ?? '—' }}</span
                >
                <small class="d-block"
                  >médias recentes: {{ item.mediaRecenteDeRecordacao ?? '—' }} /
                  {{ item.mediaRecenteDeDificuldade ?? '—' }}</small
                >
              </td>
              <td>{{ rotuloDaRevisao(item.resultadoDaUltimaRevisao) }}</td>
              <td>
                <span v-if="!item.padroesDeErroRepetidos.length">Nenhum</span>
                <ul v-else class="mb-0 ps-3">
                  <li
                    v-for="padrao in item.padroesDeErroRepetidos"
                    :key="padrao.identificador"
                  >
                    {{ padrao.descricao }} ({{ padrao.quantidadeDeEvidencias }}
                    sessões)
                  </li>
                </ul>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <div class="estrutura-do-historico">
      <section
        class="card linha-do-tempo-de-estudos"
        aria-labelledby="titulo-historico"
      >
        <header class="cabecalho-do-cartao-da-jornada">
          <div>
            <span class="rotulo-discreto">Registros preservados</span>
            <h2 id="titulo-historico">Atividade recente</h2>
          </div>
          <span class="badge text-bg-light">
            {{ registrosFiltrados.length }}
            {{ registrosFiltrados.length === 1 ? 'registro' : 'registros' }}
          </span>
        </header>

        <div
          class="btn-group align-self-start mb-3"
          role="group"
          aria-label="Filtrar histórico por período"
        >
          <button
            class="btn btn-sm btn-outline-secondary"
            :class="{ active: periodoSelecionado === 'TODOS' }"
            type="button"
            :aria-pressed="periodoSelecionado === 'TODOS'"
            @click="periodoSelecionado = 'TODOS'"
          >
            Todos
          </button>
          <button
            class="btn btn-sm btn-outline-secondary"
            :class="{ active: periodoSelecionado === 'SETE_DIAS' }"
            type="button"
            :aria-pressed="periodoSelecionado === 'SETE_DIAS'"
            @click="periodoSelecionado = 'SETE_DIAS'"
          >
            7 dias
          </button>
          <button
            class="btn btn-sm btn-outline-secondary"
            :class="{ active: periodoSelecionado === 'TRINTA_DIAS' }"
            type="button"
            :aria-pressed="periodoSelecionado === 'TRINTA_DIAS'"
            @click="periodoSelecionado = 'TRINTA_DIAS'"
          >
            30 dias
          </button>
        </div>

        <EstadoDaPagina
          v-if="carregando"
          titulo="Carregando estudos..."
          carregando
        />
        <EstadoDaPagina
          v-else-if="registrosFiltrados.length === 0"
          titulo="Nenhum estudo neste período"
          descricao="Escolha outro período ou use a ação Registrar estudo para adicionar uma atividade."
          icone="bi-clock-history"
        />
        <template v-else>
          <section
            v-for="[data, registrosDoDia] in registrosAgrupados"
            :key="data"
            class="grupo-da-linha-do-tempo"
          >
            <h3>{{ data }}</h3>
            <article
              v-for="registro in registrosDoDia"
              :key="registro.identificador"
              class="registro-na-linha-do-tempo"
            >
              <time>{{ formatarHora(registro.dataHora) }}</time>
              <span class="marcador-do-registro"></span>
              <div>
                <span class="etiqueta-da-materia-no-historico">
                  {{ nomeDaMateria(registro) }}
                </span>
                <h4>{{ registro.nomeDoTopico }}</h4>
                <small>
                  {{ registro.tituloDoMaterial || 'Sem material' }}
                  <span v-if="registro.observacao">
                    · {{ registro.observacao }}
                  </span>
                </small>
              </div>
              <b>{{ formatarTempo(registro.duracaoEmMinutos) }}</b>
              <span
                v-if="registro.situacao !== 'ATIVO'"
                class="badge text-bg-secondary"
              >
                {{ rotuloDaSituacao(registro) }}
              </span>
              <details v-else class="acoes-do-registro">
                <summary class="botao-de-icone" aria-label="Ações do registro">
                  <i class="bi bi-three-dots" aria-hidden="true"></i>
                </summary>
                <div>
                  <button type="button" @click="corrigir(registro)">
                    Corrigir
                  </button>
                  <button
                    class="text-danger"
                    type="button"
                    @click="cancelar(registro)"
                  >
                    Cancelar
                  </button>
                </div>
              </details>
            </article>
          </section>
        </template>
      </section>

      <aside class="card ritmo-real-da-semana">
        <span class="rotulo-discreto">Ritmo da semana</span>
        <h2 class="titulo-editorial">Consistência</h2>
        <div class="grafico-do-ritmo" aria-label="Minutos por dia da semana">
          <div v-for="(dia, indice) in ritmoDaSemana" :key="indice">
            <span>
              <i
                :style="{
                  height: `${Math.round((dia.minutos / maiorRitmo) * 100)}%`,
                }"
              ></i>
            </span>
            <b>{{ dia.dia }}</b>
            <small>{{ dia.minutos || '' }}</small>
          </div>
        </div>
        <div class="resumo-do-ritmo">
          <span>
            <b>{{ formatarTempo(minutosDaSemana) }}</b>
            <small>tempo registrado</small>
          </span>
          <strong>{{ diasComEstudo }}/7</strong>
        </div>
        <BarraDeProgresso
          :valor="Math.round((diasComEstudo / 7) * 100)"
          rotulo="Dias com estudo nesta semana"
        />
        <p>
          O gráfico usa somente registros ativos da semana atual. Correções e
          cancelamentos permanecem visíveis na linha do tempo.
        </p>
      </aside>
    </div>

    <ModalDaAplicacao
      v-if="formularioAberto"
      etiqueta="Correção rastreável"
      titulo="Corrigir estudo"
      descricao="O registro original será preservado no histórico."
      @fechar="formularioAberto = false"
    >
      <p v-if="erro" class="alert alert-danger" role="alert">{{ erro }}</p>
      <form
        id="formulario-estudo"
        class="formulario-da-aplicacao"
        @submit.prevent="salvar"
      >
        <label>
          <span>Tipo de estudo</span>
          <select v-model="formulario.tipoDeEstudo" required>
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
            id="materia-estudo"
            v-model="formulario.identificadorDaMateria"
            required
            @change="ajustarTopico"
          >
            <option value="">Selecione</option>
            <option
              v-for="materia in materias"
              :key="materia.identificador"
              :value="materia.identificador"
            >
              {{ materia.nome }}
            </option>
          </select>
        </label>
        <label>
          <span>Tópico</span>
          <select
            id="topico-estudo"
            v-model="formulario.identificadorDoTopico"
            required
            @change="ajustarMaterial"
          >
            <option value="">Selecione</option>
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
          <span>Material <em>opcional</em></span>
          <select
            id="material-estudo"
            v-model="formulario.identificadorDoMaterial"
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
        <div class="duas-colunas-do-formulario">
          <label>
            <span>Data e hora</span>
            <input
              id="data-estudo"
              v-model="formulario.dataHora"
              type="datetime-local"
              required
            />
          </label>
          <label>
            <span>Duração em minutos</span>
            <input
              id="duracao-estudo"
              v-model.number="formulario.duracaoEmMinutos"
              type="number"
              min="1"
              max="1440"
              required
            />
          </label>
        </div>
        <CamposDeEvidencia
          v-model="formulario.evidencia"
          :tipo="formulario.tipoDeEstudo"
          :identificador-do-topico="formulario.identificadorDoTopico"
        />
        <label>
          <span>Observação <em>opcional</em></span>
          <textarea
            id="observacao-estudo"
            v-model="formulario.observacao"
            rows="3"
          ></textarea>
        </label>
        <button class="btn btn-primary" :disabled="salvando">
          {{ salvando ? 'Salvando...' : 'Salvar correção' }}
        </button>
      </form>
    </ModalDaAplicacao>
  </main>
</template>
