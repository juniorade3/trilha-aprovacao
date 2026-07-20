# Aceite da Sprint 03

## Base

- branch: `feature/geracao-deterministica-sprint-03`
- commit inicial: `88ee273f7fde560208699aa75481ca405181b5a6`
- commit final: pendente de criação e publicação do PR
- migration final: `V12`

## Matriz

| Item | Status | Evidência |
|---|---|---|
| fluxo integral backend | APROVADO | `PlanejamentoIntegracaoTest` e `IntegracaoPlanejamentoEstudosTest` |
| determinismo | APROVADO | `GeradorDeterministicoDePlanoTest`, incluindo quatro permutações fixas |
| capacidade diária | APROVADO | testes de domínio/integração e prévias reais em 390, 768 e 1280 px |
| três matérias | APROVADO | testes de domínio e segunda-feira real com revisão mais três matérias em 170 minutos |
| redução explicada | APROVADO | quarta-feira real reduzida, com justificativa de capacidade insuficiente |
| revisão única | APROVADO | testes de domínio/integração e regeneração real com no máximo uma revisão por dia |
| preservação manual/ajustado | APROVADO | integração e regeneração real preservando os mesmos blocos manual e ajustado |
| isolamento A/B nas quatro rotas | APROVADO | quatro respostas 404, hashes e contagens antes/depois sem alteração |
| concorrência | APROVADO | `PlanejamentoIntegracaoTest`, com regenerações concorrentes e preservação |
| migrations em banco vazio | APROVADO | `MigracoesDaGeracaoDeterministicaIntegracaoTest` e PostgreSQL 17 vazio migrado de V1 a V12 |
| arquitetura | APROVADO | `ArquiteturaDaGeracaoDeterministicaTest` |
| OpenAPI | APROVADO | `DocumentacaoDaApiIntegracaoTest`, `/v3/api-docs` 200 e Swagger UI acessível |
| execução e histórico | APROVADO | fluxo integrado até ativação, Hoje, execução idempotente e histórico |
| frontend | APROVADO | 93 testes Vitest, tipos, lint, build e formatação |
| teclado e foco | APROVADO | Chrome/CDP: Tab, Enter, contenção, Escape em duas camadas e restauração do foco |
| 390 px | APROVADO | Chrome em 390 x 844, fluxo completo e sem overflow horizontal |
| 768 px | APROVADO | Chrome em 768 x 1024, fluxo completo e sem overflow horizontal |
| 1280 px | APROVADO | Chrome em 1280 x 800, fluxo completo e sem overflow horizontal |
| sessão expirada | APROVADO | logout 204, mutação 401 sem 500, aviso, redirecionamento e retorno à semana |
| erro de rede e repetição | APROVADO | falha offline anunciada e repetição da prévia com durações 55/20 preservadas |
| make verificar | APROVADO | 103 testes backend e 93 frontend; build, tipos, lint, formato e auditoria aprovados |
| CI do PR | NÃO EXECUTADO | PR ainda não aberto |

## Comandos executados

```bash
git status --short
git rev-parse HEAD
find aplicativos/backend/src/main/resources/db/migration -maxdepth 1 -type f
make verificar
./mvnw -q -Dtest=GeradorDeterministicoDePlanoTest,PlanejamentoIntegracaoTest,IntegracaoPlanejamentoEstudosTest,MigracoesDaGeracaoDeterministicaIntegracaoTest,ArquiteturaDaGeracaoDeterministicaTest,DocumentacaoDaApiIntegracaoTest test
npm run test -- src/compartilhado/api/clienteHttp.spec.ts src/modulos/planejamento/GavetaDeGeracaoDeterministica.spec.ts src/modulos/planejamento/PlanejamentoSemanaPagina.spec.ts
npm run verificar-tipos
docker compose config
docker compose up -d
docker compose -f /tmp/compose-postgresql-sprint03.yaml up -d --wait
bash /tmp/preparar-validacao-manual-sprint03.sh
bash /tmp/validar-openapi-sprint03.sh
git diff --check
```

## Validação manual

O backend foi iniciado em `18080`, o frontend em `15173` e um PostgreSQL 17
vazio e efêmero em `15432`. O Flyway aplicou as doze migrations até V12.

Em Chrome real controlado por CDP, os fluxos de 390 x 844, 768 x 1024 e
1280 x 800 percorreram prioridades, configuração, prévia, justificativa,
aplicação, edição e regeneração. Os três cenários exibiram quatro matérias com
Banco de dados uma única vez, durações 50/20, capacidade reduzida explicada,
bloco manual preservado e bloco ajustado preservado. As páginas terminaram sem
overflow horizontal.

O cenário de 390 px também exercitou foco e teclado, conexão offline, repetição
da operação correta e sessão expirada. Um primeiro envio simplificado de Enter
pelo harness não ativou o botão; a repetição com a sequência nativa completa do
CDP (`rawKeyDown`, `char`, `keyUp`) abriu a gaveta e posicionou o foco em
`Fechar`.

As quatro rotas chamadas pela sessão B contra o plano de A retornaram 404 com
`PLANO_SEMANAL_NAO_ENCONTRADO`. Os hashes dos snapshots e as contagens SQL de
prioridades e blocos permaneceram idênticos. No fluxo autenticado de Swagger,
as quatro operações responderam 200, a segunda aplicação sem substituição
respondeu 409 e a substituição explícita respondeu 200. Sem sessão houve 401 e
sem CSRF houve 403.

As evidências temporárias foram mantidas apenas em
`/tmp/trilha-sprint03-manual-final` e não integram o repositório.

## Divergências

Nenhuma divergência funcional foi observada. Dois scripts de automação tiveram
asserções intermediárias corrigidas ou repetidas; os resultados registrados na
matriz correspondem às execuções finais bem-sucedidas.

## Pendências

- Executar e registrar a CI depois da abertura do PR.
