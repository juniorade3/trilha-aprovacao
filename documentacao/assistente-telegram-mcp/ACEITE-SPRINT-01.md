# Aceite factual — Sprint 01

## Referencia

- Branch: `feature/assistente-01-identidade-operacoes`.
- Base: `7b8e74f9bb5bf8ed41db821ca0353e8d422351ef`.
- Data da verificacao: 22 de julho de 2026.
- Feature flag: desligada por padrao.

## Escopo implementado

- Migration V16 com vinculos de canal, credenciais de integracao, operacoes assistidas e auditoria append-only.
- Codigo de vinculo aleatorio, de uso unico e com validade curta.
- Um Telegram de conversa direta por vinculo ativo e credencial MCP isolada por usuario.
- Rotacao por reconexao: o acesso anterior e revogado, um novo codigo e emitido e a nova credencial so nasce depois do envio desse codigo ao bot.
- Hashes e assinaturas com HMAC-SHA-256 e comparacao em tempo constante.
- Estados, expiracao, idempotencia e controle otimista das operacoes assistidas.
- Consulta, revogacao e historico da integracao na aplicacao web.
- Endpoints web documentados no OpenAPI e troca confiavel provisoria protegida pela feature flag.
- Pagina acessivel de integracao com tratamento independente de falha no historico e devolucao de foco.

## Verificacoes executadas

- Testes backend afetados: 23 testes aprovados, sem falhas.
- Testes frontend afetados: 23 testes aprovados, sem falhas; tipos, lint e formatacao aprovados.
- `docker compose config`: aprovado.
- `make verificar`: aprovado.
  - Backend: 174 testes aprovados, sem falhas, erros ou ignorados.
  - Frontend: 29 arquivos e 149 testes aprovados.
  - Verificacao de tipos, lint, build e formatacao: aprovados.
  - `npm audit`: nenhuma vulnerabilidade encontrada.
- Banco PostgreSQL vazio: migrations V1 a V16 aplicadas pelos testes de integracao.
- `git diff --check`: aprovado antes do commit.

## Limites desta sprint

Nao foram validados nesta sprint o Telegram real, o OpenClaw, o protocolo MCP, a entrega da credencial ao agente, assinatura HMAC das requisicoes do Gateway, nonce, protecao contra replay e rate limit. Esses itens pertencem a Sprint 02 e devem estar concluidos antes de ligar a feature flag.

Nao foi declarada validacao manual nas resolucoes 390 x 844, 768 x 1024 e 1280 x 800, pois ela nao foi executada nesta sprint.
