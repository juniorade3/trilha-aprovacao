# AGENTS.md — Trilha da Aprovação

## 1. Objetivo deste arquivo

Este arquivo é o ponto de entrada dos agentes que trabalham no repositório.

Ele deve permanecer curto. Sua função é:

- explicar a arquitetura geral;
- encaminhar o agente ao módulo correto;
- indicar documentos e arquivos de referência;
- registrar regras que não podem ser deduzidas apenas lendo o código;
- evitar leitura indiscriminada do repositório.

Não transforme este arquivo em uma lista completa de classes.

## 2. Regra de navegação

Antes de alterar código:

1. Leia `documentacao/agente/INDEX.md`.
2. Identifique o domínio afetado.
3. Leia o `AGENTS.md` mais próximo do módulo.
4. Consulte o mapa específico do backend, frontend ou MCP.
5. Localize símbolos com busca antes de abrir arquivos extensos.
6. Leia somente:
   - o ponto de entrada;
   - o caso de uso;
   - a entidade ou regra de domínio;
   - o adaptador de infraestrutura;
   - os testes diretamente relacionados.

Não percorra todo o repositório sem justificar a necessidade.

## 3. Visão do produto

A Trilha da Aprovação é uma aplicação para:

- organizar concursos, editais, cargos, provas e conteúdo programático;
- manter catálogo pessoal de matérias, tópicos e materiais;
- registrar estudos e evidências;
- criar planejamento semanal manual;
- gerar e regenerar planejamento de forma determinística;
- calcular revisões, prioridades, progresso e pendências;
- permitir interação assistida pelo Telegram por meio de OpenClaw e MCP.

A aplicação web e os casos de uso do backend são a fonte de verdade.

## 4. Arquitetura

O projeto é um monorepo com:

- backend Java 21 e Spring Boot;
- frontend Vue 3 e TypeScript;
- PostgreSQL e Flyway;
- API MVC com sessão e CSRF;
- monólito modular;
- integração opcional Telegram + OpenClaw + MCP.

Fluxo web:

```text
Vue 3
  -> HTTP/JSON
  -> API MVC
  -> casos de uso
  -> domínio
  -> infraestrutura
  -> PostgreSQL
```

Fluxo do assistente:

```text
Telegram
  -> OpenClaw
  -> broker de credenciais
  -> MCP Streamable HTTP
  -> casos de uso da Trilha
  -> PostgreSQL
```

## 5. Roteamento por domínio

| Necessidade | Diretório inicial |
|---|---|
| Login, cadastro e sessão | `aplicativos/backend/src/main/java/br/com/trilhaaprovacao/autenticacao/` |
| Telegram, MCP, vínculo e operações assistidas | `aplicativos/backend/src/main/java/br/com/trilhaaprovacao/automacao/` |
| Concursos, editais, cargos e provas | `aplicativos/backend/src/main/java/br/com/trilhaaprovacao/concursos/` |
| Itens oficiais e mapeamento do edital | `aplicativos/backend/src/main/java/br/com/trilhaaprovacao/conteudoprogramatico/` |
| Matérias e tópicos pessoais | `aplicativos/backend/src/main/java/br/com/trilhaaprovacao/conteudos/` |
| Materiais e registros de estudo | `aplicativos/backend/src/main/java/br/com/trilhaaprovacao/estudos/` |
| Evidências de aprendizagem | `aplicativos/backend/src/main/java/br/com/trilhaaprovacao/evidencias/` |
| Dashboard e progresso | `aplicativos/backend/src/main/java/br/com/trilhaaprovacao/dashboard/` |
| Plano semanal, blocos, execução, geração e replanejamento | `aplicativos/backend/src/main/java/br/com/trilhaaprovacao/planejamento/` |
| Ranking e priorização | `aplicativos/backend/src/main/java/br/com/trilhaaprovacao/priorizacao/` |
| Revisões espaçadas | `aplicativos/backend/src/main/java/br/com/trilhaaprovacao/revisoes/` |
| Tipos e respostas compartilhadas | `aplicativos/backend/src/main/java/br/com/trilhaaprovacao/compartilhado/` |
| Interface web | `aplicativos/frontend/src/` |
| Runtime OpenClaw | `infraestrutura/openclaw/` |

## 6. Regras arquiteturais obrigatórias

- O domínio não depende de Spring, JPA, HTTP, JSON, PostgreSQL, Vue, MCP ou OpenClaw.
- Controllers e componentes Vue coordenam entrada e saída; não concentram regras de negócio.
- A API expõe DTOs, nunca entidades JPA.
- Regras pertencem ao domínio ou aos casos de uso.
- Consultas agregadas podem usar JDBC, mas sempre recebem o usuário autenticado e não alteram estado.
- Todo recurso de negócio deve ser isolado pelo usuário.
- Não crie microsserviço, fila, Kafka, Redis, CQRS, event sourcing ou cache distribuído sem decisão arquitetural explícita.
- Classes, métodos, DTOs, rotas internas e nomes de domínio permanecem em português.
- Não adicione dependência sem justificar necessidade, alternativas e impacto.
- Preserve compatibilidade com PostgreSQL real e Flyway.
- Não reimplemente no MCP uma regra já existente na aplicação.

## 7. Regras específicas da automação

- O modelo nunca escolhe livremente o usuário.
- O usuário efetivo é derivado da credencial MCP e do vínculo ativo.
- Nenhuma ferramenta MCP recebe `identificadorDoUsuario`.
- Ferramentas de leitura podem consultar.
- Ferramentas `preparar_*` persistem somente uma operação assistida e sua auditoria.
- Aplicação de escrita ocorre fora do controle direto do modelo.
- Toda escrita exige confirmação válida, verificação de versões e transação.
- Exclusão física, segurança, permissões, SQL, shell e filesystem genérico não entram no catálogo MCP.
- A feature flag da automação permanece desligada por padrão.
- Segredos e tokens nunca entram no repositório, workspace, log ou documentação de exemplo.

## 8. Referências canônicas

Comece pelos documentos abaixo:

- `documentacao/agente/INDEX.md`
- `documentacao/agente/MAPA-DOMINIOS.md`
- `documentacao/agente/MAPA-BACKEND.md`
- `documentacao/agente/MAPA-FRONTEND.md`
- `documentacao/agente/FLUXOS-PRINCIPAIS.md`
- `documentacao/agente/ESTADO-ATUAL.md`
- `documentacao/agente/MCP-TRILHA.md`
- `documentacao/arquitetura/ARQUITETURA-INICIAL.md`
- `documentacao/arquitetura/MODELO-DE-DOMINIO.md`
- `documentacao/decisoes/`
- `documentacao/assistente-telegram-mcp/`
- `infraestrutura/openclaw/RUNBOOK.md`

## 9. Comandos

Subir infraestrutura:

```bash
make infra-subir
```

Executar backend:

```bash
make backend-executar
```

Executar frontend:

```bash
make frontend-executar
```

Testes:

```bash
make testar-backend
make testar-frontend
```

Portas completas:

```bash
make verificar-backend
make verificar-frontend
make verificar
```

Validar OpenClaw:

```bash
infraestrutura/openclaw/scripts/validar.sh
```

## 10. Estratégia de alteração

Para uma tarefa:

1. declare o módulo afetado;
2. apresente os arquivos que pretende ler;
3. identifique o padrão de referência já existente;
4. altere o menor conjunto coerente;
5. adicione ou atualize testes;
6. execute primeiro testes direcionados;
7. execute a porta completa quando o impacto for transversal;
8. atualize documentação apenas quando comportamento, contrato ou operação mudar.

## 11. Critério de conclusão

Uma tarefa não está concluída apenas porque compila.

Informe ao final:

- comportamento entregue;
- arquivos alterados;
- decisões tomadas;
- testes executados e resultados;
- validações não executadas;
- riscos ou pendências;
- documentação atualizada.

Não afirme que um teste foi executado quando ele não foi.
