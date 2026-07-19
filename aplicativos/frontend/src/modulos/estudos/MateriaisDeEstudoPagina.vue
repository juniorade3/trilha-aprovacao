<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'

import CabecalhoDaPagina from '@/compartilhado/componentes/CabecalhoDaPagina.vue'
import EstadoDaPagina from '@/compartilhado/componentes/EstadoDaPagina.vue'
import GavetaLateral from '@/compartilhado/componentes/GavetaLateral.vue'
import ModalDaAplicacao from '@/compartilhado/componentes/ModalDaAplicacao.vue'
import {
  listarTodasAsMaterias,
  listarTodosOsTopicos,
  type Materia,
  type Topico,
} from '@/modulos/materias/apiDeConteudos'
import {
  adicionarCobertura,
  alterarMaterialDeEstudo,
  criarMaterialDeEstudo,
  definirArquivamentoDoMaterial,
  excluirMaterialDeEstudo,
  listarCoberturas,
  listarTodosOsMateriaisDeEstudo,
  removerCobertura,
  type CoberturaDeTopico,
  type MaterialDeEstudo,
  type TipoDeMaterial,
} from './apiDeEstudos'

type FiltroDeTipo = 'TODOS' | TipoDeMaterial
type OrdenacaoDosMateriais = 'RECENTES' | 'TITULO'

const rota = useRoute()

const materiais = ref<MaterialDeEstudo[]>([])
const materias = ref<Materia[]>([])
const topicos = ref<Topico[]>([])
const coberturas = ref<CoberturaDeTopico[]>([])
const pesquisa = ref('')
const incluirArquivados = ref(
  typeof rota.params.identificador === 'string' &&
    rota.params.identificador.length > 0,
)
const filtroDeTipo = ref<FiltroDeTipo>('TODOS')
const ordenacaoDosMateriais = ref<OrdenacaoDosMateriais>('RECENTES')
const carregando = ref(true)
const salvando = ref(false)
const erro = ref('')
const identificadorEmEdicao = ref<string>()
const materialSelecionado = ref<MaterialDeEstudo>()
const formularioAberto = ref(false)
const coberturaAberta = ref(false)
const quantidadesDeCoberturas = reactive<Record<string, number>>({})
const identificadorDaMateria = ref('')
const identificadorDoTopico = ref('')
const formulario = reactive({
  titulo: '',
  tipo: 'AULA' as TipoDeMaterial,
  descricao: '',
  fonte: '',
  endereco: '',
  duracaoEstimadaEmMinutos: undefined as number | undefined,
})
let cancelamento: AbortController | undefined
let versaoDoCarregamentoDeCoberturas = 0
let versaoDoCarregamentoDeTopicos = 0

const identificadorDoMaterialNaRota = computed(() => {
  const identificador = rota.params.identificador
  return typeof identificador === 'string' ? identificador : undefined
})

const materiaisExibidos = computed(() => {
  const filtrados =
    filtroDeTipo.value === 'TODOS'
      ? materiais.value
      : materiais.value.filter(
          (material) => material.tipo === filtroDeTipo.value,
        )

  return [...filtrados].sort((primeiro, segundo) => {
    if (ordenacaoDosMateriais.value === 'TITULO') {
      return primeiro.titulo.localeCompare(segundo.titulo, 'pt-BR', {
        sensitivity: 'base',
      })
    }

    const diferencaEntreDatas =
      new Date(segundo.atualizadoEm).getTime() -
      new Date(primeiro.atualizadoEm).getTime()
    if (diferencaEntreDatas !== 0) return diferencaEntreDatas
    return primeiro.titulo.localeCompare(segundo.titulo, 'pt-BR', {
      sensitivity: 'base',
    })
  })
})

const topicosDisponiveis = computed(() => {
  const vinculados = new Set(
    coberturas.value.map((item) => item.identificadorDoTopico),
  )
  return topicos.value.filter((topico) => !vinculados.has(topico.identificador))
})

async function carregar() {
  cancelamento?.abort()
  const requisicao = new AbortController()
  cancelamento = requisicao
  carregando.value = true
  erro.value = ''
  try {
    const [materiaisObtidos, materiasObtidas] = await Promise.all([
      listarTodosOsMateriaisDeEstudo(
        pesquisa.value,
        incluirArquivados.value,
        requisicao.signal,
      ),
      listarTodasAsMaterias('', false, requisicao.signal),
    ])
    materiais.value = materiaisObtidos
    materias.value = materiasObtidas
    const coberturasDosMateriais = await Promise.all(
      materiais.value.map(async (material) => ({
        identificador: material.identificador,
        coberturas: await listarCoberturas(
          material.identificador,
          requisicao.signal,
        ),
      })),
    )
    for (const chave of Object.keys(quantidadesDeCoberturas))
      delete quantidadesDeCoberturas[chave]
    for (const item of coberturasDosMateriais)
      quantidadesDeCoberturas[item.identificador] = item.coberturas.length
    const identificadorSelecionado =
      identificadorDoMaterialNaRota.value ??
      materialSelecionado.value?.identificador
    const materialAtualizado = materiais.value.find(
      (item) => item.identificador === identificadorSelecionado,
    )

    if (materialAtualizado) {
      materialSelecionado.value = materialAtualizado
      coberturaAberta.value =
        coberturaAberta.value || Boolean(identificadorDoMaterialNaRota.value)
      await carregarCoberturas(requisicao.signal)
    } else if (identificadorDoMaterialNaRota.value) {
      versaoDoCarregamentoDeCoberturas += 1
      materialSelecionado.value = undefined
      coberturaAberta.value = false
      coberturas.value = []
      erro.value = 'O material informado não foi encontrado na sua biblioteca.'
    } else if (materialSelecionado.value) {
      versaoDoCarregamentoDeCoberturas += 1
      materialSelecionado.value = undefined
      coberturaAberta.value = false
      coberturas.value = []
    }
  } catch (causa) {
    if (causa instanceof DOMException && causa.name === 'AbortError') return
    erro.value =
      causa instanceof Error
        ? causa.message
        : 'Não foi possível carregar os materiais.'
  } finally {
    if (cancelamento === requisicao) carregando.value = false
  }
}

function limparFormulario() {
  identificadorEmEdicao.value = undefined
  Object.assign(formulario, {
    titulo: '',
    tipo: 'AULA',
    descricao: '',
    fonte: '',
    endereco: '',
    duracaoEstimadaEmMinutos: undefined,
  })
}

function abrirNovoMaterial() {
  limparFormulario()
  formularioAberto.value = true
}

function editar(material: MaterialDeEstudo) {
  identificadorEmEdicao.value = material.identificador
  Object.assign(formulario, {
    titulo: material.titulo,
    tipo: material.tipo,
    descricao: material.descricao ?? '',
    fonte: material.fonte ?? '',
    endereco: material.endereco ?? '',
    duracaoEstimadaEmMinutos: material.duracaoEstimadaEmMinutos,
  })
  formularioAberto.value = true
}

async function salvar() {
  salvando.value = true
  erro.value = ''
  const dados = {
    titulo: formulario.titulo,
    tipo: formulario.tipo,
    descricao: formulario.descricao || undefined,
    fonte: formulario.fonte || undefined,
    endereco: formulario.endereco || undefined,
    duracaoEstimadaEmMinutos: formulario.duracaoEstimadaEmMinutos || undefined,
  }
  try {
    const materialSalvo = identificadorEmEdicao.value
      ? await alterarMaterialDeEstudo(identificadorEmEdicao.value, dados)
      : await criarMaterialDeEstudo(dados)
    limparFormulario()
    formularioAberto.value = false
    await carregar()
    const materialAtualizado =
      materiais.value.find(
        (material) => material.identificador === materialSalvo.identificador,
      ) ?? materialSalvo
    await selecionarMaterial(materialAtualizado)
  } catch (causa) {
    erro.value =
      causa instanceof Error ? causa.message : 'Não foi possível salvar.'
  } finally {
    salvando.value = false
  }
}

async function alternarArquivamento(material: MaterialDeEstudo) {
  erro.value = ''
  try {
    await definirArquivamentoDoMaterial(
      material.identificador,
      !material.arquivado,
    )
    await carregar()
  } catch (causa) {
    erro.value =
      causa instanceof Error ? causa.message : 'Não foi possível arquivar.'
  }
}

async function excluir(material: MaterialDeEstudo) {
  if (!window.confirm(`Excluir o material "${material.titulo}"?`)) return
  erro.value = ''
  try {
    await excluirMaterialDeEstudo(material.identificador)
    if (materialSelecionado.value?.identificador === material.identificador) {
      materialSelecionado.value = undefined
      coberturaAberta.value = false
      coberturas.value = []
    }
    await carregar()
  } catch (causa) {
    erro.value =
      causa instanceof Error ? causa.message : 'Não foi possível excluir.'
  }
}

async function selecionarMaterial(material: MaterialDeEstudo) {
  versaoDoCarregamentoDeTopicos += 1
  materialSelecionado.value = material
  coberturaAberta.value = true
  identificadorDaMateria.value = ''
  identificadorDoTopico.value = ''
  topicos.value = []
  await carregarCoberturas()
}

async function carregarCoberturas(sinal?: AbortSignal) {
  const material = materialSelecionado.value
  if (!material) return

  const identificador = material.identificador
  const versao = ++versaoDoCarregamentoDeCoberturas
  try {
    const coberturasObtidas = await listarCoberturas(identificador, sinal)
    if (
      versao !== versaoDoCarregamentoDeCoberturas ||
      materialSelecionado.value?.identificador !== identificador
    )
      return

    coberturas.value = coberturasObtidas
    quantidadesDeCoberturas[identificador] = coberturasObtidas.length
  } catch (causa) {
    if (
      versao !== versaoDoCarregamentoDeCoberturas ||
      (causa instanceof DOMException && causa.name === 'AbortError')
    )
      return
    erro.value =
      causa instanceof Error
        ? causa.message
        : 'Não foi possível carregar a cobertura do material.'
  }
}

function fecharCobertura() {
  versaoDoCarregamentoDeCoberturas += 1
  versaoDoCarregamentoDeTopicos += 1
  coberturaAberta.value = false
}

async function carregarTopicos() {
  const versao = ++versaoDoCarregamentoDeTopicos
  identificadorDoTopico.value = ''
  topicos.value = []
  const materia = identificadorDaMateria.value
  if (!materia) return
  try {
    const topicosObtidos = await listarTodosOsTopicos(
      materia,
      false,
      cancelamento?.signal,
    )
    if (
      versao === versaoDoCarregamentoDeTopicos &&
      identificadorDaMateria.value === materia
    )
      topicos.value = topicosObtidos
  } catch (causa) {
    if (
      versao !== versaoDoCarregamentoDeTopicos ||
      (causa instanceof DOMException && causa.name === 'AbortError')
    )
      return
    erro.value =
      causa instanceof Error
        ? causa.message
        : 'Não foi possível listar tópicos.'
  }
}

async function vincularTopico() {
  if (!materialSelecionado.value || !identificadorDoTopico.value) return
  erro.value = ''
  try {
    await adicionarCobertura(
      materialSelecionado.value.identificador,
      identificadorDoTopico.value,
    )
    identificadorDoTopico.value = ''
    await carregarCoberturas()
  } catch (causa) {
    erro.value =
      causa instanceof Error ? causa.message : 'Não foi possível vincular.'
  }
}

async function desvincularTopico(cobertura: CoberturaDeTopico) {
  if (!materialSelecionado.value) return
  erro.value = ''
  try {
    await removerCobertura(
      materialSelecionado.value.identificador,
      cobertura.identificadorDoTopico,
    )
    await carregarCoberturas()
  } catch (causa) {
    erro.value =
      causa instanceof Error ? causa.message : 'Não foi possível desvincular.'
  }
}

function iconeDoMaterial(tipo: TipoDeMaterial) {
  if (tipo === 'AULA') return 'bi-play-btn'
  if (tipo === 'PDF') return 'bi-file-earmark-pdf'
  return 'bi-file-earmark-text'
}

function nomeDoTipoDeMaterial(tipo: TipoDeMaterial) {
  if (tipo === 'AULA') return 'Aula'
  if (tipo === 'PDF') return 'PDF'
  return 'Outro'
}

function formatarDataDeAtualizacao(dataHora: string) {
  const data = new Date(dataHora)
  if (Number.isNaN(data.getTime())) return 'Data não informada'
  return new Intl.DateTimeFormat('pt-BR', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
  }).format(data)
}

watch(identificadorDoMaterialNaRota, async (identificador) => {
  if (!identificador) {
    fecharCobertura()
    materialSelecionado.value = undefined
    coberturas.value = []
    return
  }
  if (carregando.value) return

  const material = materiais.value.find(
    (item) => item.identificador === identificador,
  )
  if (material) {
    await selecionarMaterial(material)
    return
  }

  if (!incluirArquivados.value) {
    incluirArquivados.value = true
    await carregar()
    return
  }

  versaoDoCarregamentoDeCoberturas += 1
  materialSelecionado.value = undefined
  coberturaAberta.value = false
  coberturas.value = []
  erro.value = 'O material informado não foi encontrado na sua biblioteca.'
})

onMounted(() => carregar())
onBeforeUnmount(() => {
  versaoDoCarregamentoDeCoberturas += 1
  versaoDoCarregamentoDeTopicos += 1
  cancelamento?.abort()
})
</script>

<template>
  <main class="pagina-da-jornada pagina-de-materiais">
    <CabecalhoDaPagina
      etiqueta="Sua biblioteca de apoio"
      titulo="Materiais"
      descricao="Encontre rapidamente o que estudar e veja quais tópicos cada fonte realmente cobre."
    >
      <template #acoes>
        <button
          class="btn btn-primary"
          type="button"
          @click="abrirNovoMaterial"
        >
          <i class="bi bi-plus-lg me-2" aria-hidden="true"></i>
          Novo material
        </button>
      </template>
    </CabecalhoDaPagina>

    <p v-if="erro" class="alert alert-danger" role="alert">{{ erro }}</p>

    <form class="card barra-da-biblioteca" @submit.prevent="carregar">
      <div class="campo-de-busca">
        <i class="bi bi-search" aria-hidden="true"></i>
        <label class="visually-hidden" for="pesquisa-material">
          Pesquisar materiais por título ou fonte
        </label>
        <input
          id="pesquisa-material"
          v-model="pesquisa"
          type="search"
          placeholder="Buscar por título ou fonte"
        />
      </div>
      <div class="d-flex flex-wrap align-items-end gap-2">
        <label
          class="d-grid gap-1 small text-secondary"
          for="tipo-material-filtro"
        >
          <span>Tipo</span>
          <select
            id="tipo-material-filtro"
            v-model="filtroDeTipo"
            class="form-select form-select-sm"
          >
            <option value="TODOS">Todos os tipos</option>
            <option value="AULA">Aula</option>
            <option value="PDF">PDF</option>
            <option value="OUTRO">Outro</option>
          </select>
        </label>
        <label
          class="d-grid gap-1 small text-secondary"
          for="ordenacao-materiais"
        >
          <span>Ordenar por</span>
          <select
            id="ordenacao-materiais"
            v-model="ordenacaoDosMateriais"
            class="form-select form-select-sm"
          >
            <option value="RECENTES">Mais recentes</option>
            <option value="TITULO">Título</option>
          </select>
        </label>
        <label class="filtro-da-biblioteca pb-2" for="materiais-arquivados">
          <input
            id="materiais-arquivados"
            v-model="incluirArquivados"
            type="checkbox"
            @change="carregar"
          />
          Incluir arquivados
        </label>
      </div>
      <button class="btn btn-outline-primary" type="submit">Buscar</button>
    </form>

    <EstadoDaPagina
      v-if="carregando"
      titulo="Carregando materiais..."
      carregando
    />
    <EstadoDaPagina
      v-else-if="materiaisExibidos.length === 0"
      class="card"
      :titulo="
        materiais.length === 0
          ? 'Nenhum material encontrado'
          : 'Nenhum material corresponde ao filtro'
      "
      :descricao="
        materiais.length === 0
          ? 'Cadastre a primeira fonte da sua biblioteca.'
          : 'Escolha outro tipo para voltar a ver sua biblioteca.'
      "
      icone="bi-file-earmark-plus"
    />
    <section v-else class="grade-da-biblioteca" aria-label="Materiais">
      <article
        v-for="material in materiaisExibidos"
        :key="material.identificador"
        class="card cartao-do-material"
        :aria-labelledby="`titulo-do-material-${material.identificador}`"
      >
        <div
          class="capa-do-material"
          :class="`tipo-${material.tipo.toLowerCase()}`"
        >
          <i
            class="bi"
            :class="iconeDoMaterial(material.tipo)"
            aria-hidden="true"
          ></i>
          <span>{{ nomeDoTipoDeMaterial(material.tipo) }}</span>
        </div>
        <div class="corpo-do-cartao-do-material">
          <span class="rotulo-discreto">
            {{ material.fonte || 'Fonte não informada' }}
          </span>
          <h2 :id="`titulo-do-material-${material.identificador}`">
            {{ material.titulo }}
          </h2>
          <p>{{ material.descricao || 'Sem descrição.' }}</p>
          <div class="dados-do-material">
            <span>
              <i class="bi bi-collection" aria-hidden="true"></i>
              {{ quantidadesDeCoberturas[material.identificador] ?? 0 }}
              tópicos cobertos
            </span>
            <span v-if="material.duracaoEstimadaEmMinutos">
              <i class="bi bi-clock" aria-hidden="true"></i>
              {{ material.duracaoEstimadaEmMinutos }} min
            </span>
            <span>
              <i class="bi bi-arrow-clockwise" aria-hidden="true"></i>
              Atualizado em
              <time :datetime="material.atualizadoEm">
                {{ formatarDataDeAtualizacao(material.atualizadoEm) }}
              </time>
            </span>
          </div>
          <footer>
            <button
              class="link-da-jornada"
              type="button"
              :disabled="material.arquivado"
              :aria-label="`Ver cobertura de ${material.titulo}`"
              @click="selecionarMaterial(material)"
            >
              Ver cobertura
              <i class="bi bi-arrow-right" aria-hidden="true"></i>
            </button>
            <details class="acoes-do-material">
              <summary
                class="botao-de-icone"
                :aria-label="`Ações de ${material.titulo}`"
              >
                <i class="bi bi-three-dots" aria-hidden="true"></i>
              </summary>
              <div class="menu-de-acoes-do-material">
                <button type="button" @click="editar(material)">Editar</button>
                <button type="button" @click="alternarArquivamento(material)">
                  {{ material.arquivado ? 'Reativar' : 'Arquivar' }}
                </button>
                <button
                  class="text-danger"
                  type="button"
                  @click="excluir(material)"
                >
                  Excluir
                </button>
              </div>
            </details>
          </footer>
        </div>
      </article>
    </section>

    <ModalDaAplicacao
      v-if="formularioAberto"
      etiqueta="Cadastro contextual"
      :titulo="identificadorEmEdicao ? 'Editar material' : 'Novo material'"
      descricao="Cadastre a fonte e, em seguida, indique o conteúdo coberto."
      @fechar="formularioAberto = false"
    >
      <p v-if="erro" class="alert alert-danger" role="alert">{{ erro }}</p>
      <form
        id="formulario-material"
        class="formulario-da-aplicacao"
        @submit.prevent="salvar"
      >
        <label>
          <span>Título</span>
          <input
            id="titulo-material"
            v-model="formulario.titulo"
            placeholder="Ex.: Português — Aula 02"
            required
            autofocus
          />
        </label>
        <div class="duas-colunas-do-formulario">
          <label>
            <span>Tipo</span>
            <select id="tipo-material" v-model="formulario.tipo">
              <option value="AULA">Aula</option>
              <option value="PDF">PDF</option>
              <option value="OUTRO">Outro</option>
            </select>
          </label>
          <label>
            <span>Fonte <em>opcional</em></span>
            <input
              id="fonte-material"
              v-model="formulario.fonte"
              placeholder="Ex.: Estratégia"
            />
          </label>
        </div>
        <label>
          <span>Descrição <em>opcional</em></span>
          <textarea
            id="descricao-material"
            v-model="formulario.descricao"
            rows="3"
          ></textarea>
        </label>
        <label>
          <span>Endereço web <em>opcional</em></span>
          <input
            id="endereco-material"
            v-model="formulario.endereco"
            type="url"
            placeholder="https://"
          />
        </label>
        <label>
          <span>Duração estimada em minutos <em>opcional</em></span>
          <input
            id="duracao-material"
            v-model.number="formulario.duracaoEstimadaEmMinutos"
            type="number"
            min="1"
          />
        </label>
        <button class="btn btn-primary" :disabled="salvando">
          {{ salvando ? 'Salvando...' : 'Salvar e definir cobertura' }}
        </button>
      </form>
    </ModalDaAplicacao>

    <GavetaLateral
      v-if="coberturaAberta && materialSelecionado"
      etiqueta="Cobertura real"
      :titulo="materialSelecionado.titulo"
      descricao="Escolha somente tópicos realmente cobertos por esta fonte."
      @fechar="fecharCobertura"
    >
      <form class="formulario-da-aplicacao" @submit.prevent="vincularTopico">
        <label>
          <span>Matéria</span>
          <select
            id="materia-cobertura"
            v-model="identificadorDaMateria"
            required
            @change="carregarTopicos"
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
          <span>Tópico coberto</span>
          <select
            id="topico-cobertura"
            v-model="identificadorDoTopico"
            required
          >
            <option value="">Selecione</option>
            <option
              v-for="topico in topicosDisponiveis"
              :key="topico.identificador"
              :value="topico.identificador"
            >
              {{ topico.nome }}
            </option>
          </select>
        </label>
        <button class="btn btn-primary" type="submit">Vincular tópico</button>
      </form>
      <div class="topicos-cobertos">
        <p class="sobretitulo-da-pagina">Tópicos já cobertos</p>
        <p v-if="coberturas.length === 0" class="text-secondary">
          Nenhum tópico vinculado.
        </p>
        <div v-for="cobertura in coberturas" :key="cobertura.identificador">
          <span>
            <i class="bi bi-check2-circle" aria-hidden="true"></i>
            {{ cobertura.nomeDoTopico }}
          </span>
          <button
            class="btn btn-sm btn-link text-danger"
            type="button"
            :aria-label="`Remover vínculo com ${cobertura.nomeDoTopico}`"
            @click="desvincularTopico(cobertura)"
          >
            Remover vínculo
          </button>
        </div>
      </div>
    </GavetaLateral>
  </main>
</template>
