ALTER TABLE blocos_de_estudo
    ADD COLUMN quantidade_de_reagendamentos INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN reagendado_em TIMESTAMPTZ;

ALTER TABLE blocos_de_estudo
    ADD CONSTRAINT ck_blocos_quantidade_reagendamentos
    CHECK (quantidade_de_reagendamentos >= 0);
