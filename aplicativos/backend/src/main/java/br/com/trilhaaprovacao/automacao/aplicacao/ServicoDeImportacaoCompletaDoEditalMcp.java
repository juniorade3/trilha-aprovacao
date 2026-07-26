package br.com.trilhaaprovacao.automacao.aplicacao;

import br.com.trilhaaprovacao.automacao.aplicacao.ServicoDeOperacoesAssistidas.OperacaoPreparada;
import br.com.trilhaaprovacao.automacao.infraestrutura.ContextoDaChamadaMcp;
import br.com.trilhaaprovacao.automacao.infraestrutura.ServicoDeSegredosDaAutomacao;
import br.com.trilhaaprovacao.importacaoedital.aplicacao.DecisoesDaImportacaoDoEdital;
import br.com.trilhaaprovacao.importacaoedital.aplicacao.PreparadorDaImportacaoCompletaDoEdital;
import br.com.trilhaaprovacao.importacaoedital.aplicacao.PreparadorDaImportacaoCompletaDoEdital.PreviaDaImportacaoCompleta;
import br.com.trilhaaprovacao.importacaoedital.aplicacao.PreparadorDaImportacaoCompletaDoEdital.SolicitacaoDePreparacaoDaImportacao;
import br.com.trilhaaprovacao.importacaoedital.dominio.ModoDaImportacaoDeEdital;
import br.com.trilhaaprovacao.importacaoedital.dominio.PoliticaDeReutilizacao;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class ServicoDeImportacaoCompletaDoEditalMcp {
    public static final String TIPO = "IMPORTACAO_COMPLETA_DO_EDITAL";

    private final PreparadorDaImportacaoCompletaDoEdital importacoes;
    private final ServicoDeOperacoesAssistidas operacoes;
    private final ServicoDeSegredosDaAutomacao segredos;
    private final ObjectMapper mapeador;

    public ServicoDeImportacaoCompletaDoEditalMcp(
            PreparadorDaImportacaoCompletaDoEdital importacoes,
            ServicoDeOperacoesAssistidas operacoes,
            ServicoDeSegredosDaAutomacao segredos, ObjectMapper mapeador) {
        this.importacoes = importacoes;
        this.operacoes = operacoes;
        this.segredos = segredos;
        this.mapeador = mapeador;
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public ResultadoDaConsultaMcp preparar(ContextoDaChamadaMcp contexto,
            Map<String, Object> argumentos) {
        UUID usuario = contexto.identidade().identificadorDoUsuario();
        UUID importacao = uuid(argumentos, "identificadorDaImportacao");
        SolicitacaoDePreparacaoDaImportacao solicitacao =
                new SolicitacaoDePreparacaoDaImportacao(
                        usuario, importacao,
                        texto(argumentos, "chaveDoCargoSelecionado"),
                        ModoDaImportacaoDeEdital.valueOf(
                                texto(argumentos, "modo")),
                        uuidOpcional(argumentos,
                                "identificadorDoConcursoExistente"),
                        PoliticaDeReutilizacao.valueOf(texto(
                                argumentos, "politicaDeReutilizacao")),
                        decisoes(argumentos));
        PreviaDaImportacaoCompleta previa = importacoes.preparar(solicitacao);
        OperacaoPreparada preparada =
                operacoes.prepararParaConfirmacaoReforcada(usuario,
                        contexto.identidade().identificadorDoVinculo(), TIPO,
                        previa.resumo(),
                        json(ordenar(previa.propostaCanonica())),
                        json(ordenar(previa.versoesConsultadas())),
                        chaveDeIdempotencia(contexto, solicitacao, previa));
        importacoes.vincularOperacao(usuario, importacao,
                preparada.operacao().identificador(),
                previa.propostaCanonica(), previa.versoesConsultadas());

        Map<String, Object> dados = new LinkedHashMap<>();
        dados.put("identificadorDaImportacao", importacao);
        dados.put("identificadorDaOperacao",
                preparada.operacao().identificador());
        dados.put("tipo", preparada.operacao().tipo());
        dados.put("estado", preparada.operacao().estado().name());
        dados.put("nivelDeConfirmacao", "REFORCADA");
        dados.put("resumo", preparada.operacao().resumo());
        dados.put("contagens", previa.contagens());
        dados.put("itensACriar", previa.itensACriar());
        dados.put("itensAReutilizar", previa.itensAReutilizar());
        dados.put("conflitos", previa.conflitos());
        dados.put("incertezas", previa.incertezas());
        dados.put("camposAusentes", previa.camposAusentes());
        dados.put("codigoDeConfirmacao",
                preparada.codigoDeConfirmacao());
        dados.put("fraseDeConfirmacao",
                preparada.codigoDeConfirmacao() == null ? null
                        : "/confirmar " + preparada.codigoDeConfirmacao());
        dados.put("expiraEm", preparada.operacao().expiraEm());
        dados.put("nadaFoiAlterado", true);
        return new ResultadoDaConsultaMcp("1",
                contexto.identificadorDaCorrelacao(),
                OffsetDateTime.now(ZoneOffset.UTC), dados, List.of());
    }

    public Map<String, Object> versoesAtuais(UUID usuario,
            Map<String, Object> proposta) {
        return importacoes.versoesAtuais(usuario, proposta);
    }

    public Map<String, Object> aplicar(UUID usuario, UUID operacao,
            Map<String, Object> proposta) {
        return importacoes.aplicar(usuario, operacao, proposta);
    }

    private String chaveDeIdempotencia(ContextoDaChamadaMcp contexto,
            SolicitacaoDePreparacaoDaImportacao solicitacao,
            PreviaDaImportacaoCompleta previa) {
        Map<String, Object> identidade = new LinkedHashMap<>();
        identidade.put("identificadorDoUsuario",
                contexto.identidade().identificadorDoUsuario());
        identidade.put("identificadorDoVinculo",
                contexto.identidade().identificadorDoVinculo());
        identidade.put("identificadorDaImportacao",
                solicitacao.identificadorDaImportacao());
        identidade.put("chaveDoCargoSelecionado",
                solicitacao.chaveDoCargoSelecionado());
        identidade.put("modo", solicitacao.modo());
        identidade.put("identificadorDoConcursoExistente",
                solicitacao.identificadorDoConcursoExistente());
        identidade.put("politicaDeReutilizacao",
                solicitacao.politicaDeReutilizacao());
        Map<String, Object> decisoes = new LinkedHashMap<>();
        decisoes.put("recursosParaReutilizar", new TreeMap<>(
                solicitacao.decisoes().recursosParaReutilizar()));
        decisoes.put("definirEditalComoPrincipal",
                solicitacao.decisoes().definirEditalComoPrincipal());
        decisoes.put("selecionarCargoCriado",
                solicitacao.decisoes().selecionarCargoCriado());
        identidade.put("decisoes", decisoes);
        identidade.put("proposta", previa.propostaCanonica());
        identidade.put("versoes", previa.versoesConsultadas());
        return "mcp:" + TIPO + ":" + segredos.hash(
                "idempotencia-importacao\n" + json(ordenar(identidade)));
    }

    private DecisoesDaImportacaoDoEdital decisoes(
            Map<String, Object> argumentos) {
        Object valor = argumentos.get("decisoes");
        if (valor == null) return DecisoesDaImportacaoDoEdital.vazias();
        if (!(valor instanceof Map<?, ?> mapa)) {
            throw new IllegalArgumentException("Decisoes invalidas.");
        }
        Map<String, UUID> reutilizacoes = new LinkedHashMap<>();
        Object itens = mapa.get("reutilizacoes");
        if (itens != null) {
            if (!(itens instanceof List<?> lista)) {
                throw new IllegalArgumentException("Reutilizacoes invalidas.");
            }
            if (lista.size() > 1_000) {
                throw new IllegalArgumentException(
                        "Reutilizacoes excedem o limite permitido.");
            }
            for (Object item : lista) {
                if (!(item instanceof Map<?, ?> reutilizacao)) {
                    throw new IllegalArgumentException(
                            "Reutilizacao invalida.");
                }
                String chave = texto(reutilizacao, "chaveExtraida");
                UUID anterior = reutilizacoes.put(chave,
                        uuid(reutilizacao, "identificadorDoRecurso"));
                if (anterior != null) {
                    throw new IllegalArgumentException(
                            "Chave extraida repetida nas decisoes.");
                }
            }
        }
        return new DecisoesDaImportacaoDoEdital(reutilizacoes,
                booleanoOpcional(mapa, "definirEditalComoPrincipal"),
                booleanoOpcional(mapa, "selecionarCargoCriado"));
    }

    private String texto(Map<?, ?> mapa, String chave) {
        Object valor = mapa.get(chave);
        if (!(valor instanceof String texto) || texto.isBlank()) {
            throw new IllegalArgumentException(chave + " e obrigatorio.");
        }
        return texto.strip();
    }

    private UUID uuid(Map<?, ?> mapa, String chave) {
        UUID valor = uuidOpcional(mapa, chave);
        if (valor == null) {
            throw new IllegalArgumentException(chave + " e obrigatorio.");
        }
        return valor;
    }

    private UUID uuidOpcional(Map<?, ?> mapa, String chave) {
        Object valor = mapa.get(chave);
        return valor == null ? null : UUID.fromString(valor.toString());
    }

    private boolean booleanoOpcional(Map<?, ?> mapa, String chave) {
        Object valor = mapa.get(chave);
        if (valor == null) return false;
        if (valor instanceof Boolean booleano) return booleano;
        throw new IllegalArgumentException(chave + " deve ser booleano.");
    }

    private String json(Object valor) {
        try {
            return mapeador.writeValueAsString(valor);
        } catch (Exception excecao) {
            throw new IllegalStateException(
                    "Falha ao serializar importacao assistida.", excecao);
        }
    }

    private Object ordenar(Object valor) {
        if (valor instanceof Map<?, ?> mapa) {
            Map<String, Object> ordenado = new TreeMap<>();
            mapa.forEach((chave, item) -> ordenado.put(
                    String.valueOf(chave), ordenar(item)));
            return ordenado;
        }
        if (valor instanceof List<?> lista) {
            return lista.stream().map(this::ordenar).toList();
        }
        return valor;
    }
}
