# Lacunas e ranking por topico

## Objetivo

A priorizacao apresenta fatos objetivos sobre os topicos exigidos no contexto
oficial do usuario. Ela nao altera estudos, materiais, planos ou evidencias e
nao usa inteligencia artificial.

O contexto oficial e formado pelo concurso ativo, pelo cargo selecionado, pelo
edital principal e pelos mapeamentos confirmados entre itens do edital e
topicos pessoais ativos. Itens sem mapeamento aparecem como lacunas
estruturais, mas nao impedem a classificacao dos demais topicos.

## Classificacao

Os topicos sao classificados dentro de cada materia:

- `LACUNA`: `SEM_ESTUDO`, `SEM_EVIDENCIA`,
  `EVIDENCIA_DESATUALIZADA` ou `DADOS_INSUFICIENTES`;
- `FRAQUEZA`: `PRECISA_REFORCO` ou `DESEMPENHO_PARCIAL`;
- `CONSOLIDADO`: evidencia recente suficiente sem sinal mais grave.

A janela recente compreende os 30 dias civis terminados na data de referencia,
no fuso `America/Sao_Paulo`. O desempenho por questoes exige ao menos 20
questoes recentes: menos de 70% indica reforco, de 70% a menos de 85% indica
desempenho parcial e 85% ou mais permite consolidacao.

Uma revisao recente com recordacao 1 ou 2 indica reforco; recordacao 3 indica
resultado parcial; recordacao 4 ou 5 permite consolidacao quando nao existe
outro sinal pior. Um padrao de erro repetido continua sendo aquele presente em
duas evidencias ativas distintas e so influencia a prioridade quando possui
ocorrencia na janela recente.

Topicos sem material ativo continuam elegiveis e recebem um alerta factual.
Dificuldade percebida alta e exposta como justificativa e desempate, sem ser
tratada isoladamente como baixo desempenho.

Materias e topicos arquivados nao entram na projecao. Os empates sao resolvidos
por pior recordacao, menor percentual, mais padroes e erros, maior dificuldade,
evidencia mais antiga, maior incidencia no edital, ordem oficial, nome
normalizado e identificador. A posicao reinicia em cada grupo de cada materia.

## Interface HTTP

`GET /api/v1/priorizacao-de-topicos` recebe `dataDeReferencia` obrigatoria e
`identificadorDaMateria` opcional. A resposta contem o contexto oficial, o
resumo, os itens sem mapeamento e as materias com seus topicos ordenados,
indicadores e justificativas.

A operacao usa exclusivamente o usuario autenticado. A ausencia de concurso
ativo, cargo selecionado ou edital principal retorna
`422 CONTEXTO_DE_PRIORIZACAO_INCOMPLETO`.

Dashboard e Diagnostico por topico usam o mesmo cargo selecionado e o mesmo
edital principal ao identificar topicos exigidos. A consulta do ranking e
executada em transacao somente leitura com isolamento repetivel e nao produz
registros, snapshots ou pontuacoes.

## Limites desta entrega

Esta etapa e consultiva. Ela nao seleciona topicos para o plano, nao agenda
revisoes e nao persiste pontuacao ou snapshot. Essas capacidades pertencem as
etapas seguintes.
