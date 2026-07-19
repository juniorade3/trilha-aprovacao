# Contexto comum — Planejamento Manual Estruturado

## 1. Objetivo estável

Adicionar uma capacidade `planejamento` ao monólito modular do Trilha da Aprovação. O módulo permite que o usuário organize, visualize, execute e ajuste manualmente uma semana de estudos. Ele não decide automaticamente o que estudar.

## 2. Padrões reais do repositório

### Backend

Usar a estrutura existente:

```text
aplicativos/backend/src/main/java/br/com/trilhaaprovacao/planejamento/
├── api
├── aplicacao
├── dominio
└── infraestrutura
```

Padrões obrigatórios:

- domínio sem Spring, JPA, HTTP ou JSON;
- domínio preferencialmente em records/classes imutáveis com fábricas e métodos de transição;
- serviço de aplicação concreto `ServicoDePlanejamento`, em vez de criar uma classe por caso de uso sem necessidade;
- consultas agregadas podem ganhar classe própria, como `ConsultaDoPlanejamentoDeHoje`, seguindo `ConsultaDoDashboard`;
- entidades JPA com sufixo `Persistido` em `infraestrutura`;
- repositórios Spring Data em `infraestrutura`;
- controllers `ControladorDe...`, DTOs `RequisicaoDe...` e `RespostaDe...` em `api`;
- usuário resolvido por `IdentidadeDoUsuarioAtual`;
- recursos de outro usuário tratados como não encontrados, conforme o padrão atual;
- regras inválidas usam `RegraDeDominio` e retornam `422`;
- conflitos de estado, duplicidade ou concorrência usam `ConflitoDeDominio` e retornam `409`;
- entradas inválidas por Bean Validation retornam `400`;
- criações retornam `201` com `Location`;
- exclusões físicas permitidas retornam `204`;
- atualizar `ConfiguracaoDaDocumentacaoDaApi` e o teste de OpenAPI quando necessário.

Não criar interfaces de repositório no domínio apenas para seguir outro estilo arquitetural. O projeto atual mantém os repositórios de persistência na infraestrutura e os serviços de aplicação fazem a orquestração.

### Frontend

Usar a estrutura existente:

```text
aplicativos/frontend/src/modulos/planejamento/
```

Arquivos esperados ao longo das sprints:

```text
PlanejamentoSemanaPagina.vue
PlanejamentoSemanaPagina.spec.ts
PlanejamentoHojePagina.vue
PlanejamentoHojePagina.spec.ts
NavegacaoDoPlanejamento.vue
apiDePlanejamento.ts
```

Reutilizar:

- `aplicacao/roteamento/index.ts`;
- `aplicacao/layouts/LayoutPrincipal.vue`;
- `compartilhado/api/clienteHttp.ts`;
- `CabecalhoDaPagina.vue`;
- `EstadoDaPagina.vue`;
- `ModalDaAplicacao.vue`;
- `GavetaLateral.vue`;
- `BarraDeProgresso.vue` quando houver porcentagem legítima;
- Bootstrap, Sass e os tokens visuais atuais;
- estado local nas páginas; Pinia somente se surgir estado realmente compartilhado entre páginas.

Não usar `window.confirm`. Não criar um segundo cliente HTTP. Não criar dados simulados.

## 3. Glossário

### Plano semanal

Registro de uma semana civil de segunda-feira a domingo para um usuário. É identificado pela data da segunda-feira (`dataInicial`). Um usuário possui no máximo um plano para a mesma semana.

### Disponibilidade do dia

Quantidade de minutos que o usuário declara possuir em uma data da semana. É capacidade diária, não uma faixa de calendário.

### Bloco de estudo

Intenção manual de estudo em uma data. Possui título, tipo de atividade, duração prevista, ordem, horário opcional e referências opcionais a matéria e tópico.

### Execução do bloco

Fato que começa quando o usuário inicia um bloco. Registra início, encerramento, resultado e duração executada. Um bloco possui no máximo uma execução nesta entrega.

### Visão Semana

Consulta do plano com os sete dias, disponibilidade, carga planejada, blocos e estados.

### Visão Hoje

Consulta do plano ativo que contém a data informada, com bloco em andamento, próximo bloco e sequência do dia.

## 4. Modelo de domínio aprovado

O projeto atual não carrega toda a árvore de uma capacidade em um único agregado JPA. Manter o mesmo padrão:

```text
PlanoSemanal
DisponibilidadeDoDia
BlocoDeEstudo
ExecucaoDoBloco
```

Cada conceito possui seu modelo de domínio e seu `...Persistido`. O `ServicoDePlanejamento` coordena invariantes que atravessam mais de um registro.

Relacionamentos:

```text
Usuario 1--* PlanoSemanal
PlanoSemanal 1--7 DisponibilidadeDoDia
PlanoSemanal 1--* BlocoDeEstudo
BlocoDeEstudo 0--1 ExecucaoDoBloco
BlocoDeEstudo 0--1 Materia, por identificador
BlocoDeEstudo 0--1 TopicoDaMateria, por identificador
ExecucaoDoBloco 0--1 RegistroDeEstudo, por identificador
```

## 5. Unidade temporal

- a semana começa na segunda-feira e termina no domingo;
- `dataInicial` usa `LocalDate` e precisa ser segunda-feira;
- a data final é derivada com `dataInicial.plusDays(6)`;
- data do bloco usa `LocalDate`;
- horário previsto usa `LocalTime` opcional;
- início e encerramento da execução usam `OffsetDateTime`;
- a visão Hoje recebe `data` explicitamente por query para não depender do fuso do servidor;
- o cronômetro da interface é derivado de `iniciadaEm`, sem gravações periódicas.

## 6. Disponibilidade

- o plano possui sete disponibilidades, uma por data;
- ao criar o plano, os sete dias nascem com `0` minuto;
- valores permitidos: `0` a `1.440`;
- `0` significa indisponível;
- o total planejado do dia considera blocos não cancelados;
- rascunho pode ficar acima da disponibilidade e deve exibir aviso;
- ativação exige que nenhum dia esteja acima da disponibilidade;
- em plano ativo, reduzir disponibilidade abaixo da carga ainda planejada retorna `409`;
- não existem recorrência, horários de disponibilidade ou cópia automática nesta entrega.

## 7. Estados do plano

`EstadoDoPlanoSemanal`:

- `RASCUNHO`: editável e ainda não executável;
- `ATIVO`: validado e disponível nas visões Hoje e Semana;
- `ENCERRADO`: semana finalizada e somente leitura;
- `CANCELADO`: plano abandonado e somente leitura.

Transições válidas:

```text
RASCUNHO -> ATIVO
RASCUNHO -> CANCELADO
ATIVO -> ENCERRADO
ATIVO -> CANCELADO
```

Regras:

- não reabrir plano encerrado ou cancelado;
- semanas diferentes podem possuir planos ativos, pois não se sobrepõem;
- ativação exige pelo menos uma disponibilidade positiva e um bloco;
- encerramento exige ausência de bloco em andamento;
- ao encerrar, blocos ainda `PLANEJADO` permanecem registrados e são exibidos como **não realizados**, estado derivado da combinação entre plano encerrado e bloco planejado;
- ao cancelar o plano, blocos ainda planejados passam a `CANCELADO`;
- execuções concluídas são preservadas em ambos os casos.

## 8. Estados do bloco

`EstadoDoBlocoDeEstudo`:

- `PLANEJADO`;
- `EM_ANDAMENTO`;
- `CONCLUIDO`;
- `PARCIALMENTE_CONCLUIDO`;
- `CANCELADO`.

Transições:

```text
PLANEJADO -> EM_ANDAMENTO
PLANEJADO -> CANCELADO
EM_ANDAMENTO -> CONCLUIDO
EM_ANDAMENTO -> PARCIALMENTE_CONCLUIDO
```

Regras:

- “disponível para execução”, “próximo”, “atrasado” e “não realizado” são apresentações derivadas;
- reagendamento não cria estado `ADIADO`;
- bloco reagendado continua `PLANEJADO` e recebe nova data, horário e ordem;
- bloco concluído ou parcial não volta a planejado nesta entrega;
- bloco em andamento não pode ser editado, reagendado ou cancelado.

## 9. Conteúdo do bloco

Campos mínimos:

- `titulo`: obrigatório, até 200 caracteres;
- `tipoDeAtividade`: obrigatório;
- `data`: obrigatória e pertencente ao plano;
- `duracaoPrevistaEmMinutos`: 1 a 1.440;
- `ordem`: positiva e normalizada por dia;
- `horarioPrevisto`: opcional;
- `identificadorDaMateria`: opcional;
- `identificadorDoTopico`: opcional;
- `observacao`: opcional, até 2.000 caracteres.

`TipoDeAtividade` inicial:

- `TEORIA`;
- `QUESTOES`;
- `REVISAO`;
- `CADERNO_DE_ERROS`;
- `SIMULADO`;
- `DISCURSIVA`;
- `OUTRA`.

Regras:

- bloco livre sem matéria é permitido;
- tópico exige matéria;
- tópico deve pertencer à matéria;
- matéria e tópico devem pertencer ao usuário autenticado;
- o módulo guarda apenas os identificadores e não depende das entidades de `conteudos`;
- horário é informativo e não substitui a ordem;
- horários sobrepostos não bloqueiam a primeira versão; a interface apenas pode avisar;
- exclusão física só existe para bloco planejado em plano rascunho;
- depois da ativação, usa-se cancelamento.

## 10. Execução

- somente bloco de plano ativo pode iniciar;
- bloco do dia ou atrasado pode iniciar;
- bloco futuro precisa ser reagendado antes;
- um usuário possui no máximo uma execução em andamento;
- um bloco possui no máximo uma execução;
- a execução começa com `iniciadaEm` e sem encerramento;
- conclusão exige duração executada entre 1 e 1.440 minutos;
- `CONCLUIDO` significa que o usuário encerrou o bloco como realizado;
- `PARCIALMENTE_CONCLUIDO` significa que houve estudo, mas o objetivo do bloco não foi terminado;
- não existe pausa e retomada;
- para continuar um bloco parcial, o usuário cria ou duplica outro bloco manualmente;
- o endpoint de finalização deve ser idempotente para tolerar repetição após perda de resposta.

A execução não precisa de enum próprio: enquanto `encerradaEm` for nulo está em andamento; depois, `resultado` informa `CONCLUIDO` ou `PARCIALMENTE_CONCLUIDO`.

## 11. Integração com estudos

- o planejamento não acessa repositórios de `estudos`;
- usar `ServicoDeMateriaisEEstudos` ou extrair um serviço de aplicação estreito dentro de `estudos.aplicacao` somente se isso reduzir acoplamento real;
- execução finalizada com tópico cria `RegistroDeEstudo` com material nulo;
- a data/hora do estudo usa o início da execução;
- a duração usa `duracaoExecutadaEmMinutos`;
- a observação pode incluir o título do bloco e a observação informada pelo usuário, sem inserir metadados técnicos;
- o identificador retornado fica em `ExecucaoDoBloco.identificadorDoRegistroDeEstudo`;
- a coluna é única para impedir vínculo duplicado;
- finalização repetida retorna a execução já finalizada, sem criar outro estudo;
- execução sem tópico é válida e não gera estudo;
- bloco vinculado apenas à matéria pode receber um tópico no encerramento, desde que pertença à matéria;
- correção de execução integrada chama `corrigirEstudo` e atualiza o identificador para o novo registro ativo;
- nunca editar `registros_de_estudo` diretamente.

## 12. Edição e replanejamento

- rascunho permite editar disponibilidade e qualquer bloco planejado;
- plano ativo permite editar ou reagendar apenas bloco `PLANEJADO`;
- reagendamento nesta entrega permanece dentro da mesma semana;
- a ordem dos dias de origem e destino deve ser normalizada;
- reduzir disponibilidade ativa abaixo da carga ainda planejada retorna conflito;
- execução finalizada pode ter duração e resultado corrigidos em ação específica;
- correção da execução ocorre em lugar, com `atualizadoEm` e `versao`; não criar trilha genérica de eventos;
- se houver estudo vinculado, aplicar a correção rastreável já existente no módulo de estudos;
- cancelamento não apaga execução ou estudo.

## 13. Persistência

Migrations existentes no momento do planejamento: `V1` a `V5`.

Sequência esperada, sempre confirmando a última versão antes de criar:

- Sprint 01: `V6__cria_planos_semanais_e_disponibilidades.sql`;
- Sprint 02: `V7__cria_blocos_de_estudo.sql`;
- Sprint 05: `V8__cria_execucoes_de_blocos.sql`;
- Sprint 06: `V9__vincula_execucoes_a_registros_de_estudo.sql`;
- Sprint 07: `V10__adiciona_reagendamento_aos_blocos.sql`, somente se o campo ainda for necessário.

Requisitos:

- UUIDs;
- FKs sem cascata destrutiva;
- timestamps com fuso;
- `@Version` e coluna `versao` nas entidades mutáveis;
- índice por usuário e data inicial do plano;
- unicidade `(usuario_id, data_inicial)`;
- unicidade `(plano_id, data)` para disponibilidade;
- unicidade `(plano_id, data, ordem)` para blocos não cancelados deve ser garantida por normalização transacional; não criar índice parcial complexo sem necessidade comprovada;
- unicidade de uma execução por bloco;
- índice parcial para uma execução aberta por usuário, caso a migration use `usuario_id` em execuções;
- `ddl-auto=validate` permanece obrigatório.

## 14. API

Manter recursos na raiz de `/api/v1`, como já ocorre no projeto:

```text
/api/v1/planos-semanais
/api/v1/blocos-de-estudo
/api/v1/execucoes-de-bloco
/api/v1/planejamento/hoje
```

Não usar `/api/v1/planejamento/planos-semanais`, pois os recursos atuais não são agrupados por nome de módulo.

Ações explícitas seguem o padrão existente:

```text
POST /planos-semanais/{id}/ativacao
POST /blocos-de-estudo/{id}/inicio
POST /blocos-de-estudo/{id}/conclusao
POST /blocos-de-estudo/{id}/interrupcao
POST /blocos-de-estudo/{id}/reagendamento
POST /blocos-de-estudo/{id}/cancelamento
POST /planos-semanais/{id}/encerramento
POST /planos-semanais/{id}/cancelamento
```

Não exigir `versao` nos DTOs apenas para criar um padrão novo. A concorrência continua com `@Version` e o tratamento atual de `ObjectOptimisticLockingFailureException`.

## 15. Navegação e experiência

Rotas:

```text
/planejamento
/planejamento/semana
/planejamento/hoje
/estudos
```

Decisões:

- `/planejamento` redireciona para Semana até a Sprint 04 e depois para Hoje;
- o menu principal mantém cinco itens e substitui “Histórico” por “Planejamento”;
- o módulo mostra navegação secundária Hoje, Semana e Histórico;
- a visão Semana usa cartões de dias, não uma grade rígida de sete colunas;
- desktop: três ou quatro cartões por linha, conforme largura;
- tablet: dois cartões por linha;
- celular: um cartão por linha;
- formulários abrem em `GavetaLateral` quando precisarem de contexto e em `ModalDaAplicacao` para ações curtas;
- ao fechar edição, preservar semana, rolagem e dia selecionado;
- mensagens devem dizer o que aconteceu e como corrigir;
- conflitos `409` devem oferecer recarregar os dados;
- não remover o registro rápido global existente.

## 16. Estratégia de testes

Por sprint:

- domínio: fábricas, validações e transições;
- aplicação: escopo de usuário, orquestração e conflitos;
- infraestrutura: JPA/Testcontainers, constraints e migrations;
- API: sessão, CSRF, códigos, `Location`, A contra B e OpenAPI;
- frontend: página, estados, modais/gavetas, cliente API e roteamento;
- regressões: autenticação, registro rápido, histórico, dashboard e layout;
- responsividade: 390, 768 e 1280 px;
- acessibilidade: teclado, foco, labels, `role=status` e fechamento por `Escape`.

Não adicionar framework E2E novo. O fluxo completo pode continuar em testes de integração backend, testes de componentes frontend e validação manual documentada.

## 17. Comandos de qualidade

Usar os alvos existentes:

```bash
make testar-backend
make testar-frontend
make verificar-backend
make verificar-frontend
make verificar
```

Validações complementares:

```bash
docker compose config
git diff --check
```

Para execução local:

```bash
make infra-subir
make backend-executar
make frontend-executar
```

## 18. Fora do escopo compartilhado

- Motor de Evidências;
- Motor de Lacunas;
- Motor de Revisões;
- geração automática;
- IA e otimização;
- recorrência de disponibilidade;
- múltiplas janelas por dia;
- pausa/retomada;
- múltiplas execuções por bloco;
- dependências entre blocos;
- drag-and-drop;
- compartilhamento de planos;
- modelos prontos de semana;
- metas e gamificação;
- notificações e calendário externo.
