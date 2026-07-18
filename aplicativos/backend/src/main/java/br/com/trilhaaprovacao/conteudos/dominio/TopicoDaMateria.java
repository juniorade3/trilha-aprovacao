package br.com.trilhaaprovacao.conteudos.dominio;

import java.time.OffsetDateTime;
import java.util.UUID;

public final class TopicoDaMateria {
    private final UUID identificador;
    private final UUID identificadorDaMateria;
    private final OffsetDateTime criadoEm;
    private UUID identificadorDoTopicoPai;
    private String nome;
    private String nomeNormalizado;
    private String descricao;
    private int ordem;
    private boolean arquivado;
    private OffsetDateTime atualizadoEm;
    private long versao;

    private TopicoDaMateria(UUID identificador, UUID identificadorDaMateria, UUID identificadorDoTopicoPai,
            String nome, String descricao, int ordem, boolean arquivado, OffsetDateTime criadoEm,
            OffsetDateTime atualizadoEm, long versao) {
        this.identificador = identificador;
        this.identificadorDaMateria = identificadorDaMateria;
        this.criadoEm = criadoEm;
        this.arquivado = arquivado;
        this.versao = versao;
        alterarDados(nome, descricao, identificadorDoTopicoPai, ordem, atualizadoEm);
    }

    public static TopicoDaMateria criar(UUID identificadorDaMateria, UUID identificadorDoTopicoPai,
            String nome, String descricao, int ordem) {
        OffsetDateTime agora = OffsetDateTime.now();
        return new TopicoDaMateria(UUID.randomUUID(), identificadorDaMateria, identificadorDoTopicoPai,
                nome, descricao, ordem, false, agora, agora, 0);
    }

    public static TopicoDaMateria reconstituir(UUID identificador, UUID identificadorDaMateria,
            UUID identificadorDoTopicoPai, String nome, String descricao, int ordem, boolean arquivado,
            OffsetDateTime criadoEm, OffsetDateTime atualizadoEm, long versao) {
        return new TopicoDaMateria(identificador, identificadorDaMateria, identificadorDoTopicoPai,
                nome, descricao, ordem, arquivado, criadoEm, atualizadoEm, versao);
    }

    public void alterar(String nome, String descricao, UUID identificadorDoTopicoPai, int ordem) {
        if (arquivado) {
            throw new IllegalStateException("Topico arquivado nao pode ser alterado.");
        }
        alterarDados(nome, descricao, identificadorDoTopicoPai, ordem, OffsetDateTime.now());
    }

    public void definirArquivamento(boolean deveArquivar) {
        this.arquivado = deveArquivar;
        this.atualizadoEm = OffsetDateTime.now();
    }

    private void alterarDados(String nome, String descricao, UUID identificadorDoTopicoPai,
            int ordem, OffsetDateTime atualizadoEm) {
        if (identificador.equals(identificadorDoTopicoPai)) {
            throw new IllegalArgumentException("Topico nao pode ser pai de si mesmo.");
        }
        if (ordem < 1) {
            throw new IllegalArgumentException("Ordem deve ser positiva.");
        }
        this.nome = NormalizacaoDeTexto.obrigatorio(nome, "nome");
        this.nomeNormalizado = NormalizacaoDeTexto.chave(this.nome);
        this.descricao = NormalizacaoDeTexto.opcional(descricao);
        this.identificadorDoTopicoPai = identificadorDoTopicoPai;
        this.ordem = ordem;
        this.atualizadoEm = atualizadoEm;
    }

    public UUID identificador() { return identificador; }
    public UUID identificadorDaMateria() { return identificadorDaMateria; }
    public UUID identificadorDoTopicoPai() { return identificadorDoTopicoPai; }
    public String nome() { return nome; }
    public String nomeNormalizado() { return nomeNormalizado; }
    public String descricao() { return descricao; }
    public int ordem() { return ordem; }
    public boolean arquivado() { return arquivado; }
    public OffsetDateTime criadoEm() { return criadoEm; }
    public OffsetDateTime atualizadoEm() { return atualizadoEm; }
    public long versao() { return versao; }
}
