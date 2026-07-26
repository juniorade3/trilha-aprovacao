<script setup lang="ts">
import type { PreviaDaImportacaoDeEdital } from './apiDeImportacaoDeEdital'

defineProps<{
  previa: PreviaDaImportacaoDeEdital
}>()

function formatarRotulo(valor: string) {
  const texto = valor.replace(/_/g, ' ').toLocaleLowerCase('pt-BR')
  return texto.charAt(0).toLocaleUpperCase('pt-BR') + texto.slice(1)
}

function formatarDataHora(valor?: string | null) {
  if (!valor) return ''
  return new Intl.DateTimeFormat('pt-BR', {
    dateStyle: 'short',
    timeStyle: 'short',
  }).format(new Date(valor))
}
</script>

<template>
  <section
    class="card previa-da-importacao"
    aria-labelledby="titulo-previa-importacao"
  >
    <header>
      <p class="sobretitulo-da-pagina">Revisão antes da confirmação</p>
      <h2 id="titulo-previa-importacao">Prévia da importação</h2>
      <p>{{ previa.resumo }}</p>
    </header>

    <p class="alert alert-info" role="status">
      <strong>Nada foi alterado.</strong>
      Esta proposta será aplicada somente após confirmação válida e verificação
      da versão extraída.
    </p>

    <dl class="contagens-da-previa">
      <div v-for="(quantidade, nome) in previa.contagens" :key="nome">
        <dt>{{ formatarRotulo(String(nome)) }}</dt>
        <dd>{{ quantidade }}</dd>
      </div>
    </dl>

    <div class="colunas-da-previa">
      <section aria-labelledby="titulo-itens-a-criar">
        <h3 id="titulo-itens-a-criar">Será criado</h3>
        <p v-if="previa.itensACriar.length === 0">Nenhum item novo.</p>
        <ul v-else>
          <li
            v-for="(item, indice) in previa.itensACriar"
            :key="`${item.tipo}-${item.nome}-${indice}`"
          >
            <strong>{{ item.nome }}</strong>
            <span>{{ formatarRotulo(item.tipo) }}</span>
            <small v-if="item.contexto">{{ item.contexto }}</small>
          </li>
        </ul>
      </section>
      <section aria-labelledby="titulo-itens-a-reutilizar">
        <h3 id="titulo-itens-a-reutilizar">Será reutilizado</h3>
        <p v-if="previa.itensAReutilizar.length === 0">Nenhuma reutilização.</p>
        <ul v-else>
          <li
            v-for="(item, indice) in previa.itensAReutilizar"
            :key="`${item.tipo}-${item.nome}-${indice}`"
          >
            <strong>{{ item.nome }}</strong>
            <span>{{ formatarRotulo(item.tipo) }}</span>
            <small v-if="item.contexto">{{ item.contexto }}</small>
          </li>
        </ul>
      </section>
    </div>

    <section
      v-if="previa.conflitos.length"
      aria-labelledby="titulo-conflitos-previa"
    >
      <h3 id="titulo-conflitos-previa">Conflitos</h3>
      <ul class="lista-de-pendencias">
        <li
          v-for="(conflito, indice) in previa.conflitos"
          :key="`${conflito.codigo}-${indice}`"
        >
          <strong>{{ formatarRotulo(conflito.severidade) }}</strong>
          {{ conflito.mensagem }}
        </li>
      </ul>
    </section>

    <div class="colunas-da-previa">
      <section aria-labelledby="titulo-incertezas-previa">
        <h3 id="titulo-incertezas-previa">Incertezas</h3>
        <p v-if="previa.incertezas.length === 0">Nenhuma registrada.</p>
        <ul v-else>
          <li v-for="incerteza in previa.incertezas" :key="incerteza">
            {{ incerteza }}
          </li>
        </ul>
      </section>
      <section aria-labelledby="titulo-ausencias-previa">
        <h3 id="titulo-ausencias-previa">Campos ausentes</h3>
        <p v-if="previa.camposAusentes.length === 0">
          Nenhum obrigatório ausente.
        </p>
        <ul v-else>
          <li v-for="campo in previa.camposAusentes" :key="campo">
            {{ campo }}
          </li>
        </ul>
      </section>
    </div>

    <section
      v-if="previa.identificadorDaOperacao"
      class="confirmacao-da-importacao"
      aria-labelledby="titulo-confirmacao-importacao"
    >
      <p class="sobretitulo-da-pagina">Operação preparada</p>
      <h3 id="titulo-confirmacao-importacao">
        Confirme pelo Telegram para aplicar
      </h3>
      <p>
        Confirmação fica vinculada ao usuário, chat, bot, sessão e versão desta
        proposta. Esta página acompanhará o resultado.
      </p>
      <p
        v-if="previa.confirmacaoReforcada || previa.exigeConfirmacaoReforcada"
        class="alert alert-warning"
      >
        Confirmação reforçada ativa.
        <template v-if="previa.etapaAtualDaConfirmacao">
          Etapa {{ previa.etapaAtualDaConfirmacao }} de 2.
        </template>
        Segundo código terá curta duração.
      </p>
      <output v-if="previa.fraseDeConfirmacao" class="frase-de-confirmacao">
        {{ previa.fraseDeConfirmacao }}
      </output>
      <small v-if="previa.expiraEm">
        Expira em {{ formatarDataHora(previa.expiraEm) }}.
      </small>
    </section>
  </section>
</template>

<style scoped lang="scss">
.previa-da-importacao {
  padding: clamp(1.25rem, 3vw, 2rem);
  display: grid;
  gap: 1.5rem;
}

.contagens-da-previa {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(8rem, 1fr));
  gap: 0.75rem;
  margin: 0;
}

.contagens-da-previa div {
  border: 1px solid var(--bs-border-color);
  border-radius: 0.75rem;
  padding: 0.75rem;
}

.contagens-da-previa dd {
  font-size: 1.5rem;
  font-weight: 700;
  margin: 0;
}

.colunas-da-previa {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 1rem;
}

.colunas-da-previa > section {
  border: 1px solid var(--bs-border-color);
  border-radius: 0.75rem;
  padding: 1rem;
}

.colunas-da-previa li {
  margin-bottom: 0.5rem;
}

.colunas-da-previa li span,
.colunas-da-previa li small,
.colunas-da-previa li strong {
  display: block;
}

.confirmacao-da-importacao {
  border: 1px solid var(--bs-primary);
  border-radius: 0.75rem;
  padding: 1rem;
}

.frase-de-confirmacao {
  display: block;
  background: var(--bs-light);
  border-radius: 0.5rem;
  font-family: monospace;
  font-weight: 700;
  padding: 0.75rem;
  margin-bottom: 0.5rem;
  overflow-wrap: anywhere;
}

@media (max-width: 767px) {
  .colunas-da-previa {
    grid-template-columns: 1fr;
  }
}
</style>
