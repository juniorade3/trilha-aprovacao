package br.com.trilhaaprovacao.concursos.dominio;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

public record Edital(
        UUID identificador,
        UUID identificadorDoConcurso,
        String titulo,
        String numero,
        Integer ano,
        String descricao,
        LocalDate dataDePublicacao,
        String enderecoDoDocumento,
        boolean principal,
        OffsetDateTime criadoEm,
        OffsetDateTime atualizadoEm,
        long versao) {

    public Edital {
        Objects.requireNonNull(identificador);
        Objects.requireNonNull(identificadorDoConcurso);
        titulo = ValidacaoDoConcurso.textoObrigatorio(titulo, "Titulo");
        numero = ValidacaoDoConcurso.textoOpcional(numero);
        descricao = ValidacaoDoConcurso.textoOpcional(descricao);
        enderecoDoDocumento = ValidacaoDoConcurso.enderecoValido(enderecoDoDocumento);
        ValidacaoDoConcurso.inteiroPositivo(ano, "Ano");
        Objects.requireNonNull(criadoEm);
        Objects.requireNonNull(atualizadoEm);
    }

    public static Edital criar(UUID concurso, String titulo, String numero, Integer ano,
            String descricao, LocalDate dataDePublicacao, String enderecoDoDocumento) {
        OffsetDateTime agora = OffsetDateTime.now();
        return new Edital(UUID.randomUUID(), concurso, titulo, numero, ano, descricao,
                dataDePublicacao, enderecoDoDocumento, false, agora, agora, 0);
    }

    public Edital alterar(String novoTitulo, String novoNumero, Integer novoAno,
            String novaDescricao, LocalDate novaData, String novoEndereco) {
        return new Edital(identificador, identificadorDoConcurso, novoTitulo, novoNumero,
                novoAno, novaDescricao, novaData, novoEndereco, principal,
                criadoEm, OffsetDateTime.now(), versao);
    }

    public Edital definirComoPrincipal(boolean deveSerPrincipal) {
        return new Edital(identificador, identificadorDoConcurso, titulo, numero, ano,
                descricao, dataDePublicacao, enderecoDoDocumento, deveSerPrincipal,
                criadoEm, OffsetDateTime.now(), versao);
    }
}
