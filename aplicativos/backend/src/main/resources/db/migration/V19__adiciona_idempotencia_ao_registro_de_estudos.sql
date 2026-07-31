CREATE TABLE requisicoes_idempotentes_de_estudo (
    identificador UUID PRIMARY KEY,
    usuario_id UUID NOT NULL,
    chave_de_idempotencia VARCHAR(160) NOT NULL,
    hash_da_requisicao VARCHAR(64) NOT NULL,
    registro_de_estudo_id UUID NOT NULL,
    criado_em TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_requisicoes_idempotentes_estudo_usuario
        FOREIGN KEY (usuario_id) REFERENCES usuarios (identificador),
    CONSTRAINT fk_requisicoes_idempotentes_estudo_registro
        FOREIGN KEY (registro_de_estudo_id)
        REFERENCES registros_de_estudo (identificador),
    CONSTRAINT uk_requisicoes_idempotentes_estudo_usuario_chave
        UNIQUE (usuario_id, chave_de_idempotencia),
    CONSTRAINT ck_requisicoes_idempotentes_estudo_chave CHECK (
        chave_de_idempotencia ~
            '^[A-Za-z0-9][A-Za-z0-9._:-]{0,159}$'
    ),
    CONSTRAINT ck_requisicoes_idempotentes_estudo_hash CHECK (
        hash_da_requisicao ~ '^[0-9a-f]{64}$'
    )
);

COMMENT ON TABLE requisicoes_idempotentes_de_estudo IS
    'Recibo tecnico do POST /api/v1/estudos; permite repetir com seguranca a mesma operacao no escopo do usuario.';

COMMENT ON COLUMN requisicoes_idempotentes_de_estudo.hash_da_requisicao IS
    'SHA-256 do comando semantico canonico; nao contem o corpo integral.';
