import { describe, expect, it } from 'vitest'

import { paraEvidencia } from './apiDeEstudos'

describe('paraEvidencia', () => {
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
})
