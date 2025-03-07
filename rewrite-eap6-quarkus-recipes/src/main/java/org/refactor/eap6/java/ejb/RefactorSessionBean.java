package org.refactor.eap6.java.ejb;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jetbrains.annotations.NotNull;
import org.openrewrite.ExecutionContext;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.TypeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Value
@EqualsAndHashCode(callSuper = false)
public class RefactorSessionBean extends ScanningRecipe<RefactorSessionBean.Scanned> {

    private static final Logger LOG = LoggerFactory.getLogger(RefactorSessionBean.class);
    private static final String NEW_LINE = "\r\n";

    @JsonCreator
    public RefactorSessionBean() {
    }

    @Override
    public String getDisplayName() {
        return "ReplaceStatelessAnnotation";
    }

    @Override
    public String getDescription() {
        return "Replace EJB Stateless annotation with ApplicationScoped.";
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(10);
    }

    static class Scanned {
        private final Set<String> fullyQualifiedRemoteInterfaceList = new HashSet<>();
    }

    @Override
    public Scanned getInitialValue(ExecutionContext ctx) {
        return new Scanned();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Scanned scanned) {
        return new ReplaceStatelessVisitor(scanned);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Scanned acc) {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext executionContext) {
                if (FindRemoteInterface.find(getCursor().getParentTreeCursor().getValue(), classDecl).get()) {
                    acc.fullyQualifiedRemoteInterfaceList.add(classDecl.getType().getFullyQualifiedName());
                }
                return classDecl;
            }
        };
    }

    public class ReplaceStatelessVisitor extends JavaIsoVisitor<ExecutionContext> {

        private static final String fullyQualifiedImportApplicationScoped = "javax.enterprise.context.ApplicationScoped";
        private static final String fullyQualifiedStateless = "javax.ejb.Stateless";
        private static final String fullyQualifiedImportRequestScoped = "javax.enterprise.context.RequestScoped";
        private static final String fullyQualifiedStateful = "javax.ejb.Stateful";

        private static final String fullyQualifiedClustered = "org.jboss.ejb3.annotation.Clustered";

        public static final String fullyQualifiedTransactionConfiguration = "io.quarkus.narayana.jta.runtime.TransactionConfiguration";

        public static final String fullyQualifiedTransactionTimeout = "org.jboss.ejb3.annotation.TransactionTimeout";

        public static final String fullyQualifiedTransactionAttribute = "javax.ejb.TransactionAttribute";

        public static final String fullyQualifiedTransactionAttributeType = "javax.ejb.TransactionAttributeType";

        public static final String fullyQualifiedTransactional = "javax.transaction.Transactional";

        private static final String fullyQualifiedImportEjbHttpEndpoint = "org.refactor.util.httprpc.EjbHttpEndpoint";


        private List<String> annotations = Arrays.asList("ApplicationScoped", "EjbHttpEndpoint", "RequestScoped", "TransactionConfiguration", "Transactional", "Inject");

        private final JavaTemplate applicationScopedAnnotationTemplate = JavaTemplate.builder("@ApplicationScoped").contextSensitive()
                .javaParser(JavaParser.fromJavaVersion().dependsOn("package javax.enterprise.context; public @interface ApplicationScoped {}"))
                .imports(fullyQualifiedImportApplicationScoped, fullyQualifiedClustered).build();

        private final JavaTemplate ejbHttpEndpointAnnotationTemplateForRemote = JavaTemplate.builder("@EjbHttpEndpoint")
                .javaParser(JavaParser.fromJavaVersion().dependsOn("package org.refactor.eap6.util.httprpc; public @interface EjbHttpEndpoint {}"))
                .imports(fullyQualifiedImportEjbHttpEndpoint).build();

        private final JavaTemplate requestScopedAnnotationTemplate = JavaTemplate.builder("@RequestScoped")
                .javaParser(JavaParser.fromJavaVersion().dependsOn("package javax.enterprise.context; public @interface RequestScoped {}"))
                .imports(fullyQualifiedImportRequestScoped, fullyQualifiedClustered).build();

        private final JavaTemplate transactionConfigurationAnnotationTemplate = JavaTemplate.builder("@TransactionConfiguration(timeout = #{})")
                .javaParser(JavaParser.fromJavaVersion().dependsOn("package io.quarkus.narayana.jta.runtime; public @interface TransactionConfiguration {}"))
                .imports(fullyQualifiedTransactionConfiguration).build();

        private final JavaTemplate transactionAttributeAnnotationTemplate = JavaTemplate.builder("@Transactional(Transactional.TxType.#{})")
                .javaParser(JavaParser.fromJavaVersion().dependsOn("package javax.transaction; public @interface Transactional {}"))
                .imports(fullyQualifiedTransactional).build();

        private Object timeoutValue = 60;

        private Object transactionAttributeType = "REQUIRED";

        private Scanned scan;

        public ReplaceStatelessVisitor(Scanned scanned) {
            this.scan = scanned;
        }

        private boolean isRemoteImplementation(J.ClassDeclaration classDecl) {
            for (String fullQualifiedInterface : scan.fullyQualifiedRemoteInterfaceList) {
                if (!TypeUtils.isOfClassType(classDecl.getType(), fullQualifiedInterface) &&
                        TypeUtils.isAssignableTo(fullQualifiedInterface, classDecl.getType())) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext executionContext) {
            J.ClassDeclaration classDeclaration = super.visitClassDeclaration(classDecl, executionContext);

            classDeclaration = addApplicationScopedAnnotation(classDeclaration);

            classDeclaration = addRequestScopeAnnotation(classDeclaration);

            classDeclaration = addTransactionTimeoutAnnotation(classDeclaration);

            classDeclaration = addTransactionAttributeAnnotation(classDeclaration);

            //classDeclaration = addEjbHttpEndpointAnnotation(classDeclaration);

            classDeclaration = addDefaultTransactionAttributeAnnotation(classDeclaration);

            doAfterVisit(new TimeoutAnnotationMethodVisitor());
            doAfterVisit(new EJBAnnotationVariableVisitor());
            return classDeclaration;
        }

        @NotNull
        private J.ClassDeclaration addApplicationScopedAnnotation(J.ClassDeclaration classDeclaration) {
            if (classDeclaration.getAllAnnotations().stream().noneMatch(annotation -> annotation.getSimpleName().equals("ApplicationScoped"))
                    && classDeclaration.getAllAnnotations().stream().anyMatch(annotation -> annotation.getSimpleName().equals("Stateless"))) {

                List<J.Annotation> annotationList = new ArrayList<>();
                List<J.Annotation> allAnnotations = classDeclaration.getAllAnnotations();
                for (J.Annotation annotation : allAnnotations) {
                    if (!annotation.getSimpleName().equals("Stateless")) {
                        if (!annotation.getSimpleName().equals("Clustered") && !annotation.getSimpleName().equals("WebContext")) {
                            annotationList.add(annotation);
                        }
                    } else {
                        maybeRemoveImport(fullyQualifiedStateless);
                        maybeRemoveImport(fullyQualifiedClustered);
                        maybeAddImport(fullyQualifiedImportApplicationScoped, false);
                        classDeclaration = applicationScopedAnnotationTemplate.apply(getCursor(), classDeclaration.getCoordinates().addAnnotation(Comparator.comparing(J.Annotation::getSimpleName)));
                    }
                }
                Optional<J.Annotation> injectAnnotation = classDeclaration.getAllAnnotations().stream().filter(annotation -> annotation.getSimpleName().equals("ApplicationScoped")).findFirst();
                injectAnnotation.ifPresent(annotation -> annotationList.add(addPrefix(annotation)));
                classDeclaration = classDeclaration.withLeadingAnnotations(annotationList);
            }
            return classDeclaration;
        }

        @NotNull
        private J.ClassDeclaration addDefaultTransactionAttributeAnnotation(J.ClassDeclaration classDeclaration) {
            if (classDeclaration.getAllAnnotations().stream().noneMatch(annotation -> annotation.getSimpleName().equals("Transactional"))
                    && (classDeclaration.getAllAnnotations().stream().anyMatch(annotation -> annotation.getSimpleName().equals("ApplicationScoped")) || classDeclaration.getAllAnnotations().stream().anyMatch(annotation -> annotation.getSimpleName().equals("RequestScoped")))) {

                List<J.Annotation> annotationList = new ArrayList<>();
                List<J.Annotation> allAnnotations = classDeclaration.getAllAnnotations();
                for (J.Annotation annotation : allAnnotations) {
                    if (!annotation.getSimpleName().equals("Transactional")) {
                        if (!annotation.getSimpleName().equals("Clustered")) {
                            annotationList.add(annotation);
                        }
                    }
                }
                classDeclaration = transactionAttributeAnnotationTemplate.apply(getCursor(), classDeclaration.getCoordinates().addAnnotation(Comparator.comparing(J.Annotation::getSimpleName)), "REQUIRED");
                maybeAddImport(fullyQualifiedTransactional);
                Optional<J.Annotation> injectAnnotation = classDeclaration.getAllAnnotations().stream().filter(annotation -> annotation.getSimpleName().equals("Transactional")).findFirst();
                injectAnnotation.ifPresent(annotation -> annotationList.add(addPrefix(annotation)));
                classDeclaration = classDeclaration.withLeadingAnnotations(annotationList);

            }
            return classDeclaration;
        }

        @NotNull
        private J.ClassDeclaration addRequestScopeAnnotation(J.ClassDeclaration classDeclaration) {
            if (classDeclaration.getAllAnnotations().stream().noneMatch(annotation -> annotation.getSimpleName().equals("RequestScoped"))
                    && classDeclaration.getAllAnnotations().stream().anyMatch(annotation -> annotation.getSimpleName().equals("Stateful"))) {

                List<J.Annotation> annotationList = new ArrayList<>();
                List<J.Annotation> allAnnotations = classDeclaration.getAllAnnotations();
                for (J.Annotation annotation : allAnnotations) {
                    if (!annotation.getSimpleName().equals("Stateful")) {
                        if (!annotation.getSimpleName().equals("Clustered")) {
                            annotationList.add(annotation);
                        }
                    } else {
                        maybeRemoveImport(fullyQualifiedStateful);
                        maybeRemoveImport(fullyQualifiedClustered);
                        maybeAddImport(fullyQualifiedImportRequestScoped, false);
                        classDeclaration = requestScopedAnnotationTemplate.apply(getCursor(), classDeclaration.getCoordinates().addAnnotation(Comparator.comparing(J.Annotation::getSimpleName)));
                    }
                }
                Optional<J.Annotation> injectAnnotation = classDeclaration.getAllAnnotations().stream().filter(annotation -> annotation.getSimpleName().equals("RequestScoped")).findFirst();
                injectAnnotation.ifPresent(annotation -> annotationList.add(addPrefix(annotation)));
                classDeclaration = classDeclaration.withLeadingAnnotations(annotationList);
            }
            return classDeclaration;
        }

        @NotNull
        private J.ClassDeclaration addTransactionTimeoutAnnotation(J.ClassDeclaration classDeclaration) {
            //Gestion du TransactionTimeout
            if (classDeclaration.getAllAnnotations().stream().noneMatch(annotation -> annotation.getSimpleName().equals("TransactionConfiguration"))
                    && classDeclaration.getAllAnnotations().stream().anyMatch(annotation -> annotation.getSimpleName().equals("TransactionTimeout"))) {

                List<J.Annotation> annotationList = new ArrayList<>();
                List<J.Annotation> allAnnotations = classDeclaration.getAllAnnotations();

                for (J.Annotation annotation : allAnnotations) {
                    if (!annotation.getSimpleName().equals("TransactionTimeout")) {
                        annotationList.add(annotation);
                    } else {
                        maybeRemoveImport(fullyQualifiedTransactionTimeout);
                        maybeAddImport(fullyQualifiedTransactionConfiguration, false);
                        if (annotation.getArguments() != null && !annotation.getArguments().isEmpty()) {
                            annotation.getArguments().forEach(expression -> {
                                if (expression instanceof J.Assignment) {
                                    J.Assignment as = (J.Assignment) expression;
                                    J.Identifier var = (J.Identifier) as.getVariable();
                                    if ("value".equals(var.getSimpleName())) {
                                        J.Literal value = (J.Literal) as.getAssignment();
                                        timeoutValue = value.getValue();
                                    }
                                }
                                if (expression instanceof J.Literal) {
                                    J.Literal as = (J.Literal) expression;
                                    timeoutValue = as.getValue();
                                }
                            });
                        }
                        classDeclaration = transactionConfigurationAnnotationTemplate.apply(getCursor(), classDeclaration.getCoordinates().addAnnotation(Comparator.comparing(J.Annotation::getSimpleName)), timeoutValue);
                    }
                }
                Optional<J.Annotation> transactionConfigurationAnnotation = classDeclaration.getAllAnnotations().stream().filter(annotation -> annotation.getSimpleName().equals("TransactionConfiguration")).findFirst();
                transactionConfigurationAnnotation.ifPresent(annotation -> annotationList.add(addPrefix(annotation)));
                classDeclaration = classDeclaration.withLeadingAnnotations(annotationList);
            }
            return classDeclaration;
        }

        @NotNull
        private J.ClassDeclaration addTransactionAttributeAnnotation(J.ClassDeclaration classDeclaration) {
            //Gestion du TransactionAttribute
            if (classDeclaration.getAllAnnotations().stream().noneMatch(annotation -> annotation.getSimpleName().equals("Transactional"))
                    && classDeclaration.getAllAnnotations().stream().anyMatch(annotation -> annotation.getSimpleName().equals("TransactionAttribute"))) {

                List<J.Annotation> annotationList = new ArrayList<>();
                List<J.Annotation> allAnnotations = classDeclaration.getAllAnnotations();

                for (J.Annotation annotation : allAnnotations) {
                    if (!annotation.getSimpleName().equals("TransactionAttribute")) {
                        annotationList.add(annotation);
                    } else {
                        maybeRemoveImport(fullyQualifiedTransactionAttribute);
                        maybeRemoveImport(fullyQualifiedTransactionAttributeType);
                        maybeAddImport(fullyQualifiedTransactional, false);
                        if (annotation.getArguments() != null && !annotation.getArguments().isEmpty()) {
                            annotation.getArguments().forEach(expression -> {
                                if (expression instanceof J.FieldAccess) {
                                    J.FieldAccess as = (J.FieldAccess) expression;
                                    J.Identifier var = as.getName();
                                    transactionAttributeType = var.getSimpleName();
                                } else if (expression instanceof J.Identifier) {
                                    J.Identifier var = (J.Identifier) expression;
                                    transactionAttributeType = var.getSimpleName();
                                }
                            });
                        }
                        classDeclaration = transactionAttributeAnnotationTemplate.apply(getCursor(), classDeclaration.getCoordinates().addAnnotation(Comparator.comparing(J.Annotation::getSimpleName)), transactionAttributeType);
                    }
                }

                Optional<J.Annotation> transactionConfigurationAnnotation = classDeclaration.getAllAnnotations().stream().filter(annotation -> annotation.getSimpleName().equals("Transactional")).findFirst();
                transactionConfigurationAnnotation.ifPresent(annotation -> annotationList.add(addPrefix(annotation)));
                classDeclaration = classDeclaration.withLeadingAnnotations(annotationList);
            }
            return classDeclaration;
        }

        @NotNull
        private J.ClassDeclaration addEjbHttpEndpointAnnotation(J.ClassDeclaration classDeclaration) {
            if (classDeclaration.getAllAnnotations().stream().noneMatch(annotation -> annotation.getSimpleName().equals("EjbHttpEndpoint"))
                    && (classDeclaration.getAllAnnotations().stream().anyMatch(annotation -> annotation.getSimpleName().equals("ApplicationScoped"))
                    || classDeclaration.getAllAnnotations().stream().anyMatch(annotation -> annotation.getSimpleName().equals("RequestScoped")))) {
                if (isRemoteImplementation(classDeclaration)) {
                    LOG.info("FindRemoteImplementation : {}", classDeclaration.getType().getFullyQualifiedName());

                    List<J.Annotation> allAnnotations = classDeclaration.getAllAnnotations();

                    maybeAddImport(fullyQualifiedImportEjbHttpEndpoint, false);
                    classDeclaration = ejbHttpEndpointAnnotationTemplateForRemote.apply(getCursor(), classDeclaration.getCoordinates().addAnnotation(Comparator.comparing(J.Annotation::getSimpleName)));

                    Optional<J.Annotation> ejbhttpConfigurationAnnotation = classDeclaration.getAllAnnotations().stream().filter(annotation -> annotation.getSimpleName().equals("EjbHttpEndpoint")).findFirst();
                    ejbhttpConfigurationAnnotation.ifPresent(annotation -> allAnnotations.add(addPrefix(annotation)));
                    classDeclaration = classDeclaration.withLeadingAnnotations(allAnnotations);
                }
            }
            return classDeclaration;
        }

        /**
         * @param annotation
         * @return
         */
        private J.Annotation addPrefix(J.Annotation annotation) {
            Space prefix = annotation.getPrefix();
            if (annotations.stream().noneMatch(s -> s.equalsIgnoreCase(annotation.getSimpleName()))) {
                return annotation;
            } else {
                if (!prefix.getWhitespace().equals(NEW_LINE)) {
                    Space space = prefix.withWhitespace(NEW_LINE);
                    return annotation.withPrefix(space);
                } else {
                    return annotation;
                }
            }
        }
    }


    @Value
    @EqualsAndHashCode(callSuper = true)
    private static class FindRemoteInterface extends JavaIsoVisitor<AtomicBoolean> {

        J.ClassDeclaration classDeclaration;

        static AtomicBoolean find(J j, J.ClassDeclaration classDeclaration) {
            return new FindRemoteInterface(classDeclaration)
                    .reduce(j, new AtomicBoolean());
        }

        @Override
        public J.Annotation visitAnnotation(J.Annotation annotation, AtomicBoolean hasAnnotation) {
            if (hasAnnotation.get()) {
                return annotation;
            }

            J.Annotation anno = super.visitAnnotation(annotation, hasAnnotation);
            if (anno.getSimpleName().equals("Remote")) {
                LOG.info("FindRemoteInterface : {}", classDeclaration.getType().getFullyQualifiedName());
                hasAnnotation.set(true);
            }
            return anno;
        }
    }
}
