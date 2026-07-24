import { beforeEach, describe, expect, it, vi } from 'vitest'

const { requisitar } = vi.hoisted(() => ({ requisitar: vi.fn() }))

vi.mock('@/compartilhado/api/clienteHttp', () => ({ requisitar }))

import {
  aplicarGeracaoDeterministica,
  gerarPreviaDeterministica,
} from './apiDePlanejamento'

describe('apiDePlanejamento - geracao deterministica', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    requisitar.mockResolvedValue({})
  })

  it('solicita a previa com referencia, duracao e materias por dia', async () => {
    await gerarPreviaDeterministica('plano-1', '2026-07-21', 50, 4)

    expect(requisitar).toHaveBeenCalledWith(
      '/v1/planos-semanais/plano-1/geracao-deterministica/previa',
      {
        method: 'POST',
        body: JSON.stringify({
          dataDeReferencia: '2026-07-21',
          duracaoDoBlocoPrincipalEmMinutos: 50,
          quantidadeDeMateriasPorDia: 4,
        }),
      },
    )
  })

  it('aplica somente a previa assinada e informa a regeneracao', async () => {
    await aplicarGeracaoDeterministica(
      'plano-1',
      '2026-07-21',
      75,
      true,
      'assinatura-1',
      5,
    )

    expect(requisitar).toHaveBeenCalledWith(
      '/v1/planos-semanais/plano-1/geracao-deterministica',
      {
        method: 'POST',
        body: JSON.stringify({
          dataDeReferencia: '2026-07-21',
          duracaoDoBlocoPrincipalEmMinutos: 75,
          quantidadeDeMateriasPorDia: 5,
          substituirBlocosGerados: true,
          assinaturaDaPrevia: 'assinatura-1',
        }),
      },
    )
  })
})
