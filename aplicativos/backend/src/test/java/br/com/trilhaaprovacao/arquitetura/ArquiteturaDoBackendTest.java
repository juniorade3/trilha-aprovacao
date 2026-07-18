package br.com.trilhaaprovacao.arquitetura;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import jakarta.persistence.Entity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ArquiteturaDoBackendTest {
    private static JavaClasses classesDaAplicacao;

    @BeforeAll
    static void importarClasses() {
        classesDaAplicacao = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("br.com.trilhaaprovacao");
    }

    @Test
    void dominioNaoDeveDependerDeFrameworksOuInfraestrutura() {
        noClasses()
                .that().resideInAPackage("..dominio..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "org.springframework..",
                        "jakarta.persistence..",
                        "jakarta.servlet..",
                        "org.hibernate..",
                        "org.postgresql..",
                        "..api..",
                        "..infraestrutura..")
                .check(classesDaAplicacao);
    }

    @Test
    void entidadesJpaDevemFicarNaInfraestrutura() {
        classes()
                .that().areAnnotatedWith(Entity.class)
                .should().resideInAPackage("..infraestrutura..")
                .check(classesDaAplicacao);
    }

    @Test
    void camadaDeApiNaoDeveAcessarPersistenciaJpaDiretamente() {
        noClasses()
                .that().resideInAPackage("..api..")
                .should().dependOnClassesThat().areAnnotatedWith(Entity.class)
                .check(classesDaAplicacao);
    }
}
