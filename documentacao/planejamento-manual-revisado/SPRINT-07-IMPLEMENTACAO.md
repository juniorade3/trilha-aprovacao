# Sprint 07 — Edição e replanejamento

## Objetivo

Permitir corrigir e reorganizar o planejamento depois da ativação, sem apagar o histórico já executado e sem quebrar a rastreabilidade entre bloco, execução e registro de estudo.

## Estado inicial

- Sprint 05 mesclada na `main`.
- Sprint 06 mesclada na `main`.
- Execuções concluídas podem possuir vínculo único com `RegistroDeEstudo`.
- Blocos planejados ainda são editáveis apenas enquanto o plano está em rascunho.

## Escopo proposto

1. Permitir editar blocos ainda não iniciados de um plano ativo.
2. Permitir reagendar blocos planejados e atrasados para outro dia válido.
3. Reordenar os blocos do dia após edição ou reagendamento.
4. Impedir alteração estrutural de bloco em andamento ou já finalizado.
5. Preservar execução e registro de estudo de blocos concluídos ou parcialmente concluídos.
6. Permitir cancelar bloco não iniciado, mantendo rastreabilidade em vez de exclusão física quando o plano estiver ativo.
7. Atualizar as visões `Hoje` e `Semana` imediatamente após as alterações.
8. Garantir isolamento por usuário, controle transacional e respostas idempotentes quando aplicável.

## Fora do escopo

- editar duração, observação ou resultado de uma execução já encerrada;
- excluir ou alterar automaticamente um `RegistroDeEstudo` já criado;
- planejamento automático por inteligência artificial;
- recorrência de blocos;
- pausa e retomada de cronômetro.

## Regras iniciais

- plano em rascunho mantém o CRUD atual;
- plano ativo permite alterar apenas blocos no estado `PLANEJADO`;
- bloco `EM_ANDAMENTO`, `CONCLUIDO`, `PARCIALMENTE_CONCLUIDO` ou `CANCELADO` não pode ser movido nem ter conteúdo alterado;
- a nova data deve pertencer à semana do plano;
- matéria e tópico devem continuar pertencendo ao usuário e estar ativos;
- a ordem dos blocos deve permanecer contínua em cada dia;
- reagendamento não cria uma nova execução nem um novo registro de estudo;
- cancelamento de bloco planejado em plano ativo muda seu estado para `CANCELADO` e o remove das filas executáveis.

## Primeira fatia segura

Implementar no backend a edição e o reagendamento de blocos `PLANEJADO` em plano `ATIVO`, reutilizando as validações existentes e acrescentando testes de domínio e integração para:

- edição válida;
- mudança de dia;
- normalização da ordem nos dois dias;
- bloqueio de bloco iniciado ou finalizado;
- isolamento entre usuários;
- preservação das execuções e registros existentes.

## Critérios de conclusão

- backend, migrations e arquitetura aprovados na CI;
- frontend com tipos, lint, testes, build, formatação e auditoria aprovados;
- testes integrados com PostgreSQL cobrindo as regras centrais;
- Swagger atualizado;
- validação visual das telas `Hoje` e `Semana` em 390, 768 e 1280 px;
- nenhuma alteração direta em dados de execução ou histórico já consolidados.

## Status

Sprint iniciada. Branch criada: `feature/planejamento-sprint-07`.
