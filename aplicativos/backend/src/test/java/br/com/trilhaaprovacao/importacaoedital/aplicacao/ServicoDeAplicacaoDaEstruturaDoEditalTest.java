package br.com.trilhaaprovacao.importacaoedital.aplicacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.trilhaaprovacao.concursos.aplicacao.ServicoDaEstruturaDeConcursos;
import br.com.trilhaaprovacao.concursos.dominio.CaraterDaProva;
import br.com.trilhaaprovacao.concursos.dominio.CargoDoConcurso;
import br.com.trilhaaprovacao.concursos.dominio.Concurso;
import br.com.trilhaaprovacao.concursos.dominio.Edital;
import br.com.trilhaaprovacao.concursos.dominio.GrupoDeConteudo;
import br.com.trilhaaprovacao.concursos.dominio.MateriaDaProva;
import br.com.trilhaaprovacao.concursos.dominio.NivelDeEscolaridade;
import br.com.trilhaaprovacao.concursos.dominio.Prova;
import br.com.trilhaaprovacao.concursos.dominio.SituacaoDoConcurso;
import br.com.trilhaaprovacao.concursos.dominio.TipoDeProva;
import br.com.trilhaaprovacao.conteudoprogramatico.aplicacao.ServicoDeConteudoProgramatico;
import br.com.trilhaaprovacao.conteudoprogramatico.dominio.ItemDoEdital;
import br.com.trilhaaprovacao.conteudos.aplicacao.ServicoDeMaterias;
import br.com.trilhaaprovacao.conteudos.aplicacao.ServicoDeTopicos;
import br.com.trilhaaprovacao.conteudos.dominio.Materia;
import br.com.trilhaaprovacao.conteudos.dominio.TopicoDaMateria;
import br.com.trilhaaprovacao.importacaoedital.dominio.ExtracaoEstruturadaDoEdital;
import br.com.trilhaaprovacao.importacaoedital.dominio.ExtracaoEstruturadaDoEdital.CargoExtraido;
import br.com.trilhaaprovacao.importacaoedital.dominio.ExtracaoEstruturadaDoEdital.ConcursoExtraido;
import br.com.trilhaaprovacao.importacaoedital.dominio.ExtracaoEstruturadaDoEdital.EditalExtraido;
import br.com.trilhaaprovacao.importacaoedital.dominio.ExtracaoEstruturadaDoEdital.FonteDoEdital;
import br.com.trilhaaprovacao.importacaoedital.dominio.ExtracaoEstruturadaDoEdital.GrupoExtraido;
import br.com.trilhaaprovacao.importacaoedital.dominio.ExtracaoEstruturadaDoEdital.ItemExtraido;
import br.com.trilhaaprovacao.importacaoedital.dominio.ExtracaoEstruturadaDoEdital.MateriaExtraida;
import br.com.trilhaaprovacao.importacaoedital.dominio.ExtracaoEstruturadaDoEdital.ProvaExtraida;
import br.com.trilhaaprovacao.importacaoedital.dominio.ExtracaoEstruturadaDoEdital.TopicoExtraido;
import br.com.trilhaaprovacao.importacaoedital.dominio.ModoDaImportacaoDeEdital;
import br.com.trilhaaprovacao.importacaoedital.dominio.PoliticaDeReutilizacao;
import br.com.trilhaaprovacao.importacaoedital.dominio.ProvenienciaDoDado;
import br.com.trilhaaprovacao.importacaoedital.dominio.ValorExtraido;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ServicoDeAplicacaoDaEstruturaDoEditalTest {
    private final ServicoDaEstruturaDeConcursos estrutura =
            mock(ServicoDaEstruturaDeConcursos.class);
    private final ServicoDeMaterias materias = mock(ServicoDeMaterias.class);
    private final ServicoDeTopicos topicos = mock(ServicoDeTopicos.class);
    private final ServicoDeConteudoProgramatico conteudo =
            mock(ServicoDeConteudoProgramatico.class);
    private final ServicoDeAplicacaoDaEstruturaDoEdital servico =
            new ServicoDeAplicacaoDaEstruturaDoEdital(
                    estrutura, materias, topicos, conteudo);

    @Test
    void deveAplicarSomenteCargoSelecionadoPreservandoHierarquias() {
        UUID usuario = UUID.randomUUID();
        UUID importacao = UUID.randomUUID();
        UUID concursoId = UUID.randomUUID();
        UUID editalId = UUID.randomUUID();
        UUID cargoId = UUID.randomUUID();
        UUID provaId = UUID.randomUUID();
        UUID grupoId = UUID.randomUUID();
        UUID materiaId = UUID.randomUUID();
        UUID materiaDaProvaId = UUID.randomUUID();
        UUID topicoPaiId = UUID.randomUUID();
        UUID topicoFilhoId = UUID.randomUUID();
        UUID itemPaiId = UUID.randomUUID();
        UUID itemFilhoId = UUID.randomUUID();

        Concurso concurso = mock(Concurso.class);
        Edital edital = mock(Edital.class);
        CargoDoConcurso cargo = mock(CargoDoConcurso.class);
        Prova prova = mock(Prova.class);
        GrupoDeConteudo grupo = mock(GrupoDeConteudo.class);
        Materia materia = mock(Materia.class);
        MateriaDaProva materiaDaProva = mock(MateriaDaProva.class);
        TopicoDaMateria topicoPai = mock(TopicoDaMateria.class);
        TopicoDaMateria topicoFilho = mock(TopicoDaMateria.class);
        ItemDoEdital itemPai = mock(ItemDoEdital.class);
        ItemDoEdital itemFilho = mock(ItemDoEdital.class);
        when(concurso.identificador()).thenReturn(concursoId);
        when(concurso.situacao()).thenReturn(SituacaoDoConcurso.PLANEJADO);
        when(edital.identificador()).thenReturn(editalId);
        when(cargo.identificador()).thenReturn(cargoId);
        when(prova.identificador()).thenReturn(provaId);
        when(grupo.identificador()).thenReturn(grupoId);
        when(materia.identificador()).thenReturn(materiaId);
        when(materiaDaProva.identificador()).thenReturn(materiaDaProvaId);
        when(topicoPai.identificador()).thenReturn(topicoPaiId);
        when(topicoFilho.identificador()).thenReturn(topicoFilhoId);
        when(itemPai.identificador()).thenReturn(itemPaiId);
        when(itemFilho.identificador()).thenReturn(itemFilhoId);

        when(estrutura.criarConcurso(eq(usuario), eq("Concurso X"), any(),
                any(), any(), eq(SituacaoDoConcurso.PLANEJADO), any()))
                .thenReturn(concurso);
        when(estrutura.obterConcurso(usuario, concursoId)).thenReturn(concurso);
        when(estrutura.criarEdital(eq(usuario), eq(concursoId),
                eq("Edital 1"), any(), any(), any(), any(), any()))
                .thenReturn(edital);
        when(estrutura.criarCargo(eq(usuario), eq(concursoId),
                eq("Cargo A"), any(), any(),
                eq(NivelDeEscolaridade.SUPERIOR), eq(1))).thenReturn(cargo);
        when(estrutura.criarProva(eq(usuario), eq(cargoId),
                eq("Objetiva"), eq(TipoDeProva.OBJETIVA),
                eq(CaraterDaProva.ELIMINATORIO_E_CLASSIFICATORIO), eq(1),
                any(), any(), any(), any(), any())).thenReturn(prova);
        when(estrutura.criarGrupo(eq(usuario), eq(provaId),
                eq("Conhecimentos"), eq(1), any(), any(), any()))
                .thenReturn(grupo);
        when(materias.criar(eq(usuario), eq("Direito"), any(), any()))
                .thenReturn(materia);
        when(estrutura.criarMateriaDaProva(eq(usuario), eq(grupoId),
                eq(materiaId), eq(1), any(), any(), any()))
                .thenReturn(materiaDaProva);
        when(topicos.criar(eq(usuario), eq(materiaId), eq(null), eq("1"),
                eq("Constitucional"), any(), eq(1))).thenReturn(topicoPai);
        when(topicos.criar(eq(usuario), eq(materiaId), eq(topicoPaiId),
                eq("1.1"), eq("Direitos fundamentais"), any(), eq(2)))
                .thenReturn(topicoFilho);
        when(conteudo.criarItem(eq(usuario), eq(materiaDaProvaId),
                eq(editalId), eq("Constituicao"), eq(null), eq(1),
                eq("1"), eq("constituicao"), eq(importacao)))
                .thenReturn(itemPai);
        when(conteudo.criarItem(eq(usuario), eq(materiaDaProvaId),
                eq(editalId), eq("Direitos fundamentais"), eq(itemPaiId),
                eq(2), eq("1.1"), eq("direitos fundamentais"),
                eq(importacao))).thenReturn(itemFilho);

        ResultadoDaAplicacaoDaImportacao resultado = servico.aplicar(
                new SolicitacaoDeAplicacaoDaImportacao(importacao, usuario,
                        extracao(), "cargo-a", ModoDaImportacaoDeEdital.CRIAR_NOVO,
                        null, PoliticaDeReutilizacao.EXIGIR_DECISAO,
                        DecisoesDaImportacaoDoEdital.vazias()));

        assertThat(resultado.identificadorDoConcurso()).isEqualTo(concursoId);
        assertThat(resultado.itensCriados()).isEqualTo(2);
        assertThat(resultado.identificadoresPorChave())
                .containsEntry("topico-filho", topicoFilhoId)
                .containsEntry("item-filho", itemFilhoId);
        verify(estrutura, never()).criarCargo(eq(usuario), eq(concursoId),
                eq("Cargo B"), any(), any(), any(), anyInt());
        verify(materias, never()).criar(eq(usuario), eq("Administracao"),
                any(), any());
        verify(conteudo).criarSugestaoDeMapeamento(
                usuario, itemFilhoId, topicoFilhoId);
    }

    private ExtracaoEstruturadaDoEdital extracao() {
        CargoExtraido cargoA = new CargoExtraido("cargo-a", v("Cargo A"),
                nulo(), nulo(), v(NivelDeEscolaridade.SUPERIOR), 1);
        CargoExtraido cargoB = new CargoExtraido("cargo-b", v("Cargo B"),
                nulo(), nulo(), v(NivelDeEscolaridade.MEDIO), 2);
        GrupoExtraido grupoA = new GrupoExtraido("grupo-a",
                v("Conhecimentos"), 1, nulo(), nulo(), nulo());
        GrupoExtraido grupoB = new GrupoExtraido("grupo-b",
                v("Conhecimentos B"), 1, nulo(), nulo(), nulo());
        ProvaExtraida provaA = new ProvaExtraida("prova-a", "cargo-a",
                v("Objetiva"), v(TipoDeProva.OBJETIVA),
                v(CaraterDaProva.ELIMINATORIO_E_CLASSIFICATORIO), 1,
                nulo(), nulo(), nulo(), nulo(), nulo(), List.of(grupoA));
        ProvaExtraida provaB = new ProvaExtraida("prova-b", "cargo-b",
                v("Objetiva B"), v(TipoDeProva.OBJETIVA),
                v(CaraterDaProva.CLASSIFICATORIO), 1, nulo(), nulo(), nulo(),
                nulo(), nulo(), List.of(grupoB));
        TopicoExtraido pai = new TopicoExtraido("topico-pai", null,
                v("1"), v("Constitucional"), nulo(), 1);
        TopicoExtraido filho = new TopicoExtraido("topico-filho",
                "topico-pai", v("1.1"), v("Direitos fundamentais"),
                nulo(), 2);
        ItemExtraido itemPai = new ItemExtraido("item-pai", null, v("1"),
                v("Constituicao"), "constituicao", 1, null);
        ItemExtraido itemFilho = new ItemExtraido("item-filho", "item-pai",
                v("1.1"), v("Direitos fundamentais"),
                "direitos fundamentais", 2, "topico-filho");
        MateriaExtraida materiaA = new MateriaExtraida("materia-a", "cargo-a",
                "prova-a", "grupo-a", v("Direito"), nulo(), 1, nulo(),
                nulo(), nulo(), List.of(pai, filho),
                List.of(itemPai, itemFilho));
        MateriaExtraida materiaB = new MateriaExtraida("materia-b", "cargo-b",
                "prova-b", "grupo-b", v("Administracao"), nulo(), 1,
                nulo(), nulo(), nulo(), List.of(), List.of());
        return new ExtracaoEstruturadaDoEdital("1",
                new FonteDoEdital("edital.txt", "a".repeat(64), 1),
                new ConcursoExtraido(v("Concurso X"), nulo(), nulo(), nulo(),
                        nulo()),
                new EditalExtraido(v("Edital 1"), v("1"), v(2026), nulo(),
                        nulo()), List.of(cargoA, cargoB),
                List.of(provaA, provaB), List.of(materiaA, materiaB),
                List.of(), List.of());
    }

    private <T> ValorExtraido<T> v(T valor) {
        return new ValorExtraido<>(valor, new BigDecimal("0.9900"),
                new ProvenienciaDoDado(1, "fixture", "trecho"), false);
    }

    private <T> ValorExtraido<T> nulo() {
        return new ValorExtraido<>(null, BigDecimal.ZERO, null, false);
    }
}
