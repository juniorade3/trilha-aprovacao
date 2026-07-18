package br.com.trilhaaprovacao.concursos.dominio;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

public record CargoDoConcurso(
        UUID identificador,
        UUID identificadorDoConcurso,
        String nome,
        String nomeNormalizado,
        String area,
        String especialidade,
        NivelDeEscolaridade nivelDeEscolaridade,
        boolean selecionado,
        int ordem,
        OffsetDateTime criadoEm,
        OffsetDateTime atualizadoEm,
        long versao) {

    public CargoDoConcurso {
        Objects.requireNonNull(identificador);
        Objects.requireNonNull(identificadorDoConcurso);
        nome = ValidacaoDoConcurso.textoObrigatorio(nome, "Nome");
        nomeNormalizado = ValidacaoDoConcurso.chave(nome);
        area = ValidacaoDoConcurso.textoOpcional(area);
        especialidade = ValidacaoDoConcurso.textoOpcional(especialidade);
        Objects.requireNonNull(nivelDeEscolaridade);
        ValidacaoDoConcurso.ordemPositiva(ordem);
        Objects.requireNonNull(criadoEm);
        Objects.requireNonNull(atualizadoEm);
    }

    public static CargoDoConcurso criar(UUID concurso, String nome, String area,
            String especialidade, NivelDeEscolaridade nivel, int ordem) {
        OffsetDateTime agora = OffsetDateTime.now();
        return new CargoDoConcurso(UUID.randomUUID(), concurso, nome, null, area,
                especialidade, nivel, false, ordem, agora, agora, 0);
    }

    public CargoDoConcurso alterar(String novoNome, String novaArea,
            String novaEspecialidade, NivelDeEscolaridade novoNivel, int novaOrdem) {
        return new CargoDoConcurso(identificador, identificadorDoConcurso, novoNome, null,
                novaArea, novaEspecialidade, novoNivel, selecionado, novaOrdem,
                criadoEm, OffsetDateTime.now(), versao);
    }

    public CargoDoConcurso definirSelecao(boolean deveSelecionar) {
        return new CargoDoConcurso(identificador, identificadorDoConcurso, nome,
                nomeNormalizado, area, especialidade, nivelDeEscolaridade, deveSelecionar,
                ordem, criadoEm, OffsetDateTime.now(), versao);
    }
}
