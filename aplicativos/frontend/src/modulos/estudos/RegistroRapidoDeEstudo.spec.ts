// @vitest-environment jsdom

import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const chamadas = vi.hoisted(() => ({
  listarTodasAsMaterias: vi.fn(),
  listarTodosOsTopicos: vi.fn(),
  listarTodosOsMateriaisDeEstudo: vi.fn(),
  listarCoberturas: vi.fn(),
  registrarEstudo: vi.fn(),
}))

vi.mock('@/modulos/materias/apiDeConteudos', () => ({
  listarTodasAsMaterias: chamadas.listarTodasAsMaterias,
  listarTodosOsTopicos: chamadas.listarTodosOsTopicos,
}))
vi.mock('./apiDeEstudos', () => ({
  listarTodosOsMateriaisDeEstudo: chamadas.listarTodosOsMateriaisDeEstudo,
  listarCoberturas: chamadas.listarCoberturas,
  registrarEstudo: chamadas.registrarEstudo,
  paraEvidencia: vi.fn((modelo) =>
    modelo.dificuldadePercebida ? modelo : undefined,
  ),
  sugerirPadroesDeErro: vi.fn().mockResolvedValue([]),
}))

import RegistroRapidoDeEstudo from './RegistroRapidoDeEstudo.vue'

describe('RegistroRapidoDeEstudo', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    chamadas.listarTodasAsMaterias.mockResolvedValue([
      {
        identificador: 'materia-1',
        nome: 'Direito Constitucional',
        arquivada: false,
      },
    ])
    chamadas.listarTodosOsTopicos.mockResolvedValue([
      {
        identificador: 'topico-1',
        identificadorDaMateria: 'materia-1',
        nome: 'Direitos fundamentais',
        arquivado: false,
      },
    ])
    chamadas.listarTodosOsMateriaisDeEstudo.mockResolvedValue([
      {
        identificador: 'material-1',
        titulo: 'Curso de Constitucional',
        tipo: 'AULA',
        arquivado: false,
      },
      {
        identificador: 'material-2',
        titulo: 'Material sem cobertura',
        tipo: 'PDF',
        arquivado: false,
      },
    ])
    chamadas.listarCoberturas.mockImplementation((material: string) =>
      Promise.resolve(
        material === 'material-1'
          ? [
              {
                identificador: 'cobertura-1',
                identificadorDoMaterial: 'material-1',
                identificadorDoTopico: 'topico-1',
                nomeDoTopico: 'Direitos fundamentais',
              },
            ]
          : [],
      ),
    )
    chamadas.registrarEstudo.mockResolvedValue({})
  })

  it('oferece apenas material que cobre o topico e registra o estudo', async () => {
    const componente = mount(RegistroRapidoDeEstudo, {
      global: { stubs: { Teleport: true } },
    })
    await flushPromises()

    await componente.findAll('select')[1]!.setValue('materia-1')
    await flushPromises()
    await componente.findAll('select')[2]!.setValue('topico-1')
    await flushPromises()

    expect(componente.text()).toContain('Curso de Constitucional')
    expect(componente.text()).not.toContain('Material sem cobertura')

    await componente.findAll('select')[3]!.setValue('material-1')
    await componente.get('#registro-rapido-de-estudo').trigger('submit')
    await flushPromises()

    expect(chamadas.registrarEstudo).toHaveBeenCalledWith(
      expect.objectContaining({
        identificadorDoTopico: 'topico-1',
        identificadorDoMaterial: 'material-1',
        duracaoEmMinutos: 50,
      }),
    )
    expect(componente.emitted('registrado')).toHaveLength(1)
  })
})
