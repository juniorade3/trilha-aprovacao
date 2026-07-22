# Aceite da Sprint 03 - selecao automatica e integracao semanal

## Base

- `main`: `2f64059cbc56594273960376714324eb8bafbfab`;
- ultima migration: V15;
- branch: `feature/priorizacao-03-selecao-automatica`.

## Resultados executados

### Baseline antes da implementacao

- CI da Sprint 02: aprovado;
- `make verificar` final da Sprint 02: aprovado;
- backend: 139 testes aprovados;
- frontend: 125 testes aprovados.

### Porta de qualidade da Sprint 03

- `docker compose config --quiet`: aprovado;
- `make verificar`: aprovado;
- backend: 151 testes aprovados, sem falhas ou erros;
- frontend: 131 testes aprovados em 26 arquivos;
- verificacao de tipos, lint, build e formatacao do frontend: aprovados;
- `npm audit`: nenhuma vulnerabilidade encontrada;
- PostgreSQL vazio: migrations V1 a V15 aplicadas pelos testes de integracao;
- testes focados finais da integracao de planejamento e do gerador: 39
  aprovados;
- testes focados de arquitetura: cinco aprovados;
- testes focados de frontend: 38 aprovados; depois da correcao de foco, os 22
  testes da pagina Semana foram executados novamente e aprovados;
- concorrencia de aplicacao: uma requisicao concluiu com `200` e a concorrente
  recebeu `409`;
- `git diff --check`: aprovado.

## Validacao manual

- ambiente isolado com PostgreSQL 17 vazio, backend, frontend e Chrome em
  portas alternativas; V1 a V15 aplicadas e processos do usuario preservados;
- Swagger e OpenAPI 3.1: rota de previa e rota de aplicacao conferidas com
  respostas 200, 400, 401, 403, 404, 409, 422 e 500;
- ranking real: seis topicos, sendo quatro lacunas e duas fraquezas;
- agenda real: duas revisoes vencidas; a previa reservou dois blocos especificos
  de 20 minutos e nao persistiu blocos;
- alternancia real: `LACUNA/TEORIA` em 21, 23 e 25 de julho e
  `FRAQUEZA/QUESTOES` em 22, 24 e 26 de julho;
- aplicacao real: 14 blocos e 640 minutos criados, sem substituicao ou excesso;
- assinatura antiga depois de alterar prioridade: resposta
  `409 PREVIA_DA_GERACAO_DESATUALIZADA`;
- isolamento A/B real: usuario B recebeu 404 no plano de A, sem mutacao;
- correcao de evidencia real: fato anterior permaneceu `CORRIGIDO` e um novo
  fato `ATIVO` foi criado; a agenda passou a `JA_PLANEJADA`;
- sessao ausente: resposta 401;
- 390 x 844, 768 x 1024 e 1280 x 800: gaveta com revisoes, tipos, grupos,
  faixas e justificativas, sem rolagem horizontal;
- `?inicio=2026-07-20`: preservado durante o fluxo;
- Escape revelou ausencia de retorno de foco no navegador real; depois da
  correcao, nova execucao no Chrome confirmou foco no botao `Gerar semana` e
  preservacao da URL;
- erro de rede offline nao foi exercitado manualmente; repeticao de erro
  recuperavel permanece coberta pelos testes automatizados do frontend.

Capturas temporarias conferidas em `/tmp/trilha-s3-390x844.png`,
`/tmp/trilha-s3-768x1024.png` e `/tmp/trilha-s3-1280x800.png`. A stack isolada
foi removida e as portas alternativas foram confirmadas como livres.
