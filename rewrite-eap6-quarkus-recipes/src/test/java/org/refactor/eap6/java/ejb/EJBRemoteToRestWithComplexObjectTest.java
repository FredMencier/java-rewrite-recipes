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

class EJBRemoteToRestWithComplexObjectTest implements RewriteTest {


    SourceSpecs classCountry = java("""
            package org.refactor.eap6.java.dto;
            public class Country {
            }
              """);

    SourceSpecs classCat = java("""
            package org.refactor.eap6.java.dto;
            public class Cat {
            }
              """);

    SourceSpecs classAnimal = java("""
            package org.refactor.eap6.java.dto;
            public class Animal {
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
    public void shouldProduceAPIWithWrapperRequestBodyAndListOfStringResponse() throws IOException {
        rewriteRun(classCat, classCountry,
                java("""
                        package org.refactor.eap6.svc.ejb;

                        import java.util.Date;
                        import javax.ejb.Remote;
                        import java.util.List;
                        import org.refactor.eap6.java.dto.*;

                        @Remote
                        public interface IAnimalService {

                            List<String> getAnimals(Cat cat, Country country);
                        }
                        """, sourceSpecs -> sourceSpecs.path("target/IAnimalService.yaml"))
        );
        assertThat(FileUtils.readFileToString(new File("target/IAnimalService.yaml"))).isEqualTo(expectedContractWithWrapperRequestbodyAndListOfStringResponse);
    }

    @Test
    public void shouldProduceAPIWithWrapperRequestBodyAndWrapperResponse() throws IOException {
        rewriteRun(classCat, classCountry,
                java("""
                        package org.refactor.eap6.svc.ejb;

                        import java.util.Date;
                        import javax.ejb.Remote;
                        import java.util.List;
                        import org.refactor.eap6.java.dto.*;

                        @Remote
                        public interface IAnimalService {

                            Map<String, Cat> getAnimals(Cat cat, Country country);
                        }
                        """, sourceSpecs -> sourceSpecs.path("target/IAnimalService.yaml"))
        );
        assertThat(FileUtils.readFileToString(new File("target/IAnimalService.yaml"))).isEqualTo(expectedContractWithWrapperRequestBodyAndWrapperResponse);
    }

    private String expectedContractWithWrapperRequestBodyAndWrapperResponse = """
            """;

    private String expectedContractWithWrapperRequestbodyAndListOfStringResponse = """
            
            components:\s
              schemas:
                GetAnimalsRequest:\s
                  description: Wrapper for Country, Cat
                  properties:
                    country:\s
                      $ref: '#/components/schemas/Country'
                    cat:\s
                      $ref: '#/components/schemas/Cat'
                  type: object
                Cat:\s
                  properties:
                    pattes:\s
                      format: int32
                      type: integer
                    name:\s
                      type: string
                  type: object
                Country:\s
                  properties:
                    avarageTemperature:\s
                      format: int32
                      type: integer
                    name:\s
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

}
