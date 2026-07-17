# Instrucoes para agentes

## Regras invariaveis

- Todo identificador proprio e em portugues, sem acentos.
- O backend e um monolito modular; o dominio nao depende de Spring, JPA, HTTP ou PostgreSQL.
- Entidades JPA nunca sao respostas da API. Use DTOs e casos de uso.
- O usuario autenticado define o escopo de todas as operacoes de negocio; nao aceite identificador de usuario livre.
- Use PostgreSQL e Flyway. `ddl-auto=create` e `ddl-auto=update` sao proibidos.
- O frontend usa Vue, `fetch`, Bootstrap, Sass e Pinia apenas para estado compartilhado.
- Nao versionar segredos, volumes, logs, `node_modules`, `target` ou `dist`.

## Processo

1. Trabalhar uma sprint por vez e executar a porta de qualidade dela.
2. Nao iniciar a proxima sprint sem autorizacao explicita do usuario.
3. Antes de cada commit, rodar as verificacoes afetadas e `git diff --check`.
4. Nunca apagar nem sobrescrever conteudo fora deste repositorio sem autorizacao expressa.
