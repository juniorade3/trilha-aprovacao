// @vitest-environment jsdom

import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const chamadas = vi.hoisted(() => ({
  cancelarEstudo: vi.fn(),
  corrigirEstudo: vi.fn(),
  listarCoberturas: vi.fn(),
  listarTodosOsEstudos: vi.fn(),
  listarTodosOsMateriaisDeEstudo: vi.fn(),
  registrarEstudo: vi.fn(),
  listarTodasAsMaterias: vi.fn(),
  listarTodosOsTopicos: vi.fn(),
  consultarDiagnosticoDeTopicos: vi.fn(),
}))

vi.mock('./apiDeEstudos', () => ({
  cancelarEstudo: chamadas.cancelarEstudo,
  corrigirEstudo: chamadas.corrigirEstudo,
  listarCoberturas: chamadas.listarCoberturas,
  listarTodosOsEstudos: chamadas.listarTodosOsEstudos,
  listarTodosOsMateriaisDeEstudo: chamadas.listarTodosOsMateriaisDeEstudo,
  registrarEstudo: chamadas.registrarEstudo,
  consultarDiagnosticoDeTopicos: chamadas.consultarDiagnosticoDeTopicos,
  paraEvidencia: vi.fn(() => undefined),
  sugerirPadroesDeErro: vi.fn().mockResolvedValue([]),
}))
vi.mock('@/modulos/materias/apiDeConteudos', () => ({
  listarTodasAsMaterias: chamadas.listarTodasAsMaterias,
  listarTodosOsTopicos: chamadas.listarTodosOsTopicos,
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
  tipoDeEstudo: 'REVISAO',
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

describe('EstudosPagina', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    chamadas.listarTodosOsEstudos.mockResolvedValue([registro])
    chamadas.listarTodasAsMaterias.mockResolvedValue([materia])
    chamadas.listarTodosOsMateriaisDeEstudo.mockResolvedValue([material])
    chamadas.listarTodosOsTopicos.mockResolvedValue([topico])
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
    chamadas.consultarDiagnosticoDeTopicos.mockResolvedValue([])
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('apresenta a linha do tempo e abre o registro rapido global', async () => {
    const despachar = vi.spyOn(window, 'dispatchEvent')
    const pagina = mount(EstudosPagina)
    await flushPromises()

    expect(pagina.text()).toContain('Direitos fundamentais')
    expect(pagina.text()).toContain('Curso de Constitucional')
    expect(pagina.text()).toContain('45min')
    await pagina
      .findAll('button')
      .find((botao) => botao.text().includes('Registrar estudo'))!
      .trigger('click')

    expect(despachar).toHaveBeenCalledWith(
      expect.objectContaining({ type: 'abrir-registro-rapido' }),
    )
  })

  it('abre o registro rapido global ao entrar pela rota de novo estudo', async () => {
    const despachar = vi.spyOn(window, 'dispatchEvent')

    mount(EstudosPagina, {
      props: { abrirRegistroRapidoAoEntrar: true },
    })
    await flushPromises()

    expect(despachar).toHaveBeenCalledWith(
      expect.objectContaining({ type: 'abrir-registro-rapido' }),
    )
  })

  it('filtra totais e grupos do historico por periodo', async () => {
    const agora = Date.now()
    const haDias = (quantidade: number) =>
      new Date(agora - quantidade * 24 * 60 * 60 * 1000).toISOString()
    chamadas.listarTodosOsEstudos.mockResolvedValue([
      {
        ...registro,
        nomeDoTopico: 'Tópico recente',
        dataHora: haDias(2),
      },
      {
        ...registro,
        identificador: 'estudo-2',
        nomeDoTopico: 'Tópico intermediário',
        dataHora: haDias(20),
        situacao: 'CANCELADO',
      },
      {
        ...registro,
        identificador: 'estudo-3',
        nomeDoTopico: 'Tópico antigo',
        dataHora: haDias(40),
        situacao: 'CORRIGIDO',
      },
    ])
    const pagina = mount(EstudosPagina)
    await flushPromises()

    expect(pagina.text()).toContain('3 registros')

    const filtroDeSeteDias = pagina
      .findAll('button')
      .find((botao) => botao.text() === '7 dias')!
    await filtroDeSeteDias.trigger('click')

    expect(filtroDeSeteDias.attributes('aria-pressed')).toBe('true')
    expect(pagina.text()).toContain('1 registro')
    expect(pagina.text()).toContain('Tópico recente')
    expect(pagina.text()).not.toContain('Tópico intermediário')

    await pagina
      .findAll('button')
      .find((botao) => botao.text() === '30 dias')!
      .trigger('click')

    expect(pagina.text()).toContain('2 registros')
    expect(pagina.text()).toContain('Tópico intermediário')
    expect(pagina.text()).toContain('Cancelado')
    expect(pagina.text()).not.toContain('CANCELADO')
    expect(pagina.text()).not.toContain('Tópico antigo')
  })

  it('corrige e cancela um registro ativo sem apaga-lo', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    const pagina = mount(EstudosPagina, {
      global: { stubs: { Teleport: true } },
    })
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

  it('mostra diagnostico objetivo e padrões repetidos', async () => {
    chamadas.consultarDiagnosticoDeTopicos.mockResolvedValue([
      {
        identificadorDoTopico: 'topico-1',
        nomeDoTopico: 'Direitos fundamentais',
        identificadorDaMateria: 'materia-1',
        nomeDaMateria: 'Direito Constitucional',
        exigidoNoConcursoAtivo: true,
        quantidadeDeEvidencias: 2,
        totaisHistoricos: { questoes: 20, acertos: 15, erros: 5 },
        totaisDosUltimosTrintaDias: { questoes: 20, acertos: 15, erros: 5 },
        percentualRecenteDeAcertos: 75,
        resultadoDaUltimaRevisao: 'PARCIAL',
        padroesDeErroRepetidos: [
          {
            identificador: 'padrao-1',
            descricao: 'Confusão de conceitos',
            quantidadeDeEvidencias: 2,
            quantidadeDeOcorrencias: 3,
          },
        ],
      },
    ])
    const pagina = mount(EstudosPagina)
    await flushPromises()

    expect(pagina.text()).toContain('75% de acertos')
    expect(pagina.text()).toContain('Confusão de conceitos (2 sessões)')
    expect(pagina.text()).toContain('Parcial')
  })
})
