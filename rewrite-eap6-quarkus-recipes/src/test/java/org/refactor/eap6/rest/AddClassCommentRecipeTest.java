package org.refactor.eap6.rest;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class AddClassCommentRecipeTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new AddClassCommentRecipe());
    }

    @Test
    public void addComment() {
        rewriteRun(
                java("""
                        public class Currency {
                            private String code;
                        }
                        """,
                        """
                        public class Currency {
                            private String code;
                        }
                        """)
        );
    }
}