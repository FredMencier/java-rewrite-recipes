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

      SourceSpecs myObjects = java("""
            package org.refactor.eap6.dto;
            public class Animal {
                private String name;
                private String animalType;
            } public class Dog extends Animal {
                private Integer pattes;
            } public class Bird extends Animal {
                private Integer ailes;
            }
            """);

      @Override
    public void defaults(RecipeSpec spec) {
        spec.recipes(new EJBRemoteToRest(null, null)).typeValidationOptions(TypeValidation.none());
        spec.parser(JavaParser.fromJavaVersion().classpath("jboss-interceptors-api_1.1_spec", "javax.transaction-api"));
    }

    @Test
    public void shouldProduceAPIWithQueryParametersAndStringResponse() throws IOException {
        rewriteRun(myObjects,
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
    public void shouldProduceAPIWithQueryParametersAndListOfStringResponse() throws IOException {
        rewriteRun(myObjects,
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
    public void shouldProduceAPIWithRequestBodyAndListOfStringResponse() throws IOException {
        rewriteRun(myObjects,
                java("""
                        package org.refactor.eap6.svc.ejb;
                                                
                        import java.util.Date;
                        import javax.ejb.Remote;
                        import java.util.List;
                        import org.refactor.eap6.dto.*;
                                                        
                        @Remote
                        public interface IAnimalService {
                                                
                            List<String> getAnimals(Dog dog);
                        }
                        """, sourceSpecs -> sourceSpecs.path("target/IAnimalService.yaml"))
        );
        assertThat(FileUtils.readFileToString(new File("target/IAnimalService.yaml"))).isEqualTo("");
    }

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
