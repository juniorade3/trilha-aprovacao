# Matriz de cenários testados

| ID | Cenário | Camada | Resultado esperado | Teste | Resultado obtido | Situação |
| --- | --- | --- | --- | --- | --- | --- |
| C01 | limites/obrigatoriedade da evidência | domínio/API/DB | rejeitar inválidos sem persistir | `AuditoriaDeEvidenciasIntegracaoTest` | conforme | OK |
| C02 | normalização e padrão repetido | API/DB | acento/caixa/espaço agrupados; ocorrências somadas | mesma classe | conforme | OK |
| C03 | correção/cancelamento | API/SQL | histórico preservado, fato antigo inativo | mesma classe | conforme | OK |
| C04 | rollback de padrão | transação/DB | nenhum registro parcial | mesma classe | conforme | OK |
| C05 | retry sequencial/concorrente de estudo | API/DB | uma operação; conflito divergente 409 | `IdempotenciaDoRegistroDeEstudoIntegracaoTest` | 8/8 | OK |
| C06 | isolamento de padrões entre usuários | API/SQL | nenhum compartilhamento | auditoria de evidências | conforme | OK |
| C07 | janelas 30 dias e fuso SP | API/SQL | início/fim inclusivos; futuro excluído | auditoria de priorização | conforme | OK |
| C08 | 19/20, 69,99/70, 84,99/85 | domínio/API | faixas nos limites especificados | `ClassificadorDePriorizacaoTest` + integração | conforme | OK |
| C09 | recordação 1-5/dificuldade 4-5 | domínio/API | pior sinal prevalece; explicação factual | mesmos testes | conforme | OK |
| C10 | material ativo/arquivado/ausente | API/SQL | apenas ativo como indicador | auditoria de priorização | conforme | OK |
| C11 | mapeamento sugerido/cruzado | API/SQL | não oficial/não elegível | auditoria de priorização | conforme após correção | OK |
| C12 | revisões fora de ordem/mesmo instante/dia | domínio | ordem estável, uma revisão diária efetiva | `CalculadorDeRevisaoEspacadaTest` | 8/8 | OK |
| C13 | revisão vencida/hoje/futura/aberta | API/gerador | estado e antiduplicação corretos | revisão + geração | conforme | OK |
| C14 | bloco concluído/parcial/cancelado | gerador/DB | só aberto bloqueia próxima revisão | `GeracaoComRevisoesIntegracaoTest` | 7/7 | OK |
| C15 | prioridades ALTA/NORMAL/BAIXA/NAO_INCLUIR | domínio/API | peso manual sem apagar diagnóstico | gerador + planejamento | conforme | OK |
| C16 | lista duplicada/incompleta/inelegível | API/DB | 422 e estado anterior intacto | `PlanejamentoIntegracaoTest` | conforme | OK |
| C17 | determinismo/assinatura/stale preview | domínio/API/DB | igualdade e rejeição transacional | suítes de planejamento | conforme | OK |
| C18 | aplicação concorrente/replanejamento | API/DB | uma aplicação; concluídos preservados | `PlanejamentoIntegracaoTest` | conforme | OK |
| C19 | autenticação/CSRF/IDs alheios | segurança/API | 401/403/404 e agregados isolados | suíte de segurança | 5/5 | OK |
| C20 | conta inativada com sessão antiga | segurança/API | acesso negado | suíte de segurança | conforme após correção | OK |
| C21 | SQL com massa representativa | PostgreSQL | sem spill e latência aceitável | suíte de segurança/desempenho | 12-219 ms | OK |
| C22 | datas 23h59/00h00/UTC/mês/ano/futuro | frontend | data civil de São Paulo | `fusoHorario.spec.ts` e páginas | conforme em TZ local/UTC | OK |
| C23 | estados de revisão e refresh | frontend | vencida/hoje/futura/planejada e ranking atual | specs de planejamento | conforme | OK |
| C24 | fluxo adaptativo integral | API/PostgreSQL | dado → diagnóstico → decisão → plano | `FluxoAdaptativoCompletoIntegracaoTest` | 50%/fraqueza → 100%/consolidado; revisão segunda → domingo | OK |
| C25 | mutações manuais críticas | domínio | suíte deve falhar | quatro execuções controladas | 4/4 mutantes mortos e revertidos | OK |
