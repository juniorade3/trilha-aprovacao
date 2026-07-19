# Sprint 05 — Execução dos blocos

> Ler primeiro: `CONTEXTO-COMUM.md`.

## 1. Objetivo

Permitir iniciar um bloco planejado, recuperar a execução após recarregar a página e encerrá-la como concluída ou parcialmente concluída.

## 2. Valor entregue

O plano passa a ser executável. O usuário consegue sair da intenção semanal e registrar o que realmente realizou, ainda sem alimentar automaticamente o histórico de estudos.

## 3. Dependências

Sprints 01 a 04 concluídas.

## 4. Escopo

- criar `ExecucaoDoBloco`;
- implementar estados reais de execução no bloco;
- iniciar bloco do dia ou atrasado;
- garantir uma execução em andamento por usuário;
- recuperar execução em andamento na visão Hoje;
- exibir cronômetro derivado de `iniciadaEm`;
- concluir integralmente;
- concluir parcialmente;
- registrar duração executada e observação;
- refletir resultado nas visões Hoje e Semana;
- tornar finalização idempotente.

## 5. Fora do escopo

- criar `RegistroDeEstudo` automaticamente;
- pausa e retomada;
- múltiplas execuções por bloco;
- dificuldade, foco, confiança ou motivo estruturado;
- correção da execução;
- reagendamento;
- iniciar bloco futuro.

## 6. Regras de negócio

1. Apenas bloco `PLANEJADO` de plano `ATIVO` pode iniciar.
2. A data do bloco precisa ser hoje ou anterior à data informada.
3. Bloco futuro deve ser reagendado antes de iniciar.
4. Um usuário pode ter no máximo uma execução em andamento.
5. Um bloco pode ter no máximo uma execução.
6. Iniciar muda o bloco para `EM_ANDAMENTO` e cria a execução na mesma transação.
7. Duração executada deve estar entre 1 e 1.440 minutos.
8. Conclusão muda o bloco para `CONCLUIDO`.
9. Interrupção com estudo realizado muda para `PARCIALMENTE_CONCLUIDO`.
10. Bloco em andamento não pode ser editado, excluído ou iniciado novamente.
11. Finalização repetida retorna a execução já encerrada quando os dados forem equivalentes; não cria novo fato.
12. Não existe pausa e retomada.

## 7. Modelo de domínio

### Classes

- `ExecucaoDoBloco`;
- `ResultadoDaExecucao`, com `CONCLUIDO` e `PARCIALMENTE_CONCLUIDO`;
- expandir `EstadoDoBlocoDeEstudo`.

### Comportamentos

`BlocoDeEstudo`:

- `iniciar()`;
- `concluir()`;
- `concluirParcialmente()`;
- `exigirPlanejado()`;
- `exigirEmAndamento()`.

`ExecucaoDoBloco`:

- `iniciar(usuario, bloco, momento)`;
- `encerrar(resultado, duracao, observacao)`;
- `estaEmAndamento()`.

### Invariantes

- uma execução por bloco;
- apenas uma aberta por usuário;
- início e encerramento coerentes;
- duração válida;
- resultado obrigatório ao encerrar.

## 8. Backend

### Aplicação

Expandir `ServicoDePlanejamento`:

- `iniciarBloco(usuario, bloco, dataDeReferencia)`;
- `concluirBloco(usuario, bloco, duracao, observacao)`;
- `interromperBloco(usuario, bloco, duracao, observacao)`;
- consulta da execução em andamento.

Usar transação para salvar bloco e execução juntos.

### Persistência

Esperado:

- `ExecucaoDoBlocoPersistida`;
- `RepositorioDeExecucoesDeBloco`.

Migration esperada: `V8__cria_execucoes_de_blocos.sql`.

Campos mínimos:

- identificador;
- usuario_id;
- bloco_id;
- iniciada_em;
- encerrada_em;
- duracao_executada_em_minutos;
- resultado;
- observacao;
- timestamps;
- versão.

Constraints:

- FK para usuário e bloco;
- `UNIQUE (bloco_id)`;
- check de duração e resultado;
- índice por usuário e início;
- índice parcial único para `usuario_id` quando `encerrada_em IS NULL`, se suportado pela migration PostgreSQL;
- sem cascata destrutiva.

### API

Criar `ControladorDeExecucoesDeBloco` ou manter as ações em `ControladorDeBlocosDeEstudo` sem ultrapassar responsabilidade clara.

A resposta deve conter bloco atualizado e execução.

### Concorrência

Testar duas tentativas simultâneas de iniciar blocos diferentes do mesmo usuário. Uma deve vencer e a outra receber 409.

## 9. Frontend

### Visão Hoje

Próximo bloco ganha ação “Iniciar”.

Durante execução:

- cartão destacado “Em andamento”;
- cronômetro calculado no cliente;
- título, atividade e duração prevista;
- ações “Concluir” e “Interromper”.

### Encerramento

Usar `ModalDaAplicacao` com:

- duração realizada;
- resultado definido pela ação escolhida;
- observação opcional;
- explicação de que o histórico de estudos será integrado na próxima sprint.

### Recuperação

Ao recarregar:

- consultar Hoje novamente;
- recuperar execução aberta;
- reconstruir cronômetro por `iniciadaEm`;
- não armazenar temporizador em localStorage.

### Semana

Mostrar badges de planejado, em andamento, concluído e parcial.

## 10. Contrato da API

| Método | Rota | Finalidade | Entrada | Saída | Códigos |
| --- | --- | --- | --- | --- | --- |
| POST | `/api/v1/blocos-de-estudo/{id}/inicio` | iniciar bloco | data de referência | bloco + execução | 200, 404, 409, 422 |
| POST | `/api/v1/blocos-de-estudo/{id}/conclusao` | concluir bloco | duração + observação | bloco + execução | 200, 400, 404, 409, 422 |
| POST | `/api/v1/blocos-de-estudo/{id}/interrupcao` | concluir parcialmente | duração + observação | bloco + execução | 200, 400, 404, 409, 422 |

## 11. Fluxo principal

1. Usuário abre Hoje.
2. Seleciona “Iniciar” no próximo bloco.
3. Sistema cria execução e mostra cronômetro.
4. Usuário recarrega ou continua na página.
5. Seleciona concluir ou interromper.
6. Informa duração realizada.
7. Sistema encerra execução e atualiza o dia.

## 12. Critérios de aceite

- Dado bloco planejado de hoje, quando iniciar, então fica em andamento e cria uma execução.
- Dado outro bloco em andamento, quando iniciar um segundo, então recebe 409.
- Dado bloco futuro, quando iniciar, então recebe 422.
- Dado execução aberta, quando recarregar a página, então o cronômetro é reconstruído.
- Dado conclusão válida, então bloco e execução são atualizados na mesma transação.
- Dado repetição da mesma conclusão após perda de resposta, então não cria outra execução.
- Dado duração zero ou acima de 1.440, então recebe erro.
- Dado usuário B, quando iniciar ou finalizar bloco de A, então não acessa o recurso.

## 13. Testes obrigatórios

- domínio: todas as transições de bloco e execução;
- aplicação: início, conclusão, parcial, idempotência e concorrência;
- infraestrutura: unique por bloco e execução aberta por usuário;
- API: ações, CSRF, códigos e A/B;
- frontend: iniciar, cronômetro, refresh, concluir e interromper;
- regressão: Semana, navegação e registro rápido global.

## 14. Arquivos provavelmente afetados

- `planejamento/dominio/ExecucaoDoBloco.java` e enum;
- `BlocoDeEstudo.java`;
- `planejamento/infraestrutura/ExecucaoDoBlocoPersistida.java` e repositório;
- `V8__cria_execucoes_de_blocos.sql`;
- `ServicoDePlanejamento.java`;
- controllers e DTOs de execução;
- `ConsultaDoPlanejamentoDeHoje.java`;
- páginas Hoje/Semana, API e specs.

## 15. Ordem de implementação

- [ ] criar transições de domínio e testes;
- [ ] criar V8 e persistência;
- [ ] implementar transações de início/fim;
- [ ] testar concorrência e idempotência;
- [ ] expor ações e OpenAPI;
- [ ] criar UX de execução e cronômetro;
- [ ] testar refresh e regressões;
- [ ] executar portas e registrar.

## 16. Validação final

Executar `make verificar` e `git diff --check`.

No navegador:

1. iniciar bloco;
2. recarregar;
3. tentar iniciar outro;
4. concluir;
5. iniciar outro e interromper;
6. tentar iniciar bloco futuro;
7. conferir Semana e Swagger.

## 17. Registro de conclusão

```text
STATUS:
ARQUIVOS ALTERADOS:
DECISÕES TOMADAS:
TESTES EXECUTADOS:
PENDÊNCIAS:
PRÓXIMA SPRINT: Sprint 06 — Integração com estudos
```
