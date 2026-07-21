import { beforeEach, describe, expect, it, vi } from 'vitest'

const { requisitar } = vi.hoisted(() => ({ requisitar: vi.fn() }))

vi.mock('@/compartilhado/api/clienteHttp', () => ({ requisitar }))

import { consultarRevisoesEspacadas } from './apiDeRevisoesEspacadas'

describe('apiDeRevisoesEspacadas', () => {
  beforeEach(() => vi.clearAllMocks())

  it('consulta a agenda entre a referencia e o horizonte informado', async () => {
    const sinal = new AbortController().signal
    requisitar.mockResolvedValue({ revisoes: [] })

    await consultarRevisoesEspacadas('2026-07-21', '2026-08-20', sinal)

    expect(requisitar).toHaveBeenCalledWith(
      '/v1/revisoes-espacadas?dataDeReferencia=2026-07-21&ate=2026-08-20',
      { signal: sinal },
    )
  })

  it('mantem o sinal opcional ao consultar apenas o dia de referencia', async () => {
    requisitar.mockResolvedValue({ revisoes: [] })

    await consultarRevisoesEspacadas('2026-07-21', '2026-07-21')

    expect(requisitar).toHaveBeenCalledWith(
      '/v1/revisoes-espacadas?dataDeReferencia=2026-07-21&ate=2026-07-21',
      { signal: undefined },
    )
  })
})
