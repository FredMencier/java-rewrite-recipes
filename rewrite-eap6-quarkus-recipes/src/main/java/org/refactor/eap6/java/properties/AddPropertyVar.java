package org.refactor.eap6.java.properties;

import org.jetbrains.annotations.NotNull;
import org.openrewrite.ExecutionContext;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Statement;

public class AddPropertyVar extends JavaIsoVisitor<ExecutionContext> {

    private final RefactorProperties.VarDefinition varDefinition;

    public static final String fullyQualifiedconfigProperty = "org.eclipse.microprofile.config.inject.ConfigProperty";

    public static final String fullyQualifiedPropsUtil = "org.fin.util.PropsUtil";

    public static final String fullyQualifiedOptional = "java.util.Optional";

    public static final String CONFIG_PROPERTY_NAME_DEFAULT = "@ConfigProperty(name = #{}, defaultValue = #{}) %s #{};";

    private static final String CONFIG_PROPERTY_NAME_NODEFAULT = "@ConfigProperty(name = #{}) %s #{};";

    public static final String CONFIG_PROPERTY_NAME_OPTIONAL_NODEFAULT = "@ConfigProperty(name = #{}) Optional<%s> #{};";

    private final String classname;

    public AddPropertyVar(RefactorProperties.VarDefinition varDefinition, String classname) {
        this.varDefinition = varDefinition;
        this.classname = classname;
    }

    @Override
    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext executionContext) {
        J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, executionContext);
        if (!classname.equals(classDecl.getSimpleName())) {
            return cd;
        }

        boolean addConfigProperty = false;

        for (Statement statement : cd.getBody().getStatements()) {
            if (statement instanceof J.VariableDeclarations) {
                J.VariableDeclarations vd = (J.VariableDeclarations) statement;
                addConfigProperty = !vd.getVariables().isEmpty() && vd.getVariables().get(0).getSimpleName().equals(varDefinition.varName.getSimpleName());
                if (addConfigProperty) break;
            }
        }

        if (!addConfigProperty) {
            maybeAddImport(fullyQualifiedconfigProperty, false);
//            maybeAddImport(fullyQualifiedOptional, false);
            maybeRemoveImport(fullyQualifiedPropsUtil);
            cd = getClassPropertyDeclaration(cd);
        }
        return cd;
    }

    @NotNull
    private J.ClassDeclaration getClassPropertyDeclaration(J.ClassDeclaration cd) {
        String exp;
        if (varDefinition.expression instanceof J.Literal) {
            exp = ((J.Literal) varDefinition.expression).getValueSource();
        } else {
            exp = varDefinition.expression.toString();
        }

        if (varDefinition.expressionDefault == null) {
            //cd = getClassDeclaration(CONFIG_PROPERTY_NAME_OPTIONAL_NODEFAULT, cd, exp, null);
            cd = getClassDeclaration(CONFIG_PROPERTY_NAME_NODEFAULT, cd, exp, null);
        } else {
            if (varDefinition.expressionDefault instanceof J.Literal) {
                J.Literal expDefaultLit = (J.Literal) varDefinition.expressionDefault;
                if (JavaType.Primitive.Boolean.equals(varDefinition.expressionDefault.getType())) {
                    if ((Boolean) expDefaultLit.getValue()) {
                        cd = getClassDeclaration(CONFIG_PROPERTY_NAME_NODEFAULT, cd, exp, null);
                    } else {
                        cd = getClassDeclaration(CONFIG_PROPERTY_NAME_OPTIONAL_NODEFAULT, cd, exp, null);
                    }
                } else {
                    String defaultExp;
                    if (JavaType.Primitive.Int.equals(varDefinition.expressionDefault.getType())
                            || JavaType.Primitive.Long.equals(varDefinition.expressionDefault.getType())
                            || JavaType.Primitive.Float.equals(varDefinition.expressionDefault.getType())
                            || JavaType.Primitive.Double.equals(varDefinition.expressionDefault.getType())
                            || JavaType.Primitive.Short.equals(varDefinition.expressionDefault.getType())) {
                        defaultExp = "\"" + expDefaultLit.getValueSource() + "\"";
                    } else {
                        defaultExp = expDefaultLit.getValueSource();
                    }
                    cd = getClassDeclaration(CONFIG_PROPERTY_NAME_DEFAULT, cd, exp, defaultExp);
                }
            } else if (varDefinition.expressionDefault instanceof J.Identifier) {
                cd = getClassDeclaration(CONFIG_PROPERTY_NAME_DEFAULT, cd, exp, ((J.Identifier) varDefinition.expressionDefault).getSimpleName());
            }
        }
        return cd;
    }

    @NotNull
    private J.ClassDeclaration getClassDeclaration(String configPropertyName, J.ClassDeclaration cd, Object expression, Object expressionDefault) {
        JavaTemplate build = JavaTemplate.builder(String.format(configPropertyName, varDefinition.typeVar.getValue() ? String.format("List<%s>", varDefinition.typeVar.getKey().getClassName()) : varDefinition.typeVar.getKey().getClassName()))
                .javaParser(JavaParser.fromJavaVersion().dependsOn("package org.eclipse.microprofile.config.inject; public @interface ConfigProperty {}"))
                .imports(fullyQualifiedconfigProperty).build();
        if (expressionDefault == null) {
            cd = build.apply(getCursor(), cd.getBody().getCoordinates().firstStatement(), expression, varDefinition.varName.getSimpleName());
        } else {
            cd = build.apply(getCursor(), cd.getBody().getCoordinates().firstStatement(), expression, expressionDefault, varDefinition.varName.getSimpleName());
        }
        return cd;
    }
}
