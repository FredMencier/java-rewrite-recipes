package org.refactor.eap6.yaml;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.properties.Assertions.properties;
import static org.openrewrite.yaml.Assertions.yaml;

class ConvertPropsToYamlTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new ConvertPropsToYaml());
    }

    @Test
    void convertPropsToYamlTest() {
        rewriteRun(
                properties("myfinancial.system=Quarkus"),
                yaml("""
                                           """,
                        """
                                           myfinancial:
                                            system: Quarkus
                                           """)
        );
    }

}