import { afterEach, describe, expect, it, vi } from 'vitest'

import { assistenteTelegramEstaHabilitado } from './funcionalidades'

describe('funcionalidades', () => {
  afterEach(() => vi.unstubAllEnvs())

  it('mantem o assistente desabilitado sem liberacao explicita', () => {
    vi.stubEnv('VITE_ASSISTENTE_TELEGRAM_HABILITADO', '')

    expect(assistenteTelegramEstaHabilitado()).toBe(false)
  })

  it('libera o assistente somente com a flag verdadeira', () => {
    vi.stubEnv('VITE_ASSISTENTE_TELEGRAM_HABILITADO', 'true')

    expect(assistenteTelegramEstaHabilitado()).toBe(true)
  })
})
