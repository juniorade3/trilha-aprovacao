// @vitest-environment jsdom

import { beforeEach, describe, expect, it, vi } from 'vitest'

const { requisitar } = vi.hoisted(() => ({ requisitar: vi.fn() }))

vi.mock('@/compartilhado/api/clienteHttp', () => ({ requisitar }))

import {
  corrigirExtracaoDaImportacao,
  extrairCargoComInterpretacaoAssistida,
  iniciarNovaTentativaDaImportacao,
  obterImportacaoDeEdital,
  obterRelatorioDaImportacao,
  prepararImportacaoDeEdital,
  receberArquivoDoEdital,
  receberTextoDoEdital,
  registrarDecisoesDaImportacao,
  type DecisoesDaImportacaoDeEdital,
} from './apiDeImportacaoDeEdital'

const decisoes: DecisoesDaImportacaoDeEdital = {
  chaveDoCargoSelecionado: 'cargo-auditor',
  modo: 'CRIAR_NOVO',
  politicaDeReutilizacao: 'EXIGIR_DECISAO',
  versaoDaExtracao: 2,
  decisoesHumanas: {},
}

describe('apiDeImportacaoDeEdital', () => {
  beforeEach(() => {
    requisitar.mockReset()
    requisitar.mockResolvedValue({})
  })

  it('envia arquivo como formulario sem colocar usuario no contrato', async () => {
    const arquivo = new File(['edital'], 'edital.pdf', {
      type: 'application/pdf',
    })

    await receberArquivoDoEdital(arquivo, {
      modo: 'COMPLEMENTAR_EXISTENTE',
      identificadorDoConcursoExistente: 'concurso-1',
    })

    const [caminho, opcoes] = requisitar.mock.calls[0]!
    expect(caminho).toBe('/v1/importacoes-de-edital')
    expect(opcoes.method).toBe('POST')
    expect(opcoes.body).toBeInstanceOf(FormData)
    const formulario = opcoes.body as FormData
    expect(formulario.get('arquivo')).toBe(arquivo)
    expect(formulario.get('modo')).toBe('COMPLEMENTAR_EXISTENTE')
    expect(formulario.get('identificadorDoConcursoExistente')).toBe(
      'concurso-1',
    )
    expect(Array.from(formulario.keys())).not.toContain(
      'identificadorDoUsuario',
    )
  })

  it('envia texto por contrato JSON separado', async () => {
    await receberTextoDoEdital('CONTEUDO PROGRAMATICO', 'texto-colado.txt', {
      modo: 'CRIAR_NOVO',
    })

    expect(requisitar).toHaveBeenCalledWith(
      '/v1/importacoes-de-edital/textos',
      {
        method: 'POST',
        body: JSON.stringify({
          texto: 'CONTEUDO PROGRAMATICO',
          nomeDaFonte: 'texto-colado.txt',
          modo: 'CRIAR_NOVO',
        }),
      },
    )
  })

  it('consulta, decide, prepara e obtem relatorio', async () => {
    const sinal = new AbortController().signal

    await obterImportacaoDeEdital('importacao-1', sinal)
    await registrarDecisoesDaImportacao('importacao-1', decisoes)
    await prepararImportacaoDeEdital('importacao-1', decisoes)
    await obterRelatorioDaImportacao('importacao-1', sinal)
    await iniciarNovaTentativaDaImportacao('importacao-1')

    expect(requisitar).toHaveBeenNthCalledWith(
      1,
      '/v1/importacoes-de-edital/importacao-1',
      { signal: sinal },
    )
    expect(requisitar).toHaveBeenNthCalledWith(
      2,
      '/v1/importacoes-de-edital/importacao-1/decisoes',
      { method: 'PUT', body: JSON.stringify(decisoes) },
    )
    expect(requisitar).toHaveBeenNthCalledWith(
      3,
      '/v1/importacoes-de-edital/importacao-1/preparacao',
      { method: 'POST', body: JSON.stringify(decisoes) },
    )
    expect(requisitar).toHaveBeenNthCalledWith(
      4,
      '/v1/importacoes-de-edital/importacao-1/relatorio',
      { signal: sinal },
    )
    expect(requisitar).toHaveBeenNthCalledWith(
      5,
      '/v1/importacoes-de-edital/importacao-1/nova-tentativa',
      { method: 'POST' },
    )
  })

  it('envia correcao manual com controle de versao', async () => {
    const extracao = {
      versaoDoContrato: '1' as const,
      fonte: {
        nomeDoArquivo: 'edital.txt',
        sha256: 'a'.repeat(64),
        paginas: 1,
      },
      cargos: [],
      provas: [],
      materias: [],
      avisos: [],
      incertezas: [],
    }

    await corrigirExtracaoDaImportacao('importacao-1', 2, extracao)

    expect(requisitar).toHaveBeenCalledWith(
      '/v1/importacoes-de-edital/importacao-1/extracao',
      {
        method: 'PUT',
        body: JSON.stringify({
          versaoEsperada: 2,
          extracao,
          confirmacoesDeCampos: [],
        }),
      },
    )
  })

  it('envia somente as confirmacoes de campos explicitamente informadas', async () => {
    const extracao = {
      versaoDoContrato: '1' as const,
      fonte: {
        nomeDoArquivo: 'edital.txt',
        sha256: 'a'.repeat(64),
        paginas: 1,
      },
      cargos: [],
      provas: [],
      materias: [],
      avisos: [],
      incertezas: [],
    }
    const confirmacoesDeCampos = [
      {
        tipoDoRecurso: 'cargo',
        chaveDoRecurso: 'cargo-dados',
        campo: 'area',
      },
    ]

    await corrigirExtracaoDaImportacao(
      'importacao-1',
      2,
      extracao,
      confirmacoesDeCampos,
    )

    expect(requisitar).toHaveBeenCalledWith(
      '/v1/importacoes-de-edital/importacao-1/extracao',
      {
        method: 'PUT',
        body: JSON.stringify({
          versaoEsperada: 2,
          extracao,
          confirmacoesDeCampos,
        }),
      },
    )
  })

  it('solicita extracao assistida com um alvo explicito e versao esperada', async () => {
    await extrairCargoComInterpretacaoAssistida('importacao-1', 3, {
      descricaoDoCargoAlvo: 'Analista de TI — Engenharia de Dados',
    })

    expect(requisitar).toHaveBeenCalledWith(
      '/v1/importacoes-de-edital/importacao-1/extracao-assistida',
      {
        method: 'POST',
        body: JSON.stringify({
          versaoEsperada: 3,
          descricaoDoCargoAlvo: 'Analista de TI — Engenharia de Dados',
        }),
      },
    )
  })
})
