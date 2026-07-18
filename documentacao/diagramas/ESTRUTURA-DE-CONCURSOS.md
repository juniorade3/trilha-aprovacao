# Diagrama ER da estrutura de concursos

Este recorte corresponde a Sprint 4. Todas as consultas percorrem a hierarquia
ate o concurso e aplicam o identificador do usuario obtido da sessao.

```mermaid
erDiagram
    USUARIOS ||--o{ CONCURSOS : possui
    CONCURSOS ||--o{ EDITAIS : publica
    CONCURSOS ||--o{ CARGOS_DO_CONCURSO : oferece
    CARGOS_DO_CONCURSO ||--o{ PROVAS : possui
    PROVAS ||--o{ GRUPOS_DE_CONTEUDO : organiza
    GRUPOS_DE_CONTEUDO ||--o{ MATERIAS_DA_PROVA : exige
    MATERIAS ||--o{ MATERIAS_DA_PROVA : reutilizada_em

    CONCURSOS {
        uuid identificador PK
        uuid usuario_id FK
        varchar nome
        varchar situacao
        boolean ativo
        date data_prevista_principal
        bigint versao
    }

    EDITAIS {
        uuid identificador PK
        uuid concurso_id FK
        varchar titulo
        boolean principal
        bigint versao
    }

    CARGOS_DO_CONCURSO {
        uuid identificador PK
        uuid concurso_id FK
        varchar nome
        varchar nivel_de_escolaridade
        boolean selecionado
        integer ordem
        bigint versao
    }

    PROVAS {
        uuid identificador PK
        uuid cargo_id FK
        varchar nome
        varchar tipo
        varchar carater
        integer ordem
        bigint versao
    }

    GRUPOS_DE_CONTEUDO {
        uuid identificador PK
        uuid prova_id FK
        varchar nome
        integer ordem
        bigint versao
    }

    MATERIAS_DA_PROVA {
        uuid identificador PK
        uuid grupo_de_conteudo_id FK
        uuid materia_id FK
        integer ordem
        numeric peso
        bigint versao
    }
```

Restricoes relevantes:

- somente um concurso ativo por usuario;
- somente um edital principal por concurso;
- somente um cargo selecionado por concurso;
- cargo, prova e grupo rejeitam nomes duplicados no respectivo pai;
- a mesma materia nao se repete no grupo, mas pode ser reutilizada em outros
  grupos e concursos sem copia;
- todas as entidades usam versao para detectar atualizacoes concorrentes;
- a hierarquia usa chaves estrangeiras sem exclusao em cascata;
- um concurso arquivado permanece consultavel, mas nao aceita mudancas de
  conteudo.
