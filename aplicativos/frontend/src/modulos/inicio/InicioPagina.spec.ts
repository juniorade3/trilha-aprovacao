import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import InicioPagina from './InicioPagina.vue'

describe('InicioPagina', () => {
  it('apresenta a identidade da aplicacao', () => {
    const pagina = mount(InicioPagina)

    expect(pagina.get('h1').text()).toBe('Trilha da Aprovacao')
  })
})
