package br.com.trilhaaprovacao.importacaoedital.aplicacao;

import br.com.trilhaaprovacao.importacaoedital.dominio.ModoDaImportacaoDeEdital;
import br.com.trilhaaprovacao.importacaoedital.dominio.PoliticaDeReutilizacao;
import br.com.trilhaaprovacao.importacaoedital.dominio.ProblemaDaImportacao;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Porta entre a automacao e o staging da importacao. A preparacao apenas le e
 * valida; vincular a operacao muda somente o estado do staging, e a escrita de
 * negocio ocorre exclusivamente em {@link #aplicar}.
 *
 * <p>A proposta canonica deve identificar a importacao, versao e hash da
 * extracao, cargo, modo, politica, decisoes e a revisao/tentativa vigente. A
 * versao ou tentativa deve mudar para permitir nova operacao depois de uma
 * expiracao; retries do mesmo staging devem reencontrar a operacao anterior.</p>
 */
public interface PreparadorDaImportacaoCompletaDoEdital {

    PreviaDaImportacaoCompleta preparar(
            SolicitacaoDePreparacaoDaImportacao solicitacao);

    void vincularOperacao(UUID identificadorDoUsuario,
            UUID identificadorDaImportacao, UUID identificadorDaOperacao,
            Map<String, Object> propostaCanonica,
            Map<String, Object> versoesConsultadas);

    Map<String, Object> versoesAtuais(UUID identificadorDoUsuario,
            Map<String, Object> propostaCanonica);

    Map<String, Object> aplicar(UUID identificadorDoUsuario,
            UUID identificadorDaOperacao,
            Map<String, Object> propostaCanonica);

    record SolicitacaoDePreparacaoDaImportacao(
            UUID identificadorDoUsuario,
            UUID identificadorDaImportacao,
            String chaveDoCargoSelecionado,
            ModoDaImportacaoDeEdital modo,
            UUID identificadorDoConcursoExistente,
            PoliticaDeReutilizacao politicaDeReutilizacao,
            DecisoesDaImportacaoDoEdital decisoes) {

        public SolicitacaoDePreparacaoDaImportacao {
            Objects.requireNonNull(identificadorDoUsuario);
            Objects.requireNonNull(identificadorDaImportacao);
            if (chaveDoCargoSelecionado == null
                    || chaveDoCargoSelecionado.isBlank()
                    || chaveDoCargoSelecionado.strip().length() > 160) {
                throw new IllegalArgumentException(
                        "Cargo selecionado obrigatorio.");
            }
            chaveDoCargoSelecionado = chaveDoCargoSelecionado.strip();
            Objects.requireNonNull(modo);
            Objects.requireNonNull(politicaDeReutilizacao);
            decisoes = decisoes == null
                    ? DecisoesDaImportacaoDoEdital.vazias() : decisoes;
        }
    }

    record PreviaDaImportacaoCompleta(
            String resumo,
            Map<String, Object> propostaCanonica,
            Map<String, Object> versoesConsultadas,
            ContagensDaImportacao contagens,
            List<ItemDaPreviaDaImportacao> itensACriar,
            List<ItemDaPreviaDaImportacao> itensAReutilizar,
            List<ProblemaDaImportacao> conflitos,
            List<String> incertezas,
            List<String> camposAusentes) {

        public PreviaDaImportacaoCompleta {
            if (resumo == null || resumo.isBlank()
                    || resumo.strip().length() > 500) {
                throw new IllegalArgumentException(
                        "Resumo da importacao obrigatorio.");
            }
            resumo = resumo.strip();
            propostaCanonica = Map.copyOf(
                    Objects.requireNonNull(propostaCanonica));
            versoesConsultadas = Map.copyOf(
                    Objects.requireNonNull(versoesConsultadas));
            Objects.requireNonNull(contagens);
            itensACriar = copiar(itensACriar, 200);
            itensAReutilizar = copiar(itensAReutilizar, 200);
            conflitos = copiar(conflitos, 200);
            incertezas = copiar(incertezas, 200);
            camposAusentes = copiar(camposAusentes, 200);
            conflitos.forEach(problema -> {
                exigirLimite(problema.codigo(), 120);
                exigirLimite(problema.mensagem(), 1_000);
                exigirLimite(problema.caminho(), 500);
            });
            incertezas.forEach(valor -> exigirLimite(valor, 1_000));
            camposAusentes.forEach(valor -> exigirLimite(valor, 500));
        }

        private static <T> List<T> copiar(List<T> valores, int limite) {
            List<T> copia = valores == null ? List.of() : List.copyOf(valores);
            if (copia.size() > limite) {
                throw new IllegalArgumentException(
                        "Previa compacta excede o limite de itens.");
            }
            return copia;
        }

        private static void exigirLimite(String valor, int limite) {
            if (valor != null && valor.length() > limite) {
                throw new IllegalArgumentException(
                        "Texto da previa excede o limite permitido.");
            }
        }
    }

    record ContagensDaImportacao(
            int concursosACriar,
            int editaisACriar,
            int cargosACriar,
            int provasACriar,
            int gruposACriar,
            int materiasACriar,
            int materiasAReutilizar,
            int topicosACriar,
            int topicosAReutilizar,
            int itensDoEditalACriar,
            int sugestoesDeMapeamentoPendentes) {

        public ContagensDaImportacao {
            if (concursosACriar < 0 || editaisACriar < 0 || cargosACriar < 0
                    || provasACriar < 0 || gruposACriar < 0
                    || materiasACriar < 0 || materiasAReutilizar < 0
                    || topicosACriar < 0 || topicosAReutilizar < 0
                    || itensDoEditalACriar < 0
                    || sugestoesDeMapeamentoPendentes < 0) {
                throw new IllegalArgumentException(
                        "Contagens da importacao invalidas.");
            }
        }
    }

    record ItemDaPreviaDaImportacao(
            String tipo,
            String chave,
            String nome,
            UUID identificadorExistente) {

        public ItemDaPreviaDaImportacao {
            if (tipo == null || tipo.isBlank() || tipo.strip().length() > 80
                    || chave == null || chave.isBlank()
                    || chave.strip().length() > 160 || nome == null
                    || nome.isBlank() || nome.strip().length() > 500) {
                throw new IllegalArgumentException(
                        "Item da previa da importacao invalido.");
            }
            tipo = tipo.strip();
            chave = chave.strip();
            nome = nome.strip();
        }
    }
}
