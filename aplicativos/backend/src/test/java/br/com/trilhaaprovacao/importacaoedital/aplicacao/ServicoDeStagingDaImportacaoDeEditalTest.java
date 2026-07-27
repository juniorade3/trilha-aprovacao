package br.com.trilhaaprovacao.importacaoedital.aplicacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.trilhaaprovacao.compartilhado.api.ConflitoDeDominio;
import br.com.trilhaaprovacao.compartilhado.api.RecursoNaoEncontrado;
import br.com.trilhaaprovacao.compartilhado.api.RegraDeDominio;
import br.com.trilhaaprovacao.importacaoedital.dominio.EstadoDaImportacaoDeEdital;
import br.com.trilhaaprovacao.importacaoedital.dominio.ExtracaoEstruturadaDoEdital;
import br.com.trilhaaprovacao.importacaoedital.dominio.ExtracaoEstruturadaDoEdital.FonteDoEdital;
import br.com.trilhaaprovacao.importacaoedital.dominio.ImportacaoDeEdital;
import br.com.trilhaaprovacao.importacaoedital.dominio.ProblemaDaImportacao;
import br.com.trilhaaprovacao.importacaoedital.dominio.ProvenienciaDoDado;
import br.com.trilhaaprovacao.importacaoedital.dominio.SeveridadeDoProblemaDaImportacao;
import br.com.trilhaaprovacao.importacaoedital.dominio.TipoDaFonteDoEdital;
import br.com.trilhaaprovacao.importacaoedital.dominio.ValorExtraido;
import br.com.trilhaaprovacao.importacaoedital.dominio.ModoDaImportacaoDeEdital;
import br.com.trilhaaprovacao.importacaoedital.dominio.PoliticaDeReutilizacao;
import br.com.trilhaaprovacao.importacaoedital.infraestrutura.ConfiguracaoDaImportacaoDeEdital;
import br.com.trilhaaprovacao.importacaoedital.infraestrutura.ImportacaoDeEditalPersistida;
import br.com.trilhaaprovacao.importacaoedital.infraestrutura.RepositorioDeImportacoesDeEdital;
import br.com.trilhaaprovacao.importacaoedital.infraestrutura.RepositorioDeVersoesDaExtracaoDoEdital;
import br.com.trilhaaprovacao.importacaoedital.infraestrutura.VersaoDaExtracaoDoEditalPersistida;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

class ServicoDeStagingDaImportacaoDeEditalTest {
    @Test
    void uploadIdenticoReutilizaStagingAtivoDoMesmoUsuario() {
        var importacoes = mock(RepositorioDeImportacoesDeEdital.class);
        var versoes = mock(RepositorioDeVersoesDaExtracaoDoEdital.class);
        var extrator = mock(ServicoDeExtracaoDoArquivoDoEdital.class);
        var configuracao = new ConfiguracaoDaImportacaoDeEdital(1_000, 10,
                10_000, Duration.ofDays(1), Duration.ofSeconds(1));
        String hash = "a".repeat(64);
        byte[] conteudo = "EDITAL: Teste".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        when(extrator.inspecionar("edital.txt", conteudo)).thenReturn(
                new InspecaoDoArquivoDoEdital("edital.txt",
                        TipoDaFonteDoEdital.TEXTO, "text/plain", hash,
                        conteudo.length, List.of()));
        when(importacoes
                .findFirstByIdentificadorDoUsuarioAndSha256OrderByCriadoEmDesc(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.eq(hash)))
                .thenReturn(Optional.empty());
        var servico = new ServicoDeStagingDaImportacaoDeEdital(importacoes,
                versoes, configuracao, extrator, mock(ObjectMapper.class),
                mock(JdbcTemplate.class));
        UUID usuario = UUID.randomUUID();

        var primeira = servico.receber(usuario, "edital.txt", conteudo);
        ArgumentCaptor<ImportacaoDeEditalPersistida> captor =
                ArgumentCaptor.forClass(ImportacaoDeEditalPersistida.class);
        verify(importacoes).save(captor.capture());
        when(importacoes
                .findFirstByIdentificadorDoUsuarioAndSha256OrderByCriadoEmDesc(
                        usuario, hash)).thenReturn(Optional.of(captor.getValue()));

        var segunda = servico.receber(usuario, "edital.txt", conteudo);

        assertThat(segunda.identificador()).isEqualTo(primeira.identificador());
        verify(importacoes, times(1)).save(
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void uploadIdenticoCriaNovoLoteDepoisDaSelecaoDeCargo() {
        var importacoes = mock(RepositorioDeImportacoesDeEdital.class);
        var versoes = mock(RepositorioDeVersoesDaExtracaoDoEdital.class);
        var extrator = mock(ServicoDeExtracaoDoArquivoDoEdital.class);
        var configuracao = new ConfiguracaoDaImportacaoDeEdital(1_000, 10,
                10_000, Duration.ofDays(1), Duration.ofSeconds(1));
        String hash = "b".repeat(64);
        byte[] conteudo = "EDITAL: Dois cargos".getBytes(
                java.nio.charset.StandardCharsets.UTF_8);
        when(extrator.inspecionar("edital.txt", conteudo)).thenReturn(
                new InspecaoDoArquivoDoEdital("edital.txt",
                        TipoDaFonteDoEdital.TEXTO, "text/plain", hash,
                        conteudo.length, List.of()));
        UUID usuario = UUID.randomUUID();
        when(importacoes
                .findFirstByIdentificadorDoUsuarioAndSha256OrderByCriadoEmDesc(
                        usuario, hash)).thenReturn(Optional.empty());
        var servico = new ServicoDeStagingDaImportacaoDeEdital(importacoes,
                versoes, configuracao, extrator, mock(ObjectMapper.class),
                mock(JdbcTemplate.class));

        var primeira = servico.receber(usuario, "edital.txt", conteudo);
        ArgumentCaptor<ImportacaoDeEditalPersistida> captor =
                ArgumentCaptor.forClass(ImportacaoDeEditalPersistida.class);
        verify(importacoes).save(captor.capture());
        ImportacaoDeEditalPersistida loteAnterior = captor.getValue();
        loteAnterior.definirDecisoes("cargo-a",
                ModoDaImportacaoDeEdital.CRIAR_NOVO, null,
                PoliticaDeReutilizacao.EXIGIR_DECISAO);
        when(importacoes
                .findFirstByIdentificadorDoUsuarioAndSha256OrderByCriadoEmDesc(
                        usuario, hash)).thenReturn(Optional.of(loteAnterior));

        var segundo = servico.receber(usuario, "edital.txt", conteudo);

        assertThat(segundo.identificador()).isNotEqualTo(
                primeira.identificador());
        verify(importacoes, times(2)).save(
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void correcaoManualCriaNovaVersaoImutavelComNovoHash() {
        var importacoes = mock(RepositorioDeImportacoesDeEdital.class);
        var versoes = mock(RepositorioDeVersoesDaExtracaoDoEdital.class);
        UUID usuario = UUID.randomUUID();
        UUID identificador = UUID.randomUUID();
        ImportacaoDeEditalPersistida persistida = importacaoExtraida(
                identificador, usuario, EstadoDaImportacaoDeEdital.VALIDADA);
        when(importacoes.encontrarParaAtualizacao(identificador, usuario))
                .thenReturn(Optional.of(persistida));
        var servico = servico(importacoes, versoes, 100_000);
        ExtracaoEstruturadaDoEdital corrigida = extracao(
                "edital.txt", "a".repeat(64), 1, "Concurso corrigido");

        ResultadoDoStagingDaImportacao resultado = servico
                .registrarCorrecaoManual(usuario, identificador, 1, corrigida);

        ArgumentCaptor<VersaoDaExtracaoDoEditalPersistida> captor =
                ArgumentCaptor.forClass(
                        VersaoDaExtracaoDoEditalPersistida.class);
        verify(versoes).save(captor.capture());
        VersaoDaExtracaoDoEditalPersistida novaVersao = captor.getValue();
        assertThat(novaVersao.identificadorDaImportacao())
                .isEqualTo(identificador);
        assertThat(novaVersao.identificadorDoUsuario()).isEqualTo(usuario);
        assertThat(novaVersao.numeroDaVersao()).isEqualTo(2);
        assertThat(novaVersao.versaoDoContrato()).isEqualTo("1");
        assertThat(novaVersao.versaoDoExtrator()).isEqualTo("manual-1");
        assertThat(novaVersao.hashDaExtracao())
                .hasSize(64)
                .isNotEqualTo("b".repeat(64));
        assertThat(novaVersao.dadosEstruturados())
                .contains("Concurso corrigido");
        assertThat(resultado.importacao().versaoAtualDaExtracao()).isEqualTo(2);
        assertThat(resultado.importacao().hashDaExtracaoAtual())
                .isEqualTo(novaVersao.hashDaExtracao());
        assertThat(resultado.importacao().estado())
                .isEqualTo(EstadoDaImportacaoDeEdital.VALIDADA);
        assertThat(resultado.importacao().chaveDoCargoSelecionado()).isNull();
        verify(importacoes).encontrarParaAtualizacao(identificador, usuario);
        verify(versoes, never()).delete(any());
    }

    @Test
    void correcaoManualRecusaVersaoEsperadaDesatualizada() {
        var importacoes = mock(RepositorioDeImportacoesDeEdital.class);
        var versoes = mock(RepositorioDeVersoesDaExtracaoDoEdital.class);
        UUID usuario = UUID.randomUUID();
        UUID identificador = UUID.randomUUID();
        when(importacoes.encontrarParaAtualizacao(identificador, usuario))
                .thenReturn(Optional.of(importacaoExtraida(identificador,
                        usuario, EstadoDaImportacaoDeEdital.VALIDADA)));
        var servico = servico(importacoes, versoes, 100_000);

        assertThatThrownBy(() -> servico.registrarCorrecaoManual(usuario,
                identificador, 0, extracao("edital.txt", "a".repeat(64),
                        1, "Concurso corrigido")))
                .isInstanceOfSatisfying(ConflitoDeDominio.class,
                        conflito -> assertThat(conflito.codigo()).isEqualTo(
                                "VERSAO_DA_EXTRACAO_DESATUALIZADA"));
        verify(versoes, never()).save(any());
    }

    @Test
    void correcaoManualRecusaOperacaoAssistidaOuEstadoEmConfirmacao() {
        var importacoes = mock(RepositorioDeImportacoesDeEdital.class);
        var versoes = mock(RepositorioDeVersoesDaExtracaoDoEdital.class);
        UUID usuario = UUID.randomUUID();
        UUID identificador = UUID.randomUUID();
        ImportacaoDeEditalPersistida preparada = importacaoExtraida(
                identificador, usuario, EstadoDaImportacaoDeEdital.VALIDADA);
        preparada.vincularOperacao(UUID.randomUUID());
        when(importacoes.encontrarParaAtualizacao(identificador, usuario))
                .thenReturn(Optional.of(preparada));
        var servico = servico(importacoes, versoes, 100_000);

        assertThatThrownBy(() -> servico.registrarCorrecaoManual(usuario,
                identificador, 1, extracao("edital.txt", "a".repeat(64),
                        1, "Concurso corrigido")))
                .isInstanceOfSatisfying(ConflitoDeDominio.class,
                        conflito -> assertThat(conflito.codigo()).isEqualTo(
                                "IMPORTACAO_NAO_ACEITA_CORRECAO_MANUAL"));

        ImportacaoDeEditalPersistida emConfirmacao = importacaoExtraida(
                identificador, usuario,
                EstadoDaImportacaoDeEdital.AGUARDANDO_CONFIRMACAO);
        when(importacoes.encontrarParaAtualizacao(identificador, usuario))
                .thenReturn(Optional.of(emConfirmacao));
        assertThatThrownBy(() -> servico.registrarCorrecaoManual(usuario,
                identificador, 1, extracao("edital.txt", "a".repeat(64),
                        1, "Concurso corrigido")))
                .isInstanceOf(ConflitoDeDominio.class);
        verifyNoInteractions(versoes);
    }

    @Test
    void reextracaoRecusaOperacaoVinculadaOuEstadoEmConfirmacao() {
        var importacoes = mock(RepositorioDeImportacoesDeEdital.class);
        var versoes = mock(RepositorioDeVersoesDaExtracaoDoEdital.class);
        var extrator = mock(ServicoDeExtracaoDoArquivoDoEdital.class);
        UUID usuario = UUID.randomUUID();
        UUID identificador = UUID.randomUUID();
        ImportacaoDeEditalPersistida preparada = importacaoExtraida(
                identificador, usuario, EstadoDaImportacaoDeEdital.VALIDADA);
        preparada.vincularOperacao(UUID.randomUUID());
        when(importacoes.encontrarParaAtualizacao(identificador, usuario))
                .thenReturn(Optional.of(preparada));
        var configuracao = new ConfiguracaoDaImportacaoDeEdital(1_000, 10,
                100_000, Duration.ofDays(1), Duration.ofSeconds(1));
        var servico = new ServicoDeStagingDaImportacaoDeEdital(importacoes,
                versoes, configuracao, extrator, new ObjectMapper(),
                mock(JdbcTemplate.class));

        assertThatThrownBy(() -> servico.extrair(usuario, identificador))
                .isInstanceOfSatisfying(ConflitoDeDominio.class,
                        conflito -> assertThat(conflito.codigo()).isEqualTo(
                                "IMPORTACAO_NAO_ACEITA_REEXTRACAO"));

        when(importacoes.encontrarParaAtualizacao(identificador, usuario))
                .thenReturn(Optional.of(importacaoExtraida(identificador,
                        usuario,
                        EstadoDaImportacaoDeEdital.AGUARDANDO_CONFIRMACAO)));
        assertThatThrownBy(() -> servico.extrair(usuario, identificador))
                .isInstanceOf(ConflitoDeDominio.class);
        verifyNoInteractions(extrator, versoes);
    }

    @Test
    void consultaExibeFalhaPersistidaQuandoExtracaoNaoGerouVersao() {
        var importacoes = mock(RepositorioDeImportacoesDeEdital.class);
        var versoes = mock(RepositorioDeVersoesDaExtracaoDoEdital.class);
        UUID usuario = UUID.randomUUID();
        OffsetDateTime agora = OffsetDateTime.now();
        ImportacaoDeEdital importacao = ImportacaoDeEdital.receber(usuario,
                TipoDaFonteDoEdital.PDF_TEXTUAL, "edital.pdf",
                "application/pdf", "a".repeat(64), 539_435, agora);
        importacao.iniciarExtracao(agora.plusSeconds(1));
        importacao.falhar("CARGO_AUSENTE", agora.plusSeconds(2));
        ImportacaoDeEditalPersistida persistida =
                new ImportacaoDeEditalPersistida(importacao,
                        "%PDF".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                        agora.plusDays(1));
        persistida.registrarFalha("CARGO_AUSENTE",
                "Campo depende de um cargo anterior.");
        when(importacoes.findByIdentificadorAndIdentificadorDoUsuario(
                importacao.identificador(), usuario))
                .thenReturn(Optional.of(persistida));
        when(versoes
                .findFirstByIdentificadorDaImportacaoAndIdentificadorDoUsuarioOrderByNumeroDaVersaoDesc(
                        importacao.identificador(), usuario))
                .thenReturn(Optional.empty());
        var servico = servico(importacoes, versoes, 100_000);

        ResultadoDoStagingDaImportacao resultado = servico
                .obterExtracaoAtual(usuario, importacao.identificador());

        assertThat(resultado.importacao().estado())
                .isEqualTo(EstadoDaImportacaoDeEdital.FALHOU);
        assertThat(resultado.extracao()).isNull();
        assertThat(resultado.problemas()).singleElement().satisfies(problema -> {
            assertThat(problema.codigo()).isEqualTo("CARGO_AUSENTE");
            assertThat(problema.mensagem())
                    .isEqualTo("Campo depende de um cargo anterior.");
        });
    }

    @Test
    void correcaoManualIsolaImportacaoPeloUsuarioNoProprioLock() {
        var importacoes = mock(RepositorioDeImportacoesDeEdital.class);
        var versoes = mock(RepositorioDeVersoesDaExtracaoDoEdital.class);
        UUID usuario = UUID.randomUUID();
        UUID identificador = UUID.randomUUID();
        when(importacoes.encontrarParaAtualizacao(identificador, usuario))
                .thenReturn(Optional.empty());
        var servico = servico(importacoes, versoes, 100_000);

        assertThatThrownBy(() -> servico.registrarCorrecaoManual(usuario,
                identificador, 1, extracao("edital.txt", "a".repeat(64),
                        1, "Concurso corrigido")))
                .isInstanceOfSatisfying(RecursoNaoEncontrado.class,
                        erro -> assertThat(erro.codigo()).isEqualTo(
                                "IMPORTACAO_DE_EDITAL_NAO_ENCONTRADA"));
        verify(importacoes).encontrarParaAtualizacao(identificador, usuario);
        verifyNoInteractions(versoes);
    }

    @Test
    void correcaoManualExigeFonteOriginalComPaginasCoerentes() {
        var importacoes = mock(RepositorioDeImportacoesDeEdital.class);
        var versoes = mock(RepositorioDeVersoesDaExtracaoDoEdital.class);
        UUID usuario = UUID.randomUUID();
        UUID identificador = UUID.randomUUID();
        when(importacoes.encontrarParaAtualizacao(identificador, usuario))
                .thenReturn(Optional.of(importacaoExtraida(identificador,
                        usuario, EstadoDaImportacaoDeEdital.VALIDADA)));
        var servico = servico(importacoes, versoes, 100_000);

        assertThatThrownBy(() -> servico.registrarCorrecaoManual(usuario,
                identificador, 1, extracao("outro.txt", "a".repeat(64),
                        1, "Concurso corrigido")))
                .isInstanceOf(RegraDeDominio.class);
        assertThatThrownBy(() -> servico.registrarCorrecaoManual(usuario,
                identificador, 1, extracao("edital.txt", "c".repeat(64),
                        1, "Concurso corrigido")))
                .isInstanceOf(RegraDeDominio.class);
        assertThatThrownBy(() -> servico.registrarCorrecaoManual(usuario,
                identificador, 1, extracao("edital.txt", "a".repeat(64),
                        2, "Concurso corrigido")))
                .isInstanceOf(RegraDeDominio.class);
        assertThatThrownBy(() -> servico.registrarCorrecaoManual(usuario,
                identificador, 1, extracao("edital.txt", "a".repeat(64),
                        11, "Concurso corrigido")))
                .isInstanceOf(RegraDeDominio.class);
        verifyNoInteractions(versoes);
    }

    @Test
    void correcaoManualLimitaJsonPersistido() {
        var importacoes = mock(RepositorioDeImportacoesDeEdital.class);
        var versoes = mock(RepositorioDeVersoesDaExtracaoDoEdital.class);
        UUID usuario = UUID.randomUUID();
        UUID identificador = UUID.randomUUID();
        when(importacoes.encontrarParaAtualizacao(identificador, usuario))
                .thenReturn(Optional.of(importacaoExtraida(identificador,
                        usuario, EstadoDaImportacaoDeEdital.VALIDADA)));
        var servico = servico(importacoes, versoes, 300);

        assertThatThrownBy(() -> servico.registrarCorrecaoManual(usuario,
                identificador, 1, extracao("edital.txt", "a".repeat(64),
                        1, "Concurso corrigido")))
                .isInstanceOfSatisfying(RegraDeDominio.class,
                        erro -> assertThat(erro.codigo()).isEqualTo(
                                "CORRECAO_MANUAL_MUITO_GRANDE"));
        verify(versoes, never()).save(any());
    }

    @Test
    void correcaoManualConfirmaSeletivamenteEPreservaProblemasExternos()
            throws Exception {
        var importacoes = mock(RepositorioDeImportacoesDeEdital.class);
        var versoes = mock(RepositorioDeVersoesDaExtracaoDoEdital.class);
        UUID usuario = UUID.randomUUID();
        UUID identificador = UUID.randomUUID();
        ImportacaoDeEditalPersistida persistida = importacaoExtraida(
                identificador, usuario,
                EstadoDaImportacaoDeEdital.AGUARDANDO_CORRECOES);
        when(importacoes.encontrarParaAtualizacao(identificador, usuario))
                .thenReturn(Optional.of(persistida));
        ExtracaoEstruturadaDoEdital base = extracao(
                "edital.txt", "a".repeat(64), 1, "Concurso");
        ExtracaoEstruturadaDoEdital anterior = comInferencias(
                base, true, true,
                List.of("Aviso confiável do servidor"),
                List.of("Incerteza original",
                        ConversorDaInterpretacaoAssistidaDoEdital
                                .INCERTEZA_DE_EVIDENCIAS_NAO_VERIFICADAS));
        String cargo = anterior.cargos().getFirst().chave();
        List<ProblemaDaImportacao> problemasAnteriores = List.of(
                avisoDaFonte("VERIFICACAO_ANTIMALWARE_INDISPONIVEL"),
                evidencia("cargo", cargo, "nome"),
                evidencia("edital", "edital", "titulo"),
                new ProblemaDaImportacao(
                        SeveridadeDoProblemaDaImportacao.EXIGE_DECISAO,
                        "VALIDACAO_ANTIGA", "Não deve ser carregada.",
                        "cargos[0]", "cargo", cargo, "nome"));
        when(versoes
                .findFirstByIdentificadorDaImportacaoAndIdentificadorDoUsuarioOrderByNumeroDaVersaoDesc(
                        identificador, usuario))
                .thenReturn(Optional.of(versao(identificador, usuario,
                        anterior, problemasAnteriores)));
        ExtracaoEstruturadaDoEdital recebida =
                new ExtracaoEstruturadaDoEdital(
                        anterior.versaoDoContrato(), anterior.fonte(),
                        anterior.concurso(), anterior.edital(),
                        anterior.cargos(), anterior.provas(),
                        anterior.materias(), List.of("Aviso forjado"),
                        List.of());
        var servico = servico(importacoes, versoes, 100_000);

        ResultadoDoStagingDaImportacao resultado =
                servico.registrarCorrecaoManual(usuario, identificador, 1,
                        recebida, List.of(new ConfirmacaoDeCampoDaExtracao(
                                "cargo", cargo, "nome")));

        assertThat(resultado.extracao().avisos())
                .containsExactly("Aviso confiável do servidor");
        assertThat(resultado.extracao().incertezas())
                .contains("Incerteza original",
                        ConversorDaInterpretacaoAssistidaDoEdital
                                .INCERTEZA_DE_EVIDENCIAS_NAO_VERIFICADAS);
        assertThat(resultado.extracao().cargos().getFirst().nome().fonte()
                .secao()).isEqualTo("Correção do usuário");
        assertThat(resultado.problemas()).extracting("codigo")
                .contains("VERIFICACAO_ANTIMALWARE_INDISPONIVEL",
                        "EVIDENCIA_ASSISTIDA_NAO_VERIFICADA")
                .doesNotContain("VALIDACAO_ANTIGA");
        assertThat(resultado.problemas())
                .filteredOn(problema -> "EVIDENCIA_ASSISTIDA_NAO_VERIFICADA"
                        .equals(problema.codigo()))
                .singleElement()
                .extracting(ProblemaDaImportacao::chaveDoRecurso)
                .isEqualTo("edital");
    }

    @Test
    void correcaoManualPermiteConfirmarAssociacaoAusente()
            throws Exception {
        var importacoes = mock(RepositorioDeImportacoesDeEdital.class);
        var versoes = mock(RepositorioDeVersoesDaExtracaoDoEdital.class);
        UUID usuario = UUID.randomUUID();
        UUID identificador = UUID.randomUUID();
        when(importacoes.encontrarParaAtualizacao(identificador, usuario))
                .thenReturn(Optional.of(importacaoExtraida(
                        identificador, usuario,
                        EstadoDaImportacaoDeEdital.AGUARDANDO_CORRECOES)));
        ExtracaoEstruturadaDoEdital anterior = extracao(
                "edital.txt", "a".repeat(64), 1, "Concurso");
        var topico = anterior.materias().getFirst().topicos().getFirst();
        List<ProblemaDaImportacao> problemasAnteriores = List.of(
                evidencia("topico", topico.chave(), "chaveDoPai"));
        when(versoes
                .findFirstByIdentificadorDaImportacaoAndIdentificadorDoUsuarioOrderByNumeroDaVersaoDesc(
                        identificador, usuario))
                .thenReturn(Optional.of(versao(identificador, usuario,
                        anterior, problemasAnteriores)));
        var servico = servico(importacoes, versoes, 100_000);

        ResultadoDoStagingDaImportacao resultado =
                servico.registrarCorrecaoManual(
                        usuario, identificador, 1, anterior,
                        List.of(new ConfirmacaoDeCampoDaExtracao(
                                "topico", topico.chave(), "chaveDoPai")));

        assertThat(resultado.problemas()).extracting("codigo")
                .doesNotContain("EVIDENCIA_ASSISTIDA_NAO_VERIFICADA");
        assertThat(resultado.extracao().materias().getFirst().topicos()
                .getFirst().chaveDoPai()).isNull();
    }

    @Test
    void correcaoManualRejeitaConfirmacaoForjadaComCodigoEstavel()
            throws Exception {
        var importacoes = mock(RepositorioDeImportacoesDeEdital.class);
        var versoes = mock(RepositorioDeVersoesDaExtracaoDoEdital.class);
        UUID usuario = UUID.randomUUID();
        UUID identificador = UUID.randomUUID();
        when(importacoes.encontrarParaAtualizacao(identificador, usuario))
                .thenReturn(Optional.of(importacaoExtraida(identificador,
                        usuario,
                        EstadoDaImportacaoDeEdital.AGUARDANDO_CORRECOES)));
        ExtracaoEstruturadaDoEdital anterior = comInferencias(extracao(
                "edital.txt", "a".repeat(64), 1, "Concurso"),
                true, false, List.of(), List.of());
        when(versoes
                .findFirstByIdentificadorDaImportacaoAndIdentificadorDoUsuarioOrderByNumeroDaVersaoDesc(
                        identificador, usuario))
                .thenReturn(Optional.of(versao(identificador, usuario,
                        anterior, List.of())));
        var servico = servico(importacoes, versoes, 100_000);

        assertThatThrownBy(() -> servico.registrarCorrecaoManual(
                usuario, identificador, 1, anterior,
                List.of(new ConfirmacaoDeCampoDaExtracao(
                        "cargo", anterior.cargos().getFirst().chave(),
                        "area"))))
                .isInstanceOfSatisfying(RegraDeDominio.class,
                        erro -> assertThat(erro.codigo()).isEqualTo(
                                "CONFIRMACAO_DE_CAMPO_INVALIDA"));
        verify(versoes, never()).save(any());
    }

    @Test
    void pdfDigitalizadoFicaProntoAposConfirmarInferenciaSemPerderAviso()
            throws Exception {
        var importacoes = mock(RepositorioDeImportacoesDeEdital.class);
        var versoes = mock(RepositorioDeVersoesDaExtracaoDoEdital.class);
        UUID usuario = UUID.randomUUID();
        UUID identificador = UUID.randomUUID();
        ImportacaoDeEditalPersistida persistida = importacaoExtraida(
                identificador, usuario,
                EstadoDaImportacaoDeEdital.AGUARDANDO_CORRECOES,
                TipoDaFonteDoEdital.PDF_DIGITALIZADO, "edital.pdf");
        when(importacoes.encontrarParaAtualizacao(identificador, usuario))
                .thenReturn(Optional.of(persistida));
        ExtracaoEstruturadaDoEdital anterior = comInferencias(extracao(
                "edital.pdf", "a".repeat(64), 1, "Concurso"),
                true, false, List.of(), List.of());
        when(versoes
                .findFirstByIdentificadorDaImportacaoAndIdentificadorDoUsuarioOrderByNumeroDaVersaoDesc(
                        identificador, usuario))
                .thenReturn(Optional.of(versao(identificador, usuario,
                        anterior, List.of(avisoDaFonte(
                                "OCR_INDISPONIVEL")))));
        String cargo = anterior.cargos().getFirst().chave();
        var servico = servico(importacoes, versoes, 100_000);

        ResultadoDoStagingDaImportacao resultado =
                servico.registrarCorrecaoManual(usuario, identificador, 1,
                        anterior, List.of(new ConfirmacaoDeCampoDaExtracao(
                                "cargo", cargo, "nome")));

        assertThat(resultado.importacao().tipoDaFonte())
                .isEqualTo(TipoDaFonteDoEdital.PDF_DIGITALIZADO);
        assertThat(resultado.importacao().estado())
                .isEqualTo(EstadoDaImportacaoDeEdital.VALIDADA);
        assertThat(resultado.problemas()).singleElement().satisfies(problema -> {
            assertThat(problema.codigo()).isEqualTo("OCR_INDISPONIVEL");
            assertThat(problema.severidade())
                    .isEqualTo(SeveridadeDoProblemaDaImportacao.AVISO);
        });
    }

    @Test
    void interpretacaoAssistidaCriaVersaoComExtratorAuditavel() {
        var importacoes = mock(RepositorioDeImportacoesDeEdital.class);
        var versoes = mock(RepositorioDeVersoesDaExtracaoDoEdital.class);
        UUID usuario = UUID.randomUUID();
        UUID identificador = UUID.randomUUID();
        when(importacoes.encontrarParaAtualizacao(identificador, usuario))
                .thenReturn(Optional.of(importacaoExtraida(identificador,
                        usuario, EstadoDaImportacaoDeEdital.VALIDADA)));
        var servico = servico(importacoes, versoes, 100_000);
        ExtracaoEstruturadaDoEdital interpretada = extracao(
                "edital.txt", "a".repeat(64), 1, "Concurso pela IA");

        ResultadoDoStagingDaImportacao resultado =
                servico.registrarInterpretacaoAssistida(
                        usuario, identificador, 1, interpretada, List.of());

        ArgumentCaptor<VersaoDaExtracaoDoEditalPersistida> captor =
                ArgumentCaptor.forClass(
                        VersaoDaExtracaoDoEditalPersistida.class);
        verify(versoes).save(captor.capture());
        assertThat(captor.getValue().numeroDaVersao()).isEqualTo(2);
        assertThat(captor.getValue().versaoDoExtrator())
                .isEqualTo("ia-gpt56sol-p1");
        assertThat(resultado.importacao().versaoAtualDaExtracao())
                .isEqualTo(2);
    }

    @Test
    void interpretacaoAssistidaRecusaResultadoQuandoVersaoMudou() {
        var importacoes = mock(RepositorioDeImportacoesDeEdital.class);
        var versoes = mock(RepositorioDeVersoesDaExtracaoDoEdital.class);
        UUID usuario = UUID.randomUUID();
        UUID identificador = UUID.randomUUID();
        when(importacoes.encontrarParaAtualizacao(identificador, usuario))
                .thenReturn(Optional.of(importacaoExtraida(identificador,
                        usuario, EstadoDaImportacaoDeEdital.VALIDADA)));
        var servico = servico(importacoes, versoes, 100_000);

        assertThatThrownBy(() -> servico.registrarInterpretacaoAssistida(
                usuario, identificador, 2,
                extracao("edital.txt", "a".repeat(64), 1,
                        "Concurso pela IA"), List.of()))
                .isInstanceOfSatisfying(ConflitoDeDominio.class,
                        conflito -> assertThat(conflito.codigo()).isEqualTo(
                                "VERSAO_DA_EXTRACAO_DESATUALIZADA"));
        verify(versoes, never()).save(any());
    }

    @Test
    void interpretacaoAssistidaInformaQuandoFonteRetidaExpirou() {
        var importacoes = mock(RepositorioDeImportacoesDeEdital.class);
        var versoes = mock(RepositorioDeVersoesDaExtracaoDoEdital.class);
        UUID usuario = UUID.randomUUID();
        UUID identificador = UUID.randomUUID();
        ImportacaoDeEditalPersistida persistida = importacaoExtraida(
                identificador, usuario, EstadoDaImportacaoDeEdital.VALIDADA);
        persistida.descartarConteudoRetido();
        when(importacoes.findByIdentificadorAndIdentificadorDoUsuario(
                identificador, usuario)).thenReturn(Optional.of(persistida));
        var servico = servico(importacoes, versoes, 100_000);

        assertThatThrownBy(() -> servico.obterFonteRetida(
                usuario, identificador, 1))
                .isInstanceOfSatisfying(
                        FalhaNaInterpretacaoAssistidaDoEdital.class,
                        falha -> assertThat(falha.codigo()).isEqualTo(
                                FalhaNaInterpretacaoAssistidaDoEdital.Codigo
                                        .FONTE_EXPIRADA));
        verifyNoInteractions(versoes);
    }

    private ServicoDeStagingDaImportacaoDeEdital servico(
            RepositorioDeImportacoesDeEdital importacoes,
            RepositorioDeVersoesDaExtracaoDoEdital versoes,
            int limiteDoJson) {
        var configuracao = new ConfiguracaoDaImportacaoDeEdital(1_000, 10,
                limiteDoJson, Duration.ofDays(1), Duration.ofSeconds(1));
        return new ServicoDeStagingDaImportacaoDeEdital(importacoes, versoes,
                configuracao, mock(ServicoDeExtracaoDoArquivoDoEdital.class),
                new ObjectMapper(), mock(JdbcTemplate.class));
    }

    private ImportacaoDeEditalPersistida importacaoExtraida(UUID identificador,
            UUID usuario, EstadoDaImportacaoDeEdital estado) {
        return importacaoExtraida(identificador, usuario, estado,
                TipoDaFonteDoEdital.TEXTO, "edital.txt");
    }

    private ImportacaoDeEditalPersistida importacaoExtraida(UUID identificador,
            UUID usuario, EstadoDaImportacaoDeEdital estado,
            TipoDaFonteDoEdital tipoDaFonte, String nomeDoArquivo) {
        OffsetDateTime agora = OffsetDateTime.now();
        ImportacaoDeEdital importacao = new ImportacaoDeEdital(identificador,
                usuario, estado, tipoDaFonte, nomeDoArquivo,
                tipoDaFonte == TipoDaFonteDoEdital.TEXTO
                        ? "text/plain" : "application/pdf",
                "a".repeat(64), 100, 1, "b".repeat(64),
                null, estado == EstadoDaImportacaoDeEdital.APLICADA
                        ? agora : null,
                agora.minusHours(1), agora);
        ImportacaoDeEditalPersistida persistida =
                new ImportacaoDeEditalPersistida(importacao,
                        "conteudo".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                        agora.plusDays(1));
        persistida.registrarConteudoExtraido(tipoDaFonte, 1,
                "conteudo");
        return persistida;
    }

    private ExtracaoEstruturadaDoEdital comInferencias(
            ExtracaoEstruturadaDoEdital base, boolean cargoInferido,
            boolean editalInferido, List<String> avisos,
            List<String> incertezas) {
        var cargo = base.cargos().getFirst();
        var cargoAlterado = new ExtracaoEstruturadaDoEdital.CargoExtraido(
                cargo.chave(),
                cargoInferido ? inferido(cargo.nome()) : cargo.nome(),
                cargo.area(), cargo.especialidade(),
                cargo.nivelDeEscolaridade(), cargo.ordem());
        var edital = base.edital();
        var editalAlterado =
                new ExtracaoEstruturadaDoEdital.EditalExtraido(
                        editalInferido ? inferido(edital.titulo())
                                : edital.titulo(),
                        edital.numero(), edital.ano(), edital.descricao(),
                        edital.dataDePublicacao());
        return new ExtracaoEstruturadaDoEdital(
                base.versaoDoContrato(), base.fonte(), base.concurso(),
                editalAlterado, List.of(cargoAlterado), base.provas(),
                base.materias(), avisos, incertezas);
    }

    private <T> ValorExtraido<T> inferido(ValorExtraido<T> original) {
        return new ValorExtraido<>(original.valor(),
                new BigDecimal("0.5000"),
                new ProvenienciaDoDado(null,
                        "Interpretação assistida não verificada", null),
                true);
    }

    private ProblemaDaImportacao evidencia(String tipo, String chave,
            String campo) {
        return new ProblemaDaImportacao(
                SeveridadeDoProblemaDaImportacao.EXIGE_DECISAO,
                "EVIDENCIA_ASSISTIDA_NAO_VERIFICADA",
                "Confira o valor sugerido.", null, tipo, chave, campo);
    }

    private ProblemaDaImportacao avisoDaFonte(String codigo) {
        return new ProblemaDaImportacao(
                SeveridadeDoProblemaDaImportacao.AVISO, codigo,
                "Aviso operacional da fonte.", "fonte");
    }

    private VersaoDaExtracaoDoEditalPersistida versao(UUID importacao,
            UUID usuario, ExtracaoEstruturadaDoEdital extracao,
            List<ProblemaDaImportacao> problemas) throws Exception {
        ObjectMapper mapeador = new ObjectMapper();
        return new VersaoDaExtracaoDoEditalPersistida(
                importacao, usuario, 1, "1", "teste",
                mapeador.writeValueAsString(extracao),
                mapeador.writeValueAsString(problemas), "b".repeat(64),
                OffsetDateTime.now());
    }

    private ExtracaoEstruturadaDoEdital extracao(String nome, String hash,
            int paginas, String concurso) {
        String texto = """
                CONCURSO: %s
                EDITAL: Edital corrigido
                CARGO: Auditor
                ESCOLARIDADE: SUPERIOR
                PROVA: Objetiva
                TIPO: OBJETIVA
                CARÁTER: ELIMINATORIO_E_CLASSIFICATORIO
                GRUPO: Conhecimentos
                MATÉRIA: Direito
                TÓPICO: 1 - Atos administrativos
                """.formatted(concurso);
        return new ParserDeterministicoDoEdital().extrair(texto,
                new FonteDoEdital(nome, hash, paginas));
    }
}
