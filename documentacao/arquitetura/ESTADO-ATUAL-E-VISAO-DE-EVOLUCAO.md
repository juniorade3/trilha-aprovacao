# Estado atual e visao de evolucao

## Estado atual

As Sprints 0 a 3 estao concluidas. A aplicacao usa PostgreSQL com migrations
Flyway e validacao do esquema, autenticacao por sessao com CSRF e dados
isolados pelo usuario autenticado.

O catalogo pessoal implementa Materia e TopicoDaMateria. Materias sao paginadas,
pesquisaveis e arquivaveis. Topicos formam uma arvore ordenada, com nome unico
entre irmaos, pai da mesma materia e prevencao de ciclos. O frontend oferece
lista, detalhe, formularios e estados de carregamento, vazio e erro.

Ainda nao estao implementados concursos, editais, cargos, provas, conteudo
programatico, materiais, registros de estudo, dashboard consolidado, OpenAPI e
CI. Esses itens pertencem as Sprints 4 a 8.

## Base alvo

A evolucao continua a partir da estrutura de concursos da Sprint 4. Cada etapa
deve reutilizar o catalogo existente sem copiar materias, topicos ou historico.

## Evolucao posterior

Depois da base funcional e validada, podem ser avaliados retificacoes de edital, notificacoes, questoes, planejamento e revisao espacada. Essas evolucoes precisam de novas decisoes de dominio e nao devem aparecer como classes vazias ou infraestrutura antecipada.
