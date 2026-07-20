# Algoritmo e limitações da Geração Determinística

## 1. Entradas

O gerador recebe o plano semanal em rascunho, sete dias de disponibilidade,
matérias elegíveis, prioridades, blocos preservados e as durações configuradas
para blocos principais e revisão. A sessão autenticada define o usuário de
todo o fluxo.

## 2. Dados preservados

Blocos manuais e blocos gerados já ajustados permanecem na prévia e na
regeneração. Blocos cancelados não consomem capacidade. Na regeneração, apenas
blocos com origem `GERADO_DETERMINISTICAMENTE` são substituídos.

## 3. Cálculo da capacidade

Para cada dia, os minutos livres são a disponibilidade menos a duração dos
blocos preservados não cancelados. Sugestões nunca fazem a soma diária
ultrapassar a disponibilidade. Atividades livres consomem capacidade, mas não
contam como matéria.

## 4. Reserva de revisão

A revisão é considerada antes dos blocos principais. Ela possui o título
`Revisão do dia`, não recebe matéria nem tópico e só é sugerida quando sua
duração completa cabe. Uma revisão preservada impede a criação de outra no
mesmo dia. Duração zero desativa a sugestão.

## 5. Meta de três matérias

O gerador busca até três matérias distintas por dia, contando matérias que já
aparecem em blocos preservados. A mesma matéria não é sugerida duas vezes no
mesmo dia. Quando faltam capacidade ou candidatas, a quantidade é reduzida e o
motivo aparece nos avisos.

## 6. Rodízio ponderado

As prioridades Alta, Normal e Baixa usam pesos 3, 2 e 1. A escolha compara a
carga semanal normalizada, calculada como minutos já planejados divididos pelo
peso. `NAO_INCLUIR` remove a matéria somente da geração daquela semana.

## 7. Desempates estáveis

A seleção evita repetir a matéria do dia anterior quando existe alternativa e,
em seguida, compara carga normalizada, prioridade, ocorrências semanais, ordem
da estrutura, nome normalizado e UUID. Todas as coleções relevantes são
ordenadas explicitamente.

## 8. Duração dos blocos

Depois de reservar a revisão, a duração base é o menor valor entre a duração
padrão e a divisão inteira dos minutos livres pela quantidade de matérias. Um
bloco principal tem no mínimo 25 minutos e nunca ultrapassa o padrão. Diferenças
de um minuto podem ser distribuídas pela ordem estável; minutos excedentes
podem permanecer livres.

## 9. Justificativas

A prévia apresenta códigos e mensagens para prioridades, equilíbrio semanal,
alternância, meta diária, revisão, preservação, falta de capacidade e minutos
livres. Ao aplicar, cada bloco armazena somente um resumo textual da
justificativa.

## 10. Aplicação e regeneração

A prévia não persiste blocos. A aplicação recalcula a proposta no backend em
uma transação e não confia em blocos enviados pela interface. Uma geração
anterior exige confirmação. O plano é bloqueado para atualização durante a
regeneração, e as ordens dos blocos são normalizadas por dia.

## 11. Determinismo

O algoritmo não usa aleatoriedade, horário atual ou ordem acidental de
coleções. Entradas semanticamente iguais, mesmo apresentadas em ordens
diferentes, produzem a mesma saída.

## 12. Limitações atuais

- não escolhe tópicos;
- não escolhe conteúdo da revisão;
- não usa desempenho;
- não usa registros de erros;
- não usa IA;
- não gera em plano ativo;
- não preenche necessariamente todo minuto livre;
- não cria mais de um bloco da mesma matéria no dia;
- não mantém histórico de versões da geração;
- a justificativa persistida é um resumo textual de até 2.000 caracteres.
