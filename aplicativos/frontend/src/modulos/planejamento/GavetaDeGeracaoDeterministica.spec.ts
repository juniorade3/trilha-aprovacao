// @vitest-environment jsdom

import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { ErroDaApi } from '@/compartilhado/api/clienteHttp'

const chamadas = vi.hoisted(() => ({
  listar: vi.fn(),
  substituir: vi.fn(),
  gerar: vi.fn(),
  aplicar: vi.fn(),
}))

vi.mock('./apiDePlanejamento', () => ({
  listarMateriasParaGeracao: chamadas.listar,
  substituirPrioridadesDeMaterias: chamadas.substituir,
  gerarPreviaDeterministica: chamadas.gerar,
  aplicarGeracaoDeterministica: chamadas.aplicar,
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
    assinaturaDaPrevia: 'assinatura-1',
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
              identificadorDaMateria: 'materia-2',
              nomeDaMateria: 'Redes',
              identificadorDoTopico: 'topico-revisao',
              nomeDoTopico: 'Camada de transporte',
              grupoDaPriorizacao: 'FRAQUEZA',
              faixaDaPriorizacao: 'PRECISA_REFORCO',
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
              identificadorDoTopico: 'topico-1',
              nomeDoTopico: 'Normalização de dados',
              grupoDaPriorizacao: 'LACUNA',
              faixaDaPriorizacao: 'SEM_ESTUDO',
              titulo: 'Normalização de dados',
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

async function montar(quantidadeDeBlocosGerados = 0) {
  const componente = mount(GavetaDeGeracaoDeterministica, {
    attachTo: document.body,
    props: {
      identificadorDoPlano: 'plano-1',
      dataDeReferencia: '2026-07-21',
      quantidadeDeBlocosGerados,
    },
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
    chamadas.aplicar.mockResolvedValue({
      plano: { identificador: 'plano-1', blocos: [] },
      resumo: {
        quantidadeDeBlocosCriados: 2,
        quantidadeDeBlocosSubstituidos: 0,
        quantidadeDeBlocosPreservados: 0,
      },
    })
  })

  afterEach(() => {
    for (const componente of montadas.splice(0)) componente.unmount()
  })

  it('percorre prioridades, configuracao e aplica a previa recalculada', async () => {
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
    expect(chamadas.gerar).toHaveBeenCalledWith('plano-1', '2026-07-21', 50)
    expect(componente.findAll('.dia-da-previa')).toHaveLength(7)
    expect(componente.text()).toContain('Nada foi aplicado ao seu plano')
    expect(componente.text()).toContain('Normalização de dados')
    expect(componente.text()).toContain('Lacuna')
    expect(componente.text()).toContain('Sem estudo')
    expect(componente.text()).toContain('Teoria')
    expect(componente.text()).not.toContain('Duração da revisão')
    const aplicar = componente
      .findAll('button')
      .find((botao) => botao.text().includes('Aplicar à semana'))!
    await aplicar.trigger('click')
    await flushPromises()

    expect(chamadas.aplicar).toHaveBeenCalledWith(
      'plano-1',
      '2026-07-21',
      50,
      false,
      'assinatura-1',
    )
    expect(componente.emitted('aplicado')).toHaveLength(1)
  })

  it('recalcula um conflito de assinatura e exige nova confirmacao', async () => {
    const previaAtualizada = previa()
    previaAtualizada.assinaturaDaPrevia = 'assinatura-2'
    previaAtualizada.dias[0]!.blocosSugeridos[1]!.nomeDoTopico =
      'Índices e consultas'
    chamadas.gerar
      .mockResolvedValueOnce(previa())
      .mockResolvedValueOnce(previaAtualizada)
    chamadas.aplicar.mockRejectedValueOnce(
      new ErroDaApi(
        409,
        'A prévia da geração está desatualizada.',
        'PREVIA_DA_GERACAO_DESATUALIZADA',
      ),
    )
    const componente = await montar()
    await componente.get('button.btn-primary.w-100').trigger('click')
    await flushPromises()
    await componente.get('button.btn-primary.flex-grow-1').trigger('click')
    await flushPromises()

    const botaoAplicar = componente
      .findAll('button')
      .find((botao) => botao.text() === 'Aplicar à semana')!
    ;(botaoAplicar.element as HTMLButtonElement).focus()
    await botaoAplicar.trigger('click')
    await flushPromises()

    expect(chamadas.aplicar).toHaveBeenCalledTimes(1)
    expect(chamadas.gerar).toHaveBeenCalledTimes(2)
    expect(chamadas.listar).toHaveBeenCalledTimes(2)
    expect(componente.text()).toContain('Prévia recalculada.')
    expect(componente.text()).toContain('Revise a nova proposta')
    expect(componente.text()).toContain('Índices e consultas')
    const alerta = componente.get('[role="status"]')
    expect(alerta.attributes('aria-live')).toBe('assertive')
    const novaAplicacao = componente
      .findAll('button')
      .find((botao) => botao.text() === 'Aplicar à semana')!
    expect(document.activeElement).toBe(novaAplicacao.element)
    expect(componente.emitted('aplicado')).toBeUndefined()

    await novaAplicacao.trigger('click')
    await flushPromises()

    expect(chamadas.aplicar).toHaveBeenNthCalledWith(
      2,
      'plano-1',
      '2026-07-21',
      50,
      false,
      'assinatura-2',
    )
    expect(componente.emitted('aplicado')).toHaveLength(1)
  })

  it('mantem a aplicacao bloqueada quando o recalculo do conflito falha', async () => {
    chamadas.gerar
      .mockResolvedValueOnce(previa())
      .mockRejectedValueOnce(new Error('Falha ao atualizar a prévia.'))
    chamadas.aplicar.mockRejectedValueOnce(
      new ErroDaApi(
        409,
        'A prévia da geração está desatualizada.',
        'PREVIA_DA_GERACAO_DESATUALIZADA',
      ),
    )
    const componente = await montar()
    await componente.get('button.btn-primary.w-100').trigger('click')
    await flushPromises()
    await componente.get('button.btn-primary.flex-grow-1').trigger('click')
    await flushPromises()
    await componente
      .findAll('button')
      .find((botao) => botao.text() === 'Aplicar à semana')!
      .trigger('click')
    await flushPromises()

    const alerta = componente.get('[role="alert"]')
    expect(alerta.text()).toContain('Falha ao atualizar a prévia.')
    expect(document.activeElement).toBe(alerta.element)
    expect(componente.text()).not.toContain('Prévia da semana')
    expect(
      componente
        .findAll('button')
        .some((botao) => botao.text() === 'Aplicar à semana'),
    ).toBe(false)
    expect(chamadas.aplicar).toHaveBeenCalledTimes(1)

    await componente
      .findAll('button')
      .find((botao) => botao.text() === 'Tentar novamente')!
      .trigger('click')
    await flushPromises()

    expect(chamadas.listar).toHaveBeenCalledTimes(3)
    expect(chamadas.gerar).toHaveBeenCalledTimes(3)
    expect(componente.text()).toContain('Prévia recalculada.')
    const aplicacaoLiberada = componente
      .findAll('button')
      .find((botao) => botao.text() === 'Aplicar à semana')!
    expect(aplicacaoLiberada.attributes('disabled')).toBeUndefined()
    expect(document.activeElement).toBe(aplicacaoLiberada.element)
  })

  it('recarrega prioridades concorrentes sem sobrescreve-las', async () => {
    const materiasAtualizadas = structuredClone(materias)
    materiasAtualizadas[0]!.prioridade = 'ALTA'
    chamadas.listar
      .mockResolvedValueOnce(structuredClone(materias))
      .mockResolvedValueOnce(materiasAtualizadas)
    chamadas.aplicar.mockRejectedValueOnce(
      new ErroDaApi(
        409,
        'A prévia da geração está desatualizada.',
        'PREVIA_DA_GERACAO_DESATUALIZADA',
      ),
    )
    const componente = await montar()
    await componente.get('button.btn-primary.w-100').trigger('click')
    await flushPromises()
    await componente.get('button.btn-primary.flex-grow-1').trigger('click')
    await flushPromises()
    await componente
      .findAll('button')
      .find((botao) => botao.text() === 'Aplicar à semana')!
      .trigger('click')
    await flushPromises()

    expect(chamadas.listar).toHaveBeenCalledTimes(2)
    expect(chamadas.substituir).toHaveBeenCalledTimes(1)
    await componente
      .findAll('button')
      .find((botao) => botao.text() === 'Ajustar')!
      .trigger('click')

    expect((componente.find('select').element as HTMLSelectElement).value).toBe(
      'ALTA',
    )
    expect(componente.text()).not.toContain('Prévia desatualizada.')
  })

  it('preserva prioridades e duracao ao voltar entre todas as etapas', async () => {
    const componente = await montar()
    await componente.find('select').setValue('ALTA')
    await componente.get('button.btn-primary.w-100').trigger('click')
    await flushPromises()
    const duracoes = componente.findAll('input[type="number"]')
    await duracoes[0]!.setValue('75')
    await componente.get('button.btn-primary.flex-grow-1').trigger('click')
    await flushPromises()

    await componente
      .findAll('button')
      .find((botao) => botao.text() === 'Ajustar')!
      .trigger('click')
    expect((componente.find('select').element as HTMLSelectElement).value).toBe(
      'ALTA',
    )

    await componente
      .findAll('.etapa-da-geracao')
      .find((botao) => botao.text().includes('Configuração'))!
      .trigger('click')
    const duracoesPreservadas = componente.findAll('input[type="number"]')
    expect((duracoesPreservadas[0]!.element as HTMLInputElement).value).toBe(
      '75',
    )
    expect(duracoesPreservadas).toHaveLength(1)

    await componente
      .findAll('.etapa-da-geracao')
      .find((botao) => botao.text().includes('Prévia'))!
      .trigger('click')
    expect(componente.text()).toContain('Prévia da semana')
    expect(componente.find('[role="status"]').exists()).toBe(false)
  })

  it('repete o calculo sem recarregar materias e preserva a configuracao', async () => {
    chamadas.gerar.mockRejectedValueOnce(new Error('Falha ao gerar prévia.'))
    const componente = await montar()
    await componente.find('select').setValue('ALTA')
    await componente.get('button.btn-primary.w-100').trigger('click')
    await flushPromises()
    const duracoes = componente.findAll('input[type="number"]')
    await duracoes[0]!.setValue('75')

    await componente.get('button.btn-primary.flex-grow-1').trigger('click')
    await flushPromises()
    expect(componente.text()).toContain('Falha ao gerar prévia.')
    await componente
      .findAll('button')
      .find((botao) => botao.text() === 'Tentar novamente')!
      .trigger('click')
    await flushPromises()

    expect(chamadas.listar).toHaveBeenCalledTimes(1)
    expect(chamadas.substituir).toHaveBeenCalledWith('plano-1', [
      { identificadorDaMateria: 'materia-1', prioridade: 'ALTA' },
      { identificadorDaMateria: 'materia-2', prioridade: 'NORMAL' },
    ])
    expect(chamadas.gerar).toHaveBeenCalledTimes(2)
    expect(chamadas.gerar).toHaveBeenNthCalledWith(
      1,
      'plano-1',
      '2026-07-21',
      75,
    )
    expect(chamadas.gerar).toHaveBeenNthCalledWith(
      2,
      'plano-1',
      '2026-07-21',
      75,
    )

    await componente
      .findAll('button')
      .find((botao) => botao.text() === 'Ajustar')!
      .trigger('click')
    expect((componente.find('select').element as HTMLSelectElement).value).toBe(
      'ALTA',
    )
    await componente
      .findAll('.etapa-da-geracao')
      .find((botao) => botao.text().includes('Configuração'))!
      .trigger('click')
    const duracoesPreservadas = componente.findAll('input[type="number"]')
    expect((duracoesPreservadas[0]!.element as HTMLInputElement).value).toBe(
      '75',
    )
    expect(duracoesPreservadas).toHaveLength(1)
  })

  it('confirma regeneracao preservando manuais e ajustados', async () => {
    const componente = await montar(3)
    await componente.get('button.btn-primary.w-100').trigger('click')
    await flushPromises()
    await componente.find('button.btn-primary.flex-grow-1').trigger('click')
    await flushPromises()
    const aplicar = componente
      .findAll('button')
      .find((botao) => botao.text().includes('Aplicar à semana'))!
    await aplicar.trigger('click')
    await flushPromises()

    expect(chamadas.aplicar).not.toHaveBeenCalled()
    expect(document.body.textContent).toContain('3 bloco(s) gerado(s)')
    expect(document.body.textContent).toContain('manuais')
    expect(
      document.body.querySelector('.sobreposicao-da-aplicacao-sobre-gaveta'),
    ).not.toBeNull()
    const confirmar = Array.from(document.body.querySelectorAll('button')).find(
      (botao) => botao.textContent?.includes('Substituir e aplicar'),
    ) as HTMLButtonElement
    confirmar.click()
    await flushPromises()

    expect(chamadas.aplicar).toHaveBeenCalledWith(
      'plano-1',
      '2026-07-21',
      50,
      true,
      'assinatura-1',
    )
  })

  it('fecha primeiro o modal de regeneracao e depois a gaveta com escape', async () => {
    const componente = await montar(3)
    await componente.get('button.btn-primary.w-100').trigger('click')
    await flushPromises()
    await componente.get('button.btn-primary.flex-grow-1').trigger('click')
    await flushPromises()
    const aplicar = componente
      .findAll('button')
      .find((botao) => botao.text() === 'Aplicar à semana')!
    ;(aplicar.element as HTMLButtonElement).focus()
    await aplicar.trigger('click')
    await flushPromises()
    expect(
      document.body.querySelector('.sobreposicao-da-aplicacao-sobre-gaveta'),
    ).not.toBeNull()

    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
    await flushPromises()

    expect(
      document.body.querySelector('.sobreposicao-da-aplicacao-sobre-gaveta'),
    ).toBeNull()
    expect(componente.text()).toContain('Prévia da semana')
    expect(componente.emitted('fechar')).toBeUndefined()
    expect(document.activeElement).toBe(aplicar.element)

    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
    await flushPromises()
    expect(componente.emitted('fechar')).toHaveLength(1)
  })

  it('fecha o modal apos falha de regeneracao e expoe o retry seguro', async () => {
    chamadas.aplicar.mockRejectedValueOnce(new Error('Falha de rede.'))
    const componente = await montar(3)
    await componente.get('button.btn-primary.w-100').trigger('click')
    await flushPromises()
    await componente.get('button.btn-primary.flex-grow-1').trigger('click')
    await flushPromises()
    await componente
      .findAll('button')
      .find((botao) => botao.text() === 'Aplicar à semana')!
      .trigger('click')
    const confirmar = Array.from(document.body.querySelectorAll('button')).find(
      (botao) => botao.textContent?.includes('Substituir e aplicar'),
    ) as HTMLButtonElement
    confirmar.click()
    await flushPromises()

    expect(
      document.body.querySelector('.sobreposicao-da-aplicacao-sobre-gaveta'),
    ).toBeNull()
    expect(componente.get('[role="alert"]').text()).toContain('Falha de rede.')
    await componente
      .findAll('button')
      .find((botao) => botao.text() === 'Tentar novamente')!
      .trigger('click')
    await flushPromises()

    expect(chamadas.aplicar).toHaveBeenNthCalledWith(
      1,
      'plano-1',
      '2026-07-21',
      50,
      true,
      'assinatura-1',
    )
    expect(chamadas.aplicar).toHaveBeenNthCalledWith(
      2,
      'plano-1',
      '2026-07-21',
      50,
      true,
      'assinatura-1',
    )
    expect(componente.emitted('aplicado')).toHaveLength(1)
  })

  it('exige salvar prioridades novas e permite consultar a previa antiga', async () => {
    const componente = await montar()
    await componente.get('button.btn-primary.w-100').trigger('click')
    await flushPromises()
    await componente.get('button.btn-primary.flex-grow-1').trigger('click')
    await flushPromises()

    await componente
      .findAll('button')
      .find((botao) => botao.text() === 'Ajustar')!
      .trigger('click')
    await componente.find('select').setValue('ALTA')
    const configuracao = componente
      .findAll('.etapa-da-geracao')
      .find((botao) => botao.text().includes('Configuração'))!
    expect(configuracao.attributes('disabled')).toBeDefined()
    await configuracao.trigger('click')
    expect(componente.text()).toContain('Prioridades desta semana')
    expect(chamadas.gerar).toHaveBeenCalledTimes(1)

    const previaAntiga = componente
      .findAll('.etapa-da-geracao')
      .find((botao) => botao.text().includes('Prévia'))!
    expect(previaAntiga.attributes('disabled')).toBeUndefined()
    await previaAntiga.trigger('click')

    expect(componente.get('[role="status"]').text()).toContain(
      'Prévia desatualizada. Prioridades ou a configuração mudaram. Recalcule antes de aplicar.',
    )
    const recalcular = componente
      .findAll('button')
      .find((botao) => botao.text() === 'Recalcular prévia')!
    expect(recalcular.attributes('disabled')).toBeDefined()
    await recalcular.trigger('click')
    await flushPromises()
    expect(chamadas.gerar).toHaveBeenCalledTimes(1)
    expect(chamadas.aplicar).not.toHaveBeenCalled()

    await componente
      .findAll('button')
      .find((botao) => botao.text() === 'Ajustar')!
      .trigger('click')
    await componente.get('button.btn-primary.w-100').trigger('click')
    await flushPromises()
    expect(chamadas.substituir).toHaveBeenLastCalledWith('plano-1', [
      { identificadorDaMateria: 'materia-1', prioridade: 'ALTA' },
      { identificadorDaMateria: 'materia-2', prioridade: 'NORMAL' },
    ])
    await componente.get('button.btn-primary.flex-grow-1').trigger('click')
    await flushPromises()
    expect(chamadas.gerar).toHaveBeenCalledTimes(2)
    expect(componente.find('[role="status"]').exists()).toBe(false)
  })

  it('recalcula e libera a aplicacao pelo fluxo de ajuste e configuracao', async () => {
    const componente = await montar()
    await componente.get('button.btn-primary.w-100').trigger('click')
    await flushPromises()
    await componente.get('button.btn-primary.flex-grow-1').trigger('click')
    await flushPromises()

    await componente
      .findAll('.etapa-da-geracao')
      .find((botao) => botao.text().includes('Configuração'))!
      .trigger('click')
    await componente.find('input[type="number"]').setValue('60')
    await componente
      .findAll('.etapa-da-geracao')
      .find((botao) => botao.text().includes('Prévia'))!
      .trigger('click')
    expect(componente.get('[role="status"]').text()).toContain(
      'Prévia desatualizada.',
    )

    await componente
      .findAll('button')
      .find((botao) => botao.text() === 'Ajustar')!
      .trigger('click')
    await componente.get('button.btn-primary.w-100').trigger('click')
    await flushPromises()
    await componente.get('button.btn-primary.flex-grow-1').trigger('click')
    await flushPromises()

    expect(chamadas.gerar).toHaveBeenLastCalledWith('plano-1', '2026-07-21', 60)
    expect(componente.find('[role="status"]').exists()).toBe(false)
    const aplicar = componente
      .findAll('button')
      .find((botao) => botao.text() === 'Aplicar à semana')!
    expect(aplicar.attributes('disabled')).toBeUndefined()
    await aplicar.trigger('click')
    await flushPromises()
    expect(chamadas.aplicar).toHaveBeenCalledWith(
      'plano-1',
      '2026-07-21',
      60,
      false,
      'assinatura-1',
    )
  })

  it('repete a operacao que falhou em cada etapa recuperavel', async () => {
    chamadas.substituir.mockRejectedValueOnce(
      new Error('Falha ao salvar prioridades.'),
    )
    chamadas.gerar.mockRejectedValueOnce(new Error('Falha ao gerar prévia.'))
    chamadas.aplicar.mockRejectedValueOnce(new Error('Falha ao aplicar.'))
    const componente = await montar()

    await componente.get('button.btn-primary.w-100').trigger('click')
    await flushPromises()
    expect(componente.text()).toContain('Falha ao salvar prioridades.')
    await componente
      .findAll('button')
      .find((botao) => botao.text() === 'Tentar novamente')!
      .trigger('click')
    await flushPromises()
    expect(chamadas.substituir).toHaveBeenCalledTimes(2)
    expect(componente.text()).toContain('Configuração dos blocos')

    await componente.get('button.btn-primary.flex-grow-1').trigger('click')
    await flushPromises()
    expect(componente.text()).toContain('Falha ao gerar prévia.')
    await componente
      .findAll('button')
      .find((botao) => botao.text() === 'Tentar novamente')!
      .trigger('click')
    await flushPromises()
    expect(chamadas.gerar).toHaveBeenCalledTimes(2)
    expect(componente.text()).toContain('Prévia da semana')

    await componente
      .findAll('button')
      .find((botao) => botao.text() === 'Aplicar à semana')!
      .trigger('click')
    await flushPromises()
    expect(componente.text()).toContain('Falha ao aplicar.')
    await componente
      .findAll('button')
      .find((botao) => botao.text() === 'Tentar novamente')!
      .trigger('click')
    await flushPromises()

    expect(chamadas.aplicar).toHaveBeenCalledTimes(2)
    expect(chamadas.aplicar).toHaveBeenLastCalledWith(
      'plano-1',
      '2026-07-21',
      50,
      false,
      'assinatura-1',
    )
    expect(componente.emitted('aplicado')).toHaveLength(1)
  })

  it('explica o conflito encontrado pelo servidor sem exibir contador zero', async () => {
    chamadas.aplicar.mockRejectedValueOnce(
      new ErroDaApi(
        409,
        'A geração já foi aplicada.',
        'GERACAO_DETERMINISTICA_JA_APLICADA',
      ),
    )
    const componente = await montar()
    await componente.get('button.btn-primary.w-100').trigger('click')
    await flushPromises()
    await componente.get('button.btn-primary.flex-grow-1').trigger('click')
    await flushPromises()
    await componente
      .findAll('button')
      .find((botao) => botao.text() === 'Aplicar à semana')!
      .trigger('click')
    await flushPromises()

    expect(document.body.textContent).toContain(
      'O servidor encontrou uma geração anterior. Os blocos puramente gerados a partir da data de referência serão substituídos.',
    )
    expect(document.body.textContent).not.toContain(
      '0 bloco(s) gerado(s) serão substituídos.',
    )
    const confirmar = Array.from(document.body.querySelectorAll('button')).find(
      (botao) => botao.textContent?.includes('Substituir e aplicar'),
    ) as HTMLButtonElement
    confirmar.click()
    await flushPromises()

    expect(chamadas.aplicar).toHaveBeenLastCalledWith(
      'plano-1',
      '2026-07-21',
      50,
      true,
      'assinatura-1',
    )
  })

  it('exibe vazio recuperavel quando nao ha concurso ativo ou cargo', async () => {
    chamadas.listar.mockRejectedValueOnce(
      new Error('Defina um concurso ativo antes de gerar a semana.'),
    )
    const componente = await montar()

    expect(componente.get('[role="alert"]').text()).toContain('concurso ativo')
    expect(componente.text()).toContain('Tentar novamente')

    await componente
      .findAll('button')
      .find((botao) => botao.text() === 'Tentar novamente')!
      .trigger('click')
    await flushPromises()
    expect(chamadas.listar).toHaveBeenCalledTimes(2)
    expect(componente.text()).toContain('Banco de dados')
  })
})
