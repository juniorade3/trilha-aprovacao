# Materiais e registros de estudo

Este recorte corresponde a Sprint 6. O estudo pertence ao topico pessoal, nao
ao concurso. Assim, um unico registro e reutilizado quando o mesmo topico esta
mapeado em itens de concursos diferentes.

```mermaid
erDiagram
    USUARIOS ||--o{ MATERIAIS_DE_ESTUDO : possui
    MATERIAIS_DE_ESTUDO ||--o{ COBERTURAS_DE_TOPICOS_POR_MATERIAL : cobre
    TOPICOS_DA_MATERIA ||--o{ COBERTURAS_DE_TOPICOS_POR_MATERIAL : coberto_por
    TOPICOS_DA_MATERIA ||--o{ REGISTROS_DE_ESTUDO : recebe
    MATERIAIS_DE_ESTUDO o|--o{ REGISTROS_DE_ESTUDO : utilizado_em
    REGISTROS_DE_ESTUDO o|--o| REGISTROS_DE_ESTUDO : corrige

    MATERIAIS_DE_ESTUDO {
        uuid identificador PK
        uuid usuario_id FK
        varchar titulo
        varchar tipo
        boolean arquivado
        bigint versao
    }

    COBERTURAS_DE_TOPICOS_POR_MATERIAL {
        uuid identificador PK
        uuid material_id FK
        uuid topico_id FK
        timestamptz criado_em
    }

    REGISTROS_DE_ESTUDO {
        uuid identificador PK
        uuid topico_id FK
        uuid material_id FK
        uuid registro_de_origem_id FK
        timestamptz data_hora
        integer duracao_em_minutos
        varchar situacao
        bigint versao
    }
```

Regras de integridade:

- material, topico e registro sao acessados somente pelo usuario autenticado;
- o par material e topico e unico;
- um material opcional deve cobrir o topico informado no estudo;
- a duracao aceita valores entre 1 e 1.440 minutos;
- somente enderecos HTTP ou HTTPS sao aceitos;
- material arquivado permanece no historico e bloqueia alteracoes;
- corrigir marca o original como `CORRIGIDO` e cria um novo registro `ATIVO`;
- cancelar marca o registro como `CANCELADO`;
- registros de estudo nunca sao excluidos fisicamente;
- remover uma cobertura preserva material, topico e registros anteriores.
