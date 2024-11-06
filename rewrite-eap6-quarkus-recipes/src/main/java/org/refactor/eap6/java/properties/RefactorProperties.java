package org.refactor.eap6.java.properties;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.refactor.eap6.util.RewriteUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.VariableNameUtils;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Space;
import org.openrewrite.marker.Markers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.AbstractMap;
import java.util.List;

import static org.openrewrite.Tree.randomId;

@Value
@EqualsAndHashCode(callSuper = false)
public class RefactorProperties extends Recipe {

    private static final Logger LOG = LoggerFactory.getLogger(RefactorProperties.class);


    private static final String PROPS_UTIL_GET_PROPERTY = "PropsUtil.getProperty";


    @Override
    public String getDisplayName() {
        return "Refactor PropsUtil";
    }

    @Override
    public String getDescription() {
        return "Replace PropsUtil with ConfigProperty.";
    }


    public RefactorProperties() {
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(10);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new ReplacePropertiesVisitor();
    }

    public static class ReplacePropertiesVisitor extends JavaVisitor<ExecutionContext> {

        private final JavaTemplate templateGetProperty = JavaTemplate.builder("#{}").build();

        private final MethodMatcher getPropertyMatcher = new MethodMatcher("org.fin.util.PropsUtil getProperty(..)");

        @Override
        public J visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext executionContext) {
            J.VariableDeclarations variableDeclarations = (J.VariableDeclarations) super.visitVariableDeclarations(multiVariable, executionContext);

            for (J.VariableDeclarations.NamedVariable namedVariable : variableDeclarations.getVariables()) {
                if (isStaticContext()) {
                    continue;
                }

                Expression initializer = namedVariable.getInitializer();
                if (initializer instanceof J.MethodInvocation) {
                    AbstractMap.SimpleEntry<JavaType.Primitive, Boolean> typeToUse = getJavaType(initializer);
                    List<Expression> arguments = ((J.MethodInvocation) initializer).getArguments();
                    if (!arguments.isEmpty() && typeToUse != null) {
                        if (arguments.size() == 2) {
                            VarDefinition varDefinition = new VarDefinition(namedVariable.getName(), arguments.get(0), arguments.get(1), typeToUse);
                            doAfterVisit(new AddPropertyVar(varDefinition, getCursor().firstEnclosing(J.ClassDeclaration.class).getSimpleName()));
                            return null;
                        } else if (arguments.size() == 1) {
                            VarDefinition varDefinition = new VarDefinition(namedVariable.getName(), arguments.get(0), null, typeToUse);
                            doAfterVisit(new AddPropertyVar(varDefinition, getCursor().firstEnclosing(J.ClassDeclaration.class).getSimpleName()));
                            return null;
                        }
                    }
                }
            }
            return variableDeclarations;
        }

        @Override
        public J visitReturn(J.Return _return, ExecutionContext executionContext) {
            J.Return aReturn = (J.Return) super.visitReturn(_return, executionContext);

            if (isStaticContext()) {
                return aReturn;
            }

            Expression expression = aReturn.getExpression();
            if (getPropertyMatcher.matches(expression)) {
                if (expression instanceof J.MethodInvocation) {
                    J.MethodInvocation meth = (J.MethodInvocation) expression;

                    LOG.info("found " + meth.getSimpleName() + " in " + getCursor().firstEnclosing(J.ClassDeclaration.class).getSimpleName());
                    Expression propertyName = ((J.MethodInvocation) expression).getArguments().get(0);
                    Expression propertyDefaultValue = null;
                    if (((J.MethodInvocation) expression).getArguments().size() > 1) {
                        propertyDefaultValue = ((J.MethodInvocation) expression).getArguments().get(1);
                    }
                    String baseVarName = null;
                    if (propertyName instanceof J.Identifier) {
                        baseVarName = ((J.Identifier) propertyName).getSimpleName();
                    } else if (propertyName instanceof J.FieldAccess) {
                        baseVarName = ((J.FieldAccess) propertyName).getName().getSimpleName();
                    } else if (propertyName instanceof J.Literal) {
                        baseVarName = (String) ((J.Literal) propertyName).getValue();
                    }
                    if (baseVarName != null) {
                        String varName = RewriteUtils.getCamelCaseVariable(VariableNameUtils.generateVariableName(RewriteUtils.normalizeVariable(baseVarName), getCursor(), VariableNameUtils.GenerationStrategy.INCREMENT_NUMBER));
                        aReturn = templateGetProperty.apply(getCursor(), expression.getCoordinates().replace(), varName);
                        VarDefinition varDefinition = new VarDefinition(getIdentifier(Space.EMPTY, varName), propertyName, propertyDefaultValue, new AbstractMap.SimpleEntry<>(JavaType.Primitive.String, false));
                        doAfterVisit(new AddPropertyVar(varDefinition, getCursor().firstEnclosing(J.ClassDeclaration.class).getSimpleName()));
                    }
                }
            }
            return aReturn;
        }

        private boolean isStaticContext() {
            J.MethodDeclaration methodDeclaration = getCursor().firstEnclosing(J.MethodDeclaration.class);
            J.VariableDeclarations variableDeclarations = getCursor().firstEnclosing(J.VariableDeclarations.class);
            List<J.Modifier> modifiers = null;
            if (methodDeclaration != null) {
                modifiers = methodDeclaration.getModifiers();
            } else if (variableDeclarations != null) {
                modifiers = variableDeclarations.getModifiers();
            }
            return (modifiers != null && modifiers.stream().anyMatch(modifier -> "static".equals(modifier.toString())));
        }

        @NotNull
        private J.Identifier getIdentifier(Space space, String varname) {
            return new J.Identifier(randomId(), space, Markers.EMPTY, varname, JavaType.Primitive.String, null);
        }

        @Nullable
        private static AbstractMap.SimpleEntry<JavaType.Primitive, Boolean> getJavaType(Expression expression) {
            AbstractMap.SimpleEntry<JavaType.Primitive, Boolean> typeToUse = null;
            if (expression.toString().startsWith(PROPS_UTIL_GET_PROPERTY)) {
                typeToUse = new AbstractMap.SimpleEntry<>(JavaType.Primitive.String, false);
            }
            return typeToUse;
        }
    }

    static class VarDefinition {

        VarDefinition(J.Identifier varName, Expression expression, Expression expressionDefault, AbstractMap.SimpleEntry<JavaType.Primitive, Boolean> typeVar) {
            this.varName = varName;
            this.expression = expression;
            this.expressionDefault = expressionDefault;
            this.typeVar = typeVar;
        }

        J.Identifier varName;

        AbstractMap.SimpleEntry<JavaType.Primitive, Boolean> typeVar;

        Expression expression;

        Expression expressionDefault;
    }
}
