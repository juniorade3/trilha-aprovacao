# Sprint 03 — Consolidação e aceite integral
## Roteiro técnico executável para o Codex

> Este documento substitui a versão genérica da Sprint 03.
> Ele foi produzido após análise da `main` do repositório `juniorade3/trilha-aprovacao` depois da conclusão das Sprints 01 e 02 da Geração Determinística.

---

## 0. Base autoritativa analisada

```text
REPOSITÓRIO: juniorade3/trilha-aprovacao
BRANCH BASE: main
COMMIT BASE ANALISADO: 88ee273f7fde560208699aa75481ca405181b5a6
MERGE: PR #7 — feat: conclui geração determinística sprint 02
ÚLTIMA MIGRATION: V12__adiciona_origem_aos_blocos_de_estudo.sql
SPRINT 01: concluída e mesclada
SPRINT 02: concluída e mesclada
```

Antes de alterar código:

```bash
git switch main
git pull --ff-only
git status --short
git rev-parse HEAD
find aplicativos/backend/src/main/resources/db/migration -maxdepth 1 -type f | sort
```

Regras:

1. Se o `HEAD` continuar em `88ee273f7fde560208699aa75481ca405181b5a6`, seguir este roteiro literalmente.
2. Se houver commit posterior, comparar as mudanças com este roteiro e registrar a divergência antes de implementar.
3. Não resetar, apagar ou sobrescrever alterações locais.
4. Criar a branch:

```bash
git switch -c feature/geracao-deterministica-sprint-03
```

---

## 1. Missão da sprint

Consolidar a Geração Determinística já existente, corrigindo somente as lacunas comprovadas na análise do código e produzindo evidências reproduzíveis de que o fluxo completo funciona.

Ao final:

- a consulta de matérias elegíveis não fará consultas duplicadas;
- nomes de matérias de blocos preservados não provocarão N+1;
- alterar prioridade ou configuração tornará a prévia visivelmente desatualizada;
- uma prévia desatualizada não poderá ser aplicada;
- erros recuperáveis terão repetição da operação correta;
- todas as quatro rotas da geração terão isolamento A/B comprovado;
- haverá um teste integrado do fluxo completo até execução e histórico;
- concorrência de regeneração estará coberta;
- migrations V1 a V12 estarão comprovadas em PostgreSQL vazio;
- OpenAPI descreverá rotas, modelos, códigos e tag;
- foco, teclado e mensagens da jornada estarão cobertos;
- a documentação refletirá somente resultados executados;
- nenhum Motor de Evidências, Revisões, Lacunas ou IA será iniciado.

---

## 2. Estado real encontrado

### 2.1 O que já está correto e deve ser preservado

- `GeradorDeterministicoDePlano` é uma classe de domínio pura, sem Spring ou JPA.
- O algoritmo ordena candidatos e dias explicitamente.
- A aplicação recalcula no backend.
- `ServicoDeGeracaoDeterministica.aplicar(...)` usa transação e lock do plano.
- A regeneração remove somente `GERADO_DETERMINISTICAMENTE`.
- Blocos `MANUAL` e `GERADO_AJUSTADO_MANUALMENTE` são preservados.
- A primeira edição manual transforma o gerado em ajustado.
- V11 possui unicidade por plano/matéria e índice por plano.
- V12 possui `origem`, `justificativa_da_geracao`, `CHECK` e índice por plano/origem.
- Já existe índice `idx_blocos_plano_data_ordem`.
- A página Semana mantém a semana selecionada na query string `inicio`.
- Os badges possuem texto: `Manual`, `Gerado`, `Gerado e ajustado`.
- `usarDialogoAcessivel` já implementa:
  - foco inicial;
  - bloqueio de Tab;
  - Escape;
  - pilha de diálogos;
  - restauração de foco.
- O cliente HTTP já dispara `sessao-expirada` ao receber 401.
- O workflow de CI já executa Compose, backend, migrations, arquitetura, frontend e auditoria.

Não reimplementar esses comportamentos.

### 2.2 Lacunas comprovadas

| Código | Lacuna encontrada | Local |
|---|---|---|
| L01 | `calcularPrevia` obtém matérias elegíveis duas vezes na mesma operação | `ServicoDeGeracaoDeterministica` |
| L02 | a consulta de elegibilidade faz três idas ao PostgreSQL: concurso, cargo e candidatas | `ConsultaDeMateriasElegiveisParaPlanejamento` |
| L03 | matéria de bloco preservado que não está entre as elegíveis pode chamar `ServicoDeMaterias.obter(...)` individualmente | `nomeDaMateria(...)` |
| L04 | alterar duração depois de calcular mantém a prévia antiga navegável e aplicável | `GavetaDeGeracaoDeterministica.vue` |
| L05 | mudar prioridade local também não marca imediatamente a prévia como antiga | `GavetaDeGeracaoDeterministica.vue` |
| L06 | o botão genérico `Tentar novamente` sempre recarrega matérias, mesmo quando falhou salvar, calcular ou aplicar | `GavetaDeGeracaoDeterministica.vue` |
| L07 | os testes da jornada estão fragmentados; não há uma prova única do fluxo até Hoje/execução/histórico | testes de planejamento |
| L08 | isolamento A/B não está explicitamente provado nas quatro rotas da geração | `PlanejamentoIntegracaoTest` |
| L09 | OpenAPI prova a existência das rotas, mas não prova modelos, códigos e tag de cada operação | `DocumentacaoDaApiIntegracaoTest` |
| L10 | testes genéricos provam foco dos diálogos, mas não a jornada real da gaveta de geração | testes Vue |
| L11 | o README da Geração Determinística ainda descreve V10 e Planejamento Manual Sprint 07 como linha de base | documentação |
| L12 | não há status de CI associado ao commit de merge consultado; o texto do PR não pode ser tratado como nova evidência | relatório da sprint |

---

## 3. Limites obrigatórios

### 3.1 Não fazer

- não criar nova entidade de domínio;
- não criar tabela;
- não criar migration V13 por prevenção;
- não alterar a regra de escolha de matérias;
- não alterar pesos;
- não alterar a meta de três matérias;
- não alterar durações mínimas ou máximas;
- não gerar tópico automaticamente;
- não escolher conteúdo da revisão;
- não permitir geração em plano ativo;
- não criar Pinia para a gaveta;
- não adicionar Playwright, Cypress ou outra dependência apenas para esta sprint;
- não criar mock de API em código de produção;
- não acessar repositórios de `concursos.infraestrutura` ou `conteudos.infraestrutura` a partir de `planejamento`;
- não modificar `.github/workflows/ci.yml`, salvo falha concreta causada por incompatibilidade real;
- não executar `npm audit fix` automaticamente;
- não iniciar módulo futuro;
- não preencher validação como aprovada antes de executá-la.

### 3.2 Migration

Decisão inicial:

```text
NÃO CRIAR V13.
```

Os índices existentes cobrem as consultas atuais:

```text
idx_blocos_plano_data_ordem
idx_blocos_plano_origem
idx_prioridades_plano
uk_prioridades_plano_materia
```

Migration nova somente é permitida quando:

1. uma consulta real for alterada;
2. `EXPLAIN` demonstrar ausência de índice útil;
3. o ganho esperado for documentado;
4. o teste da migration for incluído.

Sem essas quatro condições, preservar V12 como última migration.

---

## 4. Arquivos

### 4.1 Alterar obrigatoriamente

```text
aplicativos/backend/src/main/java/br/com/trilhaaprovacao/concursos/aplicacao/ConsultaDeMateriasElegiveisParaPlanejamento.java
aplicativos/backend/src/main/java/br/com/trilhaaprovacao/conteudos/aplicacao/ServicoDeMaterias.java
aplicativos/backend/src/main/java/br/com/trilhaaprovacao/conteudos/infraestrutura/RepositorioJpaDeMaterias.java
aplicativos/backend/src/main/java/br/com/trilhaaprovacao/planejamento/aplicacao/ServicoDeGeracaoDeterministica.java
aplicativos/backend/src/main/java/br/com/trilhaaprovacao/planejamento/api/ControladorDePlanosSemanais.java

aplicativos/backend/src/test/java/br/com/trilhaaprovacao/planejamento/api/PlanejamentoIntegracaoTest.java
aplicativos/backend/src/test/java/br/com/trilhaaprovacao/compartilhado/api/DocumentacaoDaApiIntegracaoTest.java
aplicativos/backend/src/test/java/br/com/trilhaaprovacao/planejamento/dominio/GeradorDeterministicoDePlanoTest.java
aplicativos/backend/src/test/java/br/com/trilhaaprovacao/planejamento/api/IntegracaoPlanejamentoEstudosTest.java

aplicativos/frontend/src/modulos/planejamento/GavetaDeGeracaoDeterministica.vue
aplicativos/frontend/src/modulos/planejamento/GavetaDeGeracaoDeterministica.spec.ts
aplicativos/frontend/src/modulos/planejamento/PlanejamentoSemanaPagina.vue
aplicativos/frontend/src/modulos/planejamento/PlanejamentoSemanaPagina.spec.ts

README.md
documentacao/api/COMO-USAR-O-SWAGGER.md
documentacao/planejamento-geracao-deterministica-compacto/README.md
documentacao/planejamento-geracao-deterministica-compacto/PROMPTS-CODEX.md
documentacao/planejamento-geracao-deterministica-compacto/sprint-03-consolidacao-e-aceite.md
```

### 4.2 Criar

```text
aplicativos/backend/src/test/java/br/com/trilhaaprovacao/planejamento/infraestrutura/MigracoesDaGeracaoDeterministicaIntegracaoTest.java
aplicativos/backend/src/test/java/br/com/trilhaaprovacao/planejamento/arquitetura/ArquiteturaDaGeracaoDeterministicaTest.java
aplicativos/frontend/src/compartilhado/api/clienteHttp.spec.ts
documentacao/planejamento-geracao-deterministica-compacto/ALGORITMO-E-LIMITACOES.md
documentacao/planejamento-geracao-deterministica-compacto/ACEITE-SPRINT-03.md
```

### 4.3 Não alterar sem falha de teste

```text
aplicativos/backend/src/main/java/br/com/trilhaaprovacao/planejamento/dominio/**
aplicativos/backend/src/main/resources/db/migration/**
aplicativos/frontend/src/compartilhado/componentes/GavetaLateral.vue
aplicativos/frontend/src/compartilhado/componentes/ModalDaAplicacao.vue
aplicativos/frontend/src/compartilhado/componentes/usarDialogoAcessivel.ts
.github/workflows/ci.yml
```

---

# PARTE A — BACKEND

## 5. Consolidar a consulta de elegibilidade

### 5.1 Resultado exigido

Cada chamada de:

```java
materiasElegiveis.consultar(usuario)
```

deve executar um único comando SQL.

A consulta precisa distinguir:

```text
CONCURSO_ATIVO_NAO_ENCONTRADO
CARGO_SELECIONADO_NAO_ENCONTRADO
MATERIAS_ELEGIVEIS_NAO_ENCONTRADAS
OK
```

Sem fazer uma consulta antes para descobrir concurso e outra para descobrir cargo.

### 5.2 Implementação

Substituir as três chamadas atuais por um SQL único.

Estrutura esperada:

```java
@Transactional(readOnly = true)
public List<MateriaElegivelParaPlanejamento> consultar(UUID usuario) {
    List<LinhaDaConsulta> linhas = banco.sql("""
            WITH concurso_ativo AS (
                SELECT c.identificador
                FROM concursos c
                WHERE c.usuario_id = :usuario
                  AND c.ativo = TRUE
                ORDER BY c.identificador
                LIMIT 1
            ),
            cargo_selecionado AS (
                SELECT ca.identificador
                FROM cargos_do_concurso ca
                JOIN concurso_ativo c
                  ON c.identificador = ca.concurso_id
                WHERE ca.selecionado = TRUE
                ORDER BY ca.ordem, ca.identificador
                LIMIT 1
            ),
            candidatas AS (
                SELECT
                    m.identificador,
                    m.nome,
                    m.nome_normalizado,
                    p.ordem AS ordem_prova,
                    g.ordem AS ordem_grupo,
                    mp.ordem AS ordem_materia,
                    ROW_NUMBER() OVER (
                        PARTITION BY m.identificador
                        ORDER BY
                            p.ordem,
                            g.ordem,
                            mp.ordem,
                            m.nome_normalizado,
                            m.identificador
                    ) AS repeticao
                FROM cargo_selecionado ca
                JOIN provas p
                  ON p.cargo_id = ca.identificador
                JOIN grupos_de_conteudo g
                  ON g.prova_id = p.identificador
                JOIN materias_da_prova mp
                  ON mp.grupo_de_conteudo_id = g.identificador
                JOIN materias m
                  ON m.identificador = mp.materia_id
                WHERE m.usuario_id = :usuario
                  AND m.arquivada = FALSE
            ),
            deduplicadas AS (
                SELECT
                    identificador,
                    nome,
                    nome_normalizado,
                    ordem_prova,
                    ordem_grupo,
                    ordem_materia
                FROM candidatas
                WHERE repeticao = 1
            ),
            situacao AS (
                SELECT CASE
                    WHEN NOT EXISTS (SELECT 1 FROM concurso_ativo)
                        THEN 'SEM_CONCURSO'
                    WHEN NOT EXISTS (SELECT 1 FROM cargo_selecionado)
                        THEN 'SEM_CARGO'
                    WHEN NOT EXISTS (SELECT 1 FROM deduplicadas)
                        THEN 'SEM_MATERIAS'
                    ELSE 'OK'
                END AS codigo
            )
            SELECT
                s.codigo AS situacao,
                d.identificador,
                d.nome,
                d.nome_normalizado,
                d.ordem_prova,
                d.ordem_grupo,
                d.ordem_materia
            FROM situacao s
            LEFT JOIN deduplicadas d ON TRUE
            ORDER BY
                d.ordem_prova NULLS LAST,
                d.ordem_grupo NULLS LAST,
                d.ordem_materia NULLS LAST,
                d.nome_normalizado NULLS LAST,
                d.identificador NULLS LAST
            """)
            .param("usuario", usuario)
            .query((resultado, linha) -> new LinhaDaConsulta(
                    resultado.getString("situacao"),
                    resultado.getObject("identificador", UUID.class),
                    resultado.getString("nome"),
                    resultado.getString("nome_normalizado")))
            .list();

    String situacao = linhas.getFirst().situacao();

    switch (situacao) {
        case "SEM_CONCURSO" -> throw new RegraDeDominio(
                "CONCURSO_ATIVO_NAO_ENCONTRADO",
                "Defina um concurso ativo antes de gerar a semana.");
        case "SEM_CARGO" -> throw new RegraDeDominio(
                "CARGO_SELECIONADO_NAO_ENCONTRADO",
                "Selecione um cargo no concurso ativo antes de gerar a semana.");
        case "SEM_MATERIAS" -> throw new RegraDeDominio(
                "MATERIAS_ELEGIVEIS_NAO_ENCONTRADAS",
                "O cargo selecionado nao possui materias pessoais ativas para gerar a semana.");
        case "OK" -> {
            // continuar
        }
        default -> throw new IllegalStateException(
                "Situacao inesperada na consulta de materias elegiveis.");
    }

    AtomicInteger ordem = new AtomicInteger(1);

    return linhas.stream()
            .filter(item -> item.identificador() != null)
            .map(item -> new MateriaElegivelParaPlanejamento(
                    item.identificador(),
                    item.nome(),
                    item.nomeNormalizado(),
                    ordem.getAndIncrement()))
            .toList();
}

private record LinhaDaConsulta(
        String situacao,
        UUID identificador,
        String nome,
        String nomeNormalizado) {
}
```

Pode ajustar formatação e nomes privados, mas não voltar a fazer três consultas.

### 5.3 Testes

Em `PlanejamentoIntegracaoTest`, manter ou acrescentar cenários para:

```text
deveInformarAusenciaDeConcursoAtivo
deveInformarAusenciaDeCargoSelecionado
deveInformarAusenciaDeMateriasElegiveis
deveDeduplicarMateriaPresenteEmDoisGrupos
deveOrdenarMateriasDeFormaEstavel
```

---

## 6. Remover consulta duplicada e N+1 de nomes

### 6.1 Repositório de matérias

Adicionar em `RepositorioJpaDeMaterias`:

```java
List<MateriaPersistida> findAllByIdentificadorDoUsuarioAndIdentificadorIn(
        UUID identificadorDoUsuario,
        Collection<UUID> identificadores);
```

Imports:

```java
import java.util.Collection;
import java.util.List;
```

### 6.2 Serviço de matérias

Adicionar método público estreito em `ServicoDeMaterias`:

```java
@Transactional(readOnly = true)
public Map<UUID, String> obterNomes(
        UUID identificadorDoUsuario,
        Set<UUID> identificadores) {

    if (identificadores == null || identificadores.isEmpty()) {
        return Map.of();
    }

    List<MateriaPersistida> encontradas =
            materias.findAllByIdentificadorDoUsuarioAndIdentificadorIn(
                    identificadorDoUsuario, identificadores);

    Map<UUID, String> nomes = encontradas.stream()
            .collect(Collectors.toUnmodifiableMap(
                    item -> item.paraDominio().identificador(),
                    item -> item.paraDominio().nome()));

    if (!nomes.keySet().containsAll(identificadores)) {
        throw new RecursoNaoEncontrado(
                "MATERIA_NAO_ENCONTRADA",
                "Materia nao encontrada.");
    }

    return nomes;
}
```

Ajustar imports:

```java
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
```

Não expor `MateriaPersistida` fora do módulo.

### 6.3 Serviço da geração

Alterar `ServicoDeGeracaoDeterministica` para que cada operação consulte elegíveis apenas uma vez.

Assinatura nova do helper:

```java
private List<MateriaParaGeracao> montarMaterias(
        UUID plano,
        List<MateriaElegivelParaPlanejamento> elegiveis)
```

Exemplo para listagem:

```java
@Transactional(readOnly = true)
public List<MateriaParaGeracao> listarMaterias(UUID usuario, UUID plano) {
    obterPlano(usuario, plano);
    List<MateriaElegivelParaPlanejamento> elegiveis =
            materiasElegiveis.consultar(usuario);
    return montarMaterias(plano, elegiveis);
}
```

Exemplo para prioridades:

```java
@Transactional
public List<MateriaParaGeracao> substituirPrioridades(
        UUID usuario,
        UUID plano,
        List<PrioridadeDeMateriaInformada> informadas) {

    exigirRascunho(obterPlano(usuario, plano).estado());

    List<MateriaElegivelParaPlanejamento> elegiveisDaEstrutura =
            materiasElegiveis.consultar(usuario);

    List<MateriaParaGeracao> elegiveis =
            montarMaterias(plano, elegiveisDaEstrutura);

    // manter validação e persistência atuais
}
```

Em `calcularPrevia(...)`:

```java
List<MateriaElegivelParaPlanejamento> elegiveis =
        materiasElegiveis.consultar(usuario);

List<MateriaParaGeracao> materiasParaGeracao =
        montarMaterias(plano, elegiveis);
```

Não chamar `materiasElegiveis.consultar(usuario)` novamente no mesmo fluxo.

### 6.4 Nomes de blocos preservados

Antes de mapear os blocos preservados:

```java
Map<UUID, String> nomes = elegiveis.stream()
        .collect(Collectors.toMap(
                MateriaElegivelParaPlanejamento::identificadorDaMateria,
                MateriaElegivelParaPlanejamento::nome));

Set<UUID> materiasPreservadasNaoCarregadas = blocosDoPlano.stream()
        .map(BlocoDeEstudoPersistido::paraDominio)
        .map(BlocoDeEstudo::identificadorDaMateria)
        .filter(Objects::nonNull)
        .filter(identificador -> !nomes.containsKey(identificador))
        .collect(Collectors.toSet());

nomes.putAll(materias.obterNomes(
        usuario, materiasPreservadasNaoCarregadas));
```

Depois, ao construir `BlocoPreservadoNaGeracao`:

```java
String nomeDaMateria = item.identificadorDaMateria() == null
        ? null
        : nomes.get(item.identificadorDaMateria());
```

Remover:

```java
private String nomeDaMateria(
        UUID usuario,
        UUID identificador,
        Map<UUID, String> nomes)
```

O resultado deve ser:

```text
1 consulta de elegibilidade
1 consulta de prioridades
0 ou 1 consulta em lote para nomes preservados fora das elegíveis
1 consulta de disponibilidades
1 consulta de blocos
```

Não adicionar dependência de `datasource-proxy`.

### 6.5 Prova contra regressão

Adicionar teste com spy de beans, usando o suporte de testes do Spring já disponível.

Objetivo:

```text
ao gerar uma prévia:
- ConsultaDeMateriasElegiveisParaPlanejamento.consultar é chamada uma vez;
- ServicoDeMaterias.obterNomes é chamado no máximo uma vez;
- ServicoDeMaterias.obter não é chamado para cada bloco preservado.
```

Não criar um contador SQL global apenas para esta sprint.

---

## 7. Concorrência da aplicação

O lock já existe em:

```java
planos.encontrarParaAtualizacao(plano, usuario)
```

Não trocar o mecanismo antes de um teste falhar.

Criar teste integrado:

```text
deveSerializarDuasRegeneracoesConcorrentesSemDuplicarBlocos
```

Preparação:

1. criar plano rascunho;
2. informar 170 minutos em um dia;
3. criar três matérias elegíveis;
4. aplicar uma primeira geração;
5. disparar duas chamadas concorrentes a `geracao.aplicar(..., true)`;
6. aguardar ambas com timeout;
7. consultar o banco.

Resultado:

```text
- nenhuma chamada deixa exceção de integridade;
- não há ordens duplicadas no mesmo dia;
- permanece uma única proposta final;
- manual e ajustado continuam presentes;
- não há geração parcial.
```

Consulta para duplicidade:

```sql
SELECT data, ordem, COUNT(*)
FROM blocos_de_estudo
WHERE plano_id = ?::uuid
  AND estado <> 'CANCELADO'
GROUP BY data, ordem
HAVING COUNT(*) > 1;
```

O resultado deve ser vazio.

Se o teste falhar, corrigir mantendo o lock pessimista no plano. Não introduzir fila, Redis ou lock distribuído.

---

# PARTE B — TESTES DE ACEITE BACKEND

## 8. Teste integral da jornada

Adicionar a `PlanejamentoIntegracaoTest`:

```java
@Test
void deveExecutarFluxoIntegralDaGeracaoAteHojeEHistorico() throws Exception {
    // implementar exatamente a jornada abaixo
}
```

### 8.1 Preparação

Usuário A:

1. cadastrar e entrar;
2. criar plano para segunda-feira;
3. informar disponibilidades diferentes:

```text
segunda: 170
terça:   120
quarta:   70
quinta:    0
sexta:   100
sábado:   180
domingo:   50
```

4. criar quatro matérias:
   - Banco de dados;
   - Redes;
   - Segurança;
   - Engenharia de software;
5. criar tópico `Modelagem relacional` em Banco de dados;
6. criar estrutura de concurso ativo/cargo selecionado com matéria repetida em dois grupos;
7. criar um bloco manual de 30 minutos na terça.

### 8.2 Prioridades

Enviar:

```json
{
  "prioridades": [
    {
      "identificadorDaMateria": "<banco>",
      "prioridade": "ALTA"
    },
    {
      "identificadorDaMateria": "<redes>",
      "prioridade": "NORMAL"
    },
    {
      "identificadorDaMateria": "<seguranca>",
      "prioridade": "BAIXA"
    },
    {
      "identificadorDaMateria": "<engenharia>",
      "prioridade": "NORMAL"
    }
  ]
}
```

Verificar:

```text
200
prioridades persistidas
matéria repetida aparece uma vez
```

### 8.3 Prévia

Payload:

```json
{
  "duracaoPadraoDoBlocoPrincipalEmMinutos": 50,
  "duracaoDoBlocoDeRevisaoEmMinutos": 20
}
```

Executar duas vezes e comparar a árvore JSON.

Não comparar somente quantidade.

Para cada dia:

```java
assertThat(
        minutosPreservados + minutosSugeridos
).isLessThanOrEqualTo(minutosDisponiveis);
```

Verificar ainda:

```text
- segunda possui revisão + três matérias;
- quarta reduz a quantidade e explica a insuficiência;
- quinta não recebe sugestão;
- revisão manual, quando criada para um dia do cenário, não é duplicada;
- nenhum bloco foi persistido pela prévia.
```

### 8.4 Aplicação

Aplicar com:

```json
{
  "duracaoPadraoDoBlocoPrincipalEmMinutos": 50,
  "duracaoDoBlocoDeRevisaoEmMinutos": 20,
  "substituirBlocosGerados": false
}
```

Verificar:

```text
- bloco manual permanece;
- novos blocos têm origem GERADO_DETERMINISTICAMENTE;
- justificativa dos principais não está vazia;
- revisão gerada não possui matéria ou tópico;
- ordens são contínuas por dia.
```

### 8.5 Ajuste

Escolher um bloco principal gerado de Banco de dados e editar:

```text
título: Modelagem relacional
tipo: QUESTOES
duração: 45
matéria: Banco de dados
tópico: Modelagem relacional
```

Verificar:

```text
origem = GERADO_AJUSTADO_MANUALMENTE
justificativa original preservada
```

### 8.6 Regeneração

Registrar IDs dos gerados puros.

Aplicar com:

```json
{
  "duracaoPadraoDoBlocoPrincipalEmMinutos": 50,
  "duracaoDoBlocoDeRevisaoEmMinutos": 20,
  "substituirBlocosGerados": true
}
```

Verificar:

```text
- manual permanece com o mesmo ID;
- ajustado permanece com o mesmo ID;
- gerados puros anteriores deixam de existir;
- nova geração não duplica revisão;
- ordens continuam contínuas.
```

### 8.7 Ativação e execução

1. ativar o plano;
2. tentar gerar novamente e esperar:

```text
409
PLANO_SEMANAL_NAO_ESTA_EM_RASCUNHO
```

3. consultar `/api/v1/planejamento/hoje` na data do bloco ajustado;
4. iniciar o bloco;
5. concluir com duração executada;
6. verificar:
   - bloco concluído;
   - execução encerrada;
   - um `RegistroDeEstudo` ativo vinculado ao tópico;
   - histórico sem duplicidade.

Esse teste é a prova principal do aceite integral.

---

## 9. Isolamento A/B em todas as rotas

Adicionar:

```java
@Test
void deveIsolarTodasAsRotasDaGeracaoPorUsuario() throws Exception {
}
```

Preparação:

- A possui plano e matérias elegíveis.
- B está autenticado.

B deve receber `404` ao usar o ID do plano de A em:

```text
GET  /api/v1/planos-semanais/{id}/materias-para-geracao
PUT  /api/v1/planos-semanais/{id}/prioridades-de-materias
POST /api/v1/planos-semanais/{id}/geracao-deterministica/previa
POST /api/v1/planos-semanais/{id}/geracao-deterministica
```

Para PUT e POST, enviar CSRF válido da sessão B. O resultado precisa ser 404 por propriedade, não 500.

Também provar ausência de mutação:

```sql
SELECT COUNT(*)
FROM prioridades_de_materias_no_plano
WHERE plano_id = ?::uuid;
```

e:

```sql
SELECT COUNT(*)
FROM blocos_de_estudo
WHERE plano_id = ?::uuid;
```

Devem permanecer iguais antes e depois das tentativas de B.

---

## 10. Determinismo reforçado

Manter os testes existentes e adicionar a `GeradorDeterministicoDePlanoTest`:

```text
deveProduzirMesmaSaidaEmMultiplasPermutacoes
deveProduzirMesmaSaidaComBlocosPreservadosEmOrdensDeEntradaDiferentes
deveGerarTresMateriasQuandoHa170Minutos
deveReduzirEExplicarQuandoHa70Minutos
naoDeveDuplicarRevisaoPreservada
```

### 10.1 Permutações

Não usar `Random`.

Criar uma lista fixa de permutações:

```java
List<List<CandidatoDeMateriaParaGeracao>> permutacoes = List.of(
        candidatos,
        List.of(candidatos.get(3), candidatos.get(2), candidatos.get(1), candidatos.get(0)),
        List.of(candidatos.get(1), candidatos.get(3), candidatos.get(0), candidatos.get(2)),
        List.of(candidatos.get(2), candidatos.get(0), candidatos.get(3), candidatos.get(1))
);
```

Todas devem produzir objeto igual ao resultado de referência.

### 10.2 Capacidade

Para 170 minutos:

```text
20 revisão
3 blocos de 50
0 livre
```

Para 70 minutos:

```text
20 revisão
2 blocos de 25
aviso DISPONIBILIDADE_INSUFICIENTE
```

---

## 11. PostgreSQL vazio e schema

Criar:

```text
MigracoesDaGeracaoDeterministicaIntegracaoTest.java
```

Usar `PostgreSQLContainer` próprio e Spring Boot/Flyway, seguindo os testes de integração existentes.

Testes:

```text
deveSubirTodasAsMigracoesEmBancoVazio
deveConterEstruturasDasPrioridadesEDaOrigem
```

Verificar `flyway_schema_history`:

```sql
SELECT version, success
FROM flyway_schema_history
WHERE version IN ('11', '12')
ORDER BY installed_rank;
```

Esperado:

```text
11 | true
12 | true
```

Verificar:

```text
prioridades_de_materias_no_plano existe
uk_prioridades_plano_materia existe
ck_prioridades_valor existe
idx_prioridades_plano existe
blocos_de_estudo.origem é NOT NULL
blocos_de_estudo.justificativa_da_geracao possui limite 2000
ck_blocos_origem existe
idx_blocos_plano_origem existe
idx_blocos_plano_data_ordem existe
```

Não considerar o banco do Compose previamente preenchido como prova de banco vazio.

---

## 12. Arquitetura modular

Criar:

```text
ArquiteturaDaGeracaoDeterministicaTest.java
```

Estrutura:

```java
@AnalyzeClasses(packages = "br.com.trilhaaprovacao")
class ArquiteturaDaGeracaoDeterministicaTest {

    @ArchTest
    static final ArchRule dominioDoPlanejamentoNaoDependeDeFramework =
            noClasses()
                    .that().resideInAPackage(
                            "br.com.trilhaaprovacao.planejamento.dominio..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(
                            "org.springframework..",
                            "jakarta.persistence..",
                            "br.com.trilhaaprovacao.planejamento.api..",
                            "br.com.trilhaaprovacao.planejamento.infraestrutura..");

    @ArchTest
    static final ArchRule planejamentoNaoAcessaInfraestruturaDeOutroModulo =
            noClasses()
                    .that().resideInAPackage(
                            "br.com.trilhaaprovacao.planejamento..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(
                            "br.com.trilhaaprovacao.concursos.infraestrutura..",
                            "br.com.trilhaaprovacao.conteudos.infraestrutura..");
}
```

Ajustar somente a sintaxe necessária ao ArchUnit 1.4.2.

Não proibir a dependência estreita em:

```text
concursos.aplicacao
conteudos.aplicacao
```

Ela é intencional.

---

# PARTE C — OPENAPI

## 13. Contrato final

Rotas:

```text
GET  /api/v1/planos-semanais/{identificador}/materias-para-geracao
PUT  /api/v1/planos-semanais/{identificador}/prioridades-de-materias
POST /api/v1/planos-semanais/{identificador}/geracao-deterministica/previa
POST /api/v1/planos-semanais/{identificador}/geracao-deterministica
```

Tag:

```text
Planejamento
```

### 13.1 Códigos esperados

#### GET matérias

```text
200
401
404
422
```

#### PUT prioridades

```text
200
400
401
403
404
409
422
```

#### POST prévia

```text
200
400
401
403
404
409
422
```

#### POST aplicação

```text
200
400
401
403
404
409
422
```

### 13.2 Anotações

Adicionar `@Operation` e `@ApiResponses` somente nas quatro operações da geração, reutilizando `RespostaDeErro`.

Exemplo:

```java
@Operation(
        summary = "Gerar prévia determinística",
        description = """
                Calcula sete dias sem persistir blocos. Considera prioridades,
                disponibilidade e blocos preservados do plano em rascunho.
                """)
@ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Prévia calculada",
                content = @Content(
                        schema = @Schema(
                                implementation = RespostaDaPreviaDaGeracao.class))),
        @ApiResponse(
                responseCode = "400",
                description = "Requisição inválida",
                content = @Content(
                        schema = @Schema(
                                implementation = RespostaDeErro.class))),
        @ApiResponse(
                responseCode = "404",
                description = "Plano não encontrado para o usuário"),
        @ApiResponse(
                responseCode = "409",
                description = "Plano fora de rascunho"),
        @ApiResponse(
                responseCode = "422",
                description = "Regra de negócio não atendida")
})
```

Preservar a configuração global de sessão e CSRF. Não duplicar security schemes.

### 13.3 Teste do documento

Expandir `DocumentacaoDaApiIntegracaoTest`.

Além da existência dos paths, verificar para cada operação:

```text
tag Planejamento
requestBody correto quando aplicável
schema de resposta 200
códigos esperados
segurança de sessão
segurança CSRF em PUT/POST
```

Exemplos de JSON Pointer:

```java
assertThat(documento.at(
        "/paths/~1api~1v1~1planos-semanais~1{identificador}~1geracao-deterministica~1previa/post/tags/0"
).asText()).isEqualTo("Planejamento");

assertThat(documento.at(
        "/paths/~1api~1v1~1planos-semanais~1{identificador}~1geracao-deterministica/post/requestBody/content/application~1json/schema/$ref"
).asText()).endsWith("/RequisicaoDeAplicacaoDaGeracao");

assertThat(documento.at(
        "/paths/~1api~1v1~1planos-semanais~1{identificador}~1geracao-deterministica/post/responses/200/content/application~1json/schema/$ref"
).asText()).endsWith("/RespostaDaAplicacaoDaGeracao");
```

Confirmar também `400`, `404`, `409` e `422`.

---

# PARTE D — FRONTEND

## 14. Estado da prévia

### 14.1 Regra

A prévia é válida somente enquanto forem iguais:

```text
prioridades atuais
duração principal atual
duração da revisão atual
```

Se qualquer valor mudar:

- manter a prévia antiga apenas para consulta;
- exibir `Prévia desatualizada`;
- desabilitar `Aplicar à semana`;
- exigir novo cálculo;
- não apagar prioridades salvas;
- não alterar blocos.

### 14.2 Implementação por assinatura

Em `GavetaDeGeracaoDeterministica.vue`, importar:

```ts
import { computed, onMounted, ref } from 'vue'
```

Adicionar:

```ts
const assinaturaDaPrevia = ref<string>()
```

Criar:

```ts
function assinaturaAtual() {
  return JSON.stringify({
    duracaoPrincipal: Number(duracaoPrincipal.value),
    duracaoDaRevisao: Number(duracaoDaRevisao.value),
    prioridades: materias.value
      .map((materia) => ({
        identificadorDaMateria: materia.identificadorDaMateria,
        prioridade: materia.prioridade,
      }))
      .sort((primeira, segunda) =>
        primeira.identificadorDaMateria.localeCompare(
          segunda.identificadorDaMateria,
        ),
      ),
  })
}
```

Criar:

```ts
const previaDesatualizada = computed(
  () =>
    Boolean(previa.value) &&
    assinaturaDaPrevia.value !== assinaturaAtual(),
)
```

Depois de calcular com sucesso:

```ts
previa.value = resultado
assinaturaDaPrevia.value = assinaturaAtual()
etapa.value = 'PREVIA'
```

Ao aplicar:

```ts
if (!previa.value || previaDesatualizada.value) {
  erro.value = 'Recalcule a prévia antes de aplicar.'
  return
}
```

Botão:

```vue
:disabled="processando || previaDesatualizada"
```

Alerta:

```vue
<div
  v-if="previaDesatualizada"
  class="alert alert-warning"
  role="status"
  aria-live="polite"
>
  <strong>Prévia desatualizada.</strong>
  Prioridades ou durações mudaram. Recalcule antes de aplicar.
</div>
```

Texto do botão quando desatualizada:

```text
Recalcular prévia
```

### 14.3 Não usar watch destrutivo

Não apagar automaticamente a prévia em cada tecla.

A assinatura permite:

- consultar o resultado anterior;
- entender que ele está antigo;
- voltar sem perder configuração;
- recalcular conscientemente.

---

## 15. Voltar etapas sem perder dados

Enquanto a gaveta estiver aberta:

- prioridades locais permanecem;
- prioridades já salvas permanecem no backend;
- duração principal permanece;
- duração de revisão permanece;
- prévia permanece, marcada como antiga quando necessário.

Não persistir durações ao fechar a gaveta. Ao reabrir:

```text
prioridades são recarregadas do backend;
durações voltam aos padrões 50 e 20;
não criar Pinia;
não usar localStorage.
```

Essa é a decisão final da sprint.

---

## 16. Nova tentativa correta

Adicionar:

```ts
type AcaoRecuperavel =
  | 'CARREGAR_MATERIAS'
  | 'SALVAR_PRIORIDADES'
  | 'CALCULAR_PREVIA'
  | 'APLICAR'

const ultimaAcaoComFalha = ref<AcaoRecuperavel>()
```

Em cada `catch`, registrar a operação correspondente.

Criar:

```ts
function tentarNovamente() {
  switch (ultimaAcaoComFalha.value) {
    case 'SALVAR_PRIORIDADES':
      void salvarPrioridades()
      break
    case 'CALCULAR_PREVIA':
      void calcularPrevia()
      break
    case 'APLICAR':
      void aplicar(false)
      break
    default:
      void carregarMaterias()
  }
}
```

O botão `Tentar novamente` deve chamar:

```vue
@click="tentarNovamente"
```

Depois de sucesso:

```ts
ultimaAcaoComFalha.value = undefined
```

Conflito `GERACAO_DETERMINISTICA_JA_APLICADA` não é erro de rede e continua abrindo o modal.

### 16.1 Texto da confirmação

Quando `quantidadeDeBlocosGerados > 0`:

```text
N bloco(s) gerado(s) serão substituídos.
```

Quando o 409 veio do backend e o contador local está zero:

```text
O servidor encontrou uma geração anterior. Os blocos puramente gerados serão substituídos.
```

Não mostrar `0 bloco(s) serão substituídos`.

---

## 17. Semana selecionada

A semana já está na rota:

```text
/planejamento/semana?inicio=AAAA-MM-DD
```

Preservar esse comportamento.

Em `PlanejamentoSemanaPagina.vue`:

1. adicionar uma chave à gaveta:

```vue
:key="plano.identificador"
```

2. ao mudar `rota.query.inicio`, fechar a gaveta antes de carregar outro plano:

```ts
geracaoAberta.value = false
```

3. abrir e fechar a gaveta não pode alterar a query string.

Não criar estado paralelo para a data.

---

## 18. Acessibilidade

### 18.1 Gaveta real

Testar:

- botão `Gerar semana` recebe foco;
- abre gaveta;
- foco vai para o primeiro controle;
- Tab permanece dentro;
- Escape fecha;
- foco volta para `Gerar semana`.

### 18.2 Modal de regeneração

Testar:

- foco está em `Aplicar à semana`;
- abrir confirmação;
- Escape fecha somente o modal superior;
- gaveta permanece aberta;
- foco volta para `Aplicar à semana`;
- segundo Escape fecha a gaveta;
- foco volta para `Gerar semana`.

### 18.3 Mensagens

Usar:

```text
role="alert" para falhas;
role="status" e aria-live="polite" para:
- carregamento;
- prévia desatualizada;
- aplicação concluída;
- aviso recuperável.
```

Não usar cor como único indicador.

As justificativas continuam como texto em lista ou modal.

---

## 19. Testes Vue

### 19.1 `GavetaDeGeracaoDeterministica.spec.ts`

Adicionar:

```text
marcaAPreviaComoDesatualizadaAoAlterarDuracao
marcaAPreviaComoDesatualizadaAoAlterarPrioridade
naoAplicaPreviaDesatualizada
recalculaELiberaAplicacao
voltaEtapasSemPerderPrioridadesEDuracoes
repeteCalculoDepoisDeErroDeRedeSemRecarregarMaterias
preservaPrioridadesSalvasQuandoCalculoFalha
fechaModalSuperiorEMantemGavetaAberta
restauraFocoAoBotaoQueAbriuConfirmacao
```

### 19.2 `PlanejamentoSemanaPagina.spec.ts`

Adicionar:

```text
fechaGeracaoSemAplicarESemAlterarBlocos
mantemSemanaSelecionadaAoAbrirEFecharGeracao
restauraFocoNoBotaoGerarSemana
fechaGeracaoAoNavegarParaOutraSemana
executaJornadaDaGeracaoComApiSimuladaSomenteNoTeste
```

No teste `fechaGeracaoSemAplicarESemAlterarBlocos`:

```text
- abrir gaveta;
- navegar entre etapas;
- fechar;
- garantir que aplicarGeracaoDeterministica não foi chamada;
- garantir que os blocos do plano renderizado não mudaram.
```

Prioridades salvas podem permanecer; o que não pode mudar é o plano/blocos sem aplicação.

### 19.3 `clienteHttp.spec.ts`

Criar testes:

```text
disparaEventoDeSessaoExpiradaAoReceber401
preservaMensagemPadronizadaQuandoRespostaNaoPossuiJson
propagaErroDeRedeParaTratamentoRecuperavelDaTela
```

Mockar `global.fetch`.

Não alterar a política global de redirecionamento nesta sprint.

---

# PARTE E — REGRESSÃO COM ESTUDOS

## 20. Bloco gerado no fluxo de execução

Expandir `IntegracaoPlanejamentoEstudosTest` ou cobrir no teste integral da seção 8.

Provar:

```text
gerado → ajustado com tópico → regeneração → ativação → Hoje
→ início → conclusão → RegistroDeEstudo → histórico
```

Critérios:

- o bloco ajustado conserva origem e justificativa;
- a regeneração não remove o bloco;
- a execução não diferencia manual e ajustado;
- o registro de estudo é criado uma vez;
- nova conclusão idempotente não duplica estudo;
- usuário B não consulta execução ou estudo de A.

Não adicionar regra de execução baseada em origem.

---

# PARTE F — DOCUMENTAÇÃO

## 21. `ALGORITMO-E-LIMITACOES.md`

Criar com estas seções:

```text
1. Entradas
2. Dados preservados
3. Cálculo da capacidade
4. Reserva de revisão
5. Meta de três matérias
6. Rodízio ponderado
7. Desempates estáveis
8. Duração dos blocos
9. Justificativas
10. Aplicação e regeneração
11. Determinismo
12. Limitações atuais
```

Limitações obrigatórias:

- não escolhe tópicos;
- não escolhe conteúdo da revisão;
- não usa desempenho;
- não usa registros de erros;
- não usa IA;
- não gera em plano ativo;
- não preenche todo minuto livre;
- não cria mais de um bloco da mesma matéria no dia;
- não mantém histórico de versões da geração;
- justificativa persistida é um resumo textual de até 2.000 caracteres.

---

## 22. Atualizar README da geração

Remover a linha de base antiga que fala em:

```text
Planejamento Manual até Sprint 07
última migration V10
```

Substituir por:

```text
Planejamento Manual concluído até Sprint 08
Sprint 01 da geração concluída em V11
Sprint 02 da geração concluída em V12
Sprint 03 consolida o aceite sem criar nova regra
```

Após validação, registrar o commit final da Sprint 03.

---

## 23. README principal

Somente depois de `make verificar` aprovado, acrescentar ao estado do produto:

```text
O Planejamento Manual e a Geração Determinística estão implementados.
A geração permite definir prioridades, calcular uma prévia explicável,
aplicar blocos em rascunho e regenerar preservando blocos manuais ou ajustados.
```

Não anunciar Motor de Evidências.

---

## 24. Swagger

Atualizar `documentacao/api/COMO-USAR-O-SWAGGER.md` com exemplos:

### Prioridades

```json
{
  "prioridades": [
    {
      "identificadorDaMateria": "UUID",
      "prioridade": "ALTA"
    }
  ]
}
```

### Prévia

```json
{
  "duracaoPadraoDoBlocoPrincipalEmMinutos": 50,
  "duracaoDoBlocoDeRevisaoEmMinutos": 20
}
```

### Aplicação

```json
{
  "duracaoPadraoDoBlocoPrincipalEmMinutos": 50,
  "duracaoDoBlocoDeRevisaoEmMinutos": 20,
  "substituirBlocosGerados": false
}
```

Explicar:

- cookie de sessão;
- obtenção do CSRF;
- `X-XSRF-TOKEN`;
- 409 de geração anterior;
- `substituirBlocosGerados=true`;
- bloqueio em plano ativo.

---

## 25. Arquivo de aceite

Criar `ACEITE-SPRINT-03.md`.

Não preencher antecipadamente.

Modelo:

```markdown
# Aceite da Sprint 03

## Base
- branch:
- commit inicial:
- commit final:
- migration final:

## Matriz

| Item | Status | Evidência |
|---|---|---|
| fluxo integral backend | NÃO EXECUTADO | |
| determinismo | NÃO EXECUTADO | |
| capacidade diária | NÃO EXECUTADO | |
| três matérias | NÃO EXECUTADO | |
| redução explicada | NÃO EXECUTADO | |
| revisão única | NÃO EXECUTADO | |
| preservação manual/ajustado | NÃO EXECUTADO | |
| isolamento A/B nas quatro rotas | NÃO EXECUTADO | |
| concorrência | NÃO EXECUTADO | |
| migrations em banco vazio | NÃO EXECUTADO | |
| arquitetura | NÃO EXECUTADO | |
| OpenAPI | NÃO EXECUTADO | |
| execução e histórico | NÃO EXECUTADO | |
| frontend | NÃO EXECUTADO | |
| teclado e foco | NÃO EXECUTADO | |
| 390 px | NÃO EXECUTADO | |
| 768 px | NÃO EXECUTADO | |
| 1280 px | NÃO EXECUTADO | |
| sessão expirada | NÃO EXECUTADO | |
| erro de rede e repetição | NÃO EXECUTADO | |
| make verificar | NÃO EXECUTADO | |
| CI do PR | NÃO EXECUTADO | |

## Comandos executados

## Validação manual

## Divergências

## Pendências
```

Status permitidos:

```text
APROVADO
FALHOU
PARCIAL
BLOQUEADO
NÃO EXECUTADO
```

---

# PARTE G — VALIDAÇÃO

## 26. Porta backend durante a implementação

```bash
cd aplicativos/backend

./mvnw -Dtest=GeradorDeterministicoDePlanoTest test
./mvnw -Dtest=PlanejamentoIntegracaoTest test
./mvnw -Dtest=IntegracaoPlanejamentoEstudosTest test
./mvnw -Dtest=MigracoesDaGeracaoDeterministicaIntegracaoTest test
./mvnw -Dtest=ArquiteturaDaGeracaoDeterministicaTest test
./mvnw -Dtest=DocumentacaoDaApiIntegracaoTest test

./mvnw verify
```

Não declarar aprovado se Docker/Testcontainers não estiver disponível. Registrar `BLOQUEADO`.

---

## 27. Porta frontend durante a implementação

```bash
cd aplicativos/frontend

npm run test -- GavetaDeGeracaoDeterministica.spec.ts
npm run test -- PlanejamentoSemanaPagina.spec.ts
npm run test -- clienteHttp.spec.ts

npm run verificar-tipos
npm run lint
npm run test
npm run build
npm run verificar-formatacao
npm audit
```

Não executar `npm audit fix`.

---

## 28. Porta global

Na raiz:

```bash
docker compose config
docker compose up -d
docker compose ps

git diff --check
make verificar
git status --short
```

Verificar que não existem arquivos versionados indevidos:

```bash
git ls-files | grep -E '(^|/)(target|dist|coverage|test-results|playwright-report|tmp|temp)/' \
  && { echo "Artefato temporário versionado"; exit 1; } \
  || true
```

Verificar que não foram criados motores futuros:

```bash
git diff --name-only main...HEAD | grep -Ei \
  'evidencia|lacuna|motor-de-revis|inteligencia-artificial|openai|timefold|or-tools' \
  && { echo "Escopo futuro detectado"; exit 1; } \
  || true
```

A verificação por nome não substitui revisão do diff.

---

## 29. Validação manual

Usar frontend e backend reais. Não usar fixture de produção.

### 29.1 Responsividade

Executar em:

```text
390 × 844
768 × 1024
1280 × 800
```

Em cada tamanho:

1. abrir Semana;
2. abrir geração;
3. percorrer prioridades;
4. configurar;
5. calcular;
6. abrir justificativas;
7. aplicar;
8. editar;
9. regenerar.

No console:

```js
document.documentElement.scrollWidth <= document.documentElement.clientWidth
```

Esperado:

```text
true
```

Captura de tela é evidência auxiliar, não substitui registro textual.

### 29.2 Teclado

Sem mouse:

```text
Tab até Gerar semana
Enter
Tab e Shift+Tab dentro da gaveta
alterar prioridade
continuar
alterar duração
calcular
voltar
recalcular
aplicar
abrir confirmação
Escape
confirmar
Escape para fechar
```

Registrar:

```text
foco inicial
contenção de foco
Escape superior
restauração de foco
texto das mensagens
```

### 29.3 Sessão expirada

1. abrir a gaveta autenticado;
2. expirar/logout da sessão em outra aba;
3. tentar operação mutável;
4. confirmar evento global de sessão expirada e fluxo de autenticação existente;
5. confirmar ausência de erro 500.

### 29.4 Erro de rede

1. calcular prévia;
2. simular offline no navegador;
3. alterar configuração;
4. tentar recalcular;
5. confirmar erro anunciado;
6. voltar online;
7. selecionar `Tentar novamente`;
8. confirmar que a operação repetida é o cálculo, sem perder prioridades ou durações.

### 29.5 Usuário A versus B

Executar as quatro rotas usando plano de A na sessão de B e registrar os códigos.

### 29.6 Swagger

Confirmar:

```text
/v3/api-docs
/swagger-ui.html
/swagger-ui/index.html
```

Executar o fluxo autenticado com cookie e CSRF.

---

# PARTE H — ORDEM DE EXECUÇÃO

## 30. Sequência obrigatória

```text
1. confirmar base e branch;
2. executar testes atuais antes de modificar;
3. consolidar consulta de elegibilidade;
4. eliminar consulta duplicada e N+1;
5. executar testes backend afetados;
6. implementar assinatura da prévia;
7. implementar repetição da operação correta;
8. integrar foco e contexto da semana;
9. criar testes Vue;
10. criar teste integral backend;
11. criar isolamento das quatro rotas;
12. criar teste de concorrência;
13. criar teste de migrations em banco vazio;
14. criar teste de arquitetura;
15. consolidar OpenAPI;
16. atualizar documentação;
17. executar make verificar;
18. executar validação manual;
19. preencher ACEITE-SPRINT-03.md com fatos;
20. revisar diff;
21. commit;
22. push;
23. abrir PR;
24. aguardar e registrar CI;
25. parar.
```

Não começar documentação afirmativa antes dos testes.

---

## 31. Critérios de aceite executáveis

### CA01 — Saída sem aplicação

```text
Dado que a gaveta foi aberta
E prioridades/configuração foram alteradas
Quando a gaveta é fechada sem aplicar
Então nenhum bloco é criado, alterado ou removido.
```

### CA02 — Prévia desatualizada

```text
Dada uma prévia calculada
Quando prioridade ou duração muda
Então a interface anuncia que a prévia está desatualizada
E o botão de aplicação fica indisponível
Até novo cálculo.
```

### CA03 — Determinismo

```text
Dadas entradas semanticamente iguais em ordens diferentes
Quando o gerador é executado
Então os objetos de saída são iguais.
```

### CA04 — Capacidade

```text
Para cada dia:
preservados + sugeridos <= disponibilidade.
```

### CA05 — Meta diária

```text
Com 170 minutos, três matérias elegíveis e revisão de 20:
revisão + três blocos principais de 50.
```

### CA06 — Capacidade insuficiente

```text
Com 70 minutos:
revisão de 20 + dois blocos de 25
e aviso DISPONIBILIDADE_INSUFICIENTE.
```

### CA07 — Revisão única

```text
Revisão preservada impede nova revisão sugerida.
```

### CA08 — Regeneração

```text
Somente GERADO_DETERMINISTICAMENTE é removido.
MANUAL e GERADO_AJUSTADO_MANUALMENTE permanecem com os mesmos IDs.
```

### CA09 — Isolamento

```text
B recebe 404 nas quatro rotas ao usar plano de A.
```

### CA10 — Concorrência

```text
Duas regenerações concorrentes não geram ordem duplicada,
violação de integridade ou proposta parcial.
```

### CA11 — Banco vazio

```text
Flyway aplica V1 a V12 em PostgreSQL vazio.
```

### CA12 — OpenAPI

```text
Quatro rotas, tag, modelos, respostas e segurança aparecem no documento.
```

### CA13 — Acessibilidade

```text
Jornada operável por teclado, com foco restaurado e mensagens textuais.
```

### CA14 — Regressão

```text
Bloco ajustado gerado pode ser ativado, executado e registrado no histórico.
```

---

# PARTE I — COMMIT E PR

## 32. Revisão do diff

```bash
git diff --stat main...HEAD
git diff --check
git diff main...HEAD -- aplicativos/backend/src/main/java
git diff main...HEAD -- aplicativos/frontend/src
git diff main...HEAD -- documentacao
```

Perguntas obrigatórias:

```text
Foi criada entidade sem necessidade?
Foi criada migration sem evidência?
Alguma regra do gerador mudou?
Algum módulo futuro começou?
Existe mock em produção?
Existe acesso direto à infraestrutura de outro módulo?
Algum resultado não executado foi marcado como aprovado?
```

Todas devem ser respondidas no relatório.

## 33. Commit sugerido

```bash
git add \
  aplicativos/backend \
  aplicativos/frontend \
  README.md \
  documentacao

git commit -m "test: consolida aceite da geracao deterministica"
```

Não usar `git add .` antes de revisar `git status --short`.

## 34. PR

Título:

```text
test: consolida aceite da geração determinística
```

Corpo:

```markdown
## O que mudou

- elimina consultas duplicadas e N+1 na montagem da geração;
- invalida visualmente prévias após alteração de prioridades ou durações;
- melhora repetição de erros recuperáveis e preservação do contexto;
- consolida fluxo integral, isolamento, concorrência, migrations e arquitetura;
- fecha OpenAPI, acessibilidade, responsividade e documentação factual.

## O que não mudou

- algoritmo;
- pesos;
- meta de três matérias;
- regras de revisão;
- geração em plano ativo;
- motores futuros.

## Evidências

- `docker compose config`
- `make verificar`
- testes backend:
- testes frontend:
- migrations:
- OpenAPI:
- validação 390/768/1280:
- teclado:
- sessão expirada:
- erro de rede:

## Pendências

- nenhuma, ou listar objetivamente.
```

Não fazer merge com CI vermelha.

---

# PARTE J — REGISTRO DE CONCLUSÃO

## 35. Preencher somente no fim

```text
STATUS:
BRANCH:
COMMIT INICIAL:
COMMIT FINAL:
PR:
ARQUIVOS ALTERADOS:
ARQUIVOS CRIADOS:
DECISÕES TOMADAS:
MIGRATION FINAL:
TESTES BACKEND:
TESTES FRONTEND:
MIGRATIONS EM BANCO VAZIO:
OPENAPI:
ARQUITETURA:
CONCORRÊNCIA:
VALIDAÇÃO 390 PX:
VALIDAÇÃO 768 PX:
VALIDAÇÃO 1280 PX:
VALIDAÇÃO POR TECLADO:
SESSÃO EXPIRADA:
ERRO DE REDE:
ISOLAMENTO A/B:
CI:
PENDÊNCIAS:
PRÓXIMA SPRINT: Planejamento 2 concluído; não iniciar Motor de Evidências sem autorização.
```

---

# PROMPT CURTO PARA INICIAR NO CODEX

```text
Implemente exclusivamente a Sprint 03 da Geração Determinística no repositório atual.

Base analisada:
- repositório: juniorade3/trilha-aprovacao
- branch base: main
- commit analisado: 88ee273f7fde560208699aa75481ca405181b5a6
- última migration: V12

Leia somente o necessário:
1. AGENTS.md
2. documentacao/planejamento-geracao-deterministica-compacto/CONTEXTO-COMUM.md
3. registros de conclusão das Sprints 01 e 02
4. o documento detalhado da Sprint 03

Siga o roteiro técnico do documento. Não reprojete a solução.

Mudanças de produção autorizadas:
- transformar a elegibilidade em uma única consulta SQL;
- eliminar a consulta duplicada e o N+1 de nomes;
- marcar prévia como desatualizada ao mudar prioridade ou duração;
- repetir a operação correta após erro recuperável;
- pequenos ajustes de foco e contexto da semana;
- anotações OpenAPI necessárias.

Não criar entidade, tabela, migration V13, store Pinia, dependência de teste de navegador ou módulo futuro sem falha comprovada.

Consolide:
- fluxo integral até Hoje, execução e histórico;
- determinismo;
- capacidade e três matérias;
- revisão única;
- regeneração seletiva;
- isolamento A/B nas quatro rotas;
- concorrência;
- PostgreSQL vazio;
- arquitetura;
- OpenAPI;
- frontend, teclado e foco;
- documentação factual.

Antes de codificar:
- confirme git status, HEAD e última migration;
- crie feature/geracao-deterministica-sprint-03;
- execute os testes atuais e registre o resultado.

Ao terminar:
- execute docker compose config, make verificar e git diff --check;
- faça a validação manual em 390, 768 e 1280 px;
- teste teclado, sessão expirada, erro de rede, Swagger e A/B;
- preencha ACEITE-SPRINT-03.md somente com resultados executados;
- abra o PR;
- não faça merge com CI vermelha;
- pare sem iniciar Motor de Evidências, Revisões, Lacunas ou IA.
```
