package br.com.trilhaaprovacao.importacaoedital.infraestrutura;

import static br.com.trilhaaprovacao.importacaoedital.aplicacao.FalhaNaInterpretacaoAssistidaDoEdital.Codigo.IA_INDISPONIVEL;
import static br.com.trilhaaprovacao.importacaoedital.aplicacao.FalhaNaInterpretacaoAssistidaDoEdital.Codigo.LIMITE_DE_PAGINAS_RENDERIZADAS_EXCEDIDO;
import static br.com.trilhaaprovacao.importacaoedital.aplicacao.FalhaNaInterpretacaoAssistidaDoEdital.Codigo.RECURSO_OCUPADO;
import static br.com.trilhaaprovacao.importacaoedital.aplicacao.FalhaNaInterpretacaoAssistidaDoEdital.Codigo.RESPOSTA_INVALIDA_DA_IA;
import static br.com.trilhaaprovacao.importacaoedital.aplicacao.FalhaNaInterpretacaoAssistidaDoEdital.Codigo.TEMPO_LIMITE_DA_IA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.trilhaaprovacao.importacaoedital.aplicacao.FalhaNaInterpretacaoAssistidaDoEdital;
import br.com.trilhaaprovacao.importacaoedital.aplicacao.ResultadoDaInterpretacaoAssistidaDoEdital;
import br.com.trilhaaprovacao.importacaoedital.aplicacao.SolicitacaoDeInterpretacaoAssistidaDoEdital;
import br.com.trilhaaprovacao.importacaoedital.dominio.TipoDaFonteDoEdital;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@EnabledOnOs(OS.LINUX)
class InterpretadorAssistidoDoEditalPeloCodexTest {
    private final ObjectMapper json = new ObjectMapper();

    @TempDir
    Path temporario;

    private Path codexHome;
    private SimpleMeterRegistry registro;

    @BeforeEach
    void preparar() throws Exception {
        codexHome = Files.createDirectory(temporario.resolve("codex-home"));
        Files.writeString(codexHome.resolve("auth.json"),
                "{\"autenticado\":true}", StandardCharsets.UTF_8);
        registro = new SimpleMeterRegistry();
    }

    @AfterEach
    void encerrar() {
        registro.close();
    }

    @Test
    void executaComFlagsSegurasPromptNoStdinESchemaEstrito()
            throws Exception {
        Capturas capturas = criarExecutavel(arvoreValida(), "", 0, 0);
        var interpretador = criarInterpretador(
                capturas.executavel(), Duration.ofSeconds(3), 20);
        String documento = "CARGO: Engenheiro de Dados\nBANCO DE DADOS";

        ResultadoDaInterpretacaoAssistidaDoEdital resultado =
                interpretador.interpretar(solicitacaoTextual(documento));

        assertThat(resultado.arvore().cargo().nome().valor())
                .isEqualTo("Engenheiro de Dados");
        assertThat(resultado.uso().tokensDeEntrada()).isEqualTo(10);
        assertThat(resultado.uso().tokensDeSaida()).isEqualTo(5);
        assertThat(resultado.uso().totalDeTokens()).isEqualTo(15);
        List<String> argumentos = Files.readAllLines(capturas.argumentos());
        assertThat(argumentos).containsSubsequence(
                "exec", "--ephemeral", "--ignore-user-config",
                "--ignore-rules", "--skip-git-repo-check",
                "--sandbox", "read-only", "--json", "--strict-config");
        assertThat(argumentos).contains(
                "shell_tool", "unified_exec", "apps", "multi_agent",
                "hooks", "browser_use", "browser_use_external",
                "browser_use_full_cdp_access", "remote_plugin",
                "image_generation", "goals",
                "approval_policy=\"never\"", "web_search=\"disabled\"",
                "model_reasoning_effort=\"low\"", "mcp_servers={}",
                "--output-last-message", "--output-schema", "-");
        assertThat(String.join("\n", argumentos))
                .doesNotContain(documento)
                .doesNotContain("Engenheiro de Dados");
        String prompt = Files.readString(capturas.stdin());
        assertThat(prompt).contains("<documento-nao-confiavel>")
                .contains(documento)
                .contains("Cargo alvo: Engenheiro de Dados");
        assertThat(ocorrencias(prompt, documento)).isEqualTo(1);
        JsonNode esquema = json.readTree(
                Files.readString(capturas.esquema()));
        assertThat(esquema.path("additionalProperties").asBoolean()).isFalse();
        assertThat(esquema.at("/$defs/cargo/additionalProperties")
                .asBoolean()).isFalse();
        assertThat(registro.get(
                "trilha.importacao_edital.interpretacao_assistida.tokens")
                .tag("tipo", "total").summary().totalAmount()).isEqualTo(15);
    }

    @Test
    void rejeitaEventoDeFerramentaMesmoComSaidaValida() throws Exception {
        String evento = """
                {"type":"item.completed","item":{"type":"command_execution"}}
                """.strip();
        Capturas capturas = criarExecutavel(arvoreValida(), evento, 0, 0);
        var interpretador = criarInterpretador(
                capturas.executavel(), Duration.ofSeconds(3), 20);

        assertThatThrownBy(() -> interpretador.interpretar(
                solicitacaoTextual("CARGO: Engenheiro de Dados")))
                .isInstanceOfSatisfying(
                        FalhaNaInterpretacaoAssistidaDoEdital.class,
                        falha -> assertThat(falha.codigo())
                                .isEqualTo(RESPOSTA_INVALIDA_DA_IA));
    }

    @Test
    void encerraProcessoNoTimeoutSemRetry() throws Exception {
        Capturas capturas = criarExecutavel(arvoreValida(), "", 5, 0);
        var interpretador = criarInterpretador(
                capturas.executavel(), Duration.ofMillis(100), 20);

        assertThatThrownBy(() -> interpretador.interpretar(
                solicitacaoTextual("CARGO: Engenheiro de Dados")))
                .isInstanceOfSatisfying(
                        FalhaNaInterpretacaoAssistidaDoEdital.class,
                        falha -> assertThat(falha.codigo())
                                .isEqualTo(TEMPO_LIMITE_DA_IA));
        assertThat(Files.readString(capturas.execucoes()).lines().count())
                .isEqualTo(1);
    }

    @Test
    void renderizaPdfDigitalizadoEmPngEUsaImagem() throws Exception {
        Capturas capturas = criarExecutavel(arvoreValida(), "", 0, 0);
        var interpretador = criarInterpretador(
                capturas.executavel(), Duration.ofSeconds(3), 2);

        interpretador.interpretar(solicitacaoDigitalizada(pdfComPaginas(1)));

        List<String> argumentos = Files.readAllLines(capturas.argumentos());
        assertThat(argumentos).contains("-i");
        assertThat(ImageIO.read(capturas.imagem().toFile())).isNotNull();
        assertThat(Files.readString(capturas.stdin()))
                .contains("imagens anexadas")
                .doesNotContain("<documento-nao-confiavel>");
    }

    @Test
    void recusaPdfDigitalizadoAcimaDoLimiteSemIniciarCodex()
            throws Exception {
        Capturas capturas = criarExecutavel(arvoreValida(), "", 0, 0);
        var interpretador = criarInterpretador(
                capturas.executavel(), Duration.ofSeconds(3), 1);

        assertThatThrownBy(() -> interpretador.interpretar(
                solicitacaoDigitalizada(pdfComPaginas(2))))
                .isInstanceOfSatisfying(
                        FalhaNaInterpretacaoAssistidaDoEdital.class,
                        falha -> assertThat(falha.codigo()).isEqualTo(
                                LIMITE_DE_PAGINAS_RENDERIZADAS_EXCEDIDO));
        assertThat(capturas.execucoes()).doesNotExist();
    }

    @Test
    void mapeiaJsonInvalidoESaidaNaoZero() throws Exception {
        Capturas jsonInvalido = criarExecutavel("{invalido", "", 0, 0);
        var primeiro = criarInterpretador(
                jsonInvalido.executavel(), Duration.ofSeconds(3), 20);
        assertThatThrownBy(() -> primeiro.interpretar(
                solicitacaoTextual("CARGO: Engenheiro de Dados")))
                .isInstanceOfSatisfying(
                        FalhaNaInterpretacaoAssistidaDoEdital.class,
                        falha -> assertThat(falha.codigo())
                                .isEqualTo(RESPOSTA_INVALIDA_DA_IA));

        Capturas falha = criarExecutavel(arvoreValida(), "", 0, 7);
        var segundo = criarInterpretador(
                falha.executavel(), Duration.ofSeconds(3), 20);
        assertThatThrownBy(() -> segundo.interpretar(
                solicitacaoTextual("CARGO: Engenheiro de Dados")))
                .isInstanceOfSatisfying(
                        FalhaNaInterpretacaoAssistidaDoEdital.class,
                        erro -> assertThat(erro.codigo())
                                .isEqualTo(IA_INDISPONIVEL));
    }

    @Test
    void rejeitaChamadaConcorrente() throws Exception {
        Capturas capturas = criarExecutavel(arvoreValida(), "", 1, 0);
        var interpretador = criarInterpretador(
                capturas.executavel(), Duration.ofSeconds(3), 20);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var primeira = executor.submit(() -> interpretador.interpretar(
                    solicitacaoTextual("CARGO: Engenheiro de Dados")));
            aguardarArquivo(capturas.stdin());

            assertThatThrownBy(() -> interpretador.interpretar(
                    solicitacaoTextual("CARGO: Outro")))
                    .isInstanceOfSatisfying(
                            FalhaNaInterpretacaoAssistidaDoEdital.class,
                            falha -> assertThat(falha.codigo())
                                    .isEqualTo(RECURSO_OCUPADO));
            assertThat(primeira.get(3, TimeUnit.SECONDS).arvore()
                    .cargo().nome().valor()).isEqualTo("Engenheiro de Dados");
        }
    }

    private InterpretadorAssistidoDoEditalPeloCodex criarInterpretador(
            Path executavel, Duration timeout, int limiteDePaginas) {
        var configuracao = new ConfiguracaoDaInterpretacaoAssistidaDoEdital(
                true, "codex-cli", URI.create("https://api.openai.com/v1"),
                "", "gpt-5.6-sol", timeout, executavel.toString(),
                codexHome.toString(), limiteDePaginas, 72);
        return new InterpretadorAssistidoDoEditalPeloCodex(
                configuracao, json,
                new MetricasDaInterpretacaoAssistidaDoEdital(registro));
    }

    private Capturas criarExecutavel(String resposta, String eventoExtra,
            int esperaEmSegundos, int codigoDeSaida) throws Exception {
        Path pasta = Files.createDirectory(temporario.resolve(
                "fake-" + Files.list(temporario).count()));
        Path executavel = pasta.resolve("codex-fake");
        Path respostaDoFake = pasta.resolve("resposta-do-fake.json");
        Path argumentos = pasta.resolve("argumentos.txt");
        Path stdin = pasta.resolve("stdin.txt");
        Path esquema = pasta.resolve("schema.json");
        Path imagem = pasta.resolve("imagem.png");
        Path execucoes = pasta.resolve("execucoes.txt");
        Files.writeString(respostaDoFake, resposta, StandardCharsets.UTF_8);
        String script = """
                #!/bin/sh
                printf '1\\n' >> %s
                : > %s
                saida=''
                while [ "$#" -gt 0 ]; do
                  printf '%%s\\n' "$1" >> %s
                  case "$1" in
                    --output-last-message)
                      shift
                      printf '%%s\\n' "$1" >> %s
                      saida="$1"
                      ;;
                    --output-schema)
                      shift
                      printf '%%s\\n' "$1" >> %s
                      /bin/cp "$1" %s
                      ;;
                    -i)
                      shift
                      printf '%%s\\n' "$1" >> %s
                      /bin/cp "$1" %s
                      ;;
                  esac
                  shift
                done
                /bin/cat > %s
                /bin/sleep %d
                if [ %d -ne 0 ]; then exit %d; fi
                /bin/cp %s "$saida"
                printf '%%s\\n' '{"type":"thread.started"}'
                printf '%%s\\n' '{"type":"turn.started"}'
                printf '%%s\\n' '{"type":"item.completed","item":{"type":"agent_message"}}'
                %s
                printf '%%s\\n' '{"type":"turn.completed","usage":{"input_tokens":10,"cached_input_tokens":2,"output_tokens":5}}'
                """.formatted(
                escapar(execucoes), escapar(argumentos), escapar(argumentos),
                escapar(argumentos), escapar(argumentos), escapar(esquema),
                escapar(argumentos), escapar(imagem), escapar(stdin),
                esperaEmSegundos, codigoDeSaida, codigoDeSaida,
                escapar(respostaDoFake),
                eventoExtra.isBlank() ? ":"
                        : "printf '%s\\n' " + escaparTexto(eventoExtra));
        Files.writeString(executavel, script, StandardCharsets.UTF_8);
        Files.setPosixFilePermissions(executavel,
                PosixFilePermissions.fromString("rwx------"));
        return new Capturas(executavel, argumentos, stdin, esquema,
                imagem, execucoes);
    }

    private String arvoreValida() {
        Map<String, Object> ausente = dado(null);
        Map<String, Object> raiz = Map.of(
                "concurso", Map.of(
                        "nome", ausente, "descricao", ausente,
                        "orgao", ausente, "banca", ausente),
                "edital", Map.of(
                        "titulo", ausente, "numero", ausente,
                        "ano", dado(null),
                        "descricao", ausente),
                "cargo", Map.of(
                        "nome", dado("Engenheiro de Dados"),
                        "area", ausente, "especialidade", ausente,
                        "nivelDeEscolaridade", ausente,
                        "provas", List.of()));
        return json.writeValueAsString(raiz);
    }

    private Map<String, Object> dado(String valor) {
        Map<String, Object> dado = new LinkedHashMap<>();
        dado.put("valor", valor);
        dado.put("evidencia", evidencia(valor));
        return dado;
    }

    private Map<String, Object> evidencia(String trecho) {
        Map<String, Object> evidencia = new LinkedHashMap<>();
        evidencia.put("pagina", trecho == null ? null : 1);
        evidencia.put("trecho", trecho);
        return evidencia;
    }

    private SolicitacaoDeInterpretacaoAssistidaDoEdital solicitacaoTextual(
            String texto) {
        return new SolicitacaoDeInterpretacaoAssistidaDoEdital(
                TipoDaFonteDoEdital.TEXTO, "edital.txt", null, texto,
                "Engenheiro de Dados");
    }

    private SolicitacaoDeInterpretacaoAssistidaDoEdital
            solicitacaoDigitalizada(byte[] pdf) {
        return new SolicitacaoDeInterpretacaoAssistidaDoEdital(
                TipoDaFonteDoEdital.PDF_DIGITALIZADO, "edital.pdf",
                pdf, null, "Engenheiro de Dados");
    }

    private byte[] pdfComPaginas(int quantidade) throws Exception {
        try (PDDocument documento = new PDDocument();
                ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            for (int indice = 0; indice < quantidade; indice++) {
                documento.addPage(new PDPage());
            }
            documento.save(bytes);
            return bytes.toByteArray();
        }
    }

    private void aguardarArquivo(Path arquivo) throws Exception {
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while ((!Files.exists(arquivo) || Files.size(arquivo) == 0)
                && System.nanoTime() < limite) {
            Thread.sleep(10);
        }
        assertThat(arquivo).exists();
    }

    private static int ocorrencias(String texto, String trecho) {
        return (texto.length() - texto.replace(trecho, "").length())
                / trecho.length();
    }

    private static String escapar(Path caminho) {
        return escaparTexto(caminho.toString());
    }

    private static String escaparTexto(String valor) {
        return "'" + valor.replace("'", "'\\''") + "'";
    }

    private record Capturas(
            Path executavel,
            Path argumentos,
            Path stdin,
            Path esquema,
            Path imagem,
            Path execucoes) {
    }
}
