ALTER TABLE registros_de_estudo
    ADD COLUMN tipo_de_estudo VARCHAR(30);

WITH RECURSIVE tipos_vinculados (registro_id, tipo_de_estudo) AS (
    SELECT e.registro_de_estudo_id, b.tipo_de_atividade
    FROM execucoes_de_bloco e
    JOIN blocos_de_estudo b ON b.identificador = e.bloco_id
    WHERE e.registro_de_estudo_id IS NOT NULL
), tipos_dos_ancestrais (registro_id, tipo_de_estudo) AS (
    SELECT registro_id, tipo_de_estudo
    FROM tipos_vinculados

    UNION

    SELECT origem.identificador, recuperado.tipo_de_estudo
    FROM tipos_dos_ancestrais recuperado
    JOIN registros_de_estudo atual
        ON atual.identificador = recuperado.registro_id
    JOIN registros_de_estudo origem
        ON origem.identificador = atual.registro_de_origem_id
), tipos_dos_descendentes (registro_id, tipo_de_estudo) AS (
    SELECT registro_id, tipo_de_estudo
    FROM tipos_vinculados

    UNION

    SELECT correcao.identificador, recuperado.tipo_de_estudo
    FROM tipos_dos_descendentes recuperado
    JOIN registros_de_estudo correcao
        ON correcao.registro_de_origem_id = recuperado.registro_id
), tipos_recuperados AS (
    SELECT registro_id, tipo_de_estudo FROM tipos_dos_ancestrais
    UNION
    SELECT registro_id, tipo_de_estudo FROM tipos_dos_descendentes
)
UPDATE registros_de_estudo r
SET tipo_de_estudo = recuperado.tipo_de_estudo
FROM tipos_recuperados recuperado
WHERE recuperado.registro_id = r.identificador;

UPDATE registros_de_estudo
SET tipo_de_estudo = 'OUTRA'
WHERE tipo_de_estudo IS NULL;

ALTER TABLE registros_de_estudo
    ALTER COLUMN tipo_de_estudo SET DEFAULT 'OUTRA',
    ALTER COLUMN tipo_de_estudo SET NOT NULL,
    ADD CONSTRAINT ck_registros_tipo_de_estudo CHECK (
        tipo_de_estudo IN (
            'TEORIA', 'QUESTOES', 'REVISAO', 'CADERNO_DE_ERROS',
            'SIMULADO', 'DISCURSIVA', 'OUTRA'
        )
    );

CREATE INDEX idx_registros_topico_data_situacao
    ON registros_de_estudo (topico_id, data_hora DESC, situacao);

CREATE TABLE evidencias_de_aprendizagem (
    identificador UUID PRIMARY KEY,
    registro_de_estudo_id UUID NOT NULL,
    quantidade_de_questoes INTEGER,
    quantidade_de_acertos INTEGER,
    nivel_de_recordacao INTEGER,
    dificuldade_percebida INTEGER,
    criado_em TIMESTAMPTZ NOT NULL,
    atualizado_em TIMESTAMPTZ NOT NULL,
    versao BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_evidencias_registro FOREIGN KEY (registro_de_estudo_id)
        REFERENCES registros_de_estudo (identificador),
    CONSTRAINT uk_evidencias_registro UNIQUE (registro_de_estudo_id),
    CONSTRAINT ck_evidencias_questoes CHECK (
        (quantidade_de_questoes IS NULL AND quantidade_de_acertos IS NULL)
        OR (quantidade_de_questoes > 0 AND quantidade_de_acertos BETWEEN 0 AND quantidade_de_questoes)
    ),
    CONSTRAINT ck_evidencias_recordacao CHECK (
        nivel_de_recordacao IS NULL OR nivel_de_recordacao BETWEEN 1 AND 5
    ),
    CONSTRAINT ck_evidencias_dificuldade CHECK (
        dificuldade_percebida IS NULL OR dificuldade_percebida BETWEEN 1 AND 5
    ),
    CONSTRAINT ck_evidencias_possui_resultado CHECK (
        quantidade_de_questoes IS NOT NULL
        OR nivel_de_recordacao IS NOT NULL
        OR dificuldade_percebida IS NOT NULL
    )
);

CREATE TABLE padroes_de_erro (
    identificador UUID PRIMARY KEY,
    usuario_id UUID NOT NULL,
    topico_id UUID NOT NULL,
    descricao VARCHAR(200) NOT NULL,
    descricao_normalizada VARCHAR(200) NOT NULL,
    criado_em TIMESTAMPTZ NOT NULL,
    atualizado_em TIMESTAMPTZ NOT NULL,
    versao BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_padroes_usuario FOREIGN KEY (usuario_id)
        REFERENCES usuarios (identificador),
    CONSTRAINT fk_padroes_topico FOREIGN KEY (topico_id)
        REFERENCES topicos_da_materia (identificador),
    CONSTRAINT uk_padroes_usuario_topico_descricao UNIQUE (
        usuario_id, topico_id, descricao_normalizada
    ),
    CONSTRAINT ck_padroes_descricao CHECK (
        btrim(descricao) <> '' AND btrim(descricao_normalizada) <> ''
    )
);

CREATE TABLE ocorrencias_de_padrao_de_erro (
    identificador UUID PRIMARY KEY,
    evidencia_id UUID NOT NULL,
    padrao_de_erro_id UUID NOT NULL,
    quantidade_de_ocorrencias INTEGER NOT NULL,
    criado_em TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_ocorrencias_evidencia FOREIGN KEY (evidencia_id)
        REFERENCES evidencias_de_aprendizagem (identificador),
    CONSTRAINT fk_ocorrencias_padrao FOREIGN KEY (padrao_de_erro_id)
        REFERENCES padroes_de_erro (identificador),
    CONSTRAINT uk_ocorrencias_evidencia_padrao UNIQUE (evidencia_id, padrao_de_erro_id),
    CONSTRAINT ck_ocorrencias_quantidade CHECK (quantidade_de_ocorrencias > 0)
);

CREATE INDEX idx_ocorrencias_padrao_evidencia
    ON ocorrencias_de_padrao_de_erro (padrao_de_erro_id, evidencia_id);

COMMENT ON COLUMN registros_de_estudo.tipo_de_estudo IS
    'Recuperado do bloco vinculado quando possivel; registros anteriores sem origem conhecida foram classificados como OUTRA.';
