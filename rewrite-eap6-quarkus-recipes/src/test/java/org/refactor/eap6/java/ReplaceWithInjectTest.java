package org.refactor.eap6.java;

import org.refactor.eap6.util.FileUtil;
import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.SourceSpecs;
import org.openrewrite.test.TypeValidation;

import java.io.IOException;

import static org.openrewrite.java.Assertions.java;

class ReplaceWithInjectTest implements RewriteTest {

    private final FileUtil fileUtil = new FileUtil();

    SourceSpecs sourceSpecs;

    {
        try {
            sourceSpecs = java(fileUtil.readResourceFileContent("org/refactor.eap6/java/MyBean.java"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new ReplaceWithInject("MyBean"));
    }

    @Test
    void refactorPropertiesConfigPropertyTest() throws IOException {
        rewriteRun(
                spec -> spec.parser(JavaParser.fromJavaVersion().classpath("slf4j-api")).typeValidationOptions(TypeValidation.none()),
                sourceSpecs,
                java(fileUtil.readResourceFileContent("org/refactor.eap6/java/MyNewInstanceToInject.java"))
        );
    }
}