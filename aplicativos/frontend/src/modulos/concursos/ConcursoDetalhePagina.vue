<script setup lang="ts">
import {
  computed,
  nextTick,
  onBeforeUnmount,
  onMounted,
  reactive,
  ref,
} from 'vue'
import { useRoute, useRouter } from 'vue-router'

import GavetaLateral from '@/compartilhado/componentes/GavetaLateral.vue'
import type { Materia, Topico } from '@/modulos/materias/apiDeConteudos'
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
import {
  alterarItemDoEdital,
  criarItemDoEdital,
  criarMapeamentoDoItem,
  excluirItemDoEdital,
  excluirMapeamentoDoItem,
  listarItensDoEdital,
  listarMapeamentosDoItem,
  listarTopicosDisponiveis,
  type ItemDoEdital,
  type MapeamentoDeItem,
} from './apiDeConteudoProgramatico'

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
const itensPorMateriaDaProva = reactive<Record<string, ItemDoEdital[]>>({})
const mapeamentosPorItem = reactive<Record<string, MapeamentoDeItem[]>>({})
const topicosPorMateria = reactive<Record<string, Topico[]>>({})
const carregando = ref(true)
const salvando = ref(false)
const erro = ref('')
const secaoAberta = ref('edital')
const gavetaDeMapeamentoAberta = ref(false)
const gavetaDaEstruturaAberta = ref(false)
const abaDoConcurso = ref<'visao' | 'conteudo'>(
  rota.query.foco === 'mapeamentos' ? 'conteudo' : 'visao',
)
const abasDoConcurso = ['visao', 'conteudo'] as const
const identificadorDaMateriaSelecionada = ref('')
const cancelamento = new AbortController()

async function navegarEntreAbas(evento: KeyboardEvent) {
  const indiceAtual = abasDoConcurso.indexOf(abaDoConcurso.value)
  let proximoIndice: number

  if (evento.key === 'ArrowRight' || evento.key === 'ArrowDown') {
    proximoIndice = (indiceAtual + 1) % abasDoConcurso.length
  } else if (evento.key === 'ArrowLeft' || evento.key === 'ArrowUp') {
    proximoIndice =
      (indiceAtual - 1 + abasDoConcurso.length) % abasDoConcurso.length
  } else if (evento.key === 'Home') {
    proximoIndice = 0
  } else if (evento.key === 'End') {
    proximoIndice = abasDoConcurso.length - 1
  } else {
    return
  }

  evento.preventDefault()
  const proximaAba = abasDoConcurso[proximoIndice] ?? 'visao'
  abaDoConcurso.value = proximaAba
  await nextTick()
  document.getElementById(`aba-do-concurso-${proximaAba}`)?.focus()
}

const rotulosDaSituacao: Record<string, string> = {
  PLANEJADO: 'Planejado',
  EDITAL_PUBLICADO: 'Edital publicado',
  INSCRICOES_ABERTAS: 'Inscrições abertas',
  EM_ANDAMENTO: 'Em andamento',
  ENCERRADO: 'Encerrado',
  SUSPENSO: 'Suspenso',
  CANCELADO: 'Cancelado',
  ARQUIVADO: 'Arquivado',
}
const rotulosDoNivel: Record<string, string> = {
  FUNDAMENTAL: 'Ensino fundamental',
  MEDIO: 'Ensino médio',
  TECNICO: 'Ensino técnico',
  SUPERIOR: 'Ensino superior',
  NAO_INFORMADO: 'Não informado',
}
const rotulosDoTipoDeProva: Record<string, string> = {
  OBJETIVA: 'Objetiva',
  DISCURSIVA: 'Discursiva',
  PRATICA: 'Prática',
  TITULOS: 'Títulos',
  OUTRA: 'Outra',
}
const rotulosDoCarater: Record<string, string> = {
  ELIMINATORIO: 'Eliminatória',
  CLASSIFICATORIO: 'Classificatória',
  ELIMINATORIO_E_CLASSIFICATORIO: 'Eliminatória e classificatória',
  NAO_INFORMADO: 'Não informado',
}

function rotuloDoDominio(valor: string, rotulos: Record<string, string>) {
  return rotulos[valor] ?? valor.toLocaleLowerCase('pt-BR')
}

const concursoArquivado = computed(
  () => concurso.value?.situacao === 'ARQUIVADO',
)
const todasAsProvas = computed(() => Object.values(provasPorCargo).flat())
const todosOsGrupos = computed(() => Object.values(gruposPorProva).flat())
const todasAsMateriasDaProva = computed(() =>
  Object.values(materiasPorGrupo).flat(),
)
const todosOsItens = computed(() =>
  Object.values(itensPorMateriaDaProva).flat(),
)
const itensSemMapeamento = computed(() =>
  todosOsItens.value.filter(
    (item) => (mapeamentosPorItem[item.identificador] ?? []).length === 0,
  ),
)
const editalPrincipal = computed(() =>
  editais.value.find((edital) => edital.principal),
)
const cargoSelecionado = computed(() =>
  cargos.value.find((cargo) => cargo.selecionado),
)
const materiaDaProvaSelecionada = computed(() =>
  todasAsMateriasDaProva.value.find(
    (materia) =>
      materia.identificador === identificadorDaMateriaSelecionada.value,
  ),
)
const itensDaMateriaSelecionada = computed(() =>
  [
    ...(itensPorMateriaDaProva[identificadorDaMateriaSelecionada.value] ?? []),
  ].sort(
    (primeiro, segundo) =>
      primeiro.ordem - segundo.ordem ||
      primeiro.descricaoOriginal.localeCompare(segundo.descricaoOriginal),
  ),
)
const percentualDeMapeamento = computed(() =>
  todosOsItens.value.length
    ? Math.round(
        ((todosOsItens.value.length - itensSemMapeamento.value.length) /
          todosOsItens.value.length) *
          100,
      )
    : 0,
)
const estruturaInicialCompleta = computed(
  () =>
    Boolean(editalPrincipal.value) &&
    Boolean(cargoSelecionado.value) &&
    todasAsProvas.value.length > 0 &&
    todosOsGrupos.value.length > 0 &&
    todasAsMateriasDaProva.value.length > 0,
)
const proximoPasso = computed(() => {
  if (!estruturaInicialCompleta.value)
    return {
      etiqueta: 'Estrutura inicial',
      titulo: 'Complete a estrutura do concurso',
      descricao:
        'Cadastre edital, cargo, prova, grupos e matérias no contexto correto.',
      acao: 'estrutura' as const,
      rotuloDaAcao: 'Continuar estrutura',
    }
  if (todosOsItens.value.length === 0)
    return {
      etiqueta: 'Conteúdo programático',
      titulo: 'Transcreva os itens oficiais do edital',
      descricao:
        'A redação oficial conecta a estrutura da prova ao seu catálogo pessoal.',
      acao: 'item' as const,
      rotuloDaAcao: 'Adicionar primeiro item',
    }
  if (itensSemMapeamento.value.length > 0)
    return {
      etiqueta: 'Próximo passo recomendado',
      titulo: 'Conclua o mapeamento do edital',
      descricao: `${itensSemMapeamento.value.length} ${
        itensSemMapeamento.value.length === 1
          ? 'item ainda não está relacionado'
          : 'itens ainda não estão relacionados'
      } a tópicos do seu catálogo.`,
      acao: 'mapeamento' as const,
      rotuloDaAcao: 'Continuar mapeamento',
    }
  return {
    etiqueta: 'Estrutura pronta',
    titulo: 'Siga acompanhando sua jornada',
    descricao:
      'Os itens oficiais estão mapeados e o progresso será atualizado pelos estudos ativos.',
    acao: 'dashboard' as const,
    rotuloDaAcao: 'Ver visão geral',
  }
})

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
const formularioItem = reactive<{
  identificador?: string
  identificadorDaMateriaDaProva: string
  identificadorDoEdital: string
  descricaoOriginal: string
  identificadorDoItemPai: string
  ordem: number
}>({
  identificadorDaMateriaDaProva: '',
  identificadorDoEdital: '',
  descricaoOriginal: '',
  identificadorDoItemPai: '',
  ordem: 1,
})
const formularioMapeamento = reactive({
  identificadorDoItem: '',
  identificadorDoTopico: '',
})

const itensParaSelecaoDoMapeamento = computed(() => {
  const selecionado = todosOsItens.value.find(
    (item) => item.identificador === formularioMapeamento.identificadorDoItem,
  )
  if (
    selecionado &&
    !itensSemMapeamento.value.some(
      (item) => item.identificador === selecionado.identificador,
    )
  )
    return [selecionado, ...itensSemMapeamento.value]
  return itensSemMapeamento.value.length
    ? itensSemMapeamento.value
    : todosOsItens.value
})

const itemSelecionadoParaMapeamento = computed(() =>
  todosOsItens.value.find(
    (item) => item.identificador === formularioMapeamento.identificadorDoItem,
  ),
)
const materiaDaProvaDoItemSelecionado = computed(() =>
  todasAsMateriasDaProva.value.find(
    (materia) =>
      materia.identificador ===
      itemSelecionadoParaMapeamento.value?.identificadorDaMateriaDaProva,
  ),
)
const topicosParaMapeamento = computed(
  () =>
    topicosPorMateria[
      materiaDaProvaDoItemSelecionado.value?.identificadorDaMateria ?? ''
    ] ?? [],
)
const paisDisponiveisParaOItem = computed(() =>
  (
    itensPorMateriaDaProva[formularioItem.identificadorDaMateriaDaProva] ?? []
  ).filter(
    (item) =>
      item.identificador !== formularioItem.identificador &&
      item.identificadorDoEdital === formularioItem.identificadorDoEdital,
  ),
)

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
        : 'Não foi possível carregar o concurso.'
  } finally {
    carregando.value = false
  }
}

async function carregarDescendentes(cargosAtuais: Cargo[]) {
  limparMapa(provasPorCargo)
  limparMapa(gruposPorProva)
  limparMapa(materiasPorGrupo)
  limparMapa(itensPorMateriaDaProva)
  limparMapa(mapeamentosPorItem)
  limparMapa(topicosPorMateria)
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
  const materiasDaProva = paresDeMaterias.flatMap((par) => par.materias)
  const paresDeItens = await Promise.all(
    materiasDaProva.map(async (materia) => ({
      materiaDaProva: materia.identificador,
      itens: await listarItensDoEdital(
        materia.identificador,
        cancelamento.signal,
      ),
    })),
  )
  for (const par of paresDeItens)
    itensPorMateriaDaProva[par.materiaDaProva] = par.itens
  const itens = paresDeItens.flatMap((par) => par.itens)
  const paresDeMapeamentos = await Promise.all(
    itens.map(async (item) => ({
      item: item.identificador,
      mapeamentos: await listarMapeamentosDoItem(
        item.identificador,
        cancelamento.signal,
      ),
    })),
  )
  for (const par of paresDeMapeamentos)
    mapeamentosPorItem[par.item] = par.mapeamentos
  const materiasDoCatalogo = [
    ...new Set(
      materiasDaProva.map((materia) => materia.identificadorDaMateria),
    ),
  ]
  const paresDeTopicos = await Promise.all(
    materiasDoCatalogo.map(async (materia) => ({
      materia,
      topicos: (await listarTopicosDisponiveis(materia, cancelamento.signal))
        .itens,
    })),
  )
  for (const par of paresDeTopicos) topicosPorMateria[par.materia] = par.topicos
  if (!formularioProva.identificadorDoCargo && cargosAtuais[0])
    formularioProva.identificadorDoCargo = cargosAtuais[0].identificador
  if (!formularioGrupo.identificadorDaProva && provas[0])
    formularioGrupo.identificadorDaProva = provas[0].identificador
  if (!formularioMateria.identificadorDoGrupo && grupos[0])
    formularioMateria.identificadorDoGrupo = grupos[0].identificador
  if (!formularioItem.identificadorDaMateriaDaProva && materiasDaProva[0])
    formularioItem.identificadorDaMateriaDaProva =
      materiasDaProva[0].identificador
  if (!formularioItem.identificadorDoEdital && editais.value[0])
    formularioItem.identificadorDoEdital = editais.value[0].identificador
  if (!formularioMapeamento.identificadorDoItem && itens[0])
    formularioMapeamento.identificadorDoItem = itens[0].identificador
  if (
    !materiasDaProva.some(
      (materia) =>
        materia.identificador === identificadorDaMateriaSelecionada.value,
    )
  )
    identificadorDaMateriaSelecionada.value =
      materiasDaProva[0]?.identificador ?? ''
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
        : 'Não foi possível concluir a operação.'
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
    dataHoraPrevista: prova.dataHoraPrevista
      ? dataHoraParaCampoLocal(prova.dataHoraPrevista)
      : '',
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

function editalDoItem(item: ItemDoEdital) {
  return (
    editais.value.find(
      (edital) => edital.identificador === item.identificadorDoEdital,
    )?.titulo ?? 'Edital não encontrado'
  )
}

function nivelDoItem(item: ItemDoEdital, itens: ItemDoEdital[]) {
  let nivel = 0
  let pai = item.identificadorDoItemPai
  const visitados = new Set<string>()
  while (pai && !visitados.has(pai)) {
    visitados.add(pai)
    nivel += 1
    pai = itens.find(
      (candidato) => candidato.identificador === pai,
    )?.identificadorDoItemPai
  }
  return nivel
}

function editarItem(item: ItemDoEdital) {
  Object.assign(formularioItem, {
    identificador: item.identificador,
    identificadorDaMateriaDaProva: item.identificadorDaMateriaDaProva,
    identificadorDoEdital: item.identificadorDoEdital,
    descricaoOriginal: item.descricaoOriginal,
    identificadorDoItemPai: item.identificadorDoItemPai ?? '',
    ordem: item.ordem,
  })
  secaoAberta.value = 'item'
  gavetaDaEstruturaAberta.value = true
}

function abrirMapeamento(item?: ItemDoEdital) {
  const itemParaMapear =
    item ?? itensSemMapeamento.value[0] ?? todosOsItens.value[0]
  if (itemParaMapear) {
    formularioMapeamento.identificadorDoItem = itemParaMapear.identificador
    formularioMapeamento.identificadorDoTopico = ''
  }
  abaDoConcurso.value = 'conteudo'
  gavetaDaEstruturaAberta.value = false
  gavetaDeMapeamentoAberta.value = true
}

function abrirEdicaoDaEstrutura(secao = 'edital') {
  secaoAberta.value = secao
  gavetaDaEstruturaAberta.value = true
}

function executarProximoPasso() {
  if (proximoPasso.value.acao === 'estrutura') {
    abrirEdicaoDaEstrutura('edital')
    return
  }
  if (proximoPasso.value.acao === 'item') {
    abaDoConcurso.value = 'conteudo'
    abrirCadastroDeItem()
    return
  }
  if (proximoPasso.value.acao === 'mapeamento') {
    abaDoConcurso.value = 'conteudo'
    abrirMapeamento()
    return
  }
  void roteador.push('/dashboard')
}

function abrirCadastroDeItem() {
  limparItem()
  if (materiaDaProvaSelecionada.value)
    formularioItem.identificadorDaMateriaDaProva =
      materiaDaProvaSelecionada.value.identificador
  if (editalPrincipal.value)
    formularioItem.identificadorDoEdital = editalPrincipal.value.identificador
  abrirEdicaoDaEstrutura('item')
}

function nomeDaProvaDaMateria(materia: MateriaDaProva) {
  const grupo = todosOsGrupos.value.find(
    (item) => item.identificador === materia.identificadorDoGrupoDeConteudo,
  )
  return (
    todasAsProvas.value.find(
      (prova) => prova.identificador === grupo?.identificadorDaProva,
    )?.nome ?? 'Prova'
  )
}

function nomeDoGrupoDaMateria(materia: MateriaDaProva) {
  return (
    todosOsGrupos.value.find(
      (grupo) => grupo.identificador === materia.identificadorDoGrupoDeConteudo,
    )?.nome ?? 'Grupo de conteúdo'
  )
}

function limparItem() {
  Object.assign(formularioItem, {
    identificador: undefined,
    descricaoOriginal: '',
    identificadorDoItemPai: '',
    ordem: 1,
  })
}

async function salvarItem() {
  const dados = {
    descricaoOriginal: formularioItem.descricaoOriginal,
    identificadorDoItemPai: formularioItem.identificadorDoItemPai || undefined,
    ordem: Number(formularioItem.ordem),
  }
  const sucesso = await executar(() =>
    formularioItem.identificador
      ? alterarItemDoEdital(formularioItem.identificador, dados)
      : criarItemDoEdital(formularioItem.identificadorDaMateriaDaProva, {
          ...dados,
          identificadorDoEdital: formularioItem.identificadorDoEdital,
        }),
  )
  if (sucesso) limparItem()
}

async function salvarMapeamento() {
  const sucesso = await executar(() =>
    criarMapeamentoDoItem(
      formularioMapeamento.identificadorDoItem,
      formularioMapeamento.identificadorDoTopico,
    ),
  )
  if (sucesso) {
    formularioMapeamento.identificadorDoTopico = ''
    gavetaDeMapeamentoAberta.value = false
  }
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

function dataHoraParaCampoLocal(valor: string) {
  const data = new Date(valor)
  data.setMinutes(data.getMinutes() - data.getTimezoneOffset())
  return data.toISOString().slice(0, 16)
}

function formatarData(valor?: string) {
  if (!valor) return 'Não definida'
  return new Intl.DateTimeFormat('pt-BR').format(new Date(`${valor}T12:00:00`))
}

function alternarSecao(secao: string) {
  secaoAberta.value = secaoAberta.value === secao ? '' : secao
}

onMounted(async () => {
  await carregar()
  if (rota.query.foco === 'mapeamentos') abrirMapeamento()
})
onBeforeUnmount(() => cancelamento.abort())
</script>

<template>
  <main class="pagina-da-jornada pagina-do-concurso">
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
      v-if="rota.query.novo === 'concluido'"
      class="alert alert-success"
      role="status"
    >
      Concurso criado. Complete as próximas seções no seu ritmo.
    </p>

    <div v-if="carregando" class="text-center py-5" aria-live="polite">
      <div class="spinner-border text-primary mb-3" role="status">
        <span class="visually-hidden">Carregando estrutura</span>
      </div>
      <p>Carregando estrutura do concurso...</p>
    </div>

    <template v-else-if="concurso">
      <header class="cabecalho-da-pagina">
        <div>
          <p class="sobretitulo-da-pagina">Seu objetivo ativo</p>
          <div class="d-flex gap-2 my-2">
            <span class="badge etiqueta-neutra">
              {{ rotuloDoDominio(concurso.situacao, rotulosDaSituacao) }}
            </span>
            <span v-if="concurso.ativo" class="selo-de-objetivo-ativo">
              <i aria-hidden="true"></i>
              Ativo
            </span>
          </div>
          <h1>{{ concurso.nome }}</h1>
          <p>
            {{ concurso.orgao || 'Órgão não informado' }}
            <span v-if="concurso.banca"> · {{ concurso.banca }}</span>
          </p>
        </div>
        <div class="acoes-do-cabecalho">
          <button
            class="btn btn-outline-primary"
            type="button"
            :disabled="todosOsItens.length === 0"
            @click="abrirMapeamento()"
          >
            <i class="bi bi-diagram-3 me-2" aria-hidden="true"></i>
            Mapear itens
            <span
              v-if="itensSemMapeamento.length"
              class="badge text-bg-warning ms-1"
            >
              {{ itensSemMapeamento.length }}
            </span>
          </button>
          <button
            class="btn btn-outline-secondary"
            type="button"
            :disabled="salvando"
            @click="alternarArquivamento"
          >
            {{ concursoArquivado ? 'Restaurar concurso' : 'Arquivar concurso' }}
          </button>
        </div>
      </header>

      <nav class="passos-do-concurso" aria-label="Etapas do concurso">
        <button
          type="button"
          :class="{
            concluido: Boolean(editalPrincipal && cargoSelecionado),
            atual: !editalPrincipal || !cargoSelecionado,
          }"
          @click="abrirEdicaoDaEstrutura('edital')"
        >
          <i
            v-if="editalPrincipal && cargoSelecionado"
            class="bi bi-check2"
            aria-hidden="true"
          ></i>
          <i v-else>1</i>
          <span><b>1. Objetivo</b><small>Concurso, edital e cargo</small></span>
        </button>
        <button
          type="button"
          :class="{
            concluido: estruturaInicialCompleta,
            atual:
              Boolean(editalPrincipal && cargoSelecionado) &&
              !estruturaInicialCompleta,
          }"
          @click="abrirEdicaoDaEstrutura('prova')"
        >
          <i
            v-if="estruturaInicialCompleta"
            class="bi bi-check2"
            aria-hidden="true"
          ></i>
          <i v-else>2</i>
          <span
            ><b>2. Estrutura da prova</b
            ><small>Provas, grupos e matérias</small></span
          >
        </button>
        <button
          type="button"
          :class="{
            concluido:
              todosOsItens.length > 0 && itensSemMapeamento.length === 0,
            atual:
              estruturaInicialCompleta &&
              (todosOsItens.length === 0 || itensSemMapeamento.length > 0),
          }"
          @click="abaDoConcurso = 'conteudo'"
        >
          <i
            v-if="todosOsItens.length > 0 && itensSemMapeamento.length === 0"
            class="bi bi-check2"
            aria-hidden="true"
          ></i>
          <i v-else>3</i>
          <span>
            <b>3. Conteúdo programático</b>
            <small>{{ itensSemMapeamento.length }} itens sem mapeamento</small>
          </span>
        </button>
        <RouterLink
          to="/dashboard"
          :class="{
            atual: todosOsItens.length > 0 && itensSemMapeamento.length === 0,
          }"
        >
          <i>4</i>
          <span
            ><b>4. Revisar e acompanhar</b><small>Voltar à jornada</small></span
          >
        </RouterLink>
      </nav>

      <section v-if="concursoArquivado" class="alert alert-warning">
        Restaure o concurso para alterar sua estrutura.
      </section>

      <nav
        class="abas-do-concurso abas-modernas-do-concurso"
        aria-label="Visualização do concurso"
        role="tablist"
      >
        <button
          id="aba-do-concurso-visao"
          type="button"
          role="tab"
          :class="{ ativo: abaDoConcurso === 'visao' }"
          :aria-current="abaDoConcurso === 'visao' ? 'page' : undefined"
          :aria-selected="abaDoConcurso === 'visao'"
          aria-controls="painel-da-visao-do-concurso"
          :tabindex="abaDoConcurso === 'visao' ? 0 : -1"
          @click="abaDoConcurso = 'visao'"
          @keydown="navegarEntreAbas"
        >
          Visão do concurso
        </button>
        <button
          id="aba-do-concurso-conteudo"
          type="button"
          role="tab"
          :class="{ ativo: abaDoConcurso === 'conteudo' }"
          :aria-current="abaDoConcurso === 'conteudo' ? 'page' : undefined"
          :aria-selected="abaDoConcurso === 'conteudo'"
          aria-controls="painel-do-conteudo-programatico"
          :tabindex="abaDoConcurso === 'conteudo' ? 0 : -1"
          @click="abaDoConcurso = 'conteudo'"
          @keydown="navegarEntreAbas"
        >
          Conteúdo programático
          <span>{{ itensSemMapeamento.length }}</span>
        </button>
      </nav>

      <section
        v-if="abaDoConcurso === 'visao'"
        id="painel-da-visao-do-concurso"
        class="grade-da-visao-do-concurso painel-da-visao-do-concurso"
        role="tabpanel"
        aria-labelledby="aba-do-concurso-visao"
      >
        <article class="card proximo-passo-do-concurso">
          <p class="sobretitulo-da-pagina">{{ proximoPasso.etiqueta }}</p>
          <h2 class="titulo-editorial">{{ proximoPasso.titulo }}</h2>
          <p>{{ proximoPasso.descricao }}</p>
          <button
            class="btn btn-primary align-self-start"
            type="button"
            @click="executarProximoPasso"
          >
            {{ proximoPasso.rotuloDaAcao }}
            <i class="bi bi-arrow-right ms-2" aria-hidden="true"></i>
          </button>
        </article>

        <article class="card dados-resumidos-do-concurso">
          <header class="cabecalho-do-cartao-da-jornada">
            <div>
              <span class="rotulo-discreto">Dados principais</span>
              <h2>Seu objetivo</h2>
            </div>
            <button
              class="botao-de-icone"
              type="button"
              aria-label="Editar dados principais"
              @click="abrirEdicaoDaEstrutura('dados')"
            >
              <i class="bi bi-pencil" aria-hidden="true"></i>
            </button>
          </header>
          <dl>
            <div>
              <dt>Órgão</dt>
              <dd>{{ concurso.orgao || 'Não informado' }}</dd>
            </div>
            <div>
              <dt>Banca</dt>
              <dd>{{ concurso.banca || 'Não informada' }}</dd>
            </div>
            <div>
              <dt>Prova prevista</dt>
              <dd>{{ formatarData(concurso.dataPrevistaPrincipal) }}</dd>
            </div>
            <div>
              <dt>Cargo</dt>
              <dd>{{ cargoSelecionado?.nome || 'Não selecionado' }}</dd>
            </div>
          </dl>
        </article>

        <article class="card estrutura-consolidada-do-concurso">
          <header class="cabecalho-do-cartao-da-jornada">
            <div>
              <span class="rotulo-discreto">Estrutura consolidada</span>
              <h2>Como o concurso está organizado</h2>
            </div>
            <button
              class="link-da-jornada"
              type="button"
              @click="abrirEdicaoDaEstrutura()"
            >
              Editar estrutura
              <i class="bi bi-arrow-right" aria-hidden="true"></i>
            </button>
          </header>
          <div class="arvore-resumida-do-concurso">
            <div>
              <span><i class="bi bi-file-earmark-text"></i></span>
              <p>
                <b>{{ editalPrincipal?.titulo || 'Edital pendente' }}</b>
                <small>
                  {{
                    editalPrincipal
                      ? 'Edital principal'
                      : 'Cadastre o edital principal'
                  }}
                </small>
              </p>
            </div>
            <div>
              <span><i class="bi bi-bullseye"></i></span>
              <p>
                <b>{{ cargoSelecionado?.nome || 'Cargo pendente' }}</b>
                <small>
                  {{
                    cargoSelecionado
                      ? 'Cargo selecionado'
                      : 'Selecione o cargo em foco'
                  }}
                </small>
              </p>
            </div>
            <div>
              <span><i class="bi bi-layers"></i></span>
              <p>
                <b>
                  {{ todasAsProvas.length }}
                  {{ todasAsProvas.length === 1 ? 'prova' : 'provas' }}
                </b>
                <small>
                  {{ todosOsGrupos.length }} grupos ·
                  {{ todasAsMateriasDaProva.length }} matérias vinculadas
                </small>
              </p>
            </div>
          </div>
        </article>

        <article class="card distribuicao-do-conteudo-oficial">
          <span class="rotulo-discreto">Cobertura do edital</span>
          <h2 class="titulo-editorial">Conteúdo por situação</h2>
          <div
            class="anel-de-mapeamento"
            :style="{ '--valor-do-anel': `${percentualDeMapeamento}%` }"
          >
            <span>
              <strong>{{ percentualDeMapeamento }}%</strong>
              <small>mapeado</small>
            </span>
          </div>
          <ul>
            <li>
              <i class="mapeado"></i>
              <span>Itens mapeados</span>
              <b>{{ todosOsItens.length - itensSemMapeamento.length }}</b>
            </li>
            <li>
              <i class="pendente"></i>
              <span>Sem mapeamento</span>
              <b>{{ itensSemMapeamento.length }}</b>
            </li>
          </ul>
        </article>
      </section>

      <section
        v-else
        id="painel-do-conteudo-programatico"
        class="conteudo-programatico-do-concurso painel-do-conteudo-programatico"
        aria-label="Conteúdo programático"
        role="tabpanel"
        aria-labelledby="aba-do-concurso-conteudo"
      >
        <aside class="card navegador-do-conteudo-programatico">
          <span class="rotulo-discreto">Estrutura da prova</span>
          <h2 class="titulo-editorial">Matérias vinculadas</h2>
          <div
            v-if="todasAsMateriasDaProva.length === 0"
            class="estado-vazio-compacto"
          >
            Vincule uma matéria à estrutura da prova para cadastrar itens.
          </div>
          <button
            v-for="materia in todasAsMateriasDaProva"
            :key="materia.identificador"
            type="button"
            :class="{
              ativo:
                materia.identificador === identificadorDaMateriaSelecionada,
            }"
            @click="identificadorDaMateriaSelecionada = materia.identificador"
          >
            <span>
              <b>{{ materia.nomeDaMateria }}</b>
              <small>
                {{ nomeDaProvaDaMateria(materia) }} ·
                {{ nomeDoGrupoDaMateria(materia) }}
              </small>
            </span>
            <em>
              {{
                (itensPorMateriaDaProva[materia.identificador] ?? []).filter(
                  (item) =>
                    (mapeamentosPorItem[item.identificador] ?? []).length === 0,
                ).length
              }}
              pendências
            </em>
          </button>
          <button
            class="btn btn-outline-primary mt-3"
            type="button"
            @click="abrirEdicaoDaEstrutura('materia')"
          >
            Editar estrutura
          </button>
        </aside>

        <article class="card itens-oficiais-do-concurso">
          <header class="cabecalho-do-cartao-da-jornada">
            <div>
              <span class="rotulo-discreto">
                {{ materiaDaProvaSelecionada?.nomeDaMateria || 'Matéria' }}
              </span>
              <h2>Itens do edital</h2>
              <p v-if="materiaDaProvaSelecionada">
                {{ itensDaMateriaSelecionada.length }} itens oficiais nesta
                matéria.
              </p>
            </div>
            <button
              class="btn btn-outline-primary"
              type="button"
              :disabled="!materiaDaProvaSelecionada || concursoArquivado"
              @click="abrirCadastroDeItem"
            >
              <i class="bi bi-plus-lg me-2" aria-hidden="true"></i>
              Adicionar item
            </button>
          </header>

          <div v-if="!materiaDaProvaSelecionada" class="estado-do-catalogo">
            <i class="bi bi-diagram-3" aria-hidden="true"></i>
            <strong>Nenhuma matéria selecionada</strong>
            <span>Complete a estrutura da prova para continuar.</span>
          </div>
          <div
            v-else-if="itensDaMateriaSelecionada.length === 0"
            class="estado-do-catalogo"
          >
            <i class="bi bi-file-earmark-plus" aria-hidden="true"></i>
            <strong>Nenhum item oficial cadastrado</strong>
            <span
              >Transcreva o primeiro item preservando a redação oficial.</span
            >
          </div>
          <div v-else class="lista-de-itens-oficiais">
            <article
              v-for="item in itensDaMateriaSelecionada"
              :key="item.identificador"
              :class="{
                mapeado:
                  (mapeamentosPorItem[item.identificador] ?? []).length > 0,
                pendente:
                  (mapeamentosPorItem[item.identificador] ?? []).length === 0,
              }"
            >
              <span class="estado-do-item-oficial" aria-hidden="true">
                <i
                  v-if="
                    (mapeamentosPorItem[item.identificador] ?? []).length > 0
                  "
                  class="bi bi-check2"
                ></i>
                <template v-else>!</template>
              </span>
              <div>
                <h3>{{ item.descricaoOriginal }}</h3>
                <div
                  v-if="
                    (mapeamentosPorItem[item.identificador] ?? []).length > 0
                  "
                  class="mapeamentos-do-item-oficial"
                >
                  <span
                    v-for="mapeamento in mapeamentosPorItem[
                      item.identificador
                    ] ?? []"
                    :key="mapeamento.identificador"
                  >
                    Mapeado para: {{ mapeamento.nomeDoTopico }}
                    <button
                      type="button"
                      :disabled="concursoArquivado || salvando"
                      :aria-label="`Remover vínculo com ${mapeamento.nomeDoTopico}`"
                      @click="
                        executar(() =>
                          excluirMapeamentoDoItem(
                            item.identificador,
                            mapeamento.identificadorDoTopicoDaMateria,
                          ),
                        )
                      "
                    >
                      Remover vínculo
                    </button>
                  </span>
                </div>
                <small v-else>
                  Este item ainda não conta para a cobertura do edital.
                </small>
              </div>
              <div class="acoes-do-item-oficial">
                <button
                  class="botao-de-icone"
                  type="button"
                  :disabled="concursoArquivado"
                  :aria-label="`Editar item ${item.descricaoOriginal}`"
                  @click="editarItem(item)"
                >
                  <i class="bi bi-pencil" aria-hidden="true"></i>
                </button>
                <button
                  v-if="
                    (mapeamentosPorItem[item.identificador] ?? []).length === 0
                  "
                  class="link-da-jornada"
                  type="button"
                  :disabled="concursoArquivado"
                  @click="abrirMapeamento(item)"
                >
                  Mapear agora
                </button>
              </div>
            </article>
          </div>
        </article>
      </section>

      <GavetaLateral
        v-if="gavetaDaEstruturaAberta"
        etiqueta="Edição guiada"
        titulo="Estrutura do concurso"
        descricao="Edite cada nível dentro do seu contexto, sem perder a visão geral."
        larga
        @fechar="gavetaDaEstruturaAberta = false"
      >
        <section
          class="card card-body border-0 shadow-sm mb-4 dados-gerais-do-concurso"
        >
          <div class="cabecalho-do-cartao-contextual">
            <div>
              <p class="sobretitulo-da-pagina">Etapa 1</p>
              <h2 class="h4 titulo-editorial mb-0">Dados gerais do objetivo</h2>
            </div>
            <button
              class="btn btn-sm btn-outline-primary"
              type="button"
              @click="secaoAberta = secaoAberta === 'dados' ? '' : 'dados'"
            >
              <i class="bi bi-pencil me-1" aria-hidden="true"></i>
              {{ secaoAberta === 'dados' ? 'Fechar edição' : 'Editar' }}
            </button>
          </div>
          <dl
            v-if="secaoAberta !== 'dados'"
            class="resumo-dos-dados-do-concurso"
          >
            <div>
              <dt>Órgão</dt>
              <dd>{{ concurso.orgao || 'Não informado' }}</dd>
            </div>
            <div>
              <dt>Banca</dt>
              <dd>{{ concurso.banca || 'Não informada' }}</dd>
            </div>
            <div>
              <dt>Data principal</dt>
              <dd>{{ formatarData(concurso.dataPrevistaPrincipal) }}</dd>
            </div>
            <div>
              <dt>Situação</dt>
              <dd>
                {{ rotuloDoDominio(concurso.situacao, rotulosDaSituacao) }}
              </dd>
            </div>
          </dl>
          <form v-else class="mt-3" @submit.prevent="salvarConcurso">
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
                    Órgão
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
                    Situação
                  </label>
                  <select
                    id="detalhe-situacao-concurso"
                    v-model="formularioConcurso.situacao"
                    class="form-select"
                  >
                    <option value="PLANEJADO">Planejado</option>
                    <option value="EDITAL_PUBLICADO">Edital publicado</option>
                    <option value="INSCRICOES_ABERTAS">
                      Inscrições abertas
                    </option>
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
                    Descrição
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
                    {{ edital.numero || 'Sem número' }}
                    <span v-if="edital.ano"> · {{ edital.ano }}</span>
                  </p>
                </div>
                <div class="acoes-da-estrutura">
                  <button
                    class="btn btn-outline-success btn-sm"
                    :disabled="concursoArquivado || edital.principal"
                    @click="
                      executar(() =>
                        definirEditalPrincipal(edital.identificador),
                      )
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
              <h2 class="h4">Estrutura hierárquica</h2>
              <p v-if="cargos.length === 0" class="estado-vazio-compacto">
                3. Adicione um cargo para iniciar a árvore.
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
                      {{
                        rotuloDoDominio(
                          cargo.nivelDeEscolaridade,
                          rotulosDoNivel,
                        )
                      }}
                      · ordem {{ cargo.ordem }}
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
                  v-if="
                    (provasPorCargo[cargo.identificador] ?? []).length === 0
                  "
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
                        {{ rotuloDoDominio(prova.tipo, rotulosDoTipoDeProva) }}
                        ·
                        {{ rotuloDoDominio(prova.carater, rotulosDoCarater) }}
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
                        (materiasPorGrupo[grupo.identificador] ?? []).length ===
                        0
                      "
                      class="estado-vazio-compacto ms-3"
                    >
                      6. Nenhuma materia neste grupo.
                    </div>
                    <article
                      v-for="materia in materiasPorGrupo[grupo.identificador] ??
                      []"
                      :key="materia.identificador"
                      class="ramo-da-estrutura ms-3"
                    >
                      <div class="item-da-estrutura">
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
                              remover(
                                `a materia ${materia.nomeDaMateria}`,
                                () =>
                                  excluirMateriaDaProva(materia.identificador),
                              )
                            "
                          >
                            Excluir
                          </button>
                        </div>
                      </div>

                      <p
                        v-if="
                          (itensPorMateriaDaProva[materia.identificador] ?? [])
                            .length === 0
                        "
                        class="estado-vazio-compacto ms-3"
                      >
                        7. Nenhum item oficial cadastrado.
                      </p>
                      <article
                        v-for="item in itensPorMateriaDaProva[
                          materia.identificador
                        ] ?? []"
                        :key="item.identificador"
                        class="item-do-edital"
                        :style="{
                          marginLeft: `${
                            nivelDoItem(
                              item,
                              itensPorMateriaDaProva[materia.identificador] ??
                                [],
                            ) * 1.25
                          }rem`,
                        }"
                      >
                        <div class="item-da-estrutura">
                          <div>
                            <strong>7. {{ item.descricaoOriginal }}</strong>
                            <p class="small text-secondary mb-1">
                              {{ editalDoItem(item) }} · ordem {{ item.ordem }}
                            </p>
                            <p
                              v-if="
                                (mapeamentosPorItem[item.identificador] ?? [])
                                  .length === 0
                              "
                              class="small text-secondary mb-0"
                            >
                              Sem mapeamento para tópico.
                            </p>
                            <ul
                              v-else
                              class="lista-de-mapeamentos small mb-0"
                              aria-label="Topicos mapeados"
                            >
                              <li
                                v-for="mapeamento in mapeamentosPorItem[
                                  item.identificador
                                ] ?? []"
                                :key="mapeamento.identificador"
                              >
                                <span class="badge text-bg-success me-1">
                                  Confirmado
                                </span>
                                {{ mapeamento.nomeDoTopico }}
                                <button
                                  class="btn btn-link btn-sm text-danger"
                                  type="button"
                                  :disabled="concursoArquivado"
                                  :aria-label="`Remover mapeamento com ${mapeamento.nomeDoTopico}`"
                                  @click="
                                    remover(
                                      `o mapeamento com ${mapeamento.nomeDoTopico}`,
                                      () =>
                                        excluirMapeamentoDoItem(
                                          item.identificador,
                                          mapeamento.identificadorDoTopicoDaMateria,
                                        ),
                                    )
                                  "
                                >
                                  Remover vinculo
                                </button>
                              </li>
                            </ul>
                          </div>
                          <div class="acoes-da-estrutura">
                            <button
                              class="btn btn-primary btn-sm"
                              type="button"
                              :disabled="concursoArquivado"
                              @click="abrirMapeamento(item)"
                            >
                              {{
                                (mapeamentosPorItem[item.identificador] ?? [])
                                  .length
                                  ? 'Adicionar vínculo'
                                  : 'Mapear tópico'
                              }}
                            </button>
                            <button
                              class="btn btn-outline-primary btn-sm"
                              :disabled="concursoArquivado"
                              @click="editarItem(item)"
                            >
                              Editar item
                            </button>
                            <button
                              class="btn btn-outline-danger btn-sm"
                              :disabled="concursoArquivado"
                              @click="
                                remover('o item do edital', () =>
                                  excluirItemDoEdital(item.identificador),
                                )
                              "
                            >
                              Excluir item
                            </button>
                          </div>
                        </div>
                      </article>
                    </article>
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
                    {{
                      formularioEdital.identificador ? 'Editar' : 'Adicionar'
                    }}
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
                      <label class="form-label" for="titulo-edital"
                        >Titulo</label
                      >
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
                          <label class="form-label" for="area-cargo"
                            >Area</label
                          >
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
                        <option value="NAO_INFORMADO">Não informado</option>
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
                      <label class="form-label" for="cargo-da-prova"
                        >Cargo</label
                      >
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
                          <label class="form-label" for="tipo-prova"
                            >Tipo</label
                          >
                          <select
                            id="tipo-prova"
                            v-model="formularioProva.tipo"
                            class="form-select"
                          >
                            <option value="OBJETIVA">Objetiva</option>
                            <option value="DISCURSIVA">Discursiva</option>
                            <option value="PRATICA">Prática</option>
                            <option value="TITULOS">Títulos</option>
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
                        Caráter
                      </label>
                      <select
                        id="carater-prova"
                        v-model="formularioProva.carater"
                        class="form-select mb-3"
                      >
                        <option value="NAO_INFORMADO">Não informado</option>
                        <option value="ELIMINATORIO">Eliminatório</option>
                        <option value="CLASSIFICATORIO">Classificatório</option>
                        <option value="ELIMINATORIO_E_CLASSIFICATORIO">
                          Eliminatório e classificatório
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
                            Duração em minutos
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
                            v-model.number="
                              formularioProva.quantidadeDeQuestoes
                            "
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
                      <label class="form-label" for="prova-do-grupo"
                        >Prova</label
                      >
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
                            v-model.number="
                              formularioGrupo.quantidadeDeQuestoes
                            "
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
                    {{
                      formularioMateria.identificador ? 'Editar' : 'Adicionar'
                    }}
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
                      <button class="btn btn-primary mt-3">
                        Salvar materia
                      </button>
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

              <div class="accordion-item">
                <h2 class="accordion-header">
                  <button
                    class="accordion-button"
                    :class="{ collapsed: secaoAberta !== 'item' }"
                    type="button"
                    :aria-expanded="secaoAberta === 'item'"
                    aria-controls="formulario-item-do-edital"
                    @click="alternarSecao('item')"
                  >
                    {{ formularioItem.identificador ? 'Editar' : 'Adicionar' }}
                    item oficial
                  </button>
                </h2>
                <div
                  id="formulario-item-do-edital"
                  class="accordion-collapse collapse"
                  :class="{ show: secaoAberta === 'item' }"
                >
                  <form class="accordion-body" @submit.prevent="salvarItem">
                    <fieldset
                      :disabled="
                        concursoArquivado ||
                        salvando ||
                        todasAsMateriasDaProva.length === 0 ||
                        editais.length === 0
                      "
                    >
                      <label class="form-label" for="materia-do-item">
                        Materia da prova
                      </label>
                      <select
                        id="materia-do-item"
                        v-model="formularioItem.identificadorDaMateriaDaProva"
                        class="form-select mb-2"
                        :disabled="Boolean(formularioItem.identificador)"
                        required
                        @change="formularioItem.identificadorDoItemPai = ''"
                      >
                        <option
                          v-for="materia in todasAsMateriasDaProva"
                          :key="materia.identificador"
                          :value="materia.identificador"
                        >
                          {{ materia.nomeDaMateria }}
                        </option>
                      </select>
                      <label class="form-label" for="edital-do-item">
                        Edital
                      </label>
                      <select
                        id="edital-do-item"
                        v-model="formularioItem.identificadorDoEdital"
                        class="form-select mb-2"
                        :disabled="Boolean(formularioItem.identificador)"
                        required
                        @change="formularioItem.identificadorDoItemPai = ''"
                      >
                        <option
                          v-for="edital in editais"
                          :key="edital.identificador"
                          :value="edital.identificador"
                        >
                          {{ edital.titulo }}
                        </option>
                      </select>
                      <label class="form-label" for="descricao-original">
                        Redacao original
                      </label>
                      <textarea
                        id="descricao-original"
                        v-model="formularioItem.descricaoOriginal"
                        class="form-control mb-2"
                        rows="4"
                        required
                        aria-describedby="ajuda-redacao-original"
                      ></textarea>
                      <p id="ajuda-redacao-original" class="form-text">
                        Transcreva o texto oficial sem resumir ou normalizar.
                      </p>
                      <label class="form-label" for="pai-do-item">
                        Item-pai, opcional
                      </label>
                      <select
                        id="pai-do-item"
                        v-model="formularioItem.identificadorDoItemPai"
                        class="form-select mb-2"
                      >
                        <option value="">Item raiz</option>
                        <option
                          v-for="item in paisDisponiveisParaOItem"
                          :key="item.identificador"
                          :value="item.identificador"
                        >
                          {{ item.descricaoOriginal }}
                        </option>
                      </select>
                      <label class="form-label" for="ordem-item">Ordem</label>
                      <input
                        id="ordem-item"
                        v-model.number="formularioItem.ordem"
                        class="form-control"
                        type="number"
                        min="1"
                        required
                      />
                      <button class="btn btn-primary mt-3">Salvar item</button>
                      <button
                        v-if="formularioItem.identificador"
                        class="btn btn-link mt-3"
                        type="button"
                        @click="limparItem"
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
                    type="button"
                    :aria-expanded="gavetaDeMapeamentoAberta"
                    aria-controls="formulario-mapeamento"
                    @click="abrirMapeamento()"
                  >
                    Mapear item para tópico em gaveta
                  </button>
                </h2>
              </div>
            </div>
          </aside>
        </div>
      </GavetaLateral>
    </template>
  </main>

  <GavetaLateral
    v-if="gavetaDeMapeamentoAberta"
    etiqueta="Conteúdo programático"
    :titulo="`${itensSemMapeamento.length} ${
      itensSemMapeamento.length === 1
        ? 'item sem mapeamento'
        : 'itens sem mapeamento'
    }`"
    descricao="Relacione a redação oficial a um tópico da mesma matéria."
    @fechar="gavetaDeMapeamentoAberta = false"
  >
    <div id="formulario-mapeamento">
      <form @submit.prevent="salvarMapeamento">
        <fieldset
          :disabled="concursoArquivado || salvando || todosOsItens.length === 0"
        >
          <div class="formulario-da-aplicacao">
            <label>
              <span>Item oficial</span>
              <select
                id="item-do-mapeamento"
                v-model="formularioMapeamento.identificadorDoItem"
                required
                @change="formularioMapeamento.identificadorDoTopico = ''"
              >
                <option
                  v-for="item in itensParaSelecaoDoMapeamento"
                  :key="item.identificador"
                  :value="item.identificador"
                >
                  {{ item.descricaoOriginal }}
                </option>
              </select>
            </label>
            <div class="nota-contextual">
              <i class="bi bi-shield-check" aria-hidden="true"></i>
              <p>
                <strong>A redação oficial será preservada.</strong>
                <span>
                  Esta ação cria somente o vínculo com o tópico pessoal.
                </span>
              </p>
            </div>
            <label>
              <span>Tópico da mesma matéria</span>
              <select
                id="topico-do-mapeamento"
                v-model="formularioMapeamento.identificadorDoTopico"
                :disabled="topicosParaMapeamento.length === 0"
                required
              >
                <option value="" disabled>Escolha um tópico</option>
                <option
                  v-for="topico in topicosParaMapeamento"
                  :key="topico.identificador"
                  :value="topico.identificador"
                >
                  {{ topico.nome }}
                </option>
              </select>
            </label>
            <p
              v-if="
                formularioMapeamento.identificadorDoItem &&
                topicosParaMapeamento.length === 0
              "
              class="alert alert-warning"
            >
              A matéria deste item ainda não possui tópicos ativos.
            </p>
            <button
              class="btn btn-primary"
              :disabled="topicosParaMapeamento.length === 0"
            >
              <i class="bi bi-check2 me-2" aria-hidden="true"></i>
              {{ salvando ? 'Mapeando...' : 'Confirmar mapeamento' }}
            </button>
          </div>
        </fieldset>
      </form>
    </div>
  </GavetaLateral>
</template>
