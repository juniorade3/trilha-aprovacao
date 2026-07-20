<script setup lang="ts">
import { onMounted, ref } from 'vue'

import GavetaLateral from '@/compartilhado/componentes/GavetaLateral.vue'
import {
  gerarPreviaDeterministica,
  listarMateriasParaGeracao,
  substituirPrioridadesDeMaterias,
  type MateriaParaGeracao,
  type PreviaDaGeracao,
  type PrioridadeDaMateriaNoPlano,
} from './apiDePlanejamento'

const propriedades = defineProps<{ identificadorDoPlano: string }>()
const emitir = defineEmits<{ fechar: [] }>()

const etapa = ref<'PRIORIDADES' | 'CONFIGURACAO' | 'PREVIA'>('PRIORIDADES')
const materias = ref<MateriaParaGeracao[]>([])
const previa = ref<PreviaDaGeracao>()
const duracaoPrincipal = ref(50)
const duracaoDaRevisao = ref(20)
const carregando = ref(true)
const processando = ref(false)
const erro = ref('')

const rotulos: Record<PrioridadeDaMateriaNoPlano, string> = {
  ALTA: 'Alta',
  NORMAL: 'Normal',
  BAIXA: 'Baixa',
  NAO_INCLUIR: 'Não incluir',
}

async function carregarMaterias() {
  carregando.value = true
  erro.value = ''
  try {
    materias.value = await listarMateriasParaGeracao(
      propriedades.identificadorDoPlano,
    )
  } catch (causa) {
    erro.value =
      causa instanceof Error
        ? causa.message
        : 'Não foi possível carregar as matérias elegíveis.'
  } finally {
    carregando.value = false
  }
}

async function salvarPrioridades() {
  processando.value = true
  erro.value = ''
  try {
    materias.value = await substituirPrioridadesDeMaterias(
      propriedades.identificadorDoPlano,
      materias.value.map((materia) => ({
        identificadorDaMateria: materia.identificadorDaMateria,
        prioridade: materia.prioridade,
      })),
    )
    previa.value = undefined
    etapa.value = 'CONFIGURACAO'
  } catch (causa) {
    erro.value =
      causa instanceof Error
        ? causa.message
        : 'Não foi possível salvar as prioridades.'
  } finally {
    processando.value = false
  }
}

async function calcularPrevia() {
  processando.value = true
  erro.value = ''
  try {
    previa.value = await gerarPreviaDeterministica(
      propriedades.identificadorDoPlano,
      Number(duracaoPrincipal.value),
      Number(duracaoDaRevisao.value),
    )
    etapa.value = 'PREVIA'
  } catch (causa) {
    erro.value =
      causa instanceof Error
        ? causa.message
        : 'Não foi possível calcular a prévia.'
  } finally {
    processando.value = false
  }
}

function formatarData(data: string) {
  return new Intl.DateTimeFormat('pt-BR', {
    weekday: 'long',
    day: '2-digit',
    month: 'short',
  }).format(new Date(`${data}T12:00:00`))
}

onMounted(carregarMaterias)
</script>

<template>
  <GavetaLateral
    titulo="Gerar semana"
    etiqueta="Geração determinística"
    descricao="Ajuste a estratégia e confira o resultado antes de alterar o plano."
    :larga="true"
    @fechar="emitir('fechar')"
  >
    <nav class="etapas-da-geracao" aria-label="Etapas da geração">
      <button
        v-for="item in ['PRIORIDADES', 'CONFIGURACAO', 'PREVIA'] as const"
        :key="item"
        class="etapa-da-geracao"
        :class="{ ativa: etapa === item }"
        type="button"
        :disabled="item === 'PREVIA' && !previa"
        @click="etapa = item"
      >
        {{
          item === 'PRIORIDADES'
            ? '1. Prioridades'
            : item === 'CONFIGURACAO'
              ? '2. Configuração'
              : '3. Prévia'
        }}
      </button>
    </nav>

    <div v-if="erro" class="alert alert-danger" role="alert">
      {{ erro }}
      <button
        class="btn btn-sm btn-outline-danger ms-2"
        type="button"
        @click="carregarMaterias"
      >
        Tentar novamente
      </button>
    </div>

    <div v-if="carregando" class="geracao-carregando" role="status">
      <span class="spinner-border spinner-border-sm" aria-hidden="true"></span>
      Carregando matérias do concurso…
    </div>

    <section
      v-else-if="etapa === 'PRIORIDADES' && materias.length"
      aria-labelledby="titulo-prioridades"
    >
      <h3 id="titulo-prioridades">Prioridades desta semana</h3>
      <p>Matérias ausentes de configuração permanecem com prioridade Normal.</p>
      <div class="lista-de-prioridades">
        <label
          v-for="materia in materias"
          :key="materia.identificadorDaMateria"
        >
          <span>{{ materia.nome }}</span>
          <select v-model="materia.prioridade" class="form-select">
            <option
              v-for="(rotulo, valor) in rotulos"
              :key="valor"
              :value="valor"
            >
              {{ rotulo }}
            </option>
          </select>
        </label>
      </div>
      <button
        class="btn btn-primary w-100"
        type="button"
        :disabled="processando"
        @click="salvarPrioridades"
      >
        {{ processando ? 'Salvando…' : 'Salvar prioridades e continuar' }}
      </button>
    </section>

    <section
      v-else-if="etapa === 'CONFIGURACAO' && materias.length"
      aria-labelledby="titulo-configuracao"
    >
      <h3 id="titulo-configuracao">Configuração dos blocos</h3>
      <p>O cálculo respeita a disponibilidade e os blocos já existentes.</p>
      <div class="grade-da-configuracao">
        <label>
          <span>Duração principal</span>
          <input
            v-model.number="duracaoPrincipal"
            class="form-control"
            type="number"
            min="25"
            max="180"
            required
          />
          <small>Entre 25 e 180 minutos.</small>
        </label>
        <label>
          <span>Duração da revisão</span>
          <input
            v-model.number="duracaoDaRevisao"
            class="form-control"
            type="number"
            min="0"
            max="120"
            required
          />
          <small>Use zero para não sugerir revisão.</small>
        </label>
      </div>
      <div class="d-flex gap-2">
        <button
          class="btn btn-outline-primary"
          type="button"
          @click="etapa = 'PRIORIDADES'"
        >
          Voltar
        </button>
        <button
          class="btn btn-primary flex-grow-1"
          type="button"
          :disabled="processando"
          @click="calcularPrevia"
        >
          {{ processando ? 'Calculando…' : 'Calcular prévia' }}
        </button>
      </div>
    </section>

    <section
      v-else-if="etapa === 'PREVIA' && previa"
      aria-labelledby="titulo-previa"
    >
      <div class="cabecalho-da-previa">
        <div>
          <h3 id="titulo-previa">Prévia da semana</h3>
          <p>Nada foi aplicado ao seu plano. Você pode ajustar e recalcular.</p>
        </div>
        <button
          class="btn btn-outline-primary"
          type="button"
          @click="etapa = 'PRIORIDADES'"
        >
          Ajustar
        </button>
      </div>
      <div
        v-for="aviso in previa.avisos"
        :key="aviso.codigo"
        class="alert alert-warning py-2"
      >
        {{ aviso.mensagem }}
      </div>
      <div class="dias-da-previa">
        <article
          v-for="dia in previa.dias"
          :key="dia.data"
          class="dia-da-previa"
        >
          <header>
            <strong>{{ formatarData(dia.data) }}</strong>
            <span
              >{{ dia.capacidade.minutosSugeridos }} sugeridos ·
              {{ dia.capacidade.minutosLivres }} livres</span
            >
          </header>
          <div class="barra-de-capacidade" aria-hidden="true">
            <span
              :style="{
                width: `${dia.capacidade.minutosDisponiveis ? Math.min(100, ((dia.capacidade.minutosPreservados + dia.capacidade.minutosSugeridos) / dia.capacidade.minutosDisponiveis) * 100) : 0}%`,
              }"
            ></span>
          </div>
          <div
            v-for="bloco in dia.blocosPreservados"
            :key="bloco.identificador"
            class="bloco-da-previa preservado"
          >
            <span>Preservado</span><strong>{{ bloco.titulo }}</strong
            ><small>{{ bloco.duracaoEmMinutos }} min</small>
          </div>
          <div
            v-for="(bloco, indice) in dia.blocosSugeridos"
            :key="`${dia.data}-${indice}`"
            class="bloco-da-previa sugerido"
          >
            <span>{{
              bloco.tipoDeAtividade === 'REVISAO' ? 'Revisão' : 'Sugestão'
            }}</span>
            <strong>{{ bloco.titulo }}</strong
            ><small>{{ bloco.duracaoEmMinutos }} min</small>
            <ul>
              <li v-for="motivo in bloco.justificativas" :key="motivo.codigo">
                {{ motivo.mensagem }}
              </li>
            </ul>
          </div>
          <p
            v-for="aviso in dia.avisos"
            :key="aviso.codigo"
            class="aviso-do-dia"
          >
            <i class="bi bi-info-circle" aria-hidden="true"></i>
            {{ aviso.mensagem }}
          </p>
        </article>
      </div>
      <div class="alert alert-info mb-0" role="status">
        <strong>Prévia somente para conferência.</strong> Nenhum bloco foi
        criado ou alterado.
      </div>
    </section>
  </GavetaLateral>
</template>

<style scoped lang="scss">
.etapas-da-geracao {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 0.5rem;
  margin-bottom: 1.5rem;
}
.etapa-da-geracao {
  border: 0;
  border-bottom: 3px solid #dce5e2;
  background: transparent;
  padding: 0.75rem 0.4rem;
  color: #60716c;
  font-weight: 700;
}
.etapa-da-geracao.ativa {
  border-color: var(--bs-primary);
  color: var(--bs-primary);
}
.geracao-carregando {
  display: flex;
  justify-content: center;
  gap: 0.75rem;
  padding: 3rem;
}
.lista-de-prioridades {
  display: grid;
  gap: 0.75rem;
  margin: 1.25rem 0;
}
.lista-de-prioridades label {
  display: grid;
  grid-template-columns: 1fr minmax(140px, 190px);
  align-items: center;
  gap: 1rem;
  border: 1px solid #e0e6e3;
  border-radius: 0.75rem;
  padding: 0.85rem 1rem;
}
.grade-da-configuracao {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 1rem;
  margin: 1.5rem 0;
}
.grade-da-configuracao label {
  display: grid;
  gap: 0.4rem;
}
.cabecalho-da-previa,
.dia-da-previa header {
  display: flex;
  justify-content: space-between;
  gap: 1rem;
}
.dias-da-previa {
  display: grid;
  gap: 1rem;
  margin: 1rem 0;
}
.dia-da-previa {
  border: 1px solid #dfe7e4;
  border-radius: 1rem;
  padding: 1rem;
  background: #fff;
}
.dia-da-previa header span {
  color: #60716c;
  font-size: 0.875rem;
}
.barra-de-capacidade {
  height: 6px;
  background: #edf2f0;
  border-radius: 999px;
  margin: 0.75rem 0;
  overflow: hidden;
}
.barra-de-capacidade span {
  display: block;
  height: 100%;
  background: var(--bs-primary);
}
.bloco-da-previa {
  display: grid;
  grid-template-columns: 90px 1fr auto;
  gap: 0.5rem;
  padding: 0.65rem 0.75rem;
  border-radius: 0.65rem;
  margin-top: 0.5rem;
}
.bloco-da-previa.preservado {
  background: #f1f3f4;
}
.bloco-da-previa.sugerido {
  background: #eaf7f3;
}
.bloco-da-previa > span {
  color: #60716c;
  font-size: 0.75rem;
  text-transform: uppercase;
  font-weight: 700;
}
.bloco-da-previa ul {
  grid-column: 2 / -1;
  margin: 0;
  padding-left: 1rem;
  color: #60716c;
  font-size: 0.8rem;
}
.aviso-do-dia {
  color: #725b16;
  font-size: 0.85rem;
  margin: 0.6rem 0 0;
}
@media (max-width: 576px) {
  .etapas-da-geracao {
    grid-template-columns: 1fr;
  }
  .lista-de-prioridades label,
  .grade-da-configuracao {
    grid-template-columns: 1fr;
  }
  .cabecalho-da-previa,
  .dia-da-previa header {
    flex-direction: column;
  }
  .bloco-da-previa {
    grid-template-columns: 1fr auto;
  }
  .bloco-da-previa > span {
    grid-column: 1 / -1;
  }
  .bloco-da-previa ul {
    grid-column: 1 / -1;
  }
}
</style>
