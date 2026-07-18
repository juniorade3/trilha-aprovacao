# Operacao local

## Inicio

```bash
cp .env.example .env
make infra-subir
make backend-executar
```

Em outro terminal:

```bash
make frontend-executar
```

Servicos esperados:

- frontend: `http://localhost:5173`;
- backend: `http://localhost:8080`;
- health: `http://localhost:8080/actuator/health`;
- Swagger: `http://localhost:8080/swagger-ui.html`;
- OpenAPI: `http://localhost:8080/v3/api-docs`;
- PostgreSQL: porta local definida no `.env`, por padrao `5432`.

O perfil local nasce sem dados. Cadastre uma conta pela interface ou siga o
[guia do Swagger](../api/COMO-USAR-O-SWAGGER.md).

## Diagnostico

```bash
docker compose ps
docker compose logs postgresql
curl -fsS http://localhost:8080/actuator/health
curl -fsS http://localhost:8080/v3/api-docs
```

O backend deve registrar que as cinco migrations Flyway foram validadas e que
o schema esta na versao `v5`. Hibernate usa `ddl-auto=validate`; uma divergencia
entre entidades e migrations impede o inicio.

O proxy do Vite usa apenas `VITE_ALVO_API`; o codigo do frontend sempre chama
enderecos relativos sob `/api`.

## Porta de qualidade

```bash
docker compose config
make verificar
git diff --check
```

`make verificar` executa Maven `verify`, tipos, lint, Vitest, build, formatacao
e `npm audit`. Os testes de integracao exigem Docker porque usam PostgreSQL real
via Testcontainers.

## Encerramento

Interrompa backend e frontend com `Ctrl+C` nos respectivos terminais e então:

```bash
make infra-parar
docker compose ps
```

O ultimo comando nao deve apresentar containers do projeto em execucao.

## Producao

O perfil `producao` marca o cookie de sessao como seguro e desabilita OpenAPI e
Swagger por padrao. Implantacao, secrets de ambiente, proxy reverso, TLS,
observabilidade externa e deploy estao fora do escopo atual.
