# Estado atual do projeto

Data de referência desta análise: 24 de julho de 2026.

## Implementado segundo o repositório

- fundação do monorepo;
- PostgreSQL e Flyway;
- autenticação por sessão;
- catálogo de matérias e tópicos;
- concursos, editais, cargos, provas e conteúdo oficial;
- materiais e registros de estudo;
- dashboard;
- planejamento manual;
- geração determinística;
- regeneração segura;
- priorização;
- revisões;
- integração Telegram;
- MCP Streamable HTTP;
- operações assistidas;
- confirmação comum e reforçada;
- cadastro assistido de concursos;
- métricas e saúde da automação;
- provisionamento OpenClaw.

## MCP

- SDK `io.modelcontextprotocol.sdk:mcp:2.0.0`;
- endpoint `/mcp`;
- servidor `trilha-aprovacao`, contrato `1`;
- transporte stateless;
- schemas fechados;
- 24 ferramentas no catálogo;
- escopos separados;
- autenticação Bearer;
- agente e sessão vinculados;
- auditoria de sucesso e falha;
- isolamento entre usuários testado;
- confirmação fora do modelo;
- feature flag desligada por padrão.

## Qualidade registrada

O aceite da Sprint 06 registra:

- 187 testes de backend;
- 149 testes de frontend;
- catálogo MCP com 24 ferramentas;
- validação do plugin, integrador, provisionador e broker;
- `docker compose config`;
- validação da imagem OpenClaw fixada;
- `config validate`;
- `security audit --deep`;
- `make verificar`;
- auditoria sem vulnerabilidades.

Esses números são evidências registradas no repositório na data do aceite; devem ser executados novamente após novas alterações.

## Pendências operacionais registradas

- ativar a feature flag somente após validação operacional;
- subir backend e OpenClaw com segredos locais;
- concluir vínculo pela interface web;
- testar Telegram real;
- testar voz;
- executar validação visual real;
- repetir smoke com usuários A e B antes da ativação.

## Riscos de documentação

Os documentos iniciais do assistente misturam planejamento histórico e estado executado. Ao atualizar:

- use aceite de sprint para evidência;
- não marque como implementado com base apenas em documento de planejamento;
- mantenha `MCP-TRILHA.md` como visão consolidada do estado atual;
- preserve contratos históricos quando forem úteis para rastreabilidade.
