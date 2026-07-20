ALTER TABLE blocos_de_estudo DROP CONSTRAINT ck_blocos_origem;

ALTER TABLE blocos_de_estudo
    ADD COLUMN justificativa_do_replanejamento VARCHAR(2000),
    ADD CONSTRAINT ck_blocos_origem CHECK (
        origem IN (
            'MANUAL',
            'GERADO_DETERMINISTICAMENTE',
            'GERADO_AJUSTADO_MANUALMENTE',
            'REPLANEJADO',
            'REPLANEJADO_AJUSTADO_MANUALMENTE'
        )
    );

CREATE TABLE blocos_originais_dos_planos (
    identificador UUID PRIMARY KEY,
    plano_id UUID NOT NULL REFERENCES planos_semanais (identificador),
    bloco_id UUID NOT NULL REFERENCES blocos_de_estudo (identificador),
    materia_id UUID REFERENCES materias (identificador),
    topico_id UUID REFERENCES topicos_da_materia (identificador),
    titulo VARCHAR(200) NOT NULL,
    tipo_de_atividade VARCHAR(30) NOT NULL,
    data DATE NOT NULL,
    duracao_prevista_em_minutos INTEGER NOT NULL,
    ordem INTEGER NOT NULL,
    horario_previsto TIME,
    observacao VARCHAR(2000),
    origem VARCHAR(40) NOT NULL,
    justificativa_da_geracao VARCHAR(2000),
    capturado_em TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_blocos_originais_bloco UNIQUE (bloco_id),
    CONSTRAINT ck_blocos_originais_duracao CHECK (
        duracao_prevista_em_minutos BETWEEN 1 AND 1440
    ),
    CONSTRAINT ck_blocos_originais_ordem CHECK (ordem > 0)
);

CREATE INDEX idx_blocos_originais_plano_data
    ON blocos_originais_dos_planos (plano_id, data, ordem);

CREATE TABLE replanejamentos (
    identificador UUID PRIMARY KEY,
    plano_id UUID NOT NULL REFERENCES planos_semanais (identificador),
    data_de_referencia DATE NOT NULL,
    aplicado_em TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_replanejamentos_plano_aplicacao
    ON replanejamentos (plano_id, aplicado_em);

CREATE TABLE itens_de_replanejamento (
    identificador UUID PRIMARY KEY,
    replanejamento_id UUID NOT NULL REFERENCES replanejamentos (identificador),
    bloco_original_id UUID NOT NULL REFERENCES blocos_de_estudo (identificador),
    decisao VARCHAR(30) NOT NULL,
    motivo VARCHAR(40) NOT NULL,
    minutos_previstos INTEGER NOT NULL,
    minutos_executados INTEGER NOT NULL,
    minutos_pendentes INTEGER NOT NULL,
    quantidade_de_reagendamentos_anterior INTEGER NOT NULL,
    limite_confirmado BOOLEAN NOT NULL,
    justificativa VARCHAR(2000) NOT NULL,
    CONSTRAINT uk_itens_replanejamento_bloco UNIQUE (bloco_original_id),
    CONSTRAINT ck_itens_replanejamento_decisao CHECK (
        decisao IN ('ADIAR', 'DIVIDIR')
    ),
    CONSTRAINT ck_itens_replanejamento_minutos CHECK (
        minutos_previstos > 0
        AND minutos_executados >= 0
        AND minutos_pendentes > 0
    ),
    CONSTRAINT ck_itens_replanejamento_reagendamentos CHECK (
        quantidade_de_reagendamentos_anterior >= 0
    )
);

CREATE INDEX idx_itens_replanejamento_replanejamento
    ON itens_de_replanejamento (replanejamento_id);

CREATE TABLE fragmentos_de_replanejamento (
    identificador UUID PRIMARY KEY,
    item_de_replanejamento_id UUID NOT NULL
        REFERENCES itens_de_replanejamento (identificador),
    bloco_criado_id UUID NOT NULL REFERENCES blocos_de_estudo (identificador),
    sequencia INTEGER NOT NULL,
    data DATE NOT NULL,
    duracao_em_minutos INTEGER NOT NULL,
    CONSTRAINT uk_fragmentos_replanejamento_bloco UNIQUE (bloco_criado_id),
    CONSTRAINT uk_fragmentos_replanejamento_sequencia UNIQUE (
        item_de_replanejamento_id, sequencia
    ),
    CONSTRAINT ck_fragmentos_replanejamento_sequencia CHECK (sequencia > 0),
    CONSTRAINT ck_fragmentos_replanejamento_duracao CHECK (
        duracao_em_minutos > 0
    )
);

CREATE INDEX idx_fragmentos_replanejamento_item
    ON fragmentos_de_replanejamento (item_de_replanejamento_id);

-- Para planos ativados antes da V13, este e o melhor snapshot reconstruivel:
-- ele representa o estado encontrado na migracao, nao alteracoes historicas anteriores.
INSERT INTO blocos_originais_dos_planos (
    identificador, plano_id, bloco_id, materia_id, topico_id, titulo,
    tipo_de_atividade, data, duracao_prevista_em_minutos, ordem,
    horario_previsto, observacao, origem, justificativa_da_geracao, capturado_em
)
SELECT gen_random_uuid(), b.plano_id, b.identificador, b.materia_id, b.topico_id,
       b.titulo, b.tipo_de_atividade, b.data, b.duracao_prevista_em_minutos,
       b.ordem, b.horario_previsto, b.observacao, b.origem,
       b.justificativa_da_geracao, CURRENT_TIMESTAMP
FROM blocos_de_estudo b
JOIN planos_semanais p ON p.identificador = b.plano_id
WHERE p.estado IN ('ATIVO', 'ENCERRADO', 'CANCELADO')
ON CONFLICT (bloco_id) DO NOTHING;
