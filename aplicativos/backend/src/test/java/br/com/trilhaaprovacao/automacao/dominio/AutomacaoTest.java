package br.com.trilhaaprovacao.automacao.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AutomacaoTest {
    private static final OffsetDateTime AGORA =
            OffsetDateTime.of(2026, 7, 21, 18, 0, 0, 0, ZoneOffset.UTC);

    @Test
    void devePrepararAtivarERevogarVinculoDeUsoUnico() {
        UUID usuario = UUID.randomUUID();
        VinculoDeCanal vinculo = VinculoDeCanal.preparar(
                usuario, 123456L, "hash-do-codigo", AGORA.plusMinutes(10), AGORA);

        assertThat(vinculo.identificadorDoUsuario()).isEqualTo(usuario);
        assertThat(vinculo.canal()).isEqualTo(CanalDeIntegracao.TELEGRAM);
        assertThat(vinculo.estado()).isEqualTo(EstadoDoVinculoDeCanal.PENDENTE);
        assertThat(vinculo.codigoValidoEm(AGORA.plusMinutes(10).minusNanos(1)))
                .isTrue();
        assertThat(vinculo.codigoValidoEm(AGORA.plusMinutes(10))).isFalse();

        OffsetDateTime ativadoEm = AGORA.plusMinutes(1);
        vinculo.ativar(998877L, 998877L, ativadoEm);

        assertThat(vinculo.estado()).isEqualTo(EstadoDoVinculoDeCanal.ATIVO);
        assertThat(vinculo.identificadorExterno()).isEqualTo(998877L);
        assertThat(vinculo.identificadorDoChat()).isEqualTo(998877L);
        assertThat(vinculo.codigoConsumidoEm()).isEqualTo(ativadoEm);
        assertThatThrownBy(() -> vinculo.ativar(998877L, 998877L, ativadoEm))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("pendente");

        OffsetDateTime revogadoEm = AGORA.plusMinutes(2);
        vinculo.revogar(revogadoEm);
        vinculo.revogar(AGORA.plusMinutes(3));

        assertThat(vinculo.estado()).isEqualTo(EstadoDoVinculoDeCanal.REVOGADO);
        assertThat(vinculo.revogadoEm()).isEqualTo(revogadoEm);
    }

    @Test
    void deveExpirarVinculoNoLimiteEValidarIdentificadores() {
        VinculoDeCanal vinculo = VinculoDeCanal.preparar(UUID.randomUUID(),
                123456L, "hash", AGORA.plusMinutes(10), AGORA);

        assertThatThrownBy(() -> vinculo.ativar(
                998877L, 887766L, AGORA.plusMinutes(10)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("expirado");
        assertThat(vinculo.estado()).isEqualTo(EstadoDoVinculoDeCanal.EXPIRADO);

        assertThatThrownBy(() -> VinculoDeCanal.preparar(UUID.randomUUID(),
                0, "hash", AGORA.plusMinutes(1), AGORA))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> VinculoDeCanal.preparar(UUID.randomUUID(),
                1, " ", AGORA.plusMinutes(1), AGORA))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> VinculoDeCanal.preparar(UUID.randomUUID(),
                1, "hash", AGORA, AGORA))
                .isInstanceOf(IllegalArgumentException.class);

        VinculoDeCanal grupo = VinculoDeCanal.preparar(UUID.randomUUID(),
                1, "outro-hash", AGORA.plusMinutes(1), AGORA);
        assertThatThrownBy(() -> grupo.ativar(10, -100, AGORA.plusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positivos");
        assertThatThrownBy(() -> grupo.ativar(10, 20, AGORA.plusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("conversa privada");
    }

    @Test
    void deveValidarCoerenciaAoReconstituirVinculoAtivo() {
        assertThatThrownBy(() -> VinculoDeCanal.reconstituir(
                UUID.randomUUID(), UUID.randomUUID(), CanalDeIntegracao.TELEGRAM,
                123456L, null, null, EstadoDoVinculoDeCanal.ATIVO,
                "hash", AGORA.plusMinutes(10), null, AGORA, AGORA, null, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("identidade do Telegram");
        assertThatThrownBy(() -> VinculoDeCanal.reconstituir(
                UUID.randomUUID(), UUID.randomUUID(), CanalDeIntegracao.TELEGRAM,
                123456L, 111L, 222L, EstadoDoVinculoDeCanal.ATIVO,
                "hash", AGORA.plusMinutes(10), AGORA.plusMinutes(1),
                AGORA, AGORA.plusMinutes(1), null, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("conversa privada");
    }

    @Test
    void deveControlarUsoExpiracaoERevogacaoDaCredencial() {
        CredencialDeIntegracao credencial = CredencialDeIntegracao.criar(
                UUID.randomUUID(), "hash-do-token", "mcp_prefixo",
                "agenda:ler", AGORA.plusDays(90), AGORA);

        assertThat(credencial.ativaEm(AGORA.plusDays(90).minusNanos(1))).isTrue();
        assertThat(credencial.ativaEm(AGORA.plusDays(90))).isFalse();

        OffsetDateTime usadaEm = AGORA.plusMinutes(1);
        credencial.registrarUso(usadaEm);
        assertThat(credencial.ultimoUsoEm()).isEqualTo(usadaEm);

        OffsetDateTime revogadaEm = AGORA.plusMinutes(2);
        credencial.revogar(revogadaEm);
        credencial.revogar(AGORA.plusMinutes(3));
        assertThat(credencial.revogadoEm()).isEqualTo(revogadaEm);
        assertThat(credencial.ativaEm(AGORA.plusMinutes(3))).isFalse();
        assertThatThrownBy(() -> credencial.registrarUso(AGORA.plusMinutes(3)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("inativa");
    }

    @Test
    void deveExigirExpiracaoFuturaETermosDaCredencial() {
        assertThatThrownBy(() -> CredencialDeIntegracao.criar(UUID.randomUUID(),
                "hash", "prefixo", "agenda:ler", AGORA, AGORA))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("depois da criacao");
        assertThatThrownBy(() -> CredencialDeIntegracao.criar(UUID.randomUUID(),
                "hash", "prefixo", " ", AGORA.plusDays(1), AGORA))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Escopos");
    }

    @Test
    void devePercorrerTransicoesValidasDaOperacaoAssistida() {
        OperacaoAssistida operacao = novaOperacao();
        assertThat(operacao.estado()).isEqualTo(EstadoDaOperacaoAssistida.PREPARADA);

        operacao.aguardarConfirmacao(AGORA.plusMinutes(1));
        assertThat(operacao.estado())
                .isEqualTo(EstadoDaOperacaoAssistida.AGUARDANDO_CONFIRMACAO);

        operacao.confirmar("assinatura", AGORA.plusMinutes(2));
        assertThat(operacao.estado()).isEqualTo(EstadoDaOperacaoAssistida.CONFIRMADA);
        assertThat(operacao.confirmadaEm()).isEqualTo(AGORA.plusMinutes(2));

        operacao.aplicar("{\"identificador\":\"resultado\"}",
                AGORA.plusMinutes(3));
        assertThat(operacao.estado()).isEqualTo(EstadoDaOperacaoAssistida.APLICADA);
        assertThat(operacao.aplicadaEm()).isEqualTo(AGORA.plusMinutes(3));
        assertThat(operacao.resultado())
                .isEqualTo("{\"identificador\":\"resultado\"}");
        assertThatThrownBy(() -> operacao.cancelar(AGORA.plusMinutes(4)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("finalizada");
    }

    @Test
    void deveImpedirAssinaturaDivergenteETransicoesForaDeOrdem() {
        OperacaoAssistida operacao = novaOperacao();

        assertThatThrownBy(() -> operacao.confirmar(
                "assinatura", AGORA.plusMinutes(1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("nao aguarda");

        operacao.aguardarConfirmacao(AGORA.plusMinutes(1));
        assertThatThrownBy(() -> operacao.confirmar(
                "outra-assinatura", AGORA.plusMinutes(2)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("divergiu");
        assertThat(operacao.estado())
                .isEqualTo(EstadoDaOperacaoAssistida.AGUARDANDO_CONFIRMACAO);
        assertThatThrownBy(() -> operacao.aplicar("{}", AGORA.plusMinutes(2)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("confirmada");
    }

    @Test
    void deveCancelarFalharOuExpirarSemReabrirOperacaoFinalizada() {
        OperacaoAssistida cancelada = novaOperacao();
        cancelada.cancelar(AGORA.plusMinutes(1));
        cancelada.cancelar(AGORA.plusMinutes(2));
        assertThat(cancelada.estado()).isEqualTo(EstadoDaOperacaoAssistida.CANCELADA);
        assertThat(cancelada.canceladaEm()).isEqualTo(AGORA.plusMinutes(1));

        OperacaoAssistida falhou = novaOperacao();
        falhou.registrarFalha("Dependencia indisponivel", AGORA.plusMinutes(1));
        assertThat(falhou.estado()).isEqualTo(EstadoDaOperacaoAssistida.FALHOU);
        assertThat(falhou.falha()).isEqualTo("Dependencia indisponivel");
        falhou.expirar(AGORA.plusHours(1));
        assertThat(falhou.estado()).isEqualTo(EstadoDaOperacaoAssistida.FALHOU);

        OperacaoAssistida expirada = novaOperacao();
        assertThatThrownBy(() -> expirada.aguardarConfirmacao(
                AGORA.plusMinutes(30)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("expirada");
        assertThat(expirada.estado()).isEqualTo(EstadoDaOperacaoAssistida.EXPIRADA);
    }

    @Test
    void deveValidarDadosBasicosDaOperacaoEAuditoria() {
        assertThatThrownBy(() -> OperacaoAssistida.preparar(UUID.randomUUID(), null,
                "tipo invalido", "Resumo", "{}", "assinatura", "{}",
                "chave", "hash", AGORA.plusMinutes(30), AGORA))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("formato invalido");
        assertThatThrownBy(() -> OperacaoAssistida.preparar(UUID.randomUUID(), null,
                "REGISTRAR_ESTUDO", "Resumo", "{}", "assinatura", "{}",
                "chave", "hash", AGORA, AGORA))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("futuro");

        EventoDeAuditoriaDaAutomacao evento = EventoDeAuditoriaDaAutomacao.criar(
                UUID.randomUUID(), null, null, " SISTEMA ", null,
                " OPERACAO_PREPARADA ", "hash", null, " APLICACAO ",
                " SUCESSO ", UUID.randomUUID(), " ", AGORA);

        assertThat(evento.ator()).isEqualTo("SISTEMA");
        assertThat(evento.acao()).isEqualTo("OPERACAO_PREPARADA");
        assertThat(evento.metadados()).isEqualTo("{}");
    }

    private OperacaoAssistida novaOperacao() {
        return OperacaoAssistida.preparar(UUID.randomUUID(), null,
                "REGISTRAR_ESTUDO", "Registrar estudo", "{\"minutos\":30}",
                "assinatura", "{\"plano\":1}", "chave-idempotente", "hash",
                AGORA.plusMinutes(30), AGORA);
    }
}
