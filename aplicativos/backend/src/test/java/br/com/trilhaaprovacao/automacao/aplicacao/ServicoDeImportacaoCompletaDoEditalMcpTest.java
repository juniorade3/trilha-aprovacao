package br.com.trilhaaprovacao.automacao.aplicacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.trilhaaprovacao.automacao.dominio.OperacaoAssistida;
import br.com.trilhaaprovacao.automacao.dominio.EstadoDaOperacaoAssistida;
import br.com.trilhaaprovacao.automacao.infraestrutura.ContextoDaChamadaMcp;
import br.com.trilhaaprovacao.automacao.infraestrutura.IdentidadeDaIntegracaoMcp;
import br.com.trilhaaprovacao.automacao.infraestrutura.ServicoDeSegredosDaAutomacao;
import br.com.trilhaaprovacao.importacaoedital.aplicacao.PreparadorDaImportacaoCompletaDoEdital;
import br.com.trilhaaprovacao.importacaoedital.aplicacao.PreparadorDaImportacaoCompletaDoEdital.ContagensDaImportacao;
import br.com.trilhaaprovacao.importacaoedital.aplicacao.PreparadorDaImportacaoCompletaDoEdital.PreviaDaImportacaoCompleta;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

class ServicoDeImportacaoCompletaDoEditalMcpTest {

    @Test
    void deveUsarIdempotenciaEstavelEVincularOperacaoReforcada() {
        PreparadorDaImportacaoCompletaDoEdital importacoes = mock(
                PreparadorDaImportacaoCompletaDoEdital.class);
        ServicoDeOperacoesAssistidas operacoes = mock(
                ServicoDeOperacoesAssistidas.class);
        ServicoDeSegredosDaAutomacao segredos = new ServicoDeSegredosDaAutomacao(
                "segredo-forte-da-importacao-completa-123456789", true);
        var servico = new ServicoDeImportacaoCompletaDoEditalMcp(importacoes,
                operacoes, segredos, new ObjectMapper());
        UUID usuario = UUID.randomUUID();
        UUID vinculo = UUID.randomUUID();
        UUID importacao = UUID.randomUUID();
        PreviaDaImportacaoCompleta previa = previaComOrdem(importacao, false);
        when(importacoes.preparar(any())).thenReturn(previa,
                previaComOrdem(importacao, true));
        OperacaoAssistida operacao = operacao(usuario, vinculo);
        when(operacoes.prepararParaConfirmacaoReforcada(eq(usuario),
                eq(vinculo), eq(ServicoDeImportacaoCompletaDoEditalMcp.TIPO),
                anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new ServicoDeOperacoesAssistidas.OperacaoPreparada(
                        operacao, "2345678A"));
        Map<String, Object> argumentos = argumentos(importacao);

        ResultadoDaConsultaMcp primeira = servico.preparar(
                contexto(usuario, vinculo, "evento-1"), argumentos);
        ResultadoDaConsultaMcp repetida = servico.preparar(
                contexto(usuario, vinculo, "evento-2"), argumentos);

        ArgumentCaptor<String> chaves = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> propostas = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> versoes = ArgumentCaptor.forClass(String.class);
        verify(operacoes, times(2)).prepararParaConfirmacaoReforcada(
                eq(usuario), eq(vinculo),
                eq(ServicoDeImportacaoCompletaDoEditalMcp.TIPO), anyString(),
                propostas.capture(), versoes.capture(), chaves.capture());
        assertThat(chaves.getAllValues()).hasSize(2)
                .allMatch(chaves.getAllValues().getFirst()::equals);
        assertThat(propostas.getAllValues()).hasSize(2)
                .allMatch(propostas.getAllValues().getFirst()::equals);
        assertThat(versoes.getAllValues()).hasSize(2)
                .allMatch(versoes.getAllValues().getFirst()::equals);
        verify(importacoes, times(2)).vincularOperacao(eq(usuario),
                eq(importacao), eq(operacao.identificador()),
                eq(previa.propostaCanonica()), eq(previa.versoesConsultadas()));
        assertThat(primeira.dados())
                .containsEntry("identificadorDaOperacao",
                        operacao.identificador())
                .containsEntry("nivelDeConfirmacao", "REFORCADA")
                .containsEntry("fraseDeConfirmacao", "/confirmar 2345678A")
                .containsEntry("nadaFoiAlterado", true);
        assertThat(repetida.dados()).containsEntry("estado",
                "AGUARDANDO_CONFIRMACAO");
    }

    @Test
    void deveMudarIdempotenciaSomenteComNovaRevisaoDoStaging() {
        PreparadorDaImportacaoCompletaDoEdital importacoes = mock(
                PreparadorDaImportacaoCompletaDoEdital.class);
        ServicoDeOperacoesAssistidas operacoes = mock(
                ServicoDeOperacoesAssistidas.class);
        var servico = new ServicoDeImportacaoCompletaDoEditalMcp(importacoes,
                operacoes, new ServicoDeSegredosDaAutomacao(
                        "segredo-forte-da-importacao-completa-123456789", true),
                new ObjectMapper());
        UUID usuario = UUID.randomUUID();
        UUID vinculo = UUID.randomUUID();
        UUID importacao = UUID.randomUUID();
        when(importacoes.preparar(any())).thenReturn(previa(importacao, 1),
                previa(importacao, 2));
        OperacaoAssistida operacao = operacao(usuario, vinculo);
        when(operacoes.prepararParaConfirmacaoReforcada(eq(usuario),
                eq(vinculo), eq(ServicoDeImportacaoCompletaDoEditalMcp.TIPO),
                anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new ServicoDeOperacoesAssistidas.OperacaoPreparada(
                        operacao, null));

        servico.preparar(contexto(usuario, vinculo, "mesmo-evento"),
                argumentos(importacao));
        ResultadoDaConsultaMcp segunda = servico.preparar(
                contexto(usuario, vinculo, "mesmo-evento"),
                argumentos(importacao));

        ArgumentCaptor<String> chaves = ArgumentCaptor.forClass(String.class);
        verify(operacoes, times(2)).prepararParaConfirmacaoReforcada(
                eq(usuario), eq(vinculo),
                eq(ServicoDeImportacaoCompletaDoEditalMcp.TIPO), anyString(),
                anyString(), anyString(), chaves.capture());
        assertThat(chaves.getAllValues()).doesNotHaveDuplicates();
        assertThat(segunda.dados())
                .containsEntry("estado", "AGUARDANDO_CONFIRMACAO")
                .containsEntry("codigoDeConfirmacao", null)
                .containsEntry("fraseDeConfirmacao", null);
    }

    @ParameterizedTest
    @EnumSource(value = EstadoDaOperacaoAssistida.class,
            names = {"APLICADA", "EXPIRADA"})
    void devePropagarEstadoTerminalSemEmitirNovoCodigo(
            EstadoDaOperacaoAssistida estado) {
        PreparadorDaImportacaoCompletaDoEdital importacoes = mock(
                PreparadorDaImportacaoCompletaDoEdital.class);
        ServicoDeOperacoesAssistidas operacoes = mock(
                ServicoDeOperacoesAssistidas.class);
        var servico = new ServicoDeImportacaoCompletaDoEditalMcp(importacoes,
                operacoes, new ServicoDeSegredosDaAutomacao(
                        "segredo-forte-da-importacao-completa-123456789", true),
                new ObjectMapper());
        UUID usuario = UUID.randomUUID();
        UUID vinculo = UUID.randomUUID();
        UUID importacao = UUID.randomUUID();
        PreviaDaImportacaoCompleta previa = previa(importacao, 1);
        OperacaoAssistida terminal = operacao(usuario, vinculo);
        OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
        if (estado == EstadoDaOperacaoAssistida.APLICADA) {
            terminal.confirmar(terminal.assinatura(), agora);
            terminal.aplicar("{}", agora);
        } else {
            terminal.expirar(agora);
        }
        when(importacoes.preparar(any())).thenReturn(previa);
        when(operacoes.prepararParaConfirmacaoReforcada(eq(usuario),
                eq(vinculo), eq(ServicoDeImportacaoCompletaDoEditalMcp.TIPO),
                anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new ServicoDeOperacoesAssistidas.OperacaoPreparada(
                        terminal, null));

        ResultadoDaConsultaMcp resultado = servico.preparar(
                contexto(usuario, vinculo, "retry-terminal"),
                argumentos(importacao));

        assertThat(resultado.dados())
                .containsEntry("identificadorDaOperacao",
                        terminal.identificador())
                .containsEntry("estado", estado.name())
                .containsEntry("codigoDeConfirmacao", null)
                .containsEntry("fraseDeConfirmacao", null);
        verify(importacoes).vincularOperacao(eq(usuario), eq(importacao),
                eq(terminal.identificador()), eq(previa.propostaCanonica()),
                eq(previa.versoesConsultadas()));
    }

    private PreviaDaImportacaoCompleta previa(UUID importacao, int tentativa) {
        return new PreviaDaImportacaoCompleta("Importar edital validado.",
                Map.of("identificadorDaImportacao", importacao.toString(),
                        "versaoDaExtracao", 3,
                        "hashDaExtracao", "a".repeat(64),
                        "tentativa", tentativa),
                Map.of("versaoDaExtracao", 3,
                        "hashDaExtracao", "a".repeat(64),
                        "tentativa", tentativa),
                new ContagensDaImportacao(1, 1, 1, 1, 1, 2, 0, 4, 0,
                        8, 3),
                List.of(), List.of(), List.of(), List.of(), List.of());
    }

    private PreviaDaImportacaoCompleta previaComOrdem(UUID importacao,
            boolean inversa) {
        Map<String, Object> detalhes = new LinkedHashMap<>();
        if (inversa) {
            detalhes.put("segundo", 2);
            detalhes.put("primeiro", 1);
        } else {
            detalhes.put("primeiro", 1);
            detalhes.put("segundo", 2);
        }
        Map<String, Object> proposta = new LinkedHashMap<>(
                previa(importacao, 1).propostaCanonica());
        proposta.put("detalhes", detalhes);
        Map<String, Object> versoes = new LinkedHashMap<>(
                previa(importacao, 1).versoesConsultadas());
        versoes.put("detalhes", detalhes);
        return new PreviaDaImportacaoCompleta("Importar edital validado.",
                proposta, versoes, previa(importacao, 1).contagens(),
                List.of(), List.of(), List.of(), List.of(), List.of());
    }

    private Map<String, Object> argumentos(UUID importacao) {
        return Map.of(
                "identificadorDaImportacao", importacao.toString(),
                "chaveDoCargoSelecionado", "cargo-auditor",
                "modo", "CRIAR_NOVO",
                "politicaDeReutilizacao", "EXIGIR_DECISAO",
                "decisoes", Map.of(
                        "definirEditalComoPrincipal", true,
                        "selecionarCargoCriado", true,
                        "reutilizacoes", List.of()));
    }

    private ContextoDaChamadaMcp contexto(UUID usuario, UUID vinculo,
            String evento) {
        var identidade = new IdentidadeDaIntegracaoMcp(usuario, vinculo,
                UUID.randomUUID(), 10L, 20L, "agente", "sessao", 0,
                Set.of("operacoes:preparar"));
        return new ContextoDaChamadaMcp(identidade, UUID.randomUUID(), evento);
    }

    private OperacaoAssistida operacao(UUID usuario, UUID vinculo) {
        OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
        OperacaoAssistida operacao = OperacaoAssistida.preparar(usuario,
                vinculo, ServicoDeImportacaoCompletaDoEditalMcp.TIPO,
                "Importar edital validado.", "{}", "assinatura", "{}",
                "chave", "hash", agora.plusMinutes(30), agora);
        operacao.aguardarConfirmacao(agora);
        return operacao;
    }
}
