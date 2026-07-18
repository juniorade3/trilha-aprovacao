CREATE TABLE materiais_de_estudo (
    identificador UUID PRIMARY KEY,
    usuario_id UUID NOT NULL,
    titulo VARCHAR(200) NOT NULL,
    tipo VARCHAR(20) NOT NULL,
    descricao VARCHAR(1000),
    fonte VARCHAR(200),
    endereco VARCHAR(2048),
    duracao_estimada_em_minutos INTEGER,
    arquivado BOOLEAN NOT NULL DEFAULT FALSE,
    criado_em TIMESTAMP WITH TIME ZONE NOT NULL,
    atualizado_em TIMESTAMP WITH TIME ZONE NOT NULL,
    versao BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT materiais_estudo_usuario_fk FOREIGN KEY (usuario_id) REFERENCES usuarios (identificador),
    CONSTRAINT materiais_estudo_tipo_valido CHECK (tipo IN ('AULA', 'PDF', 'OUTRO')),
    CONSTRAINT materiais_estudo_duracao_positiva CHECK (
        duracao_estimada_em_minutos IS NULL OR duracao_estimada_em_minutos > 0
    )
);

CREATE INDEX materiais_estudo_usuario_indice ON materiais_de_estudo (usuario_id);
CREATE INDEX materiais_estudo_usuario_arquivado_indice
    ON materiais_de_estudo (usuario_id, arquivado);

CREATE TABLE coberturas_de_topicos_por_material (
    identificador UUID PRIMARY KEY,
    material_id UUID NOT NULL,
    topico_id UUID NOT NULL,
    criado_em TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT coberturas_material_fk FOREIGN KEY (material_id) REFERENCES materiais_de_estudo (identificador),
    CONSTRAINT coberturas_topico_fk FOREIGN KEY (topico_id) REFERENCES topicos_da_materia (identificador),
    CONSTRAINT coberturas_material_topico_unico UNIQUE (material_id, topico_id)
);

CREATE INDEX coberturas_material_indice ON coberturas_de_topicos_por_material (material_id);
CREATE INDEX coberturas_topico_indice ON coberturas_de_topicos_por_material (topico_id);

CREATE TABLE registros_de_estudo (
    identificador UUID PRIMARY KEY,
    topico_id UUID NOT NULL,
    material_id UUID,
    registro_de_origem_id UUID,
    data_hora TIMESTAMP WITH TIME ZONE NOT NULL,
    duracao_em_minutos INTEGER NOT NULL,
    observacao VARCHAR(2000),
    situacao VARCHAR(20) NOT NULL,
    criado_em TIMESTAMP WITH TIME ZONE NOT NULL,
    atualizado_em TIMESTAMP WITH TIME ZONE NOT NULL,
    versao BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT registros_topico_fk FOREIGN KEY (topico_id) REFERENCES topicos_da_materia (identificador),
    CONSTRAINT registros_material_fk FOREIGN KEY (material_id) REFERENCES materiais_de_estudo (identificador),
    CONSTRAINT registros_origem_fk FOREIGN KEY (registro_de_origem_id) REFERENCES registros_de_estudo (identificador),
    CONSTRAINT registros_origem_unica UNIQUE (registro_de_origem_id),
    CONSTRAINT registros_situacao_valida CHECK (situacao IN ('ATIVO', 'CORRIGIDO', 'CANCELADO')),
    CONSTRAINT registros_duracao_valida CHECK (duracao_em_minutos BETWEEN 1 AND 1440),
    CONSTRAINT registros_nao_originam_a_si CHECK (
        registro_de_origem_id IS NULL OR registro_de_origem_id <> identificador
    )
);

CREATE INDEX registros_topico_indice ON registros_de_estudo (topico_id);
CREATE INDEX registros_material_indice ON registros_de_estudo (material_id);
CREATE INDEX registros_data_hora_indice ON registros_de_estudo (data_hora DESC);
