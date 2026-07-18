# Diagrama ER do catalogo pessoal

Este recorte descreve as tabelas implementadas ate a Sprint 3. O usuario e
obtido exclusivamente pela sessao autenticada; os identificadores de usuario
nao fazem parte dos contratos publicos de materias e topicos.

```mermaid
erDiagram
    USUARIOS ||--o{ MATERIAS : possui
    MATERIAS ||--o{ TOPICOS_DA_MATERIA : organiza
    TOPICOS_DA_MATERIA o|--o{ TOPICOS_DA_MATERIA : agrupa

    USUARIOS {
        uuid identificador PK
        varchar nome
        varchar email UK
        varchar senha_hash
        varchar situacao
        timestamptz criado_em
        timestamptz atualizado_em
    }

    MATERIAS {
        uuid identificador PK
        uuid usuario_id FK
        varchar nome
        varchar nome_normalizado
        varchar descricao
        varchar cor
        boolean arquivada
        timestamptz criado_em
        timestamptz atualizado_em
        bigint versao
    }

    TOPICOS_DA_MATERIA {
        uuid identificador PK
        uuid materia_id FK
        uuid topico_pai_id FK
        varchar nome
        varchar nome_normalizado
        varchar descricao
        integer ordem
        boolean arquivado
        timestamptz criado_em
        timestamptz atualizado_em
        bigint versao
    }
```

Restricoes relevantes:

- materia tem nome normalizado unico por usuario;
- topico tem nome normalizado unico entre irmaos, inclusive no nivel raiz;
- topico-pai pertence a mesma materia por regra do caso de uso;
- ciclos sao rejeitados antes da persistencia;
- materias com topicos e topicos com filhos nao sao excluidos fisicamente.
