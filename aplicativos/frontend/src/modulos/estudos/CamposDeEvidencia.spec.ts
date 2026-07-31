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

  it('expõe os limites do contrato nos campos numéricos', async () => {
    const modelo = {
      quantidadeDeQuestoes: 10,
      quantidadeDeAcertos: 7,
      padroesDeErro: [] as Array<{
        descricao: string
        quantidadeDeOcorrencias: number
      }>,
    }
    const componente = mount(CamposDeEvidencia, {
      props: { tipo: 'QUESTOES', modelValue: modelo },
    })
    const numeros = componente.findAll('input[type="number"]')

    expect(numeros[0]!.attributes('min')).toBe('1')
    expect(numeros[1]!.attributes('min')).toBe('0')
    expect(numeros[1]!.attributes('max')).toBe('10')
    expect(numeros[2]!.attributes('min')).toBe('1')
    expect(numeros[2]!.attributes('max')).toBe('5')

    await numeros[0]!.setValue(0)
    expect(
      (numeros[0]!.element as HTMLInputElement).validity.rangeUnderflow,
    ).toBe(true)
    await numeros[0]!.setValue(10)
    await numeros[1]!.setValue(11)
    expect(
      (numeros[1]!.element as HTMLInputElement).validity.rangeOverflow,
    ).toBe(true)

    await numeros[1]!.setValue(7)
    await componente
      .findAll('button')
      .find((botao) => botao.text() === 'Adicionar padrão')!
      .trigger('click')
    const numerosComOcorrencias = componente.findAll('input[type="number"]')
    const ocorrencias = numerosComOcorrencias[numerosComOcorrencias.length - 1]!
    const descricao = componente.get('input[list]')
    expect(ocorrencias.attributes('min')).toBe('1')
    expect(ocorrencias.attributes('max')).toBe('3')
    expect(descricao.attributes('maxlength')).toBe('200')
  })

  it('limpa resultados incompatíveis ao trocar o tipo de estudo', async () => {
    const modelo = {
      quantidadeDeQuestoes: 10,
      quantidadeDeAcertos: 7,
      nivelDeRecordacao: 4,
      dificuldadePercebida: 3,
      padroesDeErro: [
        { descricao: 'Erro de sinal', quantidadeDeOcorrencias: 1 },
      ],
    }
    const componente = mount(CamposDeEvidencia, {
      props: { tipo: 'QUESTOES', modelValue: modelo },
    })

    await componente.setProps({ tipo: 'REVISAO' })
    expect(modelo.quantidadeDeQuestoes).toBeUndefined()
    expect(modelo.quantidadeDeAcertos).toBeUndefined()
    expect(modelo.padroesDeErro).toEqual([])
    expect(modelo.dificuldadePercebida).toBe(3)

    await componente.setProps({ tipo: 'TEORIA' })
    expect(modelo.nivelDeRecordacao).toBeUndefined()
  })
})
