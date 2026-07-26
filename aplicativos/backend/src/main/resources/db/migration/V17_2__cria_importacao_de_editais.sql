ALTER TABLE concursos
    ADD CONSTRAINT uk_concursos_identificador_usuario UNIQUE (
        identificador, usuario_id
    );

CREATE TABLE importacoes_de_edital (
    identificador UUID PRIMARY KEY,
    usuario_id UUID NOT NULL,
    importacao_de_origem_id UUID,
    estado VARCHAR(40) NOT NULL,
    tipo_da_fonte VARCHAR(20) NOT NULL,
    nome_do_arquivo VARCHAR(255) NOT NULL,
    tipo_mime VARCHAR(100) NOT NULL,
    sha256 VARCHAR(64) NOT NULL,
    tamanho_em_bytes BIGINT NOT NULL,
    quantidade_de_paginas INTEGER,
    conteudo_original BYTEA,
    texto_extraido TEXT,
    versao_atual_da_extracao INTEGER NOT NULL DEFAULT 0,
    hash_da_extracao_atual VARCHAR(64),
    chave_do_cargo_selecionado VARCHAR(160),
    modo VARCHAR(40),
    concurso_existente_id UUID,
    politica_de_reutilizacao VARCHAR(40),
    operacao_assistida_id UUID,
    tentativa_da_preparacao INTEGER NOT NULL DEFAULT 1,
    codigo_da_falha VARCHAR(120),
    descricao_da_falha VARCHAR(1000),
    reter_conteudo_ate TIMESTAMPTZ NOT NULL,
    aplicado_em TIMESTAMPTZ,
    criado_em TIMESTAMPTZ NOT NULL,
    atualizado_em TIMESTAMPTZ NOT NULL,
    versao BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_importacoes_de_edital_usuario FOREIGN KEY (usuario_id)
        REFERENCES usuarios (identificador),
    CONSTRAINT fk_importacoes_de_edital_origem FOREIGN KEY (
        importacao_de_origem_id, usuario_id
    ) REFERENCES importacoes_de_edital (identificador, usuario_id),
    CONSTRAINT fk_importacoes_de_edital_concurso FOREIGN KEY (
        concurso_existente_id, usuario_id
    ) REFERENCES concursos (identificador, usuario_id),
    CONSTRAINT fk_importacoes_de_edital_operacao FOREIGN KEY (
        operacao_assistida_id, usuario_id
    ) REFERENCES operacoes_assistidas (identificador, usuario_id),
    CONSTRAINT uk_importacoes_de_edital_identificador_usuario UNIQUE (
        identificador, usuario_id
    ),
    CONSTRAINT ck_importacoes_de_edital_estado CHECK (estado IN (
        'RECEBIDA', 'EXTRAINDO', 'EXTRAIDA', 'AGUARDANDO_SELECAO',
        'AGUARDANDO_CORRECOES', 'VALIDADA', 'AGUARDANDO_CONFIRMACAO',
        'APLICANDO', 'APLICADA', 'FALHOU', 'CANCELADA'
    )),
    CONSTRAINT ck_importacoes_de_edital_tipo_fonte CHECK (
        tipo_da_fonte IN ('TEXTO', 'PDF_TEXTUAL', 'PDF_DIGITALIZADO')
    ),
    CONSTRAINT ck_importacoes_de_edital_mime CHECK (
        tipo_mime IN ('text/plain', 'application/pdf')
    ),
    CONSTRAINT ck_importacoes_de_edital_tipo_mime CHECK (
        (tipo_da_fonte = 'TEXTO' AND tipo_mime = 'text/plain')
        OR (tipo_da_fonte IN ('PDF_TEXTUAL', 'PDF_DIGITALIZADO')
            AND tipo_mime = 'application/pdf')
    ),
    CONSTRAINT ck_importacoes_de_edital_nome CHECK (
        btrim(nome_do_arquivo) <> ''
    ),
    CONSTRAINT ck_importacoes_de_edital_sha256 CHECK (
        sha256 ~ '^[0-9a-f]{64}$'
    ),
    CONSTRAINT ck_importacoes_de_edital_tamanho CHECK (
        tamanho_em_bytes BETWEEN 1 AND 10485760
    ),
    CONSTRAINT ck_importacoes_de_edital_paginas CHECK (
        quantidade_de_paginas IS NULL OR quantidade_de_paginas BETWEEN 1 AND 500
    ),
    CONSTRAINT ck_importacoes_de_edital_versao_extracao CHECK (
        versao_atual_da_extracao >= 0
    ),
    CONSTRAINT ck_importacoes_de_edital_tentativa CHECK (
        tentativa_da_preparacao > 0
    ),
    CONSTRAINT ck_importacoes_de_edital_hash_extracao CHECK (
        hash_da_extracao_atual IS NULL
        OR hash_da_extracao_atual ~ '^[0-9a-f]{64}$'
    ),
    CONSTRAINT ck_importacoes_de_edital_versao_e_hash CHECK (
        (versao_atual_da_extracao = 0
            AND hash_da_extracao_atual IS NULL)
        OR (versao_atual_da_extracao > 0
            AND hash_da_extracao_atual IS NOT NULL)
    ),
    CONSTRAINT ck_importacoes_de_edital_estado_e_extracao CHECK (
        estado IN ('RECEBIDA', 'EXTRAINDO', 'FALHOU')
        OR versao_atual_da_extracao > 0
    ),
    CONSTRAINT ck_importacoes_de_edital_modo CHECK (
        modo IS NULL OR modo IN ('CRIAR_NOVO', 'COMPLEMENTAR_EXISTENTE')
    ),
    CONSTRAINT ck_importacoes_de_edital_politica CHECK (
        politica_de_reutilizacao IS NULL OR politica_de_reutilizacao IN (
            'REUTILIZAR_COMPATIVEIS', 'EXIGIR_DECISAO', 'CRIAR_SEPARADO'
        )
    ),
    CONSTRAINT ck_importacoes_de_edital_destino CHECK (
        (modo = 'COMPLEMENTAR_EXISTENTE'
            AND concurso_existente_id IS NOT NULL)
        OR (modo IS DISTINCT FROM 'COMPLEMENTAR_EXISTENTE'
            AND concurso_existente_id IS NULL)
    ),
    CONSTRAINT ck_importacoes_de_edital_decisoes CHECK (
        chave_do_cargo_selecionado IS NULL
        OR (modo IS NOT NULL AND politica_de_reutilizacao IS NOT NULL)
    ),
    CONSTRAINT ck_importacoes_de_edital_operacao CHECK (
        (estado IN ('AGUARDANDO_CONFIRMACAO', 'APLICANDO', 'APLICADA')
            AND operacao_assistida_id IS NOT NULL)
        OR (estado NOT IN (
                'AGUARDANDO_CONFIRMACAO', 'APLICANDO', 'APLICADA'
            ) AND operacao_assistida_id IS NULL)
    ),
    CONSTRAINT ck_importacoes_de_edital_aplicacao CHECK (
        (estado = 'APLICADA' AND aplicado_em IS NOT NULL)
        OR (estado <> 'APLICADA' AND aplicado_em IS NULL)
    ),
    CONSTRAINT ck_importacoes_de_edital_instantes CHECK (
        atualizado_em >= criado_em
        AND reter_conteudo_ate > criado_em
        AND (aplicado_em IS NULL OR aplicado_em >= criado_em)
    ),
    CONSTRAINT ck_importacoes_de_edital_falha CHECK (
        (estado = 'FALHOU' AND codigo_da_falha IS NOT NULL)
        OR (estado <> 'FALHOU' AND codigo_da_falha IS NULL
            AND descricao_da_falha IS NULL)
    )
);

CREATE INDEX idx_importacoes_de_edital_usuario_estado
    ON importacoes_de_edital (usuario_id, estado, atualizado_em DESC);
CREATE INDEX idx_importacoes_de_edital_usuario_hash
    ON importacoes_de_edital (usuario_id, sha256, criado_em DESC);
CREATE INDEX idx_importacoes_de_edital_retencao
    ON importacoes_de_edital (reter_conteudo_ate)
    WHERE conteudo_original IS NOT NULL OR texto_extraido IS NOT NULL;
CREATE UNIQUE INDEX uk_importacoes_de_edital_lote
    ON importacoes_de_edital (
        usuario_id, sha256, chave_do_cargo_selecionado,
        versao_atual_da_extracao
    )
    WHERE chave_do_cargo_selecionado IS NOT NULL
      AND estado NOT IN ('FALHOU', 'CANCELADA');
CREATE UNIQUE INDEX uk_importacoes_de_edital_recebimento_ativo
    ON importacoes_de_edital (usuario_id, sha256)
    WHERE chave_do_cargo_selecionado IS NULL
      AND estado NOT IN ('FALHOU', 'CANCELADA');

CREATE TABLE versoes_da_extracao_do_edital (
    identificador UUID PRIMARY KEY,
    importacao_id UUID NOT NULL,
    usuario_id UUID NOT NULL,
    numero_da_versao INTEGER NOT NULL,
    versao_do_contrato VARCHAR(20) NOT NULL,
    versao_do_extrator VARCHAR(40) NOT NULL,
    dados_estruturados JSONB NOT NULL,
    problemas JSONB NOT NULL,
    hash_da_extracao VARCHAR(64) NOT NULL,
    criado_em TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_versoes_da_extracao_importacao FOREIGN KEY (
        importacao_id, usuario_id
    ) REFERENCES importacoes_de_edital (identificador, usuario_id),
    CONSTRAINT uk_versoes_da_extracao_numero UNIQUE (
        importacao_id, numero_da_versao
    ),
    CONSTRAINT ck_versoes_da_extracao_numero CHECK (numero_da_versao > 0),
    CONSTRAINT ck_versoes_da_extracao_contrato CHECK (
        versao_do_contrato = '1'
    ),
    CONSTRAINT ck_versoes_da_extracao_extrator CHECK (
        btrim(versao_do_extrator) <> ''
    ),
    CONSTRAINT ck_versoes_da_extracao_json CHECK (
        jsonb_typeof(dados_estruturados) = 'object'
        AND jsonb_typeof(problemas) = 'array'
    ),
    CONSTRAINT ck_versoes_da_extracao_hash CHECK (
        hash_da_extracao ~ '^[0-9a-f]{64}$'
    )
);

CREATE INDEX idx_versoes_da_extracao_importacao
    ON versoes_da_extracao_do_edital (importacao_id, numero_da_versao DESC);

CREATE TABLE relatorios_da_importacao_do_edital (
    identificador UUID PRIMARY KEY,
    importacao_id UUID NOT NULL,
    usuario_id UUID NOT NULL,
    operacao_assistida_id UUID NOT NULL,
    concurso_id UUID NOT NULL,
    dados JSONB NOT NULL,
    criado_em TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_relatorios_da_importacao_importacao FOREIGN KEY (
        importacao_id, usuario_id
    ) REFERENCES importacoes_de_edital (identificador, usuario_id),
    CONSTRAINT fk_relatorios_da_importacao_operacao FOREIGN KEY (
        operacao_assistida_id, usuario_id
    ) REFERENCES operacoes_assistidas (identificador, usuario_id),
    CONSTRAINT fk_relatorios_da_importacao_concurso FOREIGN KEY (
        concurso_id, usuario_id
    ) REFERENCES concursos (identificador, usuario_id),
    CONSTRAINT uk_relatorios_da_importacao_importacao UNIQUE (importacao_id),
    CONSTRAINT ck_relatorios_da_importacao_json CHECK (
        jsonb_typeof(dados) = 'object'
    )
);

CREATE INDEX idx_relatorios_da_importacao_usuario
    ON relatorios_da_importacao_do_edital (usuario_id, criado_em DESC);

CREATE TABLE proveniencias_da_importacao_do_edital (
    identificador UUID PRIMARY KEY,
    importacao_id UUID NOT NULL,
    usuario_id UUID NOT NULL,
    tipo_do_recurso VARCHAR(50) NOT NULL,
    recurso_id UUID NOT NULL,
    campo VARCHAR(100) NOT NULL,
    pagina INTEGER,
    secao VARCHAR(300),
    trecho VARCHAR(1000),
    confianca NUMERIC(5, 4) NOT NULL,
    inferido BOOLEAN NOT NULL DEFAULT FALSE,
    criado_em TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_proveniencias_da_importacao FOREIGN KEY (
        importacao_id, usuario_id
    ) REFERENCES importacoes_de_edital (identificador, usuario_id),
    CONSTRAINT ck_proveniencias_da_importacao_tipo CHECK (
        tipo_do_recurso IN (
            'CONCURSO', 'EDITAL', 'CARGO', 'PROVA', 'GRUPO', 'MATERIA',
            'MATERIA_DA_PROVA', 'TOPICO', 'ITEM_DO_EDITAL'
        )
    ),
    CONSTRAINT ck_proveniencias_da_importacao_campo CHECK (btrim(campo) <> ''),
    CONSTRAINT ck_proveniencias_da_importacao_pagina CHECK (
        pagina IS NULL OR pagina > 0
    ),
    CONSTRAINT ck_proveniencias_da_importacao_confianca CHECK (
        confianca BETWEEN 0 AND 1
    ),
    CONSTRAINT ck_proveniencias_da_importacao_trecho CHECK (
        trecho IS NULL OR char_length(trecho) <= 1000
    )
);

CREATE INDEX idx_proveniencias_da_importacao_recurso
    ON proveniencias_da_importacao_do_edital (
        importacao_id, tipo_do_recurso, recurso_id
    );

ALTER TABLE topicos_da_materia
    ADD COLUMN numero_oficial VARCHAR(80);

ALTER TABLE itens_do_edital
    ADD COLUMN numero_oficial VARCHAR(80),
    ADD COLUMN descricao_normalizada TEXT,
    ADD COLUMN importacao_de_edital_id UUID,
    ADD COLUMN importacao_de_edital_usuario_id UUID,
    ADD CONSTRAINT fk_itens_do_edital_importacao FOREIGN KEY (
        importacao_de_edital_id, importacao_de_edital_usuario_id
    ) REFERENCES importacoes_de_edital (identificador, usuario_id),
    ADD CONSTRAINT ck_itens_do_edital_importacao_completa CHECK (
        (importacao_de_edital_id IS NULL
            AND importacao_de_edital_usuario_id IS NULL)
        OR (importacao_de_edital_id IS NOT NULL
            AND importacao_de_edital_usuario_id IS NOT NULL)
    ),
    ADD CONSTRAINT ck_itens_do_edital_descricao_normalizada CHECK (
        descricao_normalizada IS NULL OR btrim(descricao_normalizada) <> ''
    );

CREATE INDEX idx_itens_do_edital_importacao
    ON itens_do_edital (importacao_de_edital_id)
    WHERE importacao_de_edital_id IS NOT NULL;

COMMENT ON COLUMN importacoes_de_edital.conteudo_original IS
    'Conteudo nao confiavel, limitado e sujeito a retencao; nunca servido publicamente.';
COMMENT ON TABLE versoes_da_extracao_do_edital IS
    'Staging imutavel. Nenhuma linha representa persistencia do dominio de concursos.';
COMMENT ON TABLE proveniencias_da_importacao_do_edital IS
    'Referencias curtas para auditoria humana, sem copia integral do edital.';

CREATE FUNCTION impedir_alteracao_da_versao_da_extracao()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'versao da extracao e imutavel';
END;
$$;

CREATE TRIGGER trg_versoes_da_extracao_imutaveis
BEFORE UPDATE OR DELETE ON versoes_da_extracao_do_edital
FOR EACH ROW EXECUTE FUNCTION impedir_alteracao_da_versao_da_extracao();
