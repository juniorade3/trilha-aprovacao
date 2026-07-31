# Mapa do motor adaptativo

Data do levantamento inicial: 24 de julho de 2026.

## Objetivo e limites deste mapa

Este documento registra a rastreabilidade estática encontrada antes de qualquer
correção no motor adaptativo. Resultados de testes, divergências e correções
confirmadas serão consolidados em
`AUDITORIA-DO-MOTOR-ADAPTATIVO.md` e
`MATRIZ-DE-CENARIOS-TESTADOS.md`.

O fato-base para os cálculos é o `registro_de_estudo` cuja situação é `ATIVO`.
Correções preservam o registro e a evidência anteriores e criam um novo
registro ativo; cancelamentos apenas mudam a situação. As consultas derivadas
devem, portanto, ignorar evidências ligadas a registros `CORRIGIDO` ou
`CANCELADO`.

## Fluxo completo

```text
ação do estudante
    ↓
registro de estudo
    ↓
evidência de aprendizagem
    ↓
padrões de erro
    ↓
diagnóstico do tópico
    ↓
classificação/priorização
    ↓
agenda de revisões
    ↓
seleção automática
    ↓
geração ou replanejamento semanal
    ↓
bloco executado
    ↓
novo registro e nova evidência
```

## Etapas e rastreabilidade

| Etapa | Entrada | Regra aplicada | Classes principais | Tabelas | Saída | Consumidores | Testes existentes no levantamento |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Ação do estudante | Formulário web, execução de bloco ou operação assistida confirmada | Sessão/CSRF na web; identidade derivada da credencial e vínculo no MCP; tipo de estudo define o resultado obrigatório | Frontend: `RegistroRapidoDeEstudo`, `CamposDeEvidencia`, páginas de estudos/planejamento; APIs: `ControladorDeMateriaisEEstudos`, `ControladorDeBlocosDeEstudo`; automação: `ServicoDePreparacoesMcp`, `ServicoDeAplicacaoDeOperacoesAssistidas` | Ainda não há escrita nesta etapa; operação assistida usa `operacoes_assistidas` e auditoria | Comando tipado com tópico, material, data, duração, tipo e evidência opcional | Serviços de estudos e planejamento | `RegistroRapidoDeEstudo.spec.ts`, `CamposDeEvidencia.spec.ts`, `MateriaisEEstudosIntegracaoTest`, `IntegracaoPlanejamentoEstudosTest`, `AutomacaoIntegracaoTest`, `McpIntegracaoTest` |
| Registro de estudo | Usuário efetivo, tópico, material opcional, tipo, instante, duração e observação | Tópico/matéria/material devem pertencer ao usuário e estar ativos; material informado deve cobrir o tópico; duração entre 1 e 1440; correção encerra o original e cria sucessor; cancelamento preserva o fato | `ServicoDeMateriaisEEstudos`, `RegistroDeEstudo`, `RegistroDeEstudoPersistido`, repositórios de estudos | `registros_de_estudo`, `materiais_de_estudo`, `coberturas_de_topicos_por_material`, `topicos_da_materia`, `materias` | Registro `ATIVO`, ou cadeia histórica `CORRIGIDO`/sucessor; cancelamento em `CANCELADO` | Evidências, diagnóstico, priorização, revisões, dashboard, execução e histórico | `MateriaisEEstudosTest`, `MateriaisEEstudosIntegracaoTest`, `IntegracaoPlanejamentoEstudosTest`, `DashboardIntegracaoTest` |
| Evidência de aprendizagem | Registro salvo, tipo de estudo e `DadosDaEvidencia` | Questões e acertos são informados juntos; questões > 0; 0 ≤ acertos ≤ questões; recordação/dificuldade entre 1 e 5; ao menos um resultado; questões/simulado/caderno exigem questões e acertos; revisão exige recordação quando o resultado é obrigatório | `ServicoDeEvidenciasDeAprendizagem`, `EvidenciaDeAprendizagem`, `DadosDaEvidencia`, DTOs `RequisicaoDeEvidencia` e `RespostaDeEvidencia` | `evidencias_de_aprendizagem`, FK única para `registros_de_estudo` | Uma evidência por registro; erros e resultado de revisão derivados | Diagnóstico, priorização, revisões, respostas de estudo e planejamento | `EvidenciaDeAprendizagemTest`, `MateriaisEEstudosIntegracaoTest`, `IntegracaoPlanejamentoEstudosTest` |
| Padrões de erro | Descrição e quantidade por evidência de questões | Texto é aparado, espaços internos são colapsados e caixa/acentos são removidos para a chave normalizada; duplicata normalizada na mesma evidência é inválida; soma das ocorrências não supera os erros; catálogo é isolado por usuário e tópico | `NormalizacaoDePadraoDeErro`, `DadosDoPadraoDeErro`, `PadraoDeErroPersistido`, `OcorrenciaDePadraoDeErroPersistida`, `ServicoDeEvidenciasDeAprendizagem` | `padroes_de_erro`, `ocorrencias_de_padrao_de_erro`, `evidencias_de_aprendizagem` | Padrão canônico reutilizável e ocorrência vinculada à evidência | Sugestões do formulário, diagnóstico e priorização | `EvidenciaDeAprendizagemTest`, `MateriaisEEstudosIntegracaoTest`, `ClassificadorDePriorizacaoTest`, `PriorizacaoDeTopicosIntegracaoTest` |
| Diagnóstico do tópico | Usuário, data de referência, matéria opcional e filtro de conteúdo exigido | Janela civil inclusiva de 30 dias (`referência - 29` até referência) em `America/Sao_Paulo`; somente registros ativos e não futuros; tópicos/matérias arquivados saem da lista; contexto oficial usa concurso ativo, cargo selecionado, edital principal e mapeamento confirmado | `ConsultaDeDiagnosticoDeTopicos`, `DiagnosticoDeTopico`, `ControladorDeEvidencias` | `concursos`, `cargos_do_concurso`, `editais`, `provas`, `grupos_de_conteudo`, `materias_da_prova`, `itens_do_edital`, `mapeamentos_de_itens_do_edital`, `materias`, `topicos_da_materia`, `registros_de_estudo`, `evidencias_de_aprendizagem`, tabelas de padrões | Totais históricos/recentes, percentual, últimas/médias de recordação e dificuldade, última revisão/evidência e padrões repetidos | API de diagnóstico e frontend de estudos; referência comparativa da auditoria | `MateriaisEEstudosIntegracaoTest` (`deveDiagnosticarJanelaInclusiva...`) |
| Classificação e priorização | Conteúdo oficial exigido, estudos/evidências recentes, padrões repetidos e existência de material | Somente mapeamentos confirmados do concurso/cargo/edital ativos; grupo/faixa usa a pior evidência relevante; decisões atuais incluem mínimo de 20 questões e faixas de 70%/85%; recordação baixa e padrão repetido podem prevalecer; dificuldade alta e falta de material atualmente geram justificativa, não mudam a faixa | `ConsultaDePriorizacaoDeTopicos`, `SinaisDePriorizacao`, `ClassificadorDePriorizacao`, `ClassificacaoDaPriorizacao`, `JustificativaDaPriorizacao`, `ControladorDePriorizacaoDeTopicos` | Mesmas tabelas oficiais e de evidências; `materiais_de_estudo` e `coberturas_de_topicos_por_material` para existência de material | Contexto, resumo, itens sem mapeamento e tópicos com grupo, faixa, posição, ação, indicadores e justificativas | Página `PriorizacaoDeTopicosPagina`, MCP, `ServicoDeGeracaoDeterministica` | `ClassificadorDePriorizacaoTest`, `PriorizacaoDeTopicosIntegracaoTest`, specs da API/página de priorização |
| Agenda de revisões | Evidências ativas de tópicos oficiais e data de referência/horizonte | Primeira evidência inicia etapa; eventos de revisão com recordação movem a etapa entre 0 e 5; intervalos atuais são decisão de produto; estudo comum posterior não substitui a data-base; eventos são ordenados por instante e identificador; bloco aberto de revisão marca `JA_PLANEJADA` | `ConsultaDeRevisoesEspacadas`, `CalculadorDeRevisaoEspacada`, `EventoDeRevisaoEspacada`, `RevisaoEspacadaCalculada`, `ControladorDeRevisoesEspacadas` | Evidências/registros e contexto oficial; `blocos_de_estudo` e `planos_semanais` para revisão aberta | Revisões `VENCIDA`, `DEVIDA_HOJE`, `FUTURA` ou `JA_PLANEJADA`, com etapa, intervalo, data-base e bloco aberto | Página/cliente de revisões, MCP e geração determinística | `CalculadorDeRevisaoEspacadaTest`, `RevisoesEspacadasIntegracaoTest`, `GeradorDeterministicoComPriorizacaoTest`, specs da API de revisões |
| Seleção automática | Prioridades automáticas, prioridade manual da matéria, revisões devidas, disponibilidade e blocos preserváveis | `NAO_INCLUIR` exclui; ALTA/NORMAL/BAIXA ponderam matéria sem reescrever o diagnóstico; revisões específicas são reservadas antes dos blocos principais; não repetir revisão aberta/preservada nem revisão e bloco principal do mesmo tópico no dia | `ServicoDeGeracaoDeterministica`, `GeradorDeterministicoDePlano`, `CandidatoDeMateriaParaGeracao`, `CandidatoDeTopicoParaGeracao`, `CandidatoDeRevisaoParaGeracao`, `PrioridadeDaMateriaNoPlano` | `prioridades_de_materias_no_plano`, `disponibilidades_do_dia`, `blocos_de_estudo`, além das consultas de priorização/revisão | Candidatos ordenados e dias com blocos preservados/sugeridos e justificativas | Prévia semanal, assinatura e aplicação | `GeradorDeterministicoDePlanoTest`, `GeradorDeterministicoComPriorizacaoTest`, `PlanejamentoIntegracaoTest` |
| Geração semanal | Plano em rascunho, referência/configuração e candidatos atuais | Resultado deve ser determinístico; capacidade diária não pode ser excedida; blocos manuais/ajustados são preservados; prévia é assinada sobre estado relevante; aplicação bloqueia o plano e revalida assinatura | `ServicoDeGeracaoDeterministica`, `GeradorDeterministicoDePlano`, `AssinadorDaPreviaDaGeracao`, `PreviaDaGeracaoDaSemana`, `RepositorioDeReplanejamentos` (snapshot) | `planos_semanais`, `disponibilidades_do_dia`, `prioridades_de_materias_no_plano`, `blocos_de_estudo`, `blocos_originais_dos_planos` | Prévia sem escrita; depois blocos `GERADO_DETERMINISTICAMENTE` com justificativa | Semana/hoje no frontend, MCP de preparação/aplicação e execução | `PlanejamentoIntegracaoTest`, `GeradorDeterministicoDePlanoTest`, `GeradorDeterministicoComPriorizacaoTest`, `MigracoesDaGeracaoDeterministicaIntegracaoTest`, specs da gaveta de geração |
| Replanejamento | Pendências, capacidade restante, prioridades manuais, data de referência e confirmações | Não move concluídos; preserva ocupação; adia/divide respeitando limites; prévia assinada; aplicação serializada; histórico/snapshot preservados | `ServicoDeReplanejamento`, `ReplanejadorDeterministicoDePlano`, `RepositorioDeReplanejamentos`, `AssinadorDaPreviaDaGeracao` | Tabelas do plano e `blocos_originais_dos_planos`, `replanejamentos`, `itens_de_replanejamento`, `fragmentos_de_replanejamento` | Prévia e blocos `REPLANEJADO`, com justificativa e linhagem | Semana, histórico e automação | `ReplanejadorDeterministicoDePlanoTest`, `ReplanejadorComRevisaoEspecificaTest`, `PlanejamentoIntegracaoTest`, specs da gaveta de replanejamento |
| Bloco executado | Bloco aberto, início/fim, duração, resultado, tópico escolhido quando necessário e evidência | Uma execução por bloco e no máximo uma em andamento por usuário; conclusão/interrupção pode criar um único registro de estudo; retry semanticamente equivalente não cria outro estudo; correção preserva rastreabilidade; falha integrada causa rollback | `ServicoDePlanejamento`, `ExecucaoDoBloco`, `BlocoDeEstudo`, `ServicoDeMateriaisEEstudos` | `blocos_de_estudo`, `execucoes_de_bloco`, `registros_de_estudo`, tabelas de evidência | Bloco/execução finalizados e registro/evidência vinculados | Histórico, dashboard, diagnóstico, priorização, revisões e próxima geração | `ExecucaoDoBlocoTest`, `IntegracaoPlanejamentoEstudosTest`, `PlanejamentoIntegracaoTest`, specs de hoje/semana |
| Nova evidência | Novo estudo manual ou fato gerado pela execução | Repete a cadeia; somente o novo fato ativo entra nas projeções; uma nova prévia deve ler diagnóstico, ranking e revisões atuais | Mesmos serviços e consultas acima | Mesmas tabelas acima | Estado adaptativo recalculado, não persistido como ranking | Dashboard, ranking, agenda e geração seguintes | Ainda não havia, no levantamento inicial, um único teste nomeado que demonstrasse toda a cadeia `evidência → diagnóstico → decisão → plano`; essa prova é requisito da auditoria |

## Matriz dos sinais

| Sinal do estudante | Onde nasce | Onde é persistido | Onde é agregado | Onde altera uma decisão | Como aparece na explicação |
| --- | --- | --- | --- | --- | --- |
| Questões realizadas | Formulário/execução em `DadosDaEvidencia` | `evidencias_de_aprendizagem.quantidade_de_questoes` | Diagnóstico e `ConsultaDePriorizacaoDeTopicos`, em histórico e janela de 30 dias | Define suficiência mínima e, com acertos, a faixa automática; não agenda revisão sozinho | Indicador de questões recentes e justificativas de dados insuficientes/desempenho |
| Acertos | Mesmo ponto | `quantidade_de_acertos` | Soma e percentual recente | Define as bordas atuais de 70% e 85% quando há amostra suficiente | Acertos, percentual e justificativa da faixa |
| Erros | Derivado: questões − acertos | Não há coluna própria | SQL de diagnóstico/priorização e domínio da evidência | Influencia percentual; limita ocorrências de padrões | Total de erros e justificativas ligadas a desempenho/padrão |
| Recordação | Revisão ou evidência opcional | `nivel_de_recordacao` | Última/média no diagnóstico; sequência temporal nas revisões | Classificação pode usar recordação baixa; calculador move etapa/data da próxima revisão | Última recordação, resultado da revisão, etapa e data devida |
| Dificuldade percebida | Campo opcional em qualquer evidência | `dificuldade_percebida` | Última/média no diagnóstico; última na priorização | Na regra atual não muda grupo/faixa; apenas acrescenta justificativa. Decisão de produto a validar | Indicador e justificativa de dificuldade alta |
| Padrão de erro | Lista no resultado de questões | Catálogo `padroes_de_erro` e ocorrências por evidência | Contagem por evidências ativas e soma de ocorrências | Repetição em mais de uma evidência pode prevalecer sobre percentual alto | Quantidade/data de padrões repetidos e justificativa específica |
| Data da última evidência | `registro_de_estudo.data_hora` | Registro; evidência tem datas técnicas próprias | `MAX`/último evento por data e identificador | Determina desatualização, janela de 30 dias e data-base da revisão | Última evidência e justificativa de evidência desatualizada |
| Existência de material | Cadastro e cobertura ativa do tópico | `materiais_de_estudo`, `coberturas_de_topicos_por_material` | `EXISTS` na priorização e carga em lote para a geração/interface | Na regra automática atual não muda grupo/faixa; orienta ação/justificativa. Material arquivado não deve contar | `possuiMaterial` e justificativa de ausência |
| Prioridade manual | Configuração do plano | `prioridades_de_materias_no_plano` | Serviço de geração/replanejamento; ausência usa `NORMAL` | ALTA/NORMAL/BAIXA alteram peso/ordem da matéria; `NAO_INCLUIR` remove candidatos | Deve aparecer separada das justificativas automáticas; hoje a prévia expõe justificativas de geração, não altera o diagnóstico |
| Revisão vencida | Derivada da agenda em relação à referência | Não é estado persistido; bloco aberto é persistido | `ConsultaDeRevisoesEspacadas` | É reservada antes dos blocos principais, respeitando capacidade e antirrepetição | Situação, atraso, etapa e justificativa de revisão na prévia/bloco |
| Disponibilidade | Edição dos sete dias do plano | `disponibilidades_do_dia` | Distribuidor/gerador/replanejador | Limita minutos, quantidade e datas possíveis; não muda diagnóstico | Capacidade por dia, minutos livres/ocupados e avisos |

## Contexto oficial e elegibilidade

O caminho oficial usado nas decisões é:

```text
concursos (usuario_id, ativo)
  → cargos_do_concurso (selecionado)
  → editais (principal)
  → provas do cargo
  → grupos_de_conteudo
  → materias_da_prova
  → itens_do_edital
  → mapeamentos_de_itens_do_edital (confirmado)
  → topicos_da_materia e materias não arquivados
```

Mapeamento apenas sugerido (`confirmado = false`) não é elegível. Item sem
mapeamento permanece explícito na resposta de priorização e no dashboard.

## APIs e superfícies consumidoras

- Estudos e evidências:
  `POST /api/v1/estudos`,
  `PUT /api/v1/estudos/{id}/correcao`,
  `POST /api/v1/estudos/{id}/cancelamento`,
  `GET /api/v1/evidencias/padroes-de-erro` e
  `GET /api/v1/evidencias/diagnostico-de-topicos`.
- Priorização: `GET /api/v1/priorizacao-de-topicos`.
- Revisões: `GET /api/v1/revisoes-espacadas`.
- Planejamento: planos, disponibilidades, prioridades manuais, prévias,
  aplicação da geração/replanejamento, blocos e execuções sob `/api/v1`.
- Dashboard: `GET /api/v1/dashboard`.
- Frontend:
  `modulos/estudos`, `modulos/planejamento` e `modulos/inicio`.
- MCP:
  `ServicoDeConsultasMcp` reutiliza dashboard, priorização, revisões e
  planejamento; preparações persistem somente a operação; a escrita real
  passa por confirmação e `ServicoDeAplicacaoDeOperacoesAssistidas`.

## Tabelas centrais

| Grupo | Tabelas |
| --- | --- |
| Catálogo pessoal | `materias`, `topicos_da_materia` |
| Contexto oficial | `concursos`, `editais`, `cargos_do_concurso`, `provas`, `grupos_de_conteudo`, `materias_da_prova`, `itens_do_edital`, `mapeamentos_de_itens_do_edital` |
| Estudos | `materiais_de_estudo`, `coberturas_de_topicos_por_material`, `registros_de_estudo` |
| Evidências | `evidencias_de_aprendizagem`, `padroes_de_erro`, `ocorrencias_de_padrao_de_erro` |
| Plano | `planos_semanais`, `disponibilidades_do_dia`, `prioridades_de_materias_no_plano`, `blocos_de_estudo`, `execucoes_de_bloco` |
| Replanejamento/histórico | `blocos_originais_dos_planos`, `replanejamentos`, `itens_de_replanejamento`, `fragmentos_de_replanejamento` |
| Automação | `vinculos_de_canal`, `credenciais_de_integracao`, `operacoes_assistidas`, `eventos_de_auditoria_da_automacao` |

## Sinais coletados sem efeito decisório direto confirmado no levantamento

- `dificuldade_percebida`: altera indicadores e justificativa, não a faixa da
  priorização.
- ausência de material: altera `possuiMaterial` e justificativa, não a faixa.
- médias de recordação e dificuldade: expostas pelo diagnóstico; a
  priorização usa os últimos valores, não as médias.
- observação livre e duração do estudo: consumidas por histórico/dashboard,
  mas não pela classificação automática.
- quantidade bruta de ocorrências de um padrão: explicada e contada; a regra
  decisória de repetição é baseada na presença em evidências distintas.
- prioridade manual: influencia a geração por matéria, mas não deve ser
  interpretada como diagnóstico pedagógico.

Esses itens não são classificados automaticamente como defeitos: os limiares
e a semântica pedagógica são decisões de produto que precisam ser confirmadas.

## Explicabilidade encontrada

- A priorização retorna grupo, faixa, posição, ação sugerida, indicadores e
  justificativas.
- A prévia de geração retorna grupo/faixa do tópico e justificativas
  estruturadas; o bloco persistido guarda texto de justificativa.
- A agenda de revisões retorna etapa, intervalo, data devida, atraso e eventual
  bloco aberto.
- O MCP `explicar_bloco` combina a justificativa persistida com priorização e
  revisão atuais; estes últimos podem diferir do contexto histórico da geração,
  portanto a auditoria deve distinguir “motivo no momento da geração” de
  “estado atual”.
- O dashboard mede cobertura de tópicos com ao menos um estudo ativo; não é um
  diagnóstico de domínio e não deve ser apresentado como bom desempenho.

## Pontos que exigem prova executável na auditoria

1. Igualdade semântica entre diagnóstico e priorização nas janelas, fatos
   ativos, desempate temporal e padrões repetidos.
2. Rollback integral quando a persistência de padrão falha.
3. Idempotência do registro manual de estudo e da evidência em retries.
4. Efeito de correção/cancelamento em diagnóstico, ranking, revisão e plano.
5. Antiduplicação de revisão aberta, cancelada e durante regeneração.
6. Estabilidade da assinatura e recusa de prévia obsoleta.
7. Aplicações concorrentes da geração/replanejamento e finalização de bloco.
8. Isolamento por usuário em cada consulta JDBC, JPA, API e ferramenta MCP.
9. Bordas temporais em `America/Sao_Paulo`.
10. Cadeia completa:
    `nova evidência → diagnóstico alterado → decisão alterada → plano alterado`.

## Referências ausentes no checkout

O `AGENTS.md` raiz e os mapas de agente apontam para
`documentacao/arquitetura/`, `documentacao/decisoes/` e
`documentacao/assistente-telegram-mcp/`. Esses diretórios não estavam presentes
no checkout durante este levantamento. As decisões de produto sobre 20
questões, faixas de 70%/85%, intervalos de revisão e semântica da dificuldade
foram, por isso, rastreadas apenas no código e nos testes existentes e devem ser
tratadas como decisões pendentes de validação documental.
