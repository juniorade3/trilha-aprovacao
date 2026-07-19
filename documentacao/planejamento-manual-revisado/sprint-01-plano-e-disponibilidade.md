# Sprint 01 — Plano semanal e disponibilidade

> Ler primeiro: `CONTEXTO-COMUM.md`.

## 1. Objetivo

Permitir que o usuário crie ou abra uma semana em rascunho e informe os minutos disponíveis para cada um dos sete dias.

## 2. Valor entregue

O usuário passa a registrar sua capacidade real antes de distribuir conteúdos. A aplicação já começa a organizar a rotina semanal, mesmo sem blocos de estudo.

## 3. Dependências

Nenhuma sprint desta entrega. Exige a base atual autenticada e as portas de qualidade do projeto disponíveis.

## 4. Escopo

- criar a capacidade backend `planejamento`;
- criar plano semanal em `RASCUNHO`;
- criar automaticamente sete disponibilidades com zero minuto;
- consultar plano pela segunda-feira da semana;
- substituir as sete disponibilidades em uma operação transacional;
- criar migration inicial do módulo;
- documentar endpoints no OpenAPI;
- criar entrada principal “Planejamento” no layout;
- criar `/planejamento` e `/planejamento/semana`;
- criar navegação secundária com Semana e Histórico;
- navegar entre semana atual, anterior e próxima;
- exibir estado vazio e ação “Criar esta semana”;
- editar minutos por dia;
- exibir total disponível da semana.

## 5. Fora do escopo

- blocos de estudo;
- ativação do plano;
- visão Hoje;
- execução;
- recorrência;
- horários de disponibilidade;
- cópia de semana;
- recomendações.

## 6. Regras de negócio

1. `dataInicial` é obrigatoriamente uma segunda-feira.
2. A data final é derivada como domingo.
3. Um usuário possui no máximo um plano para a mesma `dataInicial`.
4. O plano nasce `RASCUNHO`.
5. São criadas sete disponibilidades, uma por data, todas com zero minuto.
6. Cada disponibilidade pertence ao intervalo do plano.
7. Minutos permitidos: 0 a 1.440.
8. A atualização deve informar exatamente os sete dias, sem repetição.
9. O proprietário é sempre obtido da sessão.
10. Plano encerrado ou cancelado não é alterado.
11. Conflito de concorrência usa o tratamento atual de `@Version`.

## 7. Modelo de domínio

### Classes

- `PlanoSemanal`;
- `DisponibilidadeDoDia`;
- `EstadoDoPlanoSemanal`.

### Comportamentos

- `PlanoSemanal.criar(usuario, dataInicial)`;
- `PlanoSemanal.exigirEditavel()`;
- `DisponibilidadeDoDia.criar(plano, data)`;
- `DisponibilidadeDoDia.alterarMinutos(minutos)`;
- cálculo da data final e do total disponível no serviço de aplicação ou em método de domínio apropriado.

### Invariantes

- segunda-feira válida;
- sete datas consecutivas;
- minutos válidos;
- estado editável.

### Exceções

Usar `RegraDeDominio` para data/minutos inválidos e `ConflitoDeDominio` para duplicidade ou estado incompatível.

## 8. Backend

### Aplicação

Criar `ServicoDePlanejamento` com métodos equivalentes a:

- `criarPlanoSemanal(usuario, dataInicial)`;
- `obterPlanoSemanal(usuario, dataInicial)`;
- `alterarDisponibilidades(usuario, plano, disponibilidades)`.

Não criar três classes de caso de uso separadas.

### Persistência

Esperado:

- `PlanoSemanalPersistido`;
- `DisponibilidadeDoDiaPersistida`;
- `RepositorioDePlanosSemanais`;
- `RepositorioDeDisponibilidadesDoDia`.

Migration esperada: `V6__cria_planos_semanais_e_disponibilidades.sql`, após confirmar a última versão.

Constraints mínimas:

- FK de plano para usuário;
- `UNIQUE (usuario_id, data_inicial)`;
- FK de disponibilidade para plano;
- `UNIQUE (plano_id, data)`;
- check de minutos entre 0 e 1.440;
- check de estado válido;
- timestamps e versão.

### API

Criar `ControladorDePlanosSemanais` e DTOs seguindo os nomes atuais.

A resposta deve conter plano, estado, período, sete disponibilidades, totais e versão. Não expor entidade JPA.

### Erros

- 400: formato/Bean Validation;
- 404: plano não encontrado ou de outro usuário;
- 409: plano duplicado, estado não editável ou concorrência;
- 422: regra de domínio inválida.

### OpenAPI

Adicionar tag `Planejamento` e documentar sessão/CSRF pelo mecanismo já existente.

## 9. Frontend

### Rotas e navegação

Alterar `aplicacao/roteamento/index.ts`:

- `/planejamento` redireciona para `/planejamento/semana`;
- `/planejamento/semana` usa `PlanejamentoSemanaPagina.vue`.

Alterar `LayoutPrincipal.vue`:

- substituir o item “Histórico” por “Planejamento” no desktop e no mobile;
- manter `/estudos` acessível pela navegação secundária do módulo.

### Arquivos esperados

- `modulos/planejamento/PlanejamentoSemanaPagina.vue`;
- `modulos/planejamento/PlanejamentoSemanaPagina.spec.ts`;
- `modulos/planejamento/NavegacaoDoPlanejamento.vue`;
- `modulos/planejamento/apiDePlanejamento.ts`.

### Componentes e UX

- usar `CabecalhoDaPagina`;
- usar `EstadoDaPagina` para carregamento, ausência e erro;
- editor em cartões de dia, uma coluna no celular;
- inputs numéricos com rótulo e unidade “min”;
- salvar sem sair da página;
- feedback de sucesso com `role=status`;
- preservar a semana selecionada após recarregar;
- exibir total semanal em horas e minutos;
- não usar Pinia.

### Estados

- carregando;
- semana inexistente;
- plano carregado;
- salvando;
- erro recuperável;
- conflito com ação de recarregar.

## 10. Contrato da API

| Método | Rota | Finalidade | Entrada | Saída | Códigos |
| --- | --- | --- | --- | --- | --- |
| POST | `/api/v1/planos-semanais` | criar semana | `dataInicial` | plano com sete dias | 201, 400, 409, 422 |
| GET | `/api/v1/planos-semanais?dataInicial=AAAA-MM-DD` | consultar semana | query | plano completo | 200, 400, 404 |
| PUT | `/api/v1/planos-semanais/{id}/disponibilidades` | substituir sete dias | lista data/minutos | plano atualizado | 200, 400, 404, 409, 422 |

## 11. Fluxo principal

1. Usuário abre Planejamento.
2. Sistema consulta a semana atual.
3. Se não existir, mostra explicação e “Criar esta semana”.
4. Usuário cria a semana.
5. Informa os minutos de cada dia.
6. Salva.
7. Sistema mostra o total semanal e mantém o usuário na mesma página.

## 12. Critérios de aceite

- Dado usuário autenticado sem plano, quando criar a semana atual, então recebe plano em rascunho com sete dias.
- Dado plano existente, quando criar a mesma semana novamente, então recebe 409.
- Dado segunda-feira com 180 minutos, quando salvar, então a consulta retorna 180.
- Dado lista sem os sete dias, quando salvar, então recebe erro.
- Dado data que não seja segunda-feira, quando criar plano, então recebe erro de domínio.
- Dado usuário B, quando consultar identificador ou semana de A, então não obtém os dados de A.
- Dado viewport de 390 px, quando editar os sete dias, então não há rolagem horizontal obrigatória.
- Dado navegação principal, quando selecionar Planejamento, então Histórico continua acessível na navegação secundária.

## 13. Testes obrigatórios

- domínio: criação, segunda-feira, período, minutos e estado;
- aplicação: criação, duplicidade, sete dias, autorização e concorrência;
- infraestrutura: constraints e isolamento com PostgreSQL/Testcontainers;
- API: 201/Location, GET, PUT, CSRF, erros e A/B;
- OpenAPI: tag e rotas;
- frontend: vazio, criação, edição, erro, conflito e troca de semana;
- roteamento/layout: Planejamento no menu e Histórico acessível;
- regressão: sessão, registro rápido e demais rotas.

## 14. Arquivos provavelmente afetados

- `aplicativos/backend/src/main/java/br/com/trilhaaprovacao/planejamento/**`;
- `aplicativos/backend/src/main/resources/db/migration/V6__*.sql`;
- testes backend da nova capacidade;
- `ConfiguracaoDaDocumentacaoDaApi.java` e teste OpenAPI, se necessário;
- `aplicativos/frontend/src/aplicacao/roteamento/index.ts` e teste;
- `aplicativos/frontend/src/aplicacao/layouts/LayoutPrincipal.vue`;
- `aplicativos/frontend/src/modulos/planejamento/**`;
- `principal.scss`, apenas para estilos realmente necessários.

## 15. Ordem de implementação

- [x] registrar `git status` e confirmar última migration;
- [x] modelar domínio e testes;
- [x] criar V6 e validar banco vazio;
- [x] criar persistência e `ServicoDePlanejamento`;
- [x] expor API e OpenAPI;
- [x] criar rota, navegação e página Semana;
- [x] criar testes frontend;
- [x] executar portas afetadas;
- [x] preencher registro de conclusão.

## 16. Validação final

```bash
docker compose config
make testar-backend
make testar-frontend
make verificar-backend
make verificar-frontend
git diff --check
```

No navegador:

1. criar semana;
2. informar disponibilidades;
3. trocar para semana anterior e voltar;
4. recarregar;
5. abrir Histórico pela navegação secundária;
6. conferir Swagger e `/v3/api-docs`.

## 17. Registro de conclusão

```text
STATUS: concluída
ARQUIVOS ALTERADOS: módulo backend planejamento, migration V6, OpenAPI,
testes de integração afetados, módulo frontend planejamento, rotas, layout e estilos.
DECISÕES TOMADAS: semana identificada pela segunda-feira; plano nasce em rascunho
com sete dias em zero; semana selecionada preservada na query string; edição substitui
os sete dias em uma transação e usa sessão, CSRF e tratamento de concorrência existentes.
TESTES EXECUTADOS: domínio, API, PostgreSQL/Testcontainers, OpenAPI, arquitetura,
roteamento, layout e página Vue, além das portas completas de backend e frontend.
PENDÊNCIAS: validação exploratória manual no navegador.
PRÓXIMA SPRINT: Sprint 02 não iniciada; depende de autorização explícita.
```
