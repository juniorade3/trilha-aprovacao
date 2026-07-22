# Selecao automatica e integracao semanal

## Escopo

A geracao deterministica semanal usa o ranking consultivo de topicos e a
agenda de revisoes espacadas como projecoes dos dados existentes. A entrega nao
cria migration nem persiste estado duplicado de ranking ou agenda; a ultima
migration permanece a V15.

Somente topicos exigidos no contexto oficial e pertencentes a materias
incluidas no plano participam da geracao. Materias marcadas como
`NAO_INCLUIR` sao eliminadas. Se nenhum topico estiver elegivel, a API responde
`422 SEM_TOPICOS_ELEGIVEIS_PARA_GERACAO`; nao existe fallback para um bloco sem
topico.

## Selecao dos blocos principais

A escolha ponderada de materias continua usando prioridade, carga acumulada,
alternancia, capacidade e o limite de tres materias principais por dia. Dentro
da materia escolhida, a selecao usa a ordem deterministica do ranking.

As ocorrencias oficiais da materia alternam entre `LACUNA` e `FRAQUEZA`. Quando
o grupo esperado esta vazio, usa-se o outro grupo e, por ultimo,
`CONSOLIDADO`. Blocos preservados com topico oficial participam do estado da
alternancia.

Um topico nunca estudado gera atividade `TEORIA`. Os demais geram
`QUESTOES`. O bloco persiste materia, topico, tipo, grupo, faixa e as
justificativas da escolha.

## Revisoes especificas

Antes dos blocos principais, a geracao tenta reservar ate tres revisoes
especificas de 20 minutos por dia. Elas consomem capacidade, mas nao contam
para o limite de tres materias principais. A ordem considera data devida,
etapa, recordacao, ordem oficial e identificador.

Uma revisao nunca e dividida nem antecipada. Topicos com bloco de revisao
aberto em plano rascunho ou ativo do usuario nao sao duplicados. A geracao
tambem evita colocar revisao e bloco principal do mesmo topico no mesmo dia.
O replanejamento aplica a mesma excecao das revisoes ao limite de materias.

## Previa, aplicacao e concorrencia

`POST /api/v1/planos-semanais/{id}/geracao-deterministica/previa` recebe
`dataDeReferencia` e `duracaoDoBlocoPrincipalEmMinutos`. A resposta inclui a
proposta detalhada e `assinaturaDaPrevia`; a consulta nao persiste alteracoes.

`POST /api/v1/planos-semanais/{id}/geracao-deterministica` recebe os mesmos
dados, `substituirBlocosGerados` e a assinatura confirmada. A aplicacao bloqueia
o plano, recalcula em transacao serializavel e somente persiste quando a
assinatura continua equivalente. Alteracao concorrente ou mudanca relevante
retorna `409 PREVIA_DA_GERACAO_DESATUALIZADA`.

Na regeneracao, somente blocos puramente gerados na data de referencia ou
depois dela podem ser substituidos. Blocos anteriores e blocos manuais ou
ajustados permanecem preservados.

## Interface

A gaveta mostra topico, tipo, grupo, faixa e justificativas da proposta. A
duracao generica de revisao foi removida, pois revisoes espacadas sempre usam
20 minutos. Se a previa ficar desatualizada, a interface invalida a confirmacao,
recarrega prioridades, recalcula e exige nova confirmacao; nenhuma proposta
nova e aplicada silenciosamente.

O fluxo preserva a semana da URL e o retorno de foco. Erros recuperaveis
mantem a aplicacao bloqueada ate existir uma previa valida.

## Limites

Planos ativos existentes nao sao reescritos automaticamente. Esta entrega nao
adiciona IA, Timefold, pesos configuraveis, biblioteca de graficos, Pinia novo,
adiamento manual de revisao ou novo estado persistido da agenda.
