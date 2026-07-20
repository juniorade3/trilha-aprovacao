# Planejamento 2 — Geração Determinística

## Objetivo

Evoluir o Planejamento Manual do Trilha da Aprovação para gerar uma proposta semanal previsível, explicável e editável, contendo:

- seleção automática de matérias;
- distribuição conforme a disponibilidade;
- até três matérias distintas por dia;
- bloco diário de revisão;
- prioridades manuais;
- justificativas para escolhas e exceções.

A geração é determinística: as mesmas entradas devem produzir a mesma saída. Não utiliza IA, aprendizado de máquina, Motor de Evidências, Motor de Lacunas ou Motor de Revisões.

## Linha de base observada

Na revisão do repositório:

- o módulo `planejamento` já existe no backend e frontend;
- o Planejamento Manual está implementado até a Sprint 07 na `main`;
- a última migration observada é a `V10`;
- já existem plano semanal, disponibilidade, blocos, execução, Hoje, Semana, reagendamento e integração com estudos;
- o serviço principal atual é `ServicoDePlanejamento`.

Antes de implementar, confirmar novamente o `HEAD`, a última migration e a conclusão da Sprint 08 do Planejamento Manual.

## Divisão compacta

| Sprint | Entrega | Valor |
| --- | --- | --- |
| 01 | Matérias elegíveis, prioridades e prévia completa | o usuário configura a estratégia e vê uma semana gerada sem alterar o plano |
| 02 | Aplicação, regeneração segura e edição dos gerados | a prévia vira blocos reais sem apagar trabalho manual |
| 03 | Consolidação e aceite integral | fluxo completo, Swagger, segurança, acessibilidade e regressões validados |

A divisão anterior em seis sprints foi condensada em três para reduzir releitura, prompts e troca de contexto.

## Dependência

O Planejamento Manual, incluindo sua Sprint 08, deve estar concluído e mesclado na `main`.

## Jornada final

```text
Abrir semana em rascunho
→ conferir disponibilidade
→ definir prioridades
→ configurar durações
→ gerar prévia
→ entender justificativas
→ aplicar
→ editar manualmente
→ regenerar com segurança
→ ativar o plano
```

## Definição de pronto

1. somente matérias do concurso ativo e cargo selecionado são elegíveis;
2. matéria repetida em vários grupos aparece uma vez;
3. prioridades Alta, Normal, Baixa e Não incluir funcionam;
4. mesmas entradas produzem a mesma prévia;
5. capacidade diária e blocos preservados são respeitados;
6. o sistema busca até três matérias distintas por dia;
7. revisão é reservada quando houver capacidade;
8. exceções são explicadas;
9. prévia não persiste blocos;
10. aplicação recalcula no backend;
11. blocos manuais e ajustados são preservados;
12. regeneração substitui somente gerados puros;
13. usuário A não acessa dados de B;
14. backend, frontend, migrations, Swagger e fluxo manual ficam verdes.

## Fora do escopo

- seleção automática de tópicos;
- conteúdo automático da revisão;
- revisão espaçada;
- Motor de Evidências;
- Motor de Lacunas;
- análise de desempenho;
- recomendação por IA;
- Timefold, OR-Tools ou outro otimizador;
- notificações;
- calendário externo;
- geração para plano ativo;
- geração mensal.

## Ordem de execução

1. Ler `CONTEXTO-COMUM.md`.
2. Implementar somente a sprint autorizada.
3. Executar portas de qualidade.
4. Preencher o registro de conclusão.
5. Fazer commit e PR.
6. Parar antes da sprint seguinte.

## Local recomendado

```text
documentacao/planejamento-geracao-deterministica/
```
