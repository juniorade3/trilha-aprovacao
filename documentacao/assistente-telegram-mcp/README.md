# Assistente da Trilha via Telegram, OpenClaw e MCP

## Objetivo

Este documento define a implementação planejada de um assistente multiusuário para a Trilha da Aprovação. O Telegram será uma interface adicional para consultar e operar a aplicação; a aplicação web e seus casos de uso continuarão sendo a fonte de verdade.

O primeiro valor entregue será permitir que um usuário vinculado pergunte, por exemplo, "o que devo estudar hoje?" e receba uma resposta baseada no plano, nas revisões, nas prioridades e nas evidências atuais da própria conta. A autonomia será ampliada gradualmente até o cadastro assistido de concursos e editais.

Nenhuma etapa descrita como futura neste documento deve ser interpretada como resultado já executado.

## Estado da implementacao

- O planejamento iniciou em `main` no commit `9e4d534`, com a migration V15.
- A Sprint 02 partiu de `main` no commit `d6a59e156ce15eabcf7667b789fc634632e27bcf`, depois da integracao da Sprint 01.
- A migration mais recente na branch da Sprint 02 e a V17.
- Baseline executado em 21 de julho de 2026: 151 testes de backend e 131 testes de frontend aprovados.
- Porta da Sprint 0 executada em 21 de julho de 2026: `docker compose config` aprovado e `make verificar` aprovado, incluindo empacotamento do backend, tipos, lint, testes, build, formatação e auditoria do frontend com zero vulnerabilidades.
- O [aceite da Sprint 01](ACEITE-SPRINT-01.md) registra os resultados executados da identidade e das operacoes assistidas.
- O [aceite da Sprint 02](ACEITE-SPRINT-02.md) registra os testes MCP, PostgreSQL V1-V17 e validacoes do OpenClaw ja executados. A repeticao integral de `make verificar` ainda esta pendente e a feature flag permanece desligada.
- As dependencias e os resultados devem ser confirmados novamente em cada sprint; itens sem evidencia no aceite correspondente continuam planejados.

## Arquitetura

```text
Telegram
   |
   v
OpenClaw Gateway
   |
   +-- agente e sessao exclusivos do usuario
   |
   v
MCP da Trilha (Streamable HTTP)
   |
   v
Casos de uso do monolito modular
   |
   v
PostgreSQL
```

O fluxo respeitará as seguintes fronteiras:

1. O Telegram identifica o remetente por seu identificador numérico, nunca pelo `username`.
2. O OpenClaw encaminha a mensagem para o agente, a sessão e a credencial MCP vinculados àquele remetente.
3. O adaptador MCP autentica a credencial e deriva o usuário. Nenhuma ferramenta aceita um identificador de usuário livre.
4. As ferramentas MCP chamam casos de uso da aplicação; não acessam diretamente SQL, entidades JPA ou rotas HTTP genéricas.
5. Os casos de uso aplicam as mesmas regras de domínio, isolamento e transação já usadas pela aplicação web.
6. Operações de escrita passam por uma prévia persistida e por confirmação fora do controle do modelo.

### Componentes planejados

| Componente | Responsabilidade | Limite de confiança |
|---|---|---|
| Bot do Telegram | Receber mensagens, anexos, callbacks e confirmações | Não decide identidade nem executa regras de negócio |
| OpenClaw Gateway | Rotear cada remetente ao agente correto e executar o runtime Codex nativo, autenticado pelo login existente do CLI | Não acessa banco, repositório, Docker, shell ou segredos gerais da aplicação |
| Agente conversacional | Entender a intenção, selecionar ferramentas e explicar resultados | Memória não é fonte de verdade e não confirma sua própria escrita |
| Agente leitor | Extrair estrutura de texto, imagem, PDF ou link | Não possui ferramenta de escrita e trata o documento como conteúdo hostil |
| Adaptador MCP | Expor contratos tipados e autenticar credenciais de integração | Não contém regra de domínio nem recebe identificador livre de usuário |
| Casos de uso | Consultar, validar, preparar e aplicar operações | Preservam isolamento, idempotência, concorrência e transações |
| Aplicação web | Vincular e revogar Telegram, revisar operações e continuar oferecendo todos os fluxos | Permanece fonte canônica e alternativa ao bot |
| PostgreSQL | Persistir vínculos, credenciais, operações e auditoria | Acessível somente pelo backend |
| Armazenamento temporário | Manter anexos durante extração controlada | Conteúdo expira e não é preservado nos logs |

## Decisões fixadas

- Usar `openai/gpt-5.5` pelo runtime Codex nativo e fail-closed do OpenClaw,
  com o plugin Codex. O binario e gerenciado pelo OpenClaw e reutiliza o arquivo
  do login existente do CLI, montado somente para o Gateway em modo somente
  leitura; nao existe `OPENAI_API_KEY` nem fallback por chave de API.
- Usar um Gateway OpenClaw compartilhado para um conjunto privado e previamente aprovado de usuários.
- Atender somente conversas diretas com o bot; mensagens em grupos do Telegram não executarão ferramentas.
- Manter um agente, uma sessão e uma credencial MCP isolados por vínculo ativo.
- Implementar o MCP no backend Java por Streamable HTTP, preservando o domínio livre de Spring, JPA, HTTP e PostgreSQL.
- Manter a autenticação web por sessão e CSRF. O MCP terá cadeia stateless exclusiva e credencial revogável com escopos.
- Derivar o usuário da credencial e do vínculo confiável. `identificadorDoUsuario` não fará parte dos argumentos MCP.
- Consultas poderão ocorrer automaticamente. Preparações não persistirão mudanças de negócio.
- Toda escrita exigirá operação previamente preparada, assinatura canônica, validade, versões consultadas e chave de idempotência.
- Aceitar confirmação comum por botão, texto `CONFIRMAR <codigo>` ou voz cuja transcrição contenha exatamente o desafio esperado.
- Ativação, arquivamento, cancelamento e substituições exigirão confirmação reforçada; exclusão física continuará indisponível.
- Tratar documentos, links e texto externo como conteúdo não confiável e separar leitura de aplicação.
- Criar concursos importados inicialmente como rascunho. A ativação será uma operação independente.
- Manter sugestões ambíguas de mapeamento como pendentes; a IA não as confirmará sozinha.
- Proteger toda a capacidade por feature flag até o aceite final.
- Não remover nem reduzir funcionalidades da aplicação web.

## Escopo funcional

### Incluído

- vínculo revogável entre Telegram e usuário da Trilha;
- consultas conversacionais sobre hoje, revisões, prioridades, histórico, progresso e concurso ativo;
- preparação e confirmação de registros de estudo, conclusão, interrupção e correção;
- preparação e confirmação de geração e replanejamento determinísticos;
- alteração assistida de disponibilidade e prioridades;
- cadastro em lote de concurso, edital, cargo, provas, grupos, matérias e conteúdo programático;
- sugestão assistida de reutilização e mapeamento de matérias e tópicos;
- auditoria, idempotência, concorrência, métricas e operação segura.

### Fora do escopo

- bot público ou atendimento em grupos do Telegram;
- binario Codex do host, shell, `exec`, navegador pessoal, filesystem, nodes ou
  Docker disponiveis ao agente; o uso do arquivo de login pelo runtime gerenciado
  nao concede essas capacidades;
- acesso direto do OpenClaw ou do MCP ao PostgreSQL;
- ferramenta genérica para chamar qualquer rota, executar SQL ou editar arquivos;
- armazenamento de raciocínio interno do modelo, credenciais ou documentos completos nos logs;
- recomendações livres que ignorem ranking, planejamento, revisões ou evidências da aplicação;
- banco de questões, exclusão destrutiva e alteração de autenticação ou permissões pela IA;
- aplicação silenciosa de prévia divergente ou de sugestão ambígua.

## Sequência de entrega

Cada sprint partirá da `main` que contenha a sprint anterior, terá branch e PR próprios, porta de qualidade, documentação factual e CI verde. A autorização para percorrer a sequência não elimina o aceite técnico de cada etapa.

### Sprint 0 — Documentação executável

Branch: `docs/assistente-telegram-mcp`.

- registrar arquitetura, contratos, conversas, ameaças, implantação, testes e métricas;
- separar decisões planejadas de resultados efetivamente executados;
- confirmar a base antes de iniciar código de produção.

### Sprint 1 — Identidade e operações assistidas

Branch: `feature/assistente-01-identidade-operacoes`.

- criar a migration seguinte à última existente para vínculos, credenciais, operações e auditoria;
- implementar código temporário, vínculo, consulta, revogação e rotação;
- implementar prévia assinada, expiração, idempotência e concorrência otimista;
- criar a interface web de integração e histórico de operações.

Estados previstos para uma operação: `PREPARADA`, `AGUARDANDO_CONFIRMACAO`, `CONFIRMADA`, `APLICADA`, `CANCELADA`, `EXPIRADA` e `FALHOU`.

### Sprint 2 — MCP e provisionamento OpenClaw

Branch: `feature/assistente-02-mcp-openclaw`.

Estado factual: implementado na branch e validado nos itens registrados em [ACEITE-SPRINT-02.md](ACEITE-SPRINT-02.md); a porta integral ainda aguarda a nova execucao de `make verificar`.

- adicionar endpoint MCP Streamable HTTP e segurança stateless exclusiva;
- provisionar agente, sessão e credencial isolados por vínculo;
- expor apenas ferramentas tipadas de consulta e acompanhamento de operação;
- criar consultas agregadas para evitar fan-out HTTP e N+1;
- restringir ferramentas e recursos disponíveis ao OpenClaw.

### Sprint 3 — Consultas conversacionais

Branch: `feature/assistente-03-consultas-conversacionais`.

- responder perguntas sobre estudos do dia, próximo bloco, revisões, prioridades, progresso, histórico e prova;
- oferecer comandos `/hoje`, `/revisoes`, `/prioridades`, `/progresso`, `/operacoes`, `/desconectar` e `/privacidade`;
- consultar novamente a aplicação antes de responder sobre estado de negócio;
- explicar ausência de plano ou concurso sem inventar recomendações.

### Sprint 4 — Registros e planejamento

Branch: `feature/assistente-04-registros-planejamento`.

- preparar e confirmar registro, conclusão, interrupção e correção de estudo;
- preparar e confirmar geração, replanejamento, disponibilidade e prioridades;
- reutilizar determinismo, assinaturas e linhagem existentes;
- impedir duplicidade após retry, callback repetido ou timeout depois do commit;
- recalcular prévia desatualizada e exigir nova confirmação.

### Sprint 5 — Cadastro completo de concursos

Branch: `feature/assistente-05-cadastro-concursos`.

- processar texto, imagem, PDF e link por um leitor isolado;
- preservar origem, hash, página ou seção, trecho de apoio e incerteza dos campos;
- classificar cada proposta como `CRIAR`, `REUTILIZAR`, `ALTERAR`, `IGNORAR`, `CONFLITO` ou `PENDENTE`;
- criar toda a estrutura confirmada em uma transação e reverter o lote integralmente em caso de falha;
- manter concursos novos em rascunho e mapeamentos ambíguos pendentes.

### Sprint 6 — Consolidação e ativação

Branch: `feature/assistente-06-consolidacao`.

- adicionar confirmação reforçada para operações de alto impacto;
- consolidar métricas, health checks, limites, circuit breaker, auditoria e runbook;
- validar aplicação web e bot em conjunto;
- habilitar a feature flag somente depois do aceite integral.

## Matriz de autonomia

| Operação | Ação do agente | Confirmação | Resultado permitido |
|---|---|---|---|
| Consultar agenda, revisões, prioridades, progresso e histórico | Executa consulta tipada | Não | Somente leitura |
| Explicar um bloco ou recomendação existente | Consulta fatos e redige explicação | Não | Somente leitura |
| Preparar estudo, geração, replanejamento ou cadastro | Cria prévia assinada | Não | Nenhuma mutação de negócio |
| Registrar, concluir, interromper ou corrigir estudo | Apresenta impacto e aguarda desafio | Comum | Aplicação idempotente e transacional |
| Gerar ou replanejar semana | Apresenta blocos e alterações | Comum | Aplicação somente se a assinatura continuar atual |
| Alterar disponibilidade ou prioridade | Apresenta valores anteriores e novos | Comum | Aplicação versionada |
| Criar concurso e conteúdo programático | Apresenta resumo, conflitos e pendências | Detalhada | Criação atômica em rascunho |
| Confirmar mapeamento inequívoco | Apresenta origem e destino | Comum, individual ou lote explícito | Mapeamento confirmado |
| Resolver mapeamento ambíguo | Solicita escolha do usuário | Individual | Nunca decidido autonomamente |
| Ativar, arquivar, cancelar ou substituir | Apresenta impacto e segundo desafio | Reforçada | Somente mesma sessão e vínculo, dentro da validade |
| Excluir fisicamente ou alterar segurança | Recusa | Não aplicável | Proibido |

## Operação, segurança e limites

- Vínculos usarão códigos aleatórios, curtos, de uso único e com validade limitada.
- Credenciais serão armazenadas somente como hash no backend, terão escopos mínimos e poderão ser revogadas ou rotacionadas.
- A confirmação será vinculada ao usuário, Telegram, bot, conversa, operação, assinatura, versões, nonce e expiração.
- Updates, callbacks e comandos repetidos do Telegram serão deduplicados.
- A mesma chave de idempotência com o mesmo corpo retornará o resultado original; com corpo diferente retornará conflito.
- Alteração humana entre preparação e aplicação invalidará a prévia.
- URLs externas aceitarão somente protocolos permitidos e bloquearão localhost, redes privadas, endpoints de metadados, redirecionamentos excessivos, MIME inválido e tamanho excedido.
- Anexos terão limites de tamanho e páginas, hash e retenção temporária; os logs guardarão metadados, não o conteúdo integral.
- O OpenClaw executará em container ou usuário dedicado, com versão fixada, long polling e sem porta pública.
- O Gateway não receberá acesso ao banco, repositório, Docker socket ou segredos da aplicação.
- A auditoria será append-only e registrará usuário, ator `IA_TELEGRAM`, ferramenta, hashes, origem, confirmação, versões, resultado e correlação.
- Falha do modelo ou do Telegram não poderá comprometer a aplicação web nem deixar transações parciais.
- O domínio continuará independente de Spring, JPA, HTTP, MCP, OpenClaw e PostgreSQL.

## Critérios gerais de aceite planejados

- zero escrita sem confirmação válida;
- zero operação duplicada após retry ou timeout;
- zero vazamento entre usuários A e B;
- zero aplicação parcial de cadastro em lote;
- zero campo inventado sem indicação de incerteza;
- preservação integral dos fluxos web existentes;
- banco PostgreSQL vazio migrando de V1 até a migration mais recente;
- conformidade dos schemas MCP, OpenAPI e isolamento de segurança;
- execução de `docker compose config`, `make verificar`, `git diff --check` e auditoria profunda do OpenClaw;
- validação real do frontend em 390x844, 768x1024 e 1280x800.

Esses critérios são metas da implementação. Somente documentos de aceite posteriores poderão marcá-los como executados e aprovados.
