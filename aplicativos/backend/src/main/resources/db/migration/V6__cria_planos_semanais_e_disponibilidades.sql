CREATE TABLE planos_semanais (
    identificador UUID PRIMARY KEY,
    usuario_id UUID NOT NULL REFERENCES usuarios (identificador),
    data_inicial DATE NOT NULL,
    estado VARCHAR(20) NOT NULL,
    criado_em TIMESTAMPTZ NOT NULL,
    atualizado_em TIMESTAMPTZ NOT NULL,
    versao BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_planos_semanais_usuario_data UNIQUE (usuario_id, data_inicial),
    CONSTRAINT ck_planos_semanais_inicio CHECK (EXTRACT(ISODOW FROM data_inicial) = 1),
    CONSTRAINT ck_planos_semanais_estado CHECK (
        estado IN ('RASCUNHO', 'ATIVO', 'ENCERRADO', 'CANCELADO')
    )
);

CREATE INDEX idx_planos_semanais_usuario_data
    ON planos_semanais (usuario_id, data_inicial);

CREATE TABLE disponibilidades_do_dia (
    identificador UUID PRIMARY KEY,
    plano_id UUID NOT NULL REFERENCES planos_semanais (identificador),
    data DATE NOT NULL,
    minutos_disponiveis INTEGER NOT NULL,
    criado_em TIMESTAMPTZ NOT NULL,
    atualizado_em TIMESTAMPTZ NOT NULL,
    versao BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_disponibilidades_plano_data UNIQUE (plano_id, data),
    CONSTRAINT ck_disponibilidades_minutos CHECK (
        minutos_disponiveis BETWEEN 0 AND 1440
    )
);

CREATE INDEX idx_disponibilidades_plano_data
    ON disponibilidades_do_dia (plano_id, data);
