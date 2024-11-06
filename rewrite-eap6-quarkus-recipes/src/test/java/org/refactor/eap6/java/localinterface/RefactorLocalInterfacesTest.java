package org.refactor.eap6.java.localinterface;

import org.refactor.eap6.RefactorLocalInterfaces;
import org.refactor.eap6.util.FileUtil;
import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.SourceSpecs;

import java.io.IOException;

import static org.openrewrite.java.Assertions.java;

class RefactorLocalInterfacesTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new RefactorLocalInterfaces());
    }

    private final FileUtil fileUtil = new FileUtil();

    SourceSpecs localInterface;
    SourceSpecs implementationLocalInterface;

    {
        try {
            localInterface = java(fileUtil.readResourceFileContent("org/refactor.eap6/java/localinterface/MyLocalInterface.java"));
            implementationLocalInterface = java(fileUtil.readResourceFileContent("org/refactor.eap6/java/localinterface/MyBusinessEjb.java"),
                    fileUtil.readResourceFileContent("org/refactor.eap6/java/localinterface/MyBusinessEjbTransformed.java"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void refactorLocalInterfacesSupressImplements() throws IOException {
        rewriteRun(
                spec -> spec.parser(JavaParser.fromJavaVersion()
                        .classpath("jakarta.inject-api", "slf4j-api", "jboss-ejb-api_3.1_spec")),
                localInterface,
                java(fileUtil.readResourceFileContent("org/refactor.eap6/java/localinterface/MyBusinessEjb.java"),
                        fileUtil.readResourceFileContent("org/refactor.eap6/java/localinterface/MyBusinessEjbTransformed.java"))
        );
    }

    @Test
    void refactorLocalInterfacesInject() throws IOException {
        rewriteRun(
                spec -> spec.parser(JavaParser.fromJavaVersion()
                        .classpath("jakarta.inject-api", "slf4j-api", "jboss-ejb-api_3.1_spec")),
                localInterface, implementationLocalInterface,
                java(fileUtil.readResourceFileContent("org/refactor.eap6/java/localinterface/MyManagerInjected.java"),
                        fileUtil.readResourceFileContent("org/refactor.eap6/java/localinterface/MyManagerInjectedTransformed.java"))
        );
    }
}
