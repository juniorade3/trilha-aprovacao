// @vitest-environment jsdom

import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const chamadas = vi.hoisted(() => ({
  alterarCargo: vi.fn(),
  alterarConcurso: vi.fn(),
  alterarEdital: vi.fn(),
  alterarGrupo: vi.fn(),
  alterarMateriaDaProva: vi.fn(),
  alterarProva: vi.fn(),
  arquivarConcurso: vi.fn(),
  criarCargo: vi.fn(),
  criarEdital: vi.fn(),
  criarGrupo: vi.fn(),
  criarMateriaDaProva: vi.fn(),
  criarProva: vi.fn(),
  definirEditalPrincipal: vi.fn(),
  excluirCargo: vi.fn(),
  excluirEdital: vi.fn(),
  excluirGrupo: vi.fn(),
  excluirMateriaDaProva: vi.fn(),
  excluirProva: vi.fn(),
  listarCargos: vi.fn(),
  listarEditais: vi.fn(),
  listarGrupos: vi.fn(),
  listarMateriasDaProva: vi.fn(),
  listarMateriasDisponiveis: vi.fn(),
  listarProvas: vi.fn(),
  obterConcurso: vi.fn(),
  selecionarCargo: vi.fn(),
}))

vi.mock('./apiDeConcursos', () => chamadas)

import ConcursoDetalhePagina from './ConcursoDetalhePagina.vue'

const concurso = {
  identificador: 'concurso-1',
  nome: 'Receita Federal',
  situacao: 'PLANEJADO',
  ativo: true,
  criadoEm: '2026-07-18T10:00:00Z',
  atualizadoEm: '2026-07-18T10:00:00Z',
  versao: 0,
}

function criarRoteador() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      {
        path: '/concursos/:identificador',
        component: ConcursoDetalhePagina,
      },
      { path: '/concursos', component: { template: '<div />' } },
    ],
  })
}

async function montar() {
  const roteador = criarRoteador()
  await roteador.push('/concursos/concurso-1')
  await roteador.isReady()
  return mount(ConcursoDetalhePagina, {
    global: { plugins: [roteador] },
  })
}

describe('ConcursoDetalhePagina', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    chamadas.obterConcurso.mockResolvedValue(concurso)
    chamadas.listarEditais.mockResolvedValue([])
    chamadas.listarCargos.mockResolvedValue([
      {
        identificador: 'cargo-1',
        identificadorDoConcurso: 'concurso-1',
        nome: 'Auditor',
        nivelDeEscolaridade: 'SUPERIOR',
        selecionado: true,
        ordem: 1,
      },
    ])
    chamadas.listarMateriasDisponiveis.mockResolvedValue({
      itens: [
        { identificador: 'materia-1', nome: 'Direito', arquivada: false },
      ],
    })
    chamadas.listarProvas.mockResolvedValue([
      {
        identificador: 'prova-1',
        identificadorDoCargo: 'cargo-1',
        nome: 'Objetiva',
        tipo: 'OBJETIVA',
        carater: 'ELIMINATORIO',
        ordem: 1,
      },
    ])
    chamadas.listarGrupos.mockResolvedValue([
      {
        identificador: 'grupo-1',
        identificadorDaProva: 'prova-1',
        nome: 'Conhecimentos especificos',
        ordem: 1,
      },
    ])
    chamadas.listarMateriasDaProva.mockResolvedValue([
      {
        identificador: 'vinculo-1',
        identificadorDoGrupoDeConteudo: 'grupo-1',
        identificadorDaMateria: 'materia-1',
        nomeDaMateria: 'Direito',
        ordem: 1,
      },
    ])
  })

  it('apresenta cargo, prova, grupo e materia na hierarquia', async () => {
    const pagina = await montar()
    await flushPromises()

    expect(pagina.get('h1').text()).toBe('Receita Federal')
    expect(pagina.text()).toContain('3. Auditor')
    expect(pagina.text()).toContain('4. Objetiva')
    expect(pagina.text()).toContain('5. Conhecimentos especificos')
    expect(pagina.text()).toContain('6. Direito')
  })

  it('mantem o formulario preenchido quando a API rejeita a criacao', async () => {
    chamadas.criarEdital.mockRejectedValue(new Error('Edital invalido.'))
    const pagina = await montar()
    await flushPromises()

    await pagina.get('#titulo-edital').setValue('Edital com erro')
    await pagina.get('#formulario-edital form').trigger('submit')
    await flushPromises()

    expect(pagina.get('[role="alert"]').text()).toContain('Edital invalido.')
    expect(pagina.get<HTMLInputElement>('#titulo-edital').element.value).toBe(
      'Edital com erro',
    )
  })

  it('bloqueia os formularios quando o concurso esta arquivado', async () => {
    chamadas.obterConcurso.mockResolvedValue({
      ...concurso,
      situacao: 'ARQUIVADO',
      ativo: false,
    })
    const pagina = await montar()
    await flushPromises()

    expect(pagina.text()).toContain(
      'Restaure o concurso para alterar sua estrutura.',
    )
    expect(
      pagina.get('#formulario-edital fieldset').attributes('disabled'),
    ).toBeDefined()
  })
})
