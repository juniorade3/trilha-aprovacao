# Trilha da Aprovacao

Monorepo para organizar concursos, conteudos reutilizaveis, materiais e registros de estudo. Esta reconstrucao inicia sem dados de demonstracao no perfil local.

## Estado

Sprints 0 a 5 concluidas: fundacao do monorepo, PostgreSQL/Flyway,
autenticacao por sessao, catalogo pessoal de materias e topicos e estrutura de
concursos com conteudo programatico estao implementados. Itens oficiais formam
arvores sob as materias da prova e podem ser mapeados para topicos pessoais da
mesma materia, sem copiar o catalogo ou apagar seus registros ao remover o
vinculo.

A Sprint 6, materiais e historico de estudos, somente pode comecar apos
autorizacao explicita.

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

## Verificacao

```bash
docker compose config
make verificar
```
