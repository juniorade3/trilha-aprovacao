package br.com.trilhaaprovacao.conteudos.dominio;

import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

public final class Materia {
    private static final Pattern COR_HEXADECIMAL = Pattern.compile("^#[0-9A-Fa-f]{6}$");

    private final UUID identificador;
    private final UUID identificadorDoUsuario;
    private final OffsetDateTime criadoEm;
    private String nome;
    private String nomeNormalizado;
    private String descricao;
    private String cor;
    private boolean arquivada;
    private OffsetDateTime atualizadoEm;
    private long versao;

    private Materia(UUID identificador, UUID identificadorDoUsuario, String nome, String descricao,
            String cor, boolean arquivada, OffsetDateTime criadoEm, OffsetDateTime atualizadoEm, long versao) {
        this.identificador = identificador;
        this.identificadorDoUsuario = identificadorDoUsuario;
        this.criadoEm = criadoEm;
        this.arquivada = arquivada;
        this.versao = versao;
        alterarDados(nome, descricao, cor, atualizadoEm);
    }

    public static Materia criar(UUID identificadorDoUsuario, String nome, String descricao, String cor) {
        OffsetDateTime agora = OffsetDateTime.now();
        return new Materia(UUID.randomUUID(), identificadorDoUsuario, nome, descricao, cor, false, agora, agora, 0);
    }

    public static Materia reconstituir(UUID identificador, UUID identificadorDoUsuario, String nome,
            String descricao, String cor, boolean arquivada, OffsetDateTime criadoEm,
            OffsetDateTime atualizadoEm, long versao) {
        return new Materia(identificador, identificadorDoUsuario, nome, descricao, cor,
                arquivada, criadoEm, atualizadoEm, versao);
    }

    public void alterar(String nome, String descricao, String cor) {
        if (arquivada) {
            throw new IllegalStateException("Materia arquivada nao pode ser alterada.");
        }
        alterarDados(nome, descricao, cor, OffsetDateTime.now());
    }

    public void definirArquivamento(boolean deveArquivar) {
        this.arquivada = deveArquivar;
        this.atualizadoEm = OffsetDateTime.now();
    }

    private void alterarDados(String nome, String descricao, String cor, OffsetDateTime atualizadoEm) {
        this.nome = NormalizacaoDeTexto.obrigatorio(nome, "nome");
        this.nomeNormalizado = NormalizacaoDeTexto.chave(this.nome);
        this.descricao = NormalizacaoDeTexto.opcional(descricao);
        this.cor = normalizarCor(cor);
        this.atualizadoEm = atualizadoEm;
    }

    private String normalizarCor(String valor) {
        String normalizada = NormalizacaoDeTexto.opcional(valor);
        if (normalizada == null) {
            return null;
        }
        if (!COR_HEXADECIMAL.matcher(normalizada).matches()) {
            throw new IllegalArgumentException("Cor deve usar o formato hexadecimal #RRGGBB.");
        }
        return normalizada.toUpperCase(Locale.ROOT);
    }

    public UUID identificador() { return identificador; }
    public UUID identificadorDoUsuario() { return identificadorDoUsuario; }
    public String nome() { return nome; }
    public String nomeNormalizado() { return nomeNormalizado; }
    public String descricao() { return descricao; }
    public String cor() { return cor; }
    public boolean arquivada() { return arquivada; }
    public OffsetDateTime criadoEm() { return criadoEm; }
    public OffsetDateTime atualizadoEm() { return atualizadoEm; }
    public long versao() { return versao; }
}
