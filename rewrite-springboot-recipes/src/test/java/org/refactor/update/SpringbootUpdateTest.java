package org.refactor.update;

import org.refactor.java.maven.AddOrUpdateVersionArtifactId;
import org.refactor.java.xml.ChangePluginConfigurationValue;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.openrewrite.ExecutionContext;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.maven.*;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.SourceSpec;
import org.openrewrite.text.FindAndReplace;

import static org.assertj.core.api.AssertionsForClassTypes.fail;
import static org.openrewrite.java.Assertions.mavenProject;
import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.test.SourceSpecs.text;

class SpringbootUpdateTest implements RewriteTest {

    @Override
    public @NotNull ExecutionContext defaultExecutionContext(SourceSpec<?> @NotNull [] sourceSpecs) {
        ExecutionContext ctx = new InMemoryExecutionContext(t -> fail("Failed to parse sources or run recipe", t));
        MavenExecutionContextView.view(ctx).setMavenSettings(MavenSettings.readMavenSettingsFromDisk(ctx));
        return ctx;
    }


    @Test
    void shouldUpdatePomParentVersion() {
        rewriteRun(
                recipeSpec -> recipeSpec.recipe(new UpgradeParentVersion("org.springframework.boot", "spring-boot-starter-parent", "3.4.X", null, null)),
                mavenProject("springboot",
                        //language=xml
                        pomXml(
                                """
                                        <project>
                                         <modelVersion>4.0.0</modelVersion>
                                        	<parent>
                                        		<groupId>org.springframework.boot</groupId>
                                        		<artifactId>spring-boot-starter-parent</artifactId>
                                        		<version>3.4.0</version>
                                        	</parent>
                                        	<groupId>org.refactor.test</groupId>
                                         	<artifactId>TestProject</artifactId>
                                         	<version>1.0.0-SNAPSHOT</version>
                                         	<packaging>pom</packaging>
                                         	<name>FxProject</name>
                                            <properties>
                                                <java.version>17</java.version>
                                            </properties>
                                        </project>
                                        """,
                                """
                                        <project>
                                         <modelVersion>4.0.0</modelVersion>
                                        	<parent>
                                        		<groupId>org.springframework.boot</groupId>
                                        		<artifactId>spring-boot-starter-parent</artifactId>
                                        		<version>3.4.3</version>
                                        	</parent>
                                        	<groupId>org.refactor.test</groupId>
                                         	<artifactId>TestProject</artifactId>
                                         	<version>1.0.0-SNAPSHOT</version>
                                         	<packaging>pom</packaging>
                                         	<name>FxProject</name>
                                            <properties>
                                                <java.version>17</java.version>
                                            </properties>
                                        </project>
                                        """
                        )
                )
        );
    }

    @Test
    void shouldUpdatePomJavaVersion() {
        rewriteRun(
                recipeSpec -> recipeSpec.recipes(new ChangePropertyValue("java.version", "21", null, null),
                        new ChangePropertyValue("maven.compiler.source", "21", null, null),
                        new ChangePropertyValue("maven.compiler.target", "21", null, null),
                        new ChangePluginConfigurationValue("org.apache.maven.plugins", "maven-compiler-plugin", "source", "21"),
                        new ChangePluginConfigurationValue("org.apache.maven.plugins", "maven-compiler-plugin", "target", "21")),
                mavenProject("springboot",
                        //language=xml
                        pomXml(
                                """
                                        <project>
                                         <modelVersion>4.0.0</modelVersion>
                                            <parent>
                                        		  <groupId>org.springframework.boot</groupId>
                                        		  <artifactId>spring-boot-starter-parent</artifactId>
                                        		  <version>3.4.0</version>
                                            </parent>
                                            <groupId>org.refactor.test</groupId>
                                            <artifactId>TestProject</artifactId>
                                            <version>1.0.0-SNAPSHOT</version>
                                            <packaging>pom</packaging>
                                            <name>FxProject</name>
                                            <properties>
                                                <java.version>17</java.version>
                                                <maven.compiler.source>17</maven.compiler.source>
                                                <maven.compiler.target>17</maven.compiler.target>
                                            </properties>
                                            <build>
                                                <plugins>
                                                  <plugin>
                                                    <groupId>org.apache.maven.plugins</groupId>
                                                    <artifactId>maven-compiler-plugin</artifactId>
                                                    <version>3.13.0</version>
                                                    <configuration>
                                                      <source>17</source>
                                                      <target>17</target>
                                                    </configuration>
                                                  </plugin>
                                                </plugins>
                                            </build>
                                        </project>
                                        """,
                                """
                                        <project>
                                         <modelVersion>4.0.0</modelVersion>
                                            <parent>
                                        		  <groupId>org.springframework.boot</groupId>
                                        		  <artifactId>spring-boot-starter-parent</artifactId>
                                        		  <version>3.4.0</version>
                                            </parent>
                                            <groupId>org.refactor.test</groupId>
                                            <artifactId>TestProject</artifactId>
                                            <version>1.0.0-SNAPSHOT</version>
                                            <packaging>pom</packaging>
                                            <name>FxProject</name>
                                            <properties>
                                                <java.version>21</java.version>
                                                <maven.compiler.source>21</maven.compiler.source>
                                                <maven.compiler.target>21</maven.compiler.target>
                                            </properties>
                                            <build>
                                                <plugins>
                                                  <plugin>
                                                    <groupId>org.apache.maven.plugins</groupId>
                                                    <artifactId>maven-compiler-plugin</artifactId>
                                                    <version>3.13.0</version>
                                            <configuration>
                                             <source>21</source>
                                             <target>21</target>
                                            </configuration>
                                                  </plugin>
                                                </plugins>
                                            </build>
                                        </project>
                                        """
                        )
                )
        );
    }

    @Test
    void shouldUpdateOrAddDepndencyJavaVersion() {
        rewriteRun(
                recipeSpec -> recipeSpec.recipes(
                        new AddOrUpdateVersionArtifactId("com.squareup.okhttp3", "mockwebserver", "4.12.0")),
                mavenProject("springboot",
                        //language=xml
                        pomXml(
                                """
                                        <project>
                                         <modelVersion>4.0.0</modelVersion>
                                            <parent>
                                                <groupId>org.springframework.boot</groupId>
                                                <artifactId>spring-boot-starter-parent</artifactId>
                                                <version>3.4.0</version>
                                            </parent>
                                            <groupId>org.refactor.test</groupId>
                                            <artifactId>TestProject</artifactId>
                                            <version>1.0.0-SNAPSHOT</version>
                                            <packaging>pom</packaging>
                                            <name>FxProject</name>

                                            <dependencies>
                                                <dependency>
                                                     <groupId>com.squareup.okhttp3</groupId>
                                                     <artifactId>mockwebserver</artifactId>
                                                     <scope>test</scope>
                                                 </dependency>
                                            </dependencies>
                                        </project>
                                        """,
                                """
                                        <project>
                                         <modelVersion>4.0.0</modelVersion>
                                            <parent>
                                                <groupId>org.springframework.boot</groupId>
                                                <artifactId>spring-boot-starter-parent</artifactId>
                                                <version>3.4.0</version>
                                            </parent>
                                            <groupId>org.refactor.test</groupId>
                                            <artifactId>TestProject</artifactId>
                                            <version>1.0.0-SNAPSHOT</version>
                                            <packaging>pom</packaging>
                                            <name>FxProject</name>

                                            <dependencies>
                                                <dependency>
                                                     <groupId>com.squareup.okhttp3</groupId>
                                                     <artifactId>mockwebserver</artifactId>
                                                     <scope>test</scope>
                                                     <version>4.12.0</version>
                                                 </dependency>
                                            </dependencies>
                                        </project>
                                        """
                        )
                )
        );
    }

    @Test
    void shouldUpdateJenkinsFileJavaVersion() {
        rewriteRun(
                recipeSpec -> recipeSpec.recipes(new FindAndReplace("jdkVersion = '[0-9]*'", "jdkVersion = '21'", true, null, null, null, null, null)),
                mavenProject("springboot",
                        //language=groovy
                        text(
                                """
                                        #!groovy

                                        dockerSpringbootBuild {
                                            jdkVersion = '11'
                                            emails = 'myName@gmail.com'
                                            skipCheckmarx = false
                                            skipBlackDuck = false
                                            blackDuckProjectTier = 1
                                        }
                                        """,
                                """
                                        #!groovy
                                                
                                        dockerSpringbootBuild {
                                            jdkVersion = '21'
                                            emails = 'myName@gmail.com'
                                            skipCheckmarx = false
                                            skipBlackDuck = false
                                            blackDuckProjectTier = 1
                                        }
                                        """
                        )
                )
        );
    }

    @Test
    void shouldUpdateDockerFileJavaVersion() {
        rewriteRun(
                recipeSpec -> recipeSpec.recipes(new FindAndReplace("[0-9]*-latest", "21-latest", true, null, null, null, null, null)),
                mavenProject("springboot",
                        //language=dockerfile
                        text(
                                """
                                        FROM myimage/my-jre-base:11-latest
                                        """,
                                """
                                        FROM myimage/my-jre-base:21-latest
                                        """
                        )
                )
        );
    }

    @Test
    void shouldUpdateDockerFileJavaVersionParam() {
        rewriteRun(
                recipeSpec -> recipeSpec.recipes(new FindAndReplace("[0-9]*-latest", "21-latest", true, null, null, null, null, null)),
                mavenProject("springboot",
                        //language=dockerfile
                        text(
                                """
                                        ARG BASE_IMAGE_VERSION=17-latest
                                        FROM myimage/my-jre-base:${BASE_IMAGE_VERSION}
                                        """,
                                """
                                        ARG BASE_IMAGE_VERSION=21-latest
                                        FROM myimage/my-jre-base:${BASE_IMAGE_VERSION}
                                        """
                        )
                )
        );
    }
}
