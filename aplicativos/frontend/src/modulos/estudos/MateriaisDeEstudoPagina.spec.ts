// @vitest-environment jsdom

import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const chamadas = vi.hoisted(() => ({
  adicionarCobertura: vi.fn(),
  alterarMaterialDeEstudo: vi.fn(),
  criarMaterialDeEstudo: vi.fn(),
  definirArquivamentoDoMaterial: vi.fn(),
  excluirMaterialDeEstudo: vi.fn(),
  listarCoberturas: vi.fn(),
  listarMateriaisDeEstudo: vi.fn(),
  removerCobertura: vi.fn(),
  listarMaterias: vi.fn(),
  listarTopicos: vi.fn(),
}))

vi.mock('./apiDeEstudos', () => ({
  adicionarCobertura: chamadas.adicionarCobertura,
  alterarMaterialDeEstudo: chamadas.alterarMaterialDeEstudo,
  criarMaterialDeEstudo: chamadas.criarMaterialDeEstudo,
  definirArquivamentoDoMaterial: chamadas.definirArquivamentoDoMaterial,
  excluirMaterialDeEstudo: chamadas.excluirMaterialDeEstudo,
  listarCoberturas: chamadas.listarCoberturas,
  listarMateriaisDeEstudo: chamadas.listarMateriaisDeEstudo,
  removerCobertura: chamadas.removerCobertura,
}))
vi.mock('@/modulos/materias/apiDeConteudos', () => ({
  listarMaterias: chamadas.listarMaterias,
  listarTopicos: chamadas.listarTopicos,
}))

import MateriaisDeEstudoPagina from './MateriaisDeEstudoPagina.vue'

const material = {
  identificador: 'material-1',
  titulo: 'Curso de Constitucional',
  tipo: 'AULA',
  descricao: 'Curso completo',
  arquivado: false,
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

function resposta(itens: unknown[]) {
  return {
    itens,
    pagina: 0,
    tamanho: 100,
    totalDeItens: itens.length,
    totalDePaginas: 1,
  }
}

describe('MateriaisDeEstudoPagina', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    chamadas.listarMateriaisDeEstudo.mockResolvedValue(resposta([material]))
    chamadas.listarMaterias.mockResolvedValue(resposta([materia]))
    chamadas.listarCoberturas.mockResolvedValue([])
    chamadas.listarTopicos.mockResolvedValue(resposta([topico]))
    chamadas.criarMaterialDeEstudo.mockResolvedValue(material)
    chamadas.adicionarCobertura.mockResolvedValue({
      identificador: 'cobertura-1',
      identificadorDoMaterial: 'material-1',
      identificadorDoTopico: 'topico-1',
      nomeDoTopico: topico.nome,
    })
  })

  it('lista e cadastra um material de estudo', async () => {
    const pagina = mount(MateriaisDeEstudoPagina)
    await flushPromises()

    expect(pagina.text()).toContain('Curso de Constitucional')
    await pagina.get('#titulo-material').setValue('Novo PDF')
    await pagina.get('#tipo-material').setValue('PDF')
    await pagina
      .get('#endereco-material')
      .setValue('https://exemplo.test/material.pdf')
    await pagina.get('#formulario-material').trigger('submit')
    await flushPromises()

    expect(chamadas.criarMaterialDeEstudo).toHaveBeenCalledWith(
      expect.objectContaining({
        titulo: 'Novo PDF',
        tipo: 'PDF',
        endereco: 'https://exemplo.test/material.pdf',
      }),
    )
  })

  it('vincula um topico coberto pelo material selecionado', async () => {
    const pagina = mount(MateriaisDeEstudoPagina)
    await flushPromises()

    await pagina
      .findAll('button')
      .find((botao) => botao.text() === 'Cobertura')!
      .trigger('click')
    await flushPromises()
    await pagina.get('#materia-cobertura').setValue('materia-1')
    await flushPromises()
    await pagina.get('#topico-cobertura').setValue('topico-1')
    await pagina
      .get('#topico-cobertura')
      .element.closest('form')!
      .dispatchEvent(new Event('submit'))
    await flushPromises()

    expect(chamadas.adicionarCobertura).toHaveBeenCalledWith(
      'material-1',
      'topico-1',
    )
  })
})
