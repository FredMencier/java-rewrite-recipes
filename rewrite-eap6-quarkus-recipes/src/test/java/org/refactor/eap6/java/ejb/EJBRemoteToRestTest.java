package org.refactor.eap6.java.ejb;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.SourceSpecs;
import org.openrewrite.test.TypeValidation;

import java.io.File;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.java.Assertions.java;

class EJBRemoteToRestTest implements RewriteTest {

    SourceSpecs classAnimal = java("""
            package org.refactor.eap6.java.dto;
            public class Animal {
            }
              """);

    SourceSpecs classDog = java("""
            package org.refactor.eap6.java.dto;
            public class Dog extends Animal {
            }
              """);

    SourceSpecs classBird = java("""
            package org.refactor.eap6.java.dto;
            public class Bird extends Animal {
            }
              """);

    SourceSpecs classCat = java("""
            package org.refactor.eap6.java.dto;
            public class Cat {
            }
              """);

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipes(new EJBRemoteToRest(null, null)).typeValidationOptions(TypeValidation.none());
        spec.parser(JavaParser
                .fromJavaVersion()
                .classpath("jboss-interceptors-api_1.1_spec", "javax.transaction-api"));
    }

    @Test
    public void shouldProduceAPIWithQueryParametersAndStringResponse() throws IOException {
        rewriteRun(
                java("""
                        package org.refactor.eap6.svc.ejb;

                        import java.util.Date;
                        import javax.ejb.Remote;

                        @Remote
                        public interface IAnimalService {

                            String getAnimals(String name, Date referenceDate);
                        }
                        """, sourceSpecs -> sourceSpecs.path("target/IAnimalService.yaml"))
        );
        assertThat(FileUtils.readFileToString(new File("target/IAnimalService.yaml"))).isEqualTo(expectedContractWithQueryParametersAndStringResponse);
    }

    @Test
    public void shouldProduceAPIWithQueryParametersGETAndStringResponse() throws IOException {
        rewriteRun(
                java("""
                        package org.refactor.eap6.svc.ejb;

                        import java.util.Date;
                        import javax.ejb.Remote;

                        @Remote
                        public interface IAnimalService {

                            @ToRest(path="/animals", action=ToRest.Action.GET, tag="animal", description="Animals API")
                            String getAnimals(String name, Date referenceDate);
                        }
                        """, sourceSpecs -> sourceSpecs.path("target/IAnimalService.yaml"))
        );
        assertThat(FileUtils.readFileToString(new File("target/IAnimalService.yaml"))).isEqualTo(expectedContractWithQueryParametersGETAndStringResponse);
    }

    @Test
    public void shouldProduceAPIWithQueryParametersAndListOfStringResponse() throws IOException {
        rewriteRun(
                java("""
                        package org.refactor.eap6.svc.ejb;

                        import java.util.Date;
                        import javax.ejb.Remote;
                        import java.util.List;

                        @Remote
                        public interface IAnimalService {

                            List<String> getAnimals(String name, Date referenceDate);
                        }
                        """, sourceSpecs -> sourceSpecs.path("target/IAnimalService.yaml"))
        );
        assertThat(FileUtils.readFileToString(new File("target/IAnimalService.yaml"))).isEqualTo(expectedContractWithQueryParametersAndListOfStringResponse);
    }

    @Test
    public void shouldProduceAPIWithQueryParametersAndSetOfStringResponse() throws IOException {
        rewriteRun(
                java("""
                        package org.refactor.eap6.svc.ejb;

                        import java.util.Date;
                        import javax.ejb.Remote;
                        import java.util.Set;

                        @Remote
                        public interface IAnimalService {

                            Set<String> getAnimals(String name, Date referenceDate);
                        }
                        """, sourceSpecs -> sourceSpecs.path("target/IAnimalService.yaml"))
        );
        assertThat(FileUtils.readFileToString(new File("target/IAnimalService.yaml"))).isEqualTo(expectedContractWithQueryParametersAndSetOfStringResponse);
    }

    @Test
    public void shouldProduceAPIWithRequestBodyAndListOfStringResponse() throws IOException {
        rewriteRun(classCat,
                java("""
                        package org.refactor.eap6.svc.ejb;

                        import java.util.Date;
                        import javax.ejb.Remote;
                        import java.util.List;
                        import org.refactor.eap6.java.dto.*;

                        @Remote
                        public interface IAnimalService {

                            List<String> getAnimals(Cat cat);
                        }
                        """, sourceSpecs -> sourceSpecs.path("target/IAnimalService.yaml"))
        );
        assertThat(FileUtils.readFileToString(new File("target/IAnimalService.yaml"))).isEqualTo(expectedContractWithRequestbodyAndListOfStringResponse);
    }

    private String expectedContractWithQueryParametersGETAndStringResponse = """

        components:\s
          schemas:
            GetAnimalsResponse:\s
              type: string
        info:\s
          description: IAnimalService OpenAPI definition
          title: IAnimalService
          version: 1.0.0
        openapi: 3.0.3
        paths:
          /animals:\s
            get:\s
              description: Animals API
              operationId: get-animals
              parameters:
              -\s
                in: query
                name: name
                schema:\s
                  type: string
              -\s
                in: query
                name: referenceDate
                schema:\s
                  format: date
                  type: string
              responses:
                '200':\s
                  content:
                    application/json:\s
                      schema:\s
                        $ref: '#/components/schemas/GetAnimalsResponse'
                  description: OK
              summary: get-animals
              tags:
              - animal
        tags:
        -\s
          name: animal
            """;

    private String expectedContractWithRequestbodyAndListOfStringResponse = """

            components:\s
              schemas:
                Cat:\s
                  description: org.refactor.eap6.java.dto.Cat
                  properties:
                    pattes:\s
                      format: int32
                      type: integer
                    name:\s
                      type: string
                  type: object
                GetAnimalsResponse:\s
                  items:\s
                    properties:
                      String:\s
                        type: string
                  type: array
            info:\s
              description: IAnimalService OpenAPI definition
              title: IAnimalService
              version: 1.0.0
            openapi: 3.0.3
            paths:
              /IAnimalService/getAnimals:\s
                post:\s
                  description: get-animals
                  operationId: get-animals
                  requestBody:\s
                    content:
                      application/json:\s
                        schema:\s
                          $ref: '#/components/schemas/Cat'
                    required: true
                  responses:
                    '200':\s
                      content:
                        application/json:\s
                          schema:\s
                            $ref: '#/components/schemas/GetAnimalsResponse'
                      description: OK
                  summary: get-animals
                  tags:
                  - IAnimalService
            tags:
            -\s
              name: IAnimalService
                """;

    private String expectedContractWithQueryParametersAndSetOfStringResponse = """

            components:\s
              schemas:
                GetAnimalsResponse:\s
                  items:\s
                    properties:
                      String:\s
                        type: string
                  type: array
                  uniqueItems: true
            info:\s
              description: IAnimalService OpenAPI definition
              title: IAnimalService
              version: 1.0.0
            openapi: 3.0.3
            paths:
              /IAnimalService/getAnimals:\s
                post:\s
                  description: get-animals
                  operationId: get-animals
                  parameters:
                  -\s
                    in: query
                    name: name
                    schema:\s
                      type: string
                  -\s
                    in: query
                    name: referenceDate
                    schema:\s
                      format: date
                      type: string
                  responses:
                    '200':\s
                      content:
                        application/json:\s
                          schema:\s
                            $ref: '#/components/schemas/GetAnimalsResponse'
                      description: OK
                  summary: get-animals
                  tags:
                  - IAnimalService
            tags:
            -\s
              name: IAnimalService
                """;

    private String expectedContractWithQueryParametersAndListOfStringResponse = """

            components:\s
              schemas:
                GetAnimalsResponse:\s
                  items:\s
                    properties:
                      String:\s
                        type: string
                  type: array
            info:\s
              description: IAnimalService OpenAPI definition
              title: IAnimalService
              version: 1.0.0
            openapi: 3.0.3
            paths:
              /IAnimalService/getAnimals:\s
                post:\s
                  description: get-animals
                  operationId: get-animals
                  parameters:
                  -\s
                    in: query
                    name: name
                    schema:\s
                      type: string
                  -\s
                    in: query
                    name: referenceDate
                    schema:\s
                      format: date
                      type: string
                  responses:
                    '200':\s
                      content:
                        application/json:\s
                          schema:\s
                            $ref: '#/components/schemas/GetAnimalsResponse'
                      description: OK
                  summary: get-animals
                  tags:
                  - IAnimalService
            tags:
            -\s
              name: IAnimalService
                """;

    private String expectedContractWithQueryParametersAndStringResponse = """
                        
            components:\s
              schemas:
                GetAnimalsResponse:\s
                  type: string
            info:\s
              description: IAnimalService OpenAPI definition
              title: IAnimalService
              version: 1.0.0
            openapi: 3.0.3
            paths:
              /IAnimalService/getAnimals:\s
                post:\s
                  description: get-animals
                  operationId: get-animals
                  parameters:
                  -\s
                    in: query
                    name: name
                    schema:\s
                      type: string
                  -\s
                    in: query
                    name: referenceDate
                    schema:\s
                      format: date
                      type: string
                  responses:
                    '200':\s
                      content:
                        application/json:\s
                          schema:\s
                            $ref: '#/components/schemas/GetAnimalsResponse'
                      description: OK
                  summary: get-animals
                  tags:
                  - IAnimalService
            tags:
            -\s
              name: IAnimalService
            """;
}
