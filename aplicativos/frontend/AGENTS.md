# AGENTS.md — Frontend

## Escopo

Estas instruções se aplicam a `aplicativos/frontend/`.

## Tecnologias

- Vue 3;
- TypeScript;
- Vue Router;
- Pinia;
- Vite;
- Vitest;
- Bootstrap e Bootstrap Icons;
- Sass;
- ESLint e Prettier.

## Estrutura confirmada

```text
src/
├── Aplicacao.vue
├── main.ts
├── aplicacao/
│   ├── configuracao/
│   ├── estado/
│   ├── layouts/
│   └── roteamento/
├── compartilhado/
│   └── estilos/
└── modulos/
    ├── autenticacao/
    ├── concursos/
    ├── estudos/
    ├── inicio/
    ├── integracoes/
    ├── materias/
    └── planejamento/
```

## Roteamento

O roteador principal está em:

```text
src/aplicacao/roteamento/index.ts
```

Antes de criar uma nova página, verifique se a tarefa cabe em um módulo existente.

## Regras

- páginas ficam no módulo funcional;
- estado global somente quando compartilhado;
- chamadas HTTP reutilizam o cliente compartilhado existente;
- não duplicar regras do backend;
- erros de domínio devem ser exibidos de forma compreensível;
- preservar sessão, CSRF e tratamento de sessão expirada;
- funcionalidades experimentais devem respeitar feature flag;
- não expor identificadores técnicos quando um rótulo for suficiente;
- componentes grandes devem ser decompostos por responsabilidade, não por abstração genérica.

## Fluxos existentes

- dashboard;
- concursos;
- matérias;
- materiais;
- registros de estudo;
- planejamento de hoje;
- planejamento semanal;
- priorização;
- integração Telegram;
- login e cadastro.

## Validação

```bash
npm run verificar-tipos
npm run lint
npm run test
npm run build
npm run verificar-formatacao
npm audit
```

Ou, pela raiz:

```bash
make verificar-frontend
```

## Critérios de interface

- jornada clara e humana;
- hierarquia visual explícita;
- estado vazio com orientação;
- ações destrutivas ou de alto impacto com confirmação;
- carregamento, erro e sucesso visíveis;
- acessibilidade por teclado;
- layout utilizável em tela móvel, tablet e desktop;
- nenhum comportamento crítico depende apenas de cor.
