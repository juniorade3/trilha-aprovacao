# Migrations Flyway

As migrations versionadas acompanham as capacidades de dominio:

- V1: usuarios;
- V2: materias e topicos pessoais;
- V3: estrutura de concursos;
- V4: conteudo programatico e mapeamentos;
- V5: materiais, coberturas de topicos e registros de estudo.
- V6: planos semanais e disponibilidade diaria simples.
- V7: blocos de estudo manuais vinculados ao plano semanal.
- V8: execucoes de blocos com inicio, conclusao e interrupcao rastreaveis.

O esquema e validado pelo Hibernate com `ddl-auto=validate`; nao ha criacao
automatica de tabelas nem dados artificiais de demonstracao.
- V9 vincula execucoes finalizadas a registros de estudo.
- V10 registra reagendamentos de blocos.
- V11 registra prioridades de materias por plano.
- V12 registra origem e justificativa da geracao deterministica.
- V13 registra snapshot original, replanejamentos, transferencias e fragmentos.
