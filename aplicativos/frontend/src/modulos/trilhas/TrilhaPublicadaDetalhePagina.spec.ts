// @vitest-environment jsdom

import { flushPromises, mount } from '@vue/test-utils'
import { defineComponent } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const chamadas = vi.hoisted(() => ({
  aderirATrilhaPublicada: vi.fn(),
  atualizarAcompanhamentoDaTarefa: vi.fn(),
  obterTrilhaPublicada: vi.fn(),
}))

vi.mock('./apiDeTrilhas', () => chamadas)
vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { identificador: 'trilha-1' } }),
}))

import TrilhaPublicadaDetalhePagina from './TrilhaPublicadaDetalhePagina.vue'

const RouterLink = defineComponent({
  props: { to: { type: String, required: true } },
  template: '<a :href="to"><slot /></a>',
})

const detalhe = {
  trilha: {
    identificador: 'trilha-1',
    codigo: 'TCU-1',
    nome: 'TCU TI',
    versaoPublicada: '1.0',
    quantidadeDeDisciplinas: 1,
    quantidadeDeTarefas: 1,
    quantidadeDeTarefasConcluidas: 0,
    aderida: true,
  },
  disciplinas: [
    {
      identificador: 'disciplina-1',
      nome: 'Português',
      ordem: 1,
      tarefas: [
        {
          identificador: 'tarefa-1',
          numero: 1,
          titulo: 'Estudo da aula 00',
          tipoDeAtividade: 'TEORIA',
          situacao: 'PENDENTE',
        },
      ],
    },
  ],
}

describe('TrilhaPublicadaDetalhePagina', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    chamadas.obterTrilhaPublicada.mockResolvedValue(structuredClone(detalhe))
  })

  it('atualiza o progresso pessoal sem alterar a tarefa publicada', async () => {
    chamadas.atualizarAcompanhamentoDaTarefa.mockResolvedValue({
      ...detalhe.disciplinas[0]!.tarefas[0]!,
      situacao: 'CONCLUIDA',
      concluidaEm: '2026-08-09T12:00:00Z',
    })
    const pagina = mount(TrilhaPublicadaDetalhePagina, {
      global: { components: { RouterLink } },
    })
    await flushPromises()

    await pagina.get('select').setValue('CONCLUIDA')
    await flushPromises()

    expect(chamadas.atualizarAcompanhamentoDaTarefa).toHaveBeenCalledWith(
      'trilha-1',
      'tarefa-1',
      'CONCLUIDA',
    )
    expect(pagina.text()).toContain('1 de 1 tarefas')
  })
})
