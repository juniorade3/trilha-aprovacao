<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'

import { ErroDaApi } from '@/compartilhado/api/clienteHttp'
import CabecalhoDaPagina from '@/compartilhado/componentes/CabecalhoDaPagina.vue'
import EstadoDaPagina from '@/compartilhado/componentes/EstadoDaPagina.vue'
import NavegacaoDoPlanejamento from './NavegacaoDoPlanejamento.vue'
import {
  consultarPriorizacaoDeTopicos,
  type FaixaDaPriorizacao,
  type GrupoDaPriorizacao,
  type MateriaPriorizada,
  type RespostaDePriorizacaoDeTopicos,
  type TopicoPriorizado,
} from './apiDePriorizacaoDeTopicos'

const resposta = ref<RespostaDePriorizacaoDeTopicos>()
const carregando = ref(true)
const erro = ref('')
const contextoIncompleto = ref(false)
const sessaoExpirada = ref(false)
const dataDeReferencia = ref(dataLocalAtual())
const identificadorDaMateria = ref('')
const grupoSelecionado = ref<'TODOS' | GrupoDaPriorizacao>('TODOS')
const opcoesDeMaterias = ref<Array<{ id: string; nome: string }>>([])
const botaoDeAtualizar = ref<HTMLButtonElement>()
let cancelamento: AbortController | undefined

const grupos: Array<{
  valor: GrupoDaPriorizacao
  titulo: string
  descricao: string
}> = [
  {
    valor: 'LACUNA',
    titulo: 'Lacunas',
    descricao: 'Conteúdos sem estudo ou sem evidência recente suficiente.',
  },
  {
    valor: 'FRAQUEZA',
    titulo: 'Fraquezas',
    descricao: 'Conteúdos com sinais objetivos de que precisam de reforço.',
  },
  {
    valor: 'CONSOLIDADO',
    titulo: 'Consolidados',
    descricao: 'Conteúdos com evidências recentes de consolidação.',
  },
]

const totalDeTopicos = computed(
  () =>
    resposta.value?.materias.reduce(
      (total, materia) => total + materia.topicos.length,
      0,
    ) ?? 0,
)

const gruposVisiveis = computed(() =>
  grupoSelecionado.value === 'TODOS'
    ? grupos
    : grupos.filter((grupo) => grupo.valor === grupoSelecionado.value),
)

const materiasVisiveis = computed(() => {
  const materias = resposta.value?.materias ?? []
  if (grupoSelecionado.value === 'TODOS') return materias
  return materias.filter((materia) =>
    materia.topicos.some((topico) => topico.grupo === grupoSelecionado.value),
  )
})

const totalDeTopicosVisiveis = computed(() => {
  if (grupoSelecionado.value === 'TODOS') return totalDeTopicos.value
  return materiasVisiveis.value.reduce(
    (total, materia) =>
      total +
      materia.topicos.filter(
        (topico) => topico.grupo === grupoSelecionado.value,
      ).length,
    0,
  )
})

function quantidadeDeTopicosVisiveis(materia: MateriaPriorizada) {
  if (grupoSelecionado.value === 'TODOS') return materia.topicos.length
  return topicosDoGrupo(materia, grupoSelecionado.value).length
}

function dataLocalAtual() {
  const partes = new Intl.DateTimeFormat('en-US', {
    timeZone: 'America/Sao_Paulo',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).formatToParts(new Date())
  const valor = (tipo: Intl.DateTimeFormatPartTypes) =>
    partes.find((parte) => parte.type === tipo)?.value ?? ''
  return `${valor('year')}-${valor('month')}-${valor('day')}`
}

function atualizarOpcoesDeMaterias(
  materias: MateriaPriorizada[],
  itensSemMapeamento: RespostaDePriorizacaoDeTopicos['itensSemMapeamento'],
) {
  const opcoes = new Map(
    opcoesDeMaterias.value.map((materia) => [materia.id, materia]),
  )
  for (const materia of materias)
    opcoes.set(materia.id, { id: materia.id, nome: materia.nome })
  for (const item of itensSemMapeamento) {
    if (item.idMateria && item.nomeMateria)
      opcoes.set(item.idMateria, {
        id: item.idMateria,
        nome: item.nomeMateria,
      })
  }
  opcoesDeMaterias.value = [...opcoes.values()].sort((a, b) =>
    a.nome.localeCompare(b.nome, 'pt-BR'),
  )
}

async function carregar() {
  cancelamento?.abort()
  const requisicao = new AbortController()
  cancelamento = requisicao
  carregando.value = true
  erro.value = ''
  contextoIncompleto.value = false
  sessaoExpirada.value = false
  resposta.value = undefined

  try {
    const dados = await consultarPriorizacaoDeTopicos(
      dataDeReferencia.value,
      identificadorDaMateria.value || undefined,
      requisicao.signal,
    )
    resposta.value = dados
    atualizarOpcoesDeMaterias(dados.materias, dados.itensSemMapeamento)
  } catch (causa) {
    if (causa instanceof DOMException && causa.name === 'AbortError') return
    sessaoExpirada.value = causa instanceof ErroDaApi && causa.status === 401
    contextoIncompleto.value =
      causa instanceof ErroDaApi &&
      causa.status === 422 &&
      causa.codigo === 'CONTEXTO_DE_PRIORIZACAO_INCOMPLETO'
    erro.value =
      causa instanceof Error
        ? causa.message
        : 'Não foi possível calcular as prioridades.'
  } finally {
    if (cancelamento === requisicao) carregando.value = false
  }
}

async function repetirAposErro() {
  await carregar()
  await nextTick()
  botaoDeAtualizar.value?.focus()
}

function topicosDoGrupo(materia: MateriaPriorizada, grupo: GrupoDaPriorizacao) {
  return materia.topicos.filter((topico) => topico.grupo === grupo)
}

function rotuloDaFaixa(faixa: FaixaDaPriorizacao) {
  return {
    SEM_ESTUDO: 'Sem estudo',
    SEM_EVIDENCIA: 'Sem evidência',
    EVIDENCIA_DESATUALIZADA: 'Evidência desatualizada',
    DADOS_INSUFICIENTES: 'Dados insuficientes',
    PRECISA_REFORCO: 'Precisa de reforço',
    DESEMPENHO_PARCIAL: 'Desempenho parcial',
    CONSOLIDADO: 'Consolidado',
  }[faixa]
}

function rotuloDaAcao(topico: TopicoPriorizado) {
  return topico.acaoSugerida === 'TEORIA'
    ? 'Estudar teoria'
    : 'Praticar questões'
}

function formatarData(valor?: string | null) {
  if (!valor) return '—'
  const data = new Date(valor.length === 10 ? `${valor}T12:00:00-03:00` : valor)
  if (Number.isNaN(data.getTime())) return '—'
  return new Intl.DateTimeFormat('pt-BR', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
    timeZone: 'America/Sao_Paulo',
  }).format(data)
}

function rotuloDoPercentual(valor?: number | null) {
  return valor == null ? '—' : `${valor}%`
}

onMounted(() => void carregar())
onBeforeUnmount(() => cancelamento?.abort())
</script>

<template>
  <main class="pagina-comum pagina-de-planejamento pagina-de-priorizacao">
    <div class="topo-da-area-de-planejamento">
      <CabecalhoDaPagina
        etiqueta="Prioridades objetivas"
        titulo="Lacunas e prioridades"
        descricao="Compare o conteúdo oficial com seus estudos e evidências. O ranking é determinístico e explica cada sugestão."
      />

      <NavegacaoDoPlanejamento />
    </div>

    <form
      class="card filtros-da-priorizacao"
      aria-label="Filtros da priorização"
      @submit.prevent="carregar"
    >
      <label class="form-label mb-0">
        <span>Data de referência</span>
        <input
          v-model="dataDeReferencia"
          class="form-control"
          type="date"
          required
        />
      </label>
      <label class="form-label mb-0">
        <span>Matéria</span>
        <select v-model="identificadorDaMateria" class="form-select">
          <option value="">Todas as matérias</option>
          <option
            v-for="materia in opcoesDeMaterias"
            :key="materia.id"
            :value="materia.id"
          >
            {{ materia.nome }}
          </option>
        </select>
      </label>
      <label class="form-label mb-0">
        <span>Exibir</span>
        <select
          v-model="grupoSelecionado"
          class="form-select"
          aria-label="Classificação exibida"
        >
          <option value="TODOS">Tudo junto</option>
          <option value="LACUNA">Somente lacunas</option>
          <option value="FRAQUEZA">Somente fraquezas</option>
          <option value="CONSOLIDADO">Somente consolidados</option>
        </select>
      </label>
      <button
        ref="botaoDeAtualizar"
        class="btn btn-primary"
        type="submit"
        :disabled="carregando"
      >
        <span
          v-if="carregando"
          class="spinner-border spinner-border-sm me-2"
          aria-hidden="true"
        ></span>
        Atualizar prioridades
      </button>
    </form>

    <EstadoDaPagina
      v-if="carregando"
      titulo="Calculando prioridades"
      descricao="Comparando edital, cobertura e evidências até a data escolhida."
      carregando
    />

    <EstadoDaPagina
      v-else-if="sessaoExpirada"
      titulo="Sua sessão expirou"
      descricao="Entre novamente para consultar suas prioridades."
      icone="bi-person-lock"
      role="alert"
    >
      <RouterLink
        class="btn btn-primary mt-3"
        :to="{
          name: 'login',
          query: {
            redirecionar: '/planejamento/prioridades',
            sessao: 'expirada',
          },
        }"
      >
        Entrar novamente
      </RouterLink>
    </EstadoDaPagina>

    <EstadoDaPagina
      v-else-if="contextoIncompleto"
      titulo="Complete o objetivo da priorização"
      :descricao="erro"
      icone="bi-bullseye"
      role="alert"
    >
      <RouterLink class="btn btn-primary mt-3" to="/concursos">
        Revisar concurso ativo
      </RouterLink>
    </EstadoDaPagina>

    <EstadoDaPagina
      v-else-if="erro"
      titulo="Não foi possível calcular as prioridades"
      :descricao="erro"
      icone="bi-cloud-slash"
      role="alert"
    >
      <button
        class="btn btn-outline-primary mt-3"
        type="button"
        @click="repetirAposErro"
      >
        Tentar novamente
      </button>
    </EstadoDaPagina>

    <template v-else-if="resposta">
      <section
        class="card contexto-da-priorizacao"
        aria-labelledby="titulo-contexto-oficial"
      >
        <div>
          <span class="rotulo-discreto">Contexto oficial</span>
          <h2 id="titulo-contexto-oficial">
            {{ resposta.contexto.concurso.nome }}
          </h2>
          <p class="mb-0 text-secondary">
            {{ resposta.contexto.cargo.nome }} ·
            {{ resposta.contexto.edital.nome }}
          </p>
        </div>
        <dl class="mb-0">
          <div>
            <dt>Data de referência</dt>
            <dd>{{ formatarData(resposta.contexto.dataReferencia) }}</dd>
          </div>
          <div>
            <dt>Janela recente</dt>
            <dd>
              {{ formatarData(resposta.contexto.inicioJanelaRecente) }} a
              {{ formatarData(resposta.contexto.dataReferencia) }}
            </dd>
          </div>
        </dl>
      </section>

      <section
        class="grade-do-resumo-da-priorizacao"
        aria-label="Resumo das prioridades"
      >
        <article>
          <strong>{{ resposta.resumo.itensOficiais }}</strong>
          <span>itens oficiais</span>
        </article>
        <article>
          <strong>{{ resposta.resumo.topicosExigidos }}</strong>
          <span>tópicos exigidos</span>
        </article>
        <article class="resumo-lacuna">
          <strong>{{ resposta.resumo.lacunas }}</strong>
          <span>lacunas</span>
        </article>
        <article class="resumo-fraqueza">
          <strong>{{ resposta.resumo.fraquezas }}</strong>
          <span>fraquezas</span>
        </article>
        <article class="resumo-consolidado">
          <strong>{{ resposta.resumo.consolidados }}</strong>
          <span>consolidados</span>
        </article>
        <article>
          <strong>{{ resposta.resumo.itensSemMapeamento }}</strong>
          <span>sem mapeamento</span>
        </article>
      </section>

      <section
        v-if="resposta.itensSemMapeamento.length"
        class="alert alert-warning itens-sem-mapeamento"
        aria-labelledby="titulo-itens-sem-mapeamento"
      >
        <div>
          <i class="bi bi-diagram-3" aria-hidden="true"></i>
        </div>
        <div>
          <h2 id="titulo-itens-sem-mapeamento" class="h5">
            Itens oficiais ainda sem mapeamento
          </h2>
          <p>
            Eles não impedem o ranking dos tópicos já vinculados, mas ainda não
            podem receber uma prioridade.
          </p>
          <ul class="mb-0">
            <li v-for="item in resposta.itensSemMapeamento" :key="item.id">
              <span v-if="item.nomeMateria">{{ item.nomeMateria }} · </span>
              {{ item.descricao }}
            </li>
          </ul>
        </div>
      </section>

      <EstadoDaPagina
        v-if="totalDeTopicosVisiveis === 0"
        titulo="Nenhum tópico nesta classificação"
        descricao="Escolha outra classificação ou selecione uma matéria diferente."
        icone="bi-clipboard-data"
      />

      <section
        v-for="materia in materiasVisiveis"
        v-else
        :key="materia.id"
        class="materia-da-priorizacao"
        :aria-labelledby="`materia-${materia.id}`"
      >
        <header>
          <span class="rotulo-discreto">Matéria</span>
          <h2 :id="`materia-${materia.id}`">{{ materia.nome }}</h2>
          <span class="badge rounded-pill etiqueta-neutra">
            {{ quantidadeDeTopicosVisiveis(materia) }}
            {{
              quantidadeDeTopicosVisiveis(materia) === 1 ? 'tópico' : 'tópicos'
            }}
          </span>
        </header>

        <div class="grupos-da-priorizacao">
          <section
            v-for="grupo in gruposVisiveis"
            :key="grupo.valor"
            class="grupo-da-priorizacao"
            :class="`grupo-${grupo.valor.toLowerCase()}`"
            :aria-labelledby="`grupo-${materia.id}-${grupo.valor}`"
          >
            <header>
              <div>
                <h3 :id="`grupo-${materia.id}-${grupo.valor}`">
                  {{ grupo.titulo }}
                </h3>
                <p>{{ grupo.descricao }}</p>
              </div>
              <span class="badge rounded-pill">
                {{ topicosDoGrupo(materia, grupo.valor).length }}
              </span>
            </header>

            <p
              v-if="topicosDoGrupo(materia, grupo.valor).length === 0"
              class="grupo-sem-topicos"
            >
              Nenhum tópico neste grupo.
            </p>

            <article
              v-for="topico in topicosDoGrupo(materia, grupo.valor)"
              :key="topico.id"
              class="cartao-do-topico-priorizado"
              :aria-labelledby="`topico-${topico.id}`"
            >
              <header>
                <span
                  class="posicao-do-topico"
                  :aria-label="`Posição ${topico.posicaoNoGrupo}`"
                >
                  {{ topico.posicaoNoGrupo }}
                </span>
                <div>
                  <h4 :id="`topico-${topico.id}`">{{ topico.nome }}</h4>
                  <div class="d-flex flex-wrap gap-2">
                    <span class="badge faixa-da-priorizacao">
                      {{ rotuloDaFaixa(topico.faixa) }}
                    </span>
                    <span class="badge acao-da-priorizacao">
                      {{ rotuloDaAcao(topico) }}
                    </span>
                    <span class="badge etiqueta-neutra">
                      {{ topico.quantidadeItensOficiais }}
                      {{
                        topico.quantidadeItensOficiais === 1 ? 'item' : 'itens'
                      }}
                      do edital
                    </span>
                  </div>
                </div>
              </header>

              <p v-if="!topico.possuiMaterial" class="alerta-sem-material">
                <i class="bi bi-exclamation-triangle" aria-hidden="true"></i>
                Nenhum material ativo cobre este tópico. Ele continua elegível
                para estudo.
              </p>

              <dl class="indicadores-do-topico">
                <div>
                  <dt>Estudos</dt>
                  <dd>{{ topico.indicadores.estudos }}</dd>
                </div>
                <div>
                  <dt>Evidências</dt>
                  <dd>{{ topico.indicadores.evidencias }}</dd>
                </div>
                <div>
                  <dt>Questões recentes</dt>
                  <dd>{{ topico.indicadores.questoesRecentes }}</dd>
                </div>
                <div>
                  <dt>Acertos recentes</dt>
                  <dd>{{ topico.indicadores.acertosRecentes }}</dd>
                </div>
                <div>
                  <dt>Erros recentes</dt>
                  <dd>{{ topico.indicadores.errosRecentes }}</dd>
                </div>
                <div>
                  <dt>Percentual</dt>
                  <dd>
                    {{ rotuloDoPercentual(topico.indicadores.percentual) }}
                  </dd>
                </div>
                <div>
                  <dt>Recordação</dt>
                  <dd>{{ topico.indicadores.ultimaRecordacao ?? '—' }}</dd>
                </div>
                <div>
                  <dt>Dificuldade</dt>
                  <dd>{{ topico.indicadores.ultimaDificuldade ?? '—' }}</dd>
                </div>
                <div>
                  <dt>Padrões repetidos</dt>
                  <dd>{{ topico.indicadores.quantidadePadroesRepetidos }}</dd>
                </div>
                <div>
                  <dt>Último padrão repetido</dt>
                  <dd>
                    {{
                      formatarData(
                        topico.indicadores.ultimaOcorrenciaPadraoRepetido,
                      )
                    }}
                  </dd>
                </div>
                <div>
                  <dt>Última evidência</dt>
                  <dd>
                    {{ formatarData(topico.indicadores.ultimaEvidencia) }}
                  </dd>
                </div>
              </dl>

              <div class="justificativas-da-priorizacao">
                <strong>Por que esta posição?</strong>
                <ul>
                  <li
                    v-for="justificativa in topico.justificativas"
                    :key="justificativa"
                  >
                    {{ justificativa }}
                  </li>
                </ul>
              </div>
            </article>
          </section>
        </div>
      </section>
    </template>
  </main>
</template>

<style scoped lang="scss">
.pagina-de-priorizacao {
  padding-bottom: 7rem;
}

.filtros-da-priorizacao {
  align-items: end;
  display: grid;
  gap: 1rem;
  grid-template-columns:
    minmax(12rem, 0.7fr) minmax(14rem, 1.1fr)
    minmax(12rem, 0.8fr) auto;
  margin-bottom: 1.5rem;
  padding: 1.25rem;

  label {
    display: grid;
    gap: 0.4rem;
  }
}

.contexto-da-priorizacao {
  align-items: center;
  background: var(--cor-papel);
  display: flex;
  gap: 2rem;
  justify-content: space-between;
  margin-bottom: 1rem;
  padding: 1.4rem 1.5rem;

  h2 {
    font-family: var(--fonte-editorial);
    margin: 0;
  }

  dl {
    display: flex;
    gap: 2rem;
  }

  dl div {
    display: grid;
    gap: 0.2rem;
  }

  dt {
    color: var(--cor-texto-secundario);
    font-size: 0.8125rem;
    letter-spacing: 0.06em;
    text-transform: uppercase;
  }

  dd {
    font-weight: 700;
    margin: 0;
  }
}

.grade-do-resumo-da-priorizacao {
  display: grid;
  gap: 0.75rem;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  margin-bottom: 1.5rem;

  article {
    background: var(--cor-papel);
    border: 1px solid var(--cor-borda);
    border-radius: 0.9rem;
    display: grid;
    gap: 0.2rem;
    padding: 1rem;
  }

  strong {
    font-family: var(--fonte-editorial);
    font-size: 1.8rem;
  }

  span {
    color: var(--cor-texto-secundario);
    font-size: 0.8125rem;
  }

  .resumo-lacuna {
    border-top: 3px solid var(--cor-ambar);
  }

  .resumo-fraqueza {
    border-top: 3px solid var(--cor-perigo);
  }

  .resumo-consolidado {
    border-top: 3px solid var(--cor-destaque);
  }
}

.itens-sem-mapeamento {
  align-items: flex-start;
  display: flex;
  gap: 1rem;
  margin-bottom: 1.5rem;

  > div:first-child {
    font-size: 1.4rem;
  }

  p {
    margin-bottom: 0.5rem;
  }
}

.materia-da-priorizacao {
  margin-top: 2rem;

  > header {
    align-items: center;
    display: flex;
    flex-wrap: wrap;
    gap: 0.75rem;
    margin-bottom: 1rem;
  }

  > header .rotulo-discreto {
    flex-basis: 100%;
    margin-bottom: -0.45rem;
  }

  > header h2 {
    font-family: var(--fonte-editorial);
    margin: 0;
  }
}

.grupos-da-priorizacao {
  display: grid;
  gap: 1rem;
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.grupo-da-priorizacao {
  align-self: start;
  background: var(--cor-superficie-secundaria);
  border: 1px solid var(--cor-borda);
  border-radius: 1rem;
  min-width: 0;
  padding: 1rem;

  > header {
    align-items: flex-start;
    display: flex;
    gap: 1rem;
    justify-content: space-between;
    min-height: 5rem;
  }

  > header h3 {
    font-size: 1.1rem;
    margin: 0 0 0.25rem;
  }

  > header p {
    color: var(--cor-texto-secundario);
    font-size: 0.8125rem;
    margin: 0;
  }

  > header .badge {
    background: var(--cor-destaque-clara);
    color: var(--cor-destaque);
  }
}

.grupo-lacuna {
  border-top: 4px solid var(--cor-ambar);
}

.grupo-fraqueza {
  border-top: 4px solid var(--cor-perigo);
}

.grupo-consolidado {
  border-top: 4px solid var(--cor-destaque);
}

.grupo-sem-topicos {
  border: 1px dashed var(--cor-borda);
  border-radius: 0.75rem;
  color: var(--cor-texto-secundario);
  font-size: 0.82rem;
  margin: 0;
  padding: 1rem;
  text-align: center;
}

.cartao-do-topico-priorizado {
  background: var(--cor-papel);
  border: 1px solid var(--cor-borda);
  border-radius: 0.85rem;
  box-shadow: var(--sombra-suave);
  margin-top: 0.75rem;
  overflow: hidden;
  padding: 1rem;

  > header {
    align-items: flex-start;
    display: flex;
    gap: 0.75rem;
  }

  h4 {
    font-size: 1rem;
    margin: 0 0 0.5rem;
  }
}

.posicao-do-topico {
  align-items: center;
  background: var(--cor-destaque-clara);
  border-radius: 50%;
  color: var(--cor-destaque);
  display: inline-flex;
  flex: none;
  font-weight: 800;
  height: 2rem;
  justify-content: center;
  width: 2rem;
}

.faixa-da-priorizacao {
  background: var(--cor-superficie-secundaria);
  color: var(--cor-tinta);
}

.acao-da-priorizacao {
  background: var(--cor-destaque-clara);
  color: var(--cor-destaque);
}

.alerta-sem-material {
  align-items: flex-start;
  background: var(--cor-ambar-clara);
  border-radius: 0.65rem;
  color: var(--cor-ambar);
  display: flex;
  font-size: 0.8125rem;
  gap: 0.5rem;
  margin: 0.85rem 0;
  padding: 0.65rem;
}

.indicadores-do-topico {
  display: grid;
  gap: 0.6rem;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  margin: 0.9rem 0;

  div {
    border-bottom: 1px solid var(--cor-borda);
    display: grid;
    gap: 0.1rem;
    min-width: 0;
    padding-bottom: 0.4rem;
  }

  dt {
    color: var(--cor-texto-secundario);
    font-size: 0.8125rem;
    text-transform: uppercase;
  }

  dd {
    font-size: 0.86rem;
    font-weight: 700;
    margin: 0;
    overflow-wrap: anywhere;
  }
}

.justificativas-da-priorizacao {
  border-top: 1px solid var(--cor-borda);
  font-size: 0.8125rem;
  padding-top: 0.8rem;

  ul {
    color: var(--cor-texto-secundario);
    margin: 0.35rem 0 0;
    padding-left: 1rem;
  }
}

@media (max-width: 1199.98px) {
  .grade-do-resumo-da-priorizacao {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .grupos-da-priorizacao {
    grid-template-columns: 1fr;
  }

  .grupo-da-priorizacao > header {
    min-height: auto;
  }
}

@media (max-width: 991.98px) {
  .filtros-da-priorizacao {
    grid-template-columns: repeat(2, minmax(0, 1fr));

    > * {
      min-width: 0;
    }

    .btn {
      width: 100%;
    }
  }

  .contexto-da-priorizacao,
  .contexto-da-priorizacao dl {
    align-items: flex-start;
    flex-wrap: wrap;
    gap: 1rem 2rem;
  }
}

@media (max-width: 575.98px) {
  .filtros-da-priorizacao {
    grid-template-columns: 1fr;
  }

  .grade-do-resumo-da-priorizacao {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .contexto-da-priorizacao,
  .contexto-da-priorizacao dl {
    align-items: flex-start;
    flex-direction: column;
    gap: 1rem;
  }
}
</style>
