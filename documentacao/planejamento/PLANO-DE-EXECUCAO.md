# Plano de execucao da reconstrucao

## Objetivo

Entregar um monorepo novo do Trilha da Aprovacao, com Java 21 e Spring Boot 4.1 no backend, Vue 3 e Vite 8.1 no frontend, PostgreSQL e Flyway, autenticacao por sessao, dados isolados por usuario e validacao funcional completa.

## Decisao de partida obrigatoria

A especificacao exige `~/Aplicativos/trilha-aprovacao` vazio ou inexistente. O repositorio de trabalho atual contem uma implementacao parcial anterior, conforme o README. Portanto, a execucao nao deve assumir que pode reutilizar, apagar ou sobrescrever esse conteudo. O Sprint 0 deve registrar uma destas decisoes:

1. criar a reconstrucao no diretorio preferencial vazio;
2. receber autorizacao expressa para outro diretorio vazio;
3. parar por conflito de diretorio.

## Estrategia

Cada sprint entrega uma fatia vertical verificavel, mas as migracoes, contratos de erro, seguranca e testes de isolamento devem evoluir junto com cada capacidade. O frontend nunca deve simular dados que ainda nao existam na API. Os commits locais acontecem somente ao final de uma porta de qualidade verde.

## Portas globais de qualidade

- Java 21, Node LTS, Docker Compose, Git, curl e ferramentas locais verificados sem instalacao global;
- banco iniciado por Compose, com healthcheck verde e Flyway validando migrations;
- `ddl-auto=validate`, UUIDs, timestamps com fuso, FKs, indices e restricoes no banco;
- API usa DTOs, resposta de erro padronizada e identificador de correlacao;
- toda consulta de negocio deriva o usuario da sessao, sem `identificadorDoUsuario` livre;
- frontend usa `fetch`, `credentials: include`, CSRF, Pinia apenas para estado compartilhado e URL relativa `/api`;
- cada sprint executa os testes afetados, formatacao, tipos e build aplicaveis;
- nenhum segredo, volume, log, artefato de build ou dependencia instalada e versionado.

## Sequencia e resultado

| Sprint | Resultado liberado | Dependencia |
| --- | --- | --- |
| 0 | Ambiente e diretorio aprovados ou conflito registrado | nenhuma |
| 1 | Monorepo inicial, banco e health check | 0 |
| 2 | Conta, login, logout, CSRF e isolamento base | 1 |
| 3 | Materias e topicos pessoais | 2 |
| 4 | Concursos, editais, cargos, provas e grupos | 3 |
| 5 | Itens oficiais e mapeamento para topicos | 4 |
| 6 | Materiais, cobertura e registros de estudo | 5 |
| 7 | Dashboard objetivo do concurso ativo | 6 |
| 8 | Swagger, CI, documentacao e aceite integral | 7 |

O detalhamento de cada porta esta em `SPRINTS.md`; dependencias tecnicas e por dominio estao em `MATRIZ-DE-DEPENDENCIAS.md`; comandos e cenarios de aceite estao em `VALIDACAO-E-ACEITE.md`.
