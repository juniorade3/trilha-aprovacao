import { beforeEach, describe, expect, it, vi } from 'vitest'

const { requisitar } = vi.hoisted(() => ({ requisitar: vi.fn() }))

vi.mock('@/compartilhado/api/clienteHttp', () => ({ requisitar }))

import {
  listarMateriaisRelacionadosAosTopicos,
  listarMateriaisDeEstudo,
  paraEvidencia,
} from './apiDeEstudos'

describe('paraEvidencia', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    requisitar.mockResolvedValue({})
  })

  it('nao descarta acertos informados sem a quantidade de questoes', () => {
    expect(
      paraEvidencia({ quantidadeDeAcertos: 4, padroesDeErro: [] }),
    ).toMatchObject({
      resultadoDeQuestoes: {
        quantidadeDeQuestoes: undefined,
        quantidadeDeAcertos: 4,
      },
    })
  })

  it('omite a evidencia quando nenhum resultado foi informado', () => {
    expect(paraEvidencia({ padroesDeErro: [] })).toBeUndefined()
  })

  it('filtra materiais e consulta atalhos para os topicos da semana', async () => {
    await listarMateriaisDeEstudo('', false, undefined, 0, 100, 'topico-1')
    await listarMateriaisRelacionadosAosTopicos(['topico-1', 'topico-2'])

    expect(requisitar).toHaveBeenNthCalledWith(
      1,
      '/v1/materiais?pesquisa=&incluirArquivados=false&pagina=0&tamanho=100&identificadorDoTopico=topico-1',
      { signal: undefined },
    )
    expect(requisitar).toHaveBeenNthCalledWith(
      2,
      '/v1/materiais/atalhos-por-topico?identificadoresDosTopicos=topico-1&identificadoresDosTopicos=topico-2',
      { signal: undefined },
    )
  })
})
