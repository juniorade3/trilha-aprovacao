# AGENTS.md — Automação, Telegram e MCP

## Escopo

Este módulo integra a Trilha ao Telegram por OpenClaw e MCP.

A aplicação web e os casos de uso existentes continuam sendo a fonte de verdade.

## Leia primeiro

1. `documentacao/agente/MCP-TRILHA.md`
2. `documentacao/assistente-telegram-mcp/CONTRATOS-MCP-E-CONVERSAS.md`
3. `documentacao/assistente-telegram-mcp/SEGURANCA-OPERACAO-E-ACEITE.md`
4. `infraestrutura/openclaw/README.md`
5. `infraestrutura/openclaw/RUNBOOK.md`

## Mapa do módulo

### API confiável

- `api/ControladorConfiavelDeVinculosDoTelegram.java`
- DTOs de vínculo, provisionamento e confirmação.

Esses endpoints não são ferramentas MCP. Eles são chamados pelo integrador confiável.

### Aplicação

- `aplicacao/ServicoDeConsultasMcp.java`
- `aplicacao/ServicoDePreparacoesMcp.java`
- `aplicacao/ServicoDeCadastroAssistidoDeConcursos.java`
- `aplicacao/ServicoDeOperacoesCriticasMcp.java`
- `aplicacao/ServicoDeOperacoesAssistidas.java`
- `aplicacao/ServicoDeAplicacaoDeOperacoesAssistidas.java`
- `aplicacao/ServicoDeAuditoriaMcp.java`
- `aplicacao/ServicoDeVinculosDoTelegram.java`
- `aplicacao/ResultadoDaConsultaMcp.java`

### Domínio

- vínculo de canal;
- credencial de integração;
- operação assistida;
- evento de auditoria;
- estados, transições, expiração e revogação.

### Infraestrutura

- `ConfiguracaoDoServidorMcp`
- `ConfiguracaoDeSegurancaMcp`
- `FiltroDeCredencialMcp`
- `AutenticadorDeCredencialMcp`
- `CatalogoDeFerramentasMcp`
- `ContextoDaChamadaMcp`
- `IdentidadeDaIntegracaoMcp`
- repositórios persistidos;
- segurança do Gateway;
- métricas e saúde.

## Invariantes

- `/mcp` só existe quando `trilha.automacao.habilitada=true`.
- Transporte é Streamable HTTP stateless.
- A cadeia de segurança MCP usa `@Order(1)`.
- A autenticação é Bearer e exige agente e sessão provisionados.
- Token deve começar com `mcp_`.
- Token é localizado por hash.
- Credencial, vínculo e usuário precisam estar ativos.
- Agente e sessão recebidos precisam corresponder aos valores persistidos.
- Escopos são verificados por ferramenta.
- Schemas de entrada usam `additionalProperties=false`.
- O catálogo não recebe usuário nos argumentos.
- Toda chamada é auditada com hash de entrada e saída.
- Erros internos não expõem detalhes.
- Ferramentas `preparar_*` não aplicam a mutação.
- Confirmação ocorre pelo endpoint confiável e não por ferramenta MCP.
- Operações críticas usam confirmação reforçada em duas etapas.
- Alteração concorrente invalida a prévia.

## Ao adicionar ferramenta MCP

1. Reutilize um caso de uso existente.
2. Defina nome em `snake_case`.
3. Defina schema fechado.
4. Defina output schema fechado.
5. Escolha o menor escopo.
6. Não aceite `identificadorDoUsuario`.
7. Defina corretamente:
   - `readOnlyHint`;
   - `destructiveHint=false`;
   - `idempotentHint=true`;
   - `openWorldHint=false`.
8. Retorne `ResultadoDaConsultaMcp`.
9. Registre auditoria.
10. Atualize:
    - `McpIntegracaoTest`;
    - `documentacao/agente/MCP-TRILHA.md`;
    - contratos da integração;
    - workspace do OpenClaw, quando a ferramenta ficar acessível ao agente.

## Testes mínimos

- descoberta da ferramenta;
- schema de entrada e saída;
- sucesso;
- erro estruturado;
- escopo insuficiente;
- isolamento entre usuários A e B;
- auditoria;
- idempotência, para preparação;
- concorrência e expiração, para confirmação;
- feature flag desligada.
