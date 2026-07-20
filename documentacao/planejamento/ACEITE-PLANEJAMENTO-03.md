# Aceite do Planejamento 3 — Replanejamento deterministico

## Base

- branch: `feature/planejamento-03-replanejamento`
- commit inicial: `683bab52c88c9f291ce422464ae2e8bf719da3a7`
- migration final: `V13`

## Resultados automatizados

| Item | Resultado executado |
|---|---|
| dominio e regressao da geracao | 113 testes backend aprovados no total, incluindo `ReplanejadorDeterministicoDePlanoTest` e `GeradorDeterministicoDePlanoTest` |
| PostgreSQL vazio V1–V13 | aprovado por `MigracoesDaGeracaoDeterministicaIntegracaoTest` com PostgreSQL 17 |
| previa, aplicacao, concorrencia, linhagem e historico | aprovados por `PlanejamentoIntegracaoTest` |
| isolamento A/B nas tres rotas | tres respostas 404 e ausencia de mutacao aprovadas por `PlanejamentoIntegracaoTest` |
| arquitetura | aprovado por `ArquiteturaDaGeracaoDeterministicaTest` |
| OpenAPI | contratos e respostas aprovados por `DocumentacaoDaApiIntegracaoTest` |
| frontend | 97 testes Vitest aprovados; tipos, lint, build e formatacao aprovados |
| auditoria de dependencias | `npm audit` encontrou zero vulnerabilidades |
| porta global | `make verificar` aprovado |
| CI do PR | GitHub Actions run `29759549107`: estrutura/Compose, backend/migrations/arquitetura e frontend aprovados |

## Validacao manual executada

O backend da branch foi executado na porta `8081`, o frontend na porta `5175` e
o navegador Chrome foi controlado por CDP. As instancias temporarias foram
encerradas ao final.

- 390 x 844, 768 x 1024 e 1280 x 800: gaveta, proposta e historico renderizados
  sem overflow horizontal;
- teclado: Tab permaneceu na gaveta, Escape fechou e o foco voltou a
  `Replanejar pendencias`;
- erro de rede: a falha foi anunciada e `Tentar novamente` recalculou a previa;
- sessao expirada: a resposta 401 redirecionou para o login preservando
  `?inicio=...` e indicando `sessao=expirada`;
- aplicacao integral: transferencia criada, origem replanejada e historico
  atualizado, preservando a semana na URL;
- execucao parcial: 20 de 100 minutos executados; os 80 restantes foram
  divididos em dois fragmentos de 40 minutos e o historico preservou a execucao
  original;
- Swagger UI respondeu 200 e exibiu as tres rotas do replanejamento;
- isolamento A/B, previa desatualizada e concorrencia foram exercitados nos
  testes de integracao PostgreSQL.

## Comandos finais executados

```bash
make verificar
docker compose config
git diff --check
```

Nenhum modulo de otimizacao, Timefold, Motor de Evidencias, Revisoes, Lacunas ou
IA foi iniciado.
