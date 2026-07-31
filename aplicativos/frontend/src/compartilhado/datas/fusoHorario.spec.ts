import { describe, expect, it } from 'vitest'

import {
  adicionarDiasADataCivil,
  dataCivilEhSegundaFeira,
  dataCivilEmSaoPaulo,
  dataHoraCivilEmSaoPaulo,
  inicioDaSemanaCivil,
  instanteDeDataHoraCivilEmSaoPaulo,
} from './fusoHorario'

describe('fuso horário da aplicação', () => {
  it('mantém a data civil de São Paulo na virada do dia UTC', () => {
    const instante = new Date('2026-07-22T02:59:00Z')

    expect(dataCivilEmSaoPaulo(instante)).toBe('2026-07-21')
    expect(dataHoraCivilEmSaoPaulo(instante)).toBe('2026-07-21T23:59')
    expect(instanteDeDataHoraCivilEmSaoPaulo('2026-07-21T23:59')).toBe(
      '2026-07-22T02:59:00.000Z',
    )
  })

  it('trata meia-noite local e atravessa mês e ano sem depender do navegador', () => {
    expect(dataHoraCivilEmSaoPaulo(new Date('2026-07-22T03:00:00Z'))).toBe(
      '2026-07-22T00:00',
    )
    expect(adicionarDiasADataCivil('2026-12-31', 1)).toBe('2027-01-01')
    expect(inicioDaSemanaCivil('2027-01-01')).toBe('2026-12-28')
    expect(dataCivilEhSegundaFeira('2026-12-28')).toBe(true)
  })

  it('recusa datas civis inexistentes em vez de normalizá-las silenciosamente', () => {
    expect(() => adicionarDiasADataCivil('2026-02-30', 1)).toThrow(
      'Data inválida.',
    )
    expect(() => instanteDeDataHoraCivilEmSaoPaulo('2026-02-30T10:00')).toThrow(
      'Informe uma data e horário válidos.',
    )
  })
})
