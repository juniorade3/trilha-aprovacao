# Materiais e registros de estudo

O estudo pertence ao topico pessoal, nao
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
    REGISTROS_DE_ESTUDO ||--o| EVIDENCIAS_DE_APRENDIZAGEM : possui
    EVIDENCIAS_DE_APRENDIZAGEM ||--o{ OCORRENCIAS_DE_PADRAO_DE_ERRO : registra
    PADROES_DE_ERRO ||--o{ OCORRENCIAS_DE_PADRAO_DE_ERRO : aparece_em
    TOPICOS_DA_MATERIA ||--o{ PADROES_DE_ERRO : agrupa

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
        varchar tipo_de_estudo
        timestamptz data_hora
        integer duracao_em_minutos
        varchar situacao
        bigint versao
    }

    EVIDENCIAS_DE_APRENDIZAGEM {
        uuid identificador PK
        uuid registro_de_estudo_id FK UK
        integer quantidade_de_questoes
        integer quantidade_de_acertos
        integer nivel_de_recordacao
        integer dificuldade_percebida
    }

    PADROES_DE_ERRO {
        uuid identificador PK
        uuid usuario_id FK
        uuid topico_id FK
        varchar descricao_normalizada UK
    }

    OCORRENCIAS_DE_PADRAO_DE_ERRO {
        uuid identificador PK
        uuid evidencia_id FK
        uuid padrao_de_erro_id FK
        integer quantidade_de_ocorrencias
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
- questoes e acertos sao armazenados juntos; erros sao sempre derivados;
- recordacao e dificuldade usam a escala inteira de 1 a 5;
- uma evidencia pertence a um unico registro de estudo;
- padroes sao normalizados e unicos por usuario e topico;
- um padrao repetido aparece em ao menos duas evidencias ativas distintas;
- diagnosticos ignoram registros `CORRIGIDO` e `CANCELADO` e usam uma janela
  civil inclusiva de 30 dias no fuso `America/Sao_Paulo`.
