# Plano de correções remanescentes

Não há P0/P1 remanescente.

| Ordem | Achado | Severidade | Arquivos | Teste necessário | Solução | Dependências | Critério de aceite |
| ----: | --- | --- | --- | --- | --- | --- | --- |
| 1 | AUD-011 | P2 | consultas de diagnóstico/priorização | caracterização comum | decidir e documentar as semânticas; depois alinhar somente o aprovado | decisão pedagógica/produto | ambas as APIs explicam diferenças ou usam a mesma regra |
| 2 | AUD-012 | P3 | DTO/consulta de priorização | ranking com empates | explicitar escopo da posição e desempates | compatibilidade da API | ordem reproduzível e compreensível pelo cliente |
| 3 | AUD-013 | P3 | modelo/migrations | integração com SQL inconsistente | avaliar chaves compostas, trigger ou RLS em ADR | decisão arquitetural | vínculos inválidos rejeitados no nível escolhido |
| 4 | Retenção idempotente | P3 | V19/serviço de estudos | expiração concorrente | definir prazo e limpeza segura de recibos | política operacional | retries dentro do prazo preservados e tabela limitada |
| 5 | Cobertura de planejamento | P3 | `planejamento.aplicacao` | ramos de falha restantes | cobrir somente caminhos de risco em mudanças futuras | nenhuma | branch coverage cresce sem testes artificiais |
