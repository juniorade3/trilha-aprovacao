package br.com.trilhaaprovacao.importacaoedital.aplicacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.trilhaaprovacao.importacaoedital.aplicacao.ArvoreInterpretadaDoEdital.CargoInterpretado;
import br.com.trilhaaprovacao.importacaoedital.aplicacao.ArvoreInterpretadaDoEdital.ConcursoInterpretado;
import br.com.trilhaaprovacao.importacaoedital.aplicacao.ArvoreInterpretadaDoEdital.DadoInteiroInterpretado;
import br.com.trilhaaprovacao.importacaoedital.aplicacao.ArvoreInterpretadaDoEdital.DadoTextualInterpretado;
import br.com.trilhaaprovacao.importacaoedital.aplicacao.ArvoreInterpretadaDoEdital.EditalInterpretado;
import br.com.trilhaaprovacao.importacaoedital.aplicacao.ArvoreInterpretadaDoEdital.EvidenciaInterpretada;
import br.com.trilhaaprovacao.importacaoedital.dominio.ExtracaoEstruturadaDoEdital.FonteDoEdital;
import br.com.trilhaaprovacao.importacaoedital.dominio.TipoDaFonteDoEdital;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class ServicoDeInterpretacaoAssistidaDoEditalTest {

    @Test
    void mantemRecursoOcupadoAtePersistirResultadoDaPrimeiraChamada()
            throws Exception {
        ServicoDeStagingDaImportacaoDeEdital staging = mock(
                ServicoDeStagingDaImportacaoDeEdital.class);
        InterpretadorAssistidoDoEdital interpretador = mock(
                InterpretadorAssistidoDoEdital.class);
        when(interpretador.disponivel()).thenReturn(true);
        String texto = """
                CONCURSO: Tribunal
                EDITAL: Edital 1
                CARGO: Analista
                """;
        var extracao = new ParserDeterministicoDoEdital().extrair(texto,
                new FonteDoEdital("edital.txt", "a".repeat(64), 1));
        String cargo = extracao.cargos().getFirst().chave();
        UUID usuario = UUID.randomUUID();
        UUID importacao = UUID.randomUUID();
        when(staging.obterFonteRetida(usuario, importacao, 1)).thenReturn(
                new FonteRetidaDaImportacaoDoEdital(1,
                        TipoDaFonteDoEdital.TEXTO, "edital.txt",
                        texto.getBytes(StandardCharsets.UTF_8), texto,
                        extracao));
        when(interpretador.interpretar(any())).thenReturn(
                new ResultadoDaInterpretacaoAssistidaDoEdital(
                        arvoreMinima(), null));
        CountDownLatch entrouNaPersistencia = new CountDownLatch(1);
        CountDownLatch liberarPersistencia = new CountDownLatch(1);
        ResultadoDoStagingDaImportacao persistido = mock(
                ResultadoDoStagingDaImportacao.class);
        when(staging.registrarInterpretacaoAssistida(eq(usuario),
                eq(importacao), eq(1), any(), any())).thenAnswer(invocacao -> {
                    entrouNaPersistencia.countDown();
                    if (!liberarPersistencia.await(5, TimeUnit.SECONDS)) {
                        throw new AssertionError("Persistencia nao liberada.");
                    }
                    return persistido;
                });
        var servico = new ServicoDeInterpretacaoAssistidaDoEdital(
                staging, interpretador);

        try (var executor = Executors.newSingleThreadExecutor()) {
            var primeira = executor.submit(() -> servico.interpretar(
                    usuario, importacao, 1, cargo, null));
            assertThat(entrouNaPersistencia.await(5, TimeUnit.SECONDS))
                    .isTrue();

            assertThatThrownBy(() -> servico.interpretar(
                    usuario, importacao, 1, cargo, null))
                    .isInstanceOfSatisfying(
                            FalhaNaInterpretacaoAssistidaDoEdital.class,
                            falha -> assertThat(falha.codigo()).isEqualTo(
                                    FalhaNaInterpretacaoAssistidaDoEdital
                                            .Codigo.RECURSO_OCUPADO));
            verify(interpretador, times(1)).interpretar(any());

            liberarPersistencia.countDown();
            assertThat(primeira.get(5, TimeUnit.SECONDS))
                    .isSameAs(persistido);
        }
    }

    private ArvoreInterpretadaDoEdital arvoreMinima() {
        DadoTextualInterpretado ausente = new DadoTextualInterpretado(
                null, new EvidenciaInterpretada(null, null));
        DadoTextualInterpretado nome = new DadoTextualInterpretado(
                "Analista", new EvidenciaInterpretada(1,
                        "CARGO: Analista"));
        return new ArvoreInterpretadaDoEdital(
                new ConcursoInterpretado(ausente, ausente, ausente, ausente),
                new EditalInterpretado(ausente, ausente,
                        new DadoInteiroInterpretado(null, null), ausente),
                new CargoInterpretado(nome, ausente, ausente, ausente,
                        List.of()));
    }
}
