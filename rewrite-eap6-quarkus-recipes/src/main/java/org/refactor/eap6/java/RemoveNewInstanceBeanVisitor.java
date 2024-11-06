package org.refactor.eap6.java;

import org.openrewrite.ExecutionContext;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeTree;
import org.openrewrite.java.tree.TypeUtils;

public class RemoveNewInstanceBeanVisitor extends JavaIsoVisitor<ExecutionContext> {

    private final String implementationClassName;

    public RemoveNewInstanceBeanVisitor(String implementationClassName) {
        this.implementationClassName = implementationClassName;
    }

    @Override
    public Statement visitStatement(Statement statement, ExecutionContext executionContext) {
        Statement stat = super.visitStatement(statement, executionContext);
        if (stat instanceof J.VariableDeclarations) {
            TypeTree typeExpression = ((J.VariableDeclarations) stat).getTypeExpression();
            if (typeExpression != null && TypeUtils.isOfClassType(typeExpression.getType(), implementationClassName)) {
                return null;
            }
        }
        return stat;
    }
}
