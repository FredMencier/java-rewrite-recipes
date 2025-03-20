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
    public void shouldProduceAPIWithWrapperRequestBodyAndMapResponse() throws IOException {
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
        assertThat(FileUtils.readFileToString(new File("target/IAnimalService.yaml"))).isEqualTo(expectedContractWithWrapperRequestBodyAndMapResponse);
    }

    @Test
    public void shouldProduceAPIWithWrapperRequestBodyAndMapStringResponse() throws IOException {
        rewriteRun(classCat, classCountry,
                java("""
                        package org.refactor.eap6.svc.ejb;

                        import java.util.Date;
                        import javax.ejb.Remote;
                        import java.util.List;
                        import org.refactor.eap6.java.dto.*;

                        @Remote
                        public interface IAnimalService {

                            Map<String, String> getAnimals(Cat cat, Country country);
                        }
                        """, sourceSpecs -> sourceSpecs.path("target/IAnimalService.yaml"))
        );
        assertThat(FileUtils.readFileToString(new File("target/IAnimalService.yaml"))).isEqualTo(expectedContractWithWrapperRequestBodyAndMapStringResponse);
    }

    @Test
    public void shouldProduceAPIWithMultiListResponse() throws IOException {
        rewriteRun(classCat, classCountry,
                java("""
                        package org.refactor.eap6.svc.ejb;

                        import java.util.Date;
                        import javax.ejb.Remote;
                        import java.util.List;
                        import org.refactor.eap6.java.dto.*;

                        @Remote
                        public interface IAnimalService {

                            List<List<Cat>> getAnimals(String cat, String country);
                        }
                        """, sourceSpecs -> sourceSpecs.path("target/IAnimalService.yaml"))
        );
        assertThat(FileUtils.readFileToString(new File("target/IAnimalService.yaml"))).isEqualTo(expectedContractWithMultiListResponse);
    }

    @Test
    public void shouldProduceAPIWithMultiListAndMapResponse() throws IOException {
        rewriteRun(classCat, classCountry,
                java("""
                        package org.refactor.eap6.svc.ejb;

                        import java.util.Date;
                        import javax.ejb.Remote;
                        import java.util.List;
                        import org.refactor.eap6.java.dto.*;

                        @Remote
                        public interface IAnimalService {

                            List<List<Map<String, List<Cat>>>> getAnimals(String cat, String country);
                        }
                        """, sourceSpecs -> sourceSpecs.path("target/IAnimalService.yaml"))
        );
        assertThat(FileUtils.readFileToString(new File("target/IAnimalService.yaml"))).isEqualTo(expectedContractWithMultiListAndMapResponse);
    }

    private String expectedContractWithWrapperRequestBodyAndMapStringResponse = """
    
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
                  description: org.refactor.eap6.java.dto.Cat
                  properties:
                    pattes:\s
                      format: int32
                      type: integer
                    name:\s
                      type: string
                  type: object
                Country:\s
                  description: org.refactor.eap6.java.dto.Country
                  properties:
                    avarageTemperature:\s
                      format: int32
                      type: integer
                    name:\s
                      type: string
                  type: object
                GetAnimalsResponse:\s
                  additionalProperties:\s
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
                            $ref: '#/components/schemas/GetAnimalsResponse'
                      description: OK
                  summary: get-animals
                  tags:
                  - IAnimalService
            tags:
            -\s
              name: IAnimalService
            """;

    private String expectedContractWithMultiListAndMapResponse = """

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
            items:\s
              additionalProperties:\s
                items:\s
                  $ref: '#/components/schemas/Cat'
                type: array
              type: object
            type: array
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
            name: country
            schema:\s
              type: string
          -\s
            in: query
            name: cat
            schema:\s
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


    private String expectedContractWithMultiListResponse = """

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
                items:\s
                  $ref: '#/components/schemas/Cat'
                type: array
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
                name: country
                schema:\s
                  type: string
              -\s
                in: query
                name: cat
                schema:\s
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

    private String expectedContractWithWrapperRequestBodyAndMapResponse = """

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
                  description: org.refactor.eap6.java.dto.Cat
                  properties:
                    pattes:\s
                      format: int32
                      type: integer
                    name:\s
                      type: string
                  type: object
                Country:\s
                  description: org.refactor.eap6.java.dto.Country
                  properties:
                    avarageTemperature:\s
                      format: int32
                      type: integer
                    name:\s
                      type: string
                  type: object
                GetAnimalsResponse:\s
                  additionalProperties:\s
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
                            $ref: '#/components/schemas/GetAnimalsResponse'
                      description: OK
                  summary: get-animals
                  tags:
                  - IAnimalService
            tags:
            -\s
              name: IAnimalService
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
                  description: org.refactor.eap6.java.dto.Cat
                  properties:
                    pattes:\s
                      format: int32
                      type: integer
                    name:\s
                      type: string
                  type: object
                Country:\s
                  description: org.refactor.eap6.java.dto.Country
                  properties:
                    avarageTemperature:\s
                      format: int32
                      type: integer
                    name:\s
                      type: string
                  type: object
                GetAnimalsResponse:\s
                  items:\s
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
                          $ref: '#/components/schemas/GetAnimalsRequest'
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

}
