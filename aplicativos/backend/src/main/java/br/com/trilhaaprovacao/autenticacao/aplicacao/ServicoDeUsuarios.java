package br.com.trilhaaprovacao.autenticacao.aplicacao;

import br.com.trilhaaprovacao.autenticacao.infraestrutura.RepositorioDeUsuarios;
import br.com.trilhaaprovacao.autenticacao.infraestrutura.UsuarioPersistido;
import br.com.trilhaaprovacao.compartilhado.api.ConflitoDeDominio;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ServicoDeUsuarios {

    private final RepositorioDeUsuarios repositorioDeUsuarios;
    private final PasswordEncoder codificadorDeSenha;

    public ServicoDeUsuarios(RepositorioDeUsuarios repositorioDeUsuarios, PasswordEncoder codificadorDeSenha) {
        this.repositorioDeUsuarios = repositorioDeUsuarios;
        this.codificadorDeSenha = codificadorDeSenha;
    }

    @Transactional
    public UsuarioDaAplicacao cadastrar(String nome, String email, String senha) {
        String emailNormalizado = email.trim().toLowerCase(Locale.ROOT);
        if (repositorioDeUsuarios.existsByEmail(emailNormalizado)) {
            throw new ConflitoDeDominio("EMAIL_JA_CADASTRADO", "Ja existe uma conta com este e-mail.");
        }
        return paraAplicacao(repositorioDeUsuarios.save(
                new UsuarioPersistido(nome.trim(), emailNormalizado,
                        codificadorDeSenha.encode(senha))));
    }

    @Transactional(readOnly = true)
    public UsuarioDaAplicacao consultarPorEmail(String email) {
        return repositorioDeUsuarios.findByEmail(email)
                .map(this::paraAplicacao)
                .orElseThrow();
    }

    private UsuarioDaAplicacao paraAplicacao(UsuarioPersistido usuario) {
        return new UsuarioDaAplicacao(
                usuario.identificador(), usuario.nome(), usuario.email());
    }
}
