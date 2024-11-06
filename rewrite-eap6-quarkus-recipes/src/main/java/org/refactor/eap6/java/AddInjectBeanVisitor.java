package org.refactor.eap6.java;

import org.refactor.eap6.pojo.Bean;
import org.openrewrite.ExecutionContext;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeTree;
import org.openrewrite.java.tree.TypeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class AddInjectBeanVisitor extends JavaIsoVisitor<ExecutionContext> {

    private static final Logger LOG = LoggerFactory.getLogger(AddInjectBeanVisitor.class);

    private static final String ADD_INJECT_KEY = "ADD_INJECT";
    private final String implementationClassName;

    private final static String fullyQualifiedInject = "jakarta.inject.Inject";

    JavaTemplate injectBean = JavaTemplate.builder("@Inject #{} #{};").build();

    public AddInjectBeanVisitor(String implementationClassName) {
        this.implementationClassName = implementationClassName;
    }

    @Override
    public Statement visitStatement(Statement statement, ExecutionContext executionContext) {
        Statement stat = super.visitStatement(statement, executionContext);
        if (stat instanceof J.VariableDeclarations) {
            TypeTree typeExpression = ((J.VariableDeclarations) stat).getTypeExpression();
            if (typeExpression != null && TypeUtils.isOfClassType(typeExpression.getType(), implementationClassName)) {
                if (typeExpression instanceof J.Identifier) {
                    String typeSimpleName = ((J.Identifier) typeExpression).getSimpleName();
                    List<J.VariableDeclarations.NamedVariable> variables = ((J.VariableDeclarations) stat).getVariables();
                    if (variables.size() == 1) {
                        J.VariableDeclarations.NamedVariable namedVariable = variables.get(0);
                        if (namedVariable != null) {
                            executionContext.putMessage(ADD_INJECT_KEY, new Bean(typeSimpleName, implementationClassName, namedVariable.getSimpleName()));
                        }
                    }
                }
            }
        }
        return stat;
    }

    @Override
    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext executionContext) {
        J.ClassDeclaration classDeclaration = super.visitClassDeclaration(classDecl, executionContext);
        Bean beanToInject = executionContext.getMessage(ADD_INJECT_KEY);
        if (beanToInject != null) {
            classDeclaration = injectBean.apply(getCursor(), classDeclaration.getBody().getCoordinates().firstStatement(), beanToInject.getTypeSimpleName(), beanToInject.getVarname());
            maybeAddImport(fullyQualifiedInject, false);
            doAfterVisit(new RemoveNewInstanceBeanVisitor(beanToInject.getTypeFullyQualified()));
        }
        return classDeclaration;
    }
}
