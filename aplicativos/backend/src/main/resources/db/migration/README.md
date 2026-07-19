# Migrations Flyway

As migrations versionadas acompanham as capacidades de dominio:

- V1: usuarios;
- V2: materias e topicos pessoais;
- V3: estrutura de concursos;
- V4: conteudo programatico e mapeamentos;
- V5: materiais, coberturas de topicos e registros de estudo.
- V6: planos semanais e disponibilidade diaria simples.
- V7: blocos de estudo manuais vinculados ao plano semanal.

O esquema e validado pelo Hibernate com `ddl-auto=validate`; nao ha criacao
automatica de tabelas nem dados artificiais de demonstracao.
