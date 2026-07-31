# Auditoria do motor adaptativo

Data: 24/07/2026

Veredito: **APROVADO COM RESSALVAS**

## 1. Resumo executivo

O fluxo evidência → diagnóstico → priorização → revisão → planejamento foi
validado com PostgreSQL 17 real. O teste integral demonstra que um tópico passa
de 50% com padrão repetido para 100% e consolidado; a recordação 5 muda a
assinatura da prévia e move a revisão da segunda-feira para o domingo devido.

Foram encontrados 1 P0, 4 P1, 6 P2 e 2 P3. Todos os P0/P1 e cinco P2 foram
corrigidos; permanecem 1 P2 e 2 P3, nenhum bloqueador. O veredito tem ressalvas
porque há semânticas de produto a alinhar, invariantes indiretos ainda protegidos
principalmente na aplicação e não houve inspeção visual em navegador nem scanner
de dependências Maven.

## 2. Arquitetura encontrada

```text
Vue/TypeScript
  -> API MVC com sessão e CSRF
  -> casos de uso
  -> domínio puro
  -> JPA/JDBC
  -> PostgreSQL/Flyway
```

O mapa detalhado está em
[`MAPA-DO-MOTOR-ADAPTATIVO.md`](MAPA-DO-MOTOR-ADAPTATIVO.md). As fontes de
verdade são `EvidenciaDeAprendizagem`, `ClassificadorDePriorizacao`,
`CalculadorDeRevisaoEspacada`, `GeradorDeterministicoDePlano` e os respectivos
casos de uso.

## 3. Rastreabilidade resumida

| Entrada | Persistência | Decisão | Consumidor |
| --- | --- | --- | --- |
| Estudo/evidência | registros, evidências e padrões | diagnóstico e ranking | priorização e frontend |
| Recordação | evidência de revisão | etapa e data devida | agenda e gerador |
| Ranking oficial | itens e mapeamentos confirmados | grupo/faixa/ação | gerador semanal |
| Prioridade manual | prioridades por matéria/plano | peso/ inclusão | gerador, sem alterar diagnóstico |
| Blocos existentes | plano e blocos | preservação/antiduplicação | prévia, aplicação e replanejamento |

## 4. Testes executados

- Baseline, antes das correções: `make verificar` em 247,51 s; backend 191/191
  e frontend 152/152, sem ignorados.
- Porta final: `make verificar` em 326,80 s; backend 227/227 e frontend 170/170,
  sem falhas ou ignorados; tipos, lint, build, Prettier e `npm audit` aprovados.
- Frontend também executado com `TZ=UTC`: 71/71 testes direcionados.
- Fluxo integral: 1/1 com Testcontainers/PostgreSQL.
- Segurança/desempenho: 5/5; massa de 1.000 tópicos e 30.000
  registros/evidências.
- Mutações manuais: 4/4 detectadas e revertidas (20 questões, recordação 5,
  revisão futura e terceiro reagendamento).
- Dependências: `npm audit` encontrou 0 vulnerabilidades. Não havia scanner
  Maven configurado e ele não foi acrescentado nesta auditoria enxugada.

Avisos não bloqueantes: API deprecada/unchecked já existente; mensagens de
encerramento de pools de Testcontainers; falhas SQL deliberadamente injetadas
pelos testes de rollback e concorrência.

## 5. Cobertura crítica

Relatório: `aplicativos/backend/target/site/jacoco/index.html`.

| Pacote | Linhas | Branches | Ramos não cobertos | Risco |
| --- | ---: | ---: | --- | --- |
| evidencias.dominio | 88,7% | 74,2% | 16 guardas/combinações | baixo |
| evidencias.aplicacao | 97,9% | 85,3% | 5 caminhos defensivos | baixo |
| priorizacao.dominio | 96,9% | 83,7% | 15 guardas/justificativas | baixo |
| priorizacao.aplicacao | 99,3% | 83,3% | 3 caminhos defensivos | baixo |
| revisoes.dominio | 93,5% | 76,5% | 8 limites/guardas | baixo |
| revisoes.aplicacao | 97,0% | 87,5% | 3 caminhos defensivos | baixo |
| planejamento.dominio | 94,9% | 78,3% | 86 combinações auxiliares | médio |
| planejamento.aplicacao | 91,2% | 68,8% | 128 erros/combinações | médio |

A cobertura não foi usada isoladamente: limites, concorrência, rollback e o
fluxo completo possuem provas comportamentais.

## 6. Achados

### AUD-001

**ID:** AUD-001. **Severidade:** P0. **Módulo:** dashboard/segurança.
**Regra esperada:** nenhuma agregação pode expor dados de outro usuário.
**Comportamento encontrado:** um mapeamento confirmado inconsistente podia
contabilizar estudo/tempo/atividade de tópico de outra matéria ou usuário.
**Como reproduzir:** inserir o mapeamento inconsistente e consultar o dashboard.
**Teste que comprova:** `DashboardIntegracaoTest` e
`SegurancaEDesempenhoDoMotorAdaptativoIntegracaoTest`.
**Causa raiz:** joins sem reafirmar proprietário e igualdade de matéria.
**Arquivos e linhas:** `ConsultaDoDashboard.java:158-342`.
**Impacto para o estudante:** vazamento e indicadores incorretos.
**Correção recomendada/aplicada:** filtros de usuário, matéria e proprietário
em todas as agregações. **Risco da correção:** baixo; consultas ficaram mais
restritivas.

### AUD-002

**ID:** AUD-002. **Severidade:** P1. **Módulo:** estudos HTTP/frontend.
**Regra esperada:** retry não duplica registro nem evidência.
**Comportamento encontrado:** `POST /api/v1/estudos` não possuía idempotência.
**Como reproduzir:** repetir ou concorrer a mesma requisição.
**Teste que comprova:** `IdempotenciaDoRegistroDeEstudoIntegracaoTest` (8
cenários) e `ControleDeTentativaIdempotente.spec.ts`.
**Causa raiz:** ausência de chave/recibo transacional.
**Arquivos e linhas:** `ServicoDeRegistroIdempotenteDeEstudo.java:1-153`,
`V19__adiciona_idempotencia_ao_registro_de_estudos.sql`.
**Impacto para o estudante:** métricas e evidências duplicadas.
**Correção recomendada/aplicada:** chave por usuário, hash canônico, advisory
lock, recibo imutável, 409 em conflito e chave estável no frontend.
**Risco da correção:** baixo; sem cabeçalho o contrato legado é preservado.

### AUD-003

**ID:** AUD-003. **Severidade:** P1. **Módulo:** revisões/planejamento.
**Regra esperada:** somente bloco planejado ou em andamento bloqueia nova
revisão. **Comportamento encontrado:** concluído/parcial também bloqueava.
**Como reproduzir:** concluir a revisão e gerar a semana seguinte.
**Teste que comprova:** `GeracaoComRevisoesIntegracaoTest`.
**Causa raiz:** filtro `estado <> CANCELADO`.
**Arquivos e linhas:** `ServicoDeGeracaoDeterministica.java:342`,
`AssinadorDaPreviaDaGeracao.java:180`.
**Impacto para o estudante:** próxima revisão desaparecia.
**Correção recomendada/aplicada:** aceitar como abertos apenas `PLANEJADO` e
`EM_ANDAMENTO`. **Risco da correção:** baixo, coberto por regressão.

### AUD-004

**ID:** AUD-004. **Severidade:** P1. **Módulo:** diagnóstico.
**Regra esperada:** mapeamento oficial deve apontar para tópico da mesma matéria
do item. **Comportamento encontrado:** confirmação inconsistente tornava tópico
de outra matéria oficialmente exigido. **Como reproduzir:** inserir a associação
cruzada e consultar diagnóstico. **Teste que comprova:**
`AuditoriaDeDiagnosticoEPriorizacaoIntegracaoTest`.
**Causa raiz:** CTE usava apenas o identificador do tópico.
**Arquivos e linhas:** `ConsultaDeDiagnosticoDeTopicos.java:46-64,160-178`.
**Impacto para o estudante:** diagnóstico e plano orientados ao conteúdo errado.
**Correção recomendada/aplicada:** exigir `topico.materia_id = mp.materia_id`.
**Risco da correção:** baixo; descarta somente dados inválidos.

### AUD-005

**ID:** AUD-005. **Severidade:** P1. **Módulo:** evidências/Flyway.
**Regra esperada:** questões e acertos devem ser ambos nulos ou ambos válidos.
**Comportamento encontrado:** a restrição antiga aceitava par incompleto pelo
valor SQL `UNKNOWN`. **Como reproduzir:** `INSERT` direto com somente um campo.
**Teste que comprova:** `AuditoriaDeEvidenciasIntegracaoTest`.
**Causa raiz:** `CHECK` sem testar explicitamente a nulidade pareada.
**Arquivos e linhas:** `V18__fortalece_resultado_de_questoes_das_evidencias.sql`.
**Impacto para o estudante:** percentuais corrompidos por dados inválidos.
**Correção recomendada/aplicada:** nova restrição explícita.
**Risco da correção:** baixo; dados existentes eram compatíveis.

### AUD-006

**ID:** AUD-006. **Severidade:** P2. **Módulo:** assinatura da prévia.
**Regra esperada:** mudança irrelevante não invalida a prévia.
**Comportamento encontrado:** sugestão não confirmada, material arquivado e
bloco encerrado participavam da assinatura. **Como reproduzir:** alterar um
desses fatos e aplicar a prévia. **Teste que comprova:**
`GeracaoComRevisoesIntegracaoTest`. **Causa raiz:** universo assinado era maior
que o universo decisório. **Arquivos e linhas:**
`AssinadorDaPreviaDaGeracao.java:120-183`. **Impacto para o estudante:** conflito
espúrio ao aplicar o plano. **Correção recomendada/aplicada:** assinar apenas
fatos confirmados, ativos e blocos abertos. **Risco da correção:** baixo.

### AUD-007

**ID:** AUD-007. **Severidade:** P2. **Módulo:** MCP.
**Regra esperada:** schema MCP deve refletir os limites do domínio.
**Comportamento encontrado:** zero questões e descrição de padrão até 500 eram
anunciados embora inválidos. **Como reproduzir:** inspecionar catálogo/schema.
**Teste que comprova:** `CatalogoDeFerramentasMcpSchemaTest`.
**Causa raiz:** limites duplicados divergentes. **Arquivos e linhas:**
`CatalogoDeFerramentasMcp.java`. **Impacto para o estudante:** preparação de
operação inevitavelmente rejeitada. **Correção recomendada/aplicada:** mínimo 1
e máximo 200. **Risco da correção:** baixo.

### AUD-008

**ID:** AUD-008. **Severidade:** P2. **Módulo:** autenticação.
**Regra esperada:** conta inativada não usa sessão antiga.
**Comportamento encontrado:** o login novo era negado, mas a identidade da
sessão preexistente ainda era resolvida. **Como reproduzir:** autenticar,
inativar no banco e consultar API. **Teste que comprova:**
`SegurancaEDesempenhoDoMotorAdaptativoIntegracaoTest`. **Causa raiz:** resolvedor
não filtrava situação. **Arquivos e linhas:** `IdentidadeDoUsuarioAtual.java:20`.
**Impacto para o estudante:** acesso após revogação. **Correção aplicada:** exigir
`ATIVO`. **Risco da correção:** baixo; o cookie não é destruído, mas as APIs
negam acesso.

### AUD-009

**ID:** AUD-009. **Severidade:** P2. **Módulo:** frontend/datas.
**Regra esperada:** datas civis seguem `America/Sao_Paulo` e fatos futuros não
entram no recente. **Comportamento encontrado:** timezone do navegador e apenas
limite inferior eram usados. **Como reproduzir:** executar em UTC e incluir fato
futuro. **Teste que comprova:** `fusoHorario.spec.ts` e
`EstudosPagina.spec.ts`. **Causa raiz:** conversões dispersas com `Date`.
**Arquivos e linhas:** `fusoHorario.ts`, `EstudosPagina.vue`,
`RegistroRapidoDeEstudo.vue`. **Impacto para o estudante:** dia/semana e filtros
incorretos. **Correção aplicada:** utilitário central SP e limite superior.
**Risco da correção:** baixo.

### AUD-010

**ID:** AUD-010. **Severidade:** P2. **Módulo:** frontend/explicabilidade.
**Regra esperada:** interface mostra fatos do backend e atualiza consumidores.
**Comportamento encontrado:** ranking podia ficar antigo; revisões futuras não
apareciam; cobertura era chamada de consolidação. **Como reproduzir:** registrar
estudo e visitar dashboard/planejamento. **Teste que comprova:** specs de
`InicioPagina`, `PlanejamentoHojePagina` e `PriorizacaoDeTopicosPagina`.
**Causa raiz:** evento não consumido e cópia inferencial. **Arquivos e linhas:**
componentes citados. **Impacto para o estudante:** mensagem e decisão visual
enganosas. **Correção aplicada:** refresh, quatro estados de revisão e textos
factuais. **Risco da correção:** baixo.

### AUD-011 — remanescente

**ID:** AUD-011. **Severidade:** P2. **Módulo:** diagnóstico/priorização.
**Regra esperada:** semânticas parecidas devem ser deliberadas e explicadas.
**Comportamento encontrado:** diagnóstico e ranking diferem em estudo sem
evidência, repetição histórica/recente e resposta a contexto oficial vazio.
**Como reproduzir:** cenários da classe de auditoria de priorização.
**Teste que comprova:** `AuditoriaDeDiagnosticoEPriorizacaoIntegracaoTest`.
**Causa raiz:** consultas foram evoluídas para finalidades diferentes.
**Arquivos e linhas:** `ConsultaDeDiagnosticoDeTopicos.java` e
`ConsultaDePriorizacaoDeTopicos.java`. **Impacto para o estudante:** números
distintos em telas diferentes podem exigir explicação. **Correção recomendada:**
decisão de produto antes de unificar. **Correção aplicada:** nenhuma alteração
pedagógica. **Risco da correção:** alto sem decisão explícita.

### AUD-012 — remanescente

**ID:** AUD-012. **Severidade:** P3. **Módulo:** priorização/contrato.
**Regra esperada:** posição e desempates devem ser autoexplicativos.
**Comportamento encontrado:** `posicaoNoGrupo` não é posição global e todos os
desempates não aparecem nas justificativas. **Como reproduzir:** comparar tópicos
empatados de grupos/matérias diferentes. **Teste que comprova:** testes de
caracterização do ranking. **Causa raiz:** contrato expõe resultado resumido.
**Arquivos e linhas:** `ConsultaDePriorizacaoDeTopicos.java:260-311`.
**Impacto para o estudante:** ordem pode parecer arbitrária. **Correção
recomendada:** documentar o escopo ou ampliar DTO. **Correção aplicada:** nenhuma.
**Risco da correção:** compatibilidade de API.

### AUD-013 — remanescente

**ID:** AUD-013. **Severidade:** P3. **Módulo:** persistência/segurança.
**Regra esperada:** invariantes indiretos de proprietário e matéria permanecem
consistentes. **Comportamento encontrado:** FKs simples não codificam todos esses
vínculos; casos de uso validam e consultas críticas agora se defendem.
**Como reproduzir:** inserir SQL inconsistente fora dos casos de uso.
**Teste que comprova:** suíte de segurança/constraints. **Causa raiz:** modelo
relacional sem chaves compostas/trigger/RLS. **Arquivos e linhas:** migrations
V1-V19. **Impacto para o estudante:** risco apenas se outro escritor contornar a
aplicação. **Correção recomendada:** ADR antes de defesa estrutural ampla.
**Correção aplicada:** filtros defensivos no dashboard/diagnóstico.
**Risco da correção:** mudança arquitetural e de migração.

## 7. SQL e desempenho

`EXPLAIN ANALYZE` do SQL real, com 30 mil fatos:

| Consulta | Execução |
| --- | ---: |
| padrões repetidos | 12,195 ms |
| diagnóstico de tópicos | 218,913 ms |
| priorização | 200,927 ms |
| revisões | 182,370 ms |

Todos os planos ficaram em memória (`quicksort`, sem spill/temp). Os scans
sequenciais ocorreram com seletividade de 50%; não houve evidência para criar
índice ou migration especulativa.

## 8. Correções realizadas

- V18 reforçou a integridade de evidências; V19 acrescentou idempotência.
- Consultas de dashboard, diagnóstico e prioridade foram isoladas por
  usuário/matéria.
- Revisões encerradas deixaram de bloquear o ciclo seguinte.
- Assinatura da prévia passou a refletir somente fatos decisórios.
- Frontend centralizou fuso, idempotência, refresh e estados/cópias factuais.
- MCP e sessão inativa receberam validações coerentes.

## 9. Riscos restantes

1. Semânticas distintas entre diagnóstico e ranking aguardam decisão de produto.
2. Invariantes indiretos não estão todos codificados no banco.
3. Branch coverage de `planejamento.aplicacao` é 68,8%.
4. Recibos idempotentes ainda não têm política de retenção.
5. Não houve inspeção visual em navegador real nem scanner Maven.

## 10. Decisões de produto pendentes

- Manter ou alinhar as semânticas históricas/recentes das duas consultas.
- Confirmar limites de 20 questões e 70%/85%.
- Confirmar intervalos 1/3/7/14/30/60 e efeito da recordação.
- Confirmar se dificuldade/material alteram faixa ou apenas justificativa.
- Formalizar a relação entre prioridade manual e automática.
- Decidir se replanejamento existente deve reagir ao ranking ou apenas nova
  geração deve fazê-lo.

## 11. Reprodução

```bash
git status --short
docker compose config
make verificar

cd aplicativos/backend
./mvnw -Dtest=FluxoAdaptativoCompletoIntegracaoTest test
./mvnw -Dtest=SegurancaEDesempenhoDoMotorAdaptativoIntegracaoTest test

cd ../frontend
TZ=UTC npm run test -- fusoHorario EstudosPagina RegistroRapidoDeEstudo \
  PlanejamentoHojePagina PlanejamentoSemanaPagina InicioPagina \
  ControleDeTentativaIdempotente
```
