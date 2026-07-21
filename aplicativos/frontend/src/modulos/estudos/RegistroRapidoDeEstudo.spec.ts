// @vitest-environment jsdom

import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const chamadas = vi.hoisted(() => ({
  listarTodasAsMaterias: vi.fn(),
  listarTodosOsTopicos: vi.fn(),
  listarTodosOsMateriaisDeEstudo: vi.fn(),
  listarCoberturas: vi.fn(),
  registrarEstudo: vi.fn(),
}))

vi.mock('@/modulos/materias/apiDeConteudos', () => ({
  listarTodasAsMaterias: chamadas.listarTodasAsMaterias,
  listarTodosOsTopicos: chamadas.listarTodosOsTopicos,
}))
vi.mock('./apiDeEstudos', () => ({
  listarTodosOsMateriaisDeEstudo: chamadas.listarTodosOsMateriaisDeEstudo,
  listarCoberturas: chamadas.listarCoberturas,
  registrarEstudo: chamadas.registrarEstudo,
  paraEvidencia: vi.fn((modelo) =>
    modelo.dificuldadePercebida ? modelo : undefined,
  ),
  sugerirPadroesDeErro: vi.fn().mockResolvedValue([]),
}))

import RegistroRapidoDeEstudo from './RegistroRapidoDeEstudo.vue'

const componentesMontados: ReturnType<typeof mount>[] = []

function montar(propriedades: Record<string, string> = {}, anexar = false) {
  const componente = mount(RegistroRapidoDeEstudo, {
    props: propriedades,
    ...(anexar ? { attachTo: document.body } : {}),
    global: { stubs: { Teleport: true } },
  })
  componentesMontados.push(componente)
  return componente
}

describe('RegistroRapidoDeEstudo', () => {
  afterEach(() => {
    for (const componente of componentesMontados.splice(0)) componente.unmount()
    vi.useRealTimers()
  })

  beforeEach(() => {
    vi.clearAllMocks()
    chamadas.listarTodasAsMaterias.mockResolvedValue([
      {
        identificador: 'materia-1',
        nome: 'Direito Constitucional',
        arquivada: false,
      },
    ])
    chamadas.listarTodosOsTopicos.mockResolvedValue([
      {
        identificador: 'topico-1',
        identificadorDaMateria: 'materia-1',
        nome: 'Direitos fundamentais',
        arquivado: false,
      },
    ])
    chamadas.listarTodosOsMateriaisDeEstudo.mockResolvedValue([
      {
        identificador: 'material-1',
        titulo: 'Curso de Constitucional',
        tipo: 'AULA',
        arquivado: false,
      },
      {
        identificador: 'material-2',
        titulo: 'Material sem cobertura',
        tipo: 'PDF',
        arquivado: false,
      },
    ])
    chamadas.listarCoberturas.mockImplementation((material: string) =>
      Promise.resolve(
        material === 'material-1'
          ? [
              {
                identificador: 'cobertura-1',
                identificadorDoMaterial: 'material-1',
                identificadorDoTopico: 'topico-1',
                nomeDoTopico: 'Direitos fundamentais',
              },
            ]
          : [],
      ),
    )
    chamadas.registrarEstudo.mockResolvedValue({})
  })

  it('oferece apenas material que cobre o topico e registra o estudo', async () => {
    const componente = montar()
    await flushPromises()

    await componente.findAll('select')[1]!.setValue('materia-1')
    await flushPromises()
    await componente.findAll('select')[2]!.setValue('topico-1')
    await flushPromises()

    expect(componente.text()).toContain('Curso de Constitucional')
    expect(componente.text()).not.toContain('Material sem cobertura')

    await componente.findAll('select')[3]!.setValue('material-1')
    await componente.get('#registro-rapido-de-estudo').trigger('submit')
    await flushPromises()

    expect(chamadas.registrarEstudo).toHaveBeenCalledWith(
      expect.objectContaining({
        identificadorDoTopico: 'topico-1',
        identificadorDoMaterial: 'material-1',
        duracaoEmMinutos: 50,
      }),
    )
    expect(componente.emitted('registrado')).toHaveLength(1)
  })

  it('inicia preenchido para uma revisao solicitada pela fila de hoje', async () => {
    const componente = montar({
      identificadorDaMateriaInicial: 'materia-1',
      identificadorDoTopicoInicial: 'topico-1',
      tipoDeEstudoInicial: 'REVISAO',
    })
    await flushPromises()

    const seletores = componente.findAll('select')
    expect((seletores[0]!.element as HTMLSelectElement).value).toBe('REVISAO')
    expect((seletores[1]!.element as HTMLSelectElement).value).toBe('materia-1')
    expect((seletores[2]!.element as HTMLSelectElement).value).toBe('topico-1')
    expect(componente.text()).toContain('Nível de recordação')
  })

  it('separa falha do catalogo do estado vazio e recupera foco ao repetir', async () => {
    chamadas.listarTodasAsMaterias.mockRejectedValueOnce(
      new Error('Catálogo indisponível'),
    )
    const componente = montar({}, true)
    await flushPromises()

    expect(componente.text()).toContain('Catálogo indisponível')
    expect(componente.text()).not.toContain(
      'Cadastre uma matéria e um tópico primeiro',
    )

    const repetir = componente
      .findAll('button')
      .find((botao) => botao.text() === 'Tentar novamente')!
    ;(repetir.element as HTMLButtonElement).focus()
    await repetir.trigger('click')
    await flushPromises()

    expect(componente.find('#registro-rapido-de-estudo').exists()).toBe(true)
    expect(document.activeElement).toBe(
      componente.findAll('select')[0]!.element,
    )
  })

  it('usa a data civil de Sao Paulo e envia o instante correto na virada UTC', async () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-07-22T02:30:00Z'))
    const componente = montar()
    await flushPromises()

    const dataHora = componente.get('input[type="datetime-local"]')
    expect((dataHora.element as HTMLInputElement).value).toBe(
      '2026-07-21T23:30',
    )
    await componente.findAll('select')[1]!.setValue('materia-1')
    await componente.findAll('select')[2]!.setValue('topico-1')
    await componente.get('#registro-rapido-de-estudo').trigger('submit')
    await flushPromises()

    expect(chamadas.registrarEstudo).toHaveBeenCalledWith(
      expect.objectContaining({
        dataHora: '2026-07-22T02:30:00.000Z',
      }),
    )
  })
})
