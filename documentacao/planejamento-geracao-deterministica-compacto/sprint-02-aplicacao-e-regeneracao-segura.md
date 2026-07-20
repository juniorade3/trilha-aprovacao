# Sprint 02 — Aplicação e regeneração segura

> Ler primeiro `CONTEXTO-COMUM.md` e o registro de conclusão da Sprint 01.

## 1. Objetivo

Permitir aplicar a prévia no plano em rascunho, distinguir a origem dos blocos e regenerar sem apagar blocos manuais ou gerados já ajustados.

## 2. Valor entregue

A geração passa a economizar trabalho real, mantendo o usuário no controle.

## 3. Dependências

Sprint 01 concluída, validada e mesclada.

## 4. Escopo

- adicionar origem e justificativa aos blocos;
- migrar blocos antigos para `MANUAL`;
- aplicar a geração em transação;
- recalcular a proposta no backend;
- criar revisão e blocos principais;
- detectar geração anterior;
- substituir apenas blocos puramente gerados mediante confirmação;
- preservar manuais e ajustados;
- mudar origem na primeira edição manual;
- normalizar a ordem;
- exibir badges e justificativas;
- integrar aplicação e regeneração à gaveta;
- atualizar OpenAPI e testes.

## 5. Fora do escopo

- geração em plano ativo;
- desfazer geração após ativação;
- apagar bloco ajustado;
- histórico de versões da geração;
- seleção automática de tópicos;
- motores futuros;
- IA.

## 6. Regras de negócio

1. Somente plano `RASCUNHO` aceita aplicação.
2. O backend recalcula; não recebe a lista final de blocos do frontend.
3. A aplicação é transacional.
4. Novos blocos recebem `GERADO_DETERMINISTICAMENTE`.
5. Blocos antigos recebem `MANUAL`.
6. Se já houver gerados e `substituir=false`, retornar 409.
7. Com `substituir=true`, remover somente gerados puros.
8. Manual e ajustado são preservados.
9. Primeira edição manual de bloco gerado muda a origem para ajustado.
10. Repetição sem substituir não duplica blocos.
11. Falha não deixa geração parcial.
12. Usuário B não aplica o plano de A.

## 7. Modelo de domínio

`OrigemDoBlocoDeEstudo`:

- `MANUAL`;
- `GERADO_DETERMINISTICAMENTE`;
- `GERADO_AJUSTADO_MANUALMENTE`.

Alterar `BlocoDeEstudo` para possuir:

- origem obrigatória;
- justificativa opcional;
- fábrica de bloco gerado;
- comportamento que transforma gerado em ajustado ao editar.

Origem não substitui estado.

## 8. Backend

- criar migration seguinte à última real;
- adicionar `origem` e `justificativa_da_geracao`;
- criar check de origem e índice por plano/origem;
- atualizar entidades persistidas e DTOs;
- adicionar operação transacional de aplicação;
- remover substituíveis antes de persistir novos;
- reutilizar exatamente o mesmo gerador da prévia;
- normalizar ordens;
- testar rollback;
- manter isolamento e CSRF.

## 9. Frontend

- botão `Aplicar à semana`;
- modal de confirmação quando houver geração anterior;
- informar quantos gerados serão substituídos;
- explicar que manuais e ajustados serão preservados;
- badges `Manual`, `Gerado`, `Gerado e ajustado`;
- ação para consultar justificativa;
- edição existente transforma a origem;
- atualizar a Semana sem recarregar a aplicação inteira;
- não usar `window.confirm`.

## 10. Contrato da API

| Método | Rota | Entrada | Saída | Códigos |
| --- | --- | --- | --- | --- |
| POST | `/api/v1/planos-semanais/{id}/geracao-deterministica` | configuração + substituir | plano atualizado + resumo | 200, 400, 404, 409, 422 |

## 11. Fluxo principal

1. Gerar uma prévia.
2. Selecionar `Aplicar à semana`.
3. Backend recalcular e persistir.
4. Conferir blocos na Semana.
5. Editar um bloco gerado.
6. Confirmar que virou ajustado.
7. Solicitar nova geração.
8. Confirmar substituição.
9. Conferir que manual e ajustado permaneceram.
10. Ativar o plano.
11. Confirmar que nova geração é bloqueada.

## 12. Critérios de aceite

- Dado plano rascunho, quando aplicar, então cria a proposta recalculada.
- Dado plano ativo, quando aplicar, então recebe 409.
- Dado geração existente e substituir falso, então não duplica.
- Dado substituir verdadeiro, então remove apenas gerados puros.
- Dado bloco manual, então permanece.
- Dado bloco gerado editado, então vira ajustado.
- Dado bloco ajustado, então permanece na regeneração.
- Dado falha de persistência, então ocorre rollback.
- Dado usuário B, então recebe 404.
- Dado aplicação repetida sem substituir, então o total não aumenta.

## 13. Testes obrigatórios

- domínio: origem e edição;
- migration: dados antigos, checks e índice;
- aplicação: transação, substituição e idempotência;
- integração PostgreSQL: rollback;
- API: CSRF, A/B e conflitos;
- frontend: aplicação, confirmação, badges e justificativas;
- regressão: criação, edição, ativação e execução manual;
- OpenAPI.

## 14. Arquivos provavelmente afetados

- domínio e persistência de blocos;
- migration;
- serviço e controllers do planejamento;
- DTOs;
- API TypeScript;
- página Semana e componentes da gaveta;
- testes backend e frontend.

## 15. Ordem de implementação

- [x] modelar origem;
- [x] criar migration segura;
- [x] atualizar persistência e contratos;
- [x] implementar aplicação transacional;
- [x] implementar regeneração seletiva;
- [x] integrar confirmação e badges;
- [x] testar rollback, A/B e regressões;
- [x] executar portas.

## 16. Validação final

```bash
git diff --check
make verificar
```

Fluxo manual:

1. aplicar;
2. editar bloco gerado;
3. regenerar;
4. conferir preservação;
5. ativar plano;
6. tentar gerar novamente;
7. conferir Swagger;
8. validar 390, 768 e 1280 px.

## 17. Registro de conclusão

```text
STATUS: Concluída em 20/07/2026.
ARQUIVOS ALTERADOS: domínio, aplicação, persistência, API e testes de planejamento; migration V12; cliente e telas Vue da Semana; OpenAPI.
DECISÕES TOMADAS: aplicação recalcula no backend sob lock do plano; gerados puros são substituíveis; normalização interna não altera origem; justificativa é resumo textual limitado a 2.000 caracteres.
TESTES EXECUTADOS: domínio, integração PostgreSQL, rollback, API/CSRF/A-B/conflitos, OpenAPI, componentes Vue, página Semana e diálogos aninhados.
VALIDAÇÃO MANUAL: fluxo Vue/API aplicar, editar, regenerar, preservar, ativar e bloquear nova geração executado; modal sobre a gaveta e ausência de rolagem horizontal conferidos em 390, 768 e 1.280 px; endpoint confirmado no OpenAPI.
PENDÊNCIAS: nenhuma dentro do escopo da Sprint 02.
PRÓXIMA SPRINT: Sprint 03 — Consolidação e aceite
```
