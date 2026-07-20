# Como usar o Swagger e a OpenAPI

## Enderecos locais

Com PostgreSQL e backend em execucao:

- Swagger UI: `http://localhost:8080/swagger-ui.html`;
- rota final da interface: `http://localhost:8080/swagger-ui/index.html`;
- documento OpenAPI JSON: `http://localhost:8080/v3/api-docs`.

As três rotas sao publicas no perfil local. Os endpoints de negocio continuam
protegidos por sessao e CSRF.

## Cadastro, login e sessao

O exemplo abaixo guarda cookies em um arquivo temporario. O token CSRF deve ser
obtido antes de cada sequencia de operacoes mutaveis.

```bash
curl -sS -c /tmp/trilha-cookies.txt \
  http://localhost:8080/api/v1/autenticacao/csrf
```

A resposta informa `token` e `cabecalho`. Copie o valor de `token`:

```json
{
  "token": "<token-csrf>",
  "cabecalho": "X-XSRF-TOKEN",
  "parametro": "_csrf"
}
```

Cadastro:

```bash
curl -i -b /tmp/trilha-cookies.txt -c /tmp/trilha-cookies.txt \
  -H 'Content-Type: application/json' \
  -H 'X-XSRF-TOKEN: <token-csrf>' \
  -d '{"nome":"Pessoa","email":"pessoa@example.com","senha":"senha-segura-123"}' \
  http://localhost:8080/api/v1/autenticacao/cadastro
```

Obtenha um token novo e faça login:

```bash
curl -sS -b /tmp/trilha-cookies.txt -c /tmp/trilha-cookies.txt \
  http://localhost:8080/api/v1/autenticacao/csrf

curl -i -b /tmp/trilha-cookies.txt -c /tmp/trilha-cookies.txt \
  -H 'Content-Type: application/json' \
  -H 'X-XSRF-TOKEN: <novo-token-csrf>' \
  -d '{"email":"pessoa@example.com","senha":"senha-segura-123"}' \
  http://localhost:8080/api/v1/autenticacao/login
```

Confirme a sessao:

```bash
curl -i -b /tmp/trilha-cookies.txt \
  http://localhost:8080/api/v1/autenticacao/sessao
```

## Teste de endpoint autenticado

Leituras exigem o cookie de sessao:

```bash
curl -i -b /tmp/trilha-cookies.txt \
  http://localhost:8080/api/v1/dashboard
```

Escritas exigem o cookie e o token CSRF:

```bash
curl -i -b /tmp/trilha-cookies.txt \
  -H 'Content-Type: application/json' \
  -H 'X-XSRF-TOKEN: <token-csrf-atual>' \
  -d '{"nome":"Direito Constitucional","descricao":null,"cor":"#12355B"}' \
  http://localhost:8080/api/v1/materias
```

Ao terminar:

```bash
curl -i -b /tmp/trilha-cookies.txt \
  -H 'X-XSRF-TOKEN: <token-csrf-atual>' \
  -X POST http://localhost:8080/api/v1/autenticacao/logout
rm -f /tmp/trilha-cookies.txt
```

## Planejamento Manual

O grupo **Planejamento** da OpenAPI documenta o fluxo completo nas rotas:

- `POST` e `GET /api/v1/planos-semanais`;
- `PUT /api/v1/planos-semanais/{identificador}/disponibilidades`;
- criacao, edicao, exclusao de rascunho e ordenacao de blocos;
- ativacao, encerramento e cancelamento do plano;
- `GET /api/v1/planejamento/hoje` e
  `GET /api/v1/planejamento/execucao-em-andamento`;
- inicio, consulta, conclusao e interrupcao de execucao;
- consulta de topicos e registro da execucao no Historico;
- reagendamento e cancelamento de bloco;
- correcao de execucao.

As leituras exigem `JSESSIONID`. Todas as operacoes mutaveis exigem tambem o
token no cabecalho `X-XSRF-TOKEN`. Os identificadores de plano, bloco e execucao
nao substituem a sessao: um recurso de outro usuario responde como nao
encontrado.

## Geracao Deterministica

As quatro operacoes da geracao pertencem ao grupo **Planejamento**:

- `GET /api/v1/planos-semanais/{identificador}/materias-para-geracao`;
- `PUT /api/v1/planos-semanais/{identificador}/prioridades-de-materias`;
- `POST /api/v1/planos-semanais/{identificador}/geracao-deterministica/previa`;
- `POST /api/v1/planos-semanais/{identificador}/geracao-deterministica`.

Prioridades:

```json
{
  "prioridades": [
    {
      "identificadorDaMateria": "UUID",
      "prioridade": "ALTA"
    }
  ]
}
```

Previa:

```json
{
  "duracaoPadraoDoBlocoPrincipalEmMinutos": 50,
  "duracaoDoBlocoDeRevisaoEmMinutos": 20
}
```

Aplicacao:

```json
{
  "duracaoPadraoDoBlocoPrincipalEmMinutos": 50,
  "duracaoDoBlocoDeRevisaoEmMinutos": 20,
  "substituirBlocosGerados": false
}
```

Use o cookie `JSESSIONID` em todas as quatro rotas. Para `PUT` e `POST`, obtenha
o token em `GET /api/v1/autenticacao/csrf` e envie o valor no cabecalho
`X-XSRF-TOKEN`. Se a aplicacao responder `409` porque existe uma geracao
anterior, repita a aplicacao com `substituirBlocosGerados=true` somente depois
da confirmacao do usuario. A regeneracao substitui apenas blocos puramente
gerados; blocos manuais e gerados ja ajustados permanecem. Prioridades, previa
e aplicacao sao bloqueadas quando o plano nao esta em rascunho.

## Limitacoes da interface Swagger

O navegador mantém `JSESSIONID`, mas a interface nao automatiza todo o ciclo de
obter e renovar CSRF. Para operacoes mutaveis, consulte
`GET /api/v1/autenticacao/csrf` e use **Authorize** para informar
`X-XSRF-TOKEN`. Se a sessao for recriada no login, obtenha outro token.

O esquema `sessao` apenas documenta o cookie: nao cole uma senha ou cookie real
na especificacao. Para um fluxo longo e reproduzivel, prefira `curl` com o
arquivo de cookies temporario.

## Producao

Com o perfil `producao`, OpenAPI e Swagger UI ficam desabilitados por padrao:

```text
OPENAPI_HABILITADA=false
SWAGGER_HABILITADO=false
```

Cada recurso pode ser habilitado explicitamente por ambiente. Publicar a
interface em producao exige uma decisao operacional consciente; nenhum deploy
faz parte desta reconstrucao.

## Validacao automatizada

`DocumentacaoDaApiIntegracaoTest` inicia PostgreSQL vazio, aplica as migrations
e confirma documento, grupos, caminhos do Planejamento, schemas de erro,
sessao, CSRF e as duas rotas da interface Swagger.
