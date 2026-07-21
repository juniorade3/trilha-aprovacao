# Aceite da Sprint 01 - lacunas e ranking

## Base

- `main`: `6479cd89eb65f8de532d0c9e7ee128db89e60189`;
- ultima migration: V15;
- branch: `feature/priorizacao-01-lacunas-ranking`.

## Resultados executados

### Baseline antes da implementacao

- `make verificar`: aprovado;
- backend: 121 testes aprovados;
- frontend: 104 testes aprovados;
- verificacao de tipos, lint, build e formatacao do frontend: aprovados;
- `npm audit`: nenhuma vulnerabilidade encontrada;

### Porta de qualidade final

- `make verificar`: aprovado;
- backend: 129 testes aprovados;
- frontend: 111 testes aprovados em 24 arquivos;
- verificacao de tipos, lint, build e formatacao do frontend: aprovados;
- `npm audit`: nenhuma vulnerabilidade encontrada;
- PostgreSQL vazio: migrations V1 a V15 aplicadas pelos testes de integracao;
- `docker compose config --quiet`: aprovado;
- `git diff --check`: aprovado.

### Validacao manual

- ambiente isolado com PostgreSQL 17 vazio: V1 a V15 aplicadas e backend e
  frontend iniciados em portas alternativas;
- ranking real: 5 itens oficiais, 1 sem mapeamento, 4 topicos, 2 lacunas,
  1 fraqueza e 1 consolidado, com classificacoes, acoes e ordem esperadas;
- determinismo: duas respostas do usuario A foram identicas byte a byte;
- consulta sem escrita: contagens de estudos, evidencias e mapeamentos
  permaneceram inalteradas;
- isolamento A/B: cada usuario recebeu apenas o proprio contexto; filtros
  cruzados retornaram 404 sem expor dados;
- contratos: 400 sem data, 401 sem sessao e 422 sem contexto oficial;
- Swagger e OpenAPI: interface real carregada, rota, parametros, seguranca,
  respostas e schemas conferidos;
- 390 x 844, 768 x 1024 e 1280 x 800: sem rolagem horizontal e com filtros,
  resumo, grupos e cartoes responsivos;
- teclado: ordem de Tab, Enter para recalcular e Escape sem dialogo conferidos;
- erro de rede: repeticao recuperou os dados e restaurou foco no botao de
  atualizacao;
- sessao expirada: redirecionamento preservado, aviso apresentado e foco no
  campo de e-mail;
- estados de contexto incompleto e lista vazia conferidos em interface real.

Capturas temporarias conferidas em `/tmp/trilha-sprint1-prioridades-390x844.png`,
`/tmp/trilha-sprint1-prioridades-768x1024.png` e
`/tmp/trilha-sprint1-prioridades-1280x800.png`, alem das capturas de cartoes,
contexto incompleto e estado vazio. O ambiente temporario foi encerrado.
