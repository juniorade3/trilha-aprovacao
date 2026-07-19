<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'

import BarraDeProgresso from '@/compartilhado/componentes/BarraDeProgresso.vue'
import CabecalhoDaPagina from '@/compartilhado/componentes/CabecalhoDaPagina.vue'
import EstadoDaPagina from '@/compartilhado/componentes/EstadoDaPagina.vue'
import GavetaLateral from '@/compartilhado/componentes/GavetaLateral.vue'
import ModalDaAplicacao from '@/compartilhado/componentes/ModalDaAplicacao.vue'
import { listarTodosOsEstudos } from '@/modulos/estudos/apiDeEstudos'
import {
  alterarMateria,
  alterarTopico,
  arquivarMateria,
  criarMateria,
  criarTopico,
  excluirMateria,
  listarMaterias,
  listarTodosOsTopicos,
  obterMateria,
  type Materia,
  type Topico,
} from './apiDeConteudos'

const propriedades = defineProps<{
  identificador?: string
}>()

const materias = ref<Materia[]>([])
const topicos = ref<Topico[]>([])
const topicosComEstudo = ref(new Set<string>())
const materiaSelecionada = ref<Materia>()
const pesquisa = ref('')
const incluirArquivadas = ref(false)
const carregando = ref(true)
const carregandoDetalhe = ref(false)
const salvando = ref(false)
const erro = ref('')
const pagina = ref(0)
const totalDePaginas = ref(0)
const totalDeItens = ref(0)
const identificadorEmEdicao = ref<string>()
const formularioAberto = ref(false)
const formulario = reactive({ nome: '', descricao: '', cor: '#128F83' })
const formularioTopicoAberto = ref(false)
const identificadorDoTopicoEmEdicao = ref<string>()
const salvandoTopico = ref(false)
const formularioTopico = reactive({
  nome: '',
  descricao: '',
  identificadorDoTopicoPai: '',
  ordem: 1,
})
let cancelamento: AbortController | undefined
let versaoDaSelecao = 0

const topicosOrdenados = computed(() => {
  const identificadores = new Set(
    topicos.value.map((topico) => topico.identificador),
  )
  const topicosPorPai = new Map<string, Topico[]>()
  for (const topico of topicos.value) {
    const pai =
      topico.identificadorDoTopicoPai &&
      identificadores.has(topico.identificadorDoTopicoPai)
        ? topico.identificadorDoTopicoPai
        : ''
    const filhos = topicosPorPai.get(pai) ?? []
    filhos.push(topico)
    topicosPorPai.set(pai, filhos)
  }
  for (const filhos of topicosPorPai.values()) {
    filhos.sort(
      (primeiro, segundo) =>
        primeiro.ordem - segundo.ordem ||
        primeiro.nome.localeCompare(segundo.nome),
    )
  }

  const ordenados: Topico[] = []
  const visitados = new Set<string>()
  function visitar(identificadorDoPai = '') {
    for (const topico of topicosPorPai.get(identificadorDoPai) ?? []) {
      if (visitados.has(topico.identificador)) continue
      visitados.add(topico.identificador)
      ordenados.push(topico)
      visitar(topico.identificador)
    }
  }
  visitar()
  for (const topico of topicos.value) {
    if (!visitados.has(topico.identificador)) ordenados.push(topico)
  }
  return ordenados
})

const paisDisponiveisParaTopico = computed(() => {
  const indisponiveis = new Set<string>()
  if (identificadorDoTopicoEmEdicao.value) {
    indisponiveis.add(identificadorDoTopicoEmEdicao.value)
    let encontrouDescendente = true
    while (encontrouDescendente) {
      encontrouDescendente = false
      for (const topico of topicos.value) {
        if (
          topico.identificadorDoTopicoPai &&
          indisponiveis.has(topico.identificadorDoTopicoPai) &&
          !indisponiveis.has(topico.identificador)
        ) {
          indisponiveis.add(topico.identificador)
          encontrouDescendente = true
        }
      }
    }
  }
  return topicosOrdenados.value.filter(
    (topico) => !topico.arquivado && !indisponiveis.has(topico.identificador),
  )
})

const quantidadeComEstudo = computed(
  () =>
    topicos.value.filter((topico) =>
      topicosComEstudo.value.has(topico.identificador),
    ).length,
)

const coberturaDaMateria = computed(() =>
  topicos.value.length
    ? Math.round((quantidadeComEstudo.value / topicos.value.length) * 100)
    : 0,
)

async function carregar(novaPagina = pagina.value) {
  cancelamento?.abort()
  const requisicaoAtual = new AbortController()
  cancelamento = requisicaoAtual
  carregando.value = true
  erro.value = ''
  try {
    const [resposta, estudos] = await Promise.all([
      listarMaterias(
        pesquisa.value,
        incluirArquivadas.value,
        novaPagina,
        requisicaoAtual.signal,
      ),
      listarTodosOsEstudos(requisicaoAtual.signal),
    ])
    materias.value = resposta.itens
    pagina.value = resposta.pagina
    totalDePaginas.value = resposta.totalDePaginas
    totalDeItens.value = resposta.totalDeItens
    topicosComEstudo.value = new Set(
      estudos
        .filter((estudo) => estudo.situacao === 'ATIVO')
        .map((estudo) => estudo.identificadorDoTopico),
    )
    let selecaoAtual = materias.value.find(
      (materia) =>
        materia.identificador ===
        (propriedades.identificador ?? materiaSelecionada.value?.identificador),
    )
    if (propriedades.identificador && !selecaoAtual) {
      selecaoAtual = await obterMateria(
        propriedades.identificador,
        requisicaoAtual.signal,
      )
      materias.value = [selecaoAtual, ...materias.value]
      totalDeItens.value = Math.max(totalDeItens.value, materias.value.length)
    }
    if (selecaoAtual) {
      await selecionarMateria(selecaoAtual, requisicaoAtual.signal)
    } else if (materias.value[0]) {
      await selecionarMateria(materias.value[0], requisicaoAtual.signal)
    } else {
      materiaSelecionada.value = undefined
      topicos.value = []
    }
  } catch (causa) {
    if (causa instanceof DOMException && causa.name === 'AbortError') return
    erro.value =
      causa instanceof Error
        ? causa.message
        : 'Não foi possível carregar as matérias.'
  } finally {
    if (cancelamento === requisicaoAtual) carregando.value = false
  }
}

async function selecionarMateria(
  materia: Materia,
  sinal = cancelamento?.signal,
) {
  const selecaoAtual = ++versaoDaSelecao
  materiaSelecionada.value = materia
  topicos.value = []
  carregandoDetalhe.value = true
  try {
    const resposta = await listarTodosOsTopicos(
      materia.identificador,
      true,
      sinal,
    )
    if (
      selecaoAtual === versaoDaSelecao &&
      materiaSelecionada.value?.identificador === materia.identificador
    )
      topicos.value = resposta
  } catch (causa) {
    if (causa instanceof DOMException && causa.name === 'AbortError') return
    if (selecaoAtual === versaoDaSelecao) {
      erro.value =
        causa instanceof Error
          ? causa.message
          : 'Não foi possível carregar os tópicos.'
    }
  } finally {
    if (selecaoAtual === versaoDaSelecao) carregandoDetalhe.value = false
  }
}

function abrirNovo() {
  identificadorEmEdicao.value = undefined
  Object.assign(formulario, {
    nome: '',
    descricao: '',
    cor: '#128F83',
  })
  formularioAberto.value = true
}

function editar(materia: Materia) {
  identificadorEmEdicao.value = materia.identificador
  Object.assign(formulario, {
    nome: materia.nome,
    descricao: materia.descricao ?? '',
    cor: materia.cor ?? '#128F83',
  })
  formularioAberto.value = true
}

function fecharFormulario() {
  formularioAberto.value = false
  identificadorEmEdicao.value = undefined
}

function abrirNovoTopico() {
  if (!materiaSelecionada.value || materiaSelecionada.value.arquivada) return
  identificadorDoTopicoEmEdicao.value = undefined
  Object.assign(formularioTopico, {
    nome: '',
    descricao: '',
    identificadorDoTopicoPai: '',
    ordem: Math.max(0, ...topicos.value.map((topico) => topico.ordem)) + 1,
  })
  formularioTopicoAberto.value = true
}

function editarTopico(topico: Topico) {
  if (materiaSelecionada.value?.arquivada) return
  identificadorDoTopicoEmEdicao.value = topico.identificador
  Object.assign(formularioTopico, {
    nome: topico.nome,
    descricao: topico.descricao ?? '',
    identificadorDoTopicoPai: topico.identificadorDoTopicoPai ?? '',
    ordem: topico.ordem,
  })
  formularioTopicoAberto.value = true
}

function fecharFormularioTopico() {
  formularioTopicoAberto.value = false
  identificadorDoTopicoEmEdicao.value = undefined
}

function filtrarArquivadas(incluir: boolean) {
  incluirArquivadas.value = incluir
  void carregar(0)
}

async function salvar() {
  salvando.value = true
  erro.value = ''
  const dados = {
    nome: formulario.nome,
    descricao: formulario.descricao || undefined,
    cor: formulario.cor || undefined,
  }
  try {
    const materiaSalva = identificadorEmEdicao.value
      ? await alterarMateria(identificadorEmEdicao.value, dados)
      : await criarMateria(dados)
    materiaSelecionada.value = materiaSalva
    fecharFormulario()
    await carregar(0)
  } catch (causa) {
    erro.value =
      causa instanceof Error ? causa.message : 'Não foi possível salvar.'
  } finally {
    salvando.value = false
  }
}

async function salvarTopico() {
  const materia = materiaSelecionada.value
  if (!materia) return
  salvandoTopico.value = true
  erro.value = ''
  const dados = {
    nome: formularioTopico.nome,
    descricao: formularioTopico.descricao || undefined,
    identificadorDoTopicoPai:
      formularioTopico.identificadorDoTopicoPai || undefined,
    ordem: Number(formularioTopico.ordem),
  }
  try {
    if (identificadorDoTopicoEmEdicao.value) {
      await alterarTopico(identificadorDoTopicoEmEdicao.value, dados)
    } else {
      await criarTopico(materia.identificador, dados)
    }
    fecharFormularioTopico()
    await selecionarMateria(materia)
  } catch (causa) {
    erro.value =
      causa instanceof Error ? causa.message : 'Não foi possível salvar.'
  } finally {
    salvandoTopico.value = false
  }
}

async function alternarArquivamento(materia: Materia) {
  erro.value = ''
  try {
    await arquivarMateria(materia.identificador, !materia.arquivada)
    await carregar(pagina.value)
  } catch (causa) {
    erro.value =
      causa instanceof Error ? causa.message : 'Não foi possível arquivar.'
  }
}

async function excluir(materia: Materia) {
  if (!window.confirm(`Excluir a matéria "${materia.nome}"?`)) return
  erro.value = ''
  try {
    await excluirMateria(materia.identificador)
    await carregar(pagina.value)
  } catch (causa) {
    erro.value =
      causa instanceof Error ? causa.message : 'Não foi possível excluir.'
  }
}

function nivelDoTopico(topico: Topico) {
  let nivel = 0
  let pai = topico.identificadorDoTopicoPai
  const visitados = new Set<string>()
  while (pai && !visitados.has(pai)) {
    visitados.add(pai)
    nivel += 1
    pai = topicos.value.find(
      (candidato) => candidato.identificador === pai,
    )?.identificadorDoTopicoPai
  }
  return nivel
}

onMounted(() => carregar())
onBeforeUnmount(() => cancelamento?.abort())
</script>

<template>
  <main class="pagina-da-jornada pagina-de-conteudos">
    <CabecalhoDaPagina
      etiqueta="Catálogo reutilizável"
      titulo="Conteúdos"
      descricao="Organize matérias e tópicos uma única vez. O mesmo estudo pode valer para todos os concursos que exigem o assunto."
    >
      <template #acoes>
        <button class="btn btn-primary" type="button" @click="abrirNovo">
          <i class="bi bi-plus-lg me-2" aria-hidden="true"></i>
          Nova matéria
        </button>
      </template>
    </CabecalhoDaPagina>

    <p v-if="erro" class="alert alert-danger" role="alert">{{ erro }}</p>

    <div class="faixa-resumo-de-conteudos" aria-label="Resumo do catálogo">
      <span
        ><b>{{ totalDeItens }}</b> matérias</span
      >
      <span
        ><b>{{ topicos.length }}</b> tópicos na matéria selecionada</span
      >
      <span
        ><b>{{ quantidadeComEstudo }}</b> com estudo ativo</span
      >
      <span
        ><b>{{ coberturaDaMateria }}%</b> de cobertura</span
      >
    </div>

    <section class="catalogo-mestre-detalhe">
      <aside class="card painel-mestre-de-materias">
        <form class="campo-de-busca" @submit.prevent="carregar(0)">
          <i class="bi bi-search" aria-hidden="true"></i>
          <input
            v-model="pesquisa"
            aria-label="Buscar matéria"
            placeholder="Buscar matéria"
          />
          <button class="visually-hidden">Buscar</button>
        </form>
        <div class="filtros-do-catalogo">
          <button
            type="button"
            :class="{ ativo: !incluirArquivadas }"
            :aria-pressed="!incluirArquivadas"
            @click="filtrarArquivadas(false)"
          >
            Ativas
          </button>
          <button
            type="button"
            :class="{ ativo: incluirArquivadas }"
            :aria-pressed="incluirArquivadas"
            @click="filtrarArquivadas(true)"
          >
            Com arquivadas
          </button>
        </div>

        <EstadoDaPagina
          v-if="carregando"
          titulo="Carregando matérias..."
          carregando
        />
        <EstadoDaPagina
          v-else-if="materias.length === 0"
          titulo="Nenhuma matéria encontrada"
          descricao="Crie sua primeira matéria para começar o catálogo."
          icone="bi-journal-plus"
        />
        <div v-else class="lista-mestre-de-materias">
          <button
            v-for="materia in materias"
            :key="materia.identificador"
            type="button"
            :class="{
              ativo:
                materia.identificador === materiaSelecionada?.identificador,
            }"
            @click="selecionarMateria(materia)"
          >
            <i
              class="cor-da-materia"
              :style="{ background: materia.cor ?? '#128f83' }"
            ></i>
            <span>
              <b>{{ materia.nome }}</b>
              <small>{{ materia.descricao || 'Sem descrição.' }}</small>
              <em v-if="materia.arquivada">Matéria arquivada</em>
            </span>
            <i class="bi bi-chevron-right" aria-hidden="true"></i>
          </button>
        </div>

        <nav
          v-if="totalDePaginas > 1"
          class="paginacao-do-catalogo"
          aria-label="Paginação de matérias"
        >
          <button
            type="button"
            aria-label="Página anterior de matérias"
            :disabled="pagina === 0"
            @click="carregar(pagina - 1)"
          >
            <i class="bi bi-arrow-left" aria-hidden="true"></i>
          </button>
          <span>{{ pagina + 1 }} / {{ totalDePaginas }}</span>
          <button
            type="button"
            aria-label="Próxima página de matérias"
            :disabled="pagina + 1 >= totalDePaginas"
            @click="carregar(pagina + 1)"
          >
            <i class="bi bi-arrow-right" aria-hidden="true"></i>
          </button>
        </nav>
      </aside>

      <article class="card detalhe-da-materia-no-catalogo">
        <div v-if="!materiaSelecionada" class="estado-do-catalogo">
          <i class="bi bi-arrow-left-circle" aria-hidden="true"></i>
          <strong>Selecione uma matéria</strong>
          <span>Os tópicos e dados aparecerão neste painel.</span>
        </div>
        <template v-else>
          <header class="cabecalho-do-detalhe-da-materia">
            <div>
              <span
                class="etiqueta-da-materia"
                :style="{
                  color: materiaSelecionada.cor ?? '#128f83',
                  background: `${materiaSelecionada.cor ?? '#128f83'}18`,
                }"
              >
                {{
                  materiaSelecionada.arquivada ? 'Arquivada' : 'Matéria ativa'
                }}
              </span>
              <h2>{{ materiaSelecionada.nome }}</h2>
              <p>{{ materiaSelecionada.descricao || 'Sem descrição.' }}</p>
            </div>
            <div>
              <button
                class="btn btn-sm btn-outline-primary"
                type="button"
                :disabled="materiaSelecionada.arquivada"
                @click="editar(materiaSelecionada)"
              >
                <i class="bi bi-pencil me-1" aria-hidden="true"></i>
                Editar
              </button>
              <button
                class="btn btn-sm btn-primary"
                type="button"
                :disabled="materiaSelecionada.arquivada"
                @click="abrirNovoTopico"
              >
                <i class="bi bi-plus-lg me-1" aria-hidden="true"></i>
                Novo tópico
              </button>
            </div>
          </header>

          <div class="progresso-da-materia">
            <span>
              <b>{{ quantidadeComEstudo }} tópicos com estudo</b>
              <small>de {{ topicos.length }} cadastrados</small>
            </span>
            <BarraDeProgresso
              :valor="coberturaDaMateria"
              :rotulo="`Cobertura de estudo em ${materiaSelecionada.nome}`"
              :cor="materiaSelecionada.cor ?? '#128f83'"
            />
            <strong>{{ coberturaDaMateria }}%</strong>
          </div>

          <div v-if="carregandoDetalhe" class="estado-do-catalogo">
            Carregando tópicos...
          </div>
          <div
            v-else-if="topicos.length === 0"
            class="estado-do-catalogo estado-vazio-com-borda"
          >
            <i class="bi bi-diagram-2" aria-hidden="true"></i>
            <strong>Nenhum tópico cadastrado</strong>
            <span>Crie o primeiro tópico dentro desta matéria.</span>
          </div>
          <div v-else class="arvore-de-topicos-do-catalogo">
            <div class="pasta-dos-topicos">
              <span>
                <i class="bi bi-chevron-down" aria-hidden="true"></i>
                <i class="bi bi-collection" aria-hidden="true"></i>
              </span>
              <b>Conteúdo da matéria</b>
              <em>{{ topicos.length }} tópicos</em>
            </div>
            <div
              v-for="topico in topicosOrdenados"
              :key="topico.identificador"
              class="linha-do-topico-no-catalogo"
              :style="{ paddingLeft: `${1 + nivelDoTopico(topico) * 1.4}rem` }"
            >
              <span
                :class="{
                  estudado: topicosComEstudo.has(topico.identificador),
                }"
              >
                <i
                  v-if="topicosComEstudo.has(topico.identificador)"
                  class="bi bi-check2"
                  aria-hidden="true"
                ></i>
              </span>
              <p>
                <b>{{ topico.nome }}</b>
                <small>
                  {{
                    topicosComEstudo.has(topico.identificador)
                      ? 'Possui registro de estudo ativo'
                      : 'Ainda não possui estudo ativo'
                  }}
                </small>
              </p>
              <span v-if="topico.arquivado" class="badge text-bg-secondary">
                Arquivado
              </span>
              <button
                class="botao-de-icone"
                type="button"
                :disabled="materiaSelecionada.arquivada"
                :aria-label="`Editar tópico ${topico.nome}`"
                @click="editarTopico(topico)"
              >
                <i class="bi bi-pencil" aria-hidden="true"></i>
              </button>
            </div>
          </div>

          <footer class="acoes-secundarias-da-materia">
            <RouterLink :to="`/materias/${materiaSelecionada.identificador}`">
              Gerenciar árvore completa
              <i class="bi bi-arrow-right" aria-hidden="true"></i>
            </RouterLink>
            <div>
              <button
                class="btn btn-sm btn-link text-secondary"
                type="button"
                @click="alternarArquivamento(materiaSelecionada)"
              >
                {{
                  materiaSelecionada.arquivada
                    ? 'Restaurar matéria'
                    : 'Arquivar matéria'
                }}
              </button>
              <button
                class="btn btn-sm btn-link text-danger"
                type="button"
                @click="excluir(materiaSelecionada)"
              >
                Excluir
              </button>
            </div>
          </footer>
        </template>
      </article>
    </section>

    <GavetaLateral
      v-if="formularioAberto"
      etiqueta="Cadastro contextual"
      :titulo="identificadorEmEdicao ? 'Editar matéria' : 'Nova matéria'"
      descricao="Crie uma base reutilizável para um ou mais concursos."
      @fechar="fecharFormulario"
    >
      <form
        id="formulario-materia"
        class="formulario-da-aplicacao"
        @submit.prevent="salvar"
      >
        <label>
          <span>Nome da matéria</span>
          <input
            id="nome-materia"
            v-model="formulario.nome"
            maxlength="120"
            placeholder="Ex.: Administração Pública"
            required
            autofocus
          />
        </label>
        <label>
          <span>Descrição curta <em>opcional</em></span>
          <textarea
            id="descricao-materia"
            v-model="formulario.descricao"
            maxlength="1000"
            rows="4"
          ></textarea>
        </label>
        <label>
          <span>Cor de identificação</span>
          <input
            v-model="formulario.cor"
            type="color"
            title="Escolha a cor da matéria"
          />
        </label>
        <button class="btn btn-primary" :disabled="salvando">
          {{ salvando ? 'Salvando...' : 'Salvar matéria' }}
        </button>
      </form>
    </GavetaLateral>

    <ModalDaAplicacao
      v-if="formularioTopicoAberto"
      etiqueta="Conteúdo da matéria"
      :titulo="identificadorDoTopicoEmEdicao ? 'Editar tópico' : 'Novo tópico'"
      :descricao="`Cadastre o tópico diretamente em ${materiaSelecionada?.nome ?? 'sua matéria'}.`"
      @fechar="fecharFormularioTopico"
    >
      <form
        id="formulario-topico-contextual"
        class="formulario-da-aplicacao"
        @submit.prevent="salvarTopico"
      >
        <label>
          <span>Nome do tópico</span>
          <input
            id="nome-topico-contextual"
            v-model="formularioTopico.nome"
            maxlength="160"
            placeholder="Ex.: Direitos fundamentais"
            required
            autofocus
          />
        </label>
        <label>
          <span>Descrição <em>opcional</em></span>
          <textarea
            v-model="formularioTopico.descricao"
            maxlength="1000"
            rows="3"
          ></textarea>
        </label>
        <label>
          <span>Tópico pai <em>opcional</em></span>
          <select v-model="formularioTopico.identificadorDoTopicoPai">
            <option value="">Nenhum — nível principal</option>
            <option
              v-for="topico in paisDisponiveisParaTopico"
              :key="topico.identificador"
              :value="topico.identificador"
            >
              {{ '— '.repeat(nivelDoTopico(topico)) }}{{ topico.nome }}
            </option>
          </select>
        </label>
        <label>
          <span>Ordem</span>
          <input
            v-model.number="formularioTopico.ordem"
            type="number"
            min="1"
            step="1"
            required
          />
        </label>
        <button class="btn btn-primary" :disabled="salvandoTopico">
          {{ salvandoTopico ? 'Salvando...' : 'Salvar tópico' }}
        </button>
      </form>
    </ModalDaAplicacao>
  </main>
</template>
