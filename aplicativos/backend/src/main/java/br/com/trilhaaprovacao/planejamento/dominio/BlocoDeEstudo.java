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
        OrigemDoBlocoDeEstudo origem,
        String justificativaDaGeracao,
        EstadoDoBlocoDeEstudo estado,
        int quantidadeDeReagendamentos,
        OffsetDateTime reagendadoEm,
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
        Objects.requireNonNull(origem);
        justificativaDaGeracao = textoOpcional(
                justificativaDaGeracao, "Justificativa da geracao", 2000);
        Objects.requireNonNull(estado);
        if (quantidadeDeReagendamentos < 0) {
            throw new IllegalArgumentException("Quantidade de reagendamentos invalida.");
        }
        Objects.requireNonNull(criadoEm);
        Objects.requireNonNull(atualizadoEm);
    }

    public static BlocoDeEstudo criar(UUID plano, UUID materia, UUID topico,
            String titulo, TipoDeAtividade tipo, LocalDate data, int duracao,
            int ordem, LocalTime horario, String observacao) {
        OffsetDateTime agora = OffsetDateTime.now();
        return new BlocoDeEstudo(UUID.randomUUID(), plano, materia, topico,
                titulo, tipo, data, duracao, ordem, horario, observacao,
                OrigemDoBlocoDeEstudo.MANUAL, null,
                EstadoDoBlocoDeEstudo.PLANEJADO, 0, null, agora, agora, 0);
    }

    public static BlocoDeEstudo criarGerado(UUID plano, UUID materia,
            String titulo, TipoDeAtividade tipo, LocalDate data, int duracao,
            int ordem, String justificativaDaGeracao) {
        OffsetDateTime agora = OffsetDateTime.now();
        return new BlocoDeEstudo(UUID.randomUUID(), plano, materia, null,
                titulo, tipo, data, duracao, ordem, null, null,
                OrigemDoBlocoDeEstudo.GERADO_DETERMINISTICAMENTE,
                justificativaDaGeracao, EstadoDoBlocoDeEstudo.PLANEJADO,
                0, null, agora, agora, 0);
    }

    public BlocoDeEstudo alterarPlanejamento(UUID materia, UUID topico,
            String novoTitulo, TipoDeAtividade tipo, LocalDate novaData,
            int novaDuracao, int novaOrdem, LocalTime horario, String novaObservacao) {
        exigirPlanejado();
        return new BlocoDeEstudo(identificador, identificadorDoPlano, materia, topico,
                novoTitulo, tipo, novaData, novaDuracao, novaOrdem, horario,
                novaObservacao, origemAposAjusteManual(), justificativaDaGeracao,
                estado, quantidadeDeReagendamentos, reagendadoEm, criadoEm,
                OffsetDateTime.now(), versao);
    }

    public BlocoDeEstudo moverPara(LocalDate novaData, int novaOrdem) {
        return alterarPlanejamento(identificadorDaMateria, identificadorDoTopico,
                titulo, tipoDeAtividade, novaData, duracaoPrevistaEmMinutos,
                novaOrdem, horarioPrevisto, observacao);
    }

    public BlocoDeEstudo normalizarPosicao(LocalDate novaData, int novaOrdem) {
        return new BlocoDeEstudo(identificador, identificadorDoPlano,
                identificadorDaMateria, identificadorDoTopico, titulo,
                tipoDeAtividade, novaData, duracaoPrevistaEmMinutos, novaOrdem,
                horarioPrevisto, observacao, origem, justificativaDaGeracao,
                estado, quantidadeDeReagendamentos, reagendadoEm, criadoEm,
                OffsetDateTime.now(), versao);
    }

    public BlocoDeEstudo reagendar(LocalDate novaData, LocalTime novoHorario, int novaOrdem) {
        exigirPlanejado();
        OffsetDateTime agora = OffsetDateTime.now();
        return new BlocoDeEstudo(identificador, identificadorDoPlano,
                identificadorDaMateria, identificadorDoTopico, titulo, tipoDeAtividade,
                novaData, duracaoPrevistaEmMinutos, novaOrdem, novoHorario, observacao,
                origemAposAjusteManual(), justificativaDaGeracao, estado,
                quantidadeDeReagendamentos + 1, agora, criadoEm, agora, versao);
    }

    public BlocoDeEstudo cancelar() {
        exigirPlanejado();
        return comEstado(EstadoDoBlocoDeEstudo.CANCELADO);
    }

    public BlocoDeEstudo corrigirResultado(ResultadoDaExecucao resultado) {
        if (estado != EstadoDoBlocoDeEstudo.CONCLUIDO
                && estado != EstadoDoBlocoDeEstudo.PARCIALMENTE_CONCLUIDO) {
            throw new IllegalStateException(
                    "Somente bloco com execução finalizada pode ter o resultado corrigido.");
        }
        Objects.requireNonNull(resultado);
        EstadoDoBlocoDeEstudo novoEstado = resultado == ResultadoDaExecucao.CONCLUIDO
                ? EstadoDoBlocoDeEstudo.CONCLUIDO
                : EstadoDoBlocoDeEstudo.PARCIALMENTE_CONCLUIDO;
        return comEstado(novoEstado);
    }

    public BlocoDeEstudo iniciar() {
        exigirPlanejado();
        return comEstado(EstadoDoBlocoDeEstudo.EM_ANDAMENTO);
    }

    public BlocoDeEstudo concluir() {
        exigirEmAndamento();
        return comEstado(EstadoDoBlocoDeEstudo.CONCLUIDO);
    }

    public BlocoDeEstudo concluirParcialmente() {
        exigirEmAndamento();
        return comEstado(EstadoDoBlocoDeEstudo.PARCIALMENTE_CONCLUIDO);
    }

    public void exigirPlanejado() {
        if (estado != EstadoDoBlocoDeEstudo.PLANEJADO) {
            throw new IllegalStateException("Somente bloco planejado permite esta operacao.");
        }
    }

    public void exigirEmAndamento() {
        if (estado != EstadoDoBlocoDeEstudo.EM_ANDAMENTO) {
            throw new IllegalStateException("Somente bloco em andamento pode ser encerrado.");
        }
    }

    private BlocoDeEstudo comEstado(EstadoDoBlocoDeEstudo novoEstado) {
        return new BlocoDeEstudo(identificador, identificadorDoPlano,
                identificadorDaMateria, identificadorDoTopico, titulo,
                tipoDeAtividade, data, duracaoPrevistaEmMinutos, ordem,
                horarioPrevisto, observacao, origem, justificativaDaGeracao, novoEstado,
                quantidadeDeReagendamentos, reagendadoEm, criadoEm,
                OffsetDateTime.now(), versao);
    }

    private OrigemDoBlocoDeEstudo origemAposAjusteManual() {
        return origem == OrigemDoBlocoDeEstudo.GERADO_DETERMINISTICAMENTE
                ? OrigemDoBlocoDeEstudo.GERADO_AJUSTADO_MANUALMENTE
                : origem;
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
