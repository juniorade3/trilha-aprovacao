# Trilha da Aprovacao

Aplicacao para organizar materias, topicos reutilizaveis, exigencias de editais e sessoes de estudo. A primeira fatia funcional conecta frontend, API REST, casos de uso, dominio e repositorios temporarios em memoria.

## Tecnologias

- Java 21, Spring Boot 4.1 e Maven;
- Vue 3, TypeScript, Vite, Vue Router e Pinia;
- Bootstrap 5.3, Bootstrap Icons e Sass;
- Vitest, ESLint e Prettier;
- monorepo com backend organizado como monolito modular.

## Estrutura do monorepo

```text
aplicativos/       Aplicacoes backend e frontend
pacotes/           Pacotes compartilhados futuros
infraestrutura/    Reservas para recursos locais e implantacao futura
documentacao/      Arquitetura, decisoes, diagramas e operacao
scripts/           Automacoes futuras do repositorio
.github/workflows/ Fluxos de integracao futura
```

## Pre-requisitos

- Java 21;
- Maven 3.6.3 ou superior apenas para manutencao do wrapper;
- Node.js 24 LTS;
- npm 11 ou superior;
- `make`.

## Instalacao

```bash
make instalar
```

## Execucao

Em terminais separados:

```bash
make executar-backend
make executar-frontend
```

O backend atende em `http://localhost:8080` e o frontend em `http://localhost:5173`.

O Vite encaminha requisicoes relativas a `/api` para o backend. Quando a porta padrao estiver ocupada, e possivel iniciar o backend em outra porta e informar temporariamente o alvo do proxy:

```bash
./aplicativos/backend/mvnw -f aplicativos/backend/pom.xml spring-boot:run -Dspring-boot.run.arguments=--server.port=18080
cd aplicativos/frontend
VITE_ALVO_API=http://localhost:18080 npm run dev
```

## Fluxo funcional

Pelo navegador, e possivel:

1. cadastrar uma materia e seus topicos reutilizaveis;
2. cadastrar um edital e adicionar uma materia existente;
3. preservar a descricao original dos itens do edital;
4. mapear cada item para topicos da mesma materia;
5. registrar uma sessao de estudo por topico;
6. consultar o historico do mais recente para o mais antigo;
7. visualizar itens e topicos estudados ou pendentes em cada edital.

Um estudo pertence ao topico reutilizavel. Portanto, o mesmo registro e considerado em todos os editais que mapeiam aquele topico, sem copia ou transferencia.

## Persistencia temporaria

Os repositorios desta etapa mantem dados somente na memoria do backend. Todos os cadastros sao perdidos quando o processo Java e reiniciado. Nao ha banco, arquivo de dados ou `localStorage` substituindo o backend.

## Frontend

O tema e as variaveis visuais ficam centralizados em `src/estilos/principal.scss`. Bootstrap Icons e os estilos globais sao carregados uma vez pela inicializacao da aplicacao; o JavaScript do Bootstrap nao e importado globalmente.

Componentes compartilhados padronizam navegacao, cabecalhos, campos, selecoes, botoes, alertas, carregamento, estados vazios e resumos. As paginas permanecem organizadas por funcionalidade nos modulos `inicio`, `materias`, `editais` e `estudos`.

## Testes e verificacoes

```bash
make testar
make verificar
```

Tambem e possivel executar diretamente:

```bash
cd aplicativos/backend && ./mvnw test && ./mvnw verify
cd aplicativos/frontend && npm run test && npm run build && npm run lint
```

Verificacoes completas do frontend:

```bash
cd aplicativos/frontend
npm audit
npm run verificar-tipos
npm run lint
npm run test
npm run build
npm run verificar-formatacao
```

## Cenario de validacao

O cenario principal usa a materia `Direito Constitucional`, o topico `Direitos fundamentais`, os editais `Concurso A` e `Concurso B` e um unico registro de 60 minutos. Depois que itens dos dois editais sao mapeados ao mesmo topico, ambos apresentam o topico como estudado e o historico continua contendo apenas um registro.

## Nomenclatura

Todo identificador criado para o projeto deve estar em portugues e sem acentos. Nomes externos de bibliotecas, frameworks e protocolos permanecem conforme suas especificacoes.

## Ainda nao implementado

- persistencia definitiva e banco de dados;
- autenticacao e autorizacao;
- contratos OpenAPI e cliente gerado;
- MCP, OpenClaw, mensageria ou cache distribuido;
- infraestrutura de producao e implantacao;
- planejamento, revisoes, questoes, desempenho e dashboard analitico;
- edicao ou exclusao de registros de estudo.
# trilha-aprovacao
