package br.com.trilhaaprovacao.importacaoedital.infraestrutura;

import static br.com.trilhaaprovacao.importacaoedital.aplicacao.FalhaNaInterpretacaoAssistidaDoEdital.Codigo.IA_DESABILITADA;
import static br.com.trilhaaprovacao.importacaoedital.aplicacao.FalhaNaInterpretacaoAssistidaDoEdital.Codigo.IA_INDISPONIVEL;
import static br.com.trilhaaprovacao.importacaoedital.aplicacao.FalhaNaInterpretacaoAssistidaDoEdital.Codigo.RECURSO_OCUPADO;
import static br.com.trilhaaprovacao.importacaoedital.aplicacao.FalhaNaInterpretacaoAssistidaDoEdital.Codigo.RESPOSTA_INVALIDA_DA_IA;
import static br.com.trilhaaprovacao.importacaoedital.aplicacao.FalhaNaInterpretacaoAssistidaDoEdital.Codigo.RESPOSTA_RECUSADA_PELA_IA;
import static br.com.trilhaaprovacao.importacaoedital.aplicacao.FalhaNaInterpretacaoAssistidaDoEdital.Codigo.TEMPO_LIMITE_DA_IA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.trilhaaprovacao.importacaoedital.aplicacao.ArvoreInterpretadaDoEdital;
import br.com.trilhaaprovacao.importacaoedital.aplicacao.ArvoreInterpretadaDoEdital.CargoInterpretado;
import br.com.trilhaaprovacao.importacaoedital.aplicacao.ArvoreInterpretadaDoEdital.ConcursoInterpretado;
import br.com.trilhaaprovacao.importacaoedital.aplicacao.ArvoreInterpretadaDoEdital.DadoDecimalInterpretado;
import br.com.trilhaaprovacao.importacaoedital.aplicacao.ArvoreInterpretadaDoEdital.DadoInteiroInterpretado;
import br.com.trilhaaprovacao.importacaoedital.aplicacao.ArvoreInterpretadaDoEdital.DadoTextualInterpretado;
import br.com.trilhaaprovacao.importacaoedital.aplicacao.ArvoreInterpretadaDoEdital.EditalInterpretado;
import br.com.trilhaaprovacao.importacaoedital.aplicacao.ArvoreInterpretadaDoEdital.EvidenciaInterpretada;
import br.com.trilhaaprovacao.importacaoedital.aplicacao.ArvoreInterpretadaDoEdital.GrupoInterpretado;
import br.com.trilhaaprovacao.importacaoedital.aplicacao.ArvoreInterpretadaDoEdital.ItemInterpretado;
import br.com.trilhaaprovacao.importacaoedital.aplicacao.ArvoreInterpretadaDoEdital.MateriaInterpretada;
import br.com.trilhaaprovacao.importacaoedital.aplicacao.ArvoreInterpretadaDoEdital.ProvaInterpretada;
import br.com.trilhaaprovacao.importacaoedital.aplicacao.ArvoreInterpretadaDoEdital.TopicoInterpretado;
import br.com.trilhaaprovacao.importacaoedital.aplicacao.FalhaNaInterpretacaoAssistidaDoEdital;
import br.com.trilhaaprovacao.importacaoedital.aplicacao.ResultadoDaInterpretacaoAssistidaDoEdital;
import br.com.trilhaaprovacao.importacaoedital.aplicacao.SolicitacaoDeInterpretacaoAssistidaDoEdital;
import br.com.trilhaaprovacao.importacaoedital.dominio.TipoDaFonteDoEdital;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class InterpretadorAssistidoDoEditalPelaOpenAiTest {

    private final ObjectMapper json = new ObjectMapper();
    private HttpServer servidor;
    private ExecutorService executor;
    private SimpleMeterRegistry registro;

    @BeforeEach
    void preparar() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newCachedThreadPool();
        servidor.setExecutor(executor);
        registro = new SimpleMeterRegistry();
    }

    @AfterEach
    void encerrar() {
        if (servidor != null) servidor.stop(0);
        if (executor != null) executor.shutdownNow();
        registro.close();
    }

    @Test
    void enviaPdfBase64ComDetalheAltoESchemaEstrito() throws Exception {
        byte[] pdf = "%PDF-1.7\nconteudo".getBytes(StandardCharsets.UTF_8);
        AtomicReference<String> requisicao = new AtomicReference<>();
        AtomicReference<String> autorizacao = new AtomicReference<>();
        responder(200, respostaValida(), requisicao, autorizacao);
        InterpretadorAssistidoDoEditalPelaOpenAi interpretador =
                criarInterpretador(true, Duration.ofSeconds(2));

        ResultadoDaInterpretacaoAssistidaDoEdital resultado =
                interpretador.interpretar(solicitacaoPdf(pdf));

        assertThat(resultado.arvore().cargo().nome().valor())
                .isEqualTo("Engenheiro de Dados");
        assertThat(resultado.uso().totalDeTokens()).isEqualTo(150);
        JsonNode corpo = json.readTree(requisicao.get());
        assertThat(corpo.path("model").asString()).isEqualTo("gpt-5.6-sol");
        assertThat(corpo.path("store").asBoolean()).isFalse();
        assertThat(corpo.at("/reasoning/effort").asString()).isEqualTo("low");
        assertThat(corpo.path("tools").isArray()).isTrue();
        assertThat(corpo.path("tools")).isEmpty();
        assertThat(corpo.at("/text/format/type").asString())
                .isEqualTo("json_schema");
        assertThat(corpo.at("/text/format/strict").asBoolean()).isTrue();
        assertThat(corpo.at("/text/format/schema/additionalProperties")
                .asBoolean()).isFalse();
        assertThat(corpo.at("/text/format/schema/$defs/materia/"
                + "additionalProperties").asBoolean()).isFalse();
        JsonNode arquivo = corpo.at("/input/0/content/1");
        assertThat(arquivo.path("type").asString()).isEqualTo("input_file");
        assertThat(arquivo.path("detail").asString()).isEqualTo("high");
        assertThat(decodificarPdf(arquivo.path("file_data").asString()))
                .isEqualTo(pdf);
        assertThat(autorizacao.get()).isEqualTo("Bearer segredo-de-teste");
        assertThat(registro.get(
                "trilha.importacao_edital.interpretacao_assistida.sucessos")
                .counter().count()).isEqualTo(1);
        assertThat(registro.get(
                "trilha.importacao_edital.interpretacao_assistida.tokens")
                .tag("tipo", "total").summary().totalAmount())
                .isEqualTo(150);
    }

    @Test
    void enviaTxtComoTextoSemInputFile() throws Exception {
        AtomicReference<String> requisicao = new AtomicReference<>();
        responder(200, respostaValida(), requisicao, new AtomicReference<>());
        InterpretadorAssistidoDoEditalPelaOpenAi interpretador =
                criarInterpretador(true, Duration.ofSeconds(2));

        interpretador.interpretar(new SolicitacaoDeInterpretacaoAssistidaDoEdital(
                TipoDaFonteDoEdital.TEXTO,
                "edital.txt",
                null,
                "CARGO: Engenheiro de Dados",
                "Engenheiro de Dados"));

        JsonNode corpo = json.readTree(requisicao.get());
        assertThat(corpo.at("/input/0/content").size()).isEqualTo(1);
        assertThat(corpo.at("/input/0/content/0/type").asString())
                .isEqualTo("input_text");
        assertThat(corpo.at("/input/0/content/0/text").asString())
                .contains("CARGO: Engenheiro de Dados")
                .contains("<documento-nao-confiavel>");
    }

    @Test
    void rejeitaChamadaQuandoRecursoEstaOcupado() throws Exception {
        CountDownLatch chamadaEntrou = new CountDownLatch(1);
        CountDownLatch liberarResposta = new CountDownLatch(1);
        servidor.createContext("/v1/responses", troca -> {
            chamadaEntrou.countDown();
            try {
                liberarResposta.await(2, TimeUnit.SECONDS);
                enviar(troca, 200, respostaValida());
            } catch (InterruptedException excecao) {
                Thread.currentThread().interrupt();
                enviar(troca, 500, "{}");
            }
        });
        servidor.start();
        InterpretadorAssistidoDoEditalPelaOpenAi interpretador =
                criarInterpretador(true, Duration.ofSeconds(3));
        CompletableFuture<ResultadoDaInterpretacaoAssistidaDoEdital> primeira =
                CompletableFuture.supplyAsync(
                        () -> interpretador.interpretar(solicitacaoPdf(
                                "%PDF-a".getBytes(StandardCharsets.UTF_8))));
        assertThat(chamadaEntrou.await(1, TimeUnit.SECONDS)).isTrue();

        assertThatThrownBy(() -> interpretador.interpretar(
                solicitacaoPdf("%PDF-b".getBytes(StandardCharsets.UTF_8))))
                .isInstanceOfSatisfying(
                        FalhaNaInterpretacaoAssistidaDoEdital.class,
                        falha -> assertThat(falha.codigo())
                                .isEqualTo(RECURSO_OCUPADO));

        liberarResposta.countDown();
        assertThat(primeira.get(2, TimeUnit.SECONDS).arvore().cargo()
                .nome().valor()).isEqualTo("Engenheiro de Dados");
    }

    @Test
    void mapeiaRecusaSemExporTextoDaRecusa() throws Exception {
        responder(200, respostaComConteudo(Map.of(
                "type", "refusal",
                "refusal", "conteudo sensivel da recusa")),
                new AtomicReference<>(), new AtomicReference<>());
        InterpretadorAssistidoDoEditalPelaOpenAi interpretador =
                criarInterpretador(true, Duration.ofSeconds(2));

        assertThatThrownBy(() -> interpretador.interpretar(solicitacaoPdf(
                "%PDF".getBytes(StandardCharsets.UTF_8))))
                .isInstanceOfSatisfying(
                        FalhaNaInterpretacaoAssistidaDoEdital.class,
                        falha -> {
                            assertThat(falha.codigo())
                                    .isEqualTo(RESPOSTA_RECUSADA_PELA_IA);
                            assertThat(falha.getMessage())
                                    .doesNotContain("sensivel");
                        });
    }

    @Test
    void rejeitaJsonEstruturadoInvalido() throws Exception {
        responder(200, respostaComConteudo(Map.of(
                "type", "output_text",
                "text", "{nao-json")),
                new AtomicReference<>(), new AtomicReference<>());
        InterpretadorAssistidoDoEditalPelaOpenAi interpretador =
                criarInterpretador(true, Duration.ofSeconds(2));

        assertThatThrownBy(() -> interpretador.interpretar(solicitacaoPdf(
                "%PDF".getBytes(StandardCharsets.UTF_8))))
                .isInstanceOfSatisfying(
                        FalhaNaInterpretacaoAssistidaDoEdital.class,
                        falha -> assertThat(falha.codigo())
                                .isEqualTo(RESPOSTA_INVALIDA_DA_IA));
    }

    @ParameterizedTest
    @ValueSource(ints = {429, 500, 503})
    void mapeiaLimiteEIndisponibilidadeSemRetry(int status) {
        AtomicInteger chamadas = new AtomicInteger();
        servidor.createContext("/v1/responses", troca -> {
            chamadas.incrementAndGet();
            enviar(troca, status, "{\"error\":{\"message\":\"nao registrar\"}}");
        });
        servidor.start();
        InterpretadorAssistidoDoEditalPelaOpenAi interpretador =
                criarInterpretador(true, Duration.ofSeconds(2));

        assertThatThrownBy(() -> interpretador.interpretar(solicitacaoPdf(
                "%PDF".getBytes(StandardCharsets.UTF_8))))
                .isInstanceOfSatisfying(
                        FalhaNaInterpretacaoAssistidaDoEdital.class,
                        falha -> assertThat(falha.codigo())
                                .isEqualTo(IA_INDISPONIVEL));
        assertThat(chamadas).hasValue(1);
    }

    @Test
    void interrompeNoTimeoutConfigurado() {
        servidor.createContext("/v1/responses", troca -> {
            try {
                Thread.sleep(500);
                enviar(troca, 200, respostaValida());
            } catch (InterruptedException excecao) {
                Thread.currentThread().interrupt();
            }
        });
        servidor.start();
        InterpretadorAssistidoDoEditalPelaOpenAi interpretador =
                criarInterpretador(true, Duration.ofMillis(50));

        assertThatThrownBy(() -> interpretador.interpretar(solicitacaoPdf(
                "%PDF".getBytes(StandardCharsets.UTF_8))))
                .isInstanceOfSatisfying(
                        FalhaNaInterpretacaoAssistidaDoEdital.class,
                        falha -> assertThat(falha.codigo())
                                .isEqualTo(TEMPO_LIMITE_DA_IA));
    }

    @Test
    void naoChamaApiQuandoFeatureEstaDesabilitada() {
        AtomicInteger chamadas = new AtomicInteger();
        servidor.createContext("/v1/responses", troca -> {
            chamadas.incrementAndGet();
            enviar(troca, 200, respostaValida());
        });
        servidor.start();
        InterpretadorAssistidoDoEditalPelaOpenAi interpretador =
                criarInterpretador(false, Duration.ofSeconds(2));

        assertThat(interpretador.disponivel()).isFalse();
        assertThatThrownBy(() -> interpretador.interpretar(solicitacaoPdf(
                "%PDF".getBytes(StandardCharsets.UTF_8))))
                .isInstanceOfSatisfying(
                        FalhaNaInterpretacaoAssistidaDoEdital.class,
                        falha -> assertThat(falha.codigo())
                                .isEqualTo(IA_DESABILITADA));
        assertThat(chamadas).hasValue(0);
    }

    private InterpretadorAssistidoDoEditalPelaOpenAi criarInterpretador(
            boolean habilitada, Duration timeout) {
        URI url = URI.create("http://127.0.0.1:"
                + servidor.getAddress().getPort() + "/v1");
        ConfiguracaoDaInterpretacaoAssistidaDoEdital configuracao =
                new ConfiguracaoDaInterpretacaoAssistidaDoEdital(
                        habilitada, "responses-api", url,
                        "segredo-de-teste", "gpt-5.6-sol", timeout,
                        "", "", 20, 144);
        return new InterpretadorAssistidoDoEditalPelaOpenAi(
                configuracao, json,
                new MetricasDaInterpretacaoAssistidaDoEdital(registro));
    }

    private SolicitacaoDeInterpretacaoAssistidaDoEdital solicitacaoPdf(
            byte[] pdf) {
        return new SolicitacaoDeInterpretacaoAssistidaDoEdital(
                TipoDaFonteDoEdital.PDF_DIGITALIZADO,
                "Edital 01/2026.pdf",
                pdf,
                null,
                "Engenheiro de Dados");
    }

    private void responder(int status, String corpo,
            AtomicReference<String> requisicao,
            AtomicReference<String> autorizacao) {
        servidor.createContext("/v1/responses", troca -> {
            requisicao.set(new String(troca.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8));
            autorizacao.set(troca.getRequestHeaders().getFirst("Authorization"));
            enviar(troca, status, corpo);
        });
        servidor.start();
    }

    private void enviar(HttpExchange troca, int status, String corpo)
            throws IOException {
        byte[] bytes = corpo.getBytes(StandardCharsets.UTF_8);
        troca.getResponseHeaders().set("Content-Type", "application/json");
        troca.sendResponseHeaders(status, bytes.length);
        troca.getResponseBody().write(bytes);
        troca.close();
    }

    private String respostaValida() {
        try {
            return respostaComConteudo(Map.of(
                    "type", "output_text",
                    "text", json.writeValueAsString(arvoreValida())));
        } catch (RuntimeException excecao) {
            throw excecao;
        }
    }

    private String respostaComConteudo(Map<String, Object> conteudo) {
        return json.writeValueAsString(Map.of(
                "status", "completed",
                "output", List.of(Map.of(
                        "type", "message",
                        "content", List.of(conteudo))),
                "usage", Map.of(
                        "input_tokens", 100,
                        "output_tokens", 50,
                        "total_tokens", 150)));
    }

    private ArvoreInterpretadaDoEdital arvoreValida() {
        MateriaInterpretada materia = new MateriaInterpretada(
                texto("Banco de Dados"),
                texto("Conteudo especifico"),
                decimal("2.0"),
                inteiro(10),
                decimal("20.0"),
                List.of(new TopicoInterpretado(
                        texto("1"), texto(null), texto("SQL"),
                        texto("Linguagem SQL"))),
                List.of(new ItemInterpretado(
                        texto("1.1"), texto("1"),
                        texto("Consultas e indices"))));
        GrupoInterpretado grupo = new GrupoInterpretado(
                texto("Conhecimentos especificos"),
                inteiro(10),
                decimal("20"),
                decimal("10"),
                List.of(materia));
        ProvaInterpretada prova = new ProvaInterpretada(
                texto("Prova objetiva"),
                texto("OBJETIVA"),
                texto("ELIMINATORIO_E_CLASSIFICATORIO"),
                texto(null),
                inteiro(240),
                inteiro(10),
                decimal("20"),
                decimal("10"),
                List.of(grupo),
                List.of());
        return new ArvoreInterpretadaDoEdital(
                new ConcursoInterpretado(
                        texto("Concurso do Tribunal"),
                        texto(null), texto("Tribunal"), texto("Cebraspe")),
                new EditalInterpretado(
                        texto("Edital 01/2026"), texto("01"),
                        inteiro(2026), texto(null)),
                new CargoInterpretado(
                        texto("Engenheiro de Dados"),
                        texto("Tecnologia da Informacao"),
                        texto("Engenharia de Dados"),
                        texto("SUPERIOR"),
                        List.of(prova)));
    }

    private DadoTextualInterpretado texto(String valor) {
        return new DadoTextualInterpretado(valor,
                new EvidenciaInterpretada(
                        valor == null ? null : 1,
                        valor == null ? null : valor));
    }

    private DadoInteiroInterpretado inteiro(Integer valor) {
        return new DadoInteiroInterpretado(valor,
                new EvidenciaInterpretada(
                        valor == null ? null : 1,
                        valor == null ? null : valor.toString()));
    }

    private DadoDecimalInterpretado decimal(String valor) {
        return new DadoDecimalInterpretado(
                valor == null ? null : new BigDecimal(valor),
                new EvidenciaInterpretada(
                        valor == null ? null : 1, valor));
    }

    private byte[] decodificarPdf(String dado) {
        return Base64.getDecoder().decode(
                dado.substring("data:application/pdf;base64,".length()));
    }
}
