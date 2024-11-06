package org.refactor.eap6.java.ejb;

import org.exemple.util.FileUtil;
import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.SourceSpecs;

import java.io.IOException;

import static org.openrewrite.java.Assertions.java;

class RefactorSessionBeanTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new RefactorSessionBean());
    }

    private final FileUtil fileUtil = new FileUtil();

    SourceSpecs localInterface;

    {
        try {
            localInterface = java(fileUtil.readResourceFileContent("org/exemple/java/localinterface/MyLocalInterface.java"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void replaceStatelessAnnotation() throws IOException {
        rewriteRun(
                spec -> spec.parser(JavaParser.fromJavaVersion()
                        .classpath("jakarta.inject-api", "slf4j-api", "jboss-ejb-api_3.1_spec")),
                localInterface,
                java(fileUtil.readResourceFileContent("org/exemple/java/localinterface/MyBusinessEjb.java"))
        );
    }

}