package br.com.trilhaaprovacao.estudos.aplicacao;

import br.com.trilhaaprovacao.compartilhado.api.ConflitoDeDominio;
import br.com.trilhaaprovacao.compartilhado.api.RegraDeDominio;
import br.com.trilhaaprovacao.estudos.dominio.RegistroDeEstudo;
import br.com.trilhaaprovacao.estudos.dominio.TipoDeEstudo;
import br.com.trilhaaprovacao.estudos.infraestrutura.RepositorioDeRequisicoesIdempotentesDeEstudo;
import br.com.trilhaaprovacao.estudos.infraestrutura.RequisicaoIdempotenteDeEstudoPersistida;
import br.com.trilhaaprovacao.evidencias.aplicacao.DadosDaEvidencia;
import br.com.trilhaaprovacao.evidencias.dominio.DadosDoPadraoDeErro;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class ServicoDeRegistroIdempotenteDeEstudo {
    private static final String VERSAO_DO_CONTRATO = "1";
    private static final Pattern FORMATO_DA_CHAVE =
            Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,159}");

    private final ServicoDeMateriaisEEstudos estudos;
    private final RepositorioDeRequisicoesIdempotentesDeEstudo requisicoes;
    private final ObjectMapper mapeador;
    private final EntityManager contextoDePersistencia;

    public ServicoDeRegistroIdempotenteDeEstudo(
            ServicoDeMateriaisEEstudos estudos,
            RepositorioDeRequisicoesIdempotentesDeEstudo requisicoes,
            ObjectMapper mapeador,
            EntityManager contextoDePersistencia) {
        this.estudos = estudos;
        this.requisicoes = requisicoes;
        this.mapeador = mapeador;
        this.contextoDePersistencia = contextoDePersistencia;
    }

    @Transactional
    public RegistroDeEstudo registrar(
            UUID usuario, String chaveInformada,
            UUID topico, UUID material, TipoDeEstudo tipo,
            OffsetDateTime dataHora, int duracao, String observacao,
            DadosDaEvidencia evidencia) {
        String chave = validarChaveOpcional(chaveInformada);
        TipoDeEstudo tipoEfetivo = tipo == null ? TipoDeEstudo.OUTRA : tipo;
        if (chave == null) {
            return estudos.registrarEstudo(usuario, topico, material,
                    tipoEfetivo, dataHora, duracao, observacao, evidencia, true);
        }

        String hash = hashCanonico(usuario, topico, material, tipoEfetivo,
                dataHora, duracao, observacao, evidencia);
        requisicoes.bloquearChaveDeIdempotencia(usuario, chave);
        var anterior = requisicoes
                .findByIdentificadorDoUsuarioAndChaveDeIdempotencia(
                        usuario, chave);
        if (anterior.isPresent()) {
            var recibo = anterior.get();
            if (!iguais(hash, recibo.hashDaRequisicao())) {
                throw new ConflitoDeDominio(
                        "CHAVE_DE_IDEMPOTENCIA_REUTILIZADA",
                        "A chave de idempotencia ja foi usada com outros dados.");
            }
            return estudos.obterEstudo(
                    usuario, recibo.identificadorDoRegistroDeEstudo());
        }

        RegistroDeEstudo registro = estudos.registrarEstudo(
                usuario, topico, material, tipoEfetivo, dataHora,
                duracao, observacao, evidencia, true);
        requisicoes.saveAndFlush(
                new RequisicaoIdempotenteDeEstudoPersistida(
                        usuario, chave, hash, registro.identificador()));
        contextoDePersistencia.clear();
        return estudos.obterEstudo(usuario, registro.identificador());
    }

    private String validarChaveOpcional(String informada) {
        if (informada == null) {
            return null;
        }
        String chave = informada.trim();
        if (!FORMATO_DA_CHAVE.matcher(chave).matches()) {
            throw new RegraDeDominio("CHAVE_DE_IDEMPOTENCIA_INVALIDA",
                    "A chave de idempotencia deve conter de 1 a 160 caracteres "
                            + "alfanumericos, ponto, sublinhado, dois-pontos ou hifen.");
        }
        return chave;
    }

    private String hashCanonico(
            UUID usuario, UUID topico, UUID material, TipoDeEstudo tipo,
            OffsetDateTime dataHora, int duracao, String observacao,
            DadosDaEvidencia evidencia) {
        Map<String, Object> comando = new TreeMap<>();
        comando.put("versao", VERSAO_DO_CONTRATO);
        comando.put("usuario", usuario.toString());
        comando.put("topico", String.valueOf(topico));
        comando.put("material", material == null ? null : material.toString());
        comando.put("tipo", tipo.name());
        comando.put("dataHora", dataHora.toInstant().toString());
        comando.put("duracaoEmMinutos", duracao);
        comando.put("observacao", normalizarTextoOpcional(observacao));
        comando.put("evidencia", evidenciaCanonica(evidencia));
        try {
            byte[] serializado = mapeador.writeValueAsBytes(comando);
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(serializado));
        } catch (NoSuchAlgorithmException excecao) {
            throw new IllegalStateException("SHA-256 indisponivel.", excecao);
        }
    }

    private Map<String, Object> evidenciaCanonica(DadosDaEvidencia evidencia) {
        if (evidencia == null) {
            return null;
        }
        Map<String, Object> resultado = new TreeMap<>();
        resultado.put("quantidadeDeQuestoes", evidencia.quantidadeDeQuestoes());
        resultado.put("quantidadeDeAcertos", evidencia.quantidadeDeAcertos());
        resultado.put("nivelDeRecordacao", evidencia.nivelDeRecordacao());
        resultado.put("dificuldadePercebida", evidencia.dificuldadePercebida());
        resultado.put("padroesDeErro", padroesCanonicos(evidencia.padroesDeErro()));
        return resultado;
    }

    private List<Map<String, Object>> padroesCanonicos(
            List<DadosDoPadraoDeErro> padroes) {
        return padroes.stream()
                .sorted(Comparator.comparing(DadosDoPadraoDeErro::descricaoNormalizada))
                .map(padrao -> {
                    Map<String, Object> item = new TreeMap<>();
                    item.put("descricao", padrao.descricaoNormalizada());
                    item.put("quantidadeDeOcorrencias",
                            padrao.quantidadeDeOcorrencias());
                    return item;
                })
                .toList();
    }

    private String normalizarTextoOpcional(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    private boolean iguais(String primeiro, String segundo) {
        return MessageDigest.isEqual(
                primeiro.getBytes(StandardCharsets.UTF_8),
                segundo.getBytes(StandardCharsets.UTF_8));
    }
}
