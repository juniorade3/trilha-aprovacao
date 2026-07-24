# AGENTS.md — OpenClaw

## Escopo

Este diretório contém a infraestrutura confiável entre Telegram, OpenClaw e MCP.

Leia `README.md` e `RUNBOOK.md` antes de alterar.

## Componentes

- `compose.yaml`: Gateway, integrador e broker.
- `modelos/openclaw.json`: configuração-base.
- `modelos/workspace/`: instruções por agente.
- `plugin-trilha/`: comandos `/conectar` e `/confirmar`.
- `scripts/broker-de-credenciais-mcp.mjs`: injeta credenciais fora do agente.
- `scripts/integrador-de-vinculos.mjs`: troca código, provisiona e registra.
- scripts de provisionamento, rotação, revogação e validação.

## Invariantes de segurança

- nenhuma porta pública do Gateway;
- somente DM permitida;
- grupos desabilitados;
- um agente, sessão, workspace e credencial por vínculo;
- `mcp.servers` global permanece vazio;
- token MCP não entra no workspace;
- Gateway não monta o diretório de credenciais MCP;
- broker não monta o estado dos agentes;
- plugin não recebe token ou segredo HMAC;
- sem Docker socket;
- sem privilégio;
- raiz somente leitura;
- sem shell, exec, filesystem, navegador, nodes ou ferramentas administrativas;
- segredos e autenticação Codex ficam fora do repositório;
- arquivos sensíveis são regulares, não symlinks, com permissão `0600`;
- diretórios sensíveis usam `0700`;
- imagem OpenClaw usa tag e digest fixos;
- não adicionar fallback por `OPENAI_API_KEY`.

## Mudanças obrigam

- atualização do runbook;
- atualização da versão/digest, quando aplicável;
- testes do plugin;
- testes do integrador;
- testes do broker;
- `docker compose config`;
- `scripts/validar.sh`;
- `config validate`;
- `secrets audit --check`;
- `security audit --deep`;
- smoke A/B antes de ativação.
