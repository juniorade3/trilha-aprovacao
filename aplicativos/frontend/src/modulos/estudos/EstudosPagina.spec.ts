// @vitest-environment jsdom

import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const chamadas = vi.hoisted(() => ({
  cancelarEstudo: vi.fn(),
  corrigirEstudo: vi.fn(),
  listarCoberturas: vi.fn(),
  listarEstudos: vi.fn(),
  listarMateriaisDeEstudo: vi.fn(),
  registrarEstudo: vi.fn(),
  listarMaterias: vi.fn(),
  listarTopicos: vi.fn(),
}))

vi.mock('./apiDeEstudos', () => ({
  cancelarEstudo: chamadas.cancelarEstudo,
  corrigirEstudo: chamadas.corrigirEstudo,
  listarCoberturas: chamadas.listarCoberturas,
  listarEstudos: chamadas.listarEstudos,
  listarMateriaisDeEstudo: chamadas.listarMateriaisDeEstudo,
  registrarEstudo: chamadas.registrarEstudo,
}))
vi.mock('@/modulos/materias/apiDeConteudos', () => ({
  listarMaterias: chamadas.listarMaterias,
  listarTopicos: chamadas.listarTopicos,
}))

import EstudosPagina from './EstudosPagina.vue'

const registro = {
  identificador: 'estudo-1',
  identificadorDoTopico: 'topico-1',
  nomeDoTopico: 'Direitos fundamentais',
  identificadorDoMaterial: 'material-1',
  tituloDoMaterial: 'Curso de Constitucional',
  dataHora: '2026-07-18T10:00:00Z',
  duracaoEmMinutos: 45,
  observacao: 'Revisao',
  situacao: 'ATIVO',
  criadoEm: '2026-07-18T10:00:00Z',
  atualizadoEm: '2026-07-18T10:00:00Z',
  versao: 0,
}
const materia = {
  identificador: 'materia-1',
  nome: 'Direito Constitucional',
  arquivada: false,
}
const topico = {
  identificador: 'topico-1',
  identificadorDaMateria: 'materia-1',
  nome: 'Direitos fundamentais',
  arquivado: false,
}
const material = {
  identificador: 'material-1',
  titulo: 'Curso de Constitucional',
  tipo: 'AULA',
  arquivado: false,
}

function resposta(itens: unknown[]) {
  return {
    itens,
    pagina: 0,
    tamanho: 100,
    totalDeItens: itens.length,
    totalDePaginas: 1,
  }
}

describe('EstudosPagina', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    chamadas.listarEstudos.mockResolvedValue(resposta([registro]))
    chamadas.listarMaterias.mockResolvedValue(resposta([materia]))
    chamadas.listarMateriaisDeEstudo.mockResolvedValue(resposta([material]))
    chamadas.listarTopicos.mockResolvedValue(resposta([topico]))
    chamadas.listarCoberturas.mockResolvedValue([
      {
        identificador: 'cobertura-1',
        identificadorDoMaterial: 'material-1',
        identificadorDoTopico: 'topico-1',
        nomeDoTopico: topico.nome,
      },
    ])
    chamadas.registrarEstudo.mockResolvedValue(registro)
    chamadas.corrigirEstudo.mockResolvedValue(registro)
    chamadas.cancelarEstudo.mockResolvedValue({
      ...registro,
      situacao: 'CANCELADO',
    })
  })

  it('registra um estudo usando apenas material que cobre o topico', async () => {
    const pagina = mount(EstudosPagina)
    await flushPromises()

    await pagina.get('#materia-estudo').setValue('materia-1')
    await pagina.get('#topico-estudo').setValue('topico-1')
    await pagina.get('#material-estudo').setValue('material-1')
    await pagina.get('#duracao-estudo').setValue(90)
    await pagina.get('#formulario-estudo').trigger('submit')
    await flushPromises()

    expect(chamadas.registrarEstudo).toHaveBeenCalledWith(
      expect.objectContaining({
        identificadorDoTopico: 'topico-1',
        identificadorDoMaterial: 'material-1',
        duracaoEmMinutos: 90,
      }),
    )
  })

  it('corrige e cancela um registro ativo sem apaga-lo', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    const pagina = mount(EstudosPagina)
    await flushPromises()

    await pagina
      .findAll('button')
      .find((botao) => botao.text() === 'Corrigir')!
      .trigger('click')
    await pagina.get('#duracao-estudo').setValue(50)
    await pagina.get('#formulario-estudo').trigger('submit')
    await flushPromises()

    expect(chamadas.corrigirEstudo).toHaveBeenCalledWith(
      'estudo-1',
      expect.objectContaining({ duracaoEmMinutos: 50 }),
    )

    await pagina
      .findAll('button')
      .find((botao) => botao.text() === 'Cancelar')!
      .trigger('click')
    await flushPromises()

    expect(chamadas.cancelarEstudo).toHaveBeenCalledWith('estudo-1')
  })
})
