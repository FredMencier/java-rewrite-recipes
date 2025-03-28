
package org.refactor.eap6.java.ejb;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.smallrye.openapi.api.models.*;
import io.smallrye.openapi.api.models.info.InfoImpl;
import io.smallrye.openapi.api.models.media.ContentImpl;
import io.smallrye.openapi.api.models.media.DiscriminatorImpl;
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
import org.openrewrite.*;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.*;
import org.refactor.eap6.java.annotation.ToRest;
import org.refactor.eap6.util.RewriteUtils;
import org.refactor.eap6.yaml.util.SchemaConverter;
import org.reflections.Reflections;
import org.reflections.scanners.SubTypesScanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.HttpMethod;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListMap;
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

    List<J.CompilationUnit> compilationUnits = new ArrayList<>();

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
            public @org.jspecify.annotations.Nullable J visit(@org.jspecify.annotations.Nullable Tree tree, ExecutionContext executionContext) {
                if (tree instanceof SourceFile && ((SourceFile) tree).getSourcePath().getFileName().toString().contains(".java")) {
                    compilationUnits.add((J.CompilationUnit) tree);
                }
                return super.visit(tree, executionContext);
            }

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

                Schema schemaResponse = buildSchema(returnTypeExpression, endpointInfo.additionalSchemaComponent, 0, null);
                ResponseItemComponent responseItemComponent = new ResponseItemComponent();
                responseItemComponent.componentName = getComponentName(endpointInfo.methodName, "Response");
                responseItemComponent.componentTypeList.add(schemaResponse);
                endpointInfo.responseItemComponent = responseItemComponent;

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
                    if (statement instanceof J.Empty) {
                        return;
                    }
                    J.VariableDeclarations variableDeclarations = (J.VariableDeclarations) statement;
                    TypeTree typeExpression = variableDeclarations.getTypeExpression();
                    ComponentParam componentParam = new ComponentParam();
                    Schema schemaRequest = buildSchema(typeExpression, endpointInfo.additionalSchemaComponent, 0, null);
                    String name = variableDeclarations.getVariables().get(0).getVariableType().getName();
                    componentParam.name = name;
                    componentParam.schema = schemaRequest;
                    JavaType.FullyQualified fullyQualified = TypeUtils.asFullyQualified(typeExpression.getType());
                    if (fullyQualified != null) {
                        componentParam.fullyQualified = fullyQualified.getFullyQualifiedName();
                    }
                    Optional<String> className = getClassName(componentParam.fullyQualified);
                    if (componentParam.schema.getRef() != null || (className.isPresent() && isCollection(className.get()))) {
                        endpointInfo.requestBodyParametersMap.put(name, componentParam);
                    } else {
                        endpointInfo.requestParametersMap.put(name, componentParam);
                    }
                });
                endpointInfos.add(endpointInfo);
            }

            /**
             *
             * @param parameterType
             * @param additionalSchemaComponent
             * @return
             */
            private Schema buildSchema(TypeTree parameterType, Map<String, Schema> additionalSchemaComponent, int depth, InheritanceInfo inheritInfo) {
                Schema schema = new SchemaImpl();
                depth++;
                if (parameterType instanceof J.ParameterizedType) {
                    List<Expression> typeParameters = ((J.ParameterizedType) parameterType).getTypeParameters();
                    String simpleName = ((J.Identifier) (((J.ParameterizedType) parameterType).getClazz())).getSimpleName();
                    if (isCollection(simpleName)) {
                        schema.setType(Schema.SchemaType.ARRAY);
                        if (isUniqueCollection(simpleName)) {
                            schema.uniqueItems(true);
                        }
                        Expression last = typeParameters.get(0);
                        schema.setItems(buildSchema((TypeTree) last, additionalSchemaComponent, depth, inheritInfo));
                    } else if (isMap(simpleName)) {
                        Expression first = typeParameters.get(0);
                        Expression last = typeParameters.get(1);
                        String fullyQualifiedType = first.getType().toString();
                        if (fullyQualifiedType.equals(String.class.getName())) {
                            schema.setType(Schema.SchemaType.OBJECT);
                            schema.setAdditionalPropertiesSchema(buildSchema((TypeTree) last, additionalSchemaComponent, depth, inheritInfo));
                        } else {
                            String objectName = "CompositeMapResponse" + depth;
                            schema.setRef(ROOT_PATH_COMPONENTS_SCHEMAS + objectName);
                            Schema schemaMapComposite = new SchemaImpl();
                            schemaMapComposite.setDescription("wrapper for Map " + typeParameters);
                            Schema schemaFirst = buildSchema((TypeTree) first, additionalSchemaComponent, depth, inheritInfo);
                            Schema schemaFirstList = new SchemaImpl();
                            schemaFirstList.setType(Schema.SchemaType.ARRAY);
                            schemaFirstList.setItems(schemaFirst);

                            Schema schemaLast = buildSchema((TypeTree) last, additionalSchemaComponent, depth, inheritInfo);
                            Schema schemaLastList = new SchemaImpl();
                            schemaLastList.setType(Schema.SchemaType.ARRAY);
                            schemaLastList.setItems(schemaLast);

                            schemaMapComposite.addProperty("first", schemaFirstList);
                            schemaMapComposite.addProperty("last", schemaLastList);
                            additionalSchemaComponent.put(objectName, schemaMapComposite);
                        }
                    }
                } else {
                    Map<String, Schema> schemaMap = getComponentSchemas(parameterType);
                    String key;
                    if (parameterType instanceof J.Primitive) {
                        key = ((J.Primitive) parameterType).getType().toString();
                    } else {
                        key = ((J.Identifier) parameterType).getSimpleName();
                    }
                    if (schemaMap.size() == 1) {
                        Schema schemaObject = schemaMap.get(key);
                        if (schemaObject.getType().equals(Schema.SchemaType.OBJECT)) {
                            String fullyQualifiedObjectname = schemaObject.getDescription();
                            InheritanceInfo inheritanceInfo = getSubtypes(fullyQualifiedObjectname);
                            if (inheritanceInfo.subtypes.isEmpty()) {
                                schema.setRef(ROOT_PATH_COMPONENTS_SCHEMAS + key);
                                schemaMap.forEach((key1, value) -> {
                                    if (inheritInfo!= null && !inheritInfo.isInterface) {
                                        Schema globalSchema = new SchemaImpl();
                                        Schema ref = new SchemaImpl();
                                        ref.setRef(ROOT_PATH_COMPONENTS_SCHEMAS + getClassName(inheritInfo.fullyQualifiedSuperClass).get());
                                        globalSchema.addAllOf(ref);
                                        globalSchema.addAllOf(value);
                                        additionalSchemaComponent.put(key1, globalSchema);
                                    } else {
                                        additionalSchemaComponent.put(key1, value);
                                    }
                                });
                            } else {
                                inheritanceInfo.subtypes.forEach(subtype -> {
                                    Schema schemaOneOf = new SchemaImpl();
                                    schemaOneOf.setRef(ROOT_PATH_COMPONENTS_SCHEMAS + getClassName(subtype.getName()).get());
                                    schema.addOneOf(schemaOneOf);
                                    addSubtypeToAdditionnalSchemaConmponent(subtype.getName(), additionalSchemaComponent, inheritanceInfo);
                                });

                                if (!inheritanceInfo.isInterface) {
                                    DiscriminatorImpl discriminatorForPath = new DiscriminatorImpl();
                                    String discriminatorPropertyName = "type_" + key.toLowerCase();
                                    discriminatorForPath.propertyName(discriminatorPropertyName);
                                    schema.setDiscriminator(discriminatorForPath);

                                    List<String> required = new ArrayList<>();
                                    required.add(discriminatorPropertyName);
                                    schemaObject.setRequired(required);
                                    DiscriminatorImpl discriminatorObject = new DiscriminatorImpl();
                                    discriminatorObject.propertyName(discriminatorPropertyName);
                                    schemaObject.setDiscriminator(discriminatorObject);
                                    Map<String, Schema> properties = new HashMap<>(schemaObject.getProperties());
                                    properties.put(discriminatorPropertyName, new SchemaImpl().type(Schema.SchemaType.STRING));
                                    schemaObject.setProperties(properties);
                                    additionalSchemaComponent.put(key, schemaObject);
                                }
                            }
                        } else {
                            schema.setType(schemaObject.getType());
                            if (schemaObject.getFormat() != null) {
                                schema.setFormat(schemaObject.getFormat());
                            }
                        }
                    } else {
                        schema.setRef(ROOT_PATH_COMPONENTS_SCHEMAS + key);
                        schemaMap.forEach((key1, value) -> additionalSchemaComponent.put(key, value));
                    }
                }
                return schema;
            }

            private void addSubtypeToAdditionnalSchemaConmponent(String fullyQualifiedObjectname, Map<String, Schema> additionalSchemaComponent, InheritanceInfo inheritanceInfo) {
                Set<NameTree> typeTrees = new HashSet<>();
                for (J.CompilationUnit compilationUnit : compilationUnits) {
                    typeTrees = compilationUnit.findType(fullyQualifiedObjectname);
                    if (!typeTrees.isEmpty()) {
                        break;
                    }
                }
                NameTree nameTree = typeTrees.iterator().next();
                buildSchema((TypeTree) nameTree, additionalSchemaComponent, 0, inheritanceInfo);
            }

            /**
             *
             * @param fullyQualifiedObjectname
             * @return
             */
            private InheritanceInfo getSubtypes(String fullyQualifiedObjectname) {
                InheritanceInfo inheritanceInfo = new InheritanceInfo();
                if (fullyQualifiedObjectname != null) {
                    try {
                        Class<?> aClass = getClass().getClassLoader().loadClass(fullyQualifiedObjectname);
                        inheritanceInfo.isInterface = aClass.isInterface();
                        inheritanceInfo.fullyQualifiedSuperClass = fullyQualifiedObjectname;
                        String pack = aClass.getPackage().getName();
                        //TODO ici il faudrait faire une boucle pour rechercher egalement dans les sous packages
                        Reflections reflections = new Reflections(pack, new SubTypesScanner(false));
                        inheritanceInfo.subtypes = reflections.getSubTypesOf((Class<Object>) aClass);
                    } catch (ClassNotFoundException e) {
                        LOG.error("Cannot load class {}", fullyQualifiedObjectname);
                    }
                }
                return inheritanceInfo;
            }

            /**
             *
             * @param parameterType
             * @return
             */
            private Map<String, Schema> getComponentSchemas(TypeTree parameterType) {
                ComponentParam componentParam = new ComponentParam();
                componentParam.fullyQualified = parameterType.getType().toString();
                if (parameterType instanceof J.Identifier) {
                    componentParam.name = ((J.Identifier) parameterType).getSimpleName();
                } else if (parameterType instanceof J.Primitive) {
                    componentParam.name = ((J.Primitive) parameterType).toString();
                }
                return buildSchemaForObject(componentParam);
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
                        endpointInfo.additionalSchemaComponent.forEach(components::addSchema);
                    }
                    if (endpointInfo.responseItemComponent != null) {
                        components.addSchema(endpointInfo.responseItemComponent.componentName, endpointInfo.responseItemComponent.componentTypeList.get(0));
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
                    String requestWrapperName = getComponentName(endpointInfo.methodName, "Request");
                    schema.setRef(ROOT_PATH_COMPONENTS_SCHEMAS + requestWrapperName);
                    mediaType.setSchema(schema);
                    content.setMediaTypes(Collections.singletonMap(javax.ws.rs.core.MediaType.APPLICATION_JSON, mediaType));
                    requestBody.setContent(content);
                    operation.setRequestBody(requestBody);

                    //On construit le schema pour le wrapper request
                    Schema schemaWrapper = new SchemaImpl();
                    schemaWrapper.setType(Schema.SchemaType.OBJECT);
                    List<String> descriptionList = new ArrayList<>();
                    endpointInfo.requestBodyParametersMap.forEach((name, componentParam) -> {
                        Optional<String> className = getClassName(componentParam.fullyQualified);
                        className.ifPresent(descriptionList::add);
                        schemaWrapper.addProperty(name, componentParam.schema);
                    });
                    schemaWrapper.setDescription("Wrapper for " + descriptionList);
                    endpointInfo.additionalSchemaComponent.put(requestWrapperName, schemaWrapper);
                } else if (!endpointInfo.requestParametersMap.isEmpty()) {
                    endpointInfo.requestParametersMap.forEach((name, componentParam) -> {
                        Parameter parameter = new ParameterImpl();
                        parameter.setName(name);
                        parameter.setIn(Parameter.In.QUERY);
                        LOG.info("parameters {} with type {}", name, componentParam);
                        parameter.setSchema(componentParam.schema);
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
                    SchemaFormat schemaFormat = getSchemaFormat(endpointInfo.responseItemComponent.fullyQualified != null ? endpointInfo.responseItemComponent.fullyQualified : endpointInfo.responseItemComponent.componentName);
                    apiResponse.setDescription("OK");
                    Content content = new ContentImpl();
                    MediaType mediaType = new MediaTypeImpl();
                    Schema schema = new SchemaImpl();
                    if (!schemaFormat.schemaType.equals(Schema.SchemaType.OBJECT)) {
                        if (endpointInfo.responseItemComponent.responseWrapper != null && isCollection(endpointInfo.responseItemComponent.responseWrapper)) {
                            schema.setType(Schema.SchemaType.ARRAY);
                            Schema schemaItem = new SchemaImpl();
                            schemaItem.setType(schemaFormat.schemaType);
                            schema.setItems(schemaItem);
                            if (isUniqueCollection(endpointInfo.responseItemComponent.responseWrapper)) {
                                schema.uniqueItems(true);
                            }
                        } else {
                            schema.setType(schemaFormat.schemaType);
                        }
                    } else {
                        if (endpointInfo.responseItemComponent.responseWrapper != null && isCollection(endpointInfo.responseItemComponent.responseWrapper)) {
                            schema.setType(Schema.SchemaType.ARRAY);
                            Schema schemaItem = new SchemaImpl();
                            schemaItem.setRef(ROOT_PATH_COMPONENTS_SCHEMAS + endpointInfo.responseItemComponent.componentName);
                            schema.setItems(schemaItem);
                            if (isUniqueCollection(endpointInfo.responseItemComponent.responseWrapper)) {
                                schema.uniqueItems(true);
                            }
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
                pathItemComponent.pathItem = pathItem;
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

            private Map<String, Schema> buildSchemaForObject(ComponentParam componentParam) {
                LOG.info("create yaml definition for {}", componentParam.fullyQualified);
                try {
                    Class<?> aClass = getClass().getClassLoader().loadClass(componentParam.fullyQualified);
                    Map<String, io.swagger.v3.oas.models.media.Schema> schemas = ModelConverters.getInstance().readAll(aClass);
                    Map<String, Schema> schemaResultMap = new TreeMap<>();
                    if (schemas != null && !schemas.isEmpty()) {
                        schemas.forEach((key, value) -> {
                            Schema schema = SchemaConverter.convert(value);
                            schema.setDescription(componentParam.fullyQualified);
                            Optional<String> className = getClassName(componentParam.fullyQualified);
                            if (className.isPresent() && key.equalsIgnoreCase(className.get())) {
                                schemaResultMap.put(componentParam.name, schema);
                            } else {
                                schemaResultMap.put(key, schema);
                            }
                        });
                        return schemaResultMap;
                    } else if (aClass.getEnumConstants() != null && aClass.getEnumConstants().length > 0) {
                        Schema schema = new SchemaImpl();
                        schema.setType(Schema.SchemaType.STRING);
                        schema.setEnumeration(Arrays.stream(aClass.getEnumConstants()).map(Object::toString).collect(Collectors.toList()));
                        return Collections.singletonMap(componentParam.name, schema);
                    } else {
                        SchemaFormat schemaFormat = getSchemaFormat(componentParam.fullyQualified);
                        return Collections.singletonMap(componentParam.name, getSchema(schemaFormat.schemaType, schemaFormat.format));
                    }
                } catch (ClassNotFoundException e) {
                    LOG.error("ClassNotFoundException, Error while create schema for object " + componentParam.fullyQualified + ".\n -> Add this class to dependency in rewrite-maven-plugin");
                    SchemaFormat schemaFormat = getSchemaFormat(componentParam.fullyQualified);
                    if (schemaFormat.schemaType != null) {
                        return Collections.singletonMap(componentParam.name, getSchema(schemaFormat.schemaType, schemaFormat.format));
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
            private Optional<String> getClassName(String fullyQualifiedName) {
                if (fullyQualifiedName == null) {
                    return Optional.empty();
                }
                JavaType.FullyQualified fullyQualified = TypeUtils.asFullyQualified(JavaType.buildType(fullyQualifiedName));
                if (fullyQualified != null) {
                    return Optional.of(fullyQualified.getClassName());
                } else {
                    return Optional.of(fullyQualifiedName);
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
                return type.equalsIgnoreCase(List.class.getSimpleName())
                        || type.equalsIgnoreCase(Collection.class.getSimpleName())
                        || type.equalsIgnoreCase(ArrayList.class.getSimpleName())
                        || type.equalsIgnoreCase(Vector.class.getSimpleName())
                        || type.equalsIgnoreCase(LinkedList.class.getSimpleName())
                        || type.equalsIgnoreCase(Stack.class.getSimpleName())
                        || isUniqueCollection(type);
            }

            private boolean isMap(String type) {
                return type.equalsIgnoreCase(Map.class.getSimpleName())
                        || type.equalsIgnoreCase(HashMap.class.getSimpleName())
                        || type.equalsIgnoreCase(LinkedHashMap.class.getSimpleName())
                        || type.equalsIgnoreCase(TreeMap.class.getSimpleName())
                        || type.equalsIgnoreCase(Hashtable.class.getSimpleName())
                        || type.equalsIgnoreCase(ConcurrentHashMap.class.getSimpleName())
                        || type.equalsIgnoreCase(ConcurrentMap.class.getSimpleName())
                        || type.equalsIgnoreCase(ConcurrentSkipListMap.class.getSimpleName())
                        || type.equalsIgnoreCase(EnumMap.class.getSimpleName())
                        || type.equalsIgnoreCase(SortedMap.class.getSimpleName())
                        || type.equalsIgnoreCase(NavigableMap.class.getSimpleName());
            }

            private boolean isUniqueCollection(String type) {
                return type.equalsIgnoreCase(Set.class.getSimpleName())
                        || type.equalsIgnoreCase(SortedSet.class.getSimpleName())
                        || type.equalsIgnoreCase(HashSet.class.getSimpleName())
                        || type.equalsIgnoreCase(TreeSet.class.getSimpleName())
                        || type.equalsIgnoreCase(LinkedHashSet.class.getSimpleName());
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
            if (message instanceof OpenAPI) {
                LOG.info("Call openApiVisitor after visit");
                doAfterVisit(new GenerateOpenApiVisitor(tDirectory));
            }
            return classDeclaration;
        }
    }

    static class AppInfo {
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

        Map<String, ComponentParam> requestParametersMap = new HashMap<>();

        Map<String, ComponentParam> requestBodyParametersMap = new HashMap<>();

        ResponseItemComponent responseItemComponent;

        PathItemComponent pathItemComponent;

        Map<String, Schema> additionalSchemaComponent = new TreeMap<>();

    }

    static class ComponentParam {
        String fullyQualified;

        Schema schema;

        String name;
    }

    static class ResponseItemComponent {

        String fullyQualified;

        String componentName;

        List<Schema> componentTypeList = new ArrayList<>();

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

    static class InheritanceInfo {

        Set<Class<?>> subtypes = new HashSet<>();

        String fullyQualifiedSuperClass;

        boolean isInterface;
    }
}
