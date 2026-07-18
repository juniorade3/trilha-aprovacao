// @vitest-environment jsdom

import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import ArvoreDeTopicos from './ArvoreDeTopicos.vue'
import type { Topico } from './apiDeConteudos'

const topicos: Topico[] = [
  {
    identificador: 'raiz',
    identificadorDaMateria: 'materia',
    nome: 'Constituicao',
    ordem: 1,
    arquivado: false,
    criadoEm: '2026-07-18T10:00:00Z',
    atualizadoEm: '2026-07-18T10:00:00Z',
    versao: 0,
  },
  {
    identificador: 'filho',
    identificadorDaMateria: 'materia',
    identificadorDoTopicoPai: 'raiz',
    nome: 'Direitos fundamentais',
    ordem: 1,
    arquivado: true,
    criadoEm: '2026-07-18T10:00:00Z',
    atualizadoEm: '2026-07-18T10:00:00Z',
    versao: 0,
  },
]

describe('ArvoreDeTopicos', () => {
  it('apresenta a hierarquia e encaminha as acoes do topico', async () => {
    const arvore = mount(ArvoreDeTopicos, { props: { topicos } })

    expect(arvore.text()).toContain('Constituicao')
    expect(arvore.text()).toContain('Direitos fundamentais')
    expect(arvore.findAll('ul')).toHaveLength(2)
    expect(
      arvore
        .get('button[aria-label="Editar Direitos fundamentais"]')
        .attributes('disabled'),
    ).toBeDefined()

    await arvore
      .get('button[aria-label="Arquivar Constituicao"]')
      .trigger('click')

    expect(arvore.emitted('arquivar')?.[0]).toEqual([topicos[0]])
  })
})
