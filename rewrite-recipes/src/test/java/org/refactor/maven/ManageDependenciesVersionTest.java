package org.refactor.maven;

import org.junit.jupiter.api.Test;
import org.openrewrite.ExecutionContext;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.maven.MavenExecutionContextView;
import org.openrewrite.maven.MavenSettings;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.SourceSpec;

import static org.assertj.core.api.AssertionsForClassTypes.fail;
import static org.openrewrite.maven.Assertions.pomXml;

class ManageDependenciesVersionTest implements RewriteTest {

    @Override
    public ExecutionContext defaultExecutionContext(SourceSpec<?>[] sourceSpecs) {
        ExecutionContext ctx = new InMemoryExecutionContext(t -> fail("Failed to parse sources or run recipe", t));
        MavenExecutionContextView.view(ctx).setMavenSettings(MavenSettings.readMavenSettingsFromDisk(ctx));
        return ctx;
    }

    @Test
    void shouldAddVersionPropertiesForDependenciesInScopeTest() {
        rewriteRun(
                spec -> spec.recipe(new ManageDependenciesVersion("test")),
                //language=xml
                pomXml(
                        """
                                <project>
                                    <groupId>com.mycompany.app</groupId>
                                    <artifactId>my-app</artifactId>
                                    <version>1</version>
                                    <dependencies>
                                        <dependency>
                                            <groupId>org.junit.jupiter</groupId>
                                            <artifactId>junit-jupiter-api</artifactId>
                                            <version>5.6.2</version>
                                            <scope>test</scope>
                                        </dependency>
                                    </dependencies>
                                </project>
                                """,
                        """
                                <project>
                                    <groupId>com.mycompany.app</groupId>
                                    <artifactId>my-app</artifactId>
                                    <version>1</version>
                                    <properties>
                                        <junit-jupiter-api.version>5.6.2</junit-jupiter-api.version>
                                    </properties>
                                    <dependencies>
                                        <dependency>
                                            <groupId>org.junit.jupiter</groupId>
                                            <artifactId>junit-jupiter-api</artifactId>
                                            <version>${junit-jupiter-api.version}</version>
                                            <scope>test</scope>
                                        </dependency>
                                    </dependencies>
                                </project>
                                """
                )
        );
    }

    @Test
    void shouldAddVersionPropertiesForDependenciesWithoutScopeTest() {
        rewriteRun(
                spec -> spec.recipe(new ManageDependenciesVersion()),
                //language=xml
                pomXml(
                        """
                                <project>
                                    <groupId>com.mycompany.app</groupId>
                                    <artifactId>my-app</artifactId>
                                    <version>1</version>
                                    <dependencies>
                                        <dependency>
                                            <groupId>org.junit.jupiter</groupId>
                                            <artifactId>junit-jupiter-api</artifactId>
                                            <version>5.6.2</version>
                                        </dependency>
                                    </dependencies>
                                </project>
                                """,
                        """
                                <project>
                                    <groupId>com.mycompany.app</groupId>
                                    <artifactId>my-app</artifactId>
                                    <version>1</version>
                                    <properties>
                                        <junit-jupiter-api.version>5.6.2</junit-jupiter-api.version>
                                    </properties>
                                    <dependencies>
                                        <dependency>
                                            <groupId>org.junit.jupiter</groupId>
                                            <artifactId>junit-jupiter-api</artifactId>
                                            <version>${junit-jupiter-api.version}</version>
                                        </dependency>
                                    </dependencies>
                                </project>
                                """
                )
        );
    }
}
