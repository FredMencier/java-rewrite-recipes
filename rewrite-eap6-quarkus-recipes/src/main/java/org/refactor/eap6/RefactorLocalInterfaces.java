package org.refactor.eap6;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.EqualsAndHashCode;
import lombok.Value;
import org.refactor.eap6.pojo.ImplementationClassName;
import org.openrewrite.ExecutionContext;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Value
@EqualsAndHashCode(callSuper = false)
public class RefactorLocalInterfaces extends ScanningRecipe<RefactorLocalInterfaces.Scanned> {

    private static final Logger LOG = LoggerFactory.getLogger(RefactorLocalInterfaces.class);

    @JsonCreator
    public RefactorLocalInterfaces() {
    }

    @Override
    public String getDisplayName() {
        return "RefactorLocalInterfaces";
    }

    @Override
    public String getDescription() {
        return "Work with local interfaces.";
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(3);
    }

    static class Scanned {
        private final Map<String, Set<ImplementationClassName>> fullyQualifiedLocalInterfaceList = new HashMap<>();
    }

    Set<J.ClassDeclaration> classDeclarations = new HashSet<>();

    @Override
    public Scanned getInitialValue(ExecutionContext ctx) {
        return new Scanned();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Scanned acc) {

        return new JavaIsoVisitor<>() {

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext executionContext) {
                classDeclarations.add(classDecl);
                if (HasAnnotation.find(getCursor().getParentTreeCursor().getValue(), classDecl, "Local").get()) {
                    if (classDecl.getType() != null) {
                        acc.fullyQualifiedLocalInterfaceList.put(classDecl.getType().getFullyQualifiedName(), new HashSet<>());
                    }
                }
                classDeclarations.forEach(classDeclaration -> acc.fullyQualifiedLocalInterfaceList.keySet().forEach(s -> {
                    if (!TypeUtils.isOfClassType(classDeclaration.getType(), s) &&
                            TypeUtils.isAssignableTo(s, classDeclaration.getType())) {
                        ImplementationClassName implementationClassName;
                        if (!HasAnnotation.find(getCursor().getParentTreeCursor().getValue(), classDecl,"Alternative").get()) {
                            implementationClassName = new ImplementationClassName(classDeclaration.getType().getFullyQualifiedName(), true);
                        } else {
                            implementationClassName = new ImplementationClassName(classDeclaration.getType().getFullyQualifiedName(), false);
                        }
                        Set<ImplementationClassName> implementationClassNames = acc.fullyQualifiedLocalInterfaceList.get(s);
                        Optional<ImplementationClassName> found = implementationClassNames.stream().filter(implementationClassName1 -> implementationClassName1.getClassname().equals(implementationClassName.getClassname())).findFirst();
                        if (found.isEmpty()) {
                            implementationClassNames.add(implementationClassName);
                        }
                        acc.fullyQualifiedLocalInterfaceList.replace(s, implementationClassNames);
                    }
                }));
                return classDecl;
            }
        };
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Scanned scanned) {
        return new LocalInterfacesVisitor(scanned);
    }

    public static class LocalInterfacesVisitor extends JavaIsoVisitor<ExecutionContext> {

        private final Scanned scan;

        public LocalInterfacesVisitor(Scanned scanned) {
            this.scan = scanned;
        }

        @Override
        public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext executionContext) {
            J.ClassDeclaration classDeclaration = super.visitClassDeclaration(classDecl, executionContext);
            doAfterVisit(new ReplaceLocalInterface.ReplaceLocalInterfaceVisitor(scan.fullyQualifiedLocalInterfaceList));
            return classDeclaration;
        }
    }

    @Value
    @EqualsAndHashCode(callSuper = true)
    private static class HasAnnotation extends JavaIsoVisitor<AtomicBoolean> {
        J.ClassDeclaration.ClassDeclaration classDeclaration;

        String annotationSimpleName;

        static AtomicBoolean find(J j, J.ClassDeclaration.ClassDeclaration classDeclaration, String annotationSimpleName) {
            return new HasAnnotation(classDeclaration, annotationSimpleName)
                    .reduce(j, new AtomicBoolean());
        }

        @Override
        public J.Annotation visitAnnotation(J.Annotation annotation, AtomicBoolean hasAnnotation) {
            if (hasAnnotation.get()) {
                return annotation;
            }
            List<J.Annotation> leadingAnnotations = classDeclaration.getLeadingAnnotations();
            for (J.Annotation anno : leadingAnnotations) {
                if (anno.getSimpleName().equals(annotationSimpleName)) {
                    LOG.info("--> Find Annotation {} in {}", annotationSimpleName, classDeclaration.getType().getFullyQualifiedName());
                    hasAnnotation.set(true);
                }
            }
            return annotation;
        }
    }
}
