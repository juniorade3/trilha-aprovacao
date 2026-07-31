// @vitest-environment jsdom

import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import RevisaoDaExtracaoDoEdital from './RevisaoDaExtracaoDoEdital.vue'
import type {
  EstadoDaImportacaoDeEdital,
  ExtracaoEstruturadaDoEdital,
  ImportacaoDeEdital,
  NivelDeEscolaridade,
  ProblemaDaImportacao,
  ValorExtraido,
} from './apiDeImportacaoDeEdital'

const dado = <T>(valor: T | null): ValorExtraido<T> => ({
  valor,
  confianca: 0.98,
  fonte: { pagina: 2, secao: 'Conteúdo', trecho: String(valor) },
  inferido: false,
})

function importacao(
  estado: EstadoDaImportacaoDeEdital = 'AGUARDANDO_CORRECOES',
): ImportacaoDeEdital {
  return {
    identificador: 'importacao-1',
    estado,
    tipoDaFonte: 'PDF_TEXTUAL',
    nomeDoArquivo: 'edital.pdf',
    tipoMime: 'application/pdf',
    sha256: 'a'.repeat(64),
    tamanhoEmBytes: 1000,
    modo: 'CRIAR_NOVO',
    versaoAtualDaExtracao: 4,
    criadoEm: '2026-07-26T10:00:00Z',
    atualizadoEm: '2026-07-26T10:00:00Z',
    interpretacaoAssistidaDisponivel: true,
    problemas: [
      {
        severidade: 'EXIGE_DECISAO',
        codigo: 'ESCOLARIDADE_DO_CARGO_EXIGE_REVISAO',
        mensagem: 'Escolaridade do cargo exige revisão.',
        caminho: 'cargos[0].nivelDeEscolaridade',
        tipoDoRecurso: 'CARGO',
        chaveDoRecurso: 'cargo-administrativo',
        campo: 'nivelDeEscolaridade',
      },
    ],
    avaliacoesDosCargos: [
      {
        chaveDoCargo: 'cargo-administrativo',
        pronto: false,
        problemas: [],
      },
      {
        chaveDoCargo: 'cargo-dados',
        pronto: true,
        problemas: [],
      },
    ],
    extracao: {
      versaoDoContrato: '1',
      fonte: {
        nomeDoArquivo: 'edital.pdf',
        sha256: 'a'.repeat(64),
        paginas: 20,
      },
      concurso: {
        nome: dado('Tribunal'),
        descricao: dado<string>(null),
        orgao: dado('TJ'),
        banca: dado('Cebraspe'),
        dataPrevista: dado<string>(null),
      },
      edital: {
        titulo: dado('Edital 1/2026'),
        numero: dado('1'),
        ano: dado(2026),
        descricao: dado<string>(null),
        dataDePublicacao: dado<string>(null),
      },
      cargos: [
        {
          chave: 'cargo-administrativo',
          nome: dado('Analista Administrativo'),
          area: dado('Administrativa'),
          especialidade: dado<string>(null),
          nivelDeEscolaridade: dado<NivelDeEscolaridade>('NAO_INFORMADO'),
          ordem: 1,
        },
        {
          chave: 'cargo-dados',
          nome: dado('Analista Judiciário'),
          area: dado('Tecnologia da Informação'),
          especialidade: dado('Engenheiro de Dados'),
          nivelDeEscolaridade: dado<NivelDeEscolaridade>('SUPERIOR'),
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

function montar(valor = importacao()) {
  return mount(RevisaoDaExtracaoDoEdital, {
    attachTo: document.body,
    props: {
      importacao: valor,
      modo: 'CRIAR_NOVO',
      salvando: false,
      salvandoExtracao: false,
      extraindoComIa: false,
    },
  })
}

function importacaoComArvore(problema: ProblemaDaImportacao) {
  const valor = importacao()
  const cargo = valor.extracao!.cargos[1]!
  valor.extracao!.cargos = [cargo]
  valor.extracao!.provas = [
    {
      chave: 'prova-dados',
      chaveDoCargo: cargo.chave,
      nome: dado('Prova objetiva'),
      tipo: dado('OBJETIVA'),
      carater: dado('ELIMINATORIO_E_CLASSIFICATORIO'),
      ordem: 1,
      dataHora: dado<string>(null),
      duracaoEmMinutos: dado<number>(null),
      quantidadeDeQuestoes: dado<number>(null),
      pontuacaoMaxima: dado<number>(null),
      pontuacaoMinima: dado<number>(null),
      grupos: [
        {
          chave: 'grupo-conhecimentos',
          nome: dado('Conhecimentos específicos'),
          ordem: 1,
          quantidadeDeQuestoes: dado<number>(null),
          pontuacaoMaxima: dado<number>(null),
          pontuacaoMinima: dado<number>(null),
        },
      ],
    },
  ]
  valor.extracao!.materias = [
    {
      chave: 'materia-banco',
      chaveDoCargo: cargo.chave,
      chaveDaProva: 'prova-dados',
      chaveDoGrupo: 'grupo-conhecimentos',
      nome: dado('Banco de Dados'),
      descricao: dado<string>(null),
      ordem: 1,
      peso: dado<number>(null),
      quantidadeDeQuestoes: dado<number>(null),
      pontuacaoMaxima: dado<number>(null),
      topicos: [
        {
          chave: 'topico-sql',
          chaveDoPai: null,
          numeroOficial: dado('1'),
          nome: dado('SQL'),
          descricao: dado<string>(null),
          ordem: 1,
        },
      ],
      itensDoEdital: [
        {
          chave: 'item-select',
          chaveDoPai: null,
          numeroOficial: dado('1.1'),
          descricaoLiteral: dado('Consultas SELECT'),
          nomeNormalizado: 'Consultas SELECT',
          ordem: 1,
          chaveDoTopicoSugerido: 'topico-sql',
        },
      ],
    },
  ]
  valor.problemas = [problema]
  return valor
}

function botao(wrapper: ReturnType<typeof montar>, trecho: string) {
  const encontrado = wrapper
    .findAll('button')
    .find((item) => item.text().includes(trecho))
  if (!encontrado) throw new Error(`Botão não encontrado: ${trecho}`)
  return encontrado
}

describe('RevisaoDaExtracaoDoEdital', () => {
  it('seleciona cargo pronto sem ser bloqueado por pendencia de outro cargo', async () => {
    const wrapper = montar()

    await wrapper.get('input[value="cargo-dados"]').setValue(true)
    const salvar = botao(wrapper, 'Salvar seleção e validar')
    expect(salvar.attributes('disabled')).toBeUndefined()
    await salvar.trigger('click')

    expect(wrapper.emitted('salvar')?.[0]?.[0]).toEqual(
      expect.objectContaining({
        chaveDoCargoSelecionado: 'cargo-dados',
        versaoDaExtracao: 4,
      }),
    )
    expect(wrapper.text()).toContain(
      'Pendências de outros cargos não impedirão',
    )
    wrapper.unmount()
  })

  it('permite criar uma hierarquia minima e salvar a correcao incompleta', async () => {
    const vazia = importacao()
    vazia.problemas = [
      {
        severidade: 'BLOQUEANTE',
        codigo: 'TITULO_DO_EDITAL_OBRIGATORIO',
        mensagem: 'Título do edital obrigatório.',
        caminho: 'edital.titulo',
        tipoDoRecurso: 'EDITAL',
        campo: 'titulo',
      },
      {
        severidade: 'BLOQUEANTE',
        codigo: 'CARGO_AUSENTE',
        mensagem: 'Ao menos um cargo é obrigatório.',
        caminho: 'cargos',
      },
    ]
    vazia.avaliacoesDosCargos = []
    vazia.extracao!.cargos = []
    vazia.extracao!.provas = []
    vazia.extracao!.materias = []
    vazia.extracao!.edital!.titulo.valor = null
    const wrapper = montar(vazia)

    const titulo = wrapper
      .findAll('label')
      .find((label) => label.text().includes('Título do edital'))!
      .get('input')
    await titulo.setValue('Edital nº 2/2026')
    await botao(wrapper, 'Criar cargo, prova, grupo e matéria').trigger('click')
    const nomeDoCargo = wrapper
      .findAll('label')
      .find((label) => label.text().includes('Nome do cargo'))!
      .get('input')
    await nomeDoCargo.setValue('Analista')
    await botao(wrapper, 'Salvar correções').trigger('click')

    const extracao = wrapper.emitted('salvarExtracao')?.[0]?.[0] as
      ExtracaoEstruturadaDoEdital | undefined
    expect(extracao).toBeDefined()
    expect(extracao).toEqual(
      expect.objectContaining({
        edital: expect.objectContaining({
          titulo: expect.objectContaining({ valor: 'Edital nº 2/2026' }),
        }),
      }),
    )
    expect(extracao!.cargos).toHaveLength(1)
    expect(extracao!.provas).toHaveLength(1)
    expect(extracao!.provas[0]!.grupos).toHaveLength(1)
    expect(extracao!.materias).toHaveLength(1)
    expect(extracao!.materias[0]!.itensDoEdital).toHaveLength(1)
    expect(
      (wrapper.get('input[type="radio"]:checked').element as HTMLInputElement)
        .checked,
    ).toBe(true)
    expect(
      botao(wrapper, 'Salvar seleção e validar').attributes('disabled'),
    ).toBeDefined()
    expect(wrapper.text()).toContain(
      'Salve as correções antes de validar a seleção',
    )
    wrapper.unmount()
  })

  it('bloqueia validacao e IA enquanto o rascunho possui correcoes nao salvas', async () => {
    const valor = importacao()
    valor.chaveDoCargoSelecionado = 'cargo-dados'
    const wrapper = montar(valor)
    await wrapper
      .findAll('label')
      .find((label) => label.text().includes('Autorizo o envio'))!
      .get('input')
      .setValue(true)
    const titulo = wrapper
      .findAll('label')
      .find((label) => label.text().includes('Título do edital'))!
      .get('input')

    await titulo.setValue('Edital corrigido')

    expect(
      botao(wrapper, 'Salvar seleção e validar').attributes('disabled'),
    ).toBeDefined()
    expect(
      botao(wrapper, 'Extrair este cargo com IA').attributes('disabled'),
    ).toBeDefined()
    expect(wrapper.text()).toContain(
      'Salve as correções antes de validar a seleção ou extrair um cargo com IA',
    )
    await botao(wrapper, 'Salvar seleção e validar').trigger('click')
    await botao(wrapper, 'Extrair este cargo com IA').trigger('click')
    expect(wrapper.emitted('salvar')).toBeUndefined()
    expect(wrapper.emitted('extrairComIa')).toBeUndefined()

    const salvarCorrecao = botao(wrapper, 'Salvar correções')
    expect(salvarCorrecao.attributes('disabled')).toBeUndefined()
    await wrapper.setProps({ salvandoExtracao: true })
    expect(salvarCorrecao.attributes('disabled')).toBeDefined()
    wrapper.unmount()
  })

  it('converte texto colado em um item literal por linha', async () => {
    const vazia = importacao()
    vazia.problemas = []
    vazia.extracao!.cargos = []
    vazia.extracao!.provas = []
    vazia.extracao!.materias = []
    const wrapper = montar(vazia)

    await botao(wrapper, 'Criar cargo, prova, grupo e matéria').trigger('click')
    await wrapper
      .get('textarea[placeholder*="um item por linha"]')
      .setValue('1 Direito Constitucional\n2. Administração Pública')
    await botao(wrapper, 'Converter uma linha por item').trigger('click')
    await botao(wrapper, 'Salvar correções').trigger('click')

    const extracao = wrapper.emitted('salvarExtracao')?.[0]?.[0] as
      ExtracaoEstruturadaDoEdital | undefined
    expect(extracao).toBeDefined()
    expect(extracao!.materias[0]!.itensDoEdital).toHaveLength(2)
    expect(
      extracao!.materias[0]!.itensDoEdital.map(
        (item: {
          numeroOficial: ValorExtraido<string>
          descricaoLiteral: ValorExtraido<string>
        }) => [item.numeroOficial.valor, item.descricaoLiteral.valor],
      ),
    ).toEqual([
      ['1', 'Direito Constitucional'],
      ['2', 'Administração Pública'],
    ])
    wrapper.unmount()
  })

  it('exige consentimento e alvo antes de solicitar a extracao assistida', async () => {
    const valor = importacao()
    valor.chaveDoCargoSelecionado = 'cargo-dados'
    const wrapper = montar(valor)
    const acao = botao(wrapper, 'Extrair este cargo com IA')

    expect(acao.attributes('disabled')).toBeDefined()
    await wrapper
      .findAll('label')
      .find((label) => label.text().includes('Autorizo o envio'))!
      .get('input')
      .setValue(true)
    expect(acao.attributes('disabled')).toBeUndefined()
    await acao.trigger('click')

    expect(wrapper.emitted('extrairComIa')?.[0]?.[0]).toEqual({
      chaveDoCargoAlvo: 'cargo-dados',
    })

    await wrapper.setProps({
      extraindoComIa: false,
      erroDaIa: 'O provedor demorou demais para responder.',
    })
    expect(wrapper.text()).toContain('O staging não foi alterado')
    expect(wrapper.text()).toContain('editor manual')
    wrapper.unmount()
  })

  it('confirma explicitamente campo assistido nao obrigatorio e limpa na nova versao', async () => {
    const valor = importacao()
    const cargo = valor.extracao!.cargos[1]!
    cargo.area.inferido = true
    valor.problemas = [
      {
        severidade: 'EXIGE_DECISAO',
        codigo: 'EVIDENCIA_ASSISTIDA_NAO_VERIFICADA',
        mensagem: 'Confirme a área extraída pela IA.',
        caminho: 'cargos[1].area',
        tipoDoRecurso: 'cargo',
        chaveDoRecurso: cargo.chave,
        campo: 'area',
      },
    ]
    const wrapper = montar(valor)
    const cargoComPendencia = wrapper.findAll('.secao-do-cargo')[1]!
    const campoDaArea = cargoComPendencia
      .findAll('label')
      .find((label) => label.text().includes('Área'))!

    expect(campoDaArea.text()).toContain('Confirme a área extraída pela IA.')
    const confirmacao = campoDaArea.get('[role="checkbox"]')
    expect(confirmacao.attributes('aria-checked')).toBe('false')
    await confirmacao.trigger('keydown', { key: ' ' })
    expect(confirmacao.attributes('aria-checked')).toBe('true')
    expect(campoDaArea.text()).toContain('Valor confirmado')
    await botao(wrapper, 'Salvar correções').trigger('click')

    expect(wrapper.emitted('salvarExtracao')?.[0]?.[1]).toEqual([
      {
        tipoDoRecurso: 'cargo',
        chaveDoRecurso: cargo.chave,
        campo: 'area',
      },
    ])

    const novaVersao = JSON.parse(JSON.stringify(valor)) as ImportacaoDeEdital
    novaVersao.versaoAtualDaExtracao = 5
    novaVersao.hashDaExtracaoAtual = 'extracao-5'
    await wrapper.setProps({ importacao: novaVersao })
    expect(campoDaArea.text()).not.toContain('Valor confirmado')
    expect(
      botao(wrapper, 'Salvar correções').attributes('disabled'),
    ).toBeDefined()
    wrapper.unmount()
  })

  it('nao permite confirmar campo assistido vazio', () => {
    const valor = importacaoComArvore({
      severidade: 'EXIGE_DECISAO',
      codigo: 'EVIDENCIA_ASSISTIDA_NAO_VERIFICADA',
      mensagem: 'Confirme a duração extraída pela IA.',
      caminho: 'provas[0].duracaoEmMinutos',
      tipoDoRecurso: 'prova',
      chaveDoRecurso: 'prova-dados',
      campo: 'duracaoEmMinutos',
    })
    valor.extracao!.provas[0]!.duracaoEmMinutos.inferido = true
    const wrapper = montar(valor)
    const duracao = wrapper
      .findAll('label')
      .find((label) => label.text().includes('Duração (minutos)'))!

    expect(duracao.text()).toContain(
      'Preencha o campo antes de confirmar este valor',
    )
    expect(duracao.find('[role="checkbox"]').exists()).toBe(false)
    wrapper.unmount()
  })

  it('permite confirmar explicitamente topico raiz sugerido pela IA', async () => {
    const valor = importacaoComArvore({
      severidade: 'EXIGE_DECISAO',
      codigo: 'EVIDENCIA_ASSISTIDA_NAO_VERIFICADA',
      mensagem: 'Confirme que este tópico não possui pai.',
      caminho: 'materias[0].topicos[0].chaveDoPai',
      tipoDoRecurso: 'topico',
      chaveDoRecurso: 'topico-sql',
      campo: 'chaveDoPai',
    })
    expect(valor.extracao!.materias[0]!.topicos[0]!.chaveDoPai).toBeNull()
    const wrapper = montar(valor)
    const topicoPai = wrapper
      .findAll('label')
      .find((label) => label.text().includes('Tópico pai'))!

    expect(topicoPai.text()).toContain(
      'Confirme que este tópico não possui pai.',
    )
    const confirmacao = topicoPai.get('[role="checkbox"]')
    expect(confirmacao.text()).toContain('Confirmar esta decisão')
    await confirmacao.trigger('click')
    expect(confirmacao.attributes('aria-checked')).toBe('true')
    expect(confirmacao.text()).toContain('Decisão confirmada')
    await botao(wrapper, 'Salvar correções').trigger('click')

    expect(wrapper.emitted('salvarExtracao')?.[0]?.[1]).toEqual([
      {
        tipoDoRecurso: 'topico',
        chaveDoRecurso: 'topico-sql',
        campo: 'chaveDoPai',
      },
    ])
    wrapper.unmount()
  })

  it('leva o foco ao campo indicado pela referencia estavel do problema', async () => {
    const wrapper = montar()

    await botao(wrapper, 'Ir para a primeira pendência').trigger('click')

    const escolaridade = wrapper
      .findAll('label')
      .find(
        (label) =>
          label.text().includes('Escolaridade') &&
          label.find('select[data-com-pendencia="true"]').exists(),
      )!
      .get('select')
    expect(document.activeElement).toBe(escolaridade.element)
    expect(escolaridade.attributes('aria-invalid')).toBe('true')
    wrapper.unmount()
  })

  it.each([
    [
      'referência estável',
      {
        severidade: 'BLOQUEANTE',
        codigo: 'ITEM_SEM_DESCRICAO',
        mensagem: 'Item literal sem descrição.',
        caminho: 'materias[8].itensDoEdital[4].descricaoLiteral',
        tipoDoRecurso: 'itemDoEdital',
        chaveDoRecurso: 'item-select',
        campo: 'descricaoLiteral',
      } satisfies ProblemaDaImportacao,
    ],
    [
      'caminho legado',
      {
        severidade: 'BLOQUEANTE',
        codigo: 'ITEM_SEM_DESCRICAO',
        mensagem: 'Item literal sem descrição.',
        caminho: 'materias[0].itensDoEdital[0].descricaoLiteral',
      } satisfies ProblemaDaImportacao,
    ],
  ])(
    'mantem cargo, prova e materia visiveis para pendencia descendente por %s',
    async (_descricao, problema) => {
      const wrapper = montar(importacaoComArvore(problema))
      await wrapper
        .findAll('label')
        .find((label) => label.text().includes('Mostrar somente pendências'))!
        .get('input')
        .setValue(true)

      for (const seletor of [
        '.secao-do-cargo',
        '.secao-da-prova',
        '.secao-da-materia',
      ])
        expect(wrapper.get(seletor).attributes('style') ?? '').not.toContain(
          'display: none',
        )
      wrapper.unmount()
    },
  )

  it('edita topico pai sem oferecer o proprio topico ou descendentes', async () => {
    const valor = importacaoComArvore({
      severidade: 'BLOQUEANTE',
      codigo: 'HIERARQUIA_DE_TOPICOS_INVALIDA',
      mensagem: 'Tópico possui pai inválido.',
      caminho: 'materias[0].topicos[1].chaveDoPai',
      tipoDoRecurso: 'topico',
      chaveDoRecurso: 'topico-filho',
      campo: 'chaveDoPai',
    })
    const materia = valor.extracao!.materias[0]!
    materia.topicos.push({
      chave: 'topico-filho',
      chaveDoPai: 'topico-sql',
      numeroOficial: dado('1.1'),
      nome: dado('Subconsultas'),
      descricao: dado<string>(null),
      ordem: 2,
    })
    const wrapper = montar(valor)
    const camposDePai = wrapper
      .findAll('label')
      .filter((label) => label.text().includes('Tópico pai'))
    const opcoesDoPai = camposDePai[0]!
      .findAll('option')
      .map((opcao) => opcao.text())
    const campoDoFilho = camposDePai[1]!

    expect(opcoesDoPai).toEqual(['Tópico raiz'])
    expect(campoDoFilho.text()).toContain('SQL')
    expect(campoDoFilho.text()).not.toContain('Subconsultas')
    expect(campoDoFilho.text()).toContain('Tópico possui pai inválido.')

    await campoDoFilho.get('select').setValue('')
    await botao(wrapper, 'Salvar correções').trigger('click')

    const extracao = wrapper.emitted(
      'salvarExtracao',
    )?.[0]?.[0] as ExtracaoEstruturadaDoEdital
    expect(extracao.materias[0]!.topicos[1]!.chaveDoPai).toBeNull()
    wrapper.unmount()
  })

  it('promove filhos ao remover topico ou item pai', async () => {
    const valor = importacaoComArvore({
      severidade: 'AVISO',
      codigo: 'REVISAO',
      mensagem: 'Revise a hierarquia.',
    })
    const materia = valor.extracao!.materias[0]!
    materia.topicos.push({
      chave: 'topico-filho',
      chaveDoPai: 'topico-sql',
      numeroOficial: dado('1.1'),
      nome: dado('Subconsultas'),
      descricao: dado<string>(null),
      ordem: 2,
    })
    materia.itensDoEdital.push({
      chave: 'item-filho',
      chaveDoPai: 'item-select',
      numeroOficial: dado('1.1.1'),
      descricaoLiteral: dado('Subconsultas correlacionadas'),
      nomeNormalizado: 'Subconsultas correlacionadas',
      ordem: 2,
      chaveDoTopicoSugerido: 'topico-filho',
    })
    const wrapper = montar(valor)

    await wrapper
      .findAll('button')
      .find((acao) => acao.text().includes('Remover tópico'))!
      .trigger('click')
    await wrapper
      .findAll('button')
      .find((acao) => acao.text().includes('Remover item'))!
      .trigger('click')
    await botao(wrapper, 'Salvar correções').trigger('click')

    const extracao = wrapper.emitted(
      'salvarExtracao',
    )?.[0]?.[0] as ExtracaoEstruturadaDoEdital
    expect(extracao.materias[0]!.topicos[0]!.chaveDoPai).toBeNull()
    expect(extracao.materias[0]!.itensDoEdital[0]!.chaveDoPai).toBeNull()
    wrapper.unmount()
  })
})
