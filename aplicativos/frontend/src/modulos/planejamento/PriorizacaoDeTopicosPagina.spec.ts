// @vitest-environment jsdom

import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { ErroDaApi } from '@/compartilhado/api/clienteHttp'

const { consultarPriorizacaoDeTopicos } = vi.hoisted(() => ({
  consultarPriorizacaoDeTopicos: vi.fn(),
}))

vi.mock('./apiDePriorizacaoDeTopicos', async (importarOriginal) => ({
  ...(await importarOriginal()),
  consultarPriorizacaoDeTopicos,
}))

import PriorizacaoDeTopicosPagina from './PriorizacaoDeTopicosPagina.vue'

const paginasMontadas: ReturnType<typeof mount>[] = []

const resposta = {
  contexto: {
    concurso: { identificador: 'concurso-1', nome: 'Receita Federal' },
    cargo: { identificador: 'cargo-1', nome: 'Auditor Fiscal' },
    edital: { identificador: 'edital-1', nome: 'Edital 2026' },
    dataReferencia: '2026-07-21',
    inicioJanelaRecente: '2026-06-22',
  },
  resumo: {
    itensOficiais: 5,
    itensSemMapeamento: 1,
    topicosExigidos: 3,
    lacunas: 1,
    fraquezas: 1,
    consolidados: 1,
  },
  itensSemMapeamento: [
    {
      id: 'item-1',
      descricao: 'Atos administrativos',
      idMateria: 'materia-1',
      nomeMateria: 'Direito Administrativo',
      ordem: 4,
    },
    {
      id: 'item-2',
      descricao: 'Interpretação de textos',
      idMateria: 'materia-2',
      nomeMateria: 'Língua Portuguesa',
      ordem: 1,
    },
  ],
  materias: [
    {
      id: 'materia-1',
      nome: 'Direito Administrativo',
      topicos: [
        {
          id: 'topico-1',
          nome: 'Poderes administrativos',
          grupo: 'LACUNA' as const,
          faixa: 'SEM_ESTUDO' as const,
          posicaoNoGrupo: 1,
          acaoSugerida: 'TEORIA' as const,
          possuiMaterial: false,
          quantidadeItensOficiais: 2,
          indicadores: {
            estudos: 0,
            evidencias: 0,
            questoesRecentes: 0,
            acertosRecentes: 0,
            errosRecentes: 0,
            percentual: null,
            ultimaRecordacao: null,
            ultimaDificuldade: null,
            ultimaEvidencia: null,
            quantidadePadroesRepetidos: 0,
            ultimaOcorrenciaPadraoRepetido: null,
          },
          justificativas: ['O tópico ainda não possui estudo ativo.'],
        },
        {
          id: 'topico-2',
          nome: 'Licitações',
          grupo: 'FRAQUEZA' as const,
          faixa: 'PRECISA_REFORCO' as const,
          posicaoNoGrupo: 1,
          acaoSugerida: 'QUESTOES' as const,
          possuiMaterial: true,
          quantidadeItensOficiais: 2,
          indicadores: {
            estudos: 2,
            evidencias: 2,
            questoesRecentes: 20,
            acertosRecentes: 12,
            errosRecentes: 8,
            percentual: 60,
            ultimaRecordacao: 2,
            ultimaDificuldade: 4,
            ultimaEvidencia: '2026-07-20T10:00:00-03:00',
            quantidadePadroesRepetidos: 1,
            ultimaOcorrenciaPadraoRepetido: '2026-07-20',
          },
          justificativas: [
            'O percentual recente de acertos está abaixo de 70%.',
          ],
        },
        {
          id: 'topico-3',
          nome: 'Serviços públicos',
          grupo: 'CONSOLIDADO' as const,
          faixa: 'CONSOLIDADO' as const,
          posicaoNoGrupo: 1,
          acaoSugerida: 'QUESTOES' as const,
          possuiMaterial: true,
          quantidadeItensOficiais: 1,
          indicadores: {
            estudos: 3,
            evidencias: 3,
            questoesRecentes: 25,
            acertosRecentes: 23,
            errosRecentes: 2,
            percentual: 92,
            ultimaRecordacao: 5,
            ultimaDificuldade: 2,
            ultimaEvidencia: '2026-07-20T01:00:00Z',
            quantidadePadroesRepetidos: 0,
            ultimaOcorrenciaPadraoRepetido: null,
          },
          justificativas: [
            'O percentual recente de acertos é de pelo menos 85%.',
          ],
        },
      ],
    },
  ],
}

function criarRoteador() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', component: PriorizacaoDeTopicosPagina },
      { path: '/planejamento/hoje', component: { template: '<div />' } },
      { path: '/planejamento/semana', component: { template: '<div />' } },
      {
        path: '/planejamento/prioridades',
        component: { template: '<div />' },
      },
      { path: '/estudos', component: { template: '<div />' } },
      { path: '/concursos', component: { template: '<div />' } },
      { path: '/login', name: 'login', component: { template: '<div />' } },
    ],
  })
}

async function montar(anexarAoDocumento = false) {
  const roteador = criarRoteador()
  await roteador.push('/')
  await roteador.isReady()
  const pagina = mount(PriorizacaoDeTopicosPagina, {
    ...(anexarAoDocumento ? { attachTo: document.body } : {}),
    global: { plugins: [roteador] },
  })
  paginasMontadas.push(pagina)
  await flushPromises()
  return pagina
}

describe('PriorizacaoDeTopicosPagina', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    consultarPriorizacaoDeTopicos.mockResolvedValue(resposta)
  })

  afterEach(() => {
    for (const pagina of paginasMontadas.splice(0)) pagina.unmount()
  })

  it('mostra contexto, lacunas, indicadores e justificativas do ranking', async () => {
    const pagina = await montar()

    expect(pagina.get('h1').text()).toBe('Lacunas e prioridades')
    expect(pagina.text()).toContain('Receita Federal')
    expect(pagina.text()).toContain('Auditor Fiscal · Edital 2026')
    expect(pagina.text()).toContain('Atos administrativos')
    expect(pagina.text()).toContain('Poderes administrativos')
    expect(pagina.text()).toContain('Sem estudo')
    expect(pagina.text()).toContain('Estudar teoria')
    expect(pagina.text()).toContain('Nenhum material ativo cobre este tópico')
    expect(pagina.text()).toContain('Licitações')
    expect(pagina.text()).toContain('60%')
    expect(pagina.text()).toContain('O percentual recente de acertos')
    expect(pagina.text()).toContain('Serviços públicos')
    expect(pagina.text()).toContain('19 de jul')
    expect(pagina.get('select').text()).toContain('Língua Portuguesa')
    expect(pagina.get('a[href="/planejamento/prioridades"]').text()).toContain(
      'Prioridades',
    )
  })

  it('recalcula no backend ao alterar data e materia', async () => {
    const pagina = await montar()
    const campos = pagina.findAll('form input, form select')

    await campos[0]!.setValue('2026-07-15')
    await campos[1]!.setValue('materia-1')
    await pagina.get('form').trigger('submit')
    await flushPromises()

    expect(consultarPriorizacaoDeTopicos).toHaveBeenLastCalledWith(
      '2026-07-15',
      'materia-1',
      expect.any(AbortSignal),
    )
  })

  it('filtra lacunas, fraquezas, consolidados ou exibe tudo junto', async () => {
    const pagina = await montar()
    const filtro = pagina.get('[aria-label="Classificação exibida"]')

    expect(pagina.text()).toContain('Poderes administrativos')
    expect(pagina.text()).toContain('Licitações')
    expect(pagina.text()).toContain('Serviços públicos')

    await filtro.setValue('FRAQUEZA')
    expect(pagina.text()).not.toContain('Poderes administrativos')
    expect(pagina.text()).toContain('Licitações')
    expect(pagina.text()).not.toContain('Serviços públicos')
    expect(pagina.find('.grupo-lacuna').exists()).toBe(false)
    expect(pagina.find('.grupo-fraqueza').exists()).toBe(true)
    expect(pagina.find('.grupo-consolidado').exists()).toBe(false)
    expect(consultarPriorizacaoDeTopicos).toHaveBeenCalledTimes(1)

    await filtro.setValue('LACUNA')
    expect(pagina.text()).toContain('Poderes administrativos')
    expect(pagina.text()).not.toContain('Licitações')

    await filtro.setValue('CONSOLIDADO')
    expect(pagina.text()).toContain('Serviços públicos')
    expect(pagina.text()).not.toContain('Poderes administrativos')

    await filtro.setValue('TODOS')
    expect(pagina.text()).toContain('Poderes administrativos')
    expect(pagina.text()).toContain('Licitações')
    expect(pagina.text()).toContain('Serviços públicos')
  })

  it('informa quando a classificação selecionada não possui tópicos', async () => {
    consultarPriorizacaoDeTopicos.mockResolvedValue({
      ...resposta,
      materias: [
        {
          ...resposta.materias[0],
          topicos: resposta.materias[0]!.topicos.filter(
            (topico) => topico.grupo === 'LACUNA',
          ),
        },
      ],
    })
    const pagina = await montar()

    await pagina
      .get('[aria-label="Classificação exibida"]')
      .setValue('CONSOLIDADO')

    expect(pagina.text()).toContain('Nenhum tópico nesta classificação')
  })

  it('orienta a completar o contexto oficial quando o backend retorna 422', async () => {
    consultarPriorizacaoDeTopicos.mockRejectedValue(
      new ErroDaApi(
        422,
        'Selecione o cargo e o edital principal do concurso ativo.',
        'CONTEXTO_DE_PRIORIZACAO_INCOMPLETO',
      ),
    )

    const pagina = await montar()

    expect(pagina.text()).toContain('Complete o objetivo da priorização')
    expect(pagina.text()).toContain('Selecione o cargo e o edital principal')
    expect(pagina.get('a[href="/concursos"]').text()).toContain(
      'Revisar concurso ativo',
    )
  })

  it('informa a sessao expirada sem oferecer uma repeticao anonima', async () => {
    consultarPriorizacaoDeTopicos.mockRejectedValue(
      new ErroDaApi(401, 'Sua sessão expirou.'),
    )

    const pagina = await montar()

    expect(pagina.text()).toContain('Sua sessão expirou')
    expect(pagina.text()).toContain('Entrar novamente')
    expect(pagina.text()).not.toContain('Tentar novamente')
  })

  it('repete a consulta depois de um erro recuperavel de rede', async () => {
    consultarPriorizacaoDeTopicos
      .mockRejectedValueOnce(new Error('Rede indisponível'))
      .mockResolvedValueOnce(resposta)

    const pagina = await montar(true)
    expect(pagina.text()).toContain('Rede indisponível')

    await pagina.get('button.btn-outline-primary').trigger('click')
    await flushPromises()

    expect(consultarPriorizacaoDeTopicos).toHaveBeenCalledTimes(2)
    expect(pagina.text()).toContain('Receita Federal')
    expect(document.activeElement).toBe(
      pagina.get('button[type="submit"]').element,
    )
    pagina.unmount()
  })

  it('não mantém o ranking anterior depois de uma nova evidência', async () => {
    const pagina = await montar()
    expect(consultarPriorizacaoDeTopicos).toHaveBeenCalledTimes(1)

    window.dispatchEvent(new CustomEvent('estudo-registrado'))
    await flushPromises()

    expect(consultarPriorizacaoDeTopicos).toHaveBeenCalledTimes(2)
    expect(pagina.text()).toContain('Receita Federal')
  })
})
