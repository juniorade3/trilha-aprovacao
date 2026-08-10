import { beforeEach, describe, expect, it, vi } from 'vitest'

const { requisitar } = vi.hoisted(() => ({ requisitar: vi.fn() }))

vi.mock('@/compartilhado/api/clienteHttp', () => ({ requisitar }))

import {
  aderirATrilhaPublicada,
  atualizarAcompanhamentoDaTarefa,
  listarTrilhasPublicadas,
  obterTrilhaPublicada,
} from './apiDeTrilhas'

describe('apiDeTrilhas', () => {
  beforeEach(() => requisitar.mockReset())

  it('consulta o catálogo e o detalhe da trilha publicada', () => {
    const sinal = new AbortController().signal

    listarTrilhasPublicadas(sinal)
    obterTrilhaPublicada('trilha-1', sinal)

    expect(requisitar).toHaveBeenNthCalledWith(1, '/v1/trilhas', {
      signal: sinal,
    })
    expect(requisitar).toHaveBeenNthCalledWith(2, '/v1/trilhas/trilha-1', {
      signal: sinal,
    })
  })

  it('aderе e atualiza o acompanhamento de uma tarefa', () => {
    aderirATrilhaPublicada('trilha-1')
    atualizarAcompanhamentoDaTarefa('trilha-1', 'tarefa-1', 'CONCLUIDA')

    expect(requisitar).toHaveBeenNthCalledWith(
      1,
      '/v1/trilhas/trilha-1/adesao',
      {
        method: 'POST',
      },
    )
    expect(requisitar).toHaveBeenNthCalledWith(
      2,
      '/v1/trilhas/trilha-1/tarefas/tarefa-1/acompanhamento',
      { method: 'PUT', body: JSON.stringify({ situacao: 'CONCLUIDA' }) },
    )
  })
})
