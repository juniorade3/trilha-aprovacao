// @vitest-environment jsdom

import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const chamadas = vi.hoisted(() => ({
  alterarTopico: vi.fn(),
  arquivarMateria: vi.fn(),
  arquivarTopico: vi.fn(),
  consultarUsoDaMateria: vi.fn(),
  criarTopico: vi.fn(),
  excluirTopico: vi.fn(),
  listarTopicos: vi.fn(),
  obterMateria: vi.fn(),
}))

vi.mock('./apiDeConteudos', () => chamadas)

import MateriaDetalhePagina from './MateriaDetalhePagina.vue'

const materia = {
  identificador: 'materia-1',
  nome: 'Direito Constitucional',
  cor: '#0E8F87',
  arquivada: false,
  criadoEm: '2026-07-18T10:00:00Z',
  atualizadoEm: '2026-07-18T10:00:00Z',
  versao: 0,
}

function criarRoteador() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      {
        path: '/materias/:identificador',
        component: MateriaDetalhePagina,
      },
      { path: '/materias', component: { template: '<div />' } },
    ],
  })
}

async function montarPagina() {
  const roteador = criarRoteador()
  await roteador.push('/materias/materia-1')
  await roteador.isReady()
  return mount(MateriaDetalhePagina, {
    global: { plugins: [roteador] },
  })
}

describe('MateriaDetalhePagina', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    chamadas.obterMateria.mockResolvedValue(materia)
    chamadas.listarTopicos.mockResolvedValue({
      itens: [],
      pagina: 0,
      tamanho: 100,
      totalDeItens: 0,
      totalDePaginas: 0,
    })
    chamadas.consultarUsoDaMateria.mockResolvedValue({
      materiais: [],
      estudosRecentes: [],
      concursos: [],
    })
  })

  it('apresenta a materia e o estado vazio da arvore', async () => {
    const pagina = await montarPagina()
    await flushPromises()

    expect(pagina.get('h1').text()).toBe('Direito Constitucional')
    expect(pagina.text()).toContain('Nenhum topico cadastrado')
  })

  it('cadastra topico com pai e recarrega a arvore', async () => {
    chamadas.listarTopicos
      .mockResolvedValueOnce({
        itens: [
          {
            identificador: 'raiz',
            identificadorDaMateria: 'materia-1',
            nome: 'Constituicao',
            ordem: 1,
            arquivado: false,
            criadoEm: '2026-07-18T10:00:00Z',
            atualizadoEm: '2026-07-18T10:00:00Z',
            versao: 0,
          },
        ],
        pagina: 0,
        tamanho: 100,
        totalDeItens: 1,
        totalDePaginas: 1,
      })
      .mockResolvedValueOnce({
        itens: [],
        pagina: 0,
        tamanho: 100,
        totalDeItens: 0,
        totalDePaginas: 0,
      })
    chamadas.criarTopico.mockResolvedValue({})
    const pagina = await montarPagina()
    await flushPromises()

    await pagina.get('#nome-topico').setValue('Direitos fundamentais')
    await pagina.get('#pai-topico').setValue('raiz')
    await pagina.get('#ordem-topico').setValue(2)
    await pagina.get('#formulario-topico').trigger('submit')
    await flushPromises()

    expect(chamadas.criarTopico).toHaveBeenCalledWith('materia-1', {
      nome: 'Direitos fundamentais',
      descricao: undefined,
      identificadorDoTopicoPai: 'raiz',
      ordem: 2,
    })
    expect(chamadas.listarTopicos).toHaveBeenCalledTimes(2)
  })

  it('bloqueia o formulario quando a materia esta arquivada', async () => {
    chamadas.obterMateria.mockResolvedValue({ ...materia, arquivada: true })

    const pagina = await montarPagina()
    await flushPromises()

    expect(pagina.text()).toContain(
      'Restaure a materia para alterar seus topicos.',
    )
    expect(pagina.get('fieldset').attributes('disabled')).toBeDefined()
  })

  it('mostra os usos reais da materia sem textos de sprint', async () => {
    chamadas.consultarUsoDaMateria.mockResolvedValue({
      materiais: [
        { identificador: 'material-1', titulo: 'Aula 01', tipo: 'AULA' },
      ],
      estudosRecentes: [
        {
          identificador: 'estudo-1',
          nomeDoTopico: 'Direitos fundamentais',
          dataHora: '2026-07-18T10:00:00-03:00',
          duracaoEmMinutos: 60,
        },
      ],
      concursos: [
        { identificador: 'concurso-1', nome: 'Concurso A', ativo: true },
      ],
    })

    const pagina = await montarPagina()
    await flushPromises()

    expect(pagina.text()).toContain('Aula 01')
    expect(pagina.text()).toContain('Direitos fundamentais')
    expect(pagina.text()).toContain('60 min')
    expect(pagina.text()).toContain('Concurso A')
    expect(pagina.text()).not.toContain('Disponivel na Sprint')
  })
})
