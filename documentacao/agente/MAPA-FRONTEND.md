# Mapa do frontend

## Entrada

- `src/main.ts`
- `src/Aplicacao.vue`
- `src/aplicacao/roteamento/index.ts`

## Infraestrutura de aplicação

```text
src/aplicacao/
├── configuracao/
├── estado/
├── layouts/
└── roteamento/
```

## Módulos de interface

```text
src/modulos/
├── autenticacao/
├── concursos/
├── estudos/
├── inicio/
├── integracoes/
├── materias/
└── planejamento/
```

## Rotas confirmadas

- `/dashboard`
- `/concursos`
- `/concursos/novo`
- `/concursos/:identificador`
- `/materias`
- `/materiais`
- `/materiais/:identificador`
- `/estudos`
- `/estudos/novo`
- `/planejamento/hoje`
- `/planejamento/semana`
- `/planejamento/prioridades`
- `/integracoes/telegram`
- `/login`
- `/cadastro`

## Sessão

A aplicação:

- usa Pinia;
- tenta restaurar a sessão em rota protegida;
- redireciona para login quando a sessão expira;
- preserva a rota de retorno;
- usa sessão e CSRF no backend.

## Tema e sistema visual

- a interface usa exclusivamente o tema escuro, fixado no HTML antes da
  inicialização do Vue;
- não existe seletor de tema nem variação clara baseada no sistema operacional;
- `src/compartilhado/estilos/principal.scss` é a entrada do sistema visual;
- tokens, base do Bootstrap, shell, componentes e módulos permanecem separados
  dentro de `src/compartilhado/estilos/`;
- cores fixas pertencem aos tokens semânticos; cores escolhidas para matérias
  servem apenas como marcadores decorativos;
- texto comum deve alcançar contraste WCAG AA e ações de toque devem manter
  área mínima de 44 por 44 pixels.

## Integração Telegram

A rota é protegida por feature flag. Não torne a página acessível quando o backend estiver com a automação desativada.

## Leitura mínima por tarefa

### Nova página

- roteador;
- layout usado;
- página semelhante;
- cliente HTTP;
- testes do módulo.

### Alterar formulário

- componente;
- tipos;
- cliente HTTP;
- contrato backend;
- tratamento de erros;
- teste.

### Alterar jornada

- rotas relacionadas;
- estado compartilhado;
- páginas de origem e destino;
- estados vazio, carregando, sucesso e erro.
