CREATE TABLE itens_do_edital (
    identificador UUID PRIMARY KEY,
    edital_id UUID NOT NULL,
    materia_da_prova_id UUID NOT NULL,
    descricao_original TEXT NOT NULL,
    item_pai_id UUID,
    ordem INTEGER NOT NULL,
    criado_em TIMESTAMP WITH TIME ZONE NOT NULL,
    atualizado_em TIMESTAMP WITH TIME ZONE NOT NULL,
    versao BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT itens_edital_fk FOREIGN KEY (edital_id) REFERENCES editais (identificador),
    CONSTRAINT itens_materia_da_prova_fk
        FOREIGN KEY (materia_da_prova_id) REFERENCES materias_da_prova (identificador),
    CONSTRAINT itens_pai_fk FOREIGN KEY (item_pai_id) REFERENCES itens_do_edital (identificador),
    CONSTRAINT itens_descricao_obrigatoria CHECK (btrim(descricao_original) <> ''),
    CONSTRAINT itens_ordem_positiva CHECK (ordem > 0),
    CONSTRAINT itens_nao_sao_pais_de_si CHECK (
        item_pai_id IS NULL OR item_pai_id <> identificador
    )
);

CREATE INDEX itens_materia_da_prova_indice ON itens_do_edital (materia_da_prova_id);
CREATE INDEX itens_edital_indice ON itens_do_edital (edital_id);
CREATE INDEX itens_pai_indice ON itens_do_edital (item_pai_id);

CREATE TABLE mapeamentos_de_itens_do_edital (
    identificador UUID PRIMARY KEY,
    item_do_edital_id UUID NOT NULL,
    topico_da_materia_id UUID NOT NULL,
    confirmado BOOLEAN NOT NULL,
    criado_em TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT mapeamentos_item_fk
        FOREIGN KEY (item_do_edital_id) REFERENCES itens_do_edital (identificador),
    CONSTRAINT mapeamentos_topico_fk
        FOREIGN KEY (topico_da_materia_id) REFERENCES topicos_da_materia (identificador),
    CONSTRAINT mapeamentos_item_topico_unico
        UNIQUE (item_do_edital_id, topico_da_materia_id)
);

CREATE INDEX mapeamentos_item_indice
    ON mapeamentos_de_itens_do_edital (item_do_edital_id);
CREATE INDEX mapeamentos_topico_indice
    ON mapeamentos_de_itens_do_edital (topico_da_materia_id);
