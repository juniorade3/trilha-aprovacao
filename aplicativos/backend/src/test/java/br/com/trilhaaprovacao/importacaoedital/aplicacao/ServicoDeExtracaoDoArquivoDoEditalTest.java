package br.com.trilhaaprovacao.importacaoedital.aplicacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.trilhaaprovacao.importacaoedital.dominio.TipoDaFonteDoEdital;
import br.com.trilhaaprovacao.importacaoedital.infraestrutura.ConfiguracaoDaImportacaoDeEdital;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class ServicoDeExtracaoDoArquivoDoEditalTest {
    private ServicoDeExtracaoDoArquivoDoEdital servico;

    @AfterEach
    void encerrar() {
        if (servico != null) servico.encerrar();
    }

    @Test
    void inspecionaEExtraiTxtUtf8SemConfiarNaExtensao() {
        servico = novoServico(null, null);
        byte[] conteudo = "CONCURSO: Receita Federal\r\n".getBytes(
                StandardCharsets.UTF_8);

        var inspecao = servico.inspecionar("../../edital.exe", conteudo);
        var resultado = servico.extrair(conteudo);

        assertThat(inspecao.nomeDoArquivo()).isEqualTo("edital.exe");
        assertThat(inspecao.tipoMime()).isEqualTo("text/plain");
        assertThat(inspecao.sha256()).matches("[0-9a-f]{64}");
        assertThat(resultado.tipoDaFonte()).isEqualTo(TipoDaFonteDoEdital.TEXTO);
        assertThat(resultado.texto()).isEqualTo("CONCURSO: Receita Federal\n");
        assertThat(resultado.problemas()).extracting("codigo")
                .containsExactly("VERIFICACAO_ANTIMALWARE_INDISPONIVEL");
    }

    @Test
    void rejeitaBinarioQueNaoEArquivoPermitido() {
        servico = novoServico(null, null);
        assertThatThrownBy(() -> servico.inspecionar("arquivo.bin",
                new byte[] {(byte) 0xC3, 0x28}))
                .isInstanceOf(FalhaNaExtracaoDoEdital.class)
                .extracting("codigo").isEqualTo("TIPO_DE_ARQUIVO_INVALIDO");
    }

    @Test
    void rejeitaArquivoGeradoAcimaDoLimite() {
        servico = novoServico(null, null);
        byte[] acimaDoLimite = new byte[1_048_577];

        assertThatThrownBy(() -> servico.inspecionar(
                "edital-grande.txt", acimaDoLimite))
                .isInstanceOf(FalhaNaExtracaoDoEdital.class)
                .extracting("codigo").isEqualTo("ARQUIVO_MUITO_GRANDE");
    }

    @Test
    void extraiPdfTextual() throws Exception {
        servico = novoServico(null, null);
        byte[] pdf = pdf("CONCURSO: Tribunal de Contas");
        var resultado = servico.extrair(pdf);
        assertThat(resultado.tipoDaFonte())
                .isEqualTo(TipoDaFonteDoEdital.PDF_TEXTUAL);
        assertThat(resultado.texto()).contains("Tribunal de Contas");
    }

    @Test
    void bloqueiaPdfDigitalizadoQuandoOcrNaoExiste() throws Exception {
        servico = novoServico(null, null);
        var resultado = servico.extrair(pdf(null));
        assertThat(resultado.tipoDaFonte())
                .isEqualTo(TipoDaFonteDoEdital.PDF_DIGITALIZADO);
        assertThat(resultado.problemas()).extracting("codigo")
                .contains("OCR_INDISPONIVEL");
    }

    @Test
    void verificadorDisponivelRodaNoRecebimentoUmaVez() {
        VerificadorAntimalwareDoEdital antimalware = mock(
                VerificadorAntimalwareDoEdital.class);
        when(antimalware.verificar(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new VerificadorAntimalwareDoEdital
                        .ResultadoDaVerificacao(true, "limpo"));
        servico = novoServico(null, antimalware);
        byte[] texto = "EDITAL: Teste".getBytes(StandardCharsets.UTF_8);
        servico.inspecionar("edital.txt", texto);
        servico.extrair(texto);
        verify(antimalware, times(1)).verificar(texto);
    }

    private static ServicoDeExtracaoDoArquivoDoEdital novoServico(
            ServicoDeOcrDoEdital ocr,
            VerificadorAntimalwareDoEdital antimalware) {
        ConfiguracaoDaImportacaoDeEdital configuracao =
                new ConfiguracaoDaImportacaoDeEdital(1_048_576, 20, 100_000,
                        Duration.ofDays(1), Duration.ofSeconds(5));
        @SuppressWarnings("unchecked")
        ObjectProvider<ServicoDeOcrDoEdital> provedorOcr =
                mock(ObjectProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<VerificadorAntimalwareDoEdital> provedorAntimalware =
                mock(ObjectProvider.class);
        when(provedorOcr.getIfAvailable()).thenReturn(ocr);
        when(provedorAntimalware.getIfAvailable()).thenReturn(antimalware);
        return new ServicoDeExtracaoDoArquivoDoEdital(configuracao,
                provedorOcr, provedorAntimalware);
    }

    private static byte[] pdf(String texto) throws Exception {
        try (PDDocument documento = new PDDocument();
                ByteArrayOutputStream saida = new ByteArrayOutputStream()) {
            PDPage pagina = new PDPage();
            documento.addPage(pagina);
            if (texto != null) {
                try (PDPageContentStream conteudo = new PDPageContentStream(
                        documento, pagina)) {
                    conteudo.beginText();
                    conteudo.setFont(new PDType1Font(
                            Standard14Fonts.FontName.HELVETICA), 12);
                    conteudo.newLineAtOffset(50, 700);
                    conteudo.showText(texto);
                    conteudo.endText();
                }
            }
            documento.save(saida);
            return saida.toByteArray();
        }
    }
}
