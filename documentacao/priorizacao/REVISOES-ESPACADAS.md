# Revisoes espacadas

## Objetivo

A agenda de revisoes espacadas sera uma projecao deterministica das evidencias
de aprendizagem ja registradas. Ela nao criara tabela, migration ou estado
persistido adicional e abrangera somente topicos exigidos no contexto oficial
do usuario.

O contexto oficial permanece formado pelo concurso ativo, pelo cargo
selecionado, pelo edital principal e pelos mapeamentos confirmados. Materias e
topicos arquivados nao participam da agenda.

## Inicio e progressao

A primeira evidencia ativa de um topico inicia sua agenda na etapa zero.
Estudos antigos sem evidencia nao originam datas artificiais. Todas as
evidencias historicas existentes desde a V15 participam do calculo quando seus
registros de estudo permanecem ativos.

As etapas usam os intervalos de 1, 3, 7, 14, 30 e 60 dias. Depois de uma
revisao concluida, o nivel de recordacao altera a etapa da seguinte forma:

- recordacao 1: retorna para a etapa zero;
- recordacao 2: recua uma etapa;
- recordacao 3: mantem a etapa;
- recordacao 4: avanca uma etapa;
- recordacao 5: avanca duas etapas.

A etapa permanece limitada entre zero e cinco. A proxima data e calculada a
partir da data real da revisao. Depois que a agenda foi iniciada, evidencias de
outros tipos de estudo nao alteram sua etapa.

No maximo uma revisao do mesmo topico por dia altera a progressao. Quando
existirem varias revisoes ativas no dia, prevalece a ultima por horario e, em
caso de empate, por identificador. Correcoes e cancelamentos provocam novo
calculo usando somente fatos ativos. Uma interrupcao sem evidencia nao avanca
a etapa.

## Interface HTTP

`GET /api/v1/revisoes-espacadas` recebera `dataDeReferencia` e `ate`. A consulta
usara exclusivamente o usuario autenticado e nao produzira escrita.

Cada item retornara a etapa atual, o intervalo em dias, a data devida, os dias
em atraso, a ultima revisao, a situacao e eventual bloco aberto. As situacoes
possiveis serao `VENCIDA`, `DEVIDA_HOJE`, `FUTURA` e `JA_PLANEJADA`.

A ordenacao sera deterministica. O contrato OpenAPI documentara os DTOs, os
parametros, a seguranca por sessao e as respostas aplicaveis.

## Interface Hoje

A visao Hoje apresentara a fila `Revisoes de hoje`. Quando houver bloco aberto
para o topico, a acao direcionara para esse bloco e preservara o fluxo de foco.
Sem bloco aberto, a acao reutilizara o registro rapido preenchido com o topico
e o tipo `REVISAO`.

A interface devera cobrir carregamento, lista vazia, sessao expirada e erro de
rede com repeticao recuperavel, alem de navegacao por teclado e foco.

## Integracao com a geracao semanal

A Sprint 03 de priorizacao passou a consumir esta agenda na geracao semanal.
As revisoes devidas sao blocos especificos de 20 minutos, reservados antes dos
blocos principais, sem antecipacao ou divisao e sem estado persistido duplicado.
As regras completas estao em
`SELECAO-AUTOMATICA-E-INTEGRACAO-SEMANAL.md`.

## Limites desta entrega

A agenda nao adia, dispensa nem suspende revisoes manualmente e continua sem
persistir uma copia de seu estado calculado.
