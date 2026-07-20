ALTER TABLE blocos_de_estudo
    ADD COLUMN origem VARCHAR(40) NOT NULL DEFAULT 'MANUAL',
    ADD COLUMN justificativa_da_geracao VARCHAR(2000);

ALTER TABLE blocos_de_estudo
    ADD CONSTRAINT ck_blocos_origem CHECK (
        origem IN (
            'MANUAL',
            'GERADO_DETERMINISTICAMENTE',
            'GERADO_AJUSTADO_MANUALMENTE'
        )
    );

CREATE INDEX idx_blocos_plano_origem
    ON blocos_de_estudo (plano_id, origem);
