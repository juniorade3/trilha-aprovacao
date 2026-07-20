CREATE TABLE execucoes_de_bloco (
    identificador UUID PRIMARY KEY,
    usuario_id UUID NOT NULL REFERENCES usuarios (identificador),
    bloco_id UUID NOT NULL REFERENCES blocos_de_estudo (identificador),
    iniciada_em TIMESTAMPTZ NOT NULL,
    encerrada_em TIMESTAMPTZ,
    duracao_executada_em_minutos INTEGER,
    resultado VARCHAR(40),
    observacao VARCHAR(2000),
    criado_em TIMESTAMPTZ NOT NULL,
    atualizado_em TIMESTAMPTZ NOT NULL,
    versao BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_execucoes_bloco UNIQUE (bloco_id),
    CONSTRAINT ck_execucoes_duracao CHECK (
        duracao_executada_em_minutos IS NULL
        OR duracao_executada_em_minutos BETWEEN 1 AND 1440
    ),
    CONSTRAINT ck_execucoes_resultado CHECK (
        resultado IS NULL
        OR resultado IN ('CONCLUIDO', 'PARCIALMENTE_CONCLUIDO')
    ),
    CONSTRAINT ck_execucoes_encerramento CHECK (
        (encerrada_em IS NULL
            AND duracao_executada_em_minutos IS NULL
            AND resultado IS NULL
            AND observacao IS NULL)
        OR
        (encerrada_em IS NOT NULL
            AND duracao_executada_em_minutos IS NOT NULL
            AND resultado IS NOT NULL
            AND encerrada_em >= iniciada_em)
    )
);

CREATE INDEX idx_execucoes_usuario_inicio
    ON execucoes_de_bloco (usuario_id, iniciada_em DESC);

CREATE UNIQUE INDEX uk_execucoes_usuario_em_andamento
    ON execucoes_de_bloco (usuario_id)
    WHERE encerrada_em IS NULL;
