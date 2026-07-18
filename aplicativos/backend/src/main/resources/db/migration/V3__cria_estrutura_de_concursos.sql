CREATE TABLE concursos (
    identificador UUID PRIMARY KEY,
    usuario_id UUID NOT NULL,
    nome VARCHAR(160) NOT NULL,
    nome_normalizado VARCHAR(160) NOT NULL,
    descricao VARCHAR(1000),
    orgao VARCHAR(160),
    banca VARCHAR(160),
    situacao VARCHAR(40) NOT NULL,
    data_prevista_principal DATE,
    ativo BOOLEAN NOT NULL DEFAULT FALSE,
    criado_em TIMESTAMP WITH TIME ZONE NOT NULL,
    atualizado_em TIMESTAMP WITH TIME ZONE NOT NULL,
    versao BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT concursos_usuario_fk FOREIGN KEY (usuario_id) REFERENCES usuarios (identificador),
    CONSTRAINT concursos_situacao_valida CHECK (situacao IN (
        'PLANEJADO', 'EDITAL_PUBLICADO', 'INSCRICOES_ABERTAS', 'EM_ANDAMENTO',
        'ENCERRADO', 'SUSPENSO', 'CANCELADO', 'ARQUIVADO'
    ))
);

CREATE INDEX concursos_usuario_indice ON concursos (usuario_id);
CREATE INDEX concursos_usuario_situacao_indice ON concursos (usuario_id, situacao);
CREATE UNIQUE INDEX concursos_um_ativo_por_usuario
    ON concursos (usuario_id) WHERE ativo;

CREATE TABLE editais (
    identificador UUID PRIMARY KEY,
    concurso_id UUID NOT NULL,
    titulo VARCHAR(200) NOT NULL,
    numero VARCHAR(80),
    ano INTEGER,
    descricao VARCHAR(1000),
    data_de_publicacao DATE,
    endereco_do_documento VARCHAR(2048),
    principal BOOLEAN NOT NULL DEFAULT FALSE,
    criado_em TIMESTAMP WITH TIME ZONE NOT NULL,
    atualizado_em TIMESTAMP WITH TIME ZONE NOT NULL,
    versao BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT editais_concurso_fk FOREIGN KEY (concurso_id) REFERENCES concursos (identificador),
    CONSTRAINT editais_ano_positivo CHECK (ano IS NULL OR ano > 0)
);

CREATE INDEX editais_concurso_indice ON editais (concurso_id);
CREATE UNIQUE INDEX editais_um_principal_por_concurso
    ON editais (concurso_id) WHERE principal;

CREATE TABLE cargos_do_concurso (
    identificador UUID PRIMARY KEY,
    concurso_id UUID NOT NULL,
    nome VARCHAR(160) NOT NULL,
    nome_normalizado VARCHAR(160) NOT NULL,
    area VARCHAR(160),
    especialidade VARCHAR(160),
    nivel_de_escolaridade VARCHAR(30) NOT NULL,
    selecionado BOOLEAN NOT NULL DEFAULT FALSE,
    ordem INTEGER NOT NULL,
    criado_em TIMESTAMP WITH TIME ZONE NOT NULL,
    atualizado_em TIMESTAMP WITH TIME ZONE NOT NULL,
    versao BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT cargos_concurso_fk FOREIGN KEY (concurso_id) REFERENCES concursos (identificador),
    CONSTRAINT cargos_nome_unico_no_concurso UNIQUE (concurso_id, nome_normalizado),
    CONSTRAINT cargos_ordem_positiva CHECK (ordem > 0),
    CONSTRAINT cargos_nivel_valido CHECK (nivel_de_escolaridade IN (
        'FUNDAMENTAL', 'MEDIO', 'TECNICO', 'SUPERIOR', 'NAO_INFORMADO'
    ))
);

CREATE INDEX cargos_concurso_indice ON cargos_do_concurso (concurso_id);
CREATE UNIQUE INDEX cargos_um_selecionado_por_concurso
    ON cargos_do_concurso (concurso_id) WHERE selecionado;

CREATE TABLE provas (
    identificador UUID PRIMARY KEY,
    cargo_id UUID NOT NULL,
    nome VARCHAR(160) NOT NULL,
    nome_normalizado VARCHAR(160) NOT NULL,
    tipo VARCHAR(30) NOT NULL,
    carater VARCHAR(50) NOT NULL,
    ordem INTEGER NOT NULL,
    data_hora_prevista TIMESTAMP WITH TIME ZONE,
    duracao_em_minutos INTEGER,
    quantidade_de_questoes INTEGER,
    pontuacao_maxima NUMERIC(12, 2),
    pontuacao_minima NUMERIC(12, 2),
    criado_em TIMESTAMP WITH TIME ZONE NOT NULL,
    atualizado_em TIMESTAMP WITH TIME ZONE NOT NULL,
    versao BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT provas_cargo_fk FOREIGN KEY (cargo_id) REFERENCES cargos_do_concurso (identificador),
    CONSTRAINT provas_nome_unico_no_cargo UNIQUE (cargo_id, nome_normalizado),
    CONSTRAINT provas_tipo_valido CHECK (tipo IN ('OBJETIVA', 'DISCURSIVA', 'PRATICA', 'TITULOS', 'OUTRA')),
    CONSTRAINT provas_carater_valido CHECK (carater IN (
        'ELIMINATORIO', 'CLASSIFICATORIO', 'ELIMINATORIO_E_CLASSIFICATORIO', 'NAO_INFORMADO'
    )),
    CONSTRAINT provas_ordem_positiva CHECK (ordem > 0),
    CONSTRAINT provas_duracao_positiva CHECK (duracao_em_minutos IS NULL OR duracao_em_minutos > 0),
    CONSTRAINT provas_questoes_positivas CHECK (quantidade_de_questoes IS NULL OR quantidade_de_questoes > 0),
    CONSTRAINT provas_pontuacao_maxima_positiva CHECK (pontuacao_maxima IS NULL OR pontuacao_maxima > 0),
    CONSTRAINT provas_pontuacao_minima_positiva CHECK (pontuacao_minima IS NULL OR pontuacao_minima > 0),
    CONSTRAINT provas_pontuacao_coerente CHECK (
        pontuacao_minima IS NULL OR pontuacao_maxima IS NULL OR pontuacao_minima <= pontuacao_maxima
    )
);

CREATE INDEX provas_cargo_indice ON provas (cargo_id);

CREATE TABLE grupos_de_conteudo (
    identificador UUID PRIMARY KEY,
    prova_id UUID NOT NULL,
    nome VARCHAR(160) NOT NULL,
    nome_normalizado VARCHAR(160) NOT NULL,
    ordem INTEGER NOT NULL,
    quantidade_de_questoes INTEGER,
    pontuacao_maxima NUMERIC(12, 2),
    pontuacao_minima NUMERIC(12, 2),
    criado_em TIMESTAMP WITH TIME ZONE NOT NULL,
    atualizado_em TIMESTAMP WITH TIME ZONE NOT NULL,
    versao BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT grupos_prova_fk FOREIGN KEY (prova_id) REFERENCES provas (identificador),
    CONSTRAINT grupos_nome_unico_na_prova UNIQUE (prova_id, nome_normalizado),
    CONSTRAINT grupos_ordem_positiva CHECK (ordem > 0),
    CONSTRAINT grupos_questoes_positivas CHECK (quantidade_de_questoes IS NULL OR quantidade_de_questoes > 0),
    CONSTRAINT grupos_pontuacao_maxima_positiva CHECK (pontuacao_maxima IS NULL OR pontuacao_maxima > 0),
    CONSTRAINT grupos_pontuacao_minima_positiva CHECK (pontuacao_minima IS NULL OR pontuacao_minima > 0),
    CONSTRAINT grupos_pontuacao_coerente CHECK (
        pontuacao_minima IS NULL OR pontuacao_maxima IS NULL OR pontuacao_minima <= pontuacao_maxima
    )
);

CREATE INDEX grupos_prova_indice ON grupos_de_conteudo (prova_id);

CREATE TABLE materias_da_prova (
    identificador UUID PRIMARY KEY,
    grupo_de_conteudo_id UUID NOT NULL,
    materia_id UUID NOT NULL,
    ordem INTEGER NOT NULL,
    peso NUMERIC(12, 4),
    quantidade_de_questoes INTEGER,
    pontuacao_maxima NUMERIC(12, 2),
    criado_em TIMESTAMP WITH TIME ZONE NOT NULL,
    atualizado_em TIMESTAMP WITH TIME ZONE NOT NULL,
    versao BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT materias_da_prova_grupo_fk
        FOREIGN KEY (grupo_de_conteudo_id) REFERENCES grupos_de_conteudo (identificador),
    CONSTRAINT materias_da_prova_materia_fk FOREIGN KEY (materia_id) REFERENCES materias (identificador),
    CONSTRAINT materias_da_prova_unica_no_grupo UNIQUE (grupo_de_conteudo_id, materia_id),
    CONSTRAINT materias_da_prova_ordem_positiva CHECK (ordem > 0),
    CONSTRAINT materias_da_prova_peso_positivo CHECK (peso IS NULL OR peso > 0),
    CONSTRAINT materias_da_prova_questoes_positivas CHECK (
        quantidade_de_questoes IS NULL OR quantidade_de_questoes > 0
    ),
    CONSTRAINT materias_da_prova_pontuacao_positiva CHECK (
        pontuacao_maxima IS NULL OR pontuacao_maxima > 0
    )
);

CREATE INDEX materias_da_prova_grupo_indice ON materias_da_prova (grupo_de_conteudo_id);
CREATE INDEX materias_da_prova_materia_indice ON materias_da_prova (materia_id);
