// @vitest-environment jsdom

import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const { obterDashboard } = vi.hoisted(() => ({ obterDashboard: vi.fn() }))

vi.mock('./apiDoDashboard', () => ({ obterDashboard }))

import InicioPagina from './InicioPagina.vue'

const paginasMontadas: ReturnType<typeof mount>[] = []

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
      {
        path: '/planejamento/prioridades',
        component: { template: '<div />' },
      },
    ],
  })
}

async function montar() {
  const roteador = criarRoteador()
  await roteador.push('/')
  await roteador.isReady()
  const pagina = mount(InicioPagina, { global: { plugins: [roteador] } })
  paginasMontadas.push(pagina)
  await flushPromises()
  return pagina
}

describe('InicioPagina', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  afterEach(() => {
    for (const pagina of paginasMontadas.splice(0)) pagina.unmount()
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

    expect(pagina.get('h1').text()).toContain('Visão geral')
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
    expect(pagina.get('a[href="/planejamento/prioridades"]').text()).toContain(
      'Ver lacunas',
    )
    expect(pagina.text()).not.toContain('Consolidação')
    expect(pagina.text()).not.toContain('Estudo consistente')
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

  it('recarrega as medidas depois de uma nova evidência', async () => {
    obterDashboard.mockResolvedValue({
      concursoAtivo: {
        identificador: 'concurso-1',
        nome: 'Receita Federal',
        situacao: 'EM_ANDAMENTO',
      },
      tempoEstudadoNaSemanaEmMinutos: 60,
      quantidadeDeMaterias: 1,
      quantidadeDeTopicosExigidos: 1,
      quantidadeDeTopicosComEstudo: 1,
      quantidadeDeItensMapeados: 1,
      quantidadeDeItensSemMapeamento: 0,
      atividadeRecente: [],
      alertas: [],
    })
    const pagina = await montar()
    expect(obterDashboard).toHaveBeenCalledTimes(1)

    window.dispatchEvent(new CustomEvent('estudo-registrado'))
    await flushPromises()

    expect(obterDashboard).toHaveBeenCalledTimes(2)
    expect(pagina.text()).toContain('1 de 1 tópicos com estudo')
  })

  it('exibe a atividade recente no horário civil de São Paulo', async () => {
    obterDashboard.mockResolvedValue({
      concursoAtivo: {
        identificador: 'concurso-1',
        nome: 'Receita Federal',
        situacao: 'EM_ANDAMENTO',
      },
      tempoEstudadoNaSemanaEmMinutos: 30,
      quantidadeDeMaterias: 1,
      quantidadeDeTopicosExigidos: 1,
      quantidadeDeTopicosComEstudo: 1,
      quantidadeDeItensMapeados: 1,
      quantidadeDeItensSemMapeamento: 0,
      atividadeRecente: [
        {
          identificador: 'estudo-1',
          identificadorDoTopico: 'topico-1',
          nomeDoTopico: 'Virada do dia',
          dataHora: '2026-07-22T02:30:00Z',
          duracaoEmMinutos: 30,
        },
      ],
      alertas: [],
    })

    const pagina = await montar()

    expect(pagina.text()).toContain('21 de jul.')
    expect(pagina.text()).toContain('23:30')
  })
})
