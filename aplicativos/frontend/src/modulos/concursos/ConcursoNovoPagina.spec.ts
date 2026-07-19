// @vitest-environment jsdom

import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const chamadas = vi.hoisted(() => ({
  ativarConcurso: vi.fn(),
  criarCargo: vi.fn(),
  criarConcurso: vi.fn(),
  criarEdital: vi.fn(),
  criarGrupo: vi.fn(),
  criarProva: vi.fn(),
  definirEditalPrincipal: vi.fn(),
  selecionarCargo: vi.fn(),
}))

vi.mock('./apiDeConcursos', () => chamadas)

import ConcursoNovoPagina from './ConcursoNovoPagina.vue'

function criarRoteador() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/concursos/novo', component: ConcursoNovoPagina },
      {
        path: '/concursos/:identificador',
        component: { template: '<div />' },
      },
      { path: '/concursos', component: { template: '<div />' } },
    ],
  })
}

async function preencherEAvancarAteRevisao(pagina: ReturnType<typeof mount>) {
  await pagina.get('#nome-concurso').setValue('Receita Federal')
  await pagina.get('#orgao-concurso').setValue('RFB')
  await pagina.get('form').trigger('submit')
  await pagina
    .find('input[placeholder="Ex.: Edital CGU 2027"]')
    .setValue('Edital RFB')
  await pagina
    .find('input[placeholder="Ex.: Auditor Federal — TI"]')
    .setValue('Auditor Fiscal')
  await pagina.get('form').trigger('submit')
  await pagina.get('form').trigger('submit')
}

function botaoDeCriacao(pagina: ReturnType<typeof mount>) {
  return pagina
    .findAll('button')
    .find((botao) => botao.text().includes('Criar concurso'))!
}

describe('ConcursoNovoPagina', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    chamadas.criarConcurso.mockResolvedValue({
      identificador: 'concurso-1',
    })
    chamadas.criarEdital.mockResolvedValue({ identificador: 'edital-1' })
    chamadas.criarCargo.mockResolvedValue({ identificador: 'cargo-1' })
    chamadas.criarProva.mockResolvedValue({ identificador: 'prova-1' })
    chamadas.criarGrupo.mockResolvedValue({})
    chamadas.definirEditalPrincipal.mockResolvedValue({})
    chamadas.selecionarCargo.mockResolvedValue({})
    chamadas.ativarConcurso.mockResolvedValue({})
  })

  it('cria a estrutura inicial em quatro etapas usando as APIs existentes', async () => {
    const roteador = criarRoteador()
    await roteador.push('/concursos/novo')
    await roteador.isReady()
    const pagina = mount(ConcursoNovoPagina, {
      global: { plugins: [roteador] },
    })

    await preencherEAvancarAteRevisao(pagina)
    await botaoDeCriacao(pagina).trigger('click')
    await flushPromises()

    expect(chamadas.criarConcurso).toHaveBeenCalledWith(
      expect.objectContaining({
        nome: 'Receita Federal',
        orgao: 'RFB',
        situacao: 'PLANEJADO',
      }),
    )
    expect(chamadas.criarEdital).toHaveBeenCalledWith(
      'concurso-1',
      expect.objectContaining({ titulo: 'Edital RFB', ano: undefined }),
    )
    expect(chamadas.definirEditalPrincipal).toHaveBeenCalledWith('edital-1')
    expect(chamadas.criarCargo).toHaveBeenCalledWith(
      'concurso-1',
      expect.objectContaining({ nome: 'Auditor Fiscal' }),
    )
    expect(chamadas.selecionarCargo).toHaveBeenCalledWith('cargo-1')
    expect(chamadas.criarProva).toHaveBeenCalledWith(
      'cargo-1',
      expect.objectContaining({ tipo: 'OBJETIVA' }),
    )
    expect(chamadas.criarGrupo).toHaveBeenCalledTimes(2)
    expect(chamadas.ativarConcurso).toHaveBeenCalledWith('concurso-1')
    expect(roteador.currentRoute.value.fullPath).toBe(
      '/concursos/concurso-1?novo=concluido',
    )
  })

  it('retoma do ultimo ponto concluido sem duplicar a estrutura', async () => {
    chamadas.criarGrupo
      .mockResolvedValueOnce({})
      .mockRejectedValueOnce(new Error('Falha temporaria.'))
      .mockResolvedValueOnce({})
    const roteador = criarRoteador()
    await roteador.push('/concursos/novo')
    await roteador.isReady()
    const pagina = mount(ConcursoNovoPagina, {
      global: { plugins: [roteador] },
    })

    await preencherEAvancarAteRevisao(pagina)
    await botaoDeCriacao(pagina).trigger('click')
    await flushPromises()

    expect(pagina.get('[role="alert"]').text()).toContain('Falha temporaria.')
    expect(pagina.get('[role="alert"]').text()).toContain(
      'continuar do ponto em que a criação parou',
    )
    expect(chamadas.criarGrupo).toHaveBeenCalledTimes(2)
    expect(chamadas.ativarConcurso).not.toHaveBeenCalled()

    await botaoDeCriacao(pagina).trigger('click')
    await flushPromises()

    expect(chamadas.criarConcurso).toHaveBeenCalledTimes(1)
    expect(chamadas.criarEdital).toHaveBeenCalledTimes(1)
    expect(chamadas.definirEditalPrincipal).toHaveBeenCalledTimes(1)
    expect(chamadas.criarCargo).toHaveBeenCalledTimes(1)
    expect(chamadas.selecionarCargo).toHaveBeenCalledTimes(1)
    expect(chamadas.criarProva).toHaveBeenCalledTimes(1)
    expect(chamadas.criarGrupo).toHaveBeenCalledTimes(3)
    expect(
      chamadas.criarGrupo.mock.calls.map(([, dados]) => dados.nome),
    ).toEqual([
      'Conhecimentos gerais',
      'Conhecimentos específicos',
      'Conhecimentos específicos',
    ])
    expect(chamadas.ativarConcurso).toHaveBeenCalledTimes(1)
    expect(roteador.currentRoute.value.fullPath).toBe(
      '/concursos/concurso-1?novo=concluido',
    )
  })
})
