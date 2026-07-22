package br.com.trilhaaprovacao.automacao.aplicacao;

import br.com.trilhaaprovacao.automacao.aplicacao.ServicoDeOperacoesAssistidas.OperacaoPreparada;
import br.com.trilhaaprovacao.automacao.infraestrutura.ContextoDaChamadaMcp;
import br.com.trilhaaprovacao.compartilhado.api.RecursoNaoEncontrado;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class ServicoDeOperacoesCriticasMcp {
    private final ServicoDeOperacoesAssistidas operacoes;
    private final JdbcTemplate banco;
    private final ObjectMapper mapeador;

    public ServicoDeOperacoesCriticasMcp(ServicoDeOperacoesAssistidas operacoes,
            JdbcTemplate banco, ObjectMapper mapeador) {
        this.operacoes = operacoes;
        this.banco = banco;
        this.mapeador = mapeador;
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public ResultadoDaConsultaMcp preparar(String tipo,
            ContextoDaChamadaMcp contexto, Map<String, Object> argumentos) {
        UUID usuario = contexto.identidade().identificadorDoUsuario();
        UUID concurso = UUID.fromString(argumentos.get(
                "identificadorDoConcurso").toString());
        Map<String, Object> versoes = versoesAtuais(usuario, concurso);
        Map<String, Object> proposta = Map.of(
                "identificadorDoConcurso", concurso.toString(),
                "impacto", impacto(tipo));
        String evento = contexto.identificadorDoEventoExterno() == null
                ? contexto.identificadorDaCorrelacao().toString()
                : contexto.identificadorDoEventoExterno();
        OperacaoPreparada preparada = operacoes
                .prepararParaConfirmacaoReforcada(usuario,
                        contexto.identidade().identificadorDoVinculo(), tipo,
                        impacto(tipo), json(proposta), json(versoes),
                        "mcp:" + tipo + ":" + evento);
        Map<String, Object> dados = new LinkedHashMap<>();
        dados.put("identificadorDaOperacao",
                preparada.operacao().identificador());
        dados.put("tipo", tipo);
        dados.put("estado", "AGUARDANDO_CONFIRMACAO");
        dados.put("nivelDeConfirmacao", "REFORCADA");
        dados.put("impacto", impacto(tipo));
        dados.put("codigoDeConfirmacao", preparada.codigoDeConfirmacao());
        dados.put("fraseDeConfirmacao",
                "CONFIRMAR " + preparada.codigoDeConfirmacao());
        dados.put("aviso", "Sera exigido um segundo codigo no mesmo chat e sessao.");
        dados.put("expiraEm", preparada.operacao().expiraEm());
        return new ResultadoDaConsultaMcp("1",
                contexto.identificadorDaCorrelacao(),
                OffsetDateTime.now(ZoneOffset.UTC), dados, List.of());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> versoesAtuais(UUID usuario, UUID concurso) {
        List<Map<String, Object>> linhas = banco.queryForList("""
                SELECT identificador, versao, situacao, ativo, atualizado_em
                  FROM concursos
                 WHERE identificador = ? AND usuario_id = ?
                """, concurso, usuario);
        if (linhas.isEmpty()) throw new RecursoNaoEncontrado(
                "CONCURSO_NAO_ENCONTRADO", "Concurso nao encontrado.");
        return new LinkedHashMap<>(linhas.getFirst());
    }

    private String impacto(String tipo) {
        return switch (tipo) {
            case "ATIVACAO_DO_CONCURSO" ->
                    "Ativar este concurso e desativar o concurso ativo atual.";
            case "ARQUIVAMENTO_DO_CONCURSO" ->
                    "Arquivar este concurso e impedir novas alteracoes nele.";
            case "CANCELAMENTO_DO_CONCURSO" ->
                    "Cancelar e desativar este concurso preservando o historico.";
            default -> throw new IllegalArgumentException(
                    "Operacao critica desconhecida.");
        };
    }

    private String json(Object valor) {
        try { return mapeador.writeValueAsString(valor); }
        catch (Exception excecao) { throw new IllegalArgumentException(excecao); }
    }
}
