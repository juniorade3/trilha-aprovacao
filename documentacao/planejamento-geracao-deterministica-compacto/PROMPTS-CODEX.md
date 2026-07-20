# Prompts curtos para o Codex

Use um prompt por sprint. O contexto compartilhado já está em `CONTEXTO-COMUM.md`.

## Sprint 01

```text
Implemente exclusivamente a Sprint 01 da Geração Determinística.

Leia:
- AGENTS.md
- documentacao/planejamento-geracao-deterministica/CONTEXTO-COMUM.md
- documentacao/planejamento-geracao-deterministica/sprint-01-prioridades-e-previa-completa.md

Antes de alterar código:
1. confirme que a Sprint 08 do Planejamento Manual está na main;
2. execute git status --short;
3. confirme a última migration;
4. preserve os padrões atuais.

Implemente backend, banco, frontend, testes e OpenAPI de forma integrada.
Não implemente aplicação ou regeneração.
Execute as validações exigidas no documento.
Crie branch e PR; não faça merge com CI vermelha.
Ao terminar, preencha o registro de conclusão e pare.
```

## Sprint 02

```text
Implemente exclusivamente a Sprint 02 da Geração Determinística.

Leia:
- AGENTS.md
- documentacao/planejamento-geracao-deterministica/CONTEXTO-COMUM.md
- documentacao/planejamento-geracao-deterministica/sprint-02-aplicacao-e-regeneracao-segura.md
- registro de conclusão da Sprint 01

Parta da main atualizada após o merge da Sprint 01.
Implemente origem dos blocos, aplicação transacional, regeneração seletiva, frontend, testes e OpenAPI.
Preserve blocos manuais e ajustados.
Não implemente funcionalidades da Sprint 03 ou motores futuros.
Execute make verificar, abra PR e não faça merge com CI vermelha.
Ao terminar, preencha o registro de conclusão e pare.
```

## Sprint 03

```text
Implemente exclusivamente a Sprint 03 da Geração Determinística.

Leia:
- AGENTS.md
- documentacao/planejamento-geracao-deterministica/CONTEXTO-COMUM.md
- documentacao/planejamento-geracao-deterministica/sprint-03-consolidacao-e-aceite.md
- registros de conclusão das Sprints 01 e 02

Parta da main atualizada.
Não adicione regra nova sem falha comprovada.
Consolide testes, segurança, migrations, Swagger, acessibilidade, responsividade, documentação e fluxo completo.
Remova qualquer workflow ou arquivo temporário.
Execute make verificar e a validação manual.
Abra PR e faça merge apenas com CI verde.
Não inicie Motor de Evidências, Revisões, Lacunas ou IA.
```
