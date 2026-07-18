<script setup lang="ts">
import { computed } from 'vue'

import type { Topico } from './apiDeConteudos'

defineOptions({ name: 'ArvoreDeTopicos' })

const propriedades = defineProps<{
  topicos: Topico[]
  identificadorDoPai?: string
}>()

defineEmits<{
  editar: [topico: Topico]
  arquivar: [topico: Topico]
  excluir: [topico: Topico]
}>()

const filhos = computed(() =>
  propriedades.topicos
    .filter(
      (topico) =>
        (topico.identificadorDoTopicoPai ?? undefined) ===
        propriedades.identificadorDoPai,
    )
    .sort(
      (primeiro, segundo) =>
        primeiro.ordem - segundo.ordem ||
        primeiro.nome.localeCompare(segundo.nome),
    ),
)
</script>

<template>
  <ul v-if="filhos.length" class="lista-de-topicos">
    <li v-for="topico in filhos" :key="topico.identificador">
      <article
        class="card card-body border-0 bg-body-tertiary mb-2"
        :class="{ 'opacity-75': topico.arquivado }"
      >
        <div class="d-flex flex-wrap gap-2 align-items-start">
          <span class="badge text-bg-light">{{ topico.ordem }}</span>
          <div class="flex-grow-1">
            <h3 class="h6 mb-1">
              {{ topico.nome }}
              <span
                v-if="topico.arquivado"
                class="badge text-bg-secondary ms-1"
              >
                Arquivado
              </span>
            </h3>
            <p v-if="topico.descricao" class="small text-secondary mb-0">
              {{ topico.descricao }}
            </p>
          </div>
          <div class="d-flex gap-1">
            <button
              class="btn btn-sm btn-outline-primary"
              type="button"
              :disabled="topico.arquivado"
              :aria-label="`Editar ${topico.nome}`"
              @click="$emit('editar', topico)"
            >
              <i class="bi bi-pencil" aria-hidden="true"></i>
            </button>
            <button
              class="btn btn-sm btn-outline-secondary"
              type="button"
              :aria-label="
                topico.arquivado
                  ? `Restaurar ${topico.nome}`
                  : `Arquivar ${topico.nome}`
              "
              @click="$emit('arquivar', topico)"
            >
              <i
                class="bi"
                :class="
                  topico.arquivado ? 'bi-arrow-counterclockwise' : 'bi-archive'
                "
                aria-hidden="true"
              ></i>
            </button>
            <button
              class="btn btn-sm btn-outline-danger"
              type="button"
              :aria-label="`Excluir ${topico.nome}`"
              @click="$emit('excluir', topico)"
            >
              <i class="bi bi-trash" aria-hidden="true"></i>
            </button>
          </div>
        </div>
      </article>
      <ArvoreDeTopicos
        :topicos="topicos"
        :identificador-do-pai="topico.identificador"
        @editar="$emit('editar', $event)"
        @arquivar="$emit('arquivar', $event)"
        @excluir="$emit('excluir', $event)"
      />
    </li>
  </ul>
</template>
