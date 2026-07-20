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

O backend deve registrar que as dez migrations Flyway foram validadas e que
o schema esta na versao `v10`. Hibernate usa `ddl-auto=validate`; uma divergencia
entre entidades e migrations impede o inicio.

O proxy do Vite usa apenas `VITE_ALVO_API`; o codigo do frontend sempre chama
enderecos relativos sob `/api`.

## Operacao do Planejamento Manual

Depois de entrar, use a entrada **Planejamento**. A rota principal abre **Hoje**
e a navegacao secundaria permite alternar entre **Hoje**, **Semana** e
**Historico**.

Fluxo operacional:

1. abra **Semana** e crie o rascunho, caso ainda nao exista;
2. informe os minutos disponiveis nos sete dias;
3. adicione blocos livres ou vinculados a materia e topico;
4. ajuste a ordem e ative o plano depois de corrigir eventuais excessos;
5. em **Hoje**, inicie, conclua ou interrompa um bloco;
6. consulte o estudo criado no **Historico**;
7. enquanto o plano estiver ativo, edite, reagende ou cancele apenas blocos
   ainda planejados;
8. encerre ou cancele o plano pela **Semana**.

Uma execucao aberta e recuperada ao recarregar **Hoje**. Em conflito de versao,
a interface apresenta a acao **Recarregar dados**. Planos encerrados ou
cancelados permanecem somente para leitura; execucoes e estudos nao sao
apagados. Todas as operacoes usam o usuario da sessao atual.

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
