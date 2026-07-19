CREATE TABLE blocos_de_estudo (
    identificador UUID PRIMARY KEY,
    plano_id UUID NOT NULL REFERENCES planos_semanais (identificador),
    materia_id UUID REFERENCES materias (identificador),
    topico_id UUID REFERENCES topicos_da_materia (identificador),
    titulo VARCHAR(200) NOT NULL,
    tipo_de_atividade VARCHAR(30) NOT NULL,
    data DATE NOT NULL,
    duracao_prevista_em_minutos INTEGER NOT NULL,
    ordem INTEGER NOT NULL,
    horario_previsto TIME,
    observacao VARCHAR(2000),
    estado VARCHAR(30) NOT NULL,
    criado_em TIMESTAMPTZ NOT NULL,
    atualizado_em TIMESTAMPTZ NOT NULL,
    versao BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_blocos_duracao CHECK (
        duracao_prevista_em_minutos BETWEEN 1 AND 1440
    ),
    CONSTRAINT ck_blocos_ordem CHECK (ordem > 0),
    CONSTRAINT ck_blocos_topico_exige_materia CHECK (
        topico_id IS NULL OR materia_id IS NOT NULL
    ),
    CONSTRAINT ck_blocos_tipo CHECK (
        tipo_de_atividade IN (
            'TEORIA', 'QUESTOES', 'REVISAO', 'CADERNO_DE_ERROS',
            'SIMULADO', 'DISCURSIVA', 'OUTRA'
        )
    ),
    CONSTRAINT ck_blocos_estado CHECK (
        estado IN (
            'PLANEJADO', 'EM_ANDAMENTO', 'CONCLUIDO',
            'PARCIALMENTE_CONCLUIDO', 'CANCELADO'
        )
    )
);

CREATE INDEX idx_blocos_plano_data_ordem
    ON blocos_de_estudo (plano_id, data, ordem);

CREATE INDEX idx_blocos_materia ON blocos_de_estudo (materia_id);
CREATE INDEX idx_blocos_topico ON blocos_de_estudo (topico_id);
