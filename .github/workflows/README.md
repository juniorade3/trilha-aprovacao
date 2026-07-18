# Fluxos de CI

O arquivo `ci.yml` executa verificacoes em `push` para `main` e em toda pull
request. Os trabalhos sao independentes:

- estrutura valida o Docker Compose e espacos em branco no diff;
- backend usa Java 21, Maven Wrapper, PostgreSQL real por Testcontainers,
  migrations Flyway, testes funcionais e testes de arquitetura;
- frontend usa Node 24, instalacao reproduzivel com `npm ci`, tipos, lint,
  Vitest, build, formatacao e auditoria de dependencias.

O workflow possui somente permissao de leitura, nao recebe segredos e nao
realiza deploy.
