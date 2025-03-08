
package org.refactor.eap6.java.ejb;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.smallrye.openapi.api.models.*;
import io.smallrye.openapi.api.models.info.InfoImpl;
import io.smallrye.openapi.api.models.media.ContentImpl;
import io.smallrye.openapi.api.models.media.MediaTypeImpl;
import io.smallrye.openapi.api.models.media.SchemaImpl;
import io.smallrye.openapi.api.models.parameters.ParameterImpl;
import io.smallrye.openapi.api.models.parameters.RequestBodyImpl;
import io.smallrye.openapi.api.models.responses.APIResponseImpl;
import io.smallrye.openapi.api.models.responses.APIResponsesImpl;
import io.smallrye.openapi.api.models.tags.TagImpl;
import io.swagger.v3.core.converter.ModelConverters;
import lombok.EqualsAndHashCode;
import lombok.Value;
import org.apache.http.HttpStatus;
import org.eclipse.microprofile.openapi.models.*;
import org.eclipse.microprofile.openapi.models.media.Content;
import org.eclipse.microprofile.openapi.models.media.MediaType;
import org.eclipse.microprofile.openapi.models.media.Schema;
import org.eclipse.microprofile.openapi.models.parameters.Parameter;
import org.eclipse.microprofile.openapi.models.parameters.RequestBody;
import org.eclipse.microprofile.openapi.models.responses.APIResponse;
import org.eclipse.microprofile.openapi.models.responses.APIResponses;
import org.eclipse.microprofile.openapi.models.tags.Tag;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.*;
import org.refactor.eap6.java.annotation.ToRest;
import org.refactor.eap6.util.RewriteUtils;
import org.refactor.eap6.yaml.util.SchemaConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.HttpMethod;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * https://github.com/tjquinno/openapi-snakeyaml/blob/master/src/main/java/io/helidon/examples/openapisnakeyaml/Parser.java
 */
@Value
@EqualsAndHashCode(callSuper = false)
public class EJBRemoteToRest extends ScanningRecipe<String> {

    private static final Logger LOG = LoggerFactory.getLogger(EJBRemoteToRest.class);

    private static final String ROOT_PATH_COMPONENTS_SCHEMAS = "#/components/schemas/";

    @Option(displayName = "Target directory",
            description = "The name of the directory where the OpenAPI yaml file will be generated.",
            example = "./target/openapi")
    @Nullable
    String targetDirectory;

    @Option(displayName = "Fully qualified class to process",
            description = "The name of the class to create yaml openApi contract.",
            example = "org.svc.ejb.IMyEjbRemoteInterface")
    @Nullable
    String fullyQualifiedClassToProcess;

    @Override
    public String getDisplayName() {
        return "Transform EJB Remote to REST Endpoint";
    }

    @Override
    public String getDescription() {
        return "Transform EJB Remote to REST Endpoint.";
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(10);
    }

    public EJBRemoteToRest(@JsonProperty("targetDirectory") String targetDirectory, @JsonProperty("fullyQualifiedClassToProcess") String fullyQualifiedClassToProcess) {
        this.targetDirectory = targetDirectory;
        this.fullyQualifiedClassToProcess = fullyQualifiedClassToProcess;
    }

    @Override
    public String getInitialValue(ExecutionContext ctx) {
        return targetDirectory != null ? targetDirectory : "./target";
    }

    public TreeVisitor<?, ExecutionContext> getScanner(String tDirectory) {

        String classToProcess = fullyQualifiedClassToProcess;

        return new JavaIsoVisitor<ExecutionContext>() {

            private final List<EndpointInfo> endpointInfos = new ArrayList<>();

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext executionContext) {
                J.ClassDeclaration classDeclaration = super.visitClassDeclaration(classDecl, executionContext);

                AppInfo appInfo = new AppInfo(classDecl.getSimpleName(), classDecl.getSimpleName() + " OpenAPI definition", "1.0.0");
                if (FindRemoteInterfaceVisitor.find(getCursor().getParentTreeCursor().getValue(), classDecl).get()) {

                    if (classToProcess != null) {
                        String classQualified = TypeUtils.asFullyQualified(classDecl.getType()).getFullyQualifiedName();
                        if (!classToProcess.equals(classQualified)) {
                            return classDeclaration;
                        }
                    }

                    List<Statement> statements = classDecl.getBody().getStatements();
                    for (Statement statement : statements) {
                        if (statement instanceof J.MethodDeclaration) {
                            //On build les endpoints pour toutes les méthodes remotes
                            buildEndpoint((J.MethodDeclaration) statement, classDeclaration.getSimpleName());
                        }
                    }
                    if (!endpointInfos.isEmpty()) {
                        OpenAPI openAPI = buildYamlContract(appInfo);
                        executionContext.putMessage(classDeclaration.getSimpleName(), openAPI);
                    }
                }
                return classDeclaration;
            }

            /**
             * On construit tous les Endpoints
             * @param methodDeclaration
             * @param classname
             */
            private void buildEndpoint(J.MethodDeclaration methodDeclaration, String classname) {
                EndpointInfo endpointInfo = new EndpointInfo();
                endpointInfo.methodName = methodDeclaration.getSimpleName();
                endpointInfo.operationId = toDashCase(methodDeclaration.getSimpleName());
                TypeTree returnTypeExpression = methodDeclaration.getReturnTypeExpression();
                if (returnTypeExpression instanceof J.Identifier) {
                    JavaType.FullyQualified varType = TypeUtils.asFullyQualified(returnTypeExpression.getType());
                    ResponseItemComponent responseItemComponent = new ResponseItemComponent();
                    if (varType != null) {
                        SchemaFormat schemaFormat = getSchemaFormat(varType.getFullyQualifiedName());
                        if (schemaFormat.schemaType.equals(Schema.SchemaType.OBJECT)) {
                            responseItemComponent.componentType = schemaFormat.schemaType;
                        }
                        responseItemComponent.fullyQualified = varType.getFullyQualifiedName();
                        responseItemComponent.componentName = varType.getClassName();
                        endpointInfo.responseItemComponent = responseItemComponent;
                    } else {
                        LOG.warn("varType null dans returnType");
                    }
                } else if (returnTypeExpression instanceof J.ParameterizedType) {
                    //Cas d'un wrapper (List....)
                    List<Expression> typeParameters = ((J.ParameterizedType) returnTypeExpression).getTypeParameters();
                    if (typeParameters != null && !typeParameters.isEmpty()) {
                        //List<>
                        if (typeParameters.size() == 1) {
                            JavaType.FullyQualified varType = TypeUtils.asFullyQualified((typeParameters.get(0)).getType());
                            ResponseItemComponent responseItemComponent = new ResponseItemComponent();
                            if (varType != null) {
                                SchemaFormat schemaFormat = getSchemaFormat(varType.getFullyQualifiedName());
                                if (schemaFormat.schemaType.equals(Schema.SchemaType.OBJECT)) {
                                    responseItemComponent.componentType = schemaFormat.schemaType;
                                }
                                responseItemComponent.fullyQualified = varType.getFullyQualifiedName();
                                responseItemComponent.componentName = varType.getClassName();
                                endpointInfo.responseItemComponent = responseItemComponent;
                                endpointInfo.responseItemComponent.responseWrapper = ((J.ParameterizedType) returnTypeExpression).getClazz().toString();
                            }
                        } else {
                            //Map<> ouautre define a list of object wrapper Map<Obj1, Obj2> -> List<ObjWrapper> avec ObjWrapper(Obj1, Obj2)
                            LOG.warn("return type Map not supported for method {}", methodDeclaration.getSimpleName());
                            ResponseItemComponent responseItemComponent = new ResponseItemComponent();
                            typeParameters.forEach(expression -> {
                                JavaType.FullyQualified varType = TypeUtils.asFullyQualified(expression.getType());
                                if (varType != null) {
                                    SchemaFormat schemaFormat = getSchemaFormat(varType.getFullyQualifiedName());
                                    responseItemComponent.componentTypeList.add(schemaFormat.schemaType);
                                }
                            });
                            responseItemComponent.componentName = getComponentName(endpointInfo.methodName, "Response");
                            endpointInfo.responseItemComponent = responseItemComponent;
                            Optional<String> containerTypeOpt = getContainerType(((J.ParameterizedType) methodDeclaration.getReturnTypeExpression()).getClazz().toString());
                            containerTypeOpt.ifPresent(containerType -> endpointInfo.responseItemComponent.responseWrapper = containerType);
                        }
                    }
                }

                //On recherche l'annotation @ToRest pour orienter la construction du REST point
                Optional<J.Annotation> toRest = methodDeclaration.getLeadingAnnotations().stream().filter(annotation -> annotation.getSimpleName().equals("ToRest")).findFirst();
                if (toRest.isPresent()) {
                    J.Annotation annotation = toRest.get();
                    List<Expression> arguments = annotation.getArguments();
                    buildEndpointInfo(arguments, endpointInfo);
                } else {
                    LOG.warn("annotation @ToRest not found for method {}", methodDeclaration.getSimpleName());
                    endpointInfo.path = "/" + classname + "/" + methodDeclaration.getSimpleName();
                    endpointInfo.action = ToRest.ActionType.POST.name();
                    endpointInfo.tag = classname;
                    endpointInfo.description = endpointInfo.operationId;
                }

                List<Statement> parameters = methodDeclaration.getParameters();
                parameters.forEach(statement -> {
                    if (statement instanceof J.VariableDeclarations) {
                        J.VariableDeclarations variableDeclarations = (J.VariableDeclarations) statement;
                        TypeTree typeExpression = variableDeclarations.getTypeExpression();
                        String name = variableDeclarations.getVariables().get(0).getVariableType().getName();
                        RequestParam requestParam = new RequestParam();
                        requestParam.name = name;
                        if (typeExpression instanceof J.Identifier || typeExpression instanceof J.Primitive) {
                            String type = null;
                            if (typeExpression instanceof J.Primitive) {
                                type = typeExpression.getType().toString();
                            } else {
                                JavaType.FullyQualified fullyQualified = TypeUtils.asFullyQualified(typeExpression.getType());
                                if (fullyQualified != null) {
                                    type = fullyQualified.getFullyQualifiedName();
                                }
                            }
                            if (type != null) {
                                SchemaFormat schemaFormat = getSchemaFormat(type);
                                requestParam.fullyQualified = type;
                                if (schemaFormat.schemaType.equals(Schema.SchemaType.OBJECT)) {
                                    endpointInfo.requestBodyParametersMap.put(name, requestParam);
                                } else {
                                    endpointInfo.requestParametersMap.put(name, requestParam);
                                }
                            } else {
                                requestParam.fullyQualified = TypeUtils.asFullyQualified(JavaType.buildType("java.lang.Object")).getFullyQualifiedName();
                                endpointInfo.requestParametersMap.put(name, requestParam);
                            }
                        } else if (typeExpression instanceof J.ParameterizedType) {
                            List<Expression> typeParameters = ((J.ParameterizedType) typeExpression).getTypeParameters();
                            if (typeParameters != null && !typeParameters.isEmpty()) {
                                //List<>
                                JavaType.FullyQualified fullyQualified = TypeUtils.asFullyQualified((typeParameters.get(0)).getType());
                                if (fullyQualified != null) {
                                    String type = fullyQualified.getFullyQualifiedName();
                                    SchemaFormat schemaFormat = getSchemaFormat(type);
                                    requestParam.fullyQualified = fullyQualified.getFullyQualifiedName();
                                    requestParam.requestWrapper = ((J.ParameterizedType) typeExpression).getClazz().toString();
                                    if (schemaFormat.schemaType.equals(Schema.SchemaType.OBJECT)) {
                                        endpointInfo.requestBodyParametersMap.put(name, requestParam);
                                    } else {
                                        endpointInfo.requestParametersMap.put(name, requestParam);
                                    }
                                } else {
                                    requestParam.fullyQualified = TypeUtils.asFullyQualified(JavaType.buildType("java.lang.Object")).getFullyQualifiedName();
                                    endpointInfo.requestParametersMap.put(name, requestParam);
                                }
                            }
                        }
                    } else if (statement instanceof J.Empty) {
                    }
                });
                endpointInfos.add(endpointInfo);
            }

            /**
             * On construit le contrat OpenAPI
             * @param appInfo
             * @return
             */
            private OpenAPI buildYamlContract(AppInfo appInfo) {
                OpenAPI openAPI = new OpenAPIImpl();
                InfoImpl info = new InfoImpl();
                info.setTitle(appInfo.appName);
                info.setVersion(appInfo.appVersion);
                info.setDescription(appInfo.appDescription);

                Set<Tag> tags = new HashSet<>();
                Paths paths = new PathsImpl();
                Components components = new ComponentsImpl();
                endpointInfos.forEach(endpointInfo -> {
                    buildPathItem(endpointInfo);
                    paths.addPathItem(endpointInfo.path, endpointInfo.pathItemComponent.pathItem);
                    if (endpointInfo.pathItemComponent.schemaComponent != null && !endpointInfo.pathItemComponent.schemaComponent.isEmpty()) {
                        endpointInfo.pathItemComponent.schemaComponent.forEach(schema -> components.addSchema(endpointInfo.pathItemComponent.componentName, schema));
                    }
                    if (!endpointInfo.additionalSchemaComponent.isEmpty()) {
                        endpointInfo.additionalSchemaComponent.entrySet().forEach(entry -> components.addSchema(entry.getKey(), entry.getValue()));
                    }
                    if (endpointInfo.responseItemComponent != null) {
                        if (endpointInfo.responseItemComponent.componentType != null && endpointInfo.responseItemComponent.componentType.equals(Schema.SchemaType.OBJECT)) {
                            RequestParam requestParam = new RequestParam();//TODO voir si vraiment besoin du responseItemComponent - renommer RequestParam en ComponentParam
                            requestParam.fullyQualified = endpointInfo.responseItemComponent.fullyQualified;
                            requestParam.name = endpointInfo.responseItemComponent.componentName;
                            Map<String, Schema> schemas = buildSchemaForObject(requestParam);
                            schemas.entrySet().forEach(entry -> {
                                endpointInfo.additionalSchemaComponent.put(entry.getKey(), entry.getValue());
                                components.addSchema(entry.getKey(), entry.getValue());
                            });
                        } else if (endpointInfo.responseItemComponent.componentTypeList.size() > 1) {
                            Schema schema = new SchemaImpl();
                            schema.setType(Schema.SchemaType.OBJECT);
                            endpointInfo.responseItemComponent.componentTypeList.forEach(schemaType -> {
                                Schema schemaItem = new SchemaImpl();
                                schemaItem.setType(schemaType);
                                String propName = "attr" + schemaType.name();
                                if (schemaType.name().length() > 3) {
                                    propName = "attr" + RewriteUtils.firstUpperCase(schemaType.name().toLowerCase().substring(0, 3));
                                }
                                schema.addProperty(propName, schemaItem);
                            });
                            components.addSchema(endpointInfo.responseItemComponent.componentName, schema);
                        }
                    }

                    Tag tag = new TagImpl();
                    tag.setName(endpointInfo.tag);
                    tags.add(tag);
                });
                openAPI.setInfo(info);
                openAPI.setPaths(paths);
                openAPI.setTags(new ArrayList<>(tags));
                openAPI.setComponents(components);

                return openAPI;
            }

            /**
             * Construction du pathItem
             * @param endpointInfo
             */
            private void buildPathItem(EndpointInfo endpointInfo) {
                PathItem pathItem = new PathItemImpl();
                Operation operation = new OperationImpl();
                operation.setOperationId(endpointInfo.operationId);
                operation.setSummary(endpointInfo.operationId);
                operation.setDescription(endpointInfo.description);
                operation.addTag(endpointInfo.tag);

                //On ajoute tous les params dans le requestBody
                if (!endpointInfo.requestBodyParametersMap.isEmpty() && !endpointInfo.requestParametersMap.isEmpty()) {
                    endpointInfo.requestBodyParametersMap.putAll(endpointInfo.requestParametersMap);
                    endpointInfo.requestParametersMap.clear();
                }

                if (!endpointInfo.requestBodyParametersMap.isEmpty()) {
                    RequestBody requestBody = new RequestBodyImpl();
                    requestBody.setRequired(true);
                    Content content = new ContentImpl();
                    MediaType mediaType = new MediaTypeImpl();
                    Schema schema = new SchemaImpl();
                    if (endpointInfo.requestBodyParametersMap.size() > 1) {
                        schema.setRef(ROOT_PATH_COMPONENTS_SCHEMAS + getComponentName(endpointInfo.methodName, "Request"));
                    } else {
                        Set<String> params = endpointInfo.requestBodyParametersMap.keySet();
                        String paramName = params.iterator().next();
                        RequestParam requestParam = endpointInfo.requestBodyParametersMap.get(paramName);
                        if (requestParam.requestWrapper != null && isCollection(requestParam.requestWrapper)) {
                            schema.setType(Schema.SchemaType.ARRAY);
                            Schema schemaItem = new SchemaImpl();
                            schemaItem.setRef(ROOT_PATH_COMPONENTS_SCHEMAS + getClassName(requestParam.fullyQualified));
                            schema.setItems(schemaItem);
                        } else {
                            schema.setRef(ROOT_PATH_COMPONENTS_SCHEMAS + getClassName(requestParam.fullyQualified));
                        }
                    }
                    mediaType.setSchema(schema);
                    content.setMediaTypes(Collections.singletonMap(javax.ws.rs.core.MediaType.APPLICATION_JSON, mediaType));
                    requestBody.setContent(content);
                    operation.setRequestBody(requestBody);
                } else if (!endpointInfo.requestParametersMap.isEmpty()) {
                    endpointInfo.requestParametersMap.forEach((name, requestParam) -> {
                        Parameter parameter = new ParameterImpl();
                        parameter.setName(name);
                        parameter.setIn(Parameter.In.QUERY);
                        LOG.info("parameters {} with type {}", name, requestParam);
                        Map<String, Schema> singleParameterSchema = buildSchemaForObject(requestParam);
                        if (requestParam.requestWrapper != null && isCollection(requestParam.requestWrapper)) {
                            Schema schema = new SchemaImpl();
                            schema.setType(Schema.SchemaType.ARRAY);
                            schema.setItems(singleParameterSchema.values().iterator().next());
                            parameter.setSchema(schema);
                        } else {
                            parameter.setSchema(singleParameterSchema.values().iterator().next());
                        }
                        operation.addParameter(parameter);
                    });
                }

                APIResponses apiResponses = new APIResponsesImpl();
                APIResponse apiResponse = new APIResponseImpl();
                if (endpointInfo.responseItemComponent == null) {
                    apiResponse.setDescription("no content");
                    apiResponses.addAPIResponse("default", apiResponse);
                    operation.setResponses(apiResponses);
                } else {
                    SchemaFormat schemaFormat = getSchemaFormat(endpointInfo.responseItemComponent.componentName);
                    apiResponse.setDescription("OK");
                    Content content = new ContentImpl();
                    MediaType mediaType = new MediaTypeImpl();
                    Schema schema = new SchemaImpl();
                    if (!schemaFormat.schemaType.equals(Schema.SchemaType.OBJECT)) {
                        schema.setType(Schema.SchemaType.STRING);
                    } else {
                        if (endpointInfo.responseItemComponent.responseWrapper != null && isCollection(endpointInfo.responseItemComponent.responseWrapper)) {
                            schema.setType(Schema.SchemaType.ARRAY);
                            Schema schemaItem = new SchemaImpl();
                            schemaItem.setRef(ROOT_PATH_COMPONENTS_SCHEMAS + endpointInfo.responseItemComponent.componentName);
                            schema.setItems(schemaItem);
                        } else {
                            schema.setRef(ROOT_PATH_COMPONENTS_SCHEMAS + endpointInfo.responseItemComponent.componentName);
                        }
                    }
                    mediaType.setSchema(schema);
                    content.setMediaTypes(Collections.singletonMap(javax.ws.rs.core.MediaType.APPLICATION_JSON, mediaType));
                    apiResponse.setContent(content);
                    apiResponses.addAPIResponse(String.valueOf(HttpStatus.SC_OK), apiResponse);
                    operation.setResponses(apiResponses);
                }

                if (endpointInfo.action.equalsIgnoreCase(HttpMethod.GET)) {
                    pathItem.GET(operation);
                } else if (endpointInfo.action.equalsIgnoreCase(HttpMethod.POST)) {
                    pathItem.POST(operation);
                } else if (endpointInfo.action.equalsIgnoreCase(HttpMethod.PUT)) {
                    pathItem.PUT(operation);
                } else if (endpointInfo.action.equalsIgnoreCase(HttpMethod.DELETE)) {
                    pathItem.DELETE(operation);
                }

                PathItemComponent pathItemComponent = new PathItemComponent();
                if (endpointInfo.requestBodyParametersMap.size() > 1) {
                    Schema schemaWrapper = new SchemaImpl();
                    schemaWrapper.setType(Schema.SchemaType.OBJECT);
                    String collect = endpointInfo.requestBodyParametersMap.values().stream().map(requestParam -> getClassName(requestParam.fullyQualified)).collect(Collectors.joining(", "));
                    schemaWrapper.setDescription("Wrapper for " + collect);
                    List<Schema> additionnalPathComponents = new ArrayList<>();
                    endpointInfo.requestBodyParametersMap.values().forEach(requestParam -> {
                        Map<String, Schema> schemas = buildSchemaForObject(requestParam);
                        if (requestParam.requestWrapper != null && isCollection(requestParam.requestWrapper)) {
                            Schema schema = new SchemaImpl();
                            schema.setType(Schema.SchemaType.ARRAY);
                            //car si on est dans le cas d'un objet avec des subtypes alors la clé de la map n'est pas le requestParam.name
                            Schema schemaRequestParam = schemas.get(requestParam.name);
                            if (schemaRequestParam == null) {
                                schemaRequestParam = schemas.get(getClassName(requestParam.fullyQualified));
                            }
                            if (schemaRequestParam != null && (schemaRequestParam.getType().equals(Schema.SchemaType.OBJECT) || isCollection(requestParam.requestWrapper))) {
                                Schema schemaRef = new SchemaImpl();
                                schemaRef.setRef(ROOT_PATH_COMPONENTS_SCHEMAS + getClassName(requestParam.fullyQualified));
                                schema.setItems(schemaRef);
                                schemaWrapper.addProperty(requestParam.name, schema);
                                endpointInfo.additionalSchemaComponent.put(getClassName(requestParam.fullyQualified), schemaRequestParam);
                            } else {
                                schemaWrapper.addProperty(requestParam.name, schema);
                            }
                        } else {
                            schemas.entrySet().forEach(entry -> {
                                if (entry.getKey().equalsIgnoreCase(requestParam.name)) {
                                    if (entry.getValue().getType().equals(Schema.SchemaType.OBJECT)) {
                                        Schema schemaRef = new SchemaImpl();
                                        schemaRef.setRef(ROOT_PATH_COMPONENTS_SCHEMAS + getClassName(requestParam.fullyQualified));
                                        schemaWrapper.addProperty(requestParam.name, schemaRef);
                                        endpointInfo.additionalSchemaComponent.put(getClassName(requestParam.fullyQualified), entry.getValue());
                                    } else {
                                        schemaWrapper.addProperty(requestParam.name, entry.getValue());
                                    }
                                } else {
                                    endpointInfo.additionalSchemaComponent.put(entry.getKey(), entry.getValue());
                                }
                            });
                        }
                    });
                    pathItemComponent.pathItem = pathItem;
                    additionnalPathComponents.add(schemaWrapper);
                    pathItemComponent.schemaComponent = additionnalPathComponents;
                    pathItemComponent.componentName = getComponentName(endpointInfo.methodName, "Request");
                } else if (endpointInfo.requestBodyParametersMap.size() == 1) {
                    RequestParam requestParam = endpointInfo.requestBodyParametersMap.values().iterator().next();
                    pathItemComponent.pathItem = pathItem;
                    Map<String, Schema> schemas = buildSchemaForObject(requestParam);
                    schemas.entrySet().forEach(entry -> {
                        Schema schema = entry.getValue();
                        if (entry.getKey().equalsIgnoreCase(requestParam.name)) {
                            pathItemComponent.schemaComponent = Collections.singletonList(schema);
                        } else {
                            endpointInfo.additionalSchemaComponent.put(entry.getKey(), schema);
                        }
                    });
                    pathItemComponent.componentName = getClassName(requestParam.fullyQualified);
                } else {
                    pathItemComponent.pathItem = pathItem;
                }
                endpointInfo.pathItemComponent = pathItemComponent;
            }

            private void buildEndpointInfo(List<Expression> arguments, EndpointInfo endpointInfo) {
                arguments.forEach(expression -> {
                    String simpleName = ((J.Identifier) ((J.Assignment) expression).getVariable()).getSimpleName();
                    Expression assignment = ((J.Assignment) expression).getAssignment();
                    String value = null;
                    if (assignment instanceof J.Literal) {
                        value = (String) ((J.Literal) assignment).getValue();
                    } else if (assignment instanceof J.FieldAccess) {
                        value = ((J.FieldAccess) assignment).getSimpleName();
                    }
                    if (EndpointInfo.PATH_NAME.equals(simpleName)) {
                        endpointInfo.path = value;
                    } else if (EndpointInfo.ACTION_NAME.equals(simpleName)) {
                        endpointInfo.action = value;
                    } else if (EndpointInfo.TAG_NAME.equals(simpleName)) {
                        endpointInfo.tag = value;
                    } else if (EndpointInfo.DESCRIPTION_NAME.equals(simpleName)) {
                        endpointInfo.description = value;
                    }
                });
            }

            private Map<String, Schema> buildSchemaForObject(RequestParam requestParam) {
                LOG.info("create yaml definition for {}", requestParam.fullyQualified);
                try {
                    Class<?> aClass = getClass().getClassLoader().loadClass(requestParam.fullyQualified);
                    Map<String, io.swagger.v3.oas.models.media.Schema> schemas = ModelConverters.getInstance().readAll(aClass);
                    Map<String, Schema> schemaResultMap = new TreeMap<>();
                    if (schemas != null && !schemas.isEmpty()) {
                        schemas.entrySet().forEach(entry -> {
                            Schema schema = SchemaConverter.convert(entry.getValue());
                            if (entry.getKey().equalsIgnoreCase(getClassName(requestParam.fullyQualified))) {
                                schemaResultMap.put(requestParam.name, schema);
                            } else {
                                schemaResultMap.put(entry.getKey(), schema);
                            }
                        });
                        return schemaResultMap;
                    } else if (aClass.getEnumConstants() != null && aClass.getEnumConstants().length > 0) {
                        Schema schema = new SchemaImpl();
                        schema.setType(Schema.SchemaType.STRING);
                        schema.setEnumeration(Arrays.asList(aClass.getEnumConstants()).stream().map(Object::toString).collect(Collectors.toList()));
                        return Collections.singletonMap(requestParam.name, schema);
                    } else {
                        SchemaFormat schemaFormat = getSchemaFormat(requestParam.fullyQualified);
                        return Collections.singletonMap(requestParam.name, getSchema(schemaFormat.schemaType, schemaFormat.format));
                    }
                } catch (ClassNotFoundException e) {
                    LOG.error("ClassNotFoundException, Error while create schema for object " + requestParam.fullyQualified + ".\n -> Add this class to dependency in rewrite-maven-plugin");
                    SchemaFormat schemaFormat = getSchemaFormat(requestParam.fullyQualified);
                    if (schemaFormat.schemaType != null) {
                        return Collections.singletonMap(requestParam.name, getSchema(schemaFormat.schemaType, schemaFormat.format));
                    }
                }
                return Collections.emptyMap();
            }

            private Schema getSchema(Schema.SchemaType schemaType, String format) {
                Schema schema = new SchemaImpl();
                schema.setType(schemaType);
                if (format != null) {
                    schema.setFormat(format);
                }
                return schema;
            }

            /**
             *
             * @param fullyQualifiedName
             * @return
             */
            private String getClassName(String fullyQualifiedName) {
                JavaType.FullyQualified fullyQualified = TypeUtils.asFullyQualified(JavaType.buildType(fullyQualifiedName));
                if (fullyQualified != null) {
                    return fullyQualified.getClassName();
                } else {
                    return fullyQualifiedName;
                }
            }

            private SchemaFormat getSchemaFormat(String type) {
                SchemaFormat schemaFormat = new SchemaFormat();
                if (TypeUtils.isOfType(JavaType.buildType(type), JavaType.Primitive.Void)) {
                    return schemaFormat;
                } else if (TypeUtils.isOfType(JavaType.buildType(type), JavaType.Primitive.String)) {
                    schemaFormat.schemaType = Schema.SchemaType.STRING;
                    return schemaFormat;
                } else if (TypeUtils.isOfType(JavaType.buildType(type), JavaType.Primitive.Boolean) || type.equals(Boolean.class.getName())) {
                    schemaFormat.schemaType = Schema.SchemaType.BOOLEAN;
                    return schemaFormat;
                } else if (Date.class.getName().equals(type) || LocalDate.class.getName().equals(type)) {
                    schemaFormat.schemaType = Schema.SchemaType.STRING;
                    schemaFormat.format = "date";
                    return schemaFormat;
                } else if (LocalTime.class.getName().equals(type) || LocalDateTime.class.getName().equals(type) || OffsetDateTime.class.getName().equals(type)) {
                    schemaFormat.schemaType = Schema.SchemaType.STRING;
                    schemaFormat.format = "date-time";
                    return schemaFormat;
                } else if (isIntegerNumeric(type)) {
                    Optional<String> numericFormat = getNumericFormat(type);
                    schemaFormat.schemaType = Schema.SchemaType.INTEGER;
                    numericFormat.ifPresent(string -> schemaFormat.format = string);
                    return schemaFormat;
                } else if (isDoubleNumeric(type)) {
                    Optional<String> numericFormat = getNumericFormat(type);
                    schemaFormat.schemaType = Schema.SchemaType.NUMBER;
                    numericFormat.ifPresent(string -> schemaFormat.format = string);
                    return schemaFormat;
                } else {
                    schemaFormat.schemaType = Schema.SchemaType.OBJECT;
                    return schemaFormat;
                }
            }

            private Optional<String> getNumericFormat(String type) {
                if (isLong(type)) {
                    return Optional.of("int64");
                } else if (isInteger(type)) {
                    return Optional.of("int32");
                } else if (isDoubleNumeric(type)) {
                    return Optional.of("double");
                } else {
                    return Optional.empty();
                }
            }

            private boolean isLong(String type) {
                return (TypeUtils.isOfType(JavaType.buildType(type), JavaType.Primitive.Long)
                        || type.equals(Long.class.getName()));
            }

            private boolean isInteger(String type) {
                return (TypeUtils.isOfType(JavaType.buildType(type), JavaType.Primitive.Int)
                        || type.equals(Integer.class.getName()));
            }

            private boolean isIntegerNumeric(String type) {
                return (TypeUtils.isOfType(JavaType.buildType(type), JavaType.Primitive.Long)
                        || TypeUtils.isOfType(JavaType.buildType(type), JavaType.Primitive.Int)
                        || type.equals(Long.class.getName()) || type.equals(Integer.class.getName()));
            }

            private boolean isDoubleNumeric(String type) {
                return (TypeUtils.isOfType(JavaType.buildType(type), JavaType.Primitive.Float)
                        || TypeUtils.isOfType(JavaType.buildType(type), JavaType.Primitive.Double)
                        || TypeUtils.isOfType(JavaType.buildType(type), JavaType.Primitive.Short)
                        || type.equals(Double.class.getName()) || type.equals(Float.class.getName())
                        || type.equals(Short.class.getName()) || type.equals(BigDecimal.class.getName()));
            }

            private boolean isCollection(String type) {
                return type.equalsIgnoreCase(List.class.getSimpleName()) || type.equalsIgnoreCase(Collection.class.getSimpleName()) || type.equalsIgnoreCase(Set.class.getSimpleName());
            }

            private Optional<String> getContainerType(String type) {
                if (type.equalsIgnoreCase(Map.class.getSimpleName())) {
                    return Optional.of("List");
                } else {
                    return Optional.empty();
                }
            }

            private String toDashCase(final String value) {
                String regex = "(?=[A-Z][a-z])";
                String subst = "-";
                Pattern pattern = Pattern.compile(regex);
                Matcher matcher = pattern.matcher(value);
                String result = matcher.replaceAll(subst);
                return result.toLowerCase();
            }

            private String getComponentName(final String value, final String suffix) {
                return RewriteUtils.firstUpperCase(value + suffix);
            }
        };
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(String tDirectory) {
        return new EJBRemoteToRestVisitor(tDirectory);
    }

    public static class EJBRemoteToRestVisitor extends JavaIsoVisitor<ExecutionContext> {

        private final String tDirectory;

        public EJBRemoteToRestVisitor(String tDirectory) {
            this.tDirectory = tDirectory;
        }

        @Override
        public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext executionContext) {
            J.ClassDeclaration classDeclaration = super.visitClassDeclaration(classDecl, executionContext);
            Object message = executionContext.getMessage(classDeclaration.getSimpleName());
            if (message instanceof String) {
                LOG.info("Create component for class " + classDeclaration.getType().getFullyQualifiedName());

            } else if (message instanceof OpenAPI) {
                LOG.info("Call openApiVisitor after visit");
                doAfterVisit(new GenerateOpenApiVisitor(tDirectory));
            }
            return classDeclaration;
        }
    }

    class AppInfo {
        String appName;

        String appDescription;

        String appVersion;

        AppInfo(final String appName, final String appDescription, final String version) {
            this.appName = appName;
            this.appDescription = appDescription;
            this.appVersion = version;
        }
    }

    static class EndpointInfo {

        final static String PATH_NAME = "path";

        final static String TAG_NAME = "tag";

        final static String DESCRIPTION_NAME = "description";

        final static String ACTION_NAME = "action";

        String path;

        String tag;

        String description;

        String action;

        String operationId;

        String methodName;

        Map<String, RequestParam> requestParametersMap = new HashMap<>();

        Map<String, RequestParam> requestBodyParametersMap = new HashMap<>();

        ResponseItemComponent responseItemComponent;

        PathItemComponent pathItemComponent;

        Map<String, Schema> additionalSchemaComponent = new TreeMap<>();

    }

    static class RequestParam {
        String fullyQualified;

        String name;
        String requestWrapper;
    }

    static class ResponseItemComponent {

        String fullyQualified;

        String componentName;

        @Deprecated
        Schema.SchemaType componentType;

        List<Schema.SchemaType> componentTypeList = new ArrayList<>();

        String responseWrapper;

    }

    static class PathItemComponent {

        PathItem pathItem;

        List<Schema> schemaComponent;

        String componentName;

    }

    static class SchemaFormat {

        Schema.SchemaType schemaType;

        String format;
    }
}
