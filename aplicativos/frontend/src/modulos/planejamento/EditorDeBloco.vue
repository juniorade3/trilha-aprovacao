<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'

import GavetaLateral from '@/compartilhado/componentes/GavetaLateral.vue'
import type { Materia, Topico } from '@/modulos/materias/apiDeConteudos'
import type {
  BlocoDeEstudo,
  DadosDoBlocoDeEstudo,
  TipoDeAtividade,
} from './apiDePlanejamento'

const propriedades = defineProps<{
  bloco?: BlocoDeEstudo
  dataInicial: string
  datasDaSemana: string[]
  dataSugerida: string
  quantidadesPorData: Record<string, number>
  materias: Materia[]
  topicos: Topico[]
  salvando: boolean
  erro?: string
  edicaoDePlanoAtivo?: boolean
}>()

const emitir = defineEmits<{
  fechar: []
  salvar: [dados: DadosDoBlocoDeEstudo]
}>()

const tituloFoiAlterado = ref(Boolean(propriedades.bloco))
const formulario = reactive({
  titulo: propriedades.bloco?.titulo ?? '',
  tipoDeAtividade: (propriedades.bloco?.tipoDeAtividade ??
    'TEORIA') as TipoDeAtividade,
  identificadorDaMateria: propriedades.bloco?.identificadorDaMateria ?? '',
  identificadorDoTopico: propriedades.bloco?.identificadorDoTopico ?? '',
  data: propriedades.bloco?.data ?? propriedades.dataSugerida,
  duracaoPrevistaEmMinutos: propriedades.bloco?.duracaoPrevistaEmMinutos ?? 60,
  ordem:
    propriedades.bloco?.ordem ??
    (propriedades.quantidadesPorData[propriedades.dataSugerida] ?? 0) + 1,
  horarioPrevisto: propriedades.bloco?.horarioPrevisto?.slice(0, 5) ?? '',
  observacao: propriedades.bloco?.observacao ?? '',
})

const topicosDaMateria = computed(() =>
  propriedades.topicos.filter(
    (topico) =>
      topico.identificadorDaMateria === formulario.identificadorDaMateria &&
      !topico.arquivado,
  ),
)

const quantidadeMaximaNaData = computed(() => {
  const existentes = propriedades.quantidadesPorData[formulario.data] ?? 0
  const jaEstaNaData = propriedades.bloco?.data === formulario.data
  return existentes + (jaEstaNaData ? 0 : 1)
})

watch(
  () => formulario.identificadorDaMateria,
  () => {
    if (
      formulario.identificadorDoTopico &&
      !topicosDaMateria.value.some(
        (topico) => topico.identificador === formulario.identificadorDoTopico,
      )
    )
      formulario.identificadorDoTopico = ''
  },
)

watch(
  () => formulario.identificadorDoTopico,
  (identificador) => {
    const topico = propriedades.topicos.find(
      (item) => item.identificador === identificador,
    )
    if (!topico) return
    formulario.identificadorDaMateria = topico.identificadorDaMateria
    if (!tituloFoiAlterado.value) formulario.titulo = topico.nome
  },
)

watch(
  () => formulario.data,
  () => {
    if (formulario.ordem > quantidadeMaximaNaData.value)
      formulario.ordem = quantidadeMaximaNaData.value
  },
)

function salvar() {
  emitir('salvar', {
    titulo: formulario.titulo,
    tipoDeAtividade: formulario.tipoDeAtividade,
    identificadorDaMateria: formulario.identificadorDaMateria || undefined,
    identificadorDoTopico: formulario.identificadorDoTopico || undefined,
    data: formulario.data,
    duracaoPrevistaEmMinutos: Number(formulario.duracaoPrevistaEmMinutos),
    ordem: Number(formulario.ordem),
    horarioPrevisto: formulario.horarioPrevisto || undefined,
    observacao: formulario.observacao || undefined,
  })
}
</script>

<template>
  <GavetaLateral
    class="gaveta-do-editor-de-bloco"
    :titulo="bloco ? 'Editar bloco' : 'Adicionar bloco'"
    etiqueta="Planejamento da semana"
    descricao="Organize uma intenção concreta de estudo."
    :larga="true"
    @fechar="emitir('fechar')"
  >
    <div
      v-if="erro"
      class="alert alert-danger mensagem-do-editor-de-bloco"
      role="alert"
    >
      <i class="bi bi-exclamation-octagon" aria-hidden="true"></i>
      <span>{{ erro }}</span>
    </div>
    <div
      v-if="bloco?.origem === 'GERADO_DETERMINISTICAMENTE'"
      class="alert alert-info mensagem-do-editor-de-bloco"
      role="status"
    >
      <i class="bi bi-shield-check" aria-hidden="true"></i>
      <span>
        Ao salvar, este bloco será preservado como “Gerado e ajustado” nas
        próximas regenerações.
      </span>
    </div>
    <form
      id="formulario-bloco"
      class="formulario-da-aplicacao editor-moderno-de-bloco"
      @submit.prevent="salvar"
    >
      <header class="cabecalho-da-secao-do-editor">
        <span>
          <i class="bi bi-card-text" aria-hidden="true"></i>
        </span>
        <div>
          <h3>Identificação</h3>
          <p>Defina o propósito, o tipo de atividade e o dia.</p>
        </div>
      </header>

      <div class="campo-principal-do-editor">
        <label for="titulo-bloco" class="form-label">Título</label>
        <input
          id="titulo-bloco"
          v-model="formulario.titulo"
          class="form-control"
          maxlength="200"
          required
          autofocus
          @input="tituloFoiAlterado = true"
        />
      </div>

      <div class="duas-colunas-do-formulario grade-da-identificacao-do-bloco">
        <div>
          <label for="tipo-bloco" class="form-label">Tipo de atividade</label>
          <select
            id="tipo-bloco"
            v-model="formulario.tipoDeAtividade"
            class="form-select"
            required
          >
            <option value="TEORIA">Teoria</option>
            <option value="QUESTOES">Questões</option>
            <option value="REVISAO">Revisão</option>
            <option value="CADERNO_DE_ERROS">Caderno de erros</option>
            <option value="SIMULADO">Simulado</option>
            <option value="DISCURSIVA">Discursiva</option>
            <option value="OUTRA">Outra</option>
          </select>
        </div>
        <div>
          <label for="data-bloco" class="form-label">Data</label>
          <select
            id="data-bloco"
            v-model="formulario.data"
            class="form-select"
            required
            :disabled="propriedades.edicaoDePlanoAtivo"
          >
            <option v-for="data in datasDaSemana" :key="data" :value="data">
              {{ data }}
            </option>
          </select>
        </div>
      </div>

      <header class="cabecalho-da-secao-do-editor">
        <span>
          <i class="bi bi-diagram-3" aria-hidden="true"></i>
        </span>
        <div>
          <h3>Conteúdo vinculado</h3>
          <p>Associe o bloco ao catálogo ou mantenha uma atividade livre.</p>
        </div>
      </header>

      <div class="duas-colunas-do-formulario grade-do-conteudo-do-bloco">
        <div>
          <label for="materia-bloco" class="form-label"
            >Matéria <span class="text-secondary">opcional</span></label
          >
          <select
            id="materia-bloco"
            v-model="formulario.identificadorDaMateria"
            class="form-select"
          >
            <option value="">Atividade livre</option>
            <option
              v-for="materia in materias"
              :key="materia.identificador"
              :value="materia.identificador"
            >
              {{ materia.nome }}
            </option>
          </select>
        </div>
        <div>
          <label for="topico-bloco" class="form-label"
            >Tópico <span class="text-secondary">opcional</span></label
          >
          <select
            id="topico-bloco"
            v-model="formulario.identificadorDoTopico"
            class="form-select"
            :disabled="!formulario.identificadorDaMateria"
          >
            <option value="">Sem tópico específico</option>
            <option
              v-for="topico in topicosDaMateria"
              :key="topico.identificador"
              :value="topico.identificador"
            >
              {{ topico.nome }}
            </option>
          </select>
        </div>
      </div>

      <header class="cabecalho-da-secao-do-editor">
        <span>
          <i class="bi bi-calendar2-week" aria-hidden="true"></i>
        </span>
        <div>
          <h3>Distribuição</h3>
          <p>Ajuste duração, posição no dia e horário opcional.</p>
        </div>
      </header>

      <div class="tres-colunas-do-formulario grade-da-distribuicao-do-bloco">
        <div>
          <label for="duracao-bloco" class="form-label"
            >Duração prevista (min)</label
          >
          <input
            id="duracao-bloco"
            v-model.number="formulario.duracaoPrevistaEmMinutos"
            class="form-control"
            type="number"
            min="1"
            max="1440"
            required
          />
        </div>
        <div>
          <label for="ordem-bloco" class="form-label">Ordem no dia</label>
          <input
            id="ordem-bloco"
            v-model.number="formulario.ordem"
            class="form-control"
            type="number"
            min="1"
            :max="quantidadeMaximaNaData"
            required
            :disabled="propriedades.edicaoDePlanoAtivo"
          />
        </div>
        <div>
          <label for="horario-bloco" class="form-label"
            >Horário <span class="text-secondary">opcional</span></label
          >
          <input
            id="horario-bloco"
            v-model="formulario.horarioPrevisto"
            class="form-control"
            type="time"
          />
        </div>
      </div>

      <div class="campo-de-observacao-do-bloco">
        <label for="observacao-bloco" class="form-label"
          >Observação <span class="text-secondary">opcional</span></label
        >
        <textarea
          id="observacao-bloco"
          v-model="formulario.observacao"
          class="form-control"
          rows="4"
          maxlength="2000"
        ></textarea>
      </div>

      <div class="d-flex justify-content-end gap-2 acoes-do-editor-de-bloco">
        <button
          class="btn btn-outline-secondary"
          type="button"
          @click="emitir('fechar')"
        >
          Cancelar
        </button>
        <button class="btn btn-primary" type="submit" :disabled="salvando">
          <span
            v-if="salvando"
            class="spinner-border spinner-border-sm me-2"
            aria-hidden="true"
          ></span>
          Salvar bloco
        </button>
      </div>
    </form>
  </GavetaLateral>
</template>
