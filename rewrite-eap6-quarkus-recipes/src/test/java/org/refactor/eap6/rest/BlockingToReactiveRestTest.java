package org.refactor.eap6.rest;

import org.refactor.eap6.util.FileUtil;
import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.SourceSpecs;
import org.openrewrite.test.TypeValidation;

import java.io.IOException;

import static org.openrewrite.java.Assertions.java;

class BlockingToReactiveRestTest implements RewriteTest {

    private final FileUtil fileUtil = new FileUtil();

    SourceSpecs currency;

    {
        try {
            currency = java(fileUtil.readResourceFileContent("org/refactor.eap6/java/rest/Currency.java"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new BlockingToReactiveRest());
    }

    @Test
    void convertToReactiveEndpoint() throws IOException {
        rewriteRun(
                spec -> spec.parser(JavaParser.fromJavaVersion()
                        .classpath("jakarta.inject-api", "quarkus-resteasy-reactive", "jakarta.ws.rs-api"))
                        .typeValidationOptions(TypeValidation.none()),
                currency,
                java(fileUtil.readResourceFileContent("org/refactor.eap6/java/rest/FinancialAPI.java"))
        );
    }

}