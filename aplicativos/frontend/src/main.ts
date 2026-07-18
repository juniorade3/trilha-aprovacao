import 'bootstrap-icons/font/bootstrap-icons.css'
import 'bootstrap/dist/css/bootstrap.min.css'
import '@/compartilhado/estilos/principal.scss'

import { createApp } from 'vue'
import { createPinia } from 'pinia'

import Aplicacao from './Aplicacao.vue'
import roteador from './aplicacao/roteamento'
import { usarSessao } from './aplicacao/estado/sessao'

const pinia = createPinia()

window.addEventListener('sessao-expirada', () => {
  usarSessao(pinia).limpar()
  const rotaAtual = roteador.currentRoute.value
  if (!['login', 'cadastro'].includes(String(rotaAtual.name))) {
    void roteador.replace({
      name: 'login',
      query: {
        redirecionar: rotaAtual.fullPath,
        sessao: 'expirada',
      },
    })
  }
})

createApp(Aplicacao).use(pinia).use(roteador).mount('#aplicacao')
