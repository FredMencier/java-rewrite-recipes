package org.refactor.eap6.java.ejb;

import org.openrewrite.ExecutionContext;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.tree.J;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class EJBAnnotationVariableVisitor extends JavaIsoVisitor<ExecutionContext> {

    private static final String fullyQualifiedEJB = "javax.ejb.EJB";

    private static final String fullyQualifiedInject = "javax.inject.Inject";

    private final JavaTemplate injectAnnotationTemplate = JavaTemplate.builder("@Inject")
            .javaParser(JavaParser.fromJavaVersion().dependsOn("package javax.inject; public @interface Inject {}", "package javax.ejb; public @interface EJB {}"))
            .imports(fullyQualifiedInject, fullyQualifiedEJB).build();

    @Override
    public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext executionContext) {
        J.VariableDeclarations variableDeclarations = super.visitVariableDeclarations(multiVariable, executionContext);

        if (variableDeclarations.getAllAnnotations().stream().noneMatch(annotation -> annotation.getSimpleName().equals("Inject"))
                && variableDeclarations.getAllAnnotations().stream().anyMatch(annotation -> annotation.getSimpleName().equals("EJB"))) {
            List<J.Annotation> annotationList = new ArrayList<>();
            List<J.Annotation> allAnnotations = variableDeclarations.getAllAnnotations();
            for (J.Annotation annotation : allAnnotations) {
                if (!annotation.getSimpleName().equals("EJB")) {
                    annotationList.add(annotation);
                }
            }
            maybeRemoveImport(fullyQualifiedEJB);
            maybeAddImport(fullyQualifiedInject, false);
            variableDeclarations = injectAnnotationTemplate.apply(getCursor(), variableDeclarations.getCoordinates().addAnnotation(Comparator.comparing(J.Annotation::getSimpleName)));

            Optional<J.Annotation> injectAnnotation = variableDeclarations.getAllAnnotations().stream().filter(annotation -> annotation.getSimpleName().equals("Inject")).findFirst();
            injectAnnotation.ifPresent(annotationList::add);
            variableDeclarations = variableDeclarations.withLeadingAnnotations(annotationList);
        }

        return variableDeclarations;
    }
}
