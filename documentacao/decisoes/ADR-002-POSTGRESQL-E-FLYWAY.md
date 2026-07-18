# ADR-002 — PostgreSQL e Flyway

## Contexto

O modelo exige integridade referencial, restricoes de unicidade, auditoria, concorrencia e consultas agregadas confiaveis.

## Decisao

Usar PostgreSQL em Compose e Flyway para todas as alteracoes de esquema. Hibernate usa `ddl-auto=validate` no desenvolvimento.

## Consequencias

Migrations versionadas tornam o banco reproduzivel e testavel em base vazia. UUIDs, FKs, indices, timestamps com fuso e colunas de versao serao definidos no esquema; criacao/atualizacao automatica de tabelas e cascatas destrutivas ficam proibidas.
