import { beforeEach, describe, expect, it, vi } from 'vitest'

const { requisitar } = vi.hoisted(() => ({ requisitar: vi.fn() }))

vi.mock('@/compartilhado/api/clienteHttp', () => ({ requisitar }))

import { consultarPriorizacaoDeTopicos } from './apiDePriorizacaoDeTopicos'

describe('apiDePriorizacaoDeTopicos', () => {
  beforeEach(() => vi.clearAllMocks())

  it('consulta a data de referencia e o filtro opcional de materia', async () => {
    const sinal = new AbortController().signal
    requisitar.mockResolvedValue({ materias: [] })

    await consultarPriorizacaoDeTopicos('2026-07-21', 'materia 1', sinal)

    expect(requisitar).toHaveBeenCalledWith(
      '/v1/priorizacao-de-topicos?dataDeReferencia=2026-07-21&identificadorDaMateria=materia+1',
      { signal: sinal },
    )
  })

  it('omite o filtro de materia quando ele nao foi informado', async () => {
    requisitar.mockResolvedValue({ materias: [] })

    await consultarPriorizacaoDeTopicos('2026-07-21')

    expect(requisitar).toHaveBeenCalledWith(
      '/v1/priorizacao-de-topicos?dataDeReferencia=2026-07-21',
      { signal: undefined },
    )
  })
})
