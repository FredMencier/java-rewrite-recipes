package org.refactor.java;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class CheckJavaxTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new CheckJavax());
        spec.parser(JavaParser.fromJavaVersion().classpath("javax.persistence-api"));
    }

    @Test
    void foundJavax() {
        //language=java
        rewriteRun(
                java(
                        """
                                import javax.persistence.EntityManager;
                                class Test {
                                }
                                """,
                        """
                                /*~~(//FIXME javax)~~>*/import javax.persistence.EntityManager;
                                class Test {
                                }
                                """
                )
        );
    }

    @Test
    void notFoundJavax() {
        //language=java
        rewriteRun(
                java(
                        """
                                import java.util.Date;
                                class Test {
                                }
                                """
                ),
                java(
                        """
                                import javax.ejb.EJB;
                                class Test2 {
                                }
                                """
                )
        );
    }

}
