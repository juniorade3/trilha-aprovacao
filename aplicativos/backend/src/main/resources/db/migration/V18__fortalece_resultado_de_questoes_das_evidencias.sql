ALTER TABLE evidencias_de_aprendizagem
    DROP CONSTRAINT ck_evidencias_questoes;

ALTER TABLE evidencias_de_aprendizagem
    ADD CONSTRAINT ck_evidencias_questoes CHECK (
        (
            quantidade_de_questoes IS NULL
            AND quantidade_de_acertos IS NULL
        )
        OR
        (
            quantidade_de_questoes IS NOT NULL
            AND quantidade_de_acertos IS NOT NULL
            AND quantidade_de_questoes > 0
            AND quantidade_de_acertos BETWEEN 0 AND quantidade_de_questoes
        )
    );
