<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'

import {
  ativarConcurso,
  criarCargo,
  criarConcurso,
  criarEdital,
  criarGrupo,
  criarProva,
  definirEditalPrincipal,
  selecionarCargo,
  type CaraterDaProva,
  type Cargo,
  type Concurso,
  type Edital,
  type NivelDeEscolaridade,
  type Prova,
  type SituacaoDoConcurso,
  type TipoDeProva,
} from './apiDeConcursos'

const roteador = useRouter()
const passo = ref(1)
const salvando = ref(false)
const erro = ref('')
const etapaDePersistencia = ref('')
const formularioDoPasso = ref<HTMLFormElement>()
const passos = [
  { nome: 'Objetivo', detalhe: 'Concurso e data' },
  { nome: 'Edital e cargo', detalhe: 'Seu foco' },
  { nome: 'Estrutura', detalhe: 'Prova e grupos' },
  { nome: 'Revisar', detalhe: 'Confirmar jornada' },
]

const formulario = reactive({
  nome: '',
  descricao: '',
  orgao: '',
  banca: '',
  situacao: 'PLANEJADO' as SituacaoDoConcurso,
  dataPrevistaPrincipal: '',
  tituloDoEdital: '',
  numeroDoEdital: '',
  anoDoEdital: undefined as number | undefined,
  nomeDoCargo: '',
  areaDoCargo: '',
  especialidadeDoCargo: '',
  nivelDeEscolaridade: 'SUPERIOR' as NivelDeEscolaridade,
  nomeDaProva: 'Prova objetiva',
  tipoDaProva: 'OBJETIVA' as TipoDeProva,
  caraterDaProva: 'ELIMINATORIO_E_CLASSIFICATORIO' as CaraterDaProva,
  dataHoraDaProva: '',
  quantidadeDeQuestoes: undefined as number | undefined,
  pontuacaoMaxima: undefined as number | undefined,
  grupoGeral: true,
  nomeDoGrupoGeral: 'Conhecimentos gerais',
  grupoEspecifico: true,
  nomeDoGrupoEspecifico: 'Conhecimentos específicos',
})

const progresso = reactive<{
  concurso?: Concurso
  edital?: Edital
  editalPrincipalDefinido: boolean
  cargo?: Cargo
  cargoSelecionado: boolean
  prova?: Prova
  gruposCriados: Record<string, boolean>
  concursoAtivado: boolean
}>({
  editalPrincipalDefinido: false,
  cargoSelecionado: false,
  gruposCriados: {},
  concursoAtivado: false,
})

const gruposSelecionados = computed(() =>
  [
    formulario.grupoGeral ? formulario.nomeDoGrupoGeral : '',
    formulario.grupoEspecifico ? formulario.nomeDoGrupoEspecifico : '',
  ].filter(Boolean),
)

function continuar() {
  erro.value = ''
  if (!formularioDoPasso.value?.reportValidity()) return
  if (passo.value === 3 && gruposSelecionados.value.length === 0) {
    erro.value = 'Selecione ao menos um grupo de conteúdo.'
    return
  }
  if (passo.value < 4) passo.value += 1
}

function voltar() {
  erro.value = ''
  if (passo.value === 1) {
    void roteador.push('/concursos')
    return
  }
  passo.value -= 1
}

function numeroOpcional(valor?: number) {
  return valor === undefined || Number.isNaN(valor) ? undefined : Number(valor)
}

function dataHoraComFuso(valor: string) {
  return valor ? new Date(valor).toISOString() : undefined
}

async function salvar() {
  salvando.value = true
  erro.value = ''
  try {
    if (!progresso.concurso) {
      etapaDePersistencia.value = 'Criando o objetivo...'
      progresso.concurso = await criarConcurso({
        nome: formulario.nome,
        descricao: formulario.descricao || undefined,
        orgao: formulario.orgao || undefined,
        banca: formulario.banca || undefined,
        situacao: formulario.situacao,
        dataPrevistaPrincipal: formulario.dataPrevistaPrincipal || undefined,
      })
    }

    if (!progresso.edital) {
      etapaDePersistencia.value = 'Incluindo o edital...'
      progresso.edital = await criarEdital(progresso.concurso.identificador, {
        titulo: formulario.tituloDoEdital,
        numero: formulario.numeroDoEdital || undefined,
        ano: numeroOpcional(formulario.anoDoEdital),
      })
    }
    if (!progresso.editalPrincipalDefinido) {
      etapaDePersistencia.value = 'Definindo o edital principal...'
      await definirEditalPrincipal(progresso.edital.identificador)
      progresso.editalPrincipalDefinido = true
    }

    if (!progresso.cargo) {
      etapaDePersistencia.value = 'Configurando o cargo...'
      progresso.cargo = await criarCargo(progresso.concurso.identificador, {
        nome: formulario.nomeDoCargo,
        area: formulario.areaDoCargo || undefined,
        especialidade: formulario.especialidadeDoCargo || undefined,
        nivelDeEscolaridade: formulario.nivelDeEscolaridade,
        ordem: 1,
      })
    }
    if (!progresso.cargoSelecionado) {
      etapaDePersistencia.value = 'Selecionando o cargo...'
      await selecionarCargo(progresso.cargo.identificador)
      progresso.cargoSelecionado = true
    }

    if (!progresso.prova) {
      etapaDePersistencia.value = 'Montando a prova...'
      progresso.prova = await criarProva(progresso.cargo.identificador, {
        nome: formulario.nomeDaProva,
        tipo: formulario.tipoDaProva,
        carater: formulario.caraterDaProva,
        ordem: 1,
        dataHoraPrevista: dataHoraComFuso(formulario.dataHoraDaProva),
        quantidadeDeQuestoes: numeroOpcional(formulario.quantidadeDeQuestoes),
        pontuacaoMaxima: numeroOpcional(formulario.pontuacaoMaxima),
      })
    }

    for (const [indice, nome] of gruposSelecionados.value.entries()) {
      if (progresso.gruposCriados[nome]) continue
      etapaDePersistencia.value = `Criando o grupo ${nome}...`
      await criarGrupo(progresso.prova.identificador, {
        nome,
        ordem: indice + 1,
      })
      progresso.gruposCriados[nome] = true
    }

    if (!progresso.concursoAtivado) {
      etapaDePersistencia.value = 'Ativando sua nova jornada...'
      await ativarConcurso(progresso.concurso.identificador)
      progresso.concursoAtivado = true
    }
    await roteador.push(
      `/concursos/${progresso.concurso.identificador}?novo=concluido`,
    )
  } catch (causa) {
    erro.value =
      causa instanceof Error
        ? `${causa.message} Tente novamente para continuar do ponto em que a criação parou.`
        : 'Não foi possível concluir a criação.'
  } finally {
    salvando.value = false
    etapaDePersistencia.value = ''
  }
}
</script>

<template>
  <main
    class="pagina-da-jornada pagina-do-assistente pagina-do-novo-concurso modulo-concursos-moderno"
  >
    <button
      class="btn btn-link px-0 mb-3 text-decoration-none acao-de-retorno-dos-concursos"
      type="button"
      @click="roteador.push('/concursos')"
    >
      <i class="bi bi-arrow-left me-1" aria-hidden="true"></i>
      Voltar para concursos
    </button>

    <header class="cabecalho-da-pagina cabecalho-do-assistente-de-concurso">
      <div>
        <p class="sobretitulo-da-pagina">Etapa {{ passo }} de 4</p>
        <h1>Novo concurso</h1>
        <p>
          Monte a estrutura inicial no seu ritmo. Tudo poderá ser ajustado
          depois sem perder o contexto.
        </p>
      </div>
    </header>

    <ol
      class="passos-do-assistente trilho-de-etapas-do-concurso"
      aria-label="Etapas de criação"
    >
      <li
        v-for="(item, indice) in passos"
        :key="item.nome"
        :class="{
          concluido: indice + 1 < passo,
          atual: indice + 1 === passo,
        }"
        :aria-current="indice + 1 === passo ? 'step' : undefined"
      >
        <i>
          <span v-if="indice + 1 < passo" class="bi bi-check2"></span>
          <span v-else>{{ indice + 1 }}</span>
        </i>
        <span>
          <b>{{ item.nome }}</b>
          <small>{{ item.detalhe }}</small>
        </span>
      </li>
    </ol>

    <div class="estrutura-do-assistente grade-do-assistente-de-concurso">
      <form
        ref="formularioDoPasso"
        class="card cartao-do-assistente painel-da-etapa-do-concurso"
        @submit.prevent="continuar"
      >
        <p v-if="erro" class="alert alert-danger" role="alert">{{ erro }}</p>

        <section v-if="passo === 1" class="etapa-do-assistente-de-concurso">
          <p class="sobretitulo-da-pagina">Seu objetivo</p>
          <h2 class="titulo-editorial">Qual aprovação você está buscando?</h2>
          <p class="text-secondary">
            Comece pelo essencial. A estrutura detalhada será construída nos
            próximos passos.
          </p>
          <div class="formulario-da-aplicacao mt-4">
            <label>
              <span>Nome do concurso</span>
              <input
                id="nome-concurso"
                v-model="formulario.nome"
                maxlength="160"
                placeholder="Ex.: CGU 2027"
                required
                autofocus
              />
            </label>
            <div class="duas-colunas-do-formulario">
              <label>
                <span>Órgão <em>opcional</em></span>
                <input
                  id="orgao-concurso"
                  v-model="formulario.orgao"
                  maxlength="160"
                  placeholder="Ex.: CGU"
                />
              </label>
              <label>
                <span>Banca <em>opcional</em></span>
                <input
                  id="banca-concurso"
                  v-model="formulario.banca"
                  maxlength="160"
                  placeholder="Ex.: FGV"
                />
              </label>
            </div>
            <div class="duas-colunas-do-formulario">
              <label>
                <span>Data principal prevista <em>opcional</em></span>
                <input v-model="formulario.dataPrevistaPrincipal" type="date" />
              </label>
              <label>
                <span>Situação</span>
                <select v-model="formulario.situacao">
                  <option value="PLANEJADO">Planejado</option>
                  <option value="EDITAL_PUBLICADO">Edital publicado</option>
                  <option value="INSCRICOES_ABERTAS">Inscrições abertas</option>
                  <option value="EM_ANDAMENTO">Em andamento</option>
                </select>
              </label>
            </div>
            <label>
              <span>Descrição <em>opcional</em></span>
              <textarea
                v-model="formulario.descricao"
                maxlength="1000"
                rows="3"
                placeholder="Uma nota curta para diferenciar este objetivo."
              ></textarea>
            </label>
          </div>
        </section>

        <section
          v-else-if="passo === 2"
          class="etapa-do-assistente-de-concurso"
        >
          <p class="sobretitulo-da-pagina">Edital e cargo</p>
          <h2 class="titulo-editorial">Defina o foco desta jornada</h2>
          <div class="nota-contextual mt-3">
            <i class="bi bi-file-earmark-text" aria-hidden="true"></i>
            <p>
              <strong>Vamos começar pelo edital principal.</strong>
              <span>Outros editais e cargos podem ser incluídos depois.</span>
            </p>
          </div>
          <div class="formulario-da-aplicacao mt-4">
            <label>
              <span>Título do edital</span>
              <input
                v-model="formulario.tituloDoEdital"
                maxlength="200"
                placeholder="Ex.: Edital CGU 2027"
                required
                autofocus
              />
            </label>
            <div class="duas-colunas-do-formulario">
              <label>
                <span>Número <em>opcional</em></span>
                <input
                  v-model="formulario.numeroDoEdital"
                  maxlength="80"
                  placeholder="Ex.: 01/2027"
                />
              </label>
              <label>
                <span>Ano <em>opcional</em></span>
                <input
                  v-model.number="formulario.anoDoEdital"
                  type="number"
                  min="1900"
                  max="2200"
                  placeholder="Ex.: 2027"
                />
              </label>
            </div>
            <label>
              <span>Cargo que você pretende disputar</span>
              <input
                v-model="formulario.nomeDoCargo"
                maxlength="200"
                placeholder="Ex.: Auditor Federal — TI"
                required
              />
            </label>
            <div class="duas-colunas-do-formulario">
              <label>
                <span>Área <em>opcional</em></span>
                <input v-model="formulario.areaDoCargo" maxlength="160" />
              </label>
              <label>
                <span>Especialidade <em>opcional</em></span>
                <input
                  v-model="formulario.especialidadeDoCargo"
                  maxlength="160"
                />
              </label>
            </div>
            <label>
              <span>Escolaridade</span>
              <select v-model="formulario.nivelDeEscolaridade">
                <option value="FUNDAMENTAL">Nível fundamental</option>
                <option value="MEDIO">Nível médio</option>
                <option value="TECNICO">Nível técnico</option>
                <option value="SUPERIOR">Nível superior</option>
                <option value="NAO_INFORMADO">Não informado</option>
              </select>
            </label>
          </div>
        </section>

        <section
          v-else-if="passo === 3"
          class="etapa-do-assistente-de-concurso"
        >
          <p class="sobretitulo-da-pagina">Estrutura da prova</p>
          <h2 class="titulo-editorial">Como o conteúdo será organizado?</h2>
          <p class="text-secondary">
            Crie a prova e seus grupos iniciais. As matérias serão vinculadas na
            edição contextual do concurso.
          </p>
          <div class="formulario-da-aplicacao mt-4">
            <label>
              <span>Nome da prova</span>
              <input
                v-model="formulario.nomeDaProva"
                maxlength="160"
                required
                autofocus
              />
            </label>
            <div class="duas-colunas-do-formulario">
              <label>
                <span>Tipo</span>
                <select v-model="formulario.tipoDaProva">
                  <option value="OBJETIVA">Objetiva</option>
                  <option value="DISCURSIVA">Discursiva</option>
                  <option value="PRATICA">Prática</option>
                  <option value="TITULOS">Títulos</option>
                  <option value="OUTRA">Outra</option>
                </select>
              </label>
              <label>
                <span>Caráter</span>
                <select v-model="formulario.caraterDaProva">
                  <option value="ELIMINATORIO">Eliminatório</option>
                  <option value="CLASSIFICATORIO">Classificatório</option>
                  <option value="ELIMINATORIO_E_CLASSIFICATORIO">
                    Eliminatório e classificatório
                  </option>
                  <option value="NAO_INFORMADO">Não informado</option>
                </select>
              </label>
            </div>
            <div class="duas-colunas-do-formulario">
              <label>
                <span>Data e hora <em>opcional</em></span>
                <input
                  v-model="formulario.dataHoraDaProva"
                  type="datetime-local"
                />
              </label>
              <label>
                <span>Número de questões <em>opcional</em></span>
                <input
                  v-model.number="formulario.quantidadeDeQuestoes"
                  type="number"
                  min="1"
                />
              </label>
            </div>
            <label>
              <span>Pontuação máxima <em>opcional</em></span>
              <input
                v-model.number="formulario.pontuacaoMaxima"
                type="number"
                min="0.01"
                step="0.01"
              />
            </label>
            <fieldset>
              <legend>Grupos de conteúdo</legend>
              <label class="selecao-do-assistente">
                <input v-model="formulario.grupoGeral" type="checkbox" />
                <span>
                  <b>{{ formulario.nomeDoGrupoGeral }}</b>
                  <small>Base comum da prova</small>
                </span>
              </label>
              <label class="selecao-do-assistente">
                <input v-model="formulario.grupoEspecifico" type="checkbox" />
                <span>
                  <b>{{ formulario.nomeDoGrupoEspecifico }}</b>
                  <small>Conteúdo ligado à especialidade</small>
                </span>
              </label>
            </fieldset>
          </div>
        </section>

        <section
          v-else
          class="revisao-do-assistente etapa-do-assistente-de-concurso etapa-de-revisao-do-concurso"
        >
          <span class="icone-de-confirmacao">
            <i class="bi bi-check2" aria-hidden="true"></i>
          </span>
          <p class="sobretitulo-da-pagina mt-3">Revisar e criar</p>
          <h2 class="titulo-editorial">Sua estrutura inicial está pronta</h2>
          <p class="text-secondary">
            Revise os dados. Se houver uma interrupção, você poderá tentar
            novamente sem recriar o que já foi concluído.
          </p>
          <dl>
            <div>
              <dt>Concurso</dt>
              <dd>{{ formulario.nome }}</dd>
            </div>
            <div>
              <dt>Edital</dt>
              <dd>{{ formulario.tituloDoEdital }}</dd>
            </div>
            <div>
              <dt>Cargo</dt>
              <dd>{{ formulario.nomeDoCargo }}</dd>
            </div>
            <div>
              <dt>Prova</dt>
              <dd>
                {{ formulario.nomeDaProva }}
                <span v-if="formulario.quantidadeDeQuestoes">
                  · {{ formulario.quantidadeDeQuestoes }} questões
                </span>
              </dd>
            </div>
            <div class="revisao-larga">
              <dt>Grupos</dt>
              <dd>{{ gruposSelecionados.join(' e ') }}</dd>
            </div>
          </dl>
          <p class="nota-da-ativacao">
            <i class="bi bi-bullseye" aria-hidden="true"></i>
            O concurso será definido como seu objetivo ativo.
          </p>
        </section>

        <footer class="rodape-do-assistente acoes-da-etapa-do-concurso">
          <button
            class="btn btn-link text-secondary text-decoration-none"
            type="button"
            :disabled="salvando"
            @click="voltar"
          >
            {{ passo === 1 ? 'Cancelar' : 'Voltar' }}
          </button>
          <button v-if="passo < 4" class="btn btn-primary px-4" type="submit">
            Continuar
            <i class="bi bi-arrow-right ms-2" aria-hidden="true"></i>
          </button>
          <button
            v-else
            class="btn btn-primary px-4"
            type="button"
            :disabled="salvando"
            @click="salvar"
          >
            <span
              v-if="salvando"
              class="spinner-border spinner-border-sm me-2"
              aria-hidden="true"
            ></span>
            <i v-else class="bi bi-check2 me-2" aria-hidden="true"></i>
            {{ salvando ? etapaDePersistencia : 'Criar concurso' }}
          </button>
        </footer>
      </form>

      <aside
        class="cartao-de-contexto-do-assistente contexto-do-assistente-de-concurso"
      >
        <span class="icone-redondo-da-jornada">
          <i class="bi bi-signpost-split" aria-hidden="true"></i>
        </span>
        <p class="sobretitulo-da-pagina mt-3">Jornada guiada</p>
        <h2 class="titulo-editorial">Um passo por vez</h2>
        <p>
          Organize o objetivo aos poucos e complete os detalhes no momento em
          que fizerem sentido para a sua preparação.
        </p>
        <ul>
          <li>
            <i class="bi bi-check2-circle" aria-hidden="true"></i>
            Edital principal preservado
          </li>
          <li>
            <i class="bi bi-check2-circle" aria-hidden="true"></i>
            Um cargo selecionado
          </li>
          <li>
            <i class="bi bi-check2-circle" aria-hidden="true"></i>
            Estrutura editável depois
          </li>
        </ul>
      </aside>
    </div>
  </main>
</template>
