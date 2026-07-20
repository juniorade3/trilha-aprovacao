# Sprint 01 — Prioridades e prévia determinística completa

> Ler primeiro `CONTEXTO-COMUM.md`. Não reler as outras sprints.

## 1. Objetivo

Permitir que o usuário defina prioridades e gere uma prévia semanal completa, com seleção de matérias, distribuição por disponibilidade, até três matérias por dia, revisão e justificativas, sem persistir novos blocos.

## 2. Valor entregue

O usuário avalia e ajusta a estratégia automática antes de alterar sua semana.

## 3. Dependências

Planejamento Manual completo e mesclado na `main`, incluindo sua Sprint 08.

## 4. Escopo

- consultar matérias elegíveis do concurso ativo e cargo selecionado;
- deduplicar matérias;
- persistir prioridade por plano e matéria;
- implementar gerador puro e determinístico;
- considerar disponibilidade e blocos preservados;
- reservar revisão;
- selecionar até três matérias distintas por dia;
- distribuir durações;
- produzir justificativas e avisos;
- expor prévia sem persistência;
- criar gaveta com Prioridades, Configuração e Prévia;
- atualizar Swagger;
- criar testes backend e frontend.

## 5. Fora do escopo

- aplicar a prévia;
- criar ou excluir blocos;
- origem de bloco;
- regeneração;
- seleção de tópicos;
- motores futuros;
- IA.

## 6. Regras de negócio

1. Somente plano `RASCUNHO` permite prioridades e prévia.
2. Matéria sem prioridade é `NORMAL`.
3. `NAO_INCLUIR` remove a matéria dos candidatos.
4. A mesma matéria aparece uma vez, mesmo em vários grupos.
5. A prévia não grava bloco.
6. Mesmas entradas geram mesma saída.
7. Blocos manuais consomem capacidade e carga.
8. Revisão é reservada antes dos principais.
9. O dia possui no máximo três matérias distintas contando as manuais.
10. Nenhum sugerido ultrapassa a disponibilidade.
11. Bloco principal sugerido tem matéria, mas não tópico.
12. Exceções geram avisos compreensíveis.
13. Usuário B não acessa o plano de A.

## 7. Modelo de domínio

### Persistido

`PrioridadeDeMateriaNoPlano`:

- identificador;
- plano;
- matéria;
- prioridade;
- timestamps;
- versão.

### Não persistidos

- `CandidatoDeMateriaParaGeracao`;
- `ConfiguracaoDaGeracaoDeterministica`;
- `CapacidadeDoDia`;
- `BlocoSugerido`;
- `JustificativaDaGeracao`;
- `PreviaDaGeracaoDaSemana`.

### Invariantes

- prioridade única por plano/matéria;
- duração principal entre 25 e 180;
- revisão entre 0 e 120;
- total diário dentro da capacidade;
- matéria única no dia;
- ordenação estável.

## 8. Backend

- confirmar última migration e criar a seguinte para prioridades;
- criar consulta pública estreita em `concursos.aplicacao` se necessária;
- implementar `GeradorDeterministicoDePlano` puro, sem Spring;
- montar entradas no serviço de aplicação;
- ordenar explicitamente todos os dados;
- criar DTOs específicos;
- 404 para recurso de outro usuário;
- 409 para plano fora de rascunho;
- 422 para ausência de concurso, cargo ou matérias;
- testar que a prévia não persiste blocos.

## 9. Frontend

Na página Semana:

- ação `Gerar semana`;
- gaveta com passos;
- editor de prioridades;
- configuração de duração principal e revisão;
- cartões diários da prévia;
- distinção entre preservado, revisão e sugerido;
- resumo de capacidade;
- avisos e justificativas;
- mensagem explícita de que nada foi aplicado;
- nenhum dado simulado;
- responsividade sem rolagem horizontal obrigatória.

## 10. Contrato da API

| Método | Rota | Entrada | Saída | Códigos |
| --- | --- | --- | --- | --- |
| GET | `/api/v1/planos-semanais/{id}/materias-para-geracao` | path | matérias e prioridades | 200, 404, 422 |
| PUT | `/api/v1/planos-semanais/{id}/prioridades-de-materias` | lista completa | prioridades atualizadas | 200, 400, 404, 409, 422 |
| POST | `/api/v1/planos-semanais/{id}/geracao-deterministica/previa` | durações | prévia dos sete dias | 200, 400, 404, 409, 422 |

## 11. Fluxo principal

1. Abrir semana em rascunho.
2. Selecionar `Gerar semana`.
3. Ajustar prioridades.
4. Definir 50 minutos por matéria e 20 de revisão.
5. Solicitar prévia.
6. Conferir seleção, capacidade e justificativas.
7. Alterar prioridade ou duração.
8. Recalcular.
9. Confirmar que nenhum bloco novo foi persistido.

## 12. Critérios de aceite

- Dado matéria em dois grupos, quando listar, então aparece uma vez.
- Dado ausência de prioridade, quando listar, então aparece Normal.
- Dado mesmas entradas, quando calcular duas vezes, então o resultado é igual.
- Dado capacidade suficiente, quando gerar, então busca até três matérias distintas.
- Dado capacidade insuficiente, quando gerar, então reduz e explica.
- Dado revisão manual, quando gerar, então não cria outra.
- Dado revisão sem espaço, quando gerar, então informa o motivo.
- Dado bloco manual, quando gerar, então ele consome capacidade.
- Dado `NAO_INCLUIR`, quando gerar, então a matéria não aparece.
- Dado a prévia, quando verificar o banco, então não existe bloco novo.
- Dado usuário B, quando acessar plano de A, então recebe 404.

## 13. Testes obrigatórios

- domínio: pesos, carga, alternância, divisão, limites e determinismo;
- aplicação: elegibilidade, deduplicação e montagem das entradas;
- integração: PostgreSQL, prioridades e isolamento;
- API: GET, PUT, POST, CSRF e erros;
- frontend: passos, vazios, recálculo e avisos;
- regressão do Planejamento Manual;
- propriedade: soma diária nunca excede disponibilidade;
- propriedade: ordem de entrada diferente produz mesma saída.

## 14. Arquivos provavelmente afetados

- `planejamento/dominio/**`;
- `planejamento/aplicacao/**`;
- `planejamento/infraestrutura/**`;
- `planejamento/api/**`;
- possível consulta em `concursos/aplicacao/**`;
- migration seguinte à última real;
- `frontend/src/modulos/planejamento/**`;
- OpenAPI e testes.

## 15. Ordem de implementação

- [x] confirmar `main`, Sprint 08 manual e última migration;
- [x] implementar elegibilidade;
- [x] persistir prioridades;
- [x] implementar gerador puro com testes;
- [x] criar prévia e endpoint;
- [x] integrar gaveta;
- [x] testar não persistência e A/B;
- [x] executar portas de qualidade.

## 16. Validação final

```bash
git diff --check
make testar-backend
make testar-frontend
make verificar-backend
make verificar-frontend
```

Validar manualmente:

- sem concurso ativo;
- sem cargo;
- poucas matérias;
- capacidade de 0, 70, 100 e 170 minutos;
- revisão manual;
- duas prévias idênticas;
- Swagger;
- 390, 768 e 1280 px.

## 17. Registro de conclusão

```text
STATUS: Concluída em 20/07/2026 na branch `feature/geracao-deterministica-sprint-01`.
ARQUIVOS ALTERADOS: módulo `planejamento` no backend e frontend, consulta pública estreita em `concursos.aplicacao`, migration V11, testes e OpenAPI.
DECISÕES TOMADAS: prioridades Normal não são persistidas; ausência mantém o padrão Normal. A prévia recalcula integralmente no backend, ordena explicitamente todas as entradas e nunca cria, altera ou exclui blocos. Aplicação e regeneração permaneceram fora do escopo.
TESTES EXECUTADOS: 88 testes backend e 79 testes frontend; testes de domínio, propriedade, PostgreSQL/Flyway, API, CSRF, isolamento A/B, OpenAPI, arquitetura, tipos, lint, build e formatação.
VALIDAÇÃO MANUAL: fluxo real Prioridades → Configuração → Prévia validado em navegador a 390, 768 e 1280 px, sem rolagem horizontal, com sete dias, avisos/justificativas e indicação explícita de que nada foi aplicado. Swagger e migration V11 validados pelos testes de integração.
PENDÊNCIAS: nenhuma dentro da Sprint 01. Aplicação da prévia e regeneração pertencem à Sprint 02.
PRÓXIMA SPRINT: Sprint 02 — Aplicação e regeneração segura
```
