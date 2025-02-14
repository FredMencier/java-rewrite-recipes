package org.refactor.eap6.java.properties;

import org.refactor.eap6.util.FileUtil;
import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;
import static org.openrewrite.java.Assertions.java;

import java.io.IOException;

class RefactorPropertiesTest  implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new RefactorProperties());
    }

    private final FileUtil fileUtil = new FileUtil();

    @Test
    void refactorPropertiesConfigPropertyTest() throws IOException {
        rewriteRun(
                spec -> spec.parser(JavaParser.fromJavaVersion().classpath("slf4j-api")).typeValidationOptions(TypeValidation.none()),
                java(fileUtil.readResourceFileContent("org/refactor.eap6/properties/MyBusinessClassWithProps.java"),
                        fileUtil.readResourceFileContent("org/refactor.eap6/properties/MyBusinessClassWithPropsTranformed.java"))
        );
    }
}
