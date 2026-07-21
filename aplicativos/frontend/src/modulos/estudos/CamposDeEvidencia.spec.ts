// @vitest-environment jsdom

import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'

vi.mock('./apiDeEstudos', async (importarOriginal) => {
  const original = await importarOriginal<typeof import('./apiDeEstudos')>()
  return { ...original, sugerirPadroesDeErro: vi.fn().mockResolvedValue([]) }
})

import CamposDeEvidencia from './CamposDeEvidencia.vue'

describe('CamposDeEvidencia', () => {
  it('calcula erros e permite padrões em atividades de questões', async () => {
    const modelo = {
      padroesDeErro: [] as Array<{
        descricao: string
        quantidadeDeOcorrencias: number
      }>,
    }
    const componente = mount(CamposDeEvidencia, {
      props: {
        tipo: 'QUESTOES',
        identificadorDoTopico: 'topico-1',
        modelValue: modelo,
      },
    })
    const numeros = componente.findAll('input[type="number"]')
    await numeros[0]!.setValue(10)
    await numeros[1]!.setValue(7)

    expect(componente.text()).toContain('Erros calculados: 3')
    await componente.get('button').trigger('click')
    expect(modelo.padroesDeErro).toHaveLength(1)
  })

  it('exige recordação na revisão concluída e a torna opcional na interrupção', () => {
    const concluida = mount(CamposDeEvidencia, {
      props: { tipo: 'REVISAO', modelValue: { padroesDeErro: [] } },
    })
    expect(
      concluida.get('input[type="number"]').attributes('required'),
    ).toBeDefined()

    const interrompida = mount(CamposDeEvidencia, {
      props: {
        tipo: 'REVISAO',
        interrupcao: true,
        modelValue: { padroesDeErro: [] },
      },
    })
    expect(
      interrompida.get('input[type="number"]').attributes('required'),
    ).toBeUndefined()
  })

  it('remove padrões ocultos quando não restam erros', async () => {
    const modelo = {
      quantidadeDeQuestoes: 10,
      quantidadeDeAcertos: 7,
      padroesDeErro: [
        { descricao: 'Erro de sinal', quantidadeDeOcorrencias: 1 },
      ],
    }
    const componente = mount(CamposDeEvidencia, {
      props: { tipo: 'QUESTOES', modelValue: modelo },
    })

    const numeros = componente.findAll('input[type="number"]')
    await numeros[1]!.setValue(10)

    expect(modelo.padroesDeErro).toEqual([])
    expect(componente.text()).not.toContain('Padrões de erro')
  })
})
