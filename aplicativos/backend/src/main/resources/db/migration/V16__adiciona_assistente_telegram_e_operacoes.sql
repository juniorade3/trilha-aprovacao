CREATE TABLE vinculos_de_canal (
    identificador UUID PRIMARY KEY,
    usuario_id UUID NOT NULL,
    canal VARCHAR(20) NOT NULL,
    bot BIGINT NOT NULL,
    identificador_externo BIGINT,
    identificador_do_chat BIGINT,
    estado VARCHAR(20) NOT NULL,
    codigo_de_vinculo_hash VARCHAR(128) NOT NULL,
    codigo_expira_em TIMESTAMPTZ NOT NULL,
    codigo_consumido_em TIMESTAMPTZ,
    identificador_do_agente VARCHAR(160),
    identificador_da_sessao VARCHAR(160),
    provisionado_em TIMESTAMPTZ,
    criado_em TIMESTAMPTZ NOT NULL,
    atualizado_em TIMESTAMPTZ NOT NULL,
    revogado_em TIMESTAMPTZ,
    versao BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_vinculos_de_canal_usuario FOREIGN KEY (usuario_id)
        REFERENCES usuarios (identificador),
    CONSTRAINT uk_vinculos_de_canal_codigo UNIQUE (codigo_de_vinculo_hash),
    CONSTRAINT uk_vinculos_de_canal_identificador_usuario UNIQUE (
        identificador, usuario_id
    ),
    CONSTRAINT ck_vinculos_de_canal_canal CHECK (canal IN ('TELEGRAM')),
    CONSTRAINT ck_vinculos_de_canal_estado CHECK (
        estado IN ('PENDENTE', 'ATIVO', 'REVOGADO', 'EXPIRADO')
    ),
    CONSTRAINT ck_vinculos_de_canal_identificadores CHECK (
        (bot IS NULL OR bot > 0)
        AND (identificador_externo IS NULL OR identificador_externo > 0)
        AND (identificador_do_chat IS NULL OR identificador_do_chat <> 0)
        AND (identificador_externo IS NULL OR identificador_do_chat IS NULL
            OR identificador_externo = identificador_do_chat)
    ),
    CONSTRAINT ck_vinculos_de_canal_codigo_expiracao CHECK (
        codigo_expira_em > criado_em
    ),
    CONSTRAINT ck_vinculos_de_canal_codigo_consumido CHECK (
        codigo_consumido_em IS NULL
        OR (codigo_consumido_em >= criado_em
            AND codigo_consumido_em <= codigo_expira_em)
    ),
    CONSTRAINT ck_vinculos_de_canal_atualizacao CHECK (
        atualizado_em >= criado_em
    ),
    CONSTRAINT ck_vinculos_de_canal_revogacao CHECK (
        revogado_em IS NULL OR revogado_em >= criado_em
    ),
    CONSTRAINT ck_vinculos_de_canal_estado_coerente CHECK (
        (estado <> 'PENDENTE'
            OR (codigo_consumido_em IS NULL AND revogado_em IS NULL))
        AND
        (estado <> 'ATIVO'
            OR (bot IS NOT NULL
                AND identificador_externo IS NOT NULL
                AND identificador_do_chat IS NOT NULL
                AND codigo_consumido_em IS NOT NULL
                AND revogado_em IS NULL))
        AND
        (estado <> 'REVOGADO' OR revogado_em IS NOT NULL)
        AND
        (estado <> 'EXPIRADO'
            OR (codigo_consumido_em IS NULL AND revogado_em IS NULL))
    ),
    CONSTRAINT ck_vinculos_de_canal_provisionamento CHECK (
        provisionado_em IS NULL OR provisionado_em >= criado_em
    )
);

CREATE UNIQUE INDEX uk_vinculos_de_canal_usuario_ativo_ou_pendente
    ON vinculos_de_canal (usuario_id, canal)
    WHERE estado IN ('PENDENTE', 'ATIVO');

CREATE UNIQUE INDEX uk_vinculos_de_canal_externo_ativo
    ON vinculos_de_canal (canal, bot, identificador_externo)
    WHERE estado = 'ATIVO';

CREATE UNIQUE INDEX uk_vinculos_de_canal_agente_ativo
    ON vinculos_de_canal (identificador_do_agente)
    WHERE estado = 'ATIVO' AND identificador_do_agente IS NOT NULL;

CREATE INDEX idx_vinculos_de_canal_usuario_estado
    ON vinculos_de_canal (usuario_id, estado);

CREATE INDEX idx_vinculos_de_canal_codigo_expiracao
    ON vinculos_de_canal (codigo_expira_em)
    WHERE estado = 'PENDENTE';

CREATE TABLE credenciais_de_integracao (
    identificador UUID PRIMARY KEY,
    vinculo_id UUID NOT NULL,
    token_hash VARCHAR(128) NOT NULL,
    prefixo VARCHAR(24) NOT NULL,
    escopos TEXT NOT NULL,
    expira_em TIMESTAMPTZ NOT NULL,
    ultimo_uso_em TIMESTAMPTZ,
    revogado_em TIMESTAMPTZ,
    criado_em TIMESTAMPTZ NOT NULL,
    versao BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_credenciais_de_integracao_vinculo FOREIGN KEY (vinculo_id)
        REFERENCES vinculos_de_canal (identificador),
    CONSTRAINT uk_credenciais_de_integracao_token UNIQUE (token_hash),
    CONSTRAINT uk_credenciais_de_integracao_prefixo UNIQUE (prefixo),
    CONSTRAINT ck_credenciais_de_integracao_token CHECK (
        btrim(token_hash) <> '' AND btrim(prefixo) <> ''
    ),
    CONSTRAINT ck_credenciais_de_integracao_escopos CHECK (
        btrim(escopos) <> ''
    ),
    CONSTRAINT ck_credenciais_de_integracao_expiracao CHECK (
        expira_em > criado_em
    ),
    CONSTRAINT ck_credenciais_de_integracao_ultimo_uso CHECK (
        ultimo_uso_em IS NULL OR ultimo_uso_em >= criado_em
    ),
    CONSTRAINT ck_credenciais_de_integracao_revogacao CHECK (
        revogado_em IS NULL OR revogado_em >= criado_em
    )
);

CREATE UNIQUE INDEX uk_credenciais_de_integracao_ativa_por_vinculo
    ON credenciais_de_integracao (vinculo_id)
    WHERE revogado_em IS NULL;

CREATE INDEX idx_credenciais_de_integracao_vinculo_expiracao
    ON credenciais_de_integracao (vinculo_id, expira_em DESC);

CREATE TABLE operacoes_assistidas (
    identificador UUID PRIMARY KEY,
    usuario_id UUID NOT NULL,
    vinculo_id UUID,
    tipo VARCHAR(80) NOT NULL,
    estado VARCHAR(30) NOT NULL,
    resumo VARCHAR(500) NOT NULL,
    versao_do_contrato VARCHAR(20) NOT NULL DEFAULT '1',
    proposta_canonica JSONB NOT NULL,
    assinatura VARCHAR(128) NOT NULL,
    versoes_consultadas JSONB NOT NULL,
    chave_de_idempotencia VARCHAR(160) NOT NULL,
    hash_da_requisicao VARCHAR(128) NOT NULL,
    nivel_de_confirmacao VARCHAR(20) NOT NULL DEFAULT 'COMUM',
    etapa_da_confirmacao INTEGER NOT NULL DEFAULT 0,
    codigo_de_confirmacao_hash VARCHAR(128),
    codigo_de_confirmacao_prefixo VARCHAR(24),
    nonce_da_confirmacao_hash VARCHAR(128),
    confirmacao_expira_em TIMESTAMPTZ,
    metodo_da_confirmacao VARCHAR(20),
    bot_da_confirmacao BIGINT,
    identificador_externo_da_confirmacao BIGINT,
    identificador_do_chat_da_confirmacao BIGINT,
    identificador_da_sessao_da_confirmacao VARCHAR(160),
    identificador_do_update_da_confirmacao VARCHAR(160),
    expira_em TIMESTAMPTZ NOT NULL,
    confirmada_em TIMESTAMPTZ,
    aplicada_em TIMESTAMPTZ,
    cancelada_em TIMESTAMPTZ,
    falha VARCHAR(2000),
    resultado JSONB,
    criado_em TIMESTAMPTZ NOT NULL,
    atualizado_em TIMESTAMPTZ NOT NULL,
    versao BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_operacoes_assistidas_usuario FOREIGN KEY (usuario_id)
        REFERENCES usuarios (identificador),
    CONSTRAINT fk_operacoes_assistidas_vinculo_usuario FOREIGN KEY (
        vinculo_id, usuario_id
    ) REFERENCES vinculos_de_canal (identificador, usuario_id),
    CONSTRAINT uk_operacoes_assistidas_identificador_usuario UNIQUE (
        identificador, usuario_id
    ),
    CONSTRAINT uk_operacoes_assistidas_idempotencia UNIQUE (
        usuario_id, chave_de_idempotencia
    ),
    CONSTRAINT ck_operacoes_assistidas_tipo CHECK (btrim(tipo) <> ''),
    CONSTRAINT ck_operacoes_assistidas_estado CHECK (
        estado IN (
            'PREPARADA', 'AGUARDANDO_CONFIRMACAO', 'CONFIRMADA', 'APLICADA',
            'CANCELADA', 'EXPIRADA', 'FALHOU'
        )
    ),
    CONSTRAINT ck_operacoes_assistidas_contrato CHECK (
        btrim(versao_do_contrato) <> ''
    ),
    CONSTRAINT ck_operacoes_assistidas_json CHECK (
        jsonb_typeof(proposta_canonica) = 'object'
        AND jsonb_typeof(versoes_consultadas) = 'object'
        AND (resultado IS NULL OR jsonb_typeof(resultado) = 'object')
    ),
    CONSTRAINT ck_operacoes_assistidas_hashes CHECK (
        btrim(assinatura) <> ''
        AND btrim(chave_de_idempotencia) <> ''
        AND btrim(hash_da_requisicao) <> ''
    ),
    CONSTRAINT ck_operacoes_assistidas_nivel_confirmacao CHECK (
        nivel_de_confirmacao IN ('COMUM', 'DETALHADA', 'REFORCADA')
    ),
    CONSTRAINT ck_operacoes_assistidas_etapa_confirmacao CHECK (
        etapa_da_confirmacao BETWEEN 0 AND 2
    ),
    CONSTRAINT ck_operacoes_assistidas_metodo_confirmacao CHECK (
        metodo_da_confirmacao IS NULL
        OR metodo_da_confirmacao IN ('BOTAO', 'TEXTO', 'VOZ', 'WEB')
    ),
    CONSTRAINT ck_operacoes_assistidas_contexto_confirmacao CHECK (
        (bot_da_confirmacao IS NULL OR bot_da_confirmacao > 0)
        AND (identificador_externo_da_confirmacao IS NULL
            OR identificador_externo_da_confirmacao > 0)
        AND (identificador_do_chat_da_confirmacao IS NULL
            OR identificador_do_chat_da_confirmacao <> 0)
    ),
    CONSTRAINT ck_operacoes_assistidas_codigo_confirmacao CHECK (
        (codigo_de_confirmacao_hash IS NULL
            AND codigo_de_confirmacao_prefixo IS NULL
            AND nonce_da_confirmacao_hash IS NULL
            AND confirmacao_expira_em IS NULL)
        OR
        (codigo_de_confirmacao_hash IS NOT NULL
            AND codigo_de_confirmacao_prefixo IS NOT NULL
            AND nonce_da_confirmacao_hash IS NOT NULL
            AND confirmacao_expira_em IS NOT NULL)
    ),
    CONSTRAINT ck_operacoes_assistidas_expiracao CHECK (
        expira_em > criado_em
        AND (confirmacao_expira_em IS NULL
            OR (confirmacao_expira_em > criado_em
                AND confirmacao_expira_em <= expira_em))
    ),
    CONSTRAINT ck_operacoes_assistidas_instantes CHECK (
        atualizado_em >= criado_em
        AND (confirmada_em IS NULL OR confirmada_em >= criado_em)
        AND (aplicada_em IS NULL OR aplicada_em >= criado_em)
        AND (cancelada_em IS NULL OR cancelada_em >= criado_em)
    ),
    CONSTRAINT ck_operacoes_assistidas_estado_coerente CHECK (
        (estado <> 'AGUARDANDO_CONFIRMACAO'
            OR codigo_de_confirmacao_hash IS NOT NULL)
        AND (estado NOT IN ('CONFIRMADA', 'APLICADA')
            OR confirmada_em IS NOT NULL)
        AND (estado <> 'APLICADA' OR aplicada_em IS NOT NULL)
        AND (estado <> 'CANCELADA' OR cancelada_em IS NOT NULL)
        AND (estado <> 'FALHOU'
            OR (falha IS NOT NULL AND btrim(falha) <> ''))
        AND NOT (aplicada_em IS NOT NULL AND cancelada_em IS NOT NULL)
    )
);

CREATE INDEX idx_operacoes_assistidas_usuario_criacao
    ON operacoes_assistidas (usuario_id, criado_em DESC);

CREATE INDEX idx_operacoes_assistidas_usuario_estado
    ON operacoes_assistidas (usuario_id, estado, criado_em DESC);

CREATE INDEX idx_operacoes_assistidas_vinculo_estado
    ON operacoes_assistidas (vinculo_id, estado)
    WHERE vinculo_id IS NOT NULL;

CREATE INDEX idx_operacoes_assistidas_expiracao
    ON operacoes_assistidas (expira_em)
    WHERE estado IN ('PREPARADA', 'AGUARDANDO_CONFIRMACAO', 'CONFIRMADA');

CREATE UNIQUE INDEX uk_operacoes_assistidas_update_confirmacao
    ON operacoes_assistidas (vinculo_id, identificador_do_update_da_confirmacao)
    WHERE identificador_do_update_da_confirmacao IS NOT NULL;

CREATE TABLE eventos_de_auditoria_da_automacao (
    identificador UUID PRIMARY KEY,
    usuario_id UUID NOT NULL,
    vinculo_id UUID,
    operacao_assistida_id UUID,
    ator VARCHAR(30) NOT NULL,
    ferramenta VARCHAR(100),
    acao VARCHAR(100) NOT NULL,
    hash_da_entrada VARCHAR(128),
    hash_da_saida VARCHAR(128),
    fonte VARCHAR(80) NOT NULL,
    resultado VARCHAR(40) NOT NULL,
    correlacao UUID NOT NULL,
    identificador_do_evento_externo VARCHAR(160),
    metadados JSONB NOT NULL DEFAULT '{}'::jsonb,
    ocorrido_em TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_eventos_de_auditoria_usuario FOREIGN KEY (usuario_id)
        REFERENCES usuarios (identificador),
    CONSTRAINT fk_eventos_de_auditoria_vinculo_usuario FOREIGN KEY (
        vinculo_id, usuario_id
    ) REFERENCES vinculos_de_canal (identificador, usuario_id),
    CONSTRAINT fk_eventos_de_auditoria_operacao_usuario FOREIGN KEY (
        operacao_assistida_id, usuario_id
    ) REFERENCES operacoes_assistidas (identificador, usuario_id),
    CONSTRAINT ck_eventos_de_auditoria_ator CHECK (
        ator IN ('IA_TELEGRAM', 'GATEWAY_TELEGRAM', 'USUARIO_WEB', 'SISTEMA')
    ),
    CONSTRAINT ck_eventos_de_auditoria_textos CHECK (
        (ferramenta IS NULL OR btrim(ferramenta) <> '')
        AND btrim(acao) <> ''
        AND btrim(fonte) <> ''
        AND btrim(resultado) <> ''
    ),
    CONSTRAINT ck_eventos_de_auditoria_metadados CHECK (
        jsonb_typeof(metadados) = 'object'
    )
);

CREATE INDEX idx_eventos_de_auditoria_usuario_ocorrido
    ON eventos_de_auditoria_da_automacao (usuario_id, ocorrido_em DESC);

CREATE INDEX idx_eventos_de_auditoria_operacao_ocorrido
    ON eventos_de_auditoria_da_automacao (operacao_assistida_id, ocorrido_em)
    WHERE operacao_assistida_id IS NOT NULL;

CREATE INDEX idx_eventos_de_auditoria_correlacao
    ON eventos_de_auditoria_da_automacao (correlacao, ocorrido_em);

CREATE INDEX idx_eventos_de_auditoria_evento_externo
    ON eventos_de_auditoria_da_automacao (identificador_do_evento_externo)
    WHERE identificador_do_evento_externo IS NOT NULL;

CREATE FUNCTION impedir_alteracao_de_evento_de_auditoria()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION
        'eventos_de_auditoria_da_automacao sao append-only';
END;
$$;

CREATE TRIGGER trg_eventos_de_auditoria_append_only
    BEFORE UPDATE OR DELETE ON eventos_de_auditoria_da_automacao
    FOR EACH ROW
    EXECUTE FUNCTION impedir_alteracao_de_evento_de_auditoria();

COMMENT ON COLUMN vinculos_de_canal.codigo_de_vinculo_hash IS
    'Somente o hash do codigo de uso unico e persistido.';

COMMENT ON COLUMN credenciais_de_integracao.token_hash IS
    'Somente o hash da credencial MCP e persistido.';

COMMENT ON TABLE eventos_de_auditoria_da_automacao IS
    'Registro append-only; atualizacoes e exclusoes sao bloqueadas por trigger.';
