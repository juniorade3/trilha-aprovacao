CREATE TABLE prioridades_de_materias_no_plano (
    identificador UUID PRIMARY KEY,
    plano_id UUID NOT NULL REFERENCES planos_semanais (identificador) ON DELETE CASCADE,
    materia_id UUID NOT NULL REFERENCES materias (identificador),
    prioridade VARCHAR(20) NOT NULL,
    criado_em TIMESTAMPTZ NOT NULL,
    atualizado_em TIMESTAMPTZ NOT NULL,
    versao BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_prioridades_plano_materia UNIQUE (plano_id, materia_id),
    CONSTRAINT ck_prioridades_valor CHECK (
        prioridade IN ('ALTA', 'NORMAL', 'BAIXA', 'NAO_INCLUIR')
    )
);

CREATE INDEX idx_prioridades_plano
    ON prioridades_de_materias_no_plano (plano_id);
