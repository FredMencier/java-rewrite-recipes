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

class EJBRemoteToRestBasicMethodTest implements RewriteTest {

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

    SourceSpecs interfaceHuman = java("""
            package org.refactor.eap6.java.dto;
            public interface Human {
            }
            """);

    SourceSpecs classMan = java("""
            package org.refactor.eap6.java.dto;
            public class Man implements Human {
            }
            """);

    SourceSpecs classWoman = java("""
            package org.refactor.eap6.java.dto;
            public class Woman implements Human {
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
    public void shouldProduceAPIWithEmptyParametersAndStringResponse() throws IOException {
        rewriteRun(
                java("""
                        package org.refactor.eap6.svc.ejb;

                        import java.util.Date;
                        import javax.ejb.Remote;

                        @Remote
                        public interface IAnimalService {

                            String getAnimals();
                        }
                        """, sourceSpecs -> sourceSpecs.path("target/IAnimalService.yaml"))
        );
        assertThat(FileUtils.readFileToString(new File("target/IAnimalService.yaml"))).isEqualTo(expectedContractWithEmptyParametersAndStringResponse);
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
    public void shouldProduceAPIWithQueryParametersAndInheritedObjectResponse() throws IOException {
        rewriteRun(classAnimal, classDog, classBird,
                java("""
                        package org.refactor.eap6.svc.ejb;

                        import java.util.Date;
                        import javax.ejb.Remote;
                        import org.refactor.eap6.java.dto.*;

                        @Remote
                        public interface IAnimalService {

                            Animal getAnimals(String name, Date referenceDate);
                        }
                        """, sourceSpecs -> sourceSpecs.path("target/IAnimalService.yaml"))
        );
        assertThat(FileUtils.readFileToString(new File("target/IAnimalService.yaml"))).isEqualTo("");
    }

    @Test
    public void shouldProduceAPIWithQueryParametersAndImplementsObjectResponse() throws IOException {
        rewriteRun(interfaceHuman, classMan, classWoman,
                java("""
                        package org.refactor.eap6.svc.ejb;

                        import java.util.Date;
                        import javax.ejb.Remote;
                        import org.refactor.eap6.java.dto.*;

                        @Remote
                        public interface IAnimalService {

                            Human getAnimals(String name, Date referenceDate);
                        }
                        """, sourceSpecs -> sourceSpecs.path("target/IAnimalService.yaml"))
        );
        assertThat(FileUtils.readFileToString(new File("target/IAnimalService.yaml"))).isEqualTo(expectedContractWithQueryParametersAndImplementsObjectResponse);
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

    @Test
    public void shouldProduceAPIWithRequestBodyListAndListOfStringResponse() throws IOException {
        rewriteRun(classCat,
                java("""
                        package org.refactor.eap6.svc.ejb;

                        import java.util.Date;
                        import javax.ejb.Remote;
                        import java.util.List;
                        import org.refactor.eap6.java.dto.*;

                        @Remote
                        public interface IAnimalService {

                            List<String> getAnimals(List<Cat> cats);
                        }
                        """, sourceSpecs -> sourceSpecs.path("target/IAnimalService.yaml"))
        );
        assertThat(FileUtils.readFileToString(new File("target/IAnimalService.yaml"))).isEqualTo(expectedContractWithRequestbodyListAndListOfStringResponse);
    }

    private String expectedContractWithEmptyParametersAndStringResponse = """

            components:  {}
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
                  responses:
                    '200':\s
                      content:
                        application/json:\s
                          schema:\s
                            type: string
                      description: OK
                  summary: get-animals
                  tags:
                  - IAnimalService
            tags:
            -\s
              name: IAnimalService
            """;

    private String expectedContractWithQueryParametersAndImplementsObjectResponse = """

            components:\s
              schemas:
                Human:
                  oneOf:
                    - $ref: '#/components/schemas/Man'
                    - $ref: '#/components/schemas/Woman'
                Man:\s
                  description: org.refactor.eap6.java.dto.Man
                  properties:
                    size:\s
                      type: string
                    name:\s
                      type: string
                  type: object
                Woman:\s
                  description: org.refactor.eap6.java.dto.Woman
                  properties:
                    name:\s
                      type: string
                    hairColor:\s
                      type: string
                  type: object
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
                            $ref: '#/components/schemas/Human'
                      description: OK
                  summary: get-animals
                  tags:
                  - IAnimalService
            tags:
            -\s
              name: IAnimalService
            """;

    private String expectedContractWithRequestbodyListAndListOfStringResponse = """

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
                GetAnimalsRequest:\s
                  description: Wrapper for [List]
                  properties:
                    cats:\s
                      items:\s
                        $ref: '#/components/schemas/Cat'
                      type: array
                  type: object
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
                          $ref: '#/components/schemas/GetAnimalsRequest'
                    required: true
                  responses:
                    '200':\s
                      content:
                        application/json:\s
                          schema:\s
                            items:\s
                              type: string
                            type: array
                      description: OK
                  summary: get-animals
                  tags:
                  - IAnimalService
            tags:
            -\s
              name: IAnimalService
            """;

    private String expectedContractWithQueryParametersGETAndStringResponse = """

            components:  {}
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
                            type: string
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
                GetAnimalsRequest:\s
                  description: Wrapper for [Cat]
                  properties:
                    cat:\s
                      $ref: '#/components/schemas/Cat'
                  type: object
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
                          $ref: '#/components/schemas/GetAnimalsRequest'
                    required: true
                  responses:
                    '200':\s
                      content:
                        application/json:\s
                          schema:\s
                            items:\s
                              type: string
                            type: array
                      description: OK
                  summary: get-animals
                  tags:
                  - IAnimalService
            tags:
            -\s
              name: IAnimalService
                """;

    private String expectedContractWithQueryParametersAndSetOfStringResponse = """

            components:  {}
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
                            items:\s
                              type: string
                            type: array
                            uniqueItems: true
                      description: OK
                  summary: get-animals
                  tags:
                  - IAnimalService
            tags:
            -\s
              name: IAnimalService
                """;

    private String expectedContractWithQueryParametersAndListOfStringResponse = """

            components:  {}
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
                            items:\s
                              type: string
                            type: array
                      description: OK
                  summary: get-animals
                  tags:
                  - IAnimalService
            tags:
            -\s
              name: IAnimalService
                """;

    private String expectedContractWithQueryParametersAndStringResponse = """

            components:  {}
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
                            type: string
                      description: OK
                  summary: get-animals
                  tags:
                  - IAnimalService
            tags:
            -\s
              name: IAnimalService
            """;
}
