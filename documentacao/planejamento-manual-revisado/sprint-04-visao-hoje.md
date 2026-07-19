# Sprint 04 — Visão Hoje

> Ler primeiro: `CONTEXTO-COMUM.md`.

## 1. Objetivo

Criar a visão diária do planejamento, mostrando o plano ativo da data, o próximo bloco e a sequência do dia.

## 2. Valor entregue

O usuário deixa de percorrer a semana inteira para decidir o que fazer agora. A aplicação passa a oferecer uma entrada diária clara e humana.

## 3. Dependências

Sprints 01 a 03 concluídas.

## 4. Escopo

- criar consulta específica do planejamento de uma data;
- identificar plano ativo que contém a data;
- retornar disponibilidade, carga e blocos ordenados do dia;
- derivar próximo bloco, atrasados e concluídos quando aplicável;
- criar `/planejamento/hoje`;
- mudar `/planejamento` para redirecionar a Hoje;
- expandir navegação secundária para Hoje, Semana e Histórico;
- criar estado sem plano, plano em rascunho e dia sem blocos;
- adicionar atalho discreto do dashboard para Hoje.

## 5. Fora do escopo

- iniciar ou finalizar bloco;
- cronômetro;
- execução;
- edição de plano ativo;
- replanejamento;
- indicadores de aderência.

## 6. Regras de negócio

1. A consulta recebe uma data local explícita.
2. Somente plano `ATIVO` que contenha a data é considerado executável.
3. Se existir apenas rascunho para a semana, a resposta deve indicar que ele precisa ser ativado.
4. O próximo bloco é o primeiro `PLANEJADO` pela ordem do dia.
5. Horário previsto pode ser usado para exibição, mas a ordem continua soberana.
6. Blocos cancelados não aparecem na sequência principal.
7. Blocos concluídos e parciais aparecem em seção de realizados quando existirem futuramente.
8. A consulta não cria ou altera dados.

## 7. Modelo de domínio

Nenhuma entidade nova.

Criar um resultado de aplicação, por exemplo `ResultadoDoPlanejamentoDeHoje`, contendo apenas dados necessários à tela.

Derivações devem permanecer na aplicação:

- estado da semana para a data;
- minutos disponíveis;
- minutos planejados;
- próximo bloco;
- demais blocos;
- mensagens de ausência.

## 8. Backend

### Aplicação

Criar `ConsultaDoPlanejamentoDeHoje`, seguindo o padrão de `ConsultaDoDashboard`.

A consulta usa os repositórios de planejamento e retorna um resultado próprio. Não deve retornar entidades persistidas.

### Persistência

Nenhuma migration esperada.

Criar consultas eficientes por:

- usuário e intervalo da semana;
- plano e data do bloco;
- ordem do bloco.

Evitar carregar todas as semanas ou todos os blocos do usuário.

### API

Adicionar ao `ControladorDePlanejamento` ou controller equivalente um GET de Hoje.

A resposta deve distinguir:

- `SEM_PLANO`;
- `PLANO_EM_RASCUNHO`;
- `DIA_SEM_BLOCOS`;
- `DIA_PLANEJADO`.

Esses valores são estado de apresentação do DTO, não enum de domínio persistido.

## 9. Frontend

### Página

Criar:

- `PlanejamentoHojePagina.vue`;
- `PlanejamentoHojePagina.spec.ts`.

### Estrutura visual

- `CabecalhoDaPagina` com data e acesso à Semana;
- resumo do dia: disponível, planejado e quantidade de blocos;
- cartão destacado “Próximo bloco”;
- lista vertical da sequência do dia;
- link para editar a semana;
- navegação Hoje, Semana e Histórico.

### Estados vazios

- sem plano: “Você ainda não planejou esta semana” + ação para Semana;
- plano em rascunho: “Seu plano ainda precisa ser ativado”;
- dia sem blocos: mensagem simples, sem sugerir automaticamente conteúdo;
- erro: tentar novamente.

### Roteamento e layout

- `/planejamento` passa a redirecionar `/planejamento/hoje`;
- item Planejamento do menu aponta para `/planejamento`;
- adicionar atalho no dashboard sem redesenhar `InicioPagina`.

## 10. Contrato da API

| Método | Rota | Finalidade | Entrada | Saída | Códigos |
| --- | --- | --- | --- | --- | --- |
| GET | `/api/v1/planejamento/hoje?data=AAAA-MM-DD` | consultar o dia | data | estado do dia, resumo e blocos | 200, 400 |

A ausência de plano é um estado válido da resposta `200`, não `404`, pois a tela precisa orientar o usuário.

## 11. Fluxo principal

1. Usuário abre Planejamento.
2. Sistema consulta a data atual.
3. Se houver plano ativo, mostra o próximo bloco e a sequência.
4. Se não houver, explica o estado e oferece acesso à Semana.
5. Usuário pode alternar Hoje, Semana e Histórico sem perder contexto.

## 12. Critérios de aceite

- Dado plano ativo com três blocos, quando abrir Hoje, então o primeiro planejado aparece em destaque.
- Dado plano em rascunho, quando abrir Hoje, então a tela orienta a ativação.
- Dado usuário sem plano, quando abrir Hoje, então recebe estado válido e ação para criar semana.
- Dado dia sem blocos, então a tela não inventa recomendações.
- Dado data explícita, então a resposta independe do fuso do servidor.
- Dado mobile, então a sequência é vertical e utilizável por teclado.
- Dado menu principal, então Planejamento abre Hoje e Histórico permanece na navegação secundária.

## 13. Testes obrigatórios

- aplicação: sem plano, rascunho, ativo e dia vazio;
- infraestrutura: consulta por intervalo e data;
- API: estados válidos, data inválida, autenticação e A/B;
- frontend: todos os estados, próximo bloco, navegação e erro;
- roteamento: redirect de `/planejamento`;
- regressão: Semana e dashboard.

## 14. Arquivos provavelmente afetados

- `planejamento/aplicacao/ConsultaDoPlanejamentoDeHoje.java`;
- resultado/DTOs da consulta;
- controller e OpenAPI;
- repositórios com consultas específicas;
- `modulos/planejamento/PlanejamentoHojePagina.vue` e spec;
- `NavegacaoDoPlanejamento.vue`;
- `aplicacao/roteamento/index.ts` e spec;
- `modulos/inicio/InicioPagina.vue` e spec, somente para o atalho.

## 15. Ordem de implementação

- [x] modelar resultado da consulta;
- [x] criar consultas de persistência;
- [x] expor GET Hoje;
- [x] criar página e estados;
- [x] alterar redirect e navegação secundária;
- [x] adicionar atalho no dashboard;
- [x] testar e executar portas;
- [x] preencher registro.

## 16. Validação final

Executar `make verificar` e `git diff --check`.

No navegador:

1. abrir Hoje sem plano;
2. criar rascunho e abrir Hoje;
3. ativar plano com blocos e abrir Hoje;
4. testar dia sem blocos;
5. alternar Hoje, Semana e Histórico;
6. testar 390, 768 e 1280 px;
7. conferir Swagger.

## 17. Registro de conclusão

```text
STATUS: concluída
ARQUIVOS ALTERADOS: consulta, resultado, repositórios e API de planejamento;
OpenAPI; página Hoje, cliente HTTP, navegação, rotas, dashboard, estilos e testes.
DECISÕES TOMADAS: data local enviada explicitamente; ausência retorna estado 200;
consulta usa a segunda-feira da data e buscas por plano/data; atrasados e realizados
são derivados sem novos estados persistidos; ordem do dia define o próximo bloco.
TESTES EXECUTADOS: sem plano, rascunho, ativo, dia vazio, atrasados, data inválida,
autenticação, A/B, OpenAPI, todos os estados Vue, navegação, erro, dashboard,
roteamento, responsividade estrutural e make verificar.
PENDÊNCIAS: validação exploratória manual no navegador.
PRÓXIMA SPRINT: Sprint 05 não iniciada; depende de autorização explícita.
```
