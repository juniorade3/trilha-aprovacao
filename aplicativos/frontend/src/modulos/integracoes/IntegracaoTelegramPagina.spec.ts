// @vitest-environment jsdom

import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { ErroDaApi } from '@/compartilhado/api/clienteHttp'

const api = vi.hoisted(() => ({
  cancelarOperacaoAssistida: vi.fn(),
  confirmarOperacaoAssistidaPelaWeb: vi.fn(),
  criarCodigoDeVinculo: vi.fn(),
  listarOperacoesAssistidas: vi.fn(),
  obterOperacaoAssistida: vi.fn(),
  obterVinculoDoTelegram: vi.fn(),
  revogarVinculoDoTelegram: vi.fn(),
  rotacionarVinculoDoTelegram: vi.fn(),
}))

vi.mock('./apiDeIntegracoes', async (importarOriginal) => ({
  ...(await importarOriginal()),
  ...api,
}))

import IntegracaoTelegramPagina from './IntegracaoTelegramPagina.vue'

const vinculoAtivo = {
  identificador: 'vinculo-1',
  canal: 'TELEGRAM' as const,
  estado: 'ATIVO' as const,
  identificadorDoBot: 100,
  identificadorExterno: 200,
  identificadorDoChat: 200,
  vinculadoEm: '2026-07-21T10:05:00-03:00',
  criadoEm: '2026-07-21T10:00:00-03:00',
  atualizadoEm: '2026-07-21T11:00:00-03:00',
  revogadoEm: null,
}

const operacao = {
  identificador: 'operacao-1',
  tipo: 'REGISTRAR_ESTUDO',
  estado: 'APLICADA' as const,
  resumo: 'Registrar estudo de Direito Administrativo',
  expiraEm: '2026-07-21T11:30:00-03:00',
  criadoEm: '2026-07-21T11:00:00-03:00',
  atualizadoEm: '2026-07-21T11:05:00-03:00',
}

const operacaoPendente = {
  ...operacao,
  identificador: 'operacao-pendente',
  estado: 'AGUARDANDO_CONFIRMACAO' as const,
  resumo: 'Concluir o bloco informado',
  expiraEm: '2026-08-21T11:30:00-03:00',
}

const historicoVazio = {
  itens: [],
  pagina: 0,
  tamanho: 20,
  totalDeItens: 0,
  totalDePaginas: 0,
}

function criarRoteador() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', component: IntegracaoTelegramPagina },
      { path: '/login', name: 'login', component: { template: '<div />' } },
    ],
  })
}

async function montar(anexarAoDocumento = false) {
  const roteador = criarRoteador()
  await roteador.push('/')
  await roteador.isReady()
  const pagina = mount(IntegracaoTelegramPagina, {
    ...(anexarAoDocumento ? { attachTo: document.body } : {}),
    global: { plugins: [roteador] },
  })
  await flushPromises()
  return pagina
}

describe('IntegracaoTelegramPagina', () => {
  let pagina: VueWrapper | undefined

  beforeEach(() => {
    vi.clearAllMocks()
    api.obterVinculoDoTelegram.mockResolvedValue(undefined)
    api.listarOperacoesAssistidas.mockResolvedValue(historicoVazio)
    api.revogarVinculoDoTelegram.mockResolvedValue(undefined)
    api.rotacionarVinculoDoTelegram.mockResolvedValue(undefined)
    api.confirmarOperacaoAssistidaPelaWeb.mockResolvedValue(undefined)
    api.cancelarOperacaoAssistida.mockResolvedValue(undefined)
  })

  afterEach(() => {
    pagina?.unmount()
    pagina = undefined
    document.body.innerHTML = ''
    document.body.classList.remove('modal-aberto')
  })

  it('apresenta carregamento e os estados vazios', async () => {
    let concluirVinculo!: (valor: undefined) => void
    api.obterVinculoDoTelegram.mockReturnValue(
      new Promise((resolver) => {
        concluirVinculo = resolver
      }),
    )

    const roteador = criarRoteador()
    await roteador.push('/')
    await roteador.isReady()
    pagina = mount(IntegracaoTelegramPagina, {
      global: { plugins: [roteador] },
    })

    expect(pagina.text()).toContain('Carregando integração')
    concluirVinculo(undefined)
    await flushPromises()

    expect(pagina.text()).toContain('Gerar código de conexão')
    expect(pagina.text()).toContain('Nenhuma operação registrada')
  })

  it('gera o comando temporario e move o foco para ele', async () => {
    api.criarCodigoDeVinculo.mockResolvedValue({
      codigo: '7K9P2Q',
      expiraEm: '2026-07-21T11:10:00-03:00',
      vinculo: { ...vinculoAtivo, estado: 'PENDENTE' },
    })
    pagina = await montar(true)

    await pagina.get('button.btn-primary').trigger('click')
    await flushPromises()

    const codigo = pagina.get('output')
    expect(codigo.text()).toContain('/conectar 7K9P2Q')
    expect(document.activeElement).toBe(codigo.element)
    expect(pagina.text()).toContain('O código funciona uma única vez')
  })

  it('exibe o vinculo ativo e permite revogar com confirmacao', async () => {
    api.obterVinculoDoTelegram.mockResolvedValue(vinculoAtivo)
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    pagina = await montar(true)

    expect(pagina.text()).toContain('Conectado')
    expect(pagina.text()).toContain('200')
    const botao = pagina
      .findAll('button')
      .find((item) => item.text().includes('Revogar acesso'))!
    await botao.trigger('click')
    await flushPromises()

    expect(api.revogarVinculoDoTelegram).toHaveBeenCalledOnce()
    expect(pagina.text()).toContain('integração com o Telegram foi revogada')
    const criar = pagina
      .findAll('button')
      .find((item) => item.text().includes('Gerar código de conexão'))!
    expect(document.activeElement).toBe(criar.element)
  })

  it('revoga o acesso atual e apresenta novo comando ao reconectar', async () => {
    api.obterVinculoDoTelegram.mockResolvedValue(vinculoAtivo)
    api.rotacionarVinculoDoTelegram.mockResolvedValue({
      codigo: 'N0V0C0',
      expiraEm: '2026-07-21T11:10:00-03:00',
      vinculo: {
        ...vinculoAtivo,
        identificador: 'vinculo-2',
        estado: 'PENDENTE',
      },
    })
    const confirmar = vi.spyOn(window, 'confirm').mockReturnValue(true)
    pagina = await montar(true)

    await pagina
      .findAll('button')
      .find((item) => item.text().includes('Reconectar Telegram'))!
      .trigger('click')
    await flushPromises()

    expect(confirmar).toHaveBeenCalledWith(
      expect.stringContaining(
        'acesso atual será revogado imediatamente e você precisará enviar um novo comando /conectar',
      ),
    )
    expect(api.rotacionarVinculoDoTelegram).toHaveBeenCalledOnce()
    const codigo = pagina.get('output')
    expect(codigo.text()).toContain('/conectar N0V0C0')
    expect(pagina.text()).toContain('O acesso anterior foi revogado')
    expect(document.activeElement).toBe(codigo.element)
  })

  it('cancela a reconexao sem revogar o acesso atual', async () => {
    api.obterVinculoDoTelegram.mockResolvedValue(vinculoAtivo)
    vi.spyOn(window, 'confirm').mockReturnValue(false)
    pagina = await montar()

    await pagina
      .findAll('button')
      .find((item) => item.text().includes('Reconectar Telegram'))!
      .trigger('click')

    expect(api.rotacionarVinculoDoTelegram).not.toHaveBeenCalled()
    expect(pagina.text()).toContain('Conectado')
  })

  it('bloqueia as acoes do vinculo enquanto inicia a reconexao', async () => {
    let concluir!: (valor: {
      codigo: string
      expiraEm: string
      vinculo: Omit<typeof vinculoAtivo, 'estado'> & { estado: 'PENDENTE' }
    }) => void
    api.obterVinculoDoTelegram.mockResolvedValue(vinculoAtivo)
    api.rotacionarVinculoDoTelegram.mockReturnValue(
      new Promise((resolver) => {
        concluir = resolver
      }),
    )
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    pagina = await montar()

    const reconectar = pagina
      .findAll('button')
      .find((item) => item.text().includes('Reconectar Telegram'))!
    await reconectar.trigger('click')

    expect(reconectar.attributes('disabled')).toBeDefined()
    expect(reconectar.find('.spinner-border').exists()).toBe(true)
    expect(
      pagina
        .findAll('button')
        .find((item) => item.text().includes('Revogar acesso'))!
        .attributes('disabled'),
    ).toBeDefined()

    concluir({
      codigo: 'N0V0C0',
      expiraEm: '2026-07-21T11:10:00-03:00',
      vinculo: { ...vinculoAtivo, estado: 'PENDENTE' },
    })
    await flushPromises()
  })

  it('mantem o vinculo e devolve o foco quando a reconexao falha', async () => {
    api.obterVinculoDoTelegram.mockResolvedValue(vinculoAtivo)
    api.rotacionarVinculoDoTelegram.mockRejectedValue(
      new Error('Serviço temporariamente indisponível'),
    )
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    pagina = await montar(true)

    const botao = pagina
      .findAll('button')
      .find((item) => item.text().includes('Reconectar Telegram'))!
    await botao.trigger('click')
    await flushPromises()

    expect(pagina.text()).toContain('Serviço temporariamente indisponível')
    expect(pagina.text()).toContain('Conectado')
    expect(document.activeElement).toBe(botao.element)
  })

  it('mantem o controle do vinculo quando apenas o historico falha', async () => {
    api.obterVinculoDoTelegram.mockResolvedValue(vinculoAtivo)
    api.listarOperacoesAssistidas.mockRejectedValue(
      new Error('Histórico indisponível'),
    )
    pagina = await montar()

    expect(pagina.text()).toContain('Conectado')
    expect(pagina.text()).toContain('Revogar acesso')
    expect(pagina.text()).toContain('Histórico indisponível')
  })

  it('move o foco para revogar depois de confirmar o vinculo', async () => {
    api.criarCodigoDeVinculo.mockResolvedValue({
      codigo: '7K9P2Q',
      expiraEm: '2026-07-21T11:10:00-03:00',
      vinculo: { ...vinculoAtivo, estado: 'PENDENTE' },
    })
    pagina = await montar(true)

    await pagina.get('button.btn-primary').trigger('click')
    await flushPromises()
    api.obterVinculoDoTelegram.mockResolvedValue(vinculoAtivo)
    await pagina
      .findAll('button')
      .find((item) => item.text().includes('Já enviei, verificar'))!
      .trigger('click')
    await flushPromises()

    const revogar = pagina
      .findAll('button')
      .find((item) => item.text().includes('Revogar acesso'))!
    expect(document.activeElement).toBe(revogar.element)
  })

  it('abre o detalhe em gaveta e devolve o foco ao fechar com Escape', async () => {
    api.obterVinculoDoTelegram.mockResolvedValue(vinculoAtivo)
    api.listarOperacoesAssistidas.mockResolvedValue({
      itens: [operacao],
      pagina: 0,
      tamanho: 20,
      totalDeItens: 1,
      totalDePaginas: 1,
    })
    api.obterOperacaoAssistida.mockResolvedValue({
      ...operacao,
      proposta: { minutos: 45 },
      assinatura: 'sha256:assinatura',
      versoesConsultadas: { plano: 3 },
      confirmadaEm: '2026-07-21T11:02:00-03:00',
      aplicadaEm: '2026-07-21T11:05:00-03:00',
      canceladaEm: null,
      falha: null,
      resultado: { registro: 'estudo-1' },
    })
    pagina = await montar(true)

    const origem = pagina.get(
      'button[aria-label="Ver detalhes: Registrar estudo de Direito Administrativo"]',
    )
    ;(origem.element as HTMLButtonElement).focus()
    await origem.trigger('click')
    await flushPromises()

    expect(pagina.get('[role="dialog"]').text()).toContain(
      'Proposta registrada',
    )
    expect(pagina.get('[role="dialog"]').text()).toContain('"minutos": 45')
    expect(pagina.get('[role="dialog"]').text()).toContain('Resultado aplicado')
    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
    await flushPromises()

    expect(pagina.find('[role="dialog"]').exists()).toBe(false)
    expect(document.activeElement).toBe(origem.element)
  })

  it('permite aceitar uma prévia comum pela gaveta', async () => {
    api.obterVinculoDoTelegram.mockResolvedValue(vinculoAtivo)
    api.listarOperacoesAssistidas.mockResolvedValue({
      itens: [operacaoPendente],
      pagina: 0,
      tamanho: 20,
      totalDeItens: 1,
      totalDePaginas: 1,
    })
    api.obterOperacaoAssistida.mockResolvedValue({
      ...operacaoPendente,
      proposta: { minutos: 20 },
      assinatura: 'sha256:assinatura',
      versoesConsultadas: { bloco: 1 },
      confirmadaEm: null,
      aplicadaEm: null,
      canceladaEm: null,
      falha: null,
      resultado: null,
    })
    api.confirmarOperacaoAssistidaPelaWeb.mockResolvedValue({
      ...operacaoPendente,
      estado: 'APLICADA',
      proposta: { minutos: 20 },
      assinatura: 'sha256:assinatura',
      versoesConsultadas: { bloco: 1 },
      confirmadaEm: '2026-07-21T11:05:00-03:00',
      aplicadaEm: '2026-07-21T11:05:01-03:00',
      canceladaEm: null,
      falha: null,
      resultado: { concluido: true },
    })
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    pagina = await montar()

    await pagina
      .get('button[aria-label="Ver detalhes: Concluir o bloco informado"]')
      .trigger('click')
    await flushPromises()
    await pagina
      .findAll('button')
      .find((item) => item.text().includes('Aceitar e aplicar'))!
      .trigger('click')
    await flushPromises()

    expect(api.confirmarOperacaoAssistidaPelaWeb).toHaveBeenCalledWith(
      'operacao-pendente',
    )
    expect(pagina.text()).toContain('Operação confirmada e aplicada.')
    expect(pagina.get('[role="dialog"]').text()).toContain('Aplicada')
    expect(pagina.get('[role="dialog"]').text()).not.toContain(
      'Aceitar e aplicar',
    )
  })

  it('permite cancelar uma prévia pendente sem aplicá-la', async () => {
    api.obterVinculoDoTelegram.mockResolvedValue(vinculoAtivo)
    api.listarOperacoesAssistidas.mockResolvedValue({
      itens: [operacaoPendente],
      pagina: 0,
      tamanho: 20,
      totalDeItens: 1,
      totalDePaginas: 1,
    })
    api.obterOperacaoAssistida.mockResolvedValue({
      ...operacaoPendente,
      proposta: { minutos: 20 },
      assinatura: 'sha256:assinatura',
      versoesConsultadas: { bloco: 1 },
      confirmadaEm: null,
      aplicadaEm: null,
      canceladaEm: null,
      falha: null,
      resultado: null,
    })
    api.cancelarOperacaoAssistida.mockResolvedValue({
      ...operacaoPendente,
      estado: 'CANCELADA',
      proposta: { minutos: 20 },
      assinatura: 'sha256:assinatura',
      versoesConsultadas: { bloco: 1 },
      confirmadaEm: null,
      aplicadaEm: null,
      canceladaEm: '2026-07-21T11:05:01-03:00',
      falha: null,
      resultado: null,
    })
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    pagina = await montar()

    await pagina
      .get('button[aria-label="Ver detalhes: Concluir o bloco informado"]')
      .trigger('click')
    await flushPromises()
    await pagina
      .findAll('button')
      .find((item) => item.text().includes('Cancelar operação'))!
      .trigger('click')
    await flushPromises()

    expect(api.cancelarOperacaoAssistida).toHaveBeenCalledWith(
      'operacao-pendente',
    )
    expect(pagina.text()).toContain('Operação cancelada. Nenhuma alteração')
    expect(pagina.get('[role="dialog"]').text()).toContain('Cancelada')
  })

  it('mantém a prévia pendente quando a confirmação web conflita', async () => {
    api.obterVinculoDoTelegram.mockResolvedValue(vinculoAtivo)
    api.listarOperacoesAssistidas.mockResolvedValue({
      itens: [operacaoPendente],
      pagina: 0,
      tamanho: 20,
      totalDeItens: 1,
      totalDePaginas: 1,
    })
    api.obterOperacaoAssistida.mockResolvedValue({
      ...operacaoPendente,
      proposta: { minutos: 20 },
      assinatura: 'sha256:assinatura',
      versoesConsultadas: { bloco: 1 },
      confirmadaEm: null,
      aplicadaEm: null,
      canceladaEm: null,
      falha: null,
      resultado: null,
    })
    api.confirmarOperacaoAssistidaPelaWeb.mockRejectedValue(
      new ErroDaApi(409, 'A prévia mudou. Atualize e tente novamente.'),
    )
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    pagina = await montar(true)

    await pagina
      .get('button[aria-label="Ver detalhes: Concluir o bloco informado"]')
      .trigger('click')
    await flushPromises()
    await pagina
      .findAll('button')
      .find((item) => item.text().includes('Aceitar e aplicar'))!
      .trigger('click')
    await flushPromises()

    expect(api.confirmarOperacaoAssistidaPelaWeb).toHaveBeenCalledOnce()
    expect(api.listarOperacoesAssistidas).toHaveBeenCalledTimes(2)
    expect(pagina.get('[role="dialog"]').text()).toContain(
      'Aguardando confirmação',
    )
  })

  it('orienta novo login quando a sessao expira', async () => {
    api.obterVinculoDoTelegram.mockRejectedValue(
      new ErroDaApi(401, 'Sua sessão expirou.'),
    )
    pagina = await montar()

    expect(pagina.text()).toContain('Sua sessão expirou')
    expect(pagina.text()).toContain('Entrar novamente')
    expect(pagina.text()).not.toContain('Tentar novamente')
    expect(pagina.get('a').attributes('href')).toContain(
      'redirecionar=/integracoes/telegram',
    )
  })

  it('repete a carga depois de erro de rede e recupera o foco', async () => {
    api.obterVinculoDoTelegram
      .mockRejectedValueOnce(new Error('Rede indisponível'))
      .mockResolvedValueOnce(undefined)
    pagina = await montar(true)

    expect(pagina.text()).toContain('Rede indisponível')
    await pagina
      .findAll('button')
      .find((item) => item.text() === 'Tentar novamente')!
      .trigger('click')
    await flushPromises()

    expect(api.obterVinculoDoTelegram).toHaveBeenCalledTimes(2)
    expect(pagina.text()).toContain('Gerar código de conexão')
    expect(document.activeElement).toBe(
      pagina.findAll('button').find((item) => item.text() === 'Atualizar')!
        .element,
    )
  })
})
