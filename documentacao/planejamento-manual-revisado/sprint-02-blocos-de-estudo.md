# Sprint 02 — Blocos de estudo manuais

> Ler primeiro: `CONTEXTO-COMUM.md`.

## 1. Objetivo

Permitir criar, editar, remover e ordenar blocos de estudo dentro de um plano semanal em rascunho.

## 2. Valor entregue

O usuário transforma disponibilidade em uma semana concreta, distribuindo matérias, tópicos e atividades livres manualmente.

## 3. Dependências

Sprint 01 concluída e validada.

## 4. Escopo

- criar domínio e persistência de `BlocoDeEstudo`;
- adicionar bloco a um plano rascunho;
- editar bloco planejado;
- excluir fisicamente bloco de rascunho;
- vincular opcionalmente matéria e tópico;
- permitir atividade livre sem matéria;
- definir título, tipo, data, duração, ordem, horário e observação;
- reordenar blocos de um dia por ação acessível;
- retornar blocos na consulta do plano;
- exibir carga planejada e excesso por dia;
- integrar editor de bloco à página Semana.

## 5. Fora do escopo

- ativação do plano;
- execução;
- cancelamento lógico;
- reagendamento de plano ativo;
- drag-and-drop;
- vínculo com material de estudo;
- automações e recomendações.

## 6. Regras de negócio

1. Bloco só pode ser criado, editado ou excluído em plano `RASCUNHO`.
2. A data do bloco deve pertencer à semana.
3. Duração prevista deve estar entre 1 e 1.440 minutos.
4. Título e tipo são obrigatórios.
5. Matéria é opcional.
6. Tópico exige matéria e deve pertencer a ela.
7. Matéria e tópico precisam pertencer ao usuário autenticado.
8. Bloco livre sem matéria é permitido.
9. A ordem é única por dia e normalizada após inclusão, remoção ou reordenação.
10. Horário é opcional e não substitui a ordem.
11. Excesso de disponibilidade é permitido no rascunho, mas precisa ser mostrado.
12. A carga diária considera todos os blocos do dia nesta sprint.
13. Exclusão física não deixa lacunas na ordem.

## 7. Modelo de domínio

### Classes

- `BlocoDeEstudo`;
- `TipoDeAtividade`;
- `EstadoDoBlocoDeEstudo`, inicialmente usando apenas `PLANEJADO`.

### Comportamentos

- `BlocoDeEstudo.criar(...)`;
- `alterarPlanejamento(...)`;
- `moverPara(data, ordem)` apenas para rascunho;
- validação de título, duração e data.

As validações que dependem do plano, da matéria ou do tópico ficam no `ServicoDePlanejamento`, usando os serviços públicos de `conteudos`.

### Exceções

- plano não editável;
- bloco inexistente;
- tópico incompatível;
- conteúdo de outro usuário;
- data, duração ou ordem inválida.

## 8. Backend

### Aplicação

Expandir `ServicoDePlanejamento` com métodos equivalentes a:

- `adicionarBloco(usuario, plano, dados)`;
- `alterarBloco(usuario, bloco, dados)`;
- `excluirBloco(usuario, bloco)`;
- `reordenarBlocos(usuario, plano, data, identificadoresOrdenados)`.

Para validar matéria e tópico, reutilizar `ServicoDeMaterias` e `ServicoDeTopicos`; não acessar repositórios de `conteudos`.

### Persistência

Esperado:

- `BlocoDeEstudoPersistido`;
- `RepositorioDeBlocosDeEstudo`.

Migration esperada: `V7__cria_blocos_de_estudo.sql`.

Constraints e índices:

- FK para plano;
- FKs opcionais para matéria e tópico;
- check de duração;
- check de estado e tipo;
- índice por plano/data/ordem;
- timestamps e versão;
- sem cascata destrutiva.

A unicidade da ordem deve ser mantida transacionalmente. Não introduzir índice parcial complexo se a normalização do serviço e os testes já garantirem o comportamento.

### API

Criar `ControladorDeBlocosDeEstudo` e DTOs específicos.

A resposta do plano da Sprint 01 passa a incluir os blocos agrupáveis por data.

### Erros

- 400: entrada malformada;
- 404: plano, bloco, matéria ou tópico não encontrado no escopo do usuário;
- 409: plano não editável ou concorrência;
- 422: regra de conteúdo, data ou duração inválida.

## 9. Frontend

### Página Semana

Adicionar:

- botão “Adicionar bloco” em cada dia;
- lista ordenada de blocos no cartão do dia;
- resumo `planejado / disponível`;
- aviso de excesso;
- ações editar, mover para cima, mover para baixo e excluir;
- estado vazio por dia.

### Editor

Usar `GavetaLateral` para preservar a semana visível.

Campos:

- título;
- tipo de atividade;
- matéria opcional;
- tópico opcional filtrado pela matéria;
- data;
- duração prevista;
- horário opcional;
- observação.

Comportamentos:

- ao escolher tópico, manter matéria coerente;
- ao trocar matéria, limpar tópico incompatível;
- sugerir título a partir do tópico somente enquanto o usuário não tiver digitado título próprio;
- salvar e devolver foco à ação que abriu a gaveta;
- confirmação de exclusão em `ModalDaAplicacao`.

### API e tipos

Expandir `apiDePlanejamento.ts`. Não criar store Pinia.

## 10. Contrato da API

| Método | Rota | Finalidade | Entrada | Saída | Códigos |
| --- | --- | --- | --- | --- | --- |
| POST | `/api/v1/planos-semanais/{plano}/blocos` | adicionar bloco | dados do bloco | bloco criado | 201, 400, 404, 409, 422 |
| PUT | `/api/v1/blocos-de-estudo/{id}` | editar bloco | dados completos | bloco atualizado | 200, 400, 404, 409, 422 |
| DELETE | `/api/v1/blocos-de-estudo/{id}` | excluir bloco de rascunho | nenhuma | sem corpo | 204, 404, 409 |
| PUT | `/api/v1/planos-semanais/{plano}/ordem-dos-blocos` | reordenar um dia | data + ids ordenados | plano atualizado | 200, 400, 404, 409, 422 |

## 11. Fluxo principal

1. Usuário abre uma semana em rascunho.
2. Seleciona “Adicionar bloco” em um dia.
3. Escolhe atividade livre ou matéria/tópico.
4. Informa duração e salva.
5. O bloco aparece no dia e atualiza a carga.
6. Usuário edita ou reordena sem sair da Semana.

## 12. Critérios de aceite

- Dado plano rascunho, quando criar bloco livre válido, então ele aparece no dia correto.
- Dado tópico de uma matéria, quando criar bloco com outra matéria, então recebe erro.
- Dado usuário B, quando usar identificador de matéria ou tópico de A, então não cria bloco.
- Dado carga de 200 minutos e disponibilidade de 180, então o rascunho é salvo e a interface mostra excesso de 20 minutos.
- Dado três blocos, quando mover o terceiro para cima, então a ordem fica contínua e persistida.
- Dado bloco de rascunho, quando excluir, então a resposta é 204 e os demais são reordenados.
- Dado a gaveta de edição, quando fechar, então semana e rolagem são preservadas.

## 13. Testes obrigatórios

- domínio: fábrica, alteração, título, duração e estado;
- aplicação: conteúdo válido, escopo, ordem e exclusão;
- infraestrutura: FKs, índices, persistência e isolamento;
- API: CRUD, `Location`, CSRF, erros e A/B;
- frontend: criação livre, criação com tópico, edição, exclusão e reordenação;
- acessibilidade: gaveta, modal, foco e teclado;
- regressão: disponibilidade da Sprint 01.

## 14. Arquivos provavelmente afetados

- `planejamento/dominio/BlocoDeEstudo.java` e enums;
- `planejamento/infraestrutura/BlocoDeEstudoPersistido.java` e repositório;
- `planejamento/aplicacao/ServicoDePlanejamento.java`;
- `planejamento/api/ControladorDeBlocosDeEstudo.java` e DTOs;
- `V7__cria_blocos_de_estudo.sql`;
- `modulos/planejamento/PlanejamentoSemanaPagina.vue`;
- `modulos/planejamento/apiDePlanejamento.ts`;
- componentes locais do módulo e specs;
- estilos necessários em `principal.scss`.

## 15. Ordem de implementação

- [x] confirmar Sprint 01 verde;
- [x] criar domínio e testes;
- [x] criar V7 e persistência;
- [x] expandir serviço e validações com conteúdos;
- [x] criar API/OpenAPI;
- [x] criar editor e lista de blocos;
- [x] implementar reordenação acessível;
- [x] executar testes e portas;
- [x] preencher registro.

## 16. Validação final

Executar `make verificar` e `git diff --check`.

No navegador:

1. criar blocos livre e com tópico;
2. alterar duração e dia;
3. reordenar;
4. provocar excesso;
5. excluir;
6. testar em 390, 768 e 1280 px;
7. conferir Swagger.

## 17. Registro de conclusão

```text
STATUS: concluída
ARQUIVOS ALTERADOS: domínio, aplicação, persistência e API de planejamento;
migration V7; contrato OpenAPI; página Semana, cliente HTTP, editor, estilos e testes.
DECISÕES TOMADAS: bloco livre ou vinculado a matéria/tópico; ordem contínua por dia
normalizada transacionalmente; exclusão física somente no rascunho; reordenação por
botões acessíveis; excesso permitido e destacado sem impedir o salvamento.
TESTES EXECUTADOS: domínio, CRUD e isolamento A/B na API, PostgreSQL/Flyway,
constraints, FKs, índices, OpenAPI, CSRF, regressão da Sprint 01, fluxos Vue,
teclado/foco, build, lint, tipos, formato e auditoria de dependências.
PENDÊNCIAS: validação exploratória manual em navegador nas larguras de 390, 768 e 1280 px.
PRÓXIMA SPRINT: Sprint 03 não iniciada; depende de autorização explícita.
```
