# Sprint 06 — Integração com o histórico de estudos

> Ler primeiro: `CONTEXTO-COMUM.md`.

## 1. Objetivo

Fazer com que a conclusão de um bloco vinculado a tópico gere um `RegistroDeEstudo` no módulo existente, sem duplicar registros e sem acessar a persistência de estudos diretamente.

## 2. Valor entregue

O usuário executa o plano e o histórico já existente é atualizado automaticamente. Planejamento e acompanhamento passam a compartilhar o mesmo fato de estudo.

## 3. Dependências

Sprints 01 a 05 concluídas.

## 4. Escopo

- vincular execução a `RegistroDeEstudo`;
- integrar conclusão e interrupção com `ServicoDeMateriaisEEstudos` ou serviço estreito de `estudos.aplicacao`;
- criar registro quando o bloco possuir tópico;
- permitir escolher tópico no encerramento de bloco ligado apenas à matéria;
- concluir sem registro quando não houver tópico;
- tornar a integração idempotente;
- permitir registrar no histórico execuções já concluídas na Sprint 05 e ainda não vinculadas;
- mostrar vínculo e acesso ao Histórico na interface;
- atualizar correlações e OpenAPI.

## 5. Fora do escopo

- vínculo do bloco com material;
- detectar duplicidade com registro rápido manual;
- avaliar qualidade da sessão;
- evidências genéricas;
- análise de aderência;
- correção da execução, que fica para a Sprint 07.

## 6. Regras de negócio

1. Execução finalizada com tópico deve possuir no máximo um registro de estudo vinculado.
2. O tópico precisa pertencer ao usuário autenticado.
3. Se o bloco já possuir tópico, ele é usado automaticamente.
4. Se o bloco possuir apenas matéria, o usuário pode selecionar um tópico daquela matéria ao encerrar.
5. Se não houver tópico, a execução continua válida sem estudo.
6. O registro usa material nulo.
7. `dataHora` do estudo usa `iniciadaEm` da execução.
8. A duração usa `duracaoExecutadaEmMinutos`.
9. Repetir a finalização ou a ação de registrar no histórico não cria outro estudo.
10. O identificador do registro retornado é salvo na execução.
11. O módulo de planejamento não acessa `RepositorioDeRegistrosDeEstudo`.
12. Falha ao criar estudo deve impedir a confirmação final da integração e manter consistência transacional.

## 7. Modelo de domínio

### Alteração

Adicionar a `ExecucaoDoBloco`:

- `identificadorDoRegistroDeEstudo`, opcional;
- comportamento `vincularRegistroDeEstudo(id)`;
- validação para não substituir vínculo existente sem operação de correção.

Não criar nova entidade de integração.

### Relação

```text
ExecucaoDoBloco 0--1 RegistroDeEstudo
```

O vínculo é por identificador e não cria dependência do domínio de planejamento para a classe `RegistroDeEstudo`.

## 8. Backend

### Aplicação

Modificar finalização no `ServicoDePlanejamento`:

1. finalizar execução;
2. resolver tópico do bloco ou da requisição;
3. chamar serviço público de estudos;
4. vincular identificador retornado;
5. persistir execução e bloco na mesma operação lógica.

Se a transação entre módulos ocorrer no mesmo banco e mesma aplicação, usar `@Transactional` no serviço de planejamento.

Para execuções antigas sem vínculo, adicionar método equivalente a:

- `registrarExecucaoNoHistorico(usuario, execucao, topicoOpcional)`.

### Integração com estudos

Preferência inicial: reutilizar `ServicoDeMateriaisEEstudos.registrarEstudo(...)`.

Extrair serviço estreito dentro de `estudos.aplicacao` somente se:

- o método atual exigir dependências desnecessárias; ou
- os testes demonstrarem acoplamento inadequado.

Não criar interface de porta genérica sem uso adicional.

### Persistência

Migration esperada: `V9__vincula_execucoes_a_registros_de_estudo.sql`.

Adicionar:

- coluna nullable `registro_de_estudo_id`;
- FK para `registros_de_estudo`;
- `UNIQUE (registro_de_estudo_id)`;
- índice para consultas;
- sem cascata.

### API

A conclusão/interrupção passa a aceitar tópico opcional somente quando necessário.

Adicionar ação para execução já finalizada e não vinculada.

## 9. Frontend

### Encerramento do bloco

- informar claramente: “Ao concluir, este estudo será registrado no Histórico.”;
- quando bloco possui tópico, mostrar o tópico usado;
- quando possui apenas matéria, carregar tópicos dessa matéria e permitir escolha;
- quando é atividade livre, permitir concluir sem registro;
- não obrigar tópico para simulado ou atividade livre.

### Após concluir

- mostrar confirmação única;
- exibir link “Ver no Histórico” quando houver registro;
- atualizar Hoje e disparar o evento já utilizado pelo frontend, se necessário para atualizar dashboard/histórico;
- não abrir o `RegistroRapidoDeEstudo`.

### Execuções anteriores

Na Semana ou Hoje, execução finalizada com tópico e sem vínculo pode mostrar ação “Registrar no Histórico”. Não exibir essa ação quando já vinculada.

## 10. Contrato da API

| Método | Rota | Finalidade | Entrada | Saída | Códigos |
| --- | --- | --- | --- | --- | --- |
| POST | `/api/v1/blocos-de-estudo/{id}/conclusao` | concluir e integrar | duração, observação, tópico opcional | bloco, execução e estudo opcional | 200, 400, 404, 409, 422 |
| POST | `/api/v1/blocos-de-estudo/{id}/interrupcao` | concluir parcialmente e integrar | duração, observação, tópico opcional | bloco, execução e estudo opcional | 200, 400, 404, 409, 422 |
| POST | `/api/v1/execucoes-de-bloco/{id}/registro-de-estudo` | vincular execução antiga | tópico opcional | execução com registro | 200 ou 201, 404, 409, 422 |

A implementação deve escolher e documentar se a ação de vínculo retorna sempre `200` ou usa `201` na primeira criação. Não deixar comportamento diferente sem teste.

## 11. Fluxo principal

1. Usuário conclui bloco com tópico.
2. Sistema encerra execução.
3. Sistema cria estudo com o mesmo tópico e duração.
4. Execução guarda o identificador do estudo.
5. Hoje atualiza e oferece acesso ao Histórico.
6. Repetição da requisição devolve o mesmo vínculo.

## 12. Critérios de aceite

- Dado bloco com tópico, quando concluir, então cria um registro de estudo ativo.
- Dado execução já vinculada, quando repetir conclusão, então não cria duplicidade.
- Dado bloco apenas com matéria, quando escolher tópico da mesma matéria, então registra estudo.
- Dado tópico de outra matéria, quando encerrar, então recebe 422 ou 404 conforme escopo.
- Dado atividade livre sem tópico, quando concluir, então execução é válida e nenhum estudo é criado.
- Dado execução da Sprint 05 sem vínculo, quando usar “Registrar no Histórico”, então passa a ter um único registro.
- Dado usuário B, quando tentar vincular execução de A, então não acessa o recurso.

## 13. Testes obrigatórios

- domínio: vínculo único;
- aplicação: criação, ausência de tópico, seleção por matéria e idempotência;
- integração: transação planejamento + estudos e rollback em falha;
- persistência: FK e unicidade;
- API: respostas com e sem estudo, CSRF e A/B;
- frontend: tópico automático, seleção, atividade livre e link para Histórico;
- regressão: registro rápido e correção/cancelamento de estudos existentes.

## 14. Arquivos provavelmente afetados

- `ExecucaoDoBloco.java` e persistência;
- `V9__vincula_execucoes_a_registros_de_estudo.sql`;
- `ServicoDePlanejamento.java`;
- `ServicoDeMateriaisEEstudos.java` ou novo serviço estreito em `estudos.aplicacao`;
- DTOs de conclusão e respostas;
- páginas Hoje/Semana e `apiDePlanejamento.ts`;
- testes de planejamento, estudos e fluxo integrado.

## 15. Ordem de implementação

- [ ] modelar vínculo e migration;
- [ ] integrar serviço de estudos;
- [ ] garantir transação e idempotência;
- [ ] adaptar endpoints de encerramento;
- [ ] criar ação para execução antiga;
- [ ] adaptar modal e feedback frontend;
- [ ] testar regressões de estudos;
- [ ] executar portas e registrar.

## 16. Validação final

Executar `make verificar` e `git diff --check`.

No navegador:

1. concluir bloco com tópico;
2. abrir Histórico e conferir registro;
3. concluir bloco apenas com matéria escolhendo tópico;
4. concluir atividade livre sem tópico;
5. testar ação em execução antiga;
6. repetir ação e confirmar ausência de duplicidade;
7. conferir Swagger.

## 17. Registro de conclusão

```text
STATUS:
ARQUIVOS ALTERADOS:
DECISÕES TOMADAS:
TESTES EXECUTADOS:
PENDÊNCIAS:
PRÓXIMA SPRINT: Sprint 07 — Edição e replanejamento
```
