<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import {
  alterarCargo,
  alterarConcurso,
  alterarEdital,
  alterarGrupo,
  alterarMateriaDaProva,
  alterarProva,
  arquivarConcurso,
  criarCargo,
  criarEdital,
  criarGrupo,
  criarMateriaDaProva,
  criarProva,
  definirEditalPrincipal,
  excluirCargo,
  excluirEdital,
  excluirGrupo,
  excluirMateriaDaProva,
  excluirProva,
  listarCargos,
  listarEditais,
  listarGrupos,
  listarMateriasDaProva,
  listarMateriasDisponiveis,
  listarProvas,
  obterConcurso,
  selecionarCargo,
  type Cargo,
  type Concurso,
  type DadosDeCargo,
  type DadosDeConcurso,
  type DadosDeEdital,
  type DadosDeGrupo,
  type DadosDeMateriaDaProva,
  type DadosDeProva,
  type Edital,
  type Grupo,
  type MateriaDaProva,
  type Prova,
} from './apiDeConcursos'
import type { Materia } from '@/modulos/materias/apiDeConteudos'

const rota = useRoute()
const roteador = useRouter()
const identificadorDoConcurso = String(rota.params.identificador)
const concurso = ref<Concurso>()
const editais = ref<Edital[]>([])
const cargos = ref<Cargo[]>([])
const materiasDisponiveis = ref<Materia[]>([])
const provasPorCargo = reactive<Record<string, Prova[]>>({})
const gruposPorProva = reactive<Record<string, Grupo[]>>({})
const materiasPorGrupo = reactive<Record<string, MateriaDaProva[]>>({})
const carregando = ref(true)
const salvando = ref(false)
const erro = ref('')
const secaoAberta = ref('edital')
const cancelamento = new AbortController()

const concursoArquivado = computed(
  () => concurso.value?.situacao === 'ARQUIVADO',
)
const todasAsProvas = computed(() => Object.values(provasPorCargo).flat())
const todosOsGrupos = computed(() => Object.values(gruposPorProva).flat())

const formularioConcurso = reactive<DadosDeConcurso>({
  nome: '',
  descricao: '',
  orgao: '',
  banca: '',
  situacao: 'PLANEJADO',
  dataPrevistaPrincipal: '',
})
const formularioEdital = reactive<DadosDeEdital & { identificador?: string }>({
  titulo: '',
  numero: '',
  ano: undefined,
  descricao: '',
  dataDePublicacao: '',
  enderecoDoDocumento: '',
})
const formularioCargo = reactive<DadosDeCargo & { identificador?: string }>({
  nome: '',
  area: '',
  especialidade: '',
  nivelDeEscolaridade: 'NAO_INFORMADO',
  ordem: 1,
})
const formularioProva = reactive<
  DadosDeProva & { identificador?: string; identificadorDoCargo: string }
>({
  identificadorDoCargo: '',
  nome: '',
  tipo: 'OBJETIVA',
  carater: 'NAO_INFORMADO',
  ordem: 1,
})
const formularioGrupo = reactive<
  DadosDeGrupo & { identificador?: string; identificadorDaProva: string }
>({
  identificadorDaProva: '',
  nome: '',
  ordem: 1,
})
const formularioMateria = reactive<
  DadosDeMateriaDaProva & {
    identificador?: string
    identificadorDoGrupo: string
  }
>({
  identificadorDoGrupo: '',
  identificadorDaMateria: '',
  ordem: 1,
})

async function carregar() {
  carregando.value = true
  erro.value = ''
  try {
    const [concursoObtido, editaisObtidos, cargosObtidos, materiasObtidas] =
      await Promise.all([
        obterConcurso(identificadorDoConcurso, cancelamento.signal),
        listarEditais(identificadorDoConcurso, cancelamento.signal),
        listarCargos(identificadorDoConcurso, cancelamento.signal),
        listarMateriasDisponiveis(cancelamento.signal),
      ])
    concurso.value = concursoObtido
    editais.value = editaisObtidos
    cargos.value = cargosObtidos
    materiasDisponiveis.value = materiasObtidas.itens
    preencherConcurso(concursoObtido)
    await carregarDescendentes(cargosObtidos)
  } catch (causa) {
    if (causa instanceof DOMException && causa.name === 'AbortError') return
    erro.value =
      causa instanceof Error
        ? causa.message
        : 'Nao foi possivel carregar o concurso.'
  } finally {
    carregando.value = false
  }
}

async function carregarDescendentes(cargosAtuais: Cargo[]) {
  limparMapa(provasPorCargo)
  limparMapa(gruposPorProva)
  limparMapa(materiasPorGrupo)
  const paresDeProvas = await Promise.all(
    cargosAtuais.map(async (cargo) => ({
      cargo: cargo.identificador,
      provas: await listarProvas(cargo.identificador, cancelamento.signal),
    })),
  )
  for (const par of paresDeProvas) provasPorCargo[par.cargo] = par.provas
  const provas = paresDeProvas.flatMap((par) => par.provas)
  const paresDeGrupos = await Promise.all(
    provas.map(async (prova) => ({
      prova: prova.identificador,
      grupos: await listarGrupos(prova.identificador, cancelamento.signal),
    })),
  )
  for (const par of paresDeGrupos) gruposPorProva[par.prova] = par.grupos
  const grupos = paresDeGrupos.flatMap((par) => par.grupos)
  const paresDeMaterias = await Promise.all(
    grupos.map(async (grupo) => ({
      grupo: grupo.identificador,
      materias: await listarMateriasDaProva(
        grupo.identificador,
        cancelamento.signal,
      ),
    })),
  )
  for (const par of paresDeMaterias) materiasPorGrupo[par.grupo] = par.materias
  if (!formularioProva.identificadorDoCargo && cargosAtuais[0])
    formularioProva.identificadorDoCargo = cargosAtuais[0].identificador
  if (!formularioGrupo.identificadorDaProva && provas[0])
    formularioGrupo.identificadorDaProva = provas[0].identificador
  if (!formularioMateria.identificadorDoGrupo && grupos[0])
    formularioMateria.identificadorDoGrupo = grupos[0].identificador
}

function limparMapa(mapa: Record<string, unknown>) {
  for (const chave of Object.keys(mapa)) delete mapa[chave]
}

function preencherConcurso(valor: Concurso) {
  formularioConcurso.nome = valor.nome
  formularioConcurso.descricao = valor.descricao ?? ''
  formularioConcurso.orgao = valor.orgao ?? ''
  formularioConcurso.banca = valor.banca ?? ''
  formularioConcurso.situacao =
    valor.situacao === 'ARQUIVADO' ? 'PLANEJADO' : valor.situacao
  formularioConcurso.dataPrevistaPrincipal = valor.dataPrevistaPrincipal ?? ''
}

async function executar(acao: () => Promise<unknown>) {
  salvando.value = true
  erro.value = ''
  try {
    await acao()
    await carregar()
    return true
  } catch (causa) {
    erro.value =
      causa instanceof Error
        ? causa.message
        : 'Nao foi possivel concluir a operacao.'
    return false
  } finally {
    salvando.value = false
  }
}

async function salvarConcurso() {
  await executar(() =>
    alterarConcurso(identificadorDoConcurso, {
      ...formularioConcurso,
      descricao: formularioConcurso.descricao || undefined,
      orgao: formularioConcurso.orgao || undefined,
      banca: formularioConcurso.banca || undefined,
      dataPrevistaPrincipal:
        formularioConcurso.dataPrevistaPrincipal || undefined,
    }),
  )
}

async function alternarArquivamento() {
  await executar(() =>
    arquivarConcurso(identificadorDoConcurso, !concursoArquivado.value),
  )
}

function editarEdital(edital: Edital) {
  Object.assign(formularioEdital, {
    identificador: edital.identificador,
    titulo: edital.titulo,
    numero: edital.numero ?? '',
    ano: edital.ano,
    descricao: edital.descricao ?? '',
    dataDePublicacao: edital.dataDePublicacao ?? '',
    enderecoDoDocumento: edital.enderecoDoDocumento ?? '',
  })
}

function limparEdital() {
  Object.assign(formularioEdital, {
    identificador: undefined,
    titulo: '',
    numero: '',
    ano: undefined,
    descricao: '',
    dataDePublicacao: '',
    enderecoDoDocumento: '',
  })
}

async function salvarEdital() {
  const dados = {
    titulo: formularioEdital.titulo,
    numero: formularioEdital.numero || undefined,
    ano: numeroOpcional(formularioEdital.ano),
    descricao: formularioEdital.descricao || undefined,
    dataDePublicacao: formularioEdital.dataDePublicacao || undefined,
    enderecoDoDocumento: formularioEdital.enderecoDoDocumento || undefined,
  }
  const sucesso = await executar(() =>
    formularioEdital.identificador
      ? alterarEdital(formularioEdital.identificador, dados)
      : criarEdital(identificadorDoConcurso, dados),
  )
  if (sucesso) limparEdital()
}

function editarCargo(cargo: Cargo) {
  Object.assign(formularioCargo, {
    identificador: cargo.identificador,
    nome: cargo.nome,
    area: cargo.area ?? '',
    especialidade: cargo.especialidade ?? '',
    nivelDeEscolaridade: cargo.nivelDeEscolaridade,
    ordem: cargo.ordem,
  })
}

function limparCargo() {
  Object.assign(formularioCargo, {
    identificador: undefined,
    nome: '',
    area: '',
    especialidade: '',
    nivelDeEscolaridade: 'NAO_INFORMADO',
    ordem: cargos.value.length + 1,
  })
}

async function salvarCargo() {
  const dados: DadosDeCargo = {
    nome: formularioCargo.nome,
    area: formularioCargo.area || undefined,
    especialidade: formularioCargo.especialidade || undefined,
    nivelDeEscolaridade: formularioCargo.nivelDeEscolaridade,
    ordem: Number(formularioCargo.ordem),
  }
  const sucesso = await executar(() =>
    formularioCargo.identificador
      ? alterarCargo(formularioCargo.identificador, dados)
      : criarCargo(identificadorDoConcurso, dados),
  )
  if (sucesso) limparCargo()
}

function editarProva(prova: Prova) {
  Object.assign(formularioProva, {
    identificador: prova.identificador,
    identificadorDoCargo: prova.identificadorDoCargo,
    nome: prova.nome,
    tipo: prova.tipo,
    carater: prova.carater,
    ordem: prova.ordem,
    dataHoraPrevista: prova.dataHoraPrevista?.slice(0, 16) ?? '',
    duracaoEmMinutos: prova.duracaoEmMinutos,
    quantidadeDeQuestoes: prova.quantidadeDeQuestoes,
    pontuacaoMaxima: prova.pontuacaoMaxima,
    pontuacaoMinima: prova.pontuacaoMinima,
  })
}

function limparProva() {
  Object.assign(formularioProva, {
    identificador: undefined,
    nome: '',
    tipo: 'OBJETIVA',
    carater: 'NAO_INFORMADO',
    ordem: 1,
    dataHoraPrevista: '',
    duracaoEmMinutos: undefined,
    quantidadeDeQuestoes: undefined,
    pontuacaoMaxima: undefined,
    pontuacaoMinima: undefined,
  })
}

async function salvarProva() {
  const dados: DadosDeProva = {
    nome: formularioProva.nome,
    tipo: formularioProva.tipo,
    carater: formularioProva.carater,
    ordem: Number(formularioProva.ordem),
    dataHoraPrevista: dataHoraComFuso(formularioProva.dataHoraPrevista),
    duracaoEmMinutos: numeroOpcional(formularioProva.duracaoEmMinutos),
    quantidadeDeQuestoes: numeroOpcional(formularioProva.quantidadeDeQuestoes),
    pontuacaoMaxima: numeroOpcional(formularioProva.pontuacaoMaxima),
    pontuacaoMinima: numeroOpcional(formularioProva.pontuacaoMinima),
  }
  const sucesso = await executar(() =>
    formularioProva.identificador
      ? alterarProva(formularioProva.identificador, dados)
      : criarProva(formularioProva.identificadorDoCargo, dados),
  )
  if (sucesso) limparProva()
}

function editarGrupo(grupo: Grupo) {
  Object.assign(formularioGrupo, {
    identificador: grupo.identificador,
    identificadorDaProva: grupo.identificadorDaProva,
    nome: grupo.nome,
    ordem: grupo.ordem,
    quantidadeDeQuestoes: grupo.quantidadeDeQuestoes,
    pontuacaoMaxima: grupo.pontuacaoMaxima,
    pontuacaoMinima: grupo.pontuacaoMinima,
  })
}

function limparGrupo() {
  Object.assign(formularioGrupo, {
    identificador: undefined,
    nome: '',
    ordem: 1,
    quantidadeDeQuestoes: undefined,
    pontuacaoMaxima: undefined,
    pontuacaoMinima: undefined,
  })
}

async function salvarGrupo() {
  const dados: DadosDeGrupo = {
    nome: formularioGrupo.nome,
    ordem: Number(formularioGrupo.ordem),
    quantidadeDeQuestoes: numeroOpcional(formularioGrupo.quantidadeDeQuestoes),
    pontuacaoMaxima: numeroOpcional(formularioGrupo.pontuacaoMaxima),
    pontuacaoMinima: numeroOpcional(formularioGrupo.pontuacaoMinima),
  }
  const sucesso = await executar(() =>
    formularioGrupo.identificador
      ? alterarGrupo(formularioGrupo.identificador, dados)
      : criarGrupo(formularioGrupo.identificadorDaProva, dados),
  )
  if (sucesso) limparGrupo()
}

function editarMateria(materia: MateriaDaProva) {
  Object.assign(formularioMateria, {
    identificador: materia.identificador,
    identificadorDoGrupo: materia.identificadorDoGrupoDeConteudo,
    identificadorDaMateria: materia.identificadorDaMateria,
    ordem: materia.ordem,
    peso: materia.peso,
    quantidadeDeQuestoes: materia.quantidadeDeQuestoes,
    pontuacaoMaxima: materia.pontuacaoMaxima,
  })
}

function limparMateria() {
  Object.assign(formularioMateria, {
    identificador: undefined,
    identificadorDaMateria: '',
    ordem: 1,
    peso: undefined,
    quantidadeDeQuestoes: undefined,
    pontuacaoMaxima: undefined,
  })
}

async function salvarMateria() {
  const dados = {
    ordem: Number(formularioMateria.ordem),
    peso: numeroOpcional(formularioMateria.peso),
    quantidadeDeQuestoes: numeroOpcional(
      formularioMateria.quantidadeDeQuestoes,
    ),
    pontuacaoMaxima: numeroOpcional(formularioMateria.pontuacaoMaxima),
  }
  const sucesso = await executar(() =>
    formularioMateria.identificador
      ? alterarMateriaDaProva(formularioMateria.identificador, dados)
      : criarMateriaDaProva(formularioMateria.identificadorDoGrupo, {
          ...dados,
          identificadorDaMateria: formularioMateria.identificadorDaMateria,
        }),
  )
  if (sucesso) limparMateria()
}

async function remover(rotulo: string, acao: () => Promise<void>) {
  if (!window.confirm(`Excluir ${rotulo}?`)) return
  await executar(acao)
}

function numeroOpcional(valor: number | string | undefined) {
  return valor === '' || valor === undefined || valor === null
    ? undefined
    : Number(valor)
}

function dataHoraComFuso(valor?: string) {
  return valor ? new Date(valor).toISOString() : undefined
}

function alternarSecao(secao: string) {
  secaoAberta.value = secaoAberta.value === secao ? '' : secao
}

onMounted(() => carregar())
onBeforeUnmount(() => cancelamento.abort())
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

    <p
      v-if="erro"
      class="alert alert-danger"
      role="alert"
      aria-live="assertive"
    >
      {{ erro }}
    </p>
    <p
      v-if="rota.query.novo === 'true'"
      class="alert alert-success"
      role="status"
    >
      Concurso criado. Complete as proximas secoes no seu ritmo.
    </p>

    <div v-if="carregando" class="text-center py-5" aria-live="polite">
      <div class="spinner-border text-primary mb-3" role="status">
        <span class="visually-hidden">Carregando estrutura</span>
      </div>
      <p>Carregando estrutura do concurso...</p>
    </div>

    <template v-else-if="concurso">
      <header class="card card-body border-0 shadow-sm mb-4">
        <div class="d-flex flex-wrap justify-content-between gap-3">
          <div>
            <div class="d-flex gap-2 mb-2">
              <span class="badge text-bg-light">{{ concurso.situacao }}</span>
              <span v-if="concurso.ativo" class="badge text-bg-success">
                Ativo
              </span>
            </div>
            <h1 class="mb-1">{{ concurso.nome }}</h1>
            <p class="text-secondary mb-0">
              {{ concurso.orgao || 'Orgao nao informado' }}
              <span v-if="concurso.banca"> · {{ concurso.banca }}</span>
            </p>
          </div>
          <button
            class="btn btn-outline-secondary align-self-start"
            type="button"
            :disabled="salvando"
            @click="alternarArquivamento"
          >
            {{ concursoArquivado ? 'Restaurar concurso' : 'Arquivar concurso' }}
          </button>
        </div>
        <p v-if="concursoArquivado" class="alert alert-warning mt-3 mb-0">
          Restaure o concurso para alterar sua estrutura.
        </p>
      </header>

      <section class="card card-body border-0 shadow-sm mb-4">
        <h2 class="h4">1. Dados gerais</h2>
        <form @submit.prevent="salvarConcurso">
          <fieldset :disabled="concursoArquivado || salvando">
            <div class="row g-3">
              <div class="col-md-6">
                <label class="form-label" for="detalhe-nome-concurso"
                  >Nome</label
                >
                <input
                  id="detalhe-nome-concurso"
                  v-model="formularioConcurso.nome"
                  class="form-control"
                  required
                />
              </div>
              <div class="col-md-3">
                <label class="form-label" for="detalhe-orgao-concurso">
                  Orgao
                </label>
                <input
                  id="detalhe-orgao-concurso"
                  v-model="formularioConcurso.orgao"
                  class="form-control"
                />
              </div>
              <div class="col-md-3">
                <label class="form-label" for="detalhe-banca-concurso">
                  Banca
                </label>
                <input
                  id="detalhe-banca-concurso"
                  v-model="formularioConcurso.banca"
                  class="form-control"
                />
              </div>
              <div class="col-md-4">
                <label class="form-label" for="detalhe-situacao-concurso">
                  Situacao
                </label>
                <select
                  id="detalhe-situacao-concurso"
                  v-model="formularioConcurso.situacao"
                  class="form-select"
                >
                  <option value="PLANEJADO">Planejado</option>
                  <option value="EDITAL_PUBLICADO">Edital publicado</option>
                  <option value="INSCRICOES_ABERTAS">Inscricoes abertas</option>
                  <option value="EM_ANDAMENTO">Em andamento</option>
                  <option value="ENCERRADO">Encerrado</option>
                  <option value="SUSPENSO">Suspenso</option>
                  <option value="CANCELADO">Cancelado</option>
                </select>
              </div>
              <div class="col-md-4">
                <label class="form-label" for="detalhe-data-concurso">
                  Data principal
                </label>
                <input
                  id="detalhe-data-concurso"
                  v-model="formularioConcurso.dataPrevistaPrincipal"
                  class="form-control"
                  type="date"
                />
              </div>
              <div class="col-12">
                <label class="form-label" for="detalhe-descricao-concurso">
                  Descricao
                </label>
                <textarea
                  id="detalhe-descricao-concurso"
                  v-model="formularioConcurso.descricao"
                  class="form-control"
                  rows="3"
                ></textarea>
              </div>
            </div>
            <button class="btn btn-primary mt-3">Salvar dados gerais</button>
          </fieldset>
        </form>
      </section>

      <div class="row g-4">
        <section class="col-xl-7">
          <div class="card card-body border-0 shadow-sm mb-4">
            <h2 class="h4">2. Editais</h2>
            <p v-if="editais.length === 0" class="estado-vazio-compacto">
              Nenhum edital cadastrado.
            </p>
            <article
              v-for="edital in editais"
              :key="edital.identificador"
              class="item-da-estrutura"
            >
              <div>
                <strong>{{ edital.titulo }}</strong>
                <span
                  v-if="edital.principal"
                  class="badge text-bg-success ms-2"
                >
                  Principal
                </span>
                <p class="small text-secondary mb-0">
                  {{ edital.numero || 'Sem numero' }}
                  <span v-if="edital.ano"> · {{ edital.ano }}</span>
                </p>
              </div>
              <div class="acoes-da-estrutura">
                <button
                  class="btn btn-outline-success btn-sm"
                  :disabled="concursoArquivado || edital.principal"
                  @click="
                    executar(() => definirEditalPrincipal(edital.identificador))
                  "
                >
                  Principal
                </button>
                <button
                  class="btn btn-outline-primary btn-sm"
                  :disabled="concursoArquivado"
                  @click="editarEdital(edital)"
                >
                  Editar
                </button>
                <button
                  class="btn btn-outline-danger btn-sm"
                  :disabled="concursoArquivado"
                  @click="
                    remover(`o edital ${edital.titulo}`, () =>
                      excluirEdital(edital.identificador),
                    )
                  "
                >
                  Excluir
                </button>
              </div>
            </article>
          </div>

          <div class="card card-body border-0 shadow-sm">
            <h2 class="h4">Estrutura hierarquica</h2>
            <p v-if="cargos.length === 0" class="estado-vazio-compacto">
              3. Adicione um cargo para iniciar a arvore.
            </p>
            <article
              v-for="cargo in cargos"
              :key="cargo.identificador"
              class="ramo-da-estrutura"
            >
              <div class="item-da-estrutura">
                <div>
                  <strong>3. {{ cargo.nome }}</strong>
                  <span
                    v-if="cargo.selecionado"
                    class="badge text-bg-success ms-2"
                  >
                    Selecionado
                  </span>
                  <p class="small text-secondary mb-0">
                    {{ cargo.nivelDeEscolaridade }} · ordem {{ cargo.ordem }}
                  </p>
                </div>
                <div class="acoes-da-estrutura">
                  <button
                    class="btn btn-outline-success btn-sm"
                    :disabled="concursoArquivado || cargo.selecionado"
                    @click="
                      executar(() => selecionarCargo(cargo.identificador))
                    "
                  >
                    Selecionar
                  </button>
                  <button
                    class="btn btn-outline-primary btn-sm"
                    :disabled="concursoArquivado"
                    @click="editarCargo(cargo)"
                  >
                    Editar
                  </button>
                  <button
                    class="btn btn-outline-danger btn-sm"
                    :disabled="concursoArquivado"
                    @click="
                      remover(`o cargo ${cargo.nome}`, () =>
                        excluirCargo(cargo.identificador),
                      )
                    "
                  >
                    Excluir
                  </button>
                </div>
              </div>

              <div
                v-if="(provasPorCargo[cargo.identificador] ?? []).length === 0"
                class="estado-vazio-compacto ms-3"
              >
                4. Nenhuma prova neste cargo.
              </div>
              <article
                v-for="prova in provasPorCargo[cargo.identificador] ?? []"
                :key="prova.identificador"
                class="ramo-da-estrutura ms-3"
              >
                <div class="item-da-estrutura">
                  <div>
                    <strong>4. {{ prova.nome }}</strong>
                    <p class="small text-secondary mb-0">
                      {{ prova.tipo }} · {{ prova.carater }}
                    </p>
                  </div>
                  <div class="acoes-da-estrutura">
                    <button
                      class="btn btn-outline-primary btn-sm"
                      :disabled="concursoArquivado"
                      @click="editarProva(prova)"
                    >
                      Editar
                    </button>
                    <button
                      class="btn btn-outline-danger btn-sm"
                      :disabled="concursoArquivado"
                      @click="
                        remover(`a prova ${prova.nome}`, () =>
                          excluirProva(prova.identificador),
                        )
                      "
                    >
                      Excluir
                    </button>
                  </div>
                </div>

                <article
                  v-for="grupo in gruposPorProva[prova.identificador] ?? []"
                  :key="grupo.identificador"
                  class="ramo-da-estrutura ms-3"
                >
                  <div class="item-da-estrutura">
                    <div>
                      <strong>5. {{ grupo.nome }}</strong>
                      <p class="small text-secondary mb-0">
                        Ordem {{ grupo.ordem }}
                      </p>
                    </div>
                    <div class="acoes-da-estrutura">
                      <button
                        class="btn btn-outline-primary btn-sm"
                        :disabled="concursoArquivado"
                        @click="editarGrupo(grupo)"
                      >
                        Editar
                      </button>
                      <button
                        class="btn btn-outline-danger btn-sm"
                        :disabled="concursoArquivado"
                        @click="
                          remover(`o grupo ${grupo.nome}`, () =>
                            excluirGrupo(grupo.identificador),
                          )
                        "
                      >
                        Excluir
                      </button>
                    </div>
                  </div>

                  <div
                    v-if="
                      (materiasPorGrupo[grupo.identificador] ?? []).length === 0
                    "
                    class="estado-vazio-compacto ms-3"
                  >
                    6. Nenhuma materia neste grupo.
                  </div>
                  <div
                    v-for="materia in materiasPorGrupo[grupo.identificador] ??
                    []"
                    :key="materia.identificador"
                    class="item-da-estrutura ms-3"
                  >
                    <div>
                      <strong>6. {{ materia.nomeDaMateria }}</strong>
                      <p class="small text-secondary mb-0">
                        Ordem {{ materia.ordem }}
                        <span v-if="materia.peso">
                          · peso {{ materia.peso }}</span
                        >
                      </p>
                    </div>
                    <div class="acoes-da-estrutura">
                      <button
                        class="btn btn-outline-primary btn-sm"
                        :disabled="concursoArquivado"
                        @click="editarMateria(materia)"
                      >
                        Editar
                      </button>
                      <button
                        class="btn btn-outline-danger btn-sm"
                        :disabled="concursoArquivado"
                        @click="
                          remover(`a materia ${materia.nomeDaMateria}`, () =>
                            excluirMateriaDaProva(materia.identificador),
                          )
                        "
                      >
                        Excluir
                      </button>
                    </div>
                  </div>
                </article>
              </article>
            </article>
          </div>
        </section>

        <aside class="col-xl-5">
          <div id="formularios-da-estrutura" class="accordion shadow-sm">
            <div class="accordion-item">
              <h2 class="accordion-header">
                <button
                  class="accordion-button"
                  :class="{ collapsed: secaoAberta !== 'edital' }"
                  type="button"
                  :aria-expanded="secaoAberta === 'edital'"
                  aria-controls="formulario-edital"
                  @click="alternarSecao('edital')"
                >
                  {{ formularioEdital.identificador ? 'Editar' : 'Adicionar' }}
                  edital
                </button>
              </h2>
              <div
                id="formulario-edital"
                class="accordion-collapse collapse"
                :class="{ show: secaoAberta === 'edital' }"
              >
                <form class="accordion-body" @submit.prevent="salvarEdital">
                  <fieldset :disabled="concursoArquivado || salvando">
                    <label class="form-label" for="titulo-edital">Titulo</label>
                    <input
                      id="titulo-edital"
                      v-model="formularioEdital.titulo"
                      class="form-control mb-2"
                      required
                    />
                    <div class="row g-2">
                      <div class="col-6">
                        <label class="form-label" for="numero-edital">
                          Numero
                        </label>
                        <input
                          id="numero-edital"
                          v-model="formularioEdital.numero"
                          class="form-control"
                        />
                      </div>
                      <div class="col-6">
                        <label class="form-label" for="ano-edital">Ano</label>
                        <input
                          id="ano-edital"
                          v-model.number="formularioEdital.ano"
                          class="form-control"
                          type="number"
                          min="1"
                        />
                      </div>
                    </div>
                    <label class="form-label mt-2" for="url-edital">
                      Endereco do documento
                    </label>
                    <input
                      id="url-edital"
                      v-model="formularioEdital.enderecoDoDocumento"
                      class="form-control mb-3"
                      type="url"
                    />
                    <label class="form-label" for="data-edital">
                      Data de publicacao
                    </label>
                    <input
                      id="data-edital"
                      v-model="formularioEdital.dataDePublicacao"
                      class="form-control mb-2"
                      type="date"
                    />
                    <label class="form-label" for="descricao-edital">
                      Descricao
                    </label>
                    <textarea
                      id="descricao-edital"
                      v-model="formularioEdital.descricao"
                      class="form-control mb-3"
                      rows="2"
                    ></textarea>
                    <button class="btn btn-primary">Salvar edital</button>
                    <button
                      v-if="formularioEdital.identificador"
                      class="btn btn-link"
                      type="button"
                      @click="limparEdital"
                    >
                      Cancelar
                    </button>
                  </fieldset>
                </form>
              </div>
            </div>

            <div class="accordion-item">
              <h2 class="accordion-header">
                <button
                  class="accordion-button"
                  :class="{ collapsed: secaoAberta !== 'cargo' }"
                  type="button"
                  :aria-expanded="secaoAberta === 'cargo'"
                  aria-controls="formulario-cargo"
                  @click="alternarSecao('cargo')"
                >
                  {{ formularioCargo.identificador ? 'Editar' : 'Adicionar' }}
                  cargo
                </button>
              </h2>
              <div
                id="formulario-cargo"
                class="accordion-collapse collapse"
                :class="{ show: secaoAberta === 'cargo' }"
              >
                <form class="accordion-body" @submit.prevent="salvarCargo">
                  <fieldset :disabled="concursoArquivado || salvando">
                    <label class="form-label" for="nome-cargo">Nome</label>
                    <input
                      id="nome-cargo"
                      v-model="formularioCargo.nome"
                      class="form-control mb-2"
                      required
                    />
                    <div class="row g-2">
                      <div class="col-6">
                        <label class="form-label" for="area-cargo">Area</label>
                        <input
                          id="area-cargo"
                          v-model="formularioCargo.area"
                          class="form-control"
                        />
                      </div>
                      <div class="col-6">
                        <label class="form-label" for="especialidade-cargo">
                          Especialidade
                        </label>
                        <input
                          id="especialidade-cargo"
                          v-model="formularioCargo.especialidade"
                          class="form-control"
                        />
                      </div>
                    </div>
                    <label class="form-label" for="nivel-cargo">
                      Nivel de escolaridade
                    </label>
                    <select
                      id="nivel-cargo"
                      v-model="formularioCargo.nivelDeEscolaridade"
                      class="form-select mb-2"
                    >
                      <option value="NAO_INFORMADO">Nao informado</option>
                      <option value="FUNDAMENTAL">Fundamental</option>
                      <option value="MEDIO">Medio</option>
                      <option value="TECNICO">Tecnico</option>
                      <option value="SUPERIOR">Superior</option>
                    </select>
                    <label class="form-label" for="ordem-cargo">Ordem</label>
                    <input
                      id="ordem-cargo"
                      v-model.number="formularioCargo.ordem"
                      class="form-control mb-3"
                      type="number"
                      min="1"
                      required
                    />
                    <button class="btn btn-primary">Salvar cargo</button>
                    <button
                      v-if="formularioCargo.identificador"
                      class="btn btn-link"
                      type="button"
                      @click="limparCargo"
                    >
                      Cancelar
                    </button>
                  </fieldset>
                </form>
              </div>
            </div>

            <div class="accordion-item">
              <h2 class="accordion-header">
                <button
                  class="accordion-button"
                  :class="{ collapsed: secaoAberta !== 'prova' }"
                  type="button"
                  :aria-expanded="secaoAberta === 'prova'"
                  aria-controls="formulario-prova"
                  @click="alternarSecao('prova')"
                >
                  {{ formularioProva.identificador ? 'Editar' : 'Adicionar' }}
                  prova
                </button>
              </h2>
              <div
                id="formulario-prova"
                class="accordion-collapse collapse"
                :class="{ show: secaoAberta === 'prova' }"
              >
                <form class="accordion-body" @submit.prevent="salvarProva">
                  <fieldset
                    :disabled="
                      concursoArquivado || salvando || cargos.length === 0
                    "
                  >
                    <label class="form-label" for="cargo-da-prova">Cargo</label>
                    <select
                      id="cargo-da-prova"
                      v-model="formularioProva.identificadorDoCargo"
                      class="form-select mb-2"
                      required
                    >
                      <option
                        v-for="cargo in cargos"
                        :key="cargo.identificador"
                        :value="cargo.identificador"
                      >
                        {{ cargo.nome }}
                      </option>
                    </select>
                    <label class="form-label" for="nome-prova">Nome</label>
                    <input
                      id="nome-prova"
                      v-model="formularioProva.nome"
                      class="form-control mb-2"
                      required
                    />
                    <div class="row g-2">
                      <div class="col-6">
                        <label class="form-label" for="tipo-prova">Tipo</label>
                        <select
                          id="tipo-prova"
                          v-model="formularioProva.tipo"
                          class="form-select"
                        >
                          <option value="OBJETIVA">Objetiva</option>
                          <option value="DISCURSIVA">Discursiva</option>
                          <option value="PRATICA">Pratica</option>
                          <option value="TITULOS">Titulos</option>
                          <option value="OUTRA">Outra</option>
                        </select>
                      </div>
                      <div class="col-6">
                        <label class="form-label" for="ordem-prova"
                          >Ordem</label
                        >
                        <input
                          id="ordem-prova"
                          v-model.number="formularioProva.ordem"
                          class="form-control"
                          type="number"
                          min="1"
                        />
                      </div>
                    </div>
                    <label class="form-label mt-2" for="carater-prova">
                      Carater
                    </label>
                    <select
                      id="carater-prova"
                      v-model="formularioProva.carater"
                      class="form-select mb-3"
                    >
                      <option value="NAO_INFORMADO">Nao informado</option>
                      <option value="ELIMINATORIO">Eliminatorio</option>
                      <option value="CLASSIFICATORIO">Classificatorio</option>
                      <option value="ELIMINATORIO_E_CLASSIFICATORIO">
                        Eliminatorio e classificatorio
                      </option>
                    </select>
                    <label class="form-label mt-2" for="data-prova">
                      Data e hora prevista
                    </label>
                    <input
                      id="data-prova"
                      v-model="formularioProva.dataHoraPrevista"
                      class="form-control mb-2"
                      type="datetime-local"
                    />
                    <div class="row g-2">
                      <div class="col-6">
                        <label class="form-label" for="duracao-prova">
                          Duracao em minutos
                        </label>
                        <input
                          id="duracao-prova"
                          v-model.number="formularioProva.duracaoEmMinutos"
                          class="form-control"
                          type="number"
                          min="1"
                        />
                      </div>
                      <div class="col-6">
                        <label class="form-label" for="questoes-prova">
                          Questoes
                        </label>
                        <input
                          id="questoes-prova"
                          v-model.number="formularioProva.quantidadeDeQuestoes"
                          class="form-control"
                          type="number"
                          min="1"
                        />
                      </div>
                      <div class="col-6">
                        <label class="form-label" for="maxima-prova">
                          Pontuacao maxima
                        </label>
                        <input
                          id="maxima-prova"
                          v-model.number="formularioProva.pontuacaoMaxima"
                          class="form-control"
                          type="number"
                          min="0.01"
                          step="0.01"
                        />
                      </div>
                      <div class="col-6">
                        <label class="form-label" for="minima-prova">
                          Pontuacao minima
                        </label>
                        <input
                          id="minima-prova"
                          v-model.number="formularioProva.pontuacaoMinima"
                          class="form-control"
                          type="number"
                          min="0.01"
                          step="0.01"
                        />
                      </div>
                    </div>
                    <button class="btn btn-primary">Salvar prova</button>
                    <button
                      v-if="formularioProva.identificador"
                      class="btn btn-link"
                      type="button"
                      @click="limparProva"
                    >
                      Cancelar
                    </button>
                  </fieldset>
                </form>
              </div>
            </div>

            <div class="accordion-item">
              <h2 class="accordion-header">
                <button
                  class="accordion-button"
                  :class="{ collapsed: secaoAberta !== 'grupo' }"
                  type="button"
                  :aria-expanded="secaoAberta === 'grupo'"
                  aria-controls="formulario-grupo"
                  @click="alternarSecao('grupo')"
                >
                  {{ formularioGrupo.identificador ? 'Editar' : 'Adicionar' }}
                  grupo
                </button>
              </h2>
              <div
                id="formulario-grupo"
                class="accordion-collapse collapse"
                :class="{ show: secaoAberta === 'grupo' }"
              >
                <form class="accordion-body" @submit.prevent="salvarGrupo">
                  <fieldset
                    :disabled="
                      concursoArquivado ||
                      salvando ||
                      todasAsProvas.length === 0
                    "
                  >
                    <label class="form-label" for="prova-do-grupo">Prova</label>
                    <select
                      id="prova-do-grupo"
                      v-model="formularioGrupo.identificadorDaProva"
                      class="form-select mb-2"
                      required
                    >
                      <option
                        v-for="prova in todasAsProvas"
                        :key="prova.identificador"
                        :value="prova.identificador"
                      >
                        {{ prova.nome }}
                      </option>
                    </select>
                    <label class="form-label" for="nome-grupo">Nome</label>
                    <input
                      id="nome-grupo"
                      v-model="formularioGrupo.nome"
                      class="form-control mb-2"
                      required
                    />
                    <label class="form-label" for="ordem-grupo">Ordem</label>
                    <input
                      id="ordem-grupo"
                      v-model.number="formularioGrupo.ordem"
                      class="form-control mb-3"
                      type="number"
                      min="1"
                    />
                    <div class="row g-2 mb-3">
                      <div class="col-4">
                        <label class="form-label" for="questoes-grupo">
                          Questoes
                        </label>
                        <input
                          id="questoes-grupo"
                          v-model.number="formularioGrupo.quantidadeDeQuestoes"
                          class="form-control"
                          type="number"
                          min="1"
                        />
                      </div>
                      <div class="col-4">
                        <label class="form-label" for="maxima-grupo">
                          Maxima
                        </label>
                        <input
                          id="maxima-grupo"
                          v-model.number="formularioGrupo.pontuacaoMaxima"
                          class="form-control"
                          type="number"
                          min="0.01"
                          step="0.01"
                        />
                      </div>
                      <div class="col-4">
                        <label class="form-label" for="minima-grupo">
                          Minima
                        </label>
                        <input
                          id="minima-grupo"
                          v-model.number="formularioGrupo.pontuacaoMinima"
                          class="form-control"
                          type="number"
                          min="0.01"
                          step="0.01"
                        />
                      </div>
                    </div>
                    <button class="btn btn-primary">Salvar grupo</button>
                    <button
                      v-if="formularioGrupo.identificador"
                      class="btn btn-link"
                      type="button"
                      @click="limparGrupo"
                    >
                      Cancelar
                    </button>
                  </fieldset>
                </form>
              </div>
            </div>

            <div class="accordion-item">
              <h2 class="accordion-header">
                <button
                  class="accordion-button"
                  :class="{ collapsed: secaoAberta !== 'materia' }"
                  type="button"
                  :aria-expanded="secaoAberta === 'materia'"
                  aria-controls="formulario-materia-da-prova"
                  @click="alternarSecao('materia')"
                >
                  {{ formularioMateria.identificador ? 'Editar' : 'Adicionar' }}
                  materia
                </button>
              </h2>
              <div
                id="formulario-materia-da-prova"
                class="accordion-collapse collapse"
                :class="{ show: secaoAberta === 'materia' }"
              >
                <form class="accordion-body" @submit.prevent="salvarMateria">
                  <fieldset
                    :disabled="
                      concursoArquivado ||
                      salvando ||
                      todosOsGrupos.length === 0 ||
                      materiasDisponiveis.length === 0
                    "
                  >
                    <label class="form-label" for="grupo-da-materia"
                      >Grupo</label
                    >
                    <select
                      id="grupo-da-materia"
                      v-model="formularioMateria.identificadorDoGrupo"
                      class="form-select mb-2"
                      required
                    >
                      <option
                        v-for="grupo in todosOsGrupos"
                        :key="grupo.identificador"
                        :value="grupo.identificador"
                      >
                        {{ grupo.nome }}
                      </option>
                    </select>
                    <label class="form-label" for="materia-do-grupo">
                      Materia do catalogo
                    </label>
                    <select
                      id="materia-do-grupo"
                      v-model="formularioMateria.identificadorDaMateria"
                      class="form-select mb-2"
                      :disabled="Boolean(formularioMateria.identificador)"
                      required
                    >
                      <option value="" disabled>Selecione</option>
                      <option
                        v-for="materia in materiasDisponiveis"
                        :key="materia.identificador"
                        :value="materia.identificador"
                      >
                        {{ materia.nome }}
                      </option>
                    </select>
                    <div class="row g-2">
                      <div class="col-6">
                        <label class="form-label" for="ordem-materia">
                          Ordem
                        </label>
                        <input
                          id="ordem-materia"
                          v-model.number="formularioMateria.ordem"
                          class="form-control"
                          type="number"
                          min="1"
                        />
                      </div>
                      <div class="col-6">
                        <label class="form-label" for="peso-materia"
                          >Peso</label
                        >
                        <input
                          id="peso-materia"
                          v-model.number="formularioMateria.peso"
                          class="form-control"
                          type="number"
                          min="0.0001"
                          step="0.0001"
                        />
                      </div>
                    </div>
                    <div class="row g-2 mt-1">
                      <div class="col-6">
                        <label class="form-label" for="questoes-materia">
                          Questoes
                        </label>
                        <input
                          id="questoes-materia"
                          v-model.number="
                            formularioMateria.quantidadeDeQuestoes
                          "
                          class="form-control"
                          type="number"
                          min="1"
                        />
                      </div>
                      <div class="col-6">
                        <label class="form-label" for="pontuacao-materia">
                          Pontuacao maxima
                        </label>
                        <input
                          id="pontuacao-materia"
                          v-model.number="formularioMateria.pontuacaoMaxima"
                          class="form-control"
                          type="number"
                          min="0.01"
                          step="0.01"
                        />
                      </div>
                    </div>
                    <button class="btn btn-primary mt-3">Salvar materia</button>
                    <button
                      v-if="formularioMateria.identificador"
                      class="btn btn-link mt-3"
                      type="button"
                      @click="limparMateria"
                    >
                      Cancelar
                    </button>
                  </fieldset>
                </form>
              </div>
            </div>
          </div>
        </aside>
      </div>
    </template>
  </main>
</template>
