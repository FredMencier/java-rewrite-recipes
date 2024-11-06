package org.refactor.eap6.java.properties;

import org.exemple.util.FileUtil;
import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

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
                java(fileUtil.readResourceFileContent("org/exemple/properties/MyBusinessClassWithProps.java"),
                        fileUtil.readResourceFileContent("org/exemple/properties/MyBusinessClassWithPropsTranformed.java"))
        );
    }
}
