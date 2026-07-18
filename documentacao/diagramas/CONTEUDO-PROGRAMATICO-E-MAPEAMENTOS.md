# Conteudo programatico e mapeamentos

Este recorte corresponde a Sprint 5. ItemDoEdital preserva o texto oficial no
contexto de um edital e de uma materia da prova. MapeamentoDeItemDoEdital liga
esse texto a um topico pessoal sem copiar ou assumir a propriedade do topico.

```mermaid
erDiagram
    EDITAIS ||--o{ ITENS_DO_EDITAL : descreve
    MATERIAS_DA_PROVA ||--o{ ITENS_DO_EDITAL : organiza
    ITENS_DO_EDITAL o|--o{ ITENS_DO_EDITAL : possui_filhos
    ITENS_DO_EDITAL ||--o{ MAPEAMENTOS_DE_ITENS_DO_EDITAL : mapeia
    TOPICOS_DA_MATERIA ||--o{ MAPEAMENTOS_DE_ITENS_DO_EDITAL : reutilizado_em

    ITENS_DO_EDITAL {
        uuid identificador PK
        uuid edital_id FK
        uuid materia_da_prova_id FK
        text descricao_original
        uuid item_pai_id FK
        integer ordem
        bigint versao
    }

    MAPEAMENTOS_DE_ITENS_DO_EDITAL {
        uuid identificador PK
        uuid item_do_edital_id FK
        uuid topico_da_materia_id FK
        boolean confirmado
        timestamptz criado_em
    }
```

Regras de integridade:

- a descricao oficial e obrigatoria e armazenada sem normalizacao;
- o item-pai pertence a mesma materia da prova e ao mesmo edital;
- um item nao pode ser pai de si nem ser movido para um descendente;
- edital e materia da prova pertencem ao mesmo concurso;
- o topico mapeado pertence a mesma Materia reutilizada por MateriaDaProva;
- o par item e topico e unico, e todo mapeamento manual nasce confirmado;
- remover o mapeamento nao remove ItemDoEdital nem TopicoDaMateria;
- todas as consultas chegam ao usuario autenticado pela hierarquia;
- concursos arquivados continuam consultaveis, mas seus itens e mapeamentos nao
  podem ser alterados.
