CREATE TABLE trilhas_publicadas (
    identificador UUID PRIMARY KEY,
    codigo VARCHAR(80) NOT NULL UNIQUE,
    nome VARCHAR(200) NOT NULL,
    versao_publicada VARCHAR(40) NOT NULL,
    descricao VARCHAR(1000),
    publicada_em TIMESTAMPTZ NOT NULL,
    criado_em TIMESTAMPTZ NOT NULL,
    atualizado_em TIMESTAMPTZ NOT NULL,
    versao BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE disciplinas_da_trilha (
    identificador UUID PRIMARY KEY,
    trilha_id UUID NOT NULL REFERENCES trilhas_publicadas (identificador),
    nome VARCHAR(160) NOT NULL,
    ordem INTEGER NOT NULL,
    CONSTRAINT disciplinas_da_trilha_ordem_positiva CHECK (ordem > 0),
    CONSTRAINT disciplinas_da_trilha_nome_unico UNIQUE (trilha_id, nome),
    CONSTRAINT disciplinas_da_trilha_ordem_unica UNIQUE (trilha_id, ordem)
);

CREATE TABLE tarefas_publicadas_da_trilha (
    identificador UUID PRIMARY KEY,
    disciplina_id UUID NOT NULL REFERENCES disciplinas_da_trilha (identificador),
    numero INTEGER NOT NULL,
    titulo VARCHAR(280) NOT NULL,
    aula VARCHAR(160),
    tipo_de_atividade VARCHAR(30) NOT NULL,
    endereco_do_material VARCHAR(2048),
    orientacao VARCHAR(8000),
    CONSTRAINT tarefas_publicadas_numero_positivo CHECK (numero > 0),
    CONSTRAINT tarefas_publicadas_numero_unico UNIQUE (disciplina_id, numero),
    CONSTRAINT tarefas_publicadas_tipo_valido CHECK (tipo_de_atividade IN (
        'TEORIA', 'QUESTOES', 'REVISAO', 'CADERNO_DE_ERROS',
        'SIMULADO', 'DISCURSIVA', 'OUTRA'
    ))
);

CREATE INDEX tarefas_publicadas_da_trilha_disciplina_indice
    ON tarefas_publicadas_da_trilha (disciplina_id, numero);

CREATE TABLE adesoes_a_trilhas_publicadas (
    identificador UUID PRIMARY KEY,
    usuario_id UUID NOT NULL REFERENCES usuarios (identificador),
    trilha_id UUID NOT NULL REFERENCES trilhas_publicadas (identificador),
    aderida_em TIMESTAMPTZ NOT NULL,
    versao BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT adesoes_a_trilhas_publicadas_unica UNIQUE (usuario_id, trilha_id)
);

CREATE INDEX adesoes_a_trilhas_publicadas_usuario_indice
    ON adesoes_a_trilhas_publicadas (usuario_id);

CREATE TABLE acompanhamentos_de_tarefas_da_trilha (
    identificador UUID PRIMARY KEY,
    adesao_id UUID NOT NULL REFERENCES adesoes_a_trilhas_publicadas (identificador),
    tarefa_id UUID NOT NULL REFERENCES tarefas_publicadas_da_trilha (identificador),
    situacao VARCHAR(30) NOT NULL,
    observacao VARCHAR(2000),
    concluida_em TIMESTAMPTZ,
    criado_em TIMESTAMPTZ NOT NULL,
    atualizado_em TIMESTAMPTZ NOT NULL,
    versao BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT acompanhamentos_de_tarefas_unico UNIQUE (adesao_id, tarefa_id),
    CONSTRAINT acompanhamentos_de_tarefas_situacao_valida CHECK (situacao IN (
        'PENDENTE', 'EM_ANDAMENTO', 'CONCLUIDA', 'PULADA'
    )),
    CONSTRAINT acompanhamentos_de_tarefas_conclusao_coerente CHECK (
        (situacao = 'CONCLUIDA' AND concluida_em IS NOT NULL)
        OR (situacao <> 'CONCLUIDA' AND concluida_em IS NULL)
    )
);

CREATE INDEX acompanhamentos_de_tarefas_da_trilha_adesao_indice
    ON acompanhamentos_de_tarefas_da_trilha (adesao_id);
