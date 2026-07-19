# Sprint 07 — Edição ativa, reagendamento e encerramento

> Ler primeiro: `CONTEXTO-COMUM.md`.

## 1. Objetivo

Permitir ajustar manualmente um plano ativo, reagendar ou cancelar blocos planejados, corrigir execuções e encerrar ou cancelar a semana sem apagar fatos.

## 2. Valor entregue

O planejamento deixa de ser rígido. O usuário consegue lidar com imprevistos reais e manter o plano confiável ao longo da semana.

## 3. Dependências

Sprints 01 a 06 concluídas.

## 4. Escopo

- editar bloco `PLANEJADO` de plano ativo;
- reagendar bloco dentro da mesma semana;
- cancelar bloco planejado;
- alterar disponibilidade de plano ativo quando a carga permitir;
- corrigir duração, resultado e observação de execução finalizada;
- sincronizar correção com estudo vinculado;
- encerrar plano ativo;
- cancelar plano rascunho ou ativo;
- preservar execuções e estudos;
- mostrar estados finais e ações coerentes em Hoje e Semana.

## 5. Fora do escopo

- mover bloco entre semanas;
- reabrir plano;
- reexecutar bloco concluído;
- histórico genérico de todas as alterações;
- motivo estruturado de reagendamento;
- replanejamento automático;
- Motor de Evidências.

## 6. Regras de negócio

1. Apenas bloco `PLANEJADO` pode ser editado, reagendado ou cancelado.
2. Reagendamento mantém o estado e incrementa `quantidadeDeReagendamentos`.
3. A nova data deve pertencer à mesma semana.
4. As ordens dos dias de origem e destino são normalizadas.
5. Bloco em andamento não pode ser alterado.
6. Bloco concluído ou parcial não pode ter seu planejamento alterado.
7. Plano ativo pode ter disponibilidade alterada se não ficar abaixo da carga ainda planejada.
8. Correção de execução aceita duração, resultado e observação válidos.
9. Se houver estudo vinculado, usar `corrigirEstudo` e atualizar o vínculo para o novo registro ativo.
10. Cancelar bloco não apaga execução ou estudo; como só planejado pode cancelar, normalmente não haverá execução.
11. Encerrar plano exige ausência de bloco em andamento.
12. Ao encerrar, blocos ainda planejados permanecem registrados e são apresentados como “Não realizados”.
13. Cancelar plano exige ausência de bloco em andamento.
14. Ao cancelar plano, blocos planejados passam a `CANCELADO`.
15. Execuções concluídas e estudos permanecem após encerramento ou cancelamento.
16. Plano encerrado ou cancelado não pode ser reaberto.

## 7. Modelo de domínio

### Comportamentos

`BlocoDeEstudo`:

- `reagendar(data, horario, ordem)`;
- `cancelar()`;
- `alterarPlanejamento(...)` em plano ativo, condicionado pelo serviço.

`ExecucaoDoBloco`:

- `corrigir(resultado, duracao, observacao, novoRegistroDeEstudo)`.

`PlanoSemanal`:

- `encerrar()`;
- `cancelar()`.

### Campos

Adicionar ao bloco, se ainda não existir:

- `quantidadeDeReagendamentos`;
- `reagendadoEm`, opcional.

Migration esperada somente se necessária: `V10__adiciona_reagendamento_aos_blocos.sql`.

Não criar tabela de eventos.

## 8. Backend

### Aplicação

Expandir `ServicoDePlanejamento`:

- `alterarBlocoAtivo(...)`;
- `reagendarBloco(...)`;
- `cancelarBloco(...)`;
- `alterarDisponibilidadesAtivas(...)`;
- `corrigirExecucao(...)`;
- `encerrarPlano(...)`;
- `cancelarPlano(...)`.

As operações devem ser transacionais e respeitar escopo de usuário.

### Integração com estudos

Na correção de execução integrada:

1. obter registro ativo vinculado;
2. chamar `corrigirEstudo`;
3. receber novo registro;
4. atualizar `identificadorDoRegistroDeEstudo` da execução;
5. preservar o registro anterior como corrigido pelo comportamento já existente.

### API

Manter `PUT /blocos-de-estudo/{id}` para edição comum. Criar ações explícitas para reagendamento, cancelamento e estados do plano.

### Erros

- 404: recurso fora do escopo;
- 409: estado incompatível, carga inválida ou concorrência;
- 422: data, duração, resultado ou tópico inválido.

## 9. Frontend

### Semana

- ações editar, reagendar e cancelar no menu do bloco planejado;
- `GavetaLateral` para editar;
- `ModalDaAplicacao` para reagendar e confirmar cancelamento;
- indicação discreta da quantidade de reagendamentos;
- ações “Encerrar semana” e “Cancelar plano” com explicação das consequências;
- plano encerrado mostra blocos não realizados sem permitir ação.

### Hoje

- bloco planejado pode ser reagendado ou cancelado;
- bloco concluído/parcial permite “Corrigir execução”;
- ao corrigir, atualizar o Histórico e a própria página;
- bloco em andamento explica por que não pode ser alterado.

### Disponibilidade

Plano ativo pode abrir o mesmo editor da Semana. Em caso de conflito, a mensagem deve indicar o dia e a carga mínima necessária.

### Preservação de contexto

Depois de editar, reagendar ou corrigir:

- permanecer na mesma data;
- devolver foco à ação correspondente;
- atualizar apenas dados necessários;
- não redirecionar ao dashboard.

## 10. Contrato da API

| Método | Rota | Finalidade | Entrada | Saída | Códigos |
| --- | --- | --- | --- | --- | --- |
| PUT | `/api/v1/blocos-de-estudo/{id}` | editar bloco planejado | dados completos | bloco | 200, 400, 404, 409, 422 |
| POST | `/api/v1/blocos-de-estudo/{id}/reagendamento` | mover bloco | data, horário, ordem | bloco | 200, 400, 404, 409, 422 |
| POST | `/api/v1/blocos-de-estudo/{id}/cancelamento` | cancelar bloco | nenhuma | bloco | 200, 404, 409 |
| PUT | `/api/v1/execucoes-de-bloco/{id}/correcao` | corrigir execução | duração, resultado, observação | execução | 200, 400, 404, 409, 422 |
| POST | `/api/v1/planos-semanais/{id}/encerramento` | encerrar semana | nenhuma | plano | 200, 404, 409 |
| POST | `/api/v1/planos-semanais/{id}/cancelamento` | cancelar plano | nenhuma | plano | 200, 404, 409 |

## 11. Fluxo principal

1. Usuário identifica um imprevisto.
2. Abre ação do bloco planejado.
3. Edita, reagenda ou cancela.
4. Sistema valida data, estado e capacidade.
5. Hoje e Semana são atualizados sem perder contexto.
6. Se necessário, usuário corrige execução concluída.
7. Ao final, encerra a semana; blocos pendentes aparecem como não realizados.

## 12. Critérios de aceite

- Dado bloco planejado de plano ativo, quando reagendar dentro da semana, então muda de dia e mantém estado.
- Dado bloco concluído, quando tentar reagendar, então recebe 409.
- Dado redução de disponibilidade abaixo da carga planejada, então recebe 409 com mensagem compreensível.
- Dado execução integrada, quando corrigir duração, então o estudo anterior fica corrigido e um novo registro ativo é vinculado.
- Dado plano com bloco em andamento, quando encerrar ou cancelar, então recebe 409.
- Dado plano encerrado com bloco planejado, então a interface o mostra como não realizado e somente leitura.
- Dado plano cancelado, então blocos planejados ficam cancelados e execuções permanecem.
- Dado confirmação de ação destrutiva, então não é usado `window.confirm`.

## 13. Testes obrigatórios

- domínio: reagendamento, cancelamento, correção e estados do plano;
- aplicação: ordem, capacidade, integração com estudos e transações;
- persistência: campo de reagendamento, estados e concorrência;
- API: todas as ações, erros, CSRF e A/B;
- frontend: gavetas, modais, foco e preservação de contexto;
- regressão: Hoje, Semana, execução, Histórico e registro rápido.

## 14. Arquivos provavelmente afetados

- modelos de domínio e persistência de plano, bloco e execução;
- `V10__*.sql`, se necessário;
- `ServicoDePlanejamento.java`;
- integração em `estudos.aplicacao`;
- controllers, DTOs e OpenAPI;
- páginas Hoje/Semana, API e specs;
- componentes locais de edição/reagendamento.

## 15. Ordem de implementação

- [ ] implementar regras de domínio;
- [ ] criar migration apenas se necessária;
- [ ] implementar edição/reagendamento/cancelamento;
- [ ] implementar correção integrada;
- [ ] implementar encerramento/cancelamento do plano;
- [ ] expor API/OpenAPI;
- [ ] adaptar UX e acessibilidade;
- [ ] executar regressões e portas;
- [ ] preencher registro.

## 16. Validação final

Executar `make verificar` e `git diff --check`.

No navegador:

1. editar bloco ativo planejado;
2. reagendar entre dois dias;
3. cancelar bloco;
4. provocar conflito de disponibilidade;
5. corrigir execução e conferir Histórico;
6. tentar encerrar com bloco em andamento;
7. encerrar com pendentes;
8. cancelar outro plano;
9. conferir Swagger.

## 17. Registro de conclusão

```text
STATUS:
ARQUIVOS ALTERADOS:
DECISÕES TOMADAS:
TESTES EXECUTADOS:
PENDÊNCIAS:
PRÓXIMA SPRINT: Sprint 08 — Consolidação e aceite
```
