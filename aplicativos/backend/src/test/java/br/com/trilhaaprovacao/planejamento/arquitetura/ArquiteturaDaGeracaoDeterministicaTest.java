package br.com.trilhaaprovacao.planejamento.arquitetura;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ArquiteturaDaGeracaoDeterministicaTest {
    private static JavaClasses classesDaAplicacao;

    @BeforeAll
    static void importarClasses() {
        classesDaAplicacao = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("br.com.trilhaaprovacao");
    }

    @Test
    void dominioDePlanejamentoNaoDeveDependerDeFrameworkApiOuInfraestrutura() {
        noClasses()
                .that().resideInAPackage("..planejamento.dominio..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..",
                        "jakarta.persistence..",
                        "org.hibernate..",
                        "..planejamento.api..",
                        "..planejamento.infraestrutura..")
                .check(classesDaAplicacao);
    }

    @Test
    void planejamentoDeveUsarAplicacaoDeOutrosModulosSemAcessarSuaInfraestrutura() {
        noClasses()
                .that().resideInAPackage("..planejamento..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..concursos.infraestrutura..",
                        "..conteudos.infraestrutura..")
                .check(classesDaAplicacao);
    }
}
