# Sprint 03 — Ativação e visão Semana consolidada

> Ler primeiro: `CONTEXTO-COMUM.md`.

## 1. Objetivo

Permitir validar e ativar um plano semanal, apresentando uma visão Semana clara, legível e pronta para uso.

## 2. Valor entregue

A semana deixa de ser apenas um rascunho e passa a representar um compromisso de estudo executável, com problemas visíveis antes da ativação.

## 3. Dependências

Sprints 01 e 02 concluídas.

## 4. Escopo

- implementar transição `RASCUNHO -> ATIVO`;
- validar disponibilidade e blocos antes da ativação;
- impedir mutações de rascunho que ainda não foram liberadas para plano ativo;
- consolidar a resposta da Semana;
- exibir estado, período, totais e alertas;
- ativar pela interface com confirmação acessível;
- atualizar visual dos blocos e dias para plano ativo;
- manter navegação entre semanas;
- deixar claro quando uma semana ainda está em rascunho.

## 5. Fora do escopo

- visão Hoje;
- iniciar ou concluir bloco;
- editar plano ativo;
- reagendar;
- encerrar ou cancelar plano;
- geração automática.

## 6. Regras de negócio

1. Apenas plano `RASCUNHO` pode ser ativado.
2. A ativação exige pelo menos um dia com disponibilidade positiva.
3. A ativação exige pelo menos um bloco.
4. Nenhum dia pode ter carga planejada superior à disponibilidade.
5. Todos os blocos devem estar em data pertencente ao plano.
6. A ordem dos blocos deve estar contínua em cada dia.
7. Plano duplicado para a mesma semana continua proibido.
8. A ativação é idempotente: repetir a ação sobre plano já ativo retorna o plano atual, sem duplicar efeito.
9. Plano ativo permanece somente leitura até a Sprint 07, exceto pelas operações de execução que serão adicionadas depois.

## 7. Modelo de domínio

### Comportamentos

- `PlanoSemanal.ativar()`;
- `PlanoSemanal.exigirRascunho()`;
- `PlanoSemanal.estaAtivo()`.

As validações que dependem de disponibilidade e blocos são coordenadas por `ServicoDePlanejamento` antes de chamar a transição do plano.

### Invariantes

- plano válido para execução;
- não ativar vazio;
- não ativar com excesso;
- não reativar estado terminal.

## 8. Backend

### Aplicação

Adicionar em `ServicoDePlanejamento`:

- `ativarPlanoSemanal(usuario, plano)`;
- cálculo de carga por dia;
- validação consolidada de ativação;
- consulta completa da Semana com disponibilidades e blocos.

A consulta pode continuar no serviço nesta sprint. Não criar projeção separada se o DTO atual for suficiente.

### Persistência

Nenhuma migration esperada. Persistir nova situação e `atualizadoEm` na entidade existente.

### API

Adicionar ação explícita de ativação.

A resposta da Semana deve incluir:

- estado do plano;
- total disponível;
- total planejado;
- saldo por dia;
- quantidade de blocos;
- indicador de excesso;
- blocos ordenados.

Não persistir os totais derivados.

### Erros

- 404: plano fora do escopo;
- 409: estado incompatível ou concorrência;
- 422: plano vazio, sem disponibilidade ou com excesso.

## 9. Frontend

### Visão Semana

Usar cartões de dia responsivos, não calendário de sete colunas.

Cada cartão mostra:

- dia e data;
- minutos disponíveis;
- minutos planejados;
- saldo ou excesso;
- blocos ordenados;
- ação de adicionar quando rascunho;
- estado somente leitura quando ativo.

Cabeçalho da página:

- período;
- badge Rascunho/Ativo;
- total disponível e planejado;
- ação “Ativar plano”.

### Confirmação

`ModalDaAplicacao` deve explicar:

- que o plano ficará disponível para execução;
- que a edição ampla do ativo será entregue depois;
- quais pendências impedem a ativação.

### Estado vazio e erros

- plano inexistente: criar semana;
- rascunho sem blocos: adicionar primeiro bloco;
- excesso: destacar os dias problemáticos e levar ao cartão correspondente;
- conflito: recarregar.

## 10. Contrato da API

| Método | Rota | Finalidade | Entrada | Saída | Códigos |
| --- | --- | --- | --- | --- | --- |
| POST | `/api/v1/planos-semanais/{id}/ativacao` | ativar plano | nenhuma | plano completo ativo | 200, 404, 409, 422 |
| GET | `/api/v1/planos-semanais?dataInicial=AAAA-MM-DD` | consultar Semana consolidada | query | plano, totais e blocos | 200, 400, 404 |

## 11. Fluxo principal

1. Usuário monta disponibilidade e blocos.
2. Seleciona “Ativar plano”.
3. Sistema valida a semana.
4. Se houver problema, aponta o dia e o motivo.
5. Se estiver válida, confirma ativação.
6. A Semana passa a estado ativo e somente leitura nesta fase.

## 12. Critérios de aceite

- Dado rascunho válido, quando ativar, então o estado passa a ATIVO.
- Dado rascunho sem bloco, quando ativar, então recebe 422.
- Dado dia com carga acima da disponibilidade, quando ativar, então recebe 422 e a interface destaca o dia.
- Dado plano já ativo, quando repetir ativação, então não ocorre efeito duplicado.
- Dado plano ativo, quando tentar editar disponibilidade ou bloco pelas rotas da Sprint 01/02, então recebe 409.
- Dado tela de 1280 px, então os cartões não ficam comprimidos em sete colunas.

## 13. Testes obrigatórios

- domínio: transição de estado;
- aplicação: todas as validações de ativação;
- API: ação, idempotência, 409/422 e A/B;
- frontend: modal, ativação, pendências, estado ativo e responsividade;
- regressão: criação e edição de rascunho continuam funcionando.

## 14. Arquivos provavelmente afetados

- `PlanoSemanal.java` e `ServicoDePlanejamento.java`;
- `PlanoSemanalPersistido.java`;
- `ControladorDePlanosSemanais.java` e resposta consolidada;
- testes backend e OpenAPI;
- `PlanejamentoSemanaPagina.vue` e spec;
- estilos do módulo.

## 15. Ordem de implementação

- [x] implementar transição no domínio;
- [x] criar validação consolidada no serviço;
- [x] expor ação e DTOs;
- [x] ajustar Semana e modal;
- [x] testar pendências e idempotência;
- [x] validar responsividade;
- [x] executar portas e registrar.

## 16. Validação final

Executar `make verificar` e `git diff --check`.

No navegador:

1. tentar ativar plano vazio;
2. tentar ativar plano com excesso;
3. corrigir e ativar;
4. confirmar estado somente leitura;
5. navegar para outra semana e voltar;
6. conferir Swagger.

## 17. Registro de conclusão

```text
STATUS: concluída
ARQUIVOS ALTERADOS: domínio, aplicação, persistência e API de planejamento;
resposta consolidada, OpenAPI, página Semana, cliente HTTP, estilos e testes.
DECISÕES TOMADAS: ativação idempotente; totais e saldos derivados sem persistência;
plano ativo somente leitura; pendências apresentadas no modal antes da confirmação.
TESTES EXECUTADOS: transição, validações, idempotência, A/B, CSRF, bloqueio de
mutações, OpenAPI, modal, pendências, excesso, estado ativo, regressões e make verificar.
PENDÊNCIAS: validação exploratória manual no navegador.
PRÓXIMA SPRINT: Sprint 04 autorizada pelo usuário e iniciada após esta porta verde.
```
