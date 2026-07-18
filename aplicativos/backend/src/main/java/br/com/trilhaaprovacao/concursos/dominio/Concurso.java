package br.com.trilhaaprovacao.concursos.dominio;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

public record Concurso(
        UUID identificador,
        UUID identificadorDoUsuario,
        String nome,
        String nomeNormalizado,
        String descricao,
        String orgao,
        String banca,
        SituacaoDoConcurso situacao,
        LocalDate dataPrevistaPrincipal,
        boolean ativo,
        OffsetDateTime criadoEm,
        OffsetDateTime atualizadoEm,
        long versao) {

    public Concurso {
        Objects.requireNonNull(identificador);
        Objects.requireNonNull(identificadorDoUsuario);
        nome = ValidacaoDoConcurso.textoObrigatorio(nome, "Nome");
        nomeNormalizado = ValidacaoDoConcurso.chave(nome);
        descricao = ValidacaoDoConcurso.textoOpcional(descricao);
        orgao = ValidacaoDoConcurso.textoOpcional(orgao);
        banca = ValidacaoDoConcurso.textoOpcional(banca);
        Objects.requireNonNull(situacao);
        if (situacao == SituacaoDoConcurso.ARQUIVADO && ativo) {
            throw new IllegalArgumentException("Concurso arquivado nao pode estar ativo.");
        }
        Objects.requireNonNull(criadoEm);
        Objects.requireNonNull(atualizadoEm);
    }

    public static Concurso criar(UUID usuario, String nome, String descricao, String orgao,
            String banca, SituacaoDoConcurso situacao, LocalDate dataPrevistaPrincipal) {
        OffsetDateTime agora = OffsetDateTime.now();
        return new Concurso(UUID.randomUUID(), usuario, nome, null, descricao, orgao, banca,
                situacao, dataPrevistaPrincipal, false, agora, agora, 0);
    }

    public Concurso alterar(String novoNome, String novaDescricao, String novoOrgao,
            String novaBanca, SituacaoDoConcurso novaSituacao, LocalDate novaData) {
        exigirAtivoParaAlteracoes();
        if (novaSituacao == SituacaoDoConcurso.ARQUIVADO) {
            throw new IllegalArgumentException("Use o arquivamento para arquivar o concurso.");
        }
        return new Concurso(identificador, identificadorDoUsuario, novoNome, null,
                novaDescricao, novoOrgao, novaBanca, novaSituacao, novaData,
                ativo, criadoEm, OffsetDateTime.now(), versao);
    }

    public Concurso definirAtivacao(boolean deveAtivar) {
        if (deveAtivar) {
            exigirAtivoParaAlteracoes();
        }
        return new Concurso(identificador, identificadorDoUsuario, nome, nomeNormalizado,
                descricao, orgao, banca, situacao, dataPrevistaPrincipal, deveAtivar,
                criadoEm, OffsetDateTime.now(), versao);
    }

    public Concurso definirArquivamento(boolean deveArquivar) {
        SituacaoDoConcurso novaSituacao = deveArquivar
                ? SituacaoDoConcurso.ARQUIVADO
                : SituacaoDoConcurso.PLANEJADO;
        return new Concurso(identificador, identificadorDoUsuario, nome, nomeNormalizado,
                descricao, orgao, banca, novaSituacao, dataPrevistaPrincipal, false,
                criadoEm, OffsetDateTime.now(), versao);
    }

    public void exigirAtivoParaAlteracoes() {
        if (situacao == SituacaoDoConcurso.ARQUIVADO) {
            throw new IllegalStateException("Concurso arquivado nao aceita alteracoes de conteudo.");
        }
    }
}
