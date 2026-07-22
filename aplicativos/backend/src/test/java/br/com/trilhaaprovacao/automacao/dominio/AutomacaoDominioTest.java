package br.com.trilhaaprovacao.automacao.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AutomacaoDominioTest {
    private static final OffsetDateTime AGORA =
            OffsetDateTime.of(2026, 7, 21, 18, 0, 0, 0, ZoneOffset.UTC);

    @Test
    void deveAtivarERevogarVinculoPreservandoHashDoCodigo() {
        VinculoDeCanal vinculo = VinculoDeCanal.preparar(
                UUID.randomUUID(), 123L, "hash-do-codigo", AGORA.plusMinutes(10), AGORA);

        vinculo.ativar(456L, 456L, AGORA.plusMinutes(1));

        assertThat(vinculo.estado()).isEqualTo(EstadoDoVinculoDeCanal.ATIVO);
        assertThat(vinculo.identificadorExterno()).isEqualTo(456L);
        assertThat(vinculo.codigoDeVinculoHash()).isEqualTo("hash-do-codigo");
        assertThat(vinculo.codigoValidoEm(AGORA.plusMinutes(2))).isFalse();

        vinculo.revogar(AGORA.plusMinutes(2));
        assertThat(vinculo.estado()).isEqualTo(EstadoDoVinculoDeCanal.REVOGADO);
        assertThat(vinculo.revogadoEm()).isEqualTo(AGORA.plusMinutes(2));
        assertThat(vinculo.codigoDeVinculoHash()).isEqualTo("hash-do-codigo");
    }

    @Test
    void deveExpirarCodigoSemPermitirAtivacaoPosterior() {
        VinculoDeCanal vinculo = VinculoDeCanal.preparar(
                UUID.randomUUID(), 123L, "hash-do-codigo", AGORA.plusMinutes(10), AGORA);

        assertThatThrownBy(() -> vinculo.ativar(
                456L, 456L, AGORA.plusMinutes(10)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Codigo de vinculo expirado.");
        assertThat(vinculo.estado()).isEqualTo(EstadoDoVinculoDeCanal.EXPIRADO);
        assertThat(vinculo.codigoDeVinculoHash()).isEqualTo("hash-do-codigo");
    }

    @Test
    void deveControlarValidadeUsoERevogacaoDaCredencial() {
        CredencialDeIntegracao credencial = CredencialDeIntegracao.criar(
                UUID.randomUUID(), "hash-do-token", "mcp_prefixo",
                "agenda:ler", AGORA.plusDays(90), AGORA);

        credencial.registrarUso(AGORA.plusMinutes(1));
        assertThat(credencial.ultimoUsoEm()).isEqualTo(AGORA.plusMinutes(1));
        assertThat(credencial.ativaEm(AGORA.plusDays(89))).isTrue();

        credencial.revogar(AGORA.plusMinutes(2));
        assertThat(credencial.ativaEm(AGORA.plusMinutes(3))).isFalse();
        assertThatThrownBy(() -> credencial.registrarUso(AGORA.plusMinutes(3)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Credencial inativa.");
    }

    @Test
    void deveImpedirAplicacaoSemConfirmacaoEManterTransicoesTerminais() {
        OperacaoAssistida operacao = OperacaoAssistida.preparar(UUID.randomUUID(),
                null, "REGISTRAR_ESTUDO", "Registrar estudo", "{}",
                "assinatura", "{}", "chave-idempotente", "hash-da-requisicao",
                AGORA.plusMinutes(30), AGORA);

        assertThatThrownBy(() -> operacao.aplicar("{}", AGORA.plusMinutes(1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Operacao ainda nao foi confirmada.");

        operacao.cancelar(AGORA.plusMinutes(2));
        assertThat(operacao.estado()).isEqualTo(EstadoDaOperacaoAssistida.CANCELADA);
        assertThatThrownBy(() -> operacao.registrarFalha(
                "falha", AGORA.plusMinutes(3)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Operacao finalizada nao aceita falha.");
    }
}
