package org.refactor.eap6.rest;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.tree.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Value
@EqualsAndHashCode(callSuper = false)
public class BlockingToReactiveRest extends Recipe {

    private static final Logger LOG = LoggerFactory.getLogger(BlockingToReactiveRest.class);

    private static final String GET_REST_ANNOTATION_NAME = "GET";

    private static final String BLOCKING_ANNOTATION_NAME = "Blocking";

    private static final String fullyQualifiedBlocking = "io.smallrye.common.annotation.Blocking";

    private static final String fullyQualifiedMulti = "io.smallrye.mutiny.Multi";

    private static final String multi = "Multi";

    private static final JavaTemplate injectAnnotationTemplate = JavaTemplate.builder("@Blocking")
            .javaParser(JavaParser.fromJavaVersion().dependsOn("package io.smallrye.common.annotation; public @interface Blocking {}"))
            .imports(fullyQualifiedBlocking).build();

    private static final JavaTemplate producesAnnotationTemplate = JavaTemplate.builder("@Produces({\"application/x-ndjson\"})").build();

    private static final JavaTemplate reactiveExpressionTemplate = JavaTemplate.builder("return Multi.createFrom().items(#{}.stream())").build();

    @Override
    public String getDisplayName() {
        return "Refactor blocking rest call to reactive call";
    }

    @Override
    public String getDescription() {
        return "Refactor blocking rest call to reactive call.";
    }


    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(10);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new BlockingToReactiveVisitor();
    }

    public static class BlockingToReactiveVisitor extends JavaIsoVisitor<ExecutionContext> {

        @Override
        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext executionContext) {
            J.MethodDeclaration methodDeclaration = super.visitMethodDeclaration(method, executionContext);
            if (methodDeclaration.getLeadingAnnotations().stream().noneMatch(annotation -> annotation.getSimpleName().equals(BLOCKING_ANNOTATION_NAME))
                && methodDeclaration.getLeadingAnnotations().stream().anyMatch(annotation -> annotation.getSimpleName().equals(GET_REST_ANNOTATION_NAME))) {
                if (methodDeclaration.getBody() == null || methodDeclaration.getBody().getStatements().size() != 1) {
                    return methodDeclaration;
                }
                Statement statement = methodDeclaration.getBody().getStatements().get(0);
                if (!(statement instanceof J.Return)) {
                    return methodDeclaration;
                }

                if (methodDeclaration.getReturnTypeExpression() instanceof J.ParameterizedType) {
                    JavaType type = methodDeclaration.getReturnTypeExpression().getType();
                    if (TypeUtils.isOfClassType(type, "java.util.List")) {
                        maybeAddImport(fullyQualifiedBlocking);
                        methodDeclaration = injectAnnotationTemplate.apply(getCursor(), methodDeclaration.getCoordinates().addAnnotation(Comparator.comparing(J.Annotation::getId)));

                        List<J.Annotation> annotations = getAnnotations(methodDeclaration.getLeadingAnnotations());
                        methodDeclaration = methodDeclaration.withLeadingAnnotations(annotations);
                        methodDeclaration = producesAnnotationTemplate.apply(updateCursor(methodDeclaration), methodDeclaration.getCoordinates().addAnnotation(Comparator.comparing(J.Annotation::getSimpleName)));

                        TypeTree returnTypeExpression = methodDeclaration.getReturnTypeExpression();
                        methodDeclaration = methodDeclaration.withReturnTypeExpression(((J.ParameterizedType) returnTypeExpression).withClazz(((J.Identifier) ((J.ParameterizedType) returnTypeExpression).getClazz()).withSimpleName(multi)));
                        maybeAddImport(fullyQualifiedMulti, false);

                        J.Return jreturn = (J.Return)statement;
                        methodDeclaration = reactiveExpressionTemplate.apply(updateCursor(methodDeclaration), jreturn.getCoordinates().replace(), jreturn.getExpression().toString());

                        return methodDeclaration;
                    }
                }
            }
            return methodDeclaration;
        }

        /**
         *
         * @param annotations
         * @return
         */
        private List<J.Annotation> getAnnotations(List<J.Annotation> annotations) {
            return annotations.stream().filter(annotation -> !annotation.getSimpleName().equals("Produces")).collect(Collectors.toList());
        }

        private JavaType getNewType(String fullyQualifiedType) {
            return  JavaType.ShallowClass.build(fullyQualifiedType);
        }

        private JavaType.Method updateType(JavaType.Method mt) {
            if (mt != null) {
                return mt.withReturnType(getNewType(fullyQualifiedMulti));
            }
            return null;
        }

    }
}
