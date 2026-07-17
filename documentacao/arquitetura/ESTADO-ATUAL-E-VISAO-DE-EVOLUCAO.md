# Estado atual e visao de evolucao

## Estado observado antes da reconstrucao

O repositorio de trabalho possui uma base parcial anterior, descrita no README como persistencia em memoria e sem autenticacao, PostgreSQL, Flyway, OpenAPI ou dashboard analitico. Ela nao deve ser considerada a nova base sem uma decisao explicita no Sprint 0.

## Base alvo

A reconstrucao substitui repositorios temporarios por PostgreSQL/Flyway, introduz sessao HTTP com isolamento por usuario, expande a arvore de concurso e conecta frontend e backend com contratos REST documentados.

## Evolucao posterior

Depois da base funcional e validada, podem ser avaliados retificacoes de edital, notificacoes, questoes, planejamento e revisao espacada. Essas evolucoes precisam de novas decisoes de dominio e nao devem aparecer como classes vazias ou infraestrutura antecipada.
