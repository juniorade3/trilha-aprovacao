CREATE TABLE usuarios (
    identificador UUID PRIMARY KEY,
    nome VARCHAR(120) NOT NULL,
    email VARCHAR(320) NOT NULL,
    senha_hash VARCHAR(100) NOT NULL,
    situacao VARCHAR(20) NOT NULL,
    criado_em TIMESTAMP WITH TIME ZONE NOT NULL,
    atualizado_em TIMESTAMP WITH TIME ZONE NOT NULL,
    versao BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT usuarios_email_unico UNIQUE (email),
    CONSTRAINT usuarios_situacao_valida CHECK (situacao IN ('ATIVO', 'INATIVO'))
);

CREATE INDEX usuarios_email_indice ON usuarios (email);
