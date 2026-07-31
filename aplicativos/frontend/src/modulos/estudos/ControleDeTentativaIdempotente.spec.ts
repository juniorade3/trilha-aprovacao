import { describe, expect, it, vi } from 'vitest'

import { ControleDeTentativaIdempotente } from './ControleDeTentativaIdempotente'

describe('ControleDeTentativaIdempotente', () => {
  it('reutiliza a chave no retry do mesmo payload e troca após edição', () => {
    const gerar = vi
      .fn()
      .mockReturnValueOnce('chave-1')
      .mockReturnValueOnce('chave-2')
    const controle = new ControleDeTentativaIdempotente(gerar)
    const dados = { topico: 'topico-1', duracao: 50 }

    expect(controle.chavePara(dados)).toBe('chave-1')
    expect(controle.chavePara({ ...dados })).toBe('chave-1')
    expect(controle.chavePara({ ...dados, duracao: 60 })).toBe('chave-2')
    expect(gerar).toHaveBeenCalledTimes(2)
  })

  it('inicia outra tentativa depois da conclusão', () => {
    const gerar = vi
      .fn()
      .mockReturnValueOnce('chave-1')
      .mockReturnValueOnce('chave-2')
    const controle = new ControleDeTentativaIdempotente(gerar)
    const dados = { topico: 'topico-1' }

    expect(controle.chavePara(dados)).toBe('chave-1')
    controle.concluir()

    expect(controle.chavePara(dados)).toBe('chave-2')
  })
})
