# Sprint 06 — Registro de conclusão

STATUS: implementação concluída em branch empilhada; aguardando a CI oficial do PR.

ARQUIVOS ALTERADOS: domínio, aplicação, API, persistência, migration V9,
visão Hoje, cliente HTTP e testes.

DECISÕES TOMADAS:
- a ação de vínculo retorna sempre 200;
- o registro usa material nulo, início da execução e duração realizada;
- atividade livre continua válida sem estudo;
- bloco somente com matéria permite seleção de tópico;
- o planejamento usa `ServicoDeMateriaisEEstudos`, sem acessar repositórios de estudos;
- a Sprint 06 foi criada sobre a branch da Sprint 05 porque o PR anterior ainda não estava na main.

TESTES EXECUTADOS:
- backend `./mvnw --batch-mode --no-transfer-progress verify`: aprovado;
- frontend: tipos, lint, testes, build, formatação e auditoria: aprovados na execução isolada;
- CI oficial do PR: disparada pelo commit documental final.

PENDÊNCIAS: validação visual exploratória em 390, 768 e 1280 px.

PRÓXIMA SPRINT: Sprint 07 — Edição e replanejamento, não iniciada.
