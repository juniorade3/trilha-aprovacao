# Como usar o Swagger

Este documento sera validado no Sprint 8, depois da escolha registrada de `springdoc-openapi` 3.x compativel com Spring Boot 4.1.x.

No perfil local, a API devera expor `/v3/api-docs` e a rota efetiva do Swagger UI confirmada na versao instalada, com compatibilidade esperada para `/swagger-ui.html` e `/swagger-ui/index.html`. Em producao, a UI permanece desabilitada por padrao; a publicacao do documento OpenAPI sera configuravel por ambiente.

Fluxo de teste autenticado:

1. criar conta em `POST /api/v1/autenticacao/cadastro` ou entrar em `POST /api/v1/autenticacao/login`;
2. confirmar a sessao por `GET /api/v1/autenticacao/sessao`;
3. obter token em `GET /api/v1/autenticacao/csrf`;
4. enviar o token CSRF no cabecalho configurado pelo backend nas requisicoes mutaveis, preservando o cookie de sessao;
5. testar os endpoints de negocio.

O Swagger pode ter limitacoes para manter cookie e CSRF entre chamadas. A documentacao final deve explicar o cabecalho exato, demonstrar o fluxo em ambiente local e conter teste automatizado de `/v3/api-docs` com as tags principais.
