# Índice de navegação para agentes

Use este documento para decidir o menor conjunto de arquivos que precisa ser lido.

## Autenticação e sessão

Comece por:

- `aplicativos/backend/src/main/java/br/com/trilhaaprovacao/autenticacao/`
- configuração de segurança;
- serviço de usuário e sessão;
- testes MVC de autenticação;
- `aplicativos/frontend/src/aplicacao/estado/sessao`;
- páginas de login e cadastro.

## Concursos e conteúdo oficial

Comece por:

- `concursos/aplicacao/ServicoDaEstruturaDeConcursos.java`
- módulo `concursos/dominio`;
- módulo `conteudoprogramatico`;
- `documentacao/arquitetura/MODELO-DE-DOMINIO.md`;
- testes do fluxo específico.

## Matérias e tópicos pessoais

Comece por:

- módulo `conteudos`;
- entidade de matéria;
- árvore de tópicos;
- regras de arquivamento e unicidade;
- módulo frontend `modulos/materias`.

## Trilhas publicadas

Comece por:

- módulo `trilhas`;
- `trilhas/aplicacao/ServicoDeTrilhasPublicadas.java`;
- migrations `V20__cria_catalogo_de_trilhas_publicadas.sql` e
  `V21__cadastra_catalogo_inicial_da_trilha_tcu_ti.sql`;
- páginas frontend do módulo `modulos/trilhas`;
- testes de domínio e integração de trilhas.

## Materiais e registros de estudo

Comece por:

- `estudos/aplicacao/ServicoDeMateriaisEEstudos.java`
- módulo `evidencias`;
- registro de estudo e correção;
- módulo frontend `modulos/estudos`.

## Planejamento manual

Comece por:

- `planejamento/aplicacao/ServicoDePlanejamento.java`
- entidades do plano, disponibilidade, bloco e execução;
- páginas `PlanejamentoHojePagina.vue` e `PlanejamentoSemanaPagina.vue`;
- testes de conclusão, interrupção e edição.

## Geração determinística

Comece por:

- `planejamento/aplicacao/ServicoDeGeracaoDeterministica.java`
- `planejamento/dominio/ConfiguracaoDaGeracaoDeterministica.java`
- regras de prioridade;
- assinatura da prévia;
- testes de preservação de blocos manuais ou ajustados.

## Replanejamento

Comece por:

- `planejamento/aplicacao/ServicoDeReplanejamento.java`
- geração da prévia;
- pendências ignoradas;
- confirmações de limite;
- assinatura e aplicação.

## Prioridades

Comece por:

- módulo `priorizacao`;
- consulta de priorização;
- prioridade da matéria no plano;
- página `PriorizacaoDeTopicosPagina.vue`.

## Revisões

Comece por:

- módulo `revisoes`;
- `ConsultaDeRevisoesEspacadas`;
- integração da revisão com agenda e planejamento.

## Dashboard

Comece por:

- `dashboard/aplicacao/ConsultaDoDashboard`;
- consultas de cobertura, progresso e pendências;
- página inicial do frontend.

## Telegram e vínculo

Comece por:

- `automacao/aplicacao/ServicoDeVinculosDoTelegram.java`
- controlador confiável;
- domínio de vínculo e credencial;
- plugin OpenClaw `/conectar`;
- integrador de vínculos.

## MCP de consulta

Comece por:

- `automacao/infraestrutura/ConfiguracaoDoServidorMcp.java`
- `automacao/infraestrutura/ConfiguracaoDeSegurancaMcp.java`
- `automacao/infraestrutura/AutenticadorDeCredencialMcp.java`
- `automacao/infraestrutura/CatalogoDeFerramentasMcp.java`
- `automacao/aplicacao/ServicoDeConsultasMcp.java`
- `McpIntegracaoTest.java`.

## MCP de preparação e confirmação

Comece por:

- `automacao/aplicacao/ServicoDePreparacoesMcp.java`
- `automacao/aplicacao/ServicoDeOperacoesAssistidas.java`
- `automacao/aplicacao/ServicoDeAplicacaoDeOperacoesAssistidas.java`
- plugin `/confirmar`;
- endpoint confiável;
- testes de idempotência e concorrência.

## OpenClaw

Comece por:

- `infraestrutura/openclaw/README.md`
- `infraestrutura/openclaw/RUNBOOK.md`
- `infraestrutura/openclaw/compose.yaml`
- `infraestrutura/openclaw/modelos/openclaw.json`
- script ou plugin diretamente afetado.

## Alteração de banco

Comece por:

- última migration;
- entidade persistida;
- domínio correspondente;
- repositório;
- teste Testcontainers;
- validação completa do backend.

## Importação de edital

Comece por:

- módulo `importacaoedital`;
- `documentacao/decisoes/ADR-001-importacao-segura-de-editais.md`;
- `documentacao/agente/SEGURANCA-IMPORTACAO-EDITAL.md`;
- ferramenta MCP `preparar_importacao_completa_do_edital`;
- testes de staging, seleção de cargo, confirmação e rollback.
