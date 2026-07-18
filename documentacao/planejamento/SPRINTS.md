# Sprints da reconstrucao

As estimativas abaixo servem para ordenar trabalho, nao substituem a validacao. Uma sprint nao termina com itens criticos pendentes.

## Sprint 0 — Preflight e decisao de reconstrução

Objetivo: garantir que a reconstrucao pode comecar sem tocar indevidamente em dados existentes.

- Verificar `git`, Java/Javac 21, Maven, Node/npm LTS, Docker/Compose, curl e navegador quando disponivel.
- Inspecionar `~/Aplicativos/trilha-aprovacao` antes de criar qualquer coisa.
- Registrar versoes, ausencias e a decisao sobre o diretorio.
- Consultar a matriz oficial e registrar a versao estavel de `springdoc-openapi` 3.x compativel com Spring Boot 4.1.x.

Saida: diretorio autorizado e preflight verde, ou status BLOQUEADA/PARCIAL com evidencias. Nenhuma implementacao de produto e iniciada em caso de conflito.

## Sprint 1 — Fundacao reproduzivel

Objetivo: estabelecer o monorepo, as convencoes e um caminho local repetivel de execucao.

- Criar a estrutura exigida, `.gitignore`, `.editorconfig`, `.env.example`, Makefile, wrappers e documentos de arquitetura/ADRs iniciais.
- Configurar backend MVC modular, Actuator e endpoint de health; configurar Vue, Router, Pinia, Bootstrap, Sass, ESLint, Prettier e Vitest.
- Criar Compose de PostgreSQL com volume, variaveis, porta configuravel e healthcheck.
- Configurar Flyway, perfil local sem dados e `ddl-auto=validate`.
- Criar pipeline de verificacoes local e o primeiro commit somente depois dos checks verdes.

Saida: `docker compose config`, banco, backend minimo, frontend minimo, builds e testes-base funcionam.

## Sprint 2 — Identidade, sessao e isolamento

Objetivo: tornar toda capacidade posterior autenticada e privada por usuario.

- Modelar Usuario, migration e repositorio; cadastro, login, logout, sessao atual e CSRF.
- Configurar BCrypt, cookie HttpOnly, SameSite=Lax, Secure em producao, protecao CSRF e tratamento 401/403.
- Criar contrato de erro, correlacao por requisicao e testes MVC/seguranca.
- Criar login, cadastro, guarda de rotas, cliente `fetch` central e estado minimo de sessao no frontend.

Saida: usuario inativo nao entra, senha nao vaza, sessao/CSRF funcionam e o teste A-versus-B prova isolamento base.

## Sprint 3 — Conteudos reutilizaveis

Objetivo: disponibilizar o catalogo pessoal de Materia e TopicoDaMateria.

- Migrations, entidades, casos de uso, regras de unicidade, arquivamento e arvore sem ciclos.
- CRUD paginado de materias e topicos, DTOs e erros de regra de dominio.
- Telas de lista/detalhe, arvore de topicos, formularios acessiveis e estados vazio/carregando/erro.
- Testes de dominio, casos de uso, repositorio PostgreSQL e componentes.

Saida: materias pertencem apenas ao usuario autenticado; topicos respeitam pai, irmaos e ciclos; tudo esta testado pela API e interface.

## Sprint 4 — Estrutura de concursos

Objetivo: modelar a arvore Concurso > Edital > Cargo > Prova > Grupo > MateriaDaProva.

- Implementar migrations, CRUDs, ordenacao e regras de um concurso ativo, edital principal, cargo selecionado, duplicidades e arquivamento.
- Impedir alteracoes de conteudo em concurso arquivado e exclusao fisica com dependencias.
- Criar fluxos de listagem, criacao em secoes, detalhes hierarquicos e selecao/ativacao.
- Cobrir testes de concorrencia, seguranca e regras de cada nivel.

Saida: um concurso pode ser construído gradualmente, tem apenas um cargo selecionado e reutiliza Materia sem copia.

## Sprint 5 — Conteudo programatico e mapeamentos

Objetivo: preservar o texto do edital e relaciona-lo a topicos pessoais.

- Implementar ItemDoEdital em arvore e MapeamentoDeItemDoEdital, com verificacao de materia, unicidade e ausencia de ciclos.
- Expor CRUDs e exclusao apenas do vinculo de mapeamento.
- Criar a interface de itens e mapeamentos na arvore do concurso.
- Testar que item nao mapeado nao gera progresso e que remover vinculo preserva item, topico e estudos.

Saida: o conteudo oficial e o catalogo reutilizavel estao relacionados sem duplicacao de historico.

## Sprint 6 — Materiais e estudos

Objetivo: registrar evidencias de estudo sem exclusao destrutiva.

- Implementar MaterialDeEstudo, CoberturaDeTopicoPorMaterial e RegistroDeEstudo; corrigir/cancelar mantendo rastreabilidade.
- Validar cobertura compativel, duracao entre 1 e 1.440 minutos, URL e arquivamento.
- Criar CRUD de materiais, cobertura de topicos, registro/historico/correcao/cancelamento no frontend.
- Testar o compartilhamento de estudo por topico entre concursos e os limites de duracao.

Saida: um estudo ativo pertence ao topico, e o mesmo fato pode aparecer em dois concursos que o exigem.

## Sprint 7 — Dashboard objetivo

Objetivo: calcular o estado do concurso ativo a partir de fatos persistidos.

- Criar agregacao `GET /api/v1/dashboard` sem entidade Progresso.
- Calcular topicos exigidos/com estudo, itens sem mapeamento, tempo semanal, atividade recente, prova e alertas deterministas.
- Implementar estado sem concurso e painel responsivo com cards, badges e barras Bootstrap, sem biblioteca de grafico.
- Testar agregacoes, alertas, isolamento e as duas apresentacoes do dashboard.

Saida: o dashboard exibe medidas objetivas e o fluxo entre Concurso A e B reconhece estudo comum corretamente.

## Sprint 8 — Contrato publico e consolidacao

Objetivo: fechar o produto com documentacao, automacao e prova funcional.

- Configurar e testar `/v3/api-docs` e Swagger conforme perfil; documentar uso de cookie e CSRF.
- Finalizar diagramas, ADRs, README, operacao, cliente API quando efetivamente necessario e workflow GitHub Actions sem deploy.
- Rodar toda a matriz de verificacao, `npm audit`, testes de migrations/Testcontainers e fluxo funcional de 23 passos.
- Validar interfaces em 390, 768 e 1280 px; encerrar processos temporarios; emitir relatorio final factual.

Saida: todos os criterios finais da especificacao possuem evidencia executada ou uma divergencia declarada. O commit final ocorre somente com a porta verde.
