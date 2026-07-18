# Trilha da Aprovacao

Monorepo para organizar concursos, conteudos reutilizaveis, materiais e registros de estudo. Esta reconstrucao inicia sem dados de demonstracao no perfil local.

## Estado

Sprints 0 a 4 concluidas: fundacao do monorepo, PostgreSQL/Flyway,
autenticacao por sessao, catalogo pessoal de materias e topicos e estrutura de
concursos estao implementados. A estrutura permite cadastrar gradualmente
Concurso, Edital, Cargo, Prova, Grupo de Conteudo e vincular materias do
catalogo, sempre com isolamento por usuario e interface autenticada.

A Sprint 5, conteudo programatico e mapeamentos, somente pode comecar apos
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
