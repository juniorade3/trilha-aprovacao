# Planejamento 3 — Replanejamento deterministico

## Escopo entregue

O plano ativo pode calcular e aplicar transferencias de pendencias somente entre a
data de referencia e o domingo da mesma semana. A previa nao escreve no banco. A
aplicacao bloqueia o estado relevante, recalcula e compara a assinatura antes de
criar novos blocos em uma unica transacao.

Pendencias concluidas, canceladas, em andamento, futuras ou ja transferidas nao sao
selecionadas. Execucao parcial usa `max(0, minutos previstos - minutos executados)`.
O bloco e a execucao originais permanecem intactos.

O distribuidor deterministico compartilhado centraliza duracao minima de 25 minutos,
capacidade e limite de tres materias. Revisoes e atividades livres consomem tempo;
atividade livre nao conta como materia. Uma divisao so e proposta quando todos os
fragmentos cabem, cada um com ao menos 25 minutos. Uma pendencia inteira menor que
25 minutos continua permitida.

Tres reagendamentos exigem confirmacao individual. Acima de tres, a API devolve
`DECIDIR_MANUALMENTE`; nao existe cancelamento automatico.

## Persistencia e compatibilidade

A migration `V13__adiciona_replanejamento_aos_planos.sql` cria:

- snapshot imutavel dos blocos no momento da ativacao;
- cabecalho, itens e fragmentos de replanejamento;
- unicidade de transferencia por instancia de bloco;
- origens `REPLANEJADO` e `REPLANEJADO_AJUSTADO_MANUALMENTE`;
- justificativa especifica no bloco criado.

Para planos que ja estavam ativos, encerrados ou cancelados na instalacao da V13, o
snapshot representa o estado encontrado na migracao. Alteracoes anteriores a V13 nao
podem ser reconstruidas.

## Contratos

- `POST /api/v1/planos-semanais/{id}/replanejamento/previa`
- `POST /api/v1/planos-semanais/{id}/replanejamento`
- `GET /api/v1/planos-semanais/{id}/historico-semanal?dataDeReferencia=...`

As tres rotas usam exclusivamente o usuario autenticado. Divergencia entre previa e
aplicacao retorna `409 PREVIA_DE_REPLANEJAMENTO_DESATUALIZADA`.

Na visao Semana, a gaveta permite remover pendencias com novo calculo no servidor,
confirmar o terceiro reagendamento e revisar a confirmacao final. O historico objetivo
e exibido na propria pagina, inclusive ao navegar para semanas encerradas ou
canceladas, preservando `?inicio=...`.
