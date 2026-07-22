# Aceite factual — Sprint 02

## Referencia

- Branch: `feature/assistente-02-mcp-openclaw`.
- Base: `d6a59e156ce15eabcf7667b789fc634632e27bcf`.
- Data da verificacao: 22 de julho de 2026.
- Feature flag: desligada.

## Escopo implementado

- Migration V17 com coerencia do provisionamento, unicidade de sessao ativa e metadados persistidos para protecao do Gateway.
- Servidor MCP oficial em `/mcp`, com transporte Streamable HTTP stateless e cadeia de seguranca independente da sessao web.
- Autenticacao MCP por credencial individual, agente e sessao vinculados, sempre derivando o usuario no backend.
- Catalogo fechado de oito ferramentas somente de leitura para agenda, revisoes, prioridades, progresso, historico, estrutura do concurso, explicacao de bloco e consulta de operacao assistida.
- Schemas de entrada e saida fechados, escopos minimos, limites de argumentos, auditoria e atualizacao do ultimo uso da credencial.
- Gateway confiavel protegido por HMAC, instante, nonce, idempotencia, protecao contra replay e limite de requisicoes.
- Provisionamento OpenClaw isolado por vinculo, com agente, sessao, workspace e credencial MCP proprios.
- OpenClaw fixado na versao `2026.7.1` e na imagem com digest `sha256:6a31d44b2944e7adcd2b582bf6fb463111264ebca97a0201795b799135bd102c`.
- Container sem porta publica, sem privilegios, sem Docker socket e sem ferramentas de shell, filesystem, navegador, nodes ou administracao para o agente.
- Scripts operacionais para inicializacao, provisionamento, registro assinado, rotacao, revogacao e validacao.

## Verificacoes executadas

- Testes backend afetados: 15 aprovados, sem falhas — 12 de automacao e 3 exercitando o cliente MCP real.
- PostgreSQL vazio: migrations V1 a V17 aplicadas com sucesso.
- `docker compose config`: aprovado.
- `infraestrutura/openclaw/scripts/validar.sh`: aprovado.
- Validacao pelo binario oficial do OpenClaw: configuracao valida (`valid: true`) e nenhum aviso.
- `openclaw security audit --deep`: 0 achados criticos, 1 aviso e 1 informacao. O aviso registrado informa que o Gateway one-shot nao estava iniciado durante a auditoria.
- `openclaw secrets audit --check`: aprovado, sem segredo encontrado.
- `git diff --check`: aprovado.

## Porta concluida

- Backend recompilado do zero: 186 testes aprovados, sem falhas.
- Frontend: 29 arquivos e 149 testes aprovados; tipos, lint, build e formatacao aprovados; `npm audit` sem vulnerabilidades.
- Plugin `/conectar`: 6 testes aprovados.
- Integrador confiavel: 4 testes aprovados, incluindo retry apos provisionamento local.
- Fluxo real em container do Gateway para o broker e backend falso no host: aprovado.
- `docker compose config`, imagem do integrador e `git diff --check`: aprovados.

## Limites desta verificacao

- Nao foi executada conversa real pelo Telegram nem chamada real ao provedor OpenAI.
- Nao foi executado o fluxo conversacional da Sprint 03.
- Nao foram habilitadas ferramentas de escrita, confirmacao, anexos ou voz.
- A feature flag permanece desligada.
