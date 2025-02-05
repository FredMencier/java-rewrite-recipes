package org.refactor.maven;

import org.junit.jupiter.api.Test;
import org.openrewrite.ExecutionContext;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.maven.MavenExecutionContextView;
import org.openrewrite.maven.MavenSettings;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.SourceSpec;

import static org.assertj.core.api.AssertionsForClassTypes.fail;
import static org.openrewrite.java.Assertions.mavenProject;
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
    void shouldAddVersionPropertiesForDependenciesTest() {
        rewriteRun(
                spec -> spec.recipe(new ManageDependenciesVersion()),

                mavenProject("OpenAPIGenerator",

                        //language=xml
                        pomXml(
                                """

                                        <?xml version="1.0" encoding="UTF-8"?>
                                        <project>
                                            <modelVersion>4.0.0</modelVersion>
                                                                                
                                            <groupId>com.mycompany.app</groupId>
                                            <artifactId>my-app</artifactId>
                                            <packaging>pom</packaging>
                                            <version>1</version>
                                            <modules>
                                                <module>GeneratorClientEAP6</module>
                                            </modules>
                                                                                
                                            <properties>
                                                <maven-deploy-plugin.version>3.0.0-M1</maven-deploy-plugin.version>
                                            </properties>
                                           
                                            <build>
                                                <plugins>
                                                    <plugin>
                                                        <groupId>org.apache.maven.plugins</groupId>
                                                        <artifactId>maven-deploy-plugin</artifactId>
                                                        <version>${maven-deploy-plugin.version}</version>
                                                    </plugin>
                                                </plugins>
                                            </build>
                                        </project>
                                        """
                        ),
                        mavenProject("GeneratorClientEAP6",
                                //language=xml
                                pomXml(
                                        """
                                                 <?xml version="1.0" encoding="UTF-8"?>
                                                 <project>
                                                     <parent>
                                                         <artifactId>my-app</artifactId>
                                                         <groupId>com.mycompany.app</groupId>
                                                         <version>1</version>
                                                     </parent>
                                                     <modelVersion>4.0.0</modelVersion>
                                                        
                                                     <artifactId>GeneratorEAP6</artifactId>
                                                     <version>1.0</version>
                                                        
                                                     <properties>
                                                         <templates.dir>${project.parent.basedir}/templates/Java</templates.dir>
                                                         <junit-jupiter.version>5.9.2</junit-jupiter.version>
                                                     </properties>
                                                        
                                                    <dependencies>
                                                        <dependency>
                                                            <groupId>javax.annotation</groupId>
                                                            <artifactId>javax.annotation-api</artifactId>
                                                            <version>1.3.2</version>
                                                        </dependency>
                                                        <dependency>
                                                            <groupId>org.junit.jupiter</groupId>
                                                            <artifactId>junit-jupiter-engine</artifactId>
                                                            <version>${junit-jupiter.version}</version>
                                                            <scope>test</scope>
                                                        </dependency>
                                                    </dependencies>
                                                        
                                                     <build/>
                                                        
                                                 </project>
                                                """,
                                        """
                                                 <?xml version="1.0" encoding="UTF-8"?>
                                                 <project>
                                                     <parent>
                                                         <artifactId>my-app</artifactId>
                                                         <groupId>com.mycompany.app</groupId>
                                                         <version>1</version>
                                                     </parent>
                                                     <modelVersion>4.0.0</modelVersion>
                                                        
                                                     <artifactId>GeneratorEAP6</artifactId>
                                                     <version>1.0</version>
                                                        
                                                     <properties>
                                                         <javax.annotation-api.version>1.3.2</javax.annotation-api.version>
                                                         <templates.dir>${project.parent.basedir}/templates/Java</templates.dir>
                                                         <junit-jupiter.version>5.9.2</junit-jupiter.version>
                                                     </properties>
                                                        
                                                    <dependencies>
                                                        <dependency>
                                                            <groupId>javax.annotation</groupId>
                                                            <artifactId>javax.annotation-api</artifactId>
                                                            <version>${javax.annotation-api.version}</version>
                                                        </dependency>
                                                        <dependency>
                                                            <groupId>org.junit.jupiter</groupId>
                                                            <artifactId>junit-jupiter-engine</artifactId>
                                                            <version>${junit-jupiter.version}</version>
                                                            <scope>test</scope>
                                                        </dependency>
                                                    </dependencies>
                                                        
                                                     <build/>
                                                        
                                                 </project>
                                                """
                                )
                        )
                )
        );
    }
}
