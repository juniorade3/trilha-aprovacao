// @vitest-environment jsdom

import { flushPromises, mount } from '@vue/test-utils'
import { defineComponent } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const chamadas = vi.hoisted(() => ({ listarTrilhasPublicadas: vi.fn() }))

vi.mock('./apiDeTrilhas', () => chamadas)

import TrilhasPublicadasPagina from './TrilhasPublicadasPagina.vue'

const RouterLink = defineComponent({
  props: { to: { type: String, required: true } },
  template: '<a :href="to"><slot /></a>',
})

describe('TrilhasPublicadasPagina', () => {
  beforeEach(() => chamadas.listarTrilhasPublicadas.mockReset())

  it('lista uma trilha publicada e apresenta o progresso da adesão', async () => {
    chamadas.listarTrilhasPublicadas.mockResolvedValue([
      {
        identificador: 'trilha-1',
        codigo: 'TCU-1',
        nome: 'TCU TI',
        versaoPublicada: '1.0',
        quantidadeDeDisciplinas: 15,
        quantidadeDeTarefas: 356,
        quantidadeDeTarefasConcluidas: 12,
        aderida: true,
      },
    ])

    const pagina = mount(TrilhasPublicadasPagina, {
      global: { components: { RouterLink } },
    })
    await flushPromises()

    expect(pagina.text()).toContain('TCU TI')
    expect(pagina.text()).toContain('12 concluídas')
    expect(pagina.get('a[href="/trilhas/trilha-1"]').text()).toContain(
      'Continuar trilha',
    )
  })
})
