# Arquitetura inicial

## Estilo

O sistema e um monolito modular. O backend Java organiza as capacidades
`autenticacao`, `concursos`, `conteudoprogramatico`, `conteudos`, `estudos`,
`dashboard` e `compartilhado`. Em cada capacidade, somente quando necessario,
sao usadas as camadas `dominio`, `aplicacao`, `infraestrutura` e `api`.

```text
Vue 3 -- HTTP/JSON + sessao/CSRF --> API MVC --> casos de uso --> dominio
                                             |                    |
                                             +--> infraestrutura --+--> PostgreSQL
```

O dominio nao depende de Spring, JPA, HTTP, JSON, PostgreSQL ou Vue. Controllers e componentes Vue coordenam entrada/saida; regras pertencem ao dominio ou aos casos de uso. A API usa DTOs e nunca expoe entidades JPA.

Essas fronteiras sao verificadas por ArchUnit. Consultas agregadas de leitura
podem usar JDBC na camada de aplicacao, mas recebem sempre o identificador do
usuario autenticado e nao alteram o dominio.

## Execucao local

O frontend acessa `/api` via proxy Vite, configurado por `VITE_ALVO_API`. O backend usa PostgreSQL do Compose e Flyway; Hibernate somente valida o esquema. A sessao HTTP e o token CSRF protegem operacoes autenticadas.

## Consequencias

Esta escolha prioriza consistencia transacional, simplicidade operacional e testes integrados com PostgreSQL real. Microsservicos, filas, cache distribuido, CQRS, event sourcing, Kafka, Redis e Kubernetes nao fazem parte desta base.
