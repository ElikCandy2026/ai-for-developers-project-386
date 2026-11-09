package com.callbooking.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Guards the dependency graph {@code api -> application -> {domain, persistence -> domain}}.
 * Tests are excluded because the contract drift test intentionally reads the
 * generated API models from the application test package.
 */
@AnalyzeClasses(
        packages = "com.callbooking",
        importOptions = ImportOption.DoNotIncludeTests.class)
class BackendLayersArchitectureTest {

    @ArchTest
    static final ArchRule domainDependsOnJdkOnly = noClasses()
            .that().resideInAPackage("com.callbooking.domain..")
            .should().dependOnClassesThat()
            .resideOutsideOfPackages("java..", "com.callbooking.domain..");

    @ArchTest
    static final ArchRule apiDoesNotDependOnPersistence = noClasses()
            .that().resideInAPackage("com.callbooking.api..")
            .should().dependOnClassesThat()
            .resideInAPackage("com.callbooking.persistence..");

    @ArchTest
    static final ArchRule persistenceDoesNotDependOnApiOrApplication = noClasses()
            .that().resideInAPackage("com.callbooking.persistence..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("com.callbooking.api..", "com.callbooking.application..");

    @ArchTest
    static final ArchRule applicationDoesNotDependOnApi = noClasses()
            .that().resideInAPackage("com.callbooking.application..")
            .should().dependOnClassesThat()
            .resideInAPackage("com.callbooking.api..");

    @ArchTest
    static final ArchRule generatedDtosOnlyInApi = noClasses()
            .that().resideOutsideOfPackage("com.callbooking.api..")
            .should().dependOnClassesThat()
            .resideInAPackage("com.callbooking.api.model..");
}
