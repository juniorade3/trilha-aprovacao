ALTER TABLE vinculos_de_canal
    DROP CONSTRAINT ck_vinculos_de_canal_identificadores;

ALTER TABLE vinculos_de_canal
    ADD CONSTRAINT ck_vinculos_de_canal_identificadores CHECK (
        bot > 0
        AND (identificador_externo IS NULL OR identificador_externo > 0)
        AND (identificador_do_chat IS NULL OR identificador_do_chat > 0)
    );

ALTER TABLE operacoes_assistidas
    DROP CONSTRAINT ck_operacoes_assistidas_contexto_confirmacao;

ALTER TABLE operacoes_assistidas
    ADD CONSTRAINT ck_operacoes_assistidas_contexto_confirmacao CHECK (
        (bot_da_confirmacao IS NULL OR bot_da_confirmacao > 0)
        AND (identificador_externo_da_confirmacao IS NULL
            OR identificador_externo_da_confirmacao > 0)
        AND (identificador_do_chat_da_confirmacao IS NULL
            OR identificador_do_chat_da_confirmacao > 0)
    );

ALTER TABLE operacoes_assistidas
    ADD COLUMN codigo_de_confirmacao_anterior_hash VARCHAR(128),
    ADD CONSTRAINT ck_operacoes_assistidas_codigo_anterior_hash CHECK (
        codigo_de_confirmacao_anterior_hash IS NULL
        OR codigo_de_confirmacao_anterior_hash ~ '^[0-9a-f]{64}$'
    );

CREATE INDEX idx_operacoes_assistidas_codigo_confirmacao
    ON operacoes_assistidas (
        vinculo_id, codigo_de_confirmacao_hash, criado_em DESC
    )
    WHERE vinculo_id IS NOT NULL
      AND codigo_de_confirmacao_hash IS NOT NULL;

CREATE INDEX idx_operacoes_assistidas_codigo_confirmacao_anterior
    ON operacoes_assistidas (
        vinculo_id, codigo_de_confirmacao_anterior_hash, criado_em DESC
    )
    WHERE vinculo_id IS NOT NULL
      AND codigo_de_confirmacao_anterior_hash IS NOT NULL;

COMMENT ON COLUMN operacoes_assistidas.codigo_de_confirmacao_anterior_hash IS
    'HMAC do codigo da primeira etapa reforcada; nunca armazena codigo puro.';
