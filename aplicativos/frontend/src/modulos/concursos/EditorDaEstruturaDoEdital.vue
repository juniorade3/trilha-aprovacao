<script setup lang="ts">
import { computed, reactive, ref } from 'vue'

import SituacaoDoCampoDaExtracao from './SituacaoDoCampoDaExtracao.vue'
import type {
  CaraterDaProva,
  CargoExtraido,
  ConfirmacaoDeCampoDaExtracao,
  ExtracaoEstruturadaEditavelDoEdital,
  GrupoExtraido,
  ItemExtraidoDoEdital,
  MateriaExtraida,
  NivelDeEscolaridade,
  ProblemaDaImportacao,
  ProvaExtraida,
  TipoDeProva,
  TopicoExtraido,
  ValorExtraido,
} from './apiDeImportacaoDeEdital'

const propriedades = defineProps<{
  problemas: ProblemaDaImportacao[]
  somentePendencias: boolean
  confirmacoesDeCampos: ConfirmacaoDeCampoDaExtracao[]
}>()

const modelo = defineModel<ExtracaoEstruturadaEditavelDoEdital>({
  required: true,
})

const emitir = defineEmits<{
  alterado: []
  cargoAdicionado: [chaveDoCargo: string]
  confirmacaoAlterada: [
    referencia: ConfirmacaoDeCampoDaExtracao,
    confirmada: boolean,
  ]
}>()

const raiz = ref<HTMLElement>()
const linhasDeItens = reactive<Record<string, string>>({})
let sequenciaLocal = 0

const escolaridades: Array<{
  valor: NivelDeEscolaridade
  rotulo: string
}> = [
  { valor: 'FUNDAMENTAL', rotulo: 'Ensino fundamental' },
  { valor: 'MEDIO', rotulo: 'Ensino médio' },
  { valor: 'TECNICO', rotulo: 'Ensino técnico' },
  { valor: 'SUPERIOR', rotulo: 'Ensino superior' },
  { valor: 'NAO_INFORMADO', rotulo: 'Não informado' },
]

const tiposDeProva: Array<{ valor: TipoDeProva; rotulo: string }> = [
  { valor: 'OBJETIVA', rotulo: 'Objetiva' },
  { valor: 'DISCURSIVA', rotulo: 'Discursiva' },
  { valor: 'PRATICA', rotulo: 'Prática' },
  { valor: 'TITULOS', rotulo: 'Títulos' },
  { valor: 'OUTRA', rotulo: 'Outra' },
]

const caracteresDaProva: Array<{
  valor: CaraterDaProva
  rotulo: string
}> = [
  { valor: 'ELIMINATORIO', rotulo: 'Eliminatório' },
  { valor: 'CLASSIFICATORIO', rotulo: 'Classificatório' },
  {
    valor: 'ELIMINATORIO_E_CLASSIFICATORIO',
    rotulo: 'Eliminatório e classificatório',
  },
  { valor: 'NAO_INFORMADO', rotulo: 'Não informado' },
]

function dado<T>(valor: T | null = null): ValorExtraido<T> {
  return { valor, confianca: 1, fonte: null, inferido: false }
}

function novaChave(prefixo: string) {
  const uuid =
    typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function'
      ? crypto.randomUUID()
      : `${Date.now()}-${++sequenciaLocal}`
  return `manual-${prefixo}-${uuid}`
}

function alterou() {
  emitir('alterado')
}

function numero(evento: Event): number | null {
  const texto = (evento.target as HTMLInputElement).value
  if (!texto) return null
  const valor = Number(texto)
  return Number.isFinite(valor) ? valor : null
}

function dataHoraLocal(valor?: string | null) {
  if (!valor) return ''
  const data = new Date(valor)
  if (Number.isNaN(data.getTime())) return ''
  const local = new Date(data.getTime() - data.getTimezoneOffset() * 60_000)
  return local.toISOString().slice(0, 16)
}

function instante(evento: Event): string | null {
  const texto = (evento.target as HTMLInputElement).value
  if (!texto) return null
  const data = new Date(texto)
  return Number.isNaN(data.getTime()) ? null : data.toISOString()
}

function chaveOpcional(evento: Event): string | null {
  const valor = (evento.target as HTMLSelectElement).value
  return valor || null
}

function metadadosDoProblema(problema: ProblemaDaImportacao) {
  return {
    tipo: problema.tipoDoRecurso ?? problema.referencia?.tipoDoRecurso,
    chave: problema.chaveDoRecurso ?? problema.referencia?.chaveDoRecurso,
    campo: problema.campo ?? problema.referencia?.campo,
  }
}

function normalizar(valor?: string | null) {
  return (valor ?? '')
    .normalize('NFD')
    .replace(/\p{Diacritic}/gu, '')
    .replace(/[^a-zA-Z0-9]/g, '')
    .toLocaleLowerCase('pt-BR')
}

function adicionarAoIndice(
  indice: Map<string, ProblemaDaImportacao[]>,
  chave: string,
  problema: ProblemaDaImportacao,
) {
  const atuais = indice.get(chave)
  if (atuais) atuais.push(problema)
  else indice.set(chave, [problema])
}

const indiceDosProblemas = computed(() => {
  const porReferenciaDeCampo = new Map<string, ProblemaDaImportacao[]>()
  const porTipoECampo = new Map<string, ProblemaDaImportacao[]>()
  const porChaveECampo = new Map<string, ProblemaDaImportacao[]>()
  const porCampoSemTipo = new Map<string, ProblemaDaImportacao[]>()
  const porRecurso = new Map<string, ProblemaDaImportacao[]>()
  const porChaveSemTipo = new Map<string, ProblemaDaImportacao[]>()
  const porCaminho = new Map<string, ProblemaDaImportacao[]>()
  const porPrefixo = new Map<string, ProblemaDaImportacao[]>()

  for (const problema of propriedades.problemas) {
    const referencia = metadadosDoProblema(problema)
    const tipo = normalizar(referencia.tipo)
    const campo = normalizar(referencia.campo)
    const chave = referencia.chave ?? ''
    if (tipo && chave)
      adicionarAoIndice(porRecurso, `${tipo}\0${chave}`, problema)
    else if (chave) adicionarAoIndice(porChaveSemTipo, chave, problema)
    if (campo) {
      if (tipo) adicionarAoIndice(porTipoECampo, `${tipo}\0${campo}`, problema)
      else adicionarAoIndice(porCampoSemTipo, campo, problema)
      if (chave && !tipo)
        adicionarAoIndice(porChaveECampo, `${chave}\0${campo}`, problema)
      if (tipo && chave)
        adicionarAoIndice(
          porReferenciaDeCampo,
          `${tipo}\0${chave}\0${campo}`,
          problema,
        )
    }

    const caminho = problema.caminho
    if (!caminho) continue
    adicionarAoIndice(porCaminho, caminho, problema)
    const segmentos = caminho.split('.')
    let prefixo = ''
    for (const segmento of segmentos) {
      prefixo = prefixo ? `${prefixo}.${segmento}` : segmento
      adicionarAoIndice(porPrefixo, prefixo, problema)
    }
  }

  return {
    porReferenciaDeCampo,
    porTipoECampo,
    porChaveECampo,
    porCampoSemTipo,
    porRecurso,
    porChaveSemTipo,
    porCaminho,
    porPrefixo,
  }
})

function problemasDoCampo(
  tipoDoRecurso: string,
  chaveDoRecurso: string | undefined,
  campo: string,
  caminhos: string[],
) {
  const tipo = normalizar(tipoDoRecurso)
  const nomeDoCampo = normalizar(campo)
  const indice = indiceDosProblemas.value
  const encontrados: ProblemaDaImportacao[] = []
  if (chaveDoRecurso) {
    encontrados.push(
      ...(indice.porReferenciaDeCampo.get(
        `${tipo}\0${chaveDoRecurso}\0${nomeDoCampo}`,
      ) ?? []),
      ...(indice.porChaveECampo.get(`${chaveDoRecurso}\0${nomeDoCampo}`) ?? []),
    )
  } else {
    encontrados.push(
      ...(indice.porTipoECampo.get(`${tipo}\0${nomeDoCampo}`) ?? []),
      ...(indice.porCampoSemTipo.get(nomeDoCampo) ?? []),
    )
  }
  for (const caminho of caminhos)
    encontrados.push(...(indice.porCaminho.get(caminho) ?? []))
  return [...new Set(encontrados)]
}

function problemasDoRecurso(
  tipoDoRecurso: string,
  chaveDoRecurso: string,
  prefixosDeCaminho: string[],
) {
  const tipo = normalizar(tipoDoRecurso)
  const indice = indiceDosProblemas.value
  const encontrados = [
    ...(indice.porRecurso.get(`${tipo}\0${chaveDoRecurso}`) ?? []),
    ...(indice.porChaveSemTipo.get(chaveDoRecurso) ?? []),
  ]
  for (const prefixo of prefixosDeCaminho) {
    const raiz = prefixo.split('[', 1)[0] ?? prefixo
    encontrados.push(
      ...(indice.porCaminho.get(raiz) ?? []),
      ...(indice.porCaminho.get(prefixo) ?? []),
      ...(indice.porPrefixo.get(prefixo) ?? []),
    )
  }
  return [...new Set(encontrados)]
}

function idDosProblemas(
  tipo: string,
  chave: string | undefined,
  campo: string,
) {
  return `problemas-${normalizar(tipo)}-${normalizar(chave)}-${normalizar(campo)}`
}

const chavesDosCamposConfirmados = computed(
  () =>
    new Set(
      propriedades.confirmacoesDeCampos.map(
        (referencia) =>
          `${referencia.tipoDoRecurso}\0${referencia.chaveDoRecurso}\0${referencia.campo}`,
      ),
    ),
)

function campoConfirmado(
  tipoDoRecurso: string,
  chaveDoRecurso: string,
  campo: string,
) {
  return chavesDosCamposConfirmados.value.has(
    `${tipoDoRecurso}\0${chaveDoRecurso}\0${campo}`,
  )
}

function alterarConfirmacao(
  referencia: ConfirmacaoDeCampoDaExtracao,
  confirmada: boolean,
) {
  emitir('confirmacaoAlterada', referencia, confirmada)
}

function situacaoDoCampo<T extends string | number>(
  dadoExtraido: ValorExtraido<T>,
  tipoDoRecurso: string,
  chaveDoRecurso: string,
  campo: string,
  caminhos: string[],
) {
  return {
    dado: dadoExtraido as ValorExtraido<string | number>,
    problemas: problemasDoCampo(tipoDoRecurso, chaveDoRecurso, campo, caminhos),
    tipoDoRecurso,
    chaveDoRecurso,
    campo,
    confirmada: campoConfirmado(tipoDoRecurso, chaveDoRecurso, campo),
    identificador: idDosProblemas(tipoDoRecurso, chaveDoRecurso, campo),
  }
}

function situacaoSemDado(
  tipoDoRecurso: string,
  chaveDoRecurso: string,
  campo: string,
  caminhos: string[],
) {
  return {
    dado: undefined,
    problemas: problemasDoCampo(tipoDoRecurso, chaveDoRecurso, campo, caminhos),
    tipoDoRecurso,
    chaveDoRecurso,
    campo,
    confirmada: false,
    identificador: idDosProblemas(tipoDoRecurso, chaveDoRecurso, campo),
    permitirConfirmacao: false,
  }
}

function situacaoDeAssociacao(
  tipoDoRecurso: string,
  chaveDoRecurso: string,
  campo: 'chaveDoPai' | 'chaveDoTopicoSugerido',
  caminhos: string[],
) {
  return {
    dado: undefined,
    problemas: problemasDoCampo(tipoDoRecurso, chaveDoRecurso, campo, caminhos),
    tipoDoRecurso,
    chaveDoRecurso,
    campo,
    confirmada: campoConfirmado(tipoDoRecurso, chaveDoRecurso, campo),
    identificador: idDosProblemas(tipoDoRecurso, chaveDoRecurso, campo),
    permitirConfirmacaoSemValor: true,
  }
}

function exibirSecao(problemas: ProblemaDaImportacao[]) {
  return !propriedades.somentePendencias || problemas.length > 0
}

const problemasDaIdentificacao = computed(() =>
  propriedades.problemas.filter((problema) => {
    const referencia = metadadosDoProblema(problema)
    const tipo = normalizar(referencia.tipo)
    return (
      ['concurso', 'edital'].includes(tipo) ||
      problema.caminho?.startsWith('concurso') ||
      problema.caminho?.startsWith('edital')
    )
  }),
)

function unirProblemas(
  listas: ProblemaDaImportacao[][],
): ProblemaDaImportacao[] {
  return [...new Set(listas.flat())]
}

function provasDoCargo(chaveDoCargo: string) {
  return modelo.value.provas
    .filter((prova) => prova.chaveDoCargo === chaveDoCargo)
    .sort((a, b) => a.ordem - b.ordem)
}

function materiasDaProva(chaveDaProva: string) {
  return modelo.value.materias
    .filter((materia) => materia.chaveDaProva === chaveDaProva)
    .sort((a, b) => a.ordem - b.ordem)
}

const indiceDaProvaPorChave = computed(
  () =>
    new Map(modelo.value.provas.map((prova, indice) => [prova.chave, indice])),
)

const indiceDaMateriaPorChave = computed(
  () =>
    new Map(
      modelo.value.materias.map((materia, indice) => [materia.chave, indice]),
    ),
)

function indiceDaProvaNaExtracao(prova: ProvaExtraida) {
  return indiceDaProvaPorChave.value.get(prova.chave) ?? -1
}

function indiceDaMateriaNaExtracao(materia: MateriaExtraida) {
  return indiceDaMateriaPorChave.value.get(materia.chave) ?? -1
}

const problemasDasMaterias = computed(() => {
  const resultado = new Map<string, ProblemaDaImportacao[]>()
  for (const [indiceDaMateria, materia] of modelo.value.materias.entries()) {
    const listas = [
      problemasDoRecurso('MATERIA', materia.chave, [
        `materias[${indiceDaMateria}]`,
      ]),
      ...materia.topicos.map((topico, indiceDoTopico) =>
        problemasDoRecurso('TOPICO', topico.chave, [
          `materias[${indiceDaMateria}].topicos[${indiceDoTopico}]`,
        ]),
      ),
      ...materia.itensDoEdital.map((item, indiceDoItem) =>
        problemasDoRecurso('ITEM_DO_EDITAL', item.chave, [
          `materias[${indiceDaMateria}].itensDoEdital[${indiceDoItem}]`,
        ]),
      ),
    ]
    resultado.set(materia.chave, unirProblemas(listas))
  }
  return resultado
})

const problemasDasProvas = computed(() => {
  const resultado = new Map<string, ProblemaDaImportacao[]>()
  for (const [indiceDaProva, prova] of modelo.value.provas.entries()) {
    const listas = [
      problemasDoRecurso('PROVA', prova.chave, [`provas[${indiceDaProva}]`]),
      ...prova.grupos.map((grupo, indiceDoGrupo) =>
        problemasDoRecurso('GRUPO', grupo.chave, [
          `provas[${indiceDaProva}].grupos[${indiceDoGrupo}]`,
        ]),
      ),
      ...materiasDaProva(prova.chave).map(
        (materia) => problemasDasMaterias.value.get(materia.chave) ?? [],
      ),
    ]
    resultado.set(prova.chave, unirProblemas(listas))
  }
  return resultado
})

const problemasDosCargos = computed(() => {
  const resultado = new Map<string, ProblemaDaImportacao[]>()
  for (const [indiceDoCargo, cargo] of modelo.value.cargos.entries()) {
    const listas = [
      problemasDoRecurso('CARGO', cargo.chave, [`cargos[${indiceDoCargo}]`]),
      ...provasDoCargo(cargo.chave).map(
        (prova) => problemasDasProvas.value.get(prova.chave) ?? [],
      ),
      ...modelo.value.materias
        .filter((materia) => materia.chaveDoCargo === cargo.chave)
        .map((materia) => problemasDasMaterias.value.get(materia.chave) ?? []),
    ]
    resultado.set(cargo.chave, unirProblemas(listas))
  }
  return resultado
})

function problemasDaArvoreDoCargo(chaveDoCargo: string) {
  return problemasDosCargos.value.get(chaveDoCargo) ?? []
}

function problemasDaArvoreDaProva(chaveDaProva: string) {
  return problemasDasProvas.value.get(chaveDaProva) ?? []
}

function problemasDaArvoreDaMateria(chaveDaMateria: string) {
  return problemasDasMaterias.value.get(chaveDaMateria) ?? []
}

function adicionarCargo(comEstruturaMinima = false) {
  const cargo: CargoExtraido = {
    chave: novaChave('cargo'),
    nome: dado<string>(),
    area: dado<string>(),
    especialidade: dado<string>(),
    nivelDeEscolaridade: dado<NivelDeEscolaridade>('NAO_INFORMADO'),
    ordem: modelo.value.cargos.length + 1,
  }
  modelo.value.cargos.push(cargo)
  emitir('cargoAdicionado', cargo.chave)
  if (comEstruturaMinima) {
    const prova = adicionarProva(cargo)
    const grupo = adicionarGrupo(prova)
    const materia = adicionarMateria(cargo, prova, grupo)
    adicionarItem(materia)
  }
  alterou()
}

function removerCargo(cargo: CargoExtraido) {
  const chavesDasProvas = new Set(
    modelo.value.provas
      .filter((prova) => prova.chaveDoCargo === cargo.chave)
      .map((prova) => prova.chave),
  )
  modelo.value.cargos = modelo.value.cargos.filter(
    (item) => item.chave !== cargo.chave,
  )
  modelo.value.provas = modelo.value.provas.filter(
    (prova) => !chavesDasProvas.has(prova.chave),
  )
  modelo.value.materias = modelo.value.materias.filter(
    (materia) => materia.chaveDoCargo !== cargo.chave,
  )
  alterou()
}

function adicionarProva(cargo: CargoExtraido) {
  const provas = provasDoCargo(cargo.chave)
  const prova: ProvaExtraida = {
    chave: novaChave('prova'),
    chaveDoCargo: cargo.chave,
    nome: dado<string>('Prova objetiva'),
    tipo: dado<TipoDeProva>('OBJETIVA'),
    carater: dado<CaraterDaProva>('NAO_INFORMADO'),
    ordem: provas.length + 1,
    dataHora: dado<string>(),
    duracaoEmMinutos: dado<number>(),
    quantidadeDeQuestoes: dado<number>(),
    pontuacaoMaxima: dado<number>(),
    pontuacaoMinima: dado<number>(),
    grupos: [],
  }
  modelo.value.provas.push(prova)
  alterou()
  return prova
}

function removerProva(prova: ProvaExtraida) {
  modelo.value.provas = modelo.value.provas.filter(
    (item) => item.chave !== prova.chave,
  )
  modelo.value.materias = modelo.value.materias.filter(
    (materia) => materia.chaveDaProva !== prova.chave,
  )
  alterou()
}

function adicionarGrupo(prova: ProvaExtraida) {
  const grupo: GrupoExtraido = {
    chave: novaChave('grupo'),
    nome: dado<string>(`Grupo ${prova.grupos.length + 1}`),
    ordem: prova.grupos.length + 1,
    quantidadeDeQuestoes: dado<number>(),
    pontuacaoMaxima: dado<number>(),
    pontuacaoMinima: dado<number>(),
  }
  prova.grupos.push(grupo)
  alterou()
  return grupo
}

function removerGrupo(prova: ProvaExtraida, grupo: GrupoExtraido) {
  prova.grupos = prova.grupos.filter((item) => item.chave !== grupo.chave)
  modelo.value.materias = modelo.value.materias.filter(
    (materia) => materia.chaveDoGrupo !== grupo.chave,
  )
  alterou()
}

function adicionarMateria(
  cargo: CargoExtraido,
  prova: ProvaExtraida,
  grupo?: GrupoExtraido,
) {
  const grupoAlvo = grupo ?? prova.grupos[0] ?? adicionarGrupo(prova)
  const materia: MateriaExtraida = {
    chave: novaChave('materia'),
    chaveDoCargo: cargo.chave,
    chaveDaProva: prova.chave,
    chaveDoGrupo: grupoAlvo.chave,
    nome: dado<string>(),
    descricao: dado<string>(),
    ordem: materiasDaProva(prova.chave).length + 1,
    peso: dado<number>(),
    quantidadeDeQuestoes: dado<number>(),
    pontuacaoMaxima: dado<number>(),
    topicos: [],
    itensDoEdital: [],
  }
  modelo.value.materias.push(materia)
  alterou()
  return materia
}

function removerMateria(materia: MateriaExtraida) {
  modelo.value.materias = modelo.value.materias.filter(
    (item) => item.chave !== materia.chave,
  )
  delete linhasDeItens[materia.chave]
  alterou()
}

function adicionarTopico(materia: MateriaExtraida) {
  materia.topicos.push({
    chave: novaChave('topico'),
    chaveDoPai: null,
    numeroOficial: dado<string>(),
    nome: dado<string>(),
    descricao: dado<string>(),
    ordem: materia.topicos.length + 1,
  })
  alterou()
}

function removerTopico(materia: MateriaExtraida, topico: TopicoExtraido) {
  for (const filho of materia.topicos)
    if (filho.chaveDoPai === topico.chave)
      filho.chaveDoPai = topico.chaveDoPai ?? null
  materia.topicos = materia.topicos.filter(
    (item) => item.chave !== topico.chave,
  )
  materia.topicos.forEach((item, indice) => {
    item.ordem = indice + 1
  })
  for (const item of materia.itensDoEdital)
    if (item.chaveDoTopicoSugerido === topico.chave)
      item.chaveDoTopicoSugerido = null
  alterou()
}

function topicosDisponiveisComoPai(
  materia: MateriaExtraida,
  topicoAtual: TopicoExtraido,
) {
  const indisponiveis = new Set([topicoAtual.chave])
  let encontrouDescendente = true
  while (encontrouDescendente) {
    encontrouDescendente = false
    for (const candidato of materia.topicos)
      if (
        candidato.chaveDoPai &&
        indisponiveis.has(candidato.chaveDoPai) &&
        !indisponiveis.has(candidato.chave)
      ) {
        indisponiveis.add(candidato.chave)
        encontrouDescendente = true
      }
  }
  return materia.topicos.filter(
    (candidato) => !indisponiveis.has(candidato.chave),
  )
}

function itemDaLinha(linha: string, ordem: number): ItemExtraidoDoEdital {
  const correspondencia = linha.match(
    /^\s*((?:\d+[.)]?)(?:\.\d+[.)]?)*)\s+(.+?)\s*$/,
  )
  const numeroOficial = correspondencia?.[1]?.replace(/[.)]+$/g, '') ?? null
  const descricao = correspondencia?.[2] ?? linha.trim()
  return {
    chave: novaChave('item'),
    chaveDoPai: null,
    numeroOficial: dado<string>(numeroOficial),
    descricaoLiteral: dado<string>(descricao),
    nomeNormalizado: descricao,
    ordem,
    chaveDoTopicoSugerido: null,
  }
}

function converterLinhasEmItens(materia: MateriaExtraida) {
  const linhas = (linhasDeItens[materia.chave] ?? '')
    .split(/\r?\n/)
    .map((linha) => linha.trim())
    .filter(Boolean)
  if (!linhas.length) return
  materia.itensDoEdital = materia.itensDoEdital.filter(
    (item) =>
      Boolean(item.numeroOficial.valor?.trim()) ||
      Boolean(item.descricaoLiteral.valor?.trim()),
  )
  const inicio = materia.itensDoEdital.length
  materia.itensDoEdital.push(
    ...linhas.map((linha, indice) => itemDaLinha(linha, inicio + indice + 1)),
  )
  linhasDeItens[materia.chave] = ''
  alterou()
}

function adicionarItem(materia: MateriaExtraida) {
  materia.itensDoEdital.push(itemDaLinha('', materia.itensDoEdital.length + 1))
  alterou()
}

function removerItem(materia: MateriaExtraida, item: ItemExtraidoDoEdital) {
  for (const filho of materia.itensDoEdital)
    if (filho.chaveDoPai === item.chave)
      filho.chaveDoPai = item.chaveDoPai ?? null
  materia.itensDoEdital = materia.itensDoEdital.filter(
    (valor) => valor.chave !== item.chave,
  )
  materia.itensDoEdital.forEach((valor, indice) => {
    valor.ordem = indice + 1
  })
  alterou()
}

function focarPrimeiraPendencia() {
  raiz.value?.querySelector<HTMLElement>('[data-com-pendencia="true"]')?.focus()
}

defineExpose({ focarPrimeiraPendencia })
</script>

<template>
  <section
    ref="raiz"
    class="editor-da-estrutura editor-moderno-da-estrutura-do-edital"
  >
    <details
      v-show="exibirSecao(problemasDaIdentificacao)"
      class="secao-editavel"
      open
    >
      <summary>
        <span>
          <strong>Identificação do edital</strong>
          <small>Título, número, ano e dados do concurso</small>
        </span>
      </summary>
      <div class="grade-de-campos" @input="alterou" @change="alterou">
        <label class="campo campo-largo">
          <span>Título do edital</span>
          <input
            v-model="modelo.edital.titulo.valor"
            type="text"
            :aria-invalid="
              problemasDoCampo('EDITAL', undefined, 'titulo', ['edital.titulo'])
                .length > 0
            "
            :aria-describedby="idDosProblemas('edital', undefined, 'titulo')"
            :data-com-pendencia="
              problemasDoCampo('EDITAL', undefined, 'titulo', ['edital.titulo'])
                .length
                ? 'true'
                : undefined
            "
          />
          <SituacaoDoCampoDaExtracao
            v-bind="
              situacaoDoCampo(
                modelo.edital.titulo,
                'edital',
                'edital',
                'titulo',
                ['edital.titulo'],
              )
            "
            @alterar-confirmacao="alterarConfirmacao"
          />
        </label>
        <label class="campo">
          <span>Número</span>
          <input v-model="modelo.edital.numero.valor" type="text" />
          <SituacaoDoCampoDaExtracao
            v-bind="
              situacaoDoCampo(
                modelo.edital.numero,
                'edital',
                'edital',
                'numero',
                ['edital.numero'],
              )
            "
            @alterar-confirmacao="alterarConfirmacao"
          />
        </label>
        <label class="campo">
          <span>Ano</span>
          <input
            :value="modelo.edital.ano.valor ?? ''"
            type="number"
            min="1900"
            max="2200"
            @input="modelo.edital.ano.valor = numero($event)"
          />
          <SituacaoDoCampoDaExtracao
            v-bind="
              situacaoDoCampo(modelo.edital.ano, 'edital', 'edital', 'ano', [
                'edital.ano',
              ])
            "
            @alterar-confirmacao="alterarConfirmacao"
          />
        </label>
        <label class="campo campo-largo">
          <span>Nome do concurso</span>
          <input v-model="modelo.concurso.nome.valor" type="text" />
          <SituacaoDoCampoDaExtracao
            v-bind="
              situacaoDoCampo(
                modelo.concurso.nome,
                'concurso',
                'concurso',
                'nome',
                ['concurso.nome'],
              )
            "
            @alterar-confirmacao="alterarConfirmacao"
          />
        </label>
        <label class="campo">
          <span>Órgão</span>
          <input v-model="modelo.concurso.orgao.valor" type="text" />
          <SituacaoDoCampoDaExtracao
            v-bind="
              situacaoDoCampo(
                modelo.concurso.orgao,
                'concurso',
                'concurso',
                'orgao',
                ['concurso.orgao'],
              )
            "
            @alterar-confirmacao="alterarConfirmacao"
          />
        </label>
        <label class="campo">
          <span>Banca</span>
          <input v-model="modelo.concurso.banca.valor" type="text" />
          <SituacaoDoCampoDaExtracao
            v-bind="
              situacaoDoCampo(
                modelo.concurso.banca,
                'concurso',
                'concurso',
                'banca',
                ['concurso.banca'],
              )
            "
            @alterar-confirmacao="alterarConfirmacao"
          />
        </label>
        <label class="campo">
          <span>Publicação do edital</span>
          <input v-model="modelo.edital.dataDePublicacao.valor" type="date" />
          <SituacaoDoCampoDaExtracao
            v-bind="
              situacaoDoCampo(
                modelo.edital.dataDePublicacao,
                'edital',
                'edital',
                'dataDePublicacao',
                ['edital.dataDePublicacao'],
              )
            "
            @alterar-confirmacao="alterarConfirmacao"
          />
        </label>
        <label class="campo">
          <span>Data prevista da prova</span>
          <input v-model="modelo.concurso.dataPrevista.valor" type="date" />
          <SituacaoDoCampoDaExtracao
            v-bind="
              situacaoDoCampo(
                modelo.concurso.dataPrevista,
                'concurso',
                'concurso',
                'dataPrevista',
                ['concurso.dataPrevista'],
              )
            "
            @alterar-confirmacao="alterarConfirmacao"
          />
        </label>
        <label class="campo campo-largo">
          <span>Descrição do edital</span>
          <textarea v-model="modelo.edital.descricao.valor" rows="2"></textarea>
          <SituacaoDoCampoDaExtracao
            v-bind="
              situacaoDoCampo(
                modelo.edital.descricao,
                'edital',
                'edital',
                'descricao',
                ['edital.descricao'],
              )
            "
            @alterar-confirmacao="alterarConfirmacao"
          />
        </label>
        <label class="campo campo-largo">
          <span>Descrição do concurso</span>
          <textarea
            v-model="modelo.concurso.descricao.valor"
            rows="2"
          ></textarea>
          <SituacaoDoCampoDaExtracao
            v-bind="
              situacaoDoCampo(
                modelo.concurso.descricao,
                'concurso',
                'concurso',
                'descricao',
                ['concurso.descricao'],
              )
            "
            @alterar-confirmacao="alterarConfirmacao"
          />
        </label>
      </div>
    </details>

    <div class="cabecalho-da-lista">
      <div>
        <h3>Cargos e conteúdo programático</h3>
        <p>
          Edite cada cargo separadamente. Provas, grupos e matérias permanecem
          associados ao cargo desta seção.
        </p>
      </div>
      <button
        class="btn btn-outline-primary"
        type="button"
        @click="adicionarCargo(false)"
      >
        <i class="bi bi-plus-lg" aria-hidden="true"></i>
        Adicionar cargo
      </button>
    </div>

    <section
      v-if="modelo.cargos.length === 0"
      class="estado-vazio-da-estrutura"
    >
      <h4>Nenhum cargo foi identificado</h4>
      <p>
        Você pode criar a hierarquia mínima e preencher somente os dados
        confirmados no edital.
      </p>
      <button
        class="btn btn-primary"
        type="button"
        @click="adicionarCargo(true)"
      >
        Criar cargo, prova, grupo e matéria
      </button>
    </section>

    <details
      v-for="(cargo, indiceDoCargo) in modelo.cargos"
      v-show="exibirSecao(problemasDaArvoreDoCargo(cargo.chave))"
      :key="cargo.chave"
      class="secao-editavel secao-do-cargo"
      open
    >
      <summary>
        <span>
          <strong>{{
            cargo.nome.valor || `Cargo ${indiceDoCargo + 1}`
          }}</strong>
          <small>
            {{ provasDoCargo(cargo.chave).length }} prova(s) ·
            {{
              modelo.materias.filter(
                (materia) => materia.chaveDoCargo === cargo.chave,
              ).length
            }}
            matéria(s)
          </small>
        </span>
        <span
          v-if="problemasDaArvoreDoCargo(cargo.chave).length"
          class="badge text-bg-warning"
        >
          {{ problemasDaArvoreDoCargo(cargo.chave).length }}
          pendência(s)
        </span>
      </summary>

      <div class="conteudo-da-secao">
        <div class="grade-de-campos" @input="alterou" @change="alterou">
          <label class="campo campo-largo">
            <span>Nome do cargo</span>
            <input
              v-model="cargo.nome.valor"
              type="text"
              :aria-invalid="
                problemasDoCampo('CARGO', cargo.chave, 'nome', [
                  `cargos[${indiceDoCargo}].nome`,
                ]).length > 0
              "
              :aria-describedby="idDosProblemas('cargo', cargo.chave, 'nome')"
              :data-com-pendencia="
                problemasDoCampo('CARGO', cargo.chave, 'nome', [
                  `cargos[${indiceDoCargo}].nome`,
                ]).length
                  ? 'true'
                  : undefined
              "
            />
            <SituacaoDoCampoDaExtracao
              v-bind="
                situacaoDoCampo(cargo.nome, 'cargo', cargo.chave, 'nome', [
                  `cargos[${indiceDoCargo}].nome`,
                ])
              "
              @alterar-confirmacao="alterarConfirmacao"
            />
          </label>
          <label class="campo">
            <span>Área</span>
            <input v-model="cargo.area.valor" type="text" />
            <SituacaoDoCampoDaExtracao
              v-bind="
                situacaoDoCampo(cargo.area, 'cargo', cargo.chave, 'area', [
                  `cargos[${indiceDoCargo}].area`,
                ])
              "
              @alterar-confirmacao="alterarConfirmacao"
            />
          </label>
          <label class="campo">
            <span>Especialidade</span>
            <input v-model="cargo.especialidade.valor" type="text" />
            <SituacaoDoCampoDaExtracao
              v-bind="
                situacaoDoCampo(
                  cargo.especialidade,
                  'cargo',
                  cargo.chave,
                  'especialidade',
                  [`cargos[${indiceDoCargo}].especialidade`],
                )
              "
              @alterar-confirmacao="alterarConfirmacao"
            />
          </label>
          <label class="campo">
            <span>Escolaridade</span>
            <select
              v-model="cargo.nivelDeEscolaridade.valor"
              :aria-invalid="
                problemasDoCampo('CARGO', cargo.chave, 'nivelDeEscolaridade', [
                  `cargos[${indiceDoCargo}].nivelDeEscolaridade`,
                ]).length > 0
              "
              :aria-describedby="
                idDosProblemas('cargo', cargo.chave, 'nivelDeEscolaridade')
              "
              :data-com-pendencia="
                problemasDoCampo('CARGO', cargo.chave, 'nivelDeEscolaridade', [
                  `cargos[${indiceDoCargo}].nivelDeEscolaridade`,
                ]).length
                  ? 'true'
                  : undefined
              "
            >
              <option
                v-for="escolaridade in escolaridades"
                :key="escolaridade.valor"
                :value="escolaridade.valor"
              >
                {{ escolaridade.rotulo }}
              </option>
            </select>
            <SituacaoDoCampoDaExtracao
              v-bind="
                situacaoDoCampo(
                  cargo.nivelDeEscolaridade,
                  'cargo',
                  cargo.chave,
                  'nivelDeEscolaridade',
                  [`cargos[${indiceDoCargo}].nivelDeEscolaridade`],
                )
              "
              @alterar-confirmacao="alterarConfirmacao"
            />
          </label>
        </div>

        <div class="barra-de-acoes">
          <button
            class="btn btn-sm btn-outline-primary"
            type="button"
            @click="adicionarProva(cargo)"
          >
            Adicionar prova
          </button>
          <button
            class="btn btn-sm btn-outline-danger"
            type="button"
            @click="removerCargo(cargo)"
          >
            Remover cargo
          </button>
        </div>
        <SituacaoDoCampoDaExtracao
          v-bind="
            situacaoSemDado('cargo', cargo.chave, 'provas', [
              `provas[cargo=${cargo.chave}]`,
            ])
          "
          @alterar-confirmacao="alterarConfirmacao"
        />
        <SituacaoDoCampoDaExtracao
          v-bind="
            situacaoSemDado('cargo', cargo.chave, 'materias', ['materias'])
          "
          @alterar-confirmacao="alterarConfirmacao"
        />

        <details
          v-for="(prova, indiceDaProva) in provasDoCargo(cargo.chave)"
          v-show="exibirSecao(problemasDaArvoreDaProva(prova.chave))"
          :key="prova.chave"
          class="secao-editavel secao-da-prova"
          open
        >
          <summary>
            <span>
              <strong>{{
                prova.nome.valor || `Prova ${indiceDaProva + 1}`
              }}</strong>
              <small>
                {{ prova.grupos.length }} grupo(s) ·
                {{ materiasDaProva(prova.chave).length }} matéria(s)
              </small>
            </span>
            <span
              v-if="problemasDaArvoreDaProva(prova.chave).length"
              class="badge text-bg-warning"
            >
              {{ problemasDaArvoreDaProva(prova.chave).length }}
              pendência(s)
            </span>
          </summary>
          <div class="conteudo-da-secao">
            <div class="grade-de-campos" @input="alterou" @change="alterou">
              <label class="campo campo-largo">
                <span>Nome da prova</span>
                <input
                  v-model="prova.nome.valor"
                  type="text"
                  :aria-invalid="
                    problemasDoCampo('PROVA', prova.chave, 'nome', [
                      `provas[${indiceDaProvaNaExtracao(prova)}].nome`,
                    ]).length > 0
                  "
                  :aria-describedby="
                    idDosProblemas('prova', prova.chave, 'nome')
                  "
                  :data-com-pendencia="
                    problemasDoCampo('PROVA', prova.chave, 'nome', [
                      `provas[${indiceDaProvaNaExtracao(prova)}].nome`,
                    ]).length
                      ? 'true'
                      : undefined
                  "
                />
                <SituacaoDoCampoDaExtracao
                  v-bind="
                    situacaoDoCampo(prova.nome, 'prova', prova.chave, 'nome', [
                      `provas[${indiceDaProvaNaExtracao(prova)}].nome`,
                    ])
                  "
                  @alterar-confirmacao="alterarConfirmacao"
                />
              </label>
              <label class="campo">
                <span>Tipo</span>
                <select
                  v-model="prova.tipo.valor"
                  :aria-invalid="
                    problemasDoCampo('PROVA', prova.chave, 'tipo', [
                      `provas[${indiceDaProvaNaExtracao(prova)}].tipo`,
                    ]).length > 0
                  "
                  :aria-describedby="
                    idDosProblemas('prova', prova.chave, 'tipo')
                  "
                  :data-com-pendencia="
                    problemasDoCampo('PROVA', prova.chave, 'tipo', [
                      `provas[${indiceDaProvaNaExtracao(prova)}].tipo`,
                    ]).length
                      ? 'true'
                      : undefined
                  "
                >
                  <option :value="null">Selecione</option>
                  <option
                    v-for="tipo in tiposDeProva"
                    :key="tipo.valor"
                    :value="tipo.valor"
                  >
                    {{ tipo.rotulo }}
                  </option>
                </select>
                <SituacaoDoCampoDaExtracao
                  v-bind="
                    situacaoDoCampo(prova.tipo, 'prova', prova.chave, 'tipo', [
                      `provas[${indiceDaProvaNaExtracao(prova)}].tipo`,
                    ])
                  "
                  @alterar-confirmacao="alterarConfirmacao"
                />
              </label>
              <label class="campo">
                <span>Caráter</span>
                <select
                  v-model="prova.carater.valor"
                  :aria-invalid="
                    problemasDoCampo('PROVA', prova.chave, 'carater', [
                      `provas[${indiceDaProvaNaExtracao(prova)}].carater`,
                    ]).length > 0
                  "
                  :aria-describedby="
                    idDosProblemas('prova', prova.chave, 'carater')
                  "
                  :data-com-pendencia="
                    problemasDoCampo('PROVA', prova.chave, 'carater', [
                      `provas[${indiceDaProvaNaExtracao(prova)}].carater`,
                    ]).length
                      ? 'true'
                      : undefined
                  "
                >
                  <option :value="null">Selecione</option>
                  <option
                    v-for="carater in caracteresDaProva"
                    :key="carater.valor"
                    :value="carater.valor"
                  >
                    {{ carater.rotulo }}
                  </option>
                </select>
                <SituacaoDoCampoDaExtracao
                  v-bind="
                    situacaoDoCampo(
                      prova.carater,
                      'prova',
                      prova.chave,
                      'carater',
                      [`provas[${indiceDaProvaNaExtracao(prova)}].carater`],
                    )
                  "
                  @alterar-confirmacao="alterarConfirmacao"
                />
              </label>
              <label class="campo">
                <span>Data e hora</span>
                <input
                  :value="dataHoraLocal(prova.dataHora.valor)"
                  type="datetime-local"
                  @input="prova.dataHora.valor = instante($event)"
                />
                <SituacaoDoCampoDaExtracao
                  v-bind="
                    situacaoDoCampo(
                      prova.dataHora,
                      'prova',
                      prova.chave,
                      'dataHora',
                      [`provas[${indiceDaProvaNaExtracao(prova)}].dataHora`],
                    )
                  "
                  @alterar-confirmacao="alterarConfirmacao"
                />
              </label>
              <label class="campo">
                <span>Duração (minutos)</span>
                <input
                  :value="prova.duracaoEmMinutos.valor ?? ''"
                  type="number"
                  min="1"
                  @input="prova.duracaoEmMinutos.valor = numero($event)"
                />
                <SituacaoDoCampoDaExtracao
                  v-bind="
                    situacaoDoCampo(
                      prova.duracaoEmMinutos,
                      'prova',
                      prova.chave,
                      'duracaoEmMinutos',
                      [
                        `provas[${indiceDaProvaNaExtracao(prova)}].duracaoEmMinutos`,
                      ],
                    )
                  "
                  @alterar-confirmacao="alterarConfirmacao"
                />
              </label>
              <label class="campo">
                <span>Questões</span>
                <input
                  :value="prova.quantidadeDeQuestoes.valor ?? ''"
                  type="number"
                  min="0"
                  @input="prova.quantidadeDeQuestoes.valor = numero($event)"
                />
                <SituacaoDoCampoDaExtracao
                  v-bind="
                    situacaoDoCampo(
                      prova.quantidadeDeQuestoes,
                      'prova',
                      prova.chave,
                      'quantidadeDeQuestoes',
                      [
                        `provas[${indiceDaProvaNaExtracao(prova)}].quantidadeDeQuestoes`,
                      ],
                    )
                  "
                  @alterar-confirmacao="alterarConfirmacao"
                />
              </label>
              <label class="campo">
                <span>Pontuação máxima</span>
                <input
                  :value="prova.pontuacaoMaxima.valor ?? ''"
                  type="number"
                  min="0"
                  step="0.01"
                  @input="prova.pontuacaoMaxima.valor = numero($event)"
                />
                <SituacaoDoCampoDaExtracao
                  v-bind="
                    situacaoDoCampo(
                      prova.pontuacaoMaxima,
                      'prova',
                      prova.chave,
                      'pontuacaoMaxima',
                      [
                        `provas[${indiceDaProvaNaExtracao(prova)}].pontuacaoMaxima`,
                      ],
                    )
                  "
                  @alterar-confirmacao="alterarConfirmacao"
                />
              </label>
              <label class="campo">
                <span>Pontuação mínima</span>
                <input
                  :value="prova.pontuacaoMinima.valor ?? ''"
                  type="number"
                  min="0"
                  step="0.01"
                  @input="prova.pontuacaoMinima.valor = numero($event)"
                />
                <SituacaoDoCampoDaExtracao
                  v-bind="
                    situacaoDoCampo(
                      prova.pontuacaoMinima,
                      'prova',
                      prova.chave,
                      'pontuacaoMinima',
                      [
                        `provas[${indiceDaProvaNaExtracao(prova)}].pontuacaoMinima`,
                      ],
                    )
                  "
                  @alterar-confirmacao="alterarConfirmacao"
                />
              </label>
            </div>

            <section class="lista-interna">
              <div class="cabecalho-da-lista">
                <h5>Grupos da prova</h5>
                <button
                  class="btn btn-sm btn-outline-primary"
                  type="button"
                  @click="adicionarGrupo(prova)"
                >
                  Adicionar grupo
                </button>
              </div>
              <SituacaoDoCampoDaExtracao
                v-bind="
                  situacaoSemDado('prova', prova.chave, 'grupos', [
                    `provas[${indiceDaProvaNaExtracao(prova)}].grupos`,
                  ])
                "
                @alterar-confirmacao="alterarConfirmacao"
              />
              <article
                v-for="(grupo, indiceDoGrupo) in prova.grupos"
                :key="grupo.chave"
                class="linha-editavel"
              >
                <div class="grade-de-campos" @input="alterou" @change="alterou">
                  <label class="campo campo-largo">
                    <span>Nome do grupo {{ indiceDoGrupo + 1 }}</span>
                    <input v-model="grupo.nome.valor" type="text" />
                    <SituacaoDoCampoDaExtracao
                      v-bind="
                        situacaoDoCampo(
                          grupo.nome,
                          'grupo',
                          grupo.chave,
                          'nome',
                          [
                            `provas[${indiceDaProvaNaExtracao(prova)}].grupos[${indiceDoGrupo}].nome`,
                          ],
                        )
                      "
                      @alterar-confirmacao="alterarConfirmacao"
                    />
                  </label>
                  <label class="campo">
                    <span>Questões</span>
                    <input
                      :value="grupo.quantidadeDeQuestoes.valor ?? ''"
                      type="number"
                      min="0"
                      @input="grupo.quantidadeDeQuestoes.valor = numero($event)"
                    />
                    <SituacaoDoCampoDaExtracao
                      v-bind="
                        situacaoDoCampo(
                          grupo.quantidadeDeQuestoes,
                          'grupo',
                          grupo.chave,
                          'quantidadeDeQuestoes',
                          [
                            `provas[${indiceDaProvaNaExtracao(prova)}].grupos[${indiceDoGrupo}].quantidadeDeQuestoes`,
                          ],
                        )
                      "
                      @alterar-confirmacao="alterarConfirmacao"
                    />
                  </label>
                  <label class="campo">
                    <span>Pontuação máxima</span>
                    <input
                      :value="grupo.pontuacaoMaxima.valor ?? ''"
                      type="number"
                      min="0"
                      step="0.01"
                      @input="grupo.pontuacaoMaxima.valor = numero($event)"
                    />
                    <SituacaoDoCampoDaExtracao
                      v-bind="
                        situacaoDoCampo(
                          grupo.pontuacaoMaxima,
                          'grupo',
                          grupo.chave,
                          'pontuacaoMaxima',
                          [
                            `provas[${indiceDaProvaNaExtracao(prova)}].grupos[${indiceDoGrupo}].pontuacaoMaxima`,
                          ],
                        )
                      "
                      @alterar-confirmacao="alterarConfirmacao"
                    />
                  </label>
                  <label class="campo">
                    <span>Pontuação mínima</span>
                    <input
                      :value="grupo.pontuacaoMinima.valor ?? ''"
                      type="number"
                      min="0"
                      step="0.01"
                      @input="grupo.pontuacaoMinima.valor = numero($event)"
                    />
                    <SituacaoDoCampoDaExtracao
                      v-bind="
                        situacaoDoCampo(
                          grupo.pontuacaoMinima,
                          'grupo',
                          grupo.chave,
                          'pontuacaoMinima',
                          [
                            `provas[${indiceDaProvaNaExtracao(prova)}].grupos[${indiceDoGrupo}].pontuacaoMinima`,
                          ],
                        )
                      "
                      @alterar-confirmacao="alterarConfirmacao"
                    />
                  </label>
                </div>
                <button
                  class="btn btn-sm btn-link text-danger"
                  type="button"
                  @click="removerGrupo(prova, grupo)"
                >
                  Remover grupo
                </button>
              </article>
            </section>

            <section class="lista-interna">
              <div class="cabecalho-da-lista">
                <h5>Matérias e conteúdo</h5>
                <button
                  class="btn btn-sm btn-outline-primary"
                  type="button"
                  @click="adicionarMateria(cargo, prova)"
                >
                  Adicionar matéria
                </button>
              </div>

              <details
                v-for="(materia, indiceDaMateria) in materiasDaProva(
                  prova.chave,
                )"
                v-show="exibirSecao(problemasDaArvoreDaMateria(materia.chave))"
                :key="materia.chave"
                class="secao-editavel secao-da-materia"
              >
                <summary>
                  <span>
                    <strong>{{
                      materia.nome.valor || `Matéria ${indiceDaMateria + 1}`
                    }}</strong>
                    <small>
                      {{ materia.topicos.length }} tópico(s) ·
                      {{ materia.itensDoEdital.length }} item(ns)
                    </small>
                  </span>
                  <span
                    v-if="problemasDaArvoreDaMateria(materia.chave).length"
                    class="badge text-bg-warning"
                  >
                    {{ problemasDaArvoreDaMateria(materia.chave).length }}
                    pendência(s)
                  </span>
                </summary>
                <div class="conteudo-da-secao">
                  <div
                    class="grade-de-campos"
                    @input="alterou"
                    @change="alterou"
                  >
                    <label class="campo campo-largo">
                      <span>Nome da matéria</span>
                      <input
                        v-model="materia.nome.valor"
                        type="text"
                        :aria-invalid="
                          problemasDoCampo('MATERIA', materia.chave, 'nome', [
                            `materias[${indiceDaMateriaNaExtracao(materia)}].nome`,
                          ]).length > 0
                        "
                        :aria-describedby="
                          idDosProblemas('materia', materia.chave, 'nome')
                        "
                        :data-com-pendencia="
                          problemasDoCampo('MATERIA', materia.chave, 'nome', [
                            `materias[${indiceDaMateriaNaExtracao(materia)}].nome`,
                          ]).length
                            ? 'true'
                            : undefined
                        "
                      />
                      <SituacaoDoCampoDaExtracao
                        v-bind="
                          situacaoDoCampo(
                            materia.nome,
                            'materia',
                            materia.chave,
                            'nome',
                            [
                              `materias[${indiceDaMateriaNaExtracao(materia)}].nome`,
                            ],
                          )
                        "
                        @alterar-confirmacao="alterarConfirmacao"
                      />
                    </label>
                    <label class="campo">
                      <span>Grupo</span>
                      <select v-model="materia.chaveDoGrupo">
                        <option
                          v-for="grupo in prova.grupos"
                          :key="grupo.chave"
                          :value="grupo.chave"
                        >
                          {{ grupo.nome.valor || 'Grupo sem nome' }}
                        </option>
                      </select>
                      <SituacaoDoCampoDaExtracao
                        v-bind="
                          situacaoSemDado(
                            'materia',
                            materia.chave,
                            'chaveDoGrupo',
                            [
                              `materias[${indiceDaMateriaNaExtracao(materia)}].chaveDoGrupo`,
                            ],
                          )
                        "
                        @alterar-confirmacao="alterarConfirmacao"
                      />
                    </label>
                    <label class="campo">
                      <span>Peso</span>
                      <input
                        :value="materia.peso.valor ?? ''"
                        type="number"
                        min="0"
                        step="0.01"
                        @input="materia.peso.valor = numero($event)"
                      />
                      <SituacaoDoCampoDaExtracao
                        v-bind="
                          situacaoDoCampo(
                            materia.peso,
                            'materia',
                            materia.chave,
                            'peso',
                            [
                              `materias[${indiceDaMateriaNaExtracao(materia)}].peso`,
                            ],
                          )
                        "
                        @alterar-confirmacao="alterarConfirmacao"
                      />
                    </label>
                    <label class="campo">
                      <span>Questões</span>
                      <input
                        :value="materia.quantidadeDeQuestoes.valor ?? ''"
                        type="number"
                        min="0"
                        @input="
                          materia.quantidadeDeQuestoes.valor = numero($event)
                        "
                      />
                      <SituacaoDoCampoDaExtracao
                        v-bind="
                          situacaoDoCampo(
                            materia.quantidadeDeQuestoes,
                            'materia',
                            materia.chave,
                            'quantidadeDeQuestoes',
                            [
                              `materias[${indiceDaMateriaNaExtracao(materia)}].quantidadeDeQuestoes`,
                            ],
                          )
                        "
                        @alterar-confirmacao="alterarConfirmacao"
                      />
                    </label>
                    <label class="campo">
                      <span>Pontuação máxima</span>
                      <input
                        :value="materia.pontuacaoMaxima.valor ?? ''"
                        type="number"
                        min="0"
                        step="0.01"
                        @input="materia.pontuacaoMaxima.valor = numero($event)"
                      />
                      <SituacaoDoCampoDaExtracao
                        v-bind="
                          situacaoDoCampo(
                            materia.pontuacaoMaxima,
                            'materia',
                            materia.chave,
                            'pontuacaoMaxima',
                            [
                              `materias[${indiceDaMateriaNaExtracao(materia)}].pontuacaoMaxima`,
                            ],
                          )
                        "
                        @alterar-confirmacao="alterarConfirmacao"
                      />
                    </label>
                    <label class="campo campo-largo">
                      <span>Descrição</span>
                      <textarea
                        v-model="materia.descricao.valor"
                        rows="2"
                      ></textarea>
                      <SituacaoDoCampoDaExtracao
                        v-bind="
                          situacaoDoCampo(
                            materia.descricao,
                            'materia',
                            materia.chave,
                            'descricao',
                            [
                              `materias[${indiceDaMateriaNaExtracao(materia)}].descricao`,
                            ],
                          )
                        "
                        @alterar-confirmacao="alterarConfirmacao"
                      />
                    </label>
                  </div>

                  <section class="lista-interna">
                    <div class="cabecalho-da-lista">
                      <h6>Tópicos</h6>
                      <button
                        class="btn btn-sm btn-outline-primary"
                        type="button"
                        @click="adicionarTopico(materia)"
                      >
                        Adicionar tópico
                      </button>
                    </div>
                    <SituacaoDoCampoDaExtracao
                      v-bind="
                        situacaoSemDado('materia', materia.chave, 'topicos', [
                          `materias[${indiceDaMateriaNaExtracao(materia)}].topicos`,
                        ])
                      "
                      @alterar-confirmacao="alterarConfirmacao"
                    />
                    <article
                      v-for="(topico, indiceDoTopico) in materia.topicos"
                      :key="topico.chave"
                      class="linha-editavel"
                    >
                      <div
                        class="grade-de-campos"
                        @input="alterou"
                        @change="alterou"
                      >
                        <label class="campo">
                          <span>Número</span>
                          <input
                            v-model="topico.numeroOficial.valor"
                            type="text"
                          />
                          <SituacaoDoCampoDaExtracao
                            v-bind="
                              situacaoDoCampo(
                                topico.numeroOficial,
                                'topico',
                                topico.chave,
                                'numeroOficial',
                                [
                                  `materias[${indiceDaMateriaNaExtracao(materia)}].topicos[${indiceDoTopico}].numeroOficial`,
                                ],
                              )
                            "
                            @alterar-confirmacao="alterarConfirmacao"
                          />
                        </label>
                        <label class="campo campo-largo">
                          <span>Nome do tópico {{ indiceDoTopico + 1 }}</span>
                          <input v-model="topico.nome.valor" type="text" />
                          <SituacaoDoCampoDaExtracao
                            v-bind="
                              situacaoDoCampo(
                                topico.nome,
                                'topico',
                                topico.chave,
                                'nome',
                                [
                                  `materias[${indiceDaMateriaNaExtracao(materia)}].topicos[${indiceDoTopico}].nome`,
                                ],
                              )
                            "
                            @alterar-confirmacao="alterarConfirmacao"
                          />
                        </label>
                        <label class="campo">
                          <span>Tópico pai</span>
                          <select
                            :value="topico.chaveDoPai ?? ''"
                            @change="topico.chaveDoPai = chaveOpcional($event)"
                          >
                            <option value="">Tópico raiz</option>
                            <option
                              v-for="candidato in topicosDisponiveisComoPai(
                                materia,
                                topico,
                              )"
                              :key="candidato.chave"
                              :value="candidato.chave"
                            >
                              {{ candidato.nome.valor || 'Tópico sem nome' }}
                            </option>
                          </select>
                          <SituacaoDoCampoDaExtracao
                            v-bind="
                              situacaoDeAssociacao(
                                'topico',
                                topico.chave,
                                'chaveDoPai',
                                [
                                  `materias[${indiceDaMateriaNaExtracao(materia)}].topicos[${indiceDoTopico}].chaveDoPai`,
                                ],
                              )
                            "
                            @alterar-confirmacao="alterarConfirmacao"
                          />
                        </label>
                        <label class="campo campo-largo">
                          <span>Descrição</span>
                          <textarea
                            v-model="topico.descricao.valor"
                            rows="2"
                          ></textarea>
                          <SituacaoDoCampoDaExtracao
                            v-bind="
                              situacaoDoCampo(
                                topico.descricao,
                                'topico',
                                topico.chave,
                                'descricao',
                                [
                                  `materias[${indiceDaMateriaNaExtracao(materia)}].topicos[${indiceDoTopico}].descricao`,
                                ],
                              )
                            "
                            @alterar-confirmacao="alterarConfirmacao"
                          />
                        </label>
                      </div>
                      <button
                        class="btn btn-sm btn-link text-danger"
                        type="button"
                        @click="removerTopico(materia, topico)"
                      >
                        Remover tópico
                      </button>
                    </article>
                  </section>

                  <section class="lista-interna">
                    <div class="cabecalho-da-lista">
                      <h6>Itens literais do edital</h6>
                      <button
                        class="btn btn-sm btn-outline-primary"
                        type="button"
                        @click="adicionarItem(materia)"
                      >
                        Adicionar item
                      </button>
                    </div>
                    <SituacaoDoCampoDaExtracao
                      v-bind="
                        situacaoSemDado(
                          'materia',
                          materia.chave,
                          'itensDoEdital',
                          [
                            `materias[${indiceDaMateriaNaExtracao(materia)}].itensDoEdital`,
                          ],
                        )
                      "
                      @alterar-confirmacao="alterarConfirmacao"
                    />
                    <label class="campo campo-largo conversor-de-linhas">
                      <span>Adicionar vários itens</span>
                      <textarea
                        v-model="linhasDeItens[materia.chave]"
                        rows="4"
                        placeholder="Cole o conteúdo aqui, com um item por linha"
                      ></textarea>
                      <small>
                        Numerações no início da linha serão preservadas quando
                        existirem.
                      </small>
                      <button
                        class="btn btn-sm btn-outline-secondary"
                        type="button"
                        :disabled="!(linhasDeItens[materia.chave] ?? '').trim()"
                        @click="converterLinhasEmItens(materia)"
                      >
                        Converter uma linha por item
                      </button>
                    </label>
                    <article
                      v-for="(item, indiceDoItem) in materia.itensDoEdital"
                      :key="item.chave"
                      class="linha-editavel"
                    >
                      <div
                        class="grade-de-campos"
                        @input="alterou"
                        @change="alterou"
                      >
                        <label class="campo">
                          <span>Número</span>
                          <input
                            v-model="item.numeroOficial.valor"
                            type="text"
                          />
                          <SituacaoDoCampoDaExtracao
                            v-bind="
                              situacaoDoCampo(
                                item.numeroOficial,
                                'itemDoEdital',
                                item.chave,
                                'numeroOficial',
                                [
                                  `materias[${indiceDaMateriaNaExtracao(materia)}].itensDoEdital[${indiceDoItem}].numeroOficial`,
                                ],
                              )
                            "
                            @alterar-confirmacao="alterarConfirmacao"
                          />
                        </label>
                        <label class="campo campo-largo">
                          <span>Item {{ indiceDoItem + 1 }}</span>
                          <textarea
                            v-model="item.descricaoLiteral.valor"
                            rows="2"
                            @input="
                              item.nomeNormalizado =
                                item.descricaoLiteral.valor ?? ''
                            "
                          ></textarea>
                          <SituacaoDoCampoDaExtracao
                            v-bind="
                              situacaoDoCampo(
                                item.descricaoLiteral,
                                'itemDoEdital',
                                item.chave,
                                'descricaoLiteral',
                                [
                                  `materias[${indiceDaMateriaNaExtracao(materia)}].itensDoEdital[${indiceDoItem}].descricaoLiteral`,
                                ],
                              )
                            "
                            @alterar-confirmacao="alterarConfirmacao"
                          />
                        </label>
                        <label class="campo">
                          <span>Tópico relacionado</span>
                          <select v-model="item.chaveDoTopicoSugerido">
                            <option :value="null">Não relacionar</option>
                            <option
                              v-for="topico in materia.topicos"
                              :key="topico.chave"
                              :value="topico.chave"
                            >
                              {{ topico.nome.valor || 'Tópico sem nome' }}
                            </option>
                          </select>
                          <SituacaoDoCampoDaExtracao
                            v-bind="
                              situacaoDeAssociacao(
                                'itemDoEdital',
                                item.chave,
                                'chaveDoTopicoSugerido',
                                [
                                  `materias[${indiceDaMateriaNaExtracao(materia)}].itensDoEdital[${indiceDoItem}].chaveDoTopicoSugerido`,
                                ],
                              )
                            "
                            @alterar-confirmacao="alterarConfirmacao"
                          />
                        </label>
                      </div>
                      <button
                        class="btn btn-sm btn-link text-danger"
                        type="button"
                        @click="removerItem(materia, item)"
                      >
                        Remover item
                      </button>
                    </article>
                  </section>

                  <button
                    class="btn btn-sm btn-outline-danger align-self-start"
                    type="button"
                    @click="removerMateria(materia)"
                  >
                    Remover matéria
                  </button>
                </div>
              </details>
            </section>

            <button
              class="btn btn-sm btn-outline-danger align-self-start"
              type="button"
              @click="removerProva(prova)"
            >
              Remover prova
            </button>
          </div>
        </details>
      </div>
    </details>
  </section>
</template>

<style scoped lang="scss">
.editor-da-estrutura {
  display: grid;
  gap: 1rem;
}

.secao-editavel {
  border: 1px solid var(--bs-border-color);
  border-radius: 0.8rem;
  background: var(--bs-body-bg);
}

.secao-editavel > summary {
  align-items: center;
  cursor: pointer;
  display: flex;
  gap: 0.75rem;
  justify-content: space-between;
  list-style: none;
  min-height: 3.5rem;
  padding: 0.85rem 1rem;
}

.secao-editavel > summary::-webkit-details-marker {
  display: none;
}

.secao-editavel > summary::before {
  color: var(--bs-secondary-color);
  content: '›';
  font-size: 1.4rem;
  transform: rotate(0);
  transition: transform 0.15s ease;
}

.secao-editavel[open] > summary::before {
  transform: rotate(90deg);
}

.secao-editavel > summary > span:first-of-type {
  display: grid;
  flex: 1;
}

.secao-editavel > summary small {
  color: var(--bs-secondary-color);
}

.secao-do-cargo {
  border-color: color-mix(in srgb, var(--bs-primary) 35%, transparent);
}

.secao-da-prova,
.secao-da-materia {
  background: color-mix(in srgb, var(--bs-tertiary-bg) 65%, transparent);
}

.conteudo-da-secao {
  border-top: 1px solid var(--bs-border-color);
  display: grid;
  gap: 1rem;
  padding: 1rem;
}

.grade-de-campos {
  display: grid;
  gap: 0.85rem;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.campo {
  display: grid;
  gap: 0.3rem;
}

.campo > span {
  font-weight: 650;
}

.campo-largo {
  grid-column: 1 / -1;
}

.campo input,
.campo select,
.campo textarea {
  border: 1px solid var(--bs-border-color);
  border-radius: 0.45rem;
  min-height: 2.55rem;
  padding: 0.45rem 0.6rem;
  width: 100%;
}

.campo textarea {
  min-height: auto;
}

.campo [aria-invalid='true'] {
  border-color: var(--bs-danger);
  box-shadow: 0 0 0 0.12rem
    color-mix(in srgb, var(--bs-danger) 20%, transparent);
}

.erros-do-campo {
  color: var(--bs-danger-text-emphasis);
  display: grid;
  gap: 0.2rem;
}

.cabecalho-da-lista {
  align-items: center;
  display: flex;
  gap: 1rem;
  justify-content: space-between;
}

.cabecalho-da-lista h3,
.cabecalho-da-lista h5,
.cabecalho-da-lista h6,
.cabecalho-da-lista p {
  margin: 0;
}

.cabecalho-da-lista p {
  color: var(--bs-secondary-color);
}

.barra-de-acoes {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
}

.lista-interna {
  border-top: 1px solid var(--bs-border-color);
  display: grid;
  gap: 0.75rem;
  padding-top: 1rem;
}

.linha-editavel {
  border: 1px dashed var(--bs-border-color);
  border-radius: 0.6rem;
  display: grid;
  gap: 0.35rem;
  padding: 0.8rem;
}

.linha-editavel > button {
  justify-self: end;
}

.conversor-de-linhas {
  border-radius: 0.6rem;
  background: var(--bs-tertiary-bg);
  padding: 0.8rem;
}

.conversor-de-linhas button {
  justify-self: start;
}

.estado-vazio-da-estrutura {
  border: 1px dashed var(--bs-border-color);
  border-radius: 0.8rem;
  display: grid;
  justify-items: start;
  padding: 1.25rem;
}

.estado-vazio-da-estrutura h4,
.estado-vazio-da-estrutura p {
  margin: 0 0 0.5rem;
}

@media (max-width: 767px) {
  .grade-de-campos {
    grid-template-columns: minmax(0, 1fr);
  }

  .campo-largo {
    grid-column: auto;
  }

  .cabecalho-da-lista {
    align-items: stretch;
    flex-direction: column;
  }

  .secao-editavel > summary {
    align-items: start;
  }
}
</style>
