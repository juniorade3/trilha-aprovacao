package br.com.trilhaaprovacao.automacao.infraestrutura;

import br.com.trilhaaprovacao.autenticacao.infraestrutura.RepositorioDeUsuarios;
import br.com.trilhaaprovacao.autenticacao.infraestrutura.SituacaoDoUsuario;
import br.com.trilhaaprovacao.automacao.dominio.CredencialDeIntegracao;
import br.com.trilhaaprovacao.automacao.dominio.EstadoDoVinculoDeCanal;
import br.com.trilhaaprovacao.automacao.dominio.VinculoDeCanal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AutenticadorDeCredencialMcp {
    private static final int TAMANHO_MAXIMO_DO_TOKEN = 256;

    private final RepositorioDeCredenciaisDeIntegracao credenciais;
    private final RepositorioDeVinculosDeCanal vinculos;
    private final RepositorioDeUsuarios usuarios;
    private final ServicoDeSegredosDaAutomacao segredos;

    public AutenticadorDeCredencialMcp(
            RepositorioDeCredenciaisDeIntegracao credenciais,
            RepositorioDeVinculosDeCanal vinculos,
            RepositorioDeUsuarios usuarios,
            ServicoDeSegredosDaAutomacao segredos) {
        this.credenciais = credenciais;
        this.vinculos = vinculos;
        this.usuarios = usuarios;
        this.segredos = segredos;
    }

    @Transactional
    public IdentidadeDaIntegracaoMcp autenticar(String token,
            String agenteInformado, String sessaoInformada) {
        if (token == null || !token.startsWith("mcp_")
                || token.length() < 32 || token.length() > TAMANHO_MAXIMO_DO_TOKEN) {
            throw invalida();
        }
        OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
        CredencialDeIntegracao credencial = credenciais
                .findByTokenHash(segredos.hash(token))
                .map(CredencialDeIntegracaoPersistida::paraDominio)
                .filter(item -> item.ativaEm(agora))
                .orElseThrow(this::invalida);
        VinculoDeCanal vinculo = vinculos.findById(
                        credencial.identificadorDoVinculo())
                .map(VinculoDeCanalPersistido::paraDominio)
                .filter(item -> item.estado() == EstadoDoVinculoDeCanal.ATIVO)
                .orElseThrow(this::invalida);
        if (vinculo.provisionadoEm() == null
                || !segredos.corresponde(
                        vinculo.identificadorDoAgente(), agenteInformado)
                || !segredos.corresponde(
                        vinculo.identificadorDaSessao(), sessaoInformada)) {
            throw invalida();
        }
        boolean usuarioAtivo = usuarios.findById(vinculo.identificadorDoUsuario())
                .filter(usuario -> usuario.situacao() == SituacaoDoUsuario.ATIVO)
                .isPresent();
        if (!usuarioAtivo || credenciais.registrarUso(
                credencial.identificador(), agora) != 1) {
            throw invalida();
        }
        return new IdentidadeDaIntegracaoMcp(
                vinculo.identificadorDoUsuario(), vinculo.identificador(),
                credencial.identificador(), vinculo.identificadorDoBot(),
                vinculo.identificadorExterno(), vinculo.identificadorDoAgente(),
                vinculo.identificadorDaSessao(), vinculo.versao(),
                escopos(credencial.escopos()));
    }

    private Set<String> escopos(String valor) {
        return Arrays.stream(valor.trim().split("\\s+"))
                .filter(item -> !item.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }

    private BadCredentialsException invalida() {
        return new BadCredentialsException(
                "Credencial de integracao invalida, expirada ou revogada.");
    }
}
