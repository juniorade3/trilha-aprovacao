# Motor de Evidencias e Diagnostico por Topico

## Escopo implementado

A capacidade `evidencias` registra resultados objetivos de estudos manuais e
execucoes do planejamento. Ela nao altera a geracao deterministica, nao escolhe
topicos e nao atribui ranking.

Cada `RegistroDeEstudo` possui um `TipoDeEstudo`. Uma evidencia opcional e
imutavel pode guardar questoes, acertos, recordacao, dificuldade e ocorrencias
de padroes de erro. Erros sao calculados como questoes menos acertos. Correcao
cria um novo registro e uma nova evidencia; cancelamento e correcao preservam os
fatos anteriores, que deixam de participar dos indicadores.

Atividades concluidas de questoes, simulado e caderno de erros exigem questoes
e acertos. Revisoes concluidas exigem recordacao. Interrupcoes podem omitir o
resultado. Blocos sem topico podem ser encerrados, mas nao produzem diagnostico;
quando um bloco de materia exige evidencia, o topico precisa ser selecionado.

## Consulta

`GET /api/v1/evidencias/diagnostico-de-topicos` exige `dataDeReferencia` e
aceita materia e edital confirmado como filtros. A janela recente vai de
`dataDeReferencia - 29 dias` ate a propria referencia, inclusive, no fuso
`America/Sao_Paulo`. Todos os topicos pessoais ativos aparecem mesmo sem
evidencia.

`GET /api/v1/evidencias/padroes-de-erro` sugere descricoes ja utilizadas no
topico do usuario autenticado. Um padrao e repetido somente quando aparece em
duas ou mais evidencias ativas distintas do mesmo topico.

As duas agregacoes do diagnostico usam o mesmo snapshot transacional. O
cadastro de um padrao novo usa insercao idempotente pela chave normalizada, de
modo que registros concorrentes reutilizam o mesmo padrao sem deixar evidencia
parcial.

## Migracao

A V15 recupera o tipo dos registros vinculados a blocos e o propaga para
ancestrais e descendentes da cadeia de correcoes. Os demais registros anteriores
sao classificados como `OUTRA`. Nenhuma evidencia retroativa e inventada.
As FKs nao usam cascata destrutiva, e as unicidades impedem evidencia duplicada,
padrao normalizado duplicado e repeticao do mesmo padrao numa evidencia.
