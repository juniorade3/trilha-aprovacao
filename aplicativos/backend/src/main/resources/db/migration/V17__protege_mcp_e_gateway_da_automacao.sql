ALTER TABLE vinculos_de_canal
    ADD CONSTRAINT ck_vinculos_de_canal_provisionamento_coerente CHECK (
        (identificador_do_agente IS NULL
            AND identificador_da_sessao IS NULL
            AND provisionado_em IS NULL)
        OR
        (identificador_do_agente IS NOT NULL
            AND btrim(identificador_do_agente) <> ''
            AND identificador_da_sessao IS NOT NULL
            AND btrim(identificador_da_sessao) <> ''
            AND provisionado_em IS NOT NULL)
    );

CREATE UNIQUE INDEX uk_vinculos_de_canal_sessao_ativa
    ON vinculos_de_canal (identificador_da_sessao)
    WHERE estado = 'ATIVO' AND identificador_da_sessao IS NOT NULL;

CREATE TABLE requisicoes_confiaveis_da_automacao (
    identificador UUID PRIMARY KEY,
    identificador_da_chave VARCHAR(80) NOT NULL,
    nonce_hash VARCHAR(64) NOT NULL,
    chave_de_idempotencia VARCHAR(160) NOT NULL,
    metodo VARCHAR(10) NOT NULL,
    caminho VARCHAR(500) NOT NULL,
    hash_do_corpo VARCHAR(64) NOT NULL,
    instante_informado TIMESTAMPTZ NOT NULL,
    recebido_em TIMESTAMPTZ NOT NULL,
    expira_em TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_requisicoes_confiaveis_nonce UNIQUE (
        identificador_da_chave, nonce_hash
    ),
    CONSTRAINT ck_requisicoes_confiaveis_textos CHECK (
        btrim(identificador_da_chave) <> ''
        AND btrim(chave_de_idempotencia) <> ''
        AND btrim(metodo) <> ''
        AND btrim(caminho) <> ''
    ),
    CONSTRAINT ck_requisicoes_confiaveis_hashes CHECK (
        nonce_hash ~ '^[0-9a-f]{64}$'
        AND hash_do_corpo ~ '^[0-9a-f]{64}$'
    ),
    CONSTRAINT ck_requisicoes_confiaveis_expiracao CHECK (
        expira_em > recebido_em
    )
);

CREATE INDEX idx_requisicoes_confiaveis_limite
    ON requisicoes_confiaveis_da_automacao (
        identificador_da_chave, recebido_em DESC
    );

CREATE INDEX idx_requisicoes_confiaveis_idempotencia
    ON requisicoes_confiaveis_da_automacao (
        identificador_da_chave, caminho, chave_de_idempotencia
    );

CREATE INDEX idx_requisicoes_confiaveis_expiracao
    ON requisicoes_confiaveis_da_automacao (expira_em);

COMMENT ON TABLE requisicoes_confiaveis_da_automacao IS
    'Metadados tecnicos para replay, idempotencia e limite do Gateway; nunca contem token ou corpo integral.';
