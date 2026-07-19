# Planejamento Manual Estruturado

## Objetivo geral

Adicionar ao Trilha da Aprovação um módulo de planejamento manual que permita ao usuário declarar sua disponibilidade, montar uma semana de estudos, organizar blocos, consultar as visões **Semana** e **Hoje**, executar o que foi planejado e corrigir o plano sem depender de geração automática, IA, revisão espaçada ou Motor de Evidências.

A entrega deve produzir valor diário real e, ao mesmo tempo, registrar apenas os fatos mínimos necessários para a evolução posterior do produto.

## Linha de base validada no repositório

O planejamento foi revisado contra o repositório `juniorade3/trilha-aprovacao` no branch `main`.

Base considerada:

- produto funcional concluído até a antiga Sprint 8;
- `HEAD` atual em `044f780`, posterior ao commit de consolidação `b9dc628`;
- reformulação visual do frontend já versionada;
- backend organizado por capacidades em `br.com.trilhaaprovacao`;
- classes de domínio independentes de Spring e JPA;
- serviços de aplicação concretos, como `ServicoDeMaterias` e `ServicoDeMateriaisEEstudos`;
- entidades JPA com sufixo `Persistido` dentro de `infraestrutura`;
- repositórios Spring Data dentro de `infraestrutura`;
- controllers e DTOs em `api`;
- frontend organizado em `src/modulos`, com páginas, APIs e testes próximos;
- Vue Router em `src/aplicacao/roteamento/index.ts`;
- cliente HTTP central em `src/compartilhado/api/clienteHttp.ts`;
- componentes compartilhados de cabeçalho, estado, modal e gaveta já disponíveis;
- migrations existentes de `V1` a `V5`;
- nenhuma execução de CI associada ao `HEAD` pôde ser confirmada pelo GitHub.

Consequência: estas sprints não devem introduzir uma arquitetura paralela com uma classe por caso de uso, repositórios no domínio ou rotas REST incompatíveis com os padrões atuais.

## Divisão das sprints

| Sprint | Entrega | Dependências | Valor liberado |
| --- | --- | --- | --- |
| 01 | Plano semanal em rascunho e disponibilidade diária | nenhuma | o usuário registra quanto tempo possui em cada dia |
| 02 | Blocos de estudo manuais | 01 | a disponibilidade vira uma semana concreta de estudos |
| 03 | Ativação e visão Semana consolidada | 02 | o plano passa a ser validado e utilizável |
| 04 | Visão Hoje | 03 | o usuário identifica rapidamente o que estudar no dia |
| 05 | Execução dos blocos | 04 | o usuário inicia e encerra o que planejou |
| 06 | Integração com o histórico de estudos | 05 | execuções ligadas a tópico alimentam o histórico existente |
| 07 | Edição ativa, reagendamento e encerramento | 06 | o plano pode ser corrigido sem apagar fatos |
| 08 | Consolidação e aceite integral | 07 | jornada completa validada e documentada |

## Ordem recomendada

Executar na ordem numérica. Cada sprint deve ser concluída, validada e registrada antes da seguinte. O Codex deve trabalhar somente no arquivo da sprint autorizada e no `CONTEXTO-COMUM.md`.

## Decisão de navegação

O menu principal atual possui cinco entradas. Para não criar seis itens e não sobrecarregar a navegação móvel:

- a entrada principal **Histórico** será substituída por **Planejamento**;
- dentro do módulo existirão atalhos para **Hoje**, **Semana** e **Histórico**;
- a rota `/estudos` continuará existindo e não será recriada;
- na Sprint 01, `/planejamento` redirecionará para `/planejamento/semana`;
- na Sprint 04, o redirecionamento mudará para `/planejamento/hoje`.

Essa decisão preserva os cinco itens atuais e reúne o fluxo diário de estudo em uma jornada coerente.

## Princípios de incremento

- Cada sprint entrega backend e frontend integrados.
- O usuário sempre é obtido da sessão.
- A API usa DTOs e o contrato de erro existente.
- Regras de estado ficam no domínio ou no serviço de aplicação.
- O frontend usa dados reais da API.
- Migrations são incrementais e testadas em PostgreSQL vazio.
- O plano não é gerado automaticamente.
- “Próximo”, “atrasado” e “não realizado” são apresentações derivadas, não estados persistidos.
- Reagendamento é uma operação, não um estado.
- Execuções concluídas nunca são excluídas fisicamente.
- O módulo `planejamento` pode usar serviços públicos de `conteudos` e `estudos`, mas nunca seus repositórios JPA.

## Definição de pronto da entrega completa

A entrega estará pronta quando:

1. o usuário criar uma semana de segunda a domingo;
2. informar disponibilidade por dia;
3. criar blocos livres ou vinculados a matéria e tópico;
4. ordenar e ativar o plano;
5. consultar a Semana e o dia atual;
6. iniciar apenas um bloco por vez;
7. concluir integral ou parcialmente, registrando a duração executada;
8. gerar registro de estudo quando a execução possuir tópico;
9. editar ou reagendar bloco ainda planejado;
10. corrigir uma execução concluída;
11. encerrar ou cancelar o plano sem apagar execuções;
12. o usuário A não acessar dados do usuário B;
13. Swagger, testes, lint, build e fluxo manual passarem;
14. as telas funcionarem em 390, 768 e 1280 px, por teclado e sem rolagem horizontal obrigatória.

## Funcionalidades adiadas para o Motor de Evidências

Não implementar nesta entrega:

- cálculo de aderência ao plano;
- diagnóstico de padrões de adiamento;
- classificação de qualidade da sessão;
- confiança, foco, energia, humor ou dificuldade estruturados;
- eventos pedagógicos genéricos;
- análise automática de planejado versus executado;
- indicadores de risco, lacuna ou retenção;
- recomendação automática baseada no histórico.

## Funcionalidades adiadas para outros motores

Também ficam fora:

- geração automática de plano;
- priorização automática de matérias;
- revisão espaçada automática;
- Motor de Lacunas;
- Timefold ou outro otimizador;
- coach com LLM;
- notificações;
- calendário externo;
- gamificação;
- metas automáticas;
- banco de questões novo.

## Inconsistências que o Codex deve verificar antes da Sprint 01

- confirmar que `V5` ainda é a última migration antes de criar `V6`;
- executar `git status --short` e preservar qualquer alteração local posterior ao GitHub;
- confirmar que o `HEAD` local corresponde ao branch que será alterado;
- executar `make verificar` antes da implementação ou registrar por que não foi possível;
- não assumir que a ausência de status no GitHub significa que os testes falharam ou passaram.
