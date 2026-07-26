package br.com.trilhaaprovacao.importacaoedital.infraestrutura;

import br.com.trilhaaprovacao.importacaoedital.dominio.EstadoDaImportacaoDeEdital;
import br.com.trilhaaprovacao.importacaoedital.dominio.ImportacaoDeEdital;
import br.com.trilhaaprovacao.importacaoedital.dominio.ModoDaImportacaoDeEdital;
import br.com.trilhaaprovacao.importacaoedital.dominio.PoliticaDeReutilizacao;
import br.com.trilhaaprovacao.importacaoedital.dominio.TipoDaFonteDoEdital;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.UUID;

@Entity
@Table(name = "importacoes_de_edital")
public class ImportacaoDeEditalPersistida {
    @Id
    private UUID identificador;
    @Column(name = "usuario_id", nullable = false)
    private UUID identificadorDoUsuario;
    @Column(name = "importacao_de_origem_id")
    private UUID identificadorDaImportacaoDeOrigem;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private EstadoDaImportacaoDeEdital estado;
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_da_fonte", nullable = false, length = 20)
    private TipoDaFonteDoEdital tipoDaFonte;
    @Column(name = "nome_do_arquivo", nullable = false, length = 255)
    private String nomeDoArquivo;
    @Column(name = "tipo_mime", nullable = false, length = 100)
    private String tipoMime;
    @Column(nullable = false, length = 64)
    private String sha256;
    @Column(name = "tamanho_em_bytes", nullable = false)
    private long tamanhoEmBytes;
    @Column(name = "quantidade_de_paginas")
    private Integer quantidadeDePaginas;
    @Column(name = "conteudo_original", columnDefinition = "bytea")
    private byte[] conteudoOriginal;
    @Column(name = "texto_extraido", columnDefinition = "text")
    private String textoExtraido;
    @Column(name = "versao_atual_da_extracao", nullable = false)
    private int versaoAtualDaExtracao;
    @Column(name = "hash_da_extracao_atual", length = 64)
    private String hashDaExtracaoAtual;
    @Column(name = "chave_do_cargo_selecionado", length = 160)
    private String chaveDoCargoSelecionado;
    @Enumerated(EnumType.STRING)
    @Column(length = 40)
    private ModoDaImportacaoDeEdital modo;
    @Column(name = "concurso_existente_id")
    private UUID identificadorDoConcursoExistente;
    @Enumerated(EnumType.STRING)
    @Column(name = "politica_de_reutilizacao", length = 40)
    private PoliticaDeReutilizacao politicaDeReutilizacao;
    @Column(name = "operacao_assistida_id")
    private UUID identificadorDaOperacaoAssistida;
    @Column(name = "tentativa_da_preparacao", nullable = false)
    private int tentativaDaPreparacao = 1;
    @Column(name = "codigo_da_falha", length = 120)
    private String codigoDaFalha;
    @Column(name = "descricao_da_falha", length = 1000)
    private String descricaoDaFalha;
    @Column(name = "reter_conteudo_ate", nullable = false)
    private OffsetDateTime reterConteudoAte;
    @Column(name = "aplicado_em")
    private OffsetDateTime aplicadoEm;
    @Column(name = "criado_em", nullable = false)
    private OffsetDateTime criadoEm;
    @Column(name = "atualizado_em", nullable = false)
    private OffsetDateTime atualizadoEm;
    @Version
    private long versao;

    protected ImportacaoDeEditalPersistida() {
    }

    public ImportacaoDeEditalPersistida(ImportacaoDeEdital importacao,
            byte[] conteudoOriginal, OffsetDateTime reterConteudoAte) {
        identificador = importacao.identificador();
        identificadorDoUsuario = importacao.identificadorDoUsuario();
        nomeDoArquivo = importacao.nomeDoArquivo();
        tipoMime = importacao.tipoMime();
        sha256 = importacao.sha256();
        tamanhoEmBytes = importacao.tamanhoEmBytes();
        criadoEm = importacao.criadoEm();
        this.conteudoOriginal = copiar(conteudoOriginal);
        this.reterConteudoAte = reterConteudoAte;
        atualizarDe(importacao);
    }

    public void atualizarDe(ImportacaoDeEdital importacao) {
        if (!identificador.equals(importacao.identificador())
                || !identificadorDoUsuario.equals(
                        importacao.identificadorDoUsuario())) {
            throw new IllegalArgumentException(
                    "Importacao persistida nao corresponde ao dominio.");
        }
        estado = importacao.estado();
        tipoDaFonte = importacao.tipoDaFonte();
        versaoAtualDaExtracao = importacao.versaoAtualDaExtracao();
        hashDaExtracaoAtual = importacao.hashDaExtracaoAtual();
        chaveDoCargoSelecionado = importacao.chaveDoCargoSelecionado();
        aplicadoEm = importacao.aplicadoEm();
        atualizadoEm = importacao.atualizadoEm();
    }

    public void registrarConteudoExtraido(TipoDaFonteDoEdital tipo,
            int paginas, String texto) {
        tipoDaFonte = tipo;
        quantidadeDePaginas = paginas;
        textoExtraido = texto == null || texto.isBlank() ? null : texto;
    }

    public void registrarFalha(String codigo, String descricao) {
        codigoDaFalha = limitar(codigo, 120);
        descricaoDaFalha = limitar(descricao, 1000);
    }

    public void limparFalha() {
        codigoDaFalha = null;
        descricaoDaFalha = null;
    }

    public void definirDecisoes(String chaveDoCargo,
            ModoDaImportacaoDeEdital modo, UUID concursoExistente,
            PoliticaDeReutilizacao politica) {
        chaveDoCargoSelecionado = chaveDoCargo;
        this.modo = modo;
        identificadorDoConcursoExistente = concursoExistente;
        politicaDeReutilizacao = politica;
    }

    public void definirDestinoInicial(ModoDaImportacaoDeEdital modo,
            UUID concursoExistente, PoliticaDeReutilizacao politica,
            OffsetDateTime agora) {
        if (chaveDoCargoSelecionado != null
                || identificadorDaOperacaoAssistida != null) {
            throw new IllegalStateException(
                    "Destino inicial nao pode substituir lote preparado.");
        }
        this.modo = modo;
        identificadorDoConcursoExistente = concursoExistente;
        politicaDeReutilizacao = politica;
        atualizadoEm = agora;
    }

    public void vincularOperacao(UUID operacao) {
        identificadorDaOperacaoAssistida = operacao;
    }

    public void iniciarNovaTentativaDaPreparacao(
            ImportacaoDeEdital importacao) {
        atualizarDe(importacao);
        identificadorDaOperacaoAssistida = null;
        tentativaDaPreparacao++;
    }

    public void definirImportacaoDeOrigem(UUID origem) {
        if (identificador.equals(origem)) {
            throw new IllegalArgumentException(
                    "Importacao nao pode ser origem de si mesma.");
        }
        identificadorDaImportacaoDeOrigem = origem;
    }

    public void descartarConteudoRetido() {
        conteudoOriginal = null;
        textoExtraido = null;
    }

    public void restaurarConteudoRetido(byte[] conteudo,
            OffsetDateTime reterAte, OffsetDateTime agora) {
        if (conteudo == null || conteudo.length < 1 || reterAte == null
                || agora == null || !reterAte.isAfter(agora)) {
            throw new IllegalArgumentException(
                    "Conteudo restaurado da importacao invalido.");
        }
        conteudoOriginal = copiar(conteudo);
        reterConteudoAte = reterAte;
        atualizadoEm = agora;
    }

    public ImportacaoDeEdital paraDominio() {
        return new ImportacaoDeEdital(identificador, identificadorDoUsuario,
                estado, tipoDaFonte, nomeDoArquivo, tipoMime, sha256,
                tamanhoEmBytes, versaoAtualDaExtracao,
                hashDaExtracaoAtual, chaveDoCargoSelecionado, aplicadoEm,
                criadoEm, atualizadoEm);
    }

    public UUID identificador() { return identificador; }
    public UUID identificadorDoUsuario() { return identificadorDoUsuario; }
    public UUID identificadorDaImportacaoDeOrigem() {
        return identificadorDaImportacaoDeOrigem;
    }
    public EstadoDaImportacaoDeEdital estado() { return estado; }
    public TipoDaFonteDoEdital tipoDaFonte() { return tipoDaFonte; }
    public String nomeDoArquivo() { return nomeDoArquivo; }
    public String tipoMime() { return tipoMime; }
    public String sha256() { return sha256; }
    public long tamanhoEmBytes() { return tamanhoEmBytes; }
    public Integer quantidadeDePaginas() { return quantidadeDePaginas; }
    public byte[] conteudoOriginal() { return copiar(conteudoOriginal); }
    public String textoExtraido() { return textoExtraido; }
    public int versaoAtualDaExtracao() { return versaoAtualDaExtracao; }
    public String hashDaExtracaoAtual() { return hashDaExtracaoAtual; }
    public String chaveDoCargoSelecionado() { return chaveDoCargoSelecionado; }
    public ModoDaImportacaoDeEdital modo() { return modo; }
    public UUID identificadorDoConcursoExistente() {
        return identificadorDoConcursoExistente;
    }
    public PoliticaDeReutilizacao politicaDeReutilizacao() {
        return politicaDeReutilizacao;
    }
    public UUID identificadorDaOperacaoAssistida() {
        return identificadorDaOperacaoAssistida;
    }
    public int tentativaDaPreparacao() { return tentativaDaPreparacao; }
    public OffsetDateTime reterConteudoAte() { return reterConteudoAte; }
    public OffsetDateTime aplicadoEm() { return aplicadoEm; }
    public OffsetDateTime criadoEm() { return criadoEm; }
    public OffsetDateTime atualizadoEm() { return atualizadoEm; }
    public String codigoDaFalha() { return codigoDaFalha; }
    public long versao() { return versao; }

    private static byte[] copiar(byte[] valor) {
        return valor == null ? null : Arrays.copyOf(valor, valor.length);
    }

    private static String limitar(String valor, int limite) {
        if (valor == null || valor.isBlank()) return null;
        String texto = valor.strip();
        return texto.length() <= limite ? texto : texto.substring(0, limite);
    }
}
