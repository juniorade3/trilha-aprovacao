CREATE TABLE materias (
    identificador UUID PRIMARY KEY,
    usuario_id UUID NOT NULL,
    nome VARCHAR(120) NOT NULL,
    nome_normalizado VARCHAR(120) NOT NULL,
    descricao VARCHAR(1000),
    cor VARCHAR(7),
    arquivada BOOLEAN NOT NULL DEFAULT FALSE,
    criado_em TIMESTAMP WITH TIME ZONE NOT NULL,
    atualizado_em TIMESTAMP WITH TIME ZONE NOT NULL,
    versao BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT materias_usuario_fk FOREIGN KEY (usuario_id) REFERENCES usuarios (identificador),
    CONSTRAINT materias_nome_unico_por_usuario UNIQUE (usuario_id, nome_normalizado),
    CONSTRAINT materias_cor_valida CHECK (cor IS NULL OR cor ~ '^#[0-9A-Fa-f]{6}$')
);

CREATE INDEX materias_usuario_indice ON materias (usuario_id);
CREATE INDEX materias_usuario_arquivada_indice ON materias (usuario_id, arquivada);

CREATE TABLE topicos_da_materia (
    identificador UUID PRIMARY KEY,
    materia_id UUID NOT NULL,
    topico_pai_id UUID,
    nome VARCHAR(160) NOT NULL,
    nome_normalizado VARCHAR(160) NOT NULL,
    descricao VARCHAR(1000),
    ordem INTEGER NOT NULL,
    arquivado BOOLEAN NOT NULL DEFAULT FALSE,
    criado_em TIMESTAMP WITH TIME ZONE NOT NULL,
    atualizado_em TIMESTAMP WITH TIME ZONE NOT NULL,
    versao BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT topicos_materia_fk FOREIGN KEY (materia_id) REFERENCES materias (identificador),
    CONSTRAINT topicos_pai_fk FOREIGN KEY (topico_pai_id) REFERENCES topicos_da_materia (identificador),
    CONSTRAINT topicos_ordem_positiva CHECK (ordem > 0),
    CONSTRAINT topicos_nao_sao_pais_de_si CHECK (topico_pai_id IS NULL OR topico_pai_id <> identificador),
    CONSTRAINT topicos_nome_unico_entre_irmaos
        UNIQUE NULLS NOT DISTINCT (materia_id, topico_pai_id, nome_normalizado)
);

CREATE INDEX topicos_materia_indice ON topicos_da_materia (materia_id);
CREATE INDEX topicos_pai_indice ON topicos_da_materia (topico_pai_id);
CREATE INDEX topicos_materia_arquivado_indice ON topicos_da_materia (materia_id, arquivado);
