// @vitest-environment jsdom

import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const { obterDashboard } = vi.hoisted(() => ({ obterDashboard: vi.fn() }))

vi.mock('./apiDoDashboard', () => ({ obterDashboard }))

import InicioPagina from './InicioPagina.vue'

function criarRoteador() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', component: InicioPagina },
      { path: '/concursos', component: { template: '<div />' } },
      { path: '/concursos/novo', component: { template: '<div />' } },
      { path: '/concursos/:identificador', component: { template: '<div />' } },
      { path: '/estudos', component: { template: '<div />' } },
      { path: '/materiais', component: { template: '<div />' } },
      { path: '/planejamento/hoje', component: { template: '<div />' } },
    ],
  })
}

async function montar() {
  const roteador = criarRoteador()
  await roteador.push('/')
  await roteador.isReady()
  const pagina = mount(InicioPagina, { global: { plugins: [roteador] } })
  await flushPromises()
  return pagina
}

describe('InicioPagina', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('orienta o usuario quando ainda nao existe concurso ativo', async () => {
    obterDashboard.mockResolvedValue({
      tempoEstudadoNaSemanaEmMinutos: 0,
      quantidadeDeMaterias: 0,
      quantidadeDeTopicosExigidos: 0,
      quantidadeDeTopicosComEstudo: 0,
      quantidadeDeItensMapeados: 0,
      quantidadeDeItensSemMapeamento: 0,
      atividadeRecente: [],
      alertas: [],
    })

    const pagina = await montar()

    expect(pagina.get('h1').text()).toContain('concurso ativo')
    expect(pagina.get('a[href="/concursos/novo"]').text()).toContain(
      'Criar concurso',
    )
  })

  it('apresenta medidas objetivas, atividade e alertas do concurso', async () => {
    obterDashboard.mockResolvedValue({
      concursoAtivo: {
        identificador: 'concurso-1',
        nome: 'Receita Federal',
        orgao: 'RFB',
        banca: 'Cebraspe',
        situacao: 'EM_ANDAMENTO',
        identificadorDoCargoSelecionado: 'cargo-1',
        nomeDoCargoSelecionado: 'Auditor Fiscal',
      },
      dataDaProximaProva: '2026-08-20',
      diasAteAProva: 33,
      tempoEstudadoNaSemanaEmMinutos: 95,
      quantidadeDeMaterias: 6,
      quantidadeDeTopicosExigidos: 3,
      quantidadeDeTopicosComEstudo: 1,
      quantidadeDeItensMapeados: 8,
      quantidadeDeItensSemMapeamento: 2,
      atividadeRecente: [
        {
          identificador: 'estudo-1',
          identificadorDoTopico: 'topico-1',
          nomeDoTopico: 'Direitos fundamentais',
          tituloDoMaterial: 'Curso completo',
          dataHora: '2026-07-18T10:00:00-03:00',
          duracaoEmMinutos: 60,
        },
      ],
      alertas: [
        {
          codigo: 'ITEM_SEM_MAPEAMENTO',
          titulo: 'Itens aguardando mapeamento',
          mensagem: '2 itens ainda não apontam para tópicos pessoais.',
          nivel: 'ATENCAO',
        },
      ],
    })

    const pagina = await montar()

    expect(pagina.get('h1').text()).toContain('avançando')
    expect(pagina.text()).toContain('Receita Federal')
    expect(pagina.text()).toContain('Auditor Fiscal')
    expect(pagina.text()).toContain('1 de 3')
    expect(pagina.text()).toContain('1h 35min')
    expect(pagina.text()).toContain('Direitos fundamentais')
    expect(pagina.text()).toContain('Itens aguardando mapeamento')
    expect(pagina.text()).toContain('Faltam 33 dias')
    expect(pagina.get('a[href="/planejamento/hoje"]').text()).toContain(
      'Ver plano de hoje',
    )
  })

  it('permite tentar novamente quando a consulta falha', async () => {
    obterDashboard
      .mockRejectedValueOnce(new Error('Servico indisponivel'))
      .mockResolvedValueOnce({
        tempoEstudadoNaSemanaEmMinutos: 0,
        quantidadeDeMaterias: 0,
        quantidadeDeTopicosExigidos: 0,
        quantidadeDeTopicosComEstudo: 0,
        quantidadeDeItensMapeados: 0,
        quantidadeDeItensSemMapeamento: 0,
        atividadeRecente: [],
        alertas: [],
      })

    const pagina = await montar()
    expect(pagina.text()).toContain('Servico indisponivel')

    await pagina.get('button').trigger('click')
    await flushPromises()

    expect(obterDashboard).toHaveBeenCalledTimes(2)
    expect(pagina.text()).toContain('Comece escolhendo')
  })
})
