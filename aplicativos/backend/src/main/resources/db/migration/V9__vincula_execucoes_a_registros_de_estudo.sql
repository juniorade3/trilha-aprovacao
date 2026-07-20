ALTER TABLE execucoes_de_bloco
    ADD COLUMN registro_de_estudo_id UUID;
ALTER TABLE execucoes_de_bloco
    ADD CONSTRAINT fk_execucoes_registro_de_estudo
    FOREIGN KEY (registro_de_estudo_id)
    REFERENCES registros_de_estudo (identificador);
ALTER TABLE execucoes_de_bloco
    ADD CONSTRAINT uk_execucoes_registro_de_estudo
    UNIQUE (registro_de_estudo_id);
CREATE INDEX idx_execucoes_registro_de_estudo
    ON execucoes_de_bloco (registro_de_estudo_id)
    WHERE registro_de_estudo_id IS NOT NULL;
