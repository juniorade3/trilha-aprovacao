# Sprint 08 — Registro de implementação

STATUS: concluída e aprovada nas portas locais; publicação do PR e CI oficial pendentes.

ARQUIVOS ALTERADOS:
- páginas Hoje e Semana para tratamento consistente de conflitos `409` nas ações;
- testes Vue dos estados cancelado, dia sem blocos, erro, conflito e plano cancelado somente leitura;
- testes de integração para retomada de execução, início concorrente, isolamento A/B e rollback com Estudos;
- teste do contrato OpenAPI para todos os caminhos de execução do Planejamento;
- guias de operação local e Swagger para o módulo consolidado.

DECISÕES TOMADAS:
- nenhuma rota, entidade, tabela, migration ou regra de negócio foi criada;
- a correção funcional ficou restrita à recuperação explícita após conflito na interface;
- a consulta Hoje foi mantida porque busca plano, disponibilidade e blocos do intervalo necessário sem carregar o catálogo completo;
- as migrations `V6` a `V10`, índices e constraints existentes atenderam ao aceite em PostgreSQL vazio;
- scripts, perfis e capturas usados no aceite permaneceram temporários fora do repositório.

TESTES EXECUTADOS:
- `docker compose config`, `docker compose up -d` e `docker compose ps`: APROVADO; PostgreSQL saudável;
- `./mvnw test` e `./mvnw verify`: APROVADO; 83 testes, sem falhas, erros ou ignorados;
- `make testar-backend` e `make testar-frontend`: APROVADO; 83 testes backend e 76 frontend;
- `npm ci`: APROVADO;
- tipos, lint, Vitest, build, Prettier e `npm audit`: APROVADO; 76 testes e zero vulnerabilidades;
- `make verificar`: APROVADO para backend e frontend;
- `git diff --check`: APROVADO;
- PostgreSQL vazio: APROVADO; 10 migrations aplicadas até `v10` e Hibernate validado com `ddl-auto=validate`;
- testes de arquitetura, sessão, CSRF, isolamento A/B, concorrência, idempotência, rollback e OpenAPI: APROVADO;
- fluxo no navegador: APROVADO para disponibilidade, blocos, ordem, excesso `422`, ativação, Hoje, início, refresh, conclusão, interrupção, Histórico, correção, reagendamento, cancelamento, encerramento e preservação dos fatos;
- teclado e modal: APROVADO; foco inicial no diálogo, fechamento por `Escape` e retorno ao acionador;
- Hoje e Semana em 390, 768 e 1280 px: APROVADO, sem rolagem horizontal obrigatória;
- `/v3/api-docs`: APROVADO (`200`);
- `/swagger-ui.html`: APROVADO (`302` para `/swagger-ui/index.html`);
- `/swagger-ui/index.html`: APROVADO (`200`).

PENDÊNCIAS: abrir o PR, aguardar a CI oficial verde e mesclar na `main`.

PRÓXIMA SPRINT: iniciar apenas o planejamento do Motor de Evidências, mediante nova autorização.
