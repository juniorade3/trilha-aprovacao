// @vitest-environment jsdom

import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const chamadas = vi.hoisted(() => ({
  listar: vi.fn(),
  substituir: vi.fn(),
  gerar: vi.fn(),
}))

vi.mock('./apiDePlanejamento', () => ({
  listarMateriasParaGeracao: chamadas.listar,
  substituirPrioridadesDeMaterias: chamadas.substituir,
  gerarPreviaDeterministica: chamadas.gerar,
}))

import GavetaDeGeracaoDeterministica from './GavetaDeGeracaoDeterministica.vue'

const montadas: ReturnType<typeof mount>[] = []
const materias = [
  {
    identificadorDaMateria: 'materia-1',
    nome: 'Banco de dados',
    ordemEstavel: 1,
    prioridade: 'NORMAL',
  },
  {
    identificadorDaMateria: 'materia-2',
    nome: 'Redes',
    ordemEstavel: 2,
    prioridade: 'NORMAL',
  },
]

function previa() {
  return {
    identificadorDoPlano: 'plano-1',
    aplicada: false,
    avisos: [
      {
        codigo: 'POUCAS_MATERIAS_ELEGIVEIS',
        mensagem: 'Há poucas matérias elegíveis.',
      },
    ],
    dias: Array.from({ length: 7 }, (_, indice) => ({
      data: `2026-07-${20 + indice}`,
      capacidade: {
        minutosDisponiveis: indice ? 0 : 70,
        minutosPreservados: 0,
        minutosSugeridos: indice ? 0 : 70,
        minutosLivres: 0,
      },
      blocosPreservados: [],
      blocosSugeridos: indice
        ? []
        : [
            {
              titulo: 'Revisão do dia',
              tipoDeAtividade: 'REVISAO',
              duracaoEmMinutos: 20,
              justificativas: [
                { codigo: 'REVISAO_RESERVADA', mensagem: 'Revisão reservada.' },
              ],
            },
            {
              identificadorDaMateria: 'materia-1',
              nomeDaMateria: 'Banco de dados',
              titulo: 'Banco de dados',
              tipoDeAtividade: 'TEORIA',
              duracaoEmMinutos: 50,
              justificativas: [
                { codigo: 'PRIORIDADE_ALTA', mensagem: 'Prioridade alta.' },
              ],
            },
          ],
      avisos: [],
    })),
  }
}

async function montar() {
  const componente = mount(GavetaDeGeracaoDeterministica, {
    attachTo: document.body,
    props: { identificadorDoPlano: 'plano-1' },
  })
  montadas.push(componente)
  await flushPromises()
  return componente
}

describe('GavetaDeGeracaoDeterministica', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    chamadas.listar.mockResolvedValue(structuredClone(materias))
    chamadas.substituir.mockImplementation(
      async (
        _plano: string,
        prioridades: { identificadorDaMateria: string; prioridade: string }[],
      ) =>
        materias.map((materia) => ({
          ...materia,
          prioridade:
            prioridades.find(
              (item) =>
                item.identificadorDaMateria === materia.identificadorDaMateria,
            )?.prioridade ?? materia.prioridade,
        })),
    )
    chamadas.gerar.mockResolvedValue(previa())
  })

  afterEach(() => {
    for (const componente of montadas.splice(0)) componente.unmount()
  })

  it('percorre prioridades, configuracao e previa sem oferecer aplicacao', async () => {
    const componente = await montar()
    expect(componente.text()).toContain('Banco de dados')

    await componente.find('select').setValue('ALTA')
    await componente.get('button.btn-primary.w-100').trigger('click')
    await flushPromises()
    expect(chamadas.substituir).toHaveBeenCalledWith('plano-1', [
      { identificadorDaMateria: 'materia-1', prioridade: 'ALTA' },
      { identificadorDaMateria: 'materia-2', prioridade: 'NORMAL' },
    ])
    expect(componente.text()).toContain('Configuração dos blocos')

    await componente.find('button.btn-primary.flex-grow-1').trigger('click')
    await flushPromises()
    expect(chamadas.gerar).toHaveBeenCalledWith('plano-1', 50, 20)
    expect(componente.findAll('.dia-da-previa')).toHaveLength(7)
    expect(componente.text()).toContain('Nada foi aplicado ao seu plano')
    expect(componente.text()).not.toContain('Aplicar prévia')
  })

  it('exibe vazio recuperavel quando nao ha concurso ativo ou cargo', async () => {
    chamadas.listar.mockRejectedValueOnce(
      new Error('Defina um concurso ativo antes de gerar a semana.'),
    )
    const componente = await montar()

    expect(componente.get('[role="alert"]').text()).toContain('concurso ativo')
    expect(componente.text()).toContain('Tentar novamente')
  })
})
