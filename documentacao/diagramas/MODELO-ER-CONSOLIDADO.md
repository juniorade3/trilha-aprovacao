# Modelo ER consolidado

O diagrama representa persistencia e vinculos. Progresso e dashboard nao sao
tabelas: ambos sao calculados a partir dos fatos existentes.

```mermaid
erDiagram
    USUARIOS ||--o{ MATERIAS : possui
    USUARIOS ||--o{ CONCURSOS : possui
    USUARIOS ||--o{ MATERIAIS_DE_ESTUDO : possui

    MATERIAS ||--o{ TOPICOS_DA_MATERIA : organiza
    TOPICOS_DA_MATERIA ||--o{ TOPICOS_DA_MATERIA : contem

    CONCURSOS ||--o{ EDITAIS : publica
    CONCURSOS ||--o{ CARGOS_DO_CONCURSO : oferece
    CARGOS_DO_CONCURSO ||--o{ PROVAS : avalia
    PROVAS ||--o{ GRUPOS_DE_CONTEUDO : divide
    GRUPOS_DE_CONTEUDO ||--o{ MATERIAS_DA_PROVA : exige
    MATERIAS ||--o{ MATERIAS_DA_PROVA : reutiliza

    EDITAIS ||--o{ ITENS_DO_EDITAL : contem
    MATERIAS_DA_PROVA ||--o{ ITENS_DO_EDITAL : classifica
    ITENS_DO_EDITAL ||--o{ ITENS_DO_EDITAL : detalha
    ITENS_DO_EDITAL ||--o{ MAPEAMENTOS_DE_ITENS_DO_EDITAL : mapeia
    TOPICOS_DA_MATERIA ||--o{ MAPEAMENTOS_DE_ITENS_DO_EDITAL : identifica

    MATERIAIS_DE_ESTUDO ||--o{ COBERTURAS_DE_TOPICOS_POR_MATERIAL : cobre
    TOPICOS_DA_MATERIA ||--o{ COBERTURAS_DE_TOPICOS_POR_MATERIAL : coberto
    TOPICOS_DA_MATERIA ||--o{ REGISTROS_DE_ESTUDO : estudado
    MATERIAIS_DE_ESTUDO ||--o{ REGISTROS_DE_ESTUDO : utilizado
    REGISTROS_DE_ESTUDO ||--o| REGISTROS_DE_ESTUDO : corrige
```

As chaves parciais do PostgreSQL asseguram no maximo um concurso ativo por
usuario, um edital principal por concurso e um cargo selecionado por concurso.
As demais invariantes de compatibilidade e pertencimento sao verificadas pelos
casos de uso antes da persistencia.
