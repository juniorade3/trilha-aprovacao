package br.com.trilhaaprovacao.planejamento.dominio;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

public record BlocoDeEstudo(
        UUID identificador,
        UUID identificadorDoPlano,
        UUID identificadorDaMateria,
        UUID identificadorDoTopico,
        String titulo,
        TipoDeAtividade tipoDeAtividade,
        LocalDate data,
        int duracaoPrevistaEmMinutos,
        int ordem,
        LocalTime horarioPrevisto,
        String observacao,
        EstadoDoBlocoDeEstudo estado,
        OffsetDateTime criadoEm,
        OffsetDateTime atualizadoEm,
        long versao) {

    public BlocoDeEstudo {
        Objects.requireNonNull(identificador);
        Objects.requireNonNull(identificadorDoPlano);
        titulo = textoObrigatorio(titulo, "Titulo", 200);
        Objects.requireNonNull(tipoDeAtividade);
        Objects.requireNonNull(data);
        if (duracaoPrevistaEmMinutos < 1 || duracaoPrevistaEmMinutos > 1440) {
            throw new IllegalArgumentException(
                    "Duracao prevista deve estar entre 1 e 1440 minutos.");
        }
        if (ordem < 1) {
            throw new IllegalArgumentException("Ordem deve ser positiva.");
        }
        if (identificadorDoTopico != null && identificadorDaMateria == null) {
            throw new IllegalArgumentException("Topico exige uma materia.");
        }
        observacao = textoOpcional(observacao, "Observacao", 2000);
        Objects.requireNonNull(estado);
        Objects.requireNonNull(criadoEm);
        Objects.requireNonNull(atualizadoEm);
    }

    public static BlocoDeEstudo criar(UUID plano, UUID materia, UUID topico,
            String titulo, TipoDeAtividade tipo, LocalDate data, int duracao,
            int ordem, LocalTime horario, String observacao) {
        OffsetDateTime agora = OffsetDateTime.now();
        return new BlocoDeEstudo(UUID.randomUUID(), plano, materia, topico,
                titulo, tipo, data, duracao, ordem, horario, observacao,
                EstadoDoBlocoDeEstudo.PLANEJADO, agora, agora, 0);
    }

    public BlocoDeEstudo alterarPlanejamento(UUID materia, UUID topico,
            String novoTitulo, TipoDeAtividade tipo, LocalDate novaData,
            int novaDuracao, int novaOrdem, LocalTime horario, String novaObservacao) {
        exigirPlanejado();
        return new BlocoDeEstudo(identificador, identificadorDoPlano, materia, topico,
                novoTitulo, tipo, novaData, novaDuracao, novaOrdem, horario,
                novaObservacao, estado, criadoEm, OffsetDateTime.now(), versao);
    }

    public BlocoDeEstudo moverPara(LocalDate novaData, int novaOrdem) {
        return alterarPlanejamento(identificadorDaMateria, identificadorDoTopico,
                titulo, tipoDeAtividade, novaData, duracaoPrevistaEmMinutos,
                novaOrdem, horarioPrevisto, observacao);
    }

    private void exigirPlanejado() {
        if (estado != EstadoDoBlocoDeEstudo.PLANEJADO) {
            throw new IllegalStateException("Somente bloco planejado pode ser alterado.");
        }
    }

    private static String textoObrigatorio(String valor, String campo, int limite) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(campo + " e obrigatorio.");
        }
        String normalizado = valor.trim();
        if (normalizado.length() > limite) {
            throw new IllegalArgumentException(campo + " excede " + limite + " caracteres.");
        }
        return normalizado;
    }

    private static String textoOpcional(String valor, String campo, int limite) {
        if (valor == null || valor.isBlank()) return null;
        String normalizado = valor.trim();
        if (normalizado.length() > limite) {
            throw new IllegalArgumentException(campo + " excede " + limite + " caracteres.");
        }
        return normalizado;
    }
}
