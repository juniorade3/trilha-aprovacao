# Sprint 07 — Registro de implementação

STATUS: concluída e aprovada nas portas locais; o workflow oficial continua sendo a porta de merge.

ARQUIVOS ALTERADOS:
- domínio e aplicação de planejamento para reagendamento, cancelamento, correção e estados finais;
- API, DTOs, OpenAPI, persistência JPA e migration `V10__adiciona_reagendamento_aos_blocos.sql`;
- páginas Hoje e Semana, cliente de planejamento, editor de bloco e estilos responsivos;
- testes de domínio, integração, API, OpenAPI e componentes Vue.

DECISÕES TOMADAS:
- edição comum de plano ativo preserva data e ordem; mudanças de posição usam a ação explícita de reagendamento;
- somente blocos planejados participam da normalização de ordem após reagendamento ou cancelamento;
- correção de execução sincroniza o estado do bloco e, quando integrado, substitui o vínculo pelo novo estudo ativo;
- planos encerrados e cancelados têm estados próprios em Hoje e permanecem somente leitura;
- encerramento e cancelamentos usam `ModalDaAplicacao`, sem `window.confirm`;
- ações do plano foram reorganizadas no celular para permanecerem integralmente acessíveis.

TESTES EXECUTADOS:
- `make testar-backend`: 81 testes aprovados;
- `make testar-frontend`: 72 testes aprovados;
- `make verificar-backend`: aprovado, incluindo build do artefato;
- `make verificar-frontend`: tipos, lint, 72 testes, build, formatação e auditoria aprovados; zero vulnerabilidades;
- `git diff --check`: aprovado;
- PostgreSQL/Flyway: 10 migrations validadas e schema levado à V10 com `ddl-auto=validate`;
- OpenAPI local: confirmadas as rotas de reagendamento e cancelamento de bloco, correção de execução e encerramento e cancelamento de plano;
- navegador autenticado contra API e PostgreSQL atuais: Hoje e Semana validados em 390, 768 e 1280 px, sem rolagem horizontal; ações móveis dentro do viewport.

PENDÊNCIAS: nenhuma pendência funcional da Sprint 07; merge condicionado ao CI oficial verde.

PRÓXIMA SPRINT: Sprint 08 — Consolidação e aceite, não iniciada.
