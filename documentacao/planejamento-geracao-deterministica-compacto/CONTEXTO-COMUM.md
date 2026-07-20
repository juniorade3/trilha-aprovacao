# Contexto comum — Geração Determinística

## 1. Objetivo estável

Adicionar ao módulo `planejamento` uma geração semanal determinística que transforme disponibilidade, matérias elegíveis, prioridades, blocos preservados e durações em uma prévia explicável e, após confirmação, em blocos reais.

## 2. Arquitetura obrigatória

- preservar o monólito modular atual;
- domínio sem Spring, JPA, HTTP, JSON, PostgreSQL ou Vue;
- usar `dominio`, `aplicacao`, `infraestrutura` e `api` conforme o padrão existente;
- entidades JPA com sufixo `Persistido`;
- controllers e DTOs em `api`;
- usuário obtido exclusivamente da sessão;
- nenhuma requisição recebe `identificadorDoUsuario`;
- não acessar repositórios JPA de outro módulo;
- usar PostgreSQL e Flyway;
- manter `ddl-auto=validate`;
- reutilizar cliente HTTP, CSRF, sessão, modais e componentes existentes;
- não criar arquitetura paralela ou abstrações sem uso imediato.

## 3. Matéria elegível

Matéria pessoal não arquivada vinculada ao concurso ativo e ao cargo selecionado:

```text
Concurso ativo
→ Cargo selecionado
→ Provas
→ Grupos de conteúdo
→ Matérias da prova
→ Matéria pessoal
```

Regras:

1. exigir concurso ativo;
2. exigir cargo selecionado;
3. deduplicar pela matéria pessoal;
4. excluir matéria arquivada;
5. não exigir item de edital mapeado;
6. não exigir tópico;
7. não usar registros de estudo, desempenho ou erros;
8. usar ordem estável da estrutura, nome normalizado e UUID como desempates.

Quando necessário, criar consulta pública estreita em `concursos.aplicacao`. O planejamento não acessa repositórios de concursos diretamente.

## 4. Prioridade manual

`PrioridadeDaMateriaNoPlano`:

- `ALTA`: peso 3;
- `NORMAL`: peso 2;
- `BAIXA`: peso 1;
- `NAO_INCLUIR`: peso 0.

Regras:

- ausência de registro significa `NORMAL`;
- uma prioridade por plano e matéria;
- somente plano `RASCUNHO` aceita alteração;
- `NAO_INCLUIR` exclui apenas daquela semana;
- prioridade não altera catálogo nem concurso.

## 5. Configuração

- `duracaoPadraoDoBlocoPrincipalEmMinutos`: padrão 50, mínimo 25, máximo 180;
- `duracaoDoBlocoDeRevisaoEmMinutos`: padrão 20, mínimo 0, máximo 120;
- `substituirBlocosGerados`: somente na aplicação, padrão `false`.

Meta fixa:

```text
até três matérias distintas por dia
```

## 6. Capacidade diária

```text
minutosLivres = disponibilidade - duração dos blocos preservados não cancelados
```

- nunca ultrapassar a disponibilidade;
- disponibilidade zero não recebe sugestão;
- bloco manual com matéria conta para a meta de matérias;
- atividade livre consome capacidade, mas não conta como matéria;
- revisão não conta como matéria;
- na regeneração, gerados puros são substituíveis e não consomem a nova prévia.

## 7. Revisão

- tipo `REVISAO`;
- título `Revisão do dia`;
- sem matéria e tópico;
- no máximo uma por dia;
- reservada antes dos blocos principais;
- não criada se duração for zero;
- não criada se já existir revisão preservada;
- não criada se a duração completa não couber;
- ausência gera aviso;
- não escolhe conteúdo automaticamente.

## 8. Três matérias por dia

Depois de reservar a revisão:

1. contar matérias distintas já presentes em blocos preservados;
2. calcular quantas faltam para chegar a três;
3. limitar pelas candidatas ainda não usadas no dia;
4. limitar pelos blocos mínimos de 25 minutos que cabem;
5. selecionar;
6. distribuir duração.

A mesma matéria não se repete no mesmo dia.

## 9. Algoritmo determinístico

Usar rodízio ponderado por carga normalizada.

```text
peso = 3, 2 ou 1
cargaNormalizada = minutos já planejados na semana / peso
```

Ordem de escolha:

1. ainda não usada no dia;
2. evitar repetição no dia anterior quando houver alternativa;
3. menor carga normalizada;
4. maior prioridade;
5. menor número de ocorrências na semana;
6. ordem estável da estrutura;
7. nome normalizado;
8. UUID.

Blocos manuais com matéria entram no cálculo da carga.

Proibido usar `Random`, hora atual, ordem acidental de coleção ou IA.

## 10. Duração

```text
duracaoBase = min(duracaoPadrao, floor(minutosLivresDepoisDaRevisao / quantidade))
```

- nenhum bloco principal gerado tem menos de 25 minutos;
- nenhum ultrapassa o padrão;
- diferença de até um minuto pode ser distribuída pela ordem;
- minutos excedentes ficam livres;
- não criar blocos extras só para preencher o tempo;
- bloco principal inicial usa tipo `TEORIA`;
- possui matéria, mas não tópico.

## 11. Justificativas

Formato:

```text
codigo
mensagem
```

Códigos iniciais:

- `PRIORIDADE_ALTA`;
- `PRIORIDADE_NORMAL`;
- `PRIORIDADE_BAIXA`;
- `EQUILIBRIO_DA_SEMANA`;
- `ALTERNANCIA_ENTRE_DIAS`;
- `META_DE_TRES_MATERIAS`;
- `PRESERVA_BLOCO_MANUAL`;
- `DISPONIBILIDADE_INSUFICIENTE`;
- `POUCAS_MATERIAS_ELEGIVEIS`;
- `REVISAO_RESERVADA`;
- `REVISAO_JA_EXISTENTE`;
- `REVISAO_NAO_CABE`;
- `MINUTOS_LIVRES_NAO_UTILIZADOS`.

Persistir apenas um resumo textual no bloco aplicado. Não criar tabela genérica de explicabilidade.

## 12. Origem dos blocos

`OrigemDoBlocoDeEstudo`:

- `MANUAL`;
- `GERADO_DETERMINISTICAMENTE`;
- `GERADO_AJUSTADO_MANUALMENTE`.

- blocos antigos recebem `MANUAL`;
- aplicação cria `GERADO_DETERMINISTICAMENTE`;
- primeira edição manual muda para ajustado;
- regeneração remove somente gerados puros;
- origem não é estado de execução.

## 13. Prévia e aplicação

### Prévia

- exige plano `RASCUNHO`;
- não grava blocos;
- retorna sete dias;
- diferencia preservados e sugeridos;
- informa capacidade, avisos e justificativas;
- mesmas entradas produzem a mesma saída.

### Aplicação

- exige plano `RASCUNHO`;
- recalcula tudo no backend;
- é transacional;
- não confia em blocos enviados pelo frontend;
- com gerados existentes e `substituir=false`, retorna 409;
- com `substituir=true`, remove somente gerados puros;
- preserva manuais e ajustados;
- normaliza ordens.

## 14. Contratos REST base

| Método | Rota | Finalidade |
| --- | --- | --- |
| GET | `/api/v1/planos-semanais/{id}/materias-para-geracao` | elegíveis e prioridades |
| PUT | `/api/v1/planos-semanais/{id}/prioridades-de-materias` | substituir prioridades |
| POST | `/api/v1/planos-semanais/{id}/geracao-deterministica/previa` | calcular sem persistir |
| POST | `/api/v1/planos-semanais/{id}/geracao-deterministica` | aplicar ou regenerar |

Códigos: 200, 400, 404, 409 e 422 conforme o contrato atual.

## 15. Frontend

Integrar à visão Semana em uma única gaveta:

```text
Prioridades → Configuração → Prévia → Aplicação
```

Reutilizar cliente HTTP, `GavetaLateral`, `EstadoDaPagina`, `ModalDaAplicacao` e estilos existentes. Não criar Pinia para estado local.

Estados mínimos:

- carregando;
- sem concurso;
- sem cargo;
- sem matérias;
- configurando;
- calculando;
- prévia pronta;
- aplicando;
- conflito de regeneração;
- erro recuperável;
- sucesso.

## 16. Testes transversais

- isolamento A/B;
- CSRF;
- determinismo;
- desempates;
- matéria duplicada em grupos;
- `NAO_INCLUIR`;
- três matérias quando houver capacidade;
- redução quando não houver;
- revisão única;
- preservação de manual e ajustado;
- regeneração seletiva;
- bloqueio em plano ativo;
- migrations em PostgreSQL vazio;
- OpenAPI;
- 390, 768 e 1280 px;
- teclado e foco;
- regressão do Planejamento Manual.

## 17. Fora do escopo

- seleção de tópico;
- revisão automática;
- Motor de Evidências;
- análise de desempenho;
- IA;
- otimizador externo;
- notificações;
- calendário;
- geração para plano ativo;
- exclusão de execuções ou estudos.
