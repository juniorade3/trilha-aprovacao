# Validacao e aceite

## Comandos por porta

Infraestrutura:

```bash
docker compose config
docker compose up -d
docker compose ps
```

Backend:

```bash
cd aplicativos/backend
./mvnw test
./mvnw verify
```

Frontend:

```bash
cd aplicativos/frontend
npm run verificar-tipos
npm run lint
npm run test
npm run build
npm run verificar-formatacao
npm audit
```

Integrado:

```bash
make verificar
```

Os nomes finais dos alvos do Makefile devem corresponder aos requeridos pela especificacao: `ajuda`, `infra-subir`, `infra-parar`, `backend-executar`, `frontend-executar`, `testar-backend`, `testar-frontend`, `verificar-backend`, `verificar-frontend`, `verificar` e `limpar`.

## Cenários obrigatorios

1. Usuario A cria dados e Usuario B recebe 404/403 ao tentar le-los ou alterá-los.
2. O mesmo usuario nao consegue ativar dois concursos nem selecionar dois cargos no mesmo concurso.
3. Uma Materia e seu Topico sao reutilizados em dois concursos sem copiar o registro de estudo.
4. Item sem mapeamento nao aparece como estudado; material incompativel e duracao extrema retornam erro de negocio, nunca 500.
5. Uma base PostgreSQL vazia sobe todas as migrations; `/v3/api-docs` responde e apresenta as capacidades principais.
6. Fluxo completo: conta, login, materia/topico/material/cobertura, Concurso A, edital/cargo/prova/grupo/materia/item/mapa, estudo de 60 minutos, dashboard, Concurso B reutilizando o topico, logout e bloqueio de rotas.
7. As telas principais funcionam em 390, 768 e 1280 px, com navegacao por teclado e mensagens anunciadas.

## Evidencias por sprint

Para cada sprint, registrar no PR ou no log de execucao: commit local, comandos executados, resultado, versoes relevantes, migrations criadas, testes novos, capturas apenas quando ajudarem a provar o comportamento e divergencias conhecidas. Falhas devem bloquear o proximo sprint ou ser explicitamente autorizadas como excecao.

### Sprint 3 — executada em 18/07/2026

- `make verificar`: aprovado;
- backend: 14 testes aprovados, incluindo dominio, API, seguranca e PostgreSQL
  real com Testcontainers;
- frontend: 16 testes aprovados, verificacao de tipos, lint, build e formatacao;
- `npm audit`: nenhuma vulnerabilidade encontrada;
- migration `V2__cria_materias_e_topicos.sql`: aplicada do esquema v1 ao v2 e
  validada pelo Hibernate com `ddl-auto=validate`;
- fluxo HTTP real: health `UP`, criacao de materia, topico raiz e filho,
  duplicidade `409`, ciclo `422`, bloqueio de materia arquivada `422` e
  isolamento A-versus-B com `404`;
- frontend real: Vite respondeu a rota `/materias` e encaminhou `/api` ao
  backend;
- dados sinteticos removidos ao final e processos temporarios encerrados;
- divergencias conhecidas da Sprint 3: nenhuma.

### Sprint 4 — executada em 18/07/2026

- `make verificar`: aprovado;
- backend: 23 testes aprovados, incluindo dominio, regras de exclusividade,
  concorrencia otimista, seguranca e API sobre PostgreSQL real com
  Testcontainers;
- frontend: 22 testes aprovados, verificacao de tipos, lint, build e formatacao;
- `npm audit`: nenhuma vulnerabilidade encontrada;
- migration `V3__cria_estrutura_de_concursos.sql`: aplicada do esquema v2 ao v3
  e validada pelo Hibernate com `ddl-auto=validate`;
- fluxo HTTP real: health `UP`, arvore completa de Concurso, Edital, Cargo,
  Prova, Grupo de Conteudo e MateriaDaProva, com troca de concurso ativo, edital
  principal e cargo selecionado;
- isolamento A-versus-B: leitura de concurso e vinculacao de materia retornaram
  `404`;
- concurso arquivado: tentativa de criar conteudo retornou `422`;
- frontend real: Vite respondeu a rota `/concursos` e encaminhou `/api` ao
  backend;
- dados sinteticos removidos ao final, migration atual confirmada como v3,
  processos temporarios encerrados e portas liberadas;
- divergencias conhecidas da Sprint 4: nenhuma.

### Sprint 5 — executada em 18/07/2026

- `make verificar`: aprovado;
- backend: 30 testes aprovados, incluindo dominio, API, seguranca, isolamento,
  arvore sem ciclos e PostgreSQL real com Testcontainers;
- frontend: 23 testes aprovados, verificacao de tipos, lint, build e formatacao;
- `npm audit`: nenhuma vulnerabilidade encontrada;
- migration `V4__cria_conteudo_programatico_e_mapeamentos.sql`: aplicada do
  esquema v3 ao v4, aplicada tambem em bancos vazios e validada pelo Hibernate
  com `ddl-auto=validate`;
- fluxo HTTP real: item raiz e filho criados, tentativa de ciclo recusada com
  `422`, redacao original preservada e mapeamento manual confirmado;
- isolamento A-versus-B: item e lista de mapeamentos retornaram `404`;
- remocao do mapeamento: item e topico continuaram respondendo `200`, enquanto a
  lista de vinculos passou a ter zero elementos;
- concurso arquivado: tentativa de mapear um item retornou `422`;
- frontend real: Vite respondeu `/concursos` e encaminhou uma chamada `/api` ao
  backend, ambos com `200`;
- dados sinteticos removidos ao final, migration atual confirmada como v4,
  processos temporarios encerrados e portas liberadas;
- divergencias conhecidas da Sprint 5: nenhuma.

### Sprint 6 — executada em 18/07/2026

- `make verificar`: aprovado;
- backend: 38 testes aprovados, incluindo dominio, API, rastreabilidade,
  isolamento e PostgreSQL real com Testcontainers;
- frontend: 27 testes aprovados, verificacao de tipos, lint, build e
  formatacao;
- `npm audit`: nenhuma vulnerabilidade encontrada;
- migration `V5__cria_materiais_coberturas_e_estudos.sql`: aplicada do esquema
  v4 ao v5, aplicada tambem em bancos vazios e validada pelo Hibernate com
  `ddl-auto=validate`;
- fluxo HTTP real: material e cobertura criados, estudo de 60 minutos
  registrado, correcao para 75 minutos mantendo o original como `CORRIGIDO` e
  cancelamento do novo registro como `CANCELADO`;
- historico real: dois registros preservados depois da correcao;
- isolamento A-versus-B: leitura do material de outro usuario retornou `404`;
- reutilizacao entre concursos: teste de integracao confirmou dois itens
  oficiais mapeados ao mesmo topico e somente um registro de estudo ativo;
- frontend real: Vite respondeu `/materiais` e `/estudos`, e encaminhou `/api`,
  todos com `200`;
- dados sinteticos removidos ao final, migration atual confirmada como v5,
  processos temporarios encerrados e portas liberadas;
- divergencias conhecidas da Sprint 6: nenhuma.

### Sprint 7 — executada em 18/07/2026

- `make verificar`: aprovado;
- backend: 41 testes aprovados, incluindo agregacao do dashboard, alertas,
  isolamento e PostgreSQL real com Testcontainers;
- frontend: 29 testes aprovados, verificacao de tipos, lint, build e
  formatacao;
- `npm audit`: nenhuma vulnerabilidade encontrada;
- endpoint `GET /api/v1/dashboard`: estado sem concurso, concurso incompleto e
  painel completo validados;
- agregacoes validadas: proxima prova, dias restantes, tempo semanal, materias,
  topicos exigidos, topicos com estudo, itens mapeados e sem mapeamento e
  atividade recente;
- alertas deterministicos validados: sem cargo, sem prova, grupo sem materia,
  item sem mapeamento e materia sem topico; a regra de prova marcada sem estudos
  tambem esta implementada;
- isolamento A-versus-B: o Usuario B recebeu o estado sem concurso e nao viu
  medidas do Usuario A;
- reutilizacao A-versus-B de concursos: ao ativar o Concurso B com o mesmo
  topico exigido, o unico estudo ativo continuou contabilizado sem copia;
- fluxo HTTP real: dashboard retornou concurso, cargo, prova em 33 dias, 145
  minutos semanais, dois de tres topicos com estudo, duas atividades e os tres
  alertas esperados;
- interface real inspecionada em 1280, 768 e 390 px, incluindo navegacao
  recolhivel, cards, linha do tempo, alertas e atalhos, sem estouro horizontal;
- dados e capturas sinteticos removidos ao final, processos temporarios
  encerrados e portas liberadas;
- divergencias conhecidas da Sprint 7: nenhuma.

## Relatorio de encerramento

Usar exatamente as secoes exigidas no item 36 da especificacao-fonte. Resultados nao executados devem constar como `nao executado`, `parcial` ou `bloqueado`; nunca como aprovados por inferencia.
