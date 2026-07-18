# Estado atual e visao de evolucao

## Estado atual

As Sprints 0 a 6 estao concluidas. A aplicacao usa PostgreSQL com migrations
Flyway e validacao do esquema, autenticacao por sessao com CSRF e dados
isolados pelo usuario autenticado.

O catalogo pessoal implementa Materia e TopicoDaMateria. Materias sao paginadas,
pesquisaveis e arquivaveis. Topicos formam uma arvore ordenada, com nome unico
entre irmaos, pai da mesma materia e prevencao de ciclos. O frontend oferece
lista, detalhe, formularios e estados de carregamento, vazio e erro.

A estrutura de concursos implementa Concurso, Edital, CargoDoConcurso, Prova,
GrupoDeConteudo e MateriaDaProva. O cadastro e gradual, permite um concurso
ativo por usuario, um edital principal e um cargo selecionado por concurso e
reutiliza Materia por referencia. Concursos arquivados bloqueiam alteracoes de
conteudo, e exclusoes fisicas com dependencias sao recusadas.

O conteudo programatico implementa ItemDoEdital em arvore por MateriaDaProva,
preserva a redacao recebida e impede pais de outra materia, outro edital e
ciclos. MapeamentoDeItemDoEdital liga manualmente o item a um TopicoDaMateria
compativel e confirmado. Excluir esse mapeamento remove somente o vinculo.

MaterialDeEstudo forma a biblioteca pessoal de aulas, PDFs e outras fontes.
CoberturaDeTopicoPorMaterial associa cada material aos topicos que ele atende.
RegistroDeEstudo pertence ao topico e aceita material somente quando existe a
cobertura. Correcao encerra o registro original e cria um registro ativo ligado
a ele; cancelamento altera a situacao sem exclusao fisica.

Ainda nao estao implementados dashboard consolidado, OpenAPI e CI. Esses itens
pertencem as Sprints 7 e 8.

## Base alvo

A evolucao continua a partir do dashboard objetivo da Sprint 7.
Cada etapa deve reutilizar o catalogo, a estrutura de concursos e os mapeamentos
existentes sem copiar materias, topicos ou historico.

## Evolucao posterior

Depois da base funcional e validada, podem ser avaliados retificacoes de edital, notificacoes, questoes, planejamento e revisao espacada. Essas evolucoes precisam de novas decisoes de dominio e nao devem aparecer como classes vazias ou infraestrutura antecipada.
