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

import static org.refactor.eap6.java.ejb.RefactorSessionBean.ReplaceStatelessVisitor.*;

public class TimeoutAnnotationMethodVisitor extends JavaIsoVisitor<ExecutionContext> {

    private final JavaTemplate transactionConfigurationAnnotationTemplate = JavaTemplate.builder("@TransactionConfiguration(timeout = #{})")
            .javaParser(JavaParser.fromJavaVersion().dependsOn("package io.quarkus.narayana.jta.runtime; public @interface TransactionConfiguration {}"))
            .imports(fullyQualifiedTransactionConfiguration).build();

    private final JavaTemplate transactionAttributeAnnotationTemplate = JavaTemplate.builder("@Transactional(Transactional.TxType.#{})")
            .javaParser(JavaParser.fromJavaVersion().dependsOn("package javax.transaction; public @interface Transactional {}"))
            .imports(fullyQualifiedTransactional).build();


    private Object timeoutValue = 60;

    private Object transactionAttributeType = "REQUIRED";

    @Override
    public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext executionContext) {
        //LOG.info(TreeVisitingPrinter.printTree(getCursor()));
        J.MethodDeclaration methodDeclaration = super.visitMethodDeclaration(method, executionContext);

        //Gestion du TransactionTimeout
        if (methodDeclaration.getAllAnnotations().stream().noneMatch(annotation -> annotation.getSimpleName().equals("TransactionConfiguration"))
                && methodDeclaration.getAllAnnotations().stream().anyMatch(annotation -> annotation.getSimpleName().equals("TransactionTimeout"))) {

            List<J.Annotation> annotationList = new ArrayList<>();
            List<J.Annotation> allAnnotations = methodDeclaration.getAllAnnotations();

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
                    methodDeclaration = transactionConfigurationAnnotationTemplate.apply(getCursor(), methodDeclaration.getCoordinates().addAnnotation(Comparator.comparing(J.Annotation::getSimpleName)), timeoutValue);
                }
            }
            Optional<J.Annotation> transactionConfigurationAnnotation = methodDeclaration.getAllAnnotations().stream().filter(annotation -> annotation.getSimpleName().equals("TransactionConfiguration")).findFirst();
            transactionConfigurationAnnotation.ifPresent(annotationList::add);
            methodDeclaration = methodDeclaration.withLeadingAnnotations(annotationList);
        }


        //Gestion du TransactionAttribute
        if (methodDeclaration.getAllAnnotations().stream().noneMatch(annotation -> annotation.getSimpleName().equals("Transactional"))
                && methodDeclaration.getAllAnnotations().stream().anyMatch(annotation -> annotation.getSimpleName().equals("TransactionAttribute"))) {

            List<J.Annotation> annotationList = new ArrayList<>();
            List<J.Annotation> allAnnotations = methodDeclaration.getAllAnnotations();

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
                    methodDeclaration = transactionAttributeAnnotationTemplate.apply(getCursor(), methodDeclaration.getCoordinates().addAnnotation(Comparator.comparing(J.Annotation::getSimpleName)), transactionAttributeType);
                }
            }
            Optional<J.Annotation> transactionConfigurationAnnotation = methodDeclaration.getAllAnnotations().stream().filter(annotation -> annotation.getSimpleName().equals("Transactional")).findFirst();
            transactionConfigurationAnnotation.ifPresent(annotationList::add);
            methodDeclaration = methodDeclaration.withLeadingAnnotations(annotationList);
        }

        return methodDeclaration;
    }
}
