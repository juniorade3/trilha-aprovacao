package br.com.trilhaaprovacao.importacaoedital.dominio;

import java.time.OffsetDateTime;
import java.util.UUID;

public class ImportacaoDeEdital {
    private final UUID identificador;
    private final UUID identificadorDoUsuario;
    private EstadoDaImportacaoDeEdital estado;
    private TipoDaFonteDoEdital tipoDaFonte;
    private final String nomeDoArquivo;
    private final String tipoMime;
    private final String sha256;
    private final long tamanhoEmBytes;
    private int versaoAtualDaExtracao;
    private String hashDaExtracaoAtual;
    private String chaveDoCargoSelecionado;
    private OffsetDateTime aplicadoEm;
    private final OffsetDateTime criadoEm;
    private OffsetDateTime atualizadoEm;

    public ImportacaoDeEdital(UUID identificador, UUID identificadorDoUsuario,
            EstadoDaImportacaoDeEdital estado, TipoDaFonteDoEdital tipoDaFonte,
            String nomeDoArquivo, String tipoMime, String sha256,
            long tamanhoEmBytes, int versaoAtualDaExtracao,
            String hashDaExtracaoAtual, String chaveDoCargoSelecionado,
            OffsetDateTime aplicadoEm, OffsetDateTime criadoEm,
            OffsetDateTime atualizadoEm) {
        this.identificador = exigir(identificador, "Importacao");
        this.identificadorDoUsuario = exigir(identificadorDoUsuario, "Usuario");
        this.estado = exigir(estado, "Estado");
        this.tipoDaFonte = exigir(tipoDaFonte, "Tipo da fonte");
        this.nomeDoArquivo = exigirTexto(nomeDoArquivo, "Nome do arquivo");
        this.tipoMime = exigirTexto(tipoMime, "MIME");
        this.sha256 = exigirHash(sha256, "SHA-256");
        if (tamanhoEmBytes < 1) throw new IllegalArgumentException(
                "Arquivo deve possuir conteudo.");
        this.tamanhoEmBytes = tamanhoEmBytes;
        this.versaoAtualDaExtracao = versaoAtualDaExtracao;
        this.hashDaExtracaoAtual = hashDaExtracaoAtual == null
                ? null : exigirHash(hashDaExtracaoAtual, "Hash da extracao");
        this.chaveDoCargoSelecionado = chaveDoCargoSelecionado;
        this.aplicadoEm = aplicadoEm;
        this.criadoEm = exigir(criadoEm, "Criacao");
        this.atualizadoEm = exigir(atualizadoEm, "Atualizacao");
    }

    public static ImportacaoDeEdital receber(UUID usuario,
            TipoDaFonteDoEdital tipo, String nome, String mime, String sha256,
            long tamanho, OffsetDateTime agora) {
        return new ImportacaoDeEdital(UUID.randomUUID(), usuario,
                EstadoDaImportacaoDeEdital.RECEBIDA, tipo, nome, mime, sha256,
                tamanho, 0, null, null, null, agora, agora);
    }

    public void iniciarExtracao(OffsetDateTime agora) {
        exigirEstado(EstadoDaImportacaoDeEdital.RECEBIDA,
                EstadoDaImportacaoDeEdital.FALHOU);
        estado = EstadoDaImportacaoDeEdital.EXTRAINDO;
        atualizadoEm = exigir(agora, "Atualizacao");
    }

    public void reiniciarExtracao(OffsetDateTime agora) {
        exigirEstado(EstadoDaImportacaoDeEdital.EXTRAIDA,
                EstadoDaImportacaoDeEdital.AGUARDANDO_SELECAO,
                EstadoDaImportacaoDeEdital.AGUARDANDO_CORRECOES,
                EstadoDaImportacaoDeEdital.VALIDADA,
                EstadoDaImportacaoDeEdital.FALHOU);
        chaveDoCargoSelecionado = null;
        estado = EstadoDaImportacaoDeEdital.EXTRAINDO;
        atualizadoEm = exigir(agora, "Atualizacao");
    }

    public void classificarFonte(TipoDaFonteDoEdital tipo,
            OffsetDateTime agora) {
        exigirEstado(EstadoDaImportacaoDeEdital.RECEBIDA,
                EstadoDaImportacaoDeEdital.EXTRAINDO);
        tipoDaFonte = exigir(tipo, "Tipo da fonte");
        atualizadoEm = exigir(agora, "Atualizacao");
    }

    public void registrarExtracao(int versao, String hash,
            EstadoDaImportacaoDeEdital proximoEstado, OffsetDateTime agora) {
        if (estado != EstadoDaImportacaoDeEdital.EXTRAINDO
                || versao != versaoAtualDaExtracao + 1
                || !java.util.Set.of(EstadoDaImportacaoDeEdital.EXTRAIDA,
                        EstadoDaImportacaoDeEdital.AGUARDANDO_SELECAO,
                        EstadoDaImportacaoDeEdital.AGUARDANDO_CORRECOES,
                        EstadoDaImportacaoDeEdital.VALIDADA).contains(proximoEstado)) {
            throw new IllegalStateException("Transicao da extracao invalida.");
        }
        versaoAtualDaExtracao = versao;
        hashDaExtracaoAtual = exigirHash(hash, "Hash da extracao");
        estado = proximoEstado;
        atualizadoEm = agora;
    }

    public void selecionarCargo(String chave, boolean possuiBloqueante,
            boolean exigeDecisao, OffsetDateTime agora) {
        if (!java.util.Set.of(EstadoDaImportacaoDeEdital.EXTRAIDA,
                EstadoDaImportacaoDeEdital.AGUARDANDO_SELECAO,
                EstadoDaImportacaoDeEdital.AGUARDANDO_CORRECOES,
                EstadoDaImportacaoDeEdital.VALIDADA).contains(estado)) {
            throw new IllegalStateException("Importacao nao aceita selecao de cargo.");
        }
        chaveDoCargoSelecionado = exigirTexto(chave, "Cargo selecionado");
        estado = possuiBloqueante || exigeDecisao
                ? EstadoDaImportacaoDeEdital.AGUARDANDO_CORRECOES
                : EstadoDaImportacaoDeEdital.VALIDADA;
        atualizadoEm = agora;
    }

    public void aguardarConfirmacao(OffsetDateTime agora) {
        exigirEstado(EstadoDaImportacaoDeEdital.VALIDADA,
                EstadoDaImportacaoDeEdital.AGUARDANDO_CONFIRMACAO);
        estado = EstadoDaImportacaoDeEdital.AGUARDANDO_CONFIRMACAO;
        atualizadoEm = agora;
    }

    public void retomarPreparacao(OffsetDateTime agora) {
        exigirEstado(EstadoDaImportacaoDeEdital.AGUARDANDO_CONFIRMACAO,
                EstadoDaImportacaoDeEdital.FALHOU);
        estado = EstadoDaImportacaoDeEdital.VALIDADA;
        atualizadoEm = exigir(agora, "Atualizacao");
    }

    public void iniciarAplicacao(OffsetDateTime agora) {
        exigirEstado(EstadoDaImportacaoDeEdital.AGUARDANDO_CONFIRMACAO);
        estado = EstadoDaImportacaoDeEdital.APLICANDO;
        atualizadoEm = agora;
    }

    public void concluirAplicacao(OffsetDateTime agora) {
        exigirEstado(EstadoDaImportacaoDeEdital.APLICANDO);
        estado = EstadoDaImportacaoDeEdital.APLICADA;
        aplicadoEm = agora;
        atualizadoEm = agora;
    }

    public void falhar(String codigo, OffsetDateTime agora) {
        if (estado == EstadoDaImportacaoDeEdital.APLICADA
                || estado == EstadoDaImportacaoDeEdital.CANCELADA) {
            throw new IllegalStateException("Importacao concluida nao pode falhar.");
        }
        exigirTexto(codigo, "Codigo da falha");
        estado = EstadoDaImportacaoDeEdital.FALHOU;
        atualizadoEm = exigir(agora, "Atualizacao");
    }

    public void cancelar(OffsetDateTime agora) {
        if (estado == EstadoDaImportacaoDeEdital.APLICADA) {
            throw new IllegalStateException(
                    "Importacao aplicada nao pode ser cancelada.");
        }
        estado = EstadoDaImportacaoDeEdital.CANCELADA;
        atualizadoEm = exigir(agora, "Atualizacao");
    }

    private void exigirEstado(EstadoDaImportacaoDeEdital... permitidos) {
        if (java.util.Arrays.stream(permitidos).noneMatch(estado::equals)) {
            throw new IllegalStateException("Estado da importacao incompativel.");
        }
    }

    private static <T> T exigir(T valor, String nome) {
        if (valor == null) throw new IllegalArgumentException(nome + " obrigatorio.");
        return valor;
    }

    private static String exigirTexto(String valor, String nome) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(nome + " obrigatorio.");
        }
        return valor.strip();
    }

    private static String exigirHash(String valor, String nome) {
        String hash = exigirTexto(valor, nome);
        if (!hash.matches("^[0-9a-f]{64}$")) {
            throw new IllegalArgumentException(nome + " invalido.");
        }
        return hash;
    }

    public UUID identificador() { return identificador; }
    public UUID identificadorDoUsuario() { return identificadorDoUsuario; }
    public EstadoDaImportacaoDeEdital estado() { return estado; }
    public TipoDaFonteDoEdital tipoDaFonte() { return tipoDaFonte; }
    public String nomeDoArquivo() { return nomeDoArquivo; }
    public String tipoMime() { return tipoMime; }
    public String sha256() { return sha256; }
    public long tamanhoEmBytes() { return tamanhoEmBytes; }
    public int versaoAtualDaExtracao() { return versaoAtualDaExtracao; }
    public String hashDaExtracaoAtual() { return hashDaExtracaoAtual; }
    public String chaveDoCargoSelecionado() { return chaveDoCargoSelecionado; }
    public OffsetDateTime aplicadoEm() { return aplicadoEm; }
    public OffsetDateTime criadoEm() { return criadoEm; }
    public OffsetDateTime atualizadoEm() { return atualizadoEm; }
}
