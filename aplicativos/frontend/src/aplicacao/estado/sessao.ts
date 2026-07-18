import { defineStore } from 'pinia'

import { requisitar } from '@/compartilhado/api/clienteHttp'

type Usuario = { identificador: string; nome: string; email: string }
type Sessao = { autenticada: boolean; usuario: Usuario }

export const usarSessao = defineStore('sessao', {
  state: () => ({ usuario: undefined as Usuario | undefined }),
  getters: { autenticada: (estado) => Boolean(estado.usuario) },
  actions: {
    async atualizar() {
      this.usuario = (
        await requisitar<Sessao>('/v1/autenticacao/sessao')
      ).usuario
    },
    limpar() {
      this.usuario = undefined
    },
  },
})
