// @vitest-environment jsdom

import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { ErroDaApi } from '@/compartilhado/api/clienteHttp'

const chamadas = vi.hoisted(() => ({
  corrigirExtracaoDaImportacao: vi.fn(),
  extrairCargoComInterpretacaoAssistida: vi.fn(),
  obterImportacaoDeEdital: vi.fn(),
  obterRelatorioDaImportacao: vi.fn(),
  iniciarNovaTentativaDaImportacao: vi.fn(),
  prepararImportacaoDeEdital: vi.fn(),
  receberArquivoDoEdital: vi.fn(),
  receberTextoDoEdital: vi.fn(),
  registrarDecisoesDaImportacao: vi.fn(),
}))

const chamadasDeConcursos = vi.hoisted(() => ({
  listarConcursos: vi.fn(),
}))

vi.mock('./apiDeImportacaoDeEdital', () => chamadas)
vi.mock('./apiDeConcursos', () => chamadasDeConcursos)

import ImportacaoDeEditalPagina from './ImportacaoDeEditalPagina.vue'
import type {
  EstadoDaImportacaoDeEdital,
  ImportacaoDeEdital,
  ValorExtraido,
} from './apiDeImportacaoDeEdital'

const dado = <T>(valor: T | null, confianca = 0.98): ValorExtraido<T> => ({
  valor,
  confianca,
  fonte: { pagina: 12, secao: '3.1', trecho: String(valor) },
  inferido: false,
})

function importacao(estado: EstadoDaImportacaoDeEdital): ImportacaoDeEdital {
  return {
    identificador: 'importacao-1',
    estado,
    tipoDaFonte: 'PDF_TEXTUAL',
    nomeDoArquivo: 'edital.pdf',
    tipoMime: 'application/pdf',
    sha256: 'a'.repeat(64),
    tamanhoEmBytes: 1000,
    modo: 'CRIAR_NOVO',
    versaoAtualDaExtracao: 1,
    criadoEm: '2026-07-26T10:00:00Z',
    atualizadoEm: '2026-07-26T10:00:00Z',
    problemas: [],
    extracao: {
      versaoDoContrato: '1',
      fonte: {
        nomeDoArquivo: 'edital.pdf',
        sha256: 'a'.repeat(64),
        paginas: 20,
      },
      cargos: [
        {
          chave: 'cargo-auditor',
          nome: dado('Auditor'),
          area: dado('Tecnologia'),
          especialidade: dado('Sistemas'),
          nivelDeEscolaridade: dado('SUPERIOR'),
          ordem: 1,
        },
        {
          chave: 'cargo-tecnico',
          nome: dado('Técnico'),
          area: dado('Administrativa'),
          especialidade: dado<string>(null),
          nivelDeEscolaridade: dado('MEDIO'),
          ordem: 2,
        },
      ],
      provas: [],
      materias: [],
      avisos: [],
      incertezas: [],
    },
  }
}

function criarRoteador() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      {
        path: '/concursos/importar',
        name: 'importacao-de-edital-nova',
        component: ImportacaoDeEditalPagina,
      },
      {
        path: '/concursos/importacoes/:identificador',
        name: 'importacao-de-edital',
        component: ImportacaoDeEditalPagina,
      },
      { path: '/concursos', component: { template: '<div />' } },
      {
        path: '/concursos/:identificador',
        component: { template: '<div />' },
      },
    ],
  })
}

async function montar(caminho: string) {
  const roteador = criarRoteador()
  await roteador.push(caminho)
  await roteador.isReady()
  const pagina = mount(ImportacaoDeEditalPagina, {
    attachTo: document.body,
    global: { plugins: [roteador] },
  })
  await flushPromises()
  return { pagina, roteador }
}

describe('ImportacaoDeEditalPagina', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    document.body.innerHTML = ''
    chamadasDeConcursos.listarConcursos.mockResolvedValue({
      itens: [],
      pagina: 0,
      tamanho: 12,
      totalDeItens: 0,
      totalDePaginas: 0,
    })
  })

  it('recebe texto e transforma a importacao em rota retomavel', async () => {
    chamadas.receberTextoDoEdital.mockResolvedValue(importacao('EXTRAINDO'))
    const { pagina, roteador } = await montar('/concursos/importar')

    await pagina.get('input[value="TEXTO"]').setValue(true)
    await pagina.get('#texto-do-edital').setValue('Conteúdo programático')
    await pagina.get('.cartao-do-assistente').trigger('submit')
    await flushPromises()

    expect(chamadas.receberTextoDoEdital).toHaveBeenCalledWith(
      'Conteúdo programático',
      'texto-colado.txt',
      {
        modo: 'CRIAR_NOVO',
        identificadorDoConcursoExistente: undefined,
      },
    )
    expect(roteador.currentRoute.value.fullPath).toBe(
      '/concursos/importacoes/importacao-1',
    )
    expect(pagina.text()).toContain('Extraindo dados do edital')
    pagina.unmount()
  })

  it('aceita arquivo TXT e exige selecao mesmo com extracao validada', async () => {
    const validada = importacao('VALIDADA')
    chamadas.receberArquivoDoEdital.mockResolvedValue(validada)
    const { pagina } = await montar('/concursos/importar')
    Object.defineProperty(pagina.get('form').element, 'reportValidity', {
      configurable: true,
      value: () => true,
    })
    const entrada = pagina.get('#arquivo-do-edital')
    const arquivo = new File(['CONCURSO: Teste'], 'edital.txt', {
      type: 'text/plain',
    })
    Object.defineProperty(entrada.element, 'files', {
      configurable: true,
      value: [arquivo],
    })

    await entrada.trigger('change')
    await pagina.get('.cartao-do-assistente').trigger('submit')
    await flushPromises()

    expect(chamadas.receberArquivoDoEdital).toHaveBeenCalledWith(arquivo, {
      modo: 'CRIAR_NOVO',
      identificadorDoConcursoExistente: undefined,
    })
    expect(pagina.text()).toContain('Revisar extração')
    expect(pagina.text()).not.toContain('Gerar prévia segura')
    pagina.unmount()
  })

  it('exige cargo explicito e nao transforma texto livre em correcao', async () => {
    const aguardando = importacao('AGUARDANDO_SELECAO')
    aguardando.problemas = [
      {
        severidade: 'EXIGE_DECISAO',
        codigo: 'SELECAO_DE_CARGO_OBRIGATORIA',
        mensagem: 'Escolha um cargo explicitamente.',
        caminho: 'cargos',
      },
      {
        severidade: 'AVISO',
        codigo: 'DATA_AUSENTE',
        mensagem: 'Data da prova não encontrada.',
      },
    ]
    chamadas.obterImportacaoDeEdital.mockResolvedValue(aguardando)
    chamadas.registrarDecisoesDaImportacao.mockResolvedValue({
      ...aguardando,
      estado: 'VALIDADA',
      chaveDoCargoSelecionado: 'cargo-tecnico',
      problemas: [],
    })
    const { pagina } = await montar('/concursos/importacoes/importacao-1')

    expect(pagina.text()).toContain('Exige sua decisão')
    expect(pagina.text()).toContain('Aviso para revisão')
    const salvar = pagina
      .findAll('button')
      .find((botao) => botao.text().includes('Salvar seleção'))!
    expect(salvar.attributes('disabled')).toBeDefined()

    await pagina.get('input[value="cargo-tecnico"]').setValue(true)
    await salvar.trigger('click')
    await flushPromises()

    expect(chamadas.registrarDecisoesDaImportacao).toHaveBeenCalledWith(
      'importacao-1',
      expect.objectContaining({
        chaveDoCargoSelecionado: 'cargo-tecnico',
        versaoDaExtracao: 1,
        decisoesHumanas: {},
      }),
    )
    expect(
      chamadas.registrarDecisoesDaImportacao.mock.calls[0]?.[1],
    ).not.toHaveProperty('identificadorDoUsuario')
    expect(pagina.text()).toContain('Gerar prévia segura')
    pagina.unmount()
  })

  it('salva correcoes intermediarias com versao e recarrega em conflito', async () => {
    const aguardando = importacao('AGUARDANDO_CORRECOES')
    aguardando.extracao!.edital = {
      titulo: dado('Versão inicial'),
      numero: dado('1'),
      ano: dado(2026),
      descricao: dado<string>(null),
      dataDePublicacao: dado<string>(null),
    }
    const atualizada = importacao('AGUARDANDO_CORRECOES')
    atualizada.versaoAtualDaExtracao = 2
    atualizada.hashDaExtracaoAtual = 'extracao-2'
    atualizada.extracao!.edital = {
      titulo: dado('Versão do servidor'),
      numero: dado('1'),
      ano: dado(2026),
      descricao: dado<string>(null),
      dataDePublicacao: dado<string>(null),
    }
    chamadas.obterImportacaoDeEdital
      .mockResolvedValueOnce(aguardando)
      .mockResolvedValueOnce(atualizada)
    chamadas.corrigirExtracaoDaImportacao.mockRejectedValue(
      new ErroDaApi(
        409,
        'Extração alterada.',
        'EXTRACAO_DA_IMPORTACAO_DESATUALIZADA',
      ),
    )
    const { pagina } = await montar('/concursos/importacoes/importacao-1')
    const titulo = pagina
      .findAll('label')
      .find((label) => label.text().includes('Título do edital'))!
      .get('input')

    await titulo.setValue('Minha correção')
    await pagina
      .findAll('button')
      .find((botao) => botao.text().includes('Salvar correções'))!
      .trigger('click')
    await flushPromises()

    expect(chamadas.corrigirExtracaoDaImportacao).toHaveBeenCalledWith(
      'importacao-1',
      1,
      expect.objectContaining({
        edital: expect.objectContaining({
          titulo: expect.objectContaining({ valor: 'Minha correção' }),
        }),
      }),
      [],
    )
    expect(pagina.get('[role="alert"]').text()).toContain(
      'rascunho local foi descartado',
    )
    expect(
      (
        pagina
          .findAll('label')
          .find((label) => label.text().includes('Título do edital'))!
          .get('input').element as HTMLInputElement
      ).value,
    ).toBe('Versão do servidor')
    expect(pagina.text()).not.toContain('Há correções não salvas')
    pagina.unmount()
  })

  it('exibe o codigo e a mensagem devolvidos quando a importacao falha', async () => {
    const falhou = importacao('FALHOU')
    falhou.problemas = [
      {
        severidade: 'BLOQUEANTE',
        codigo: 'CARGO_AUSENTE',
        mensagem: 'Campo depende de um cargo anterior.',
        caminho: 'cargos',
      },
    ]
    chamadas.obterImportacaoDeEdital.mockResolvedValue(falhou)

    const { pagina } = await montar('/concursos/importacoes/importacao-1')

    expect(pagina.text()).toContain('Importação falhou')
    expect(pagina.text()).toContain('CARGO_AUSENTE')
    expect(pagina.text()).toContain('Campo depende de um cargo anterior.')
    pagina.unmount()
  })

  it('reinicia o componente ao voltar para uma nova importacao', async () => {
    const falhou = importacao('FALHOU')
    falhou.problemas = [
      {
        severidade: 'BLOQUEANTE',
        codigo: 'CARGO_AUSENTE',
        mensagem: 'Campo depende de um cargo anterior.',
      },
    ]
    chamadas.obterImportacaoDeEdital.mockResolvedValue(falhou)
    const { pagina, roteador } = await montar(
      '/concursos/importacoes/importacao-1',
    )

    await pagina.get('a[href="/concursos/importar"]').trigger('click')
    await flushPromises()

    expect(roteador.currentRoute.value.fullPath).toBe('/concursos/importar')
    expect(pagina.text()).toContain('Como deseja enviar o edital?')
    expect(pagina.text()).not.toContain('Importação falhou')
    expect(pagina.text()).not.toContain('CARGO_AUSENTE')
    expect(chamadasDeConcursos.listarConcursos).toHaveBeenCalled()
    pagina.unmount()
  })

  it('aborta consulta antiga e ignora sua resposta depois da troca de rota', async () => {
    let concluirConsulta: ((valor: ImportacaoDeEdital) => void) | undefined
    let sinalDaConsulta: AbortSignal | undefined
    chamadas.obterImportacaoDeEdital.mockImplementation(
      (_identificador: string, sinal: AbortSignal) => {
        sinalDaConsulta = sinal
        return new Promise<ImportacaoDeEdital>((resolve) => {
          concluirConsulta = resolve
        })
      },
    )
    const { pagina, roteador } = await montar(
      '/concursos/importacoes/importacao-1',
    )

    await roteador.push('/concursos/importar')
    await flushPromises()

    expect(sinalDaConsulta?.aborted).toBe(true)
    concluirConsulta?.(importacao('EXTRAINDO'))
    await flushPromises()
    expect(pagina.text()).toContain('Como deseja enviar o edital?')
    expect(pagina.text()).not.toContain('Extraindo dados do edital')
    pagina.unmount()
  })

  it('mostra previa sem aplicar e acompanha confirmacao reforcada', async () => {
    const validada = {
      ...importacao('VALIDADA'),
      chaveDoCargoSelecionado: 'cargo-auditor',
    }
    const previa = {
      resumo: 'Criar estrutura do cargo Auditor.',
      contagens: { materias: 2, topicos: 6 },
      itensACriar: [{ tipo: 'MATERIA', nome: 'Direito Constitucional' }],
      itensAReutilizar: [],
      conflitos: [],
      incertezas: ['Peso não informado.'],
      camposAusentes: ['Data da prova'],
      nadaFoiAlterado: true,
    }
    chamadas.obterImportacaoDeEdital.mockResolvedValue(validada)
    chamadas.prepararImportacaoDeEdital.mockResolvedValue({
      importacao: validada,
      previa,
    })
    const { pagina } = await montar('/concursos/importacoes/importacao-1')

    await pagina
      .findAll('button')
      .find((botao) => botao.text().includes('Gerar prévia da importação'))!
      .trigger('click')
    await flushPromises()

    expect(pagina.text()).toContain('Nada foi alterado')
    expect(pagina.text()).toContain('Direito Constitucional')
    expect(pagina.text()).toContain('peça ao assistente no Telegram')
    expect(pagina.text()).toContain('importacao-1')
    expect(pagina.text()).not.toContain('Aplicar agora')
    pagina.unmount()
  })

  it('descarta a previa quando a extracao muda antes da preparacao', async () => {
    const validada = {
      ...importacao('VALIDADA'),
      hashDaExtracaoAtual: 'extracao-1',
      chaveDoCargoSelecionado: 'cargo-auditor',
    }
    chamadas.obterImportacaoDeEdital
      .mockResolvedValueOnce(validada)
      .mockResolvedValueOnce({
        ...validada,
        estado: 'AGUARDANDO_SELECAO',
        versaoAtualDaExtracao: 2,
        hashDaExtracaoAtual: 'extracao-2',
        chaveDoCargoSelecionado: undefined,
      })
    chamadas.prepararImportacaoDeEdital.mockRejectedValue(
      new ErroDaApi(
        409,
        'Extração alterada.',
        'EXTRACAO_DA_IMPORTACAO_DESATUALIZADA',
      ),
    )
    const { pagina } = await montar('/concursos/importacoes/importacao-1')

    await pagina
      .findAll('button')
      .find((botao) => botao.text().includes('Gerar prévia da importação'))!
      .trigger('click')
    await flushPromises()

    expect(pagina.get('[role="alert"]').text()).toContain('A extração mudou')
    expect(pagina.text()).toContain('Revisar extração')
    expect(pagina.text()).not.toContain('Prévia da importação')
    pagina.unmount()
  })

  it('exibe recibo e leva para revisao dos mapeamentos', async () => {
    chamadas.obterImportacaoDeEdital.mockResolvedValue(importacao('APLICADA'))
    chamadas.obterRelatorioDaImportacao.mockResolvedValue({
      identificadorDaImportacao: 'importacao-1',
      identificadorDoConcurso: 'concurso-1',
      situacaoDoConcurso: 'PLANEJADO',
      contagens: { materias: 2, itens: 10 },
      identificadoresCriados: {},
      reutilizacoes: [],
      pendencias: ['Revisar uma sugestão de mapeamento.'],
      incertezas: [],
      sugestoesDeMapeamento: 1,
      aplicadoEm: '2026-07-26T10:05:00Z',
    })
    const { pagina } = await montar('/concursos/importacoes/importacao-1')

    expect(pagina.text()).toContain('Estrutura criada com atomicidade')
    expect(pagina.text()).toContain('Planejado')
    expect(
      pagina
        .get('a[href="/concursos/concurso-1?foco=mapeamentos"]')
        .attributes('href'),
    ).toBe('/concursos/concurso-1?foco=mapeamentos')
    pagina.unmount()
  })
})
