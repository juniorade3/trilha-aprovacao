# Trilha da Aprovacao

Monorepo para organizar concursos, conteudos reutilizaveis, materiais e registros de estudo. Esta reconstrucao inicia sem dados de demonstracao no perfil local.

## Estado

Sprints 0 a 8 concluidas: fundacao do monorepo, PostgreSQL/Flyway,
autenticacao por sessao, catalogo pessoal de materias e topicos e estrutura de
concursos com conteudo programatico estao implementados. Itens oficiais formam
arvores sob as materias da prova e podem ser mapeados para topicos pessoais da
mesma materia, sem copiar o catalogo ou apagar seus registros ao remover o
vinculo. Materiais de estudo cobrem um ou mais topicos, e registros de estudo
podem ser corrigidos ou cancelados com preservacao integral do historico. O
dashboard do concurso ativo deriva medidas objetivas desses fatos e apresenta
provas, cobertura de estudos, atividade recente e pendencias estruturais.
O Planejamento Manual e a Geracao Deterministica estao implementados. A geracao
permite definir prioridades, calcular uma previa explicavel, aplicar blocos em
rascunho e regenerar preservando blocos manuais ou ajustados.
O contrato OpenAPI documenta sessao, CSRF, grupos e erros; Swagger fica
disponivel localmente, o perfil de producao o desabilita por padrao e a CI
executa as mesmas portas de qualidade sem realizar deploy.

## Pre-requisitos

- Java 21;
- Node.js 24 LTS e npm;
- Docker com Compose;
- `curl` e `jar` (fornecido pelo JDK).

O backend inclui `./mvnw`, que baixa Maven localmente em `.mvn/` quando necessario; Maven global nao e requerido.

## Execucao local

```bash
cp .env.example .env
make infra-subir
make backend-executar
make frontend-executar
```

O health check do backend fica em `http://localhost:8080/actuator/health`. O frontend inicia em `http://localhost:5173` e encaminha `/api` ao backend.
Swagger fica em `http://localhost:8080/swagger-ui.html`.

## Verificacao

```bash
docker compose config
make verificar
```

## Documentacao

- [Operacao local](documentacao/operacao/README.md)
- [Swagger, sessao e CSRF](documentacao/api/COMO-USAR-O-SWAGGER.md)
- [Arquitetura](documentacao/arquitetura/ARQUITETURA-INICIAL.md)
- [Modelo de dominio](documentacao/arquitetura/MODELO-DE-DOMINIO.md)
- [Diagramas](documentacao/diagramas/README.md)
- [Decisoes arquiteturais](documentacao/decisoes/)
- [Validacao e aceite](documentacao/planejamento/VALIDACAO-E-ACEITE.md)

O repositorio nao inclui deploy nem cliente gerado da OpenAPI. O frontend usa
um cliente `fetch` pequeno, relativo a `/api`, com sessao, CSRF, erros
padronizados e cancelamento por `AbortController`.
