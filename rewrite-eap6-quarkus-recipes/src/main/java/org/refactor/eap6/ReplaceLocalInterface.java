package org.refactor.eap6;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Value;
import org.refactor.eap6.java.AddInjectBeanVisitor;
import org.refactor.eap6.pojo.ImplementationClassName;
import org.refactor.eap6.util.FileUtil;
import org.refactor.eap6.util.RewriteUtils;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.lang.NonNull;
import org.openrewrite.java.ChangeFieldName;
import org.openrewrite.java.ChangeFieldType;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.RemoveImplements;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Value
@EqualsAndHashCode(callSuper = false)
public class ReplaceLocalInterface extends Recipe {

    private static final Logger LOG = LoggerFactory.getLogger(ReplaceLocalInterface.class);

    @Override
    public String getDisplayName() {
        return "Replace Local interface with implementation";
    }

    @Override
    public String getDescription() {
        return "Replace Local interface with implementation.";
    }

    @Option(displayName = "localInterface",
            description = "The fully qualified local interface.",
            example = "")
    String localInterface;

    @Option(displayName = "Local Interface Implementation",
            description = "The fully qualified local interface implementation.",
            example = "")
    String localInterfaceImplementation;

    @JsonCreator
    public ReplaceLocalInterface(@NonNull @JsonProperty("localInterface") String localInterface, @JsonProperty("localInterfaceImplementation") String localInterfaceImplementation) {
        this.localInterface = localInterface;
        this.localInterfaceImplementation = localInterfaceImplementation;
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(5);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        Map<String, Set<ImplementationClassName>> res = Collections.singletonMap(localInterface, Collections.singleton(new ImplementationClassName(localInterfaceImplementation, true)));
        return new ReplaceLocalInterfaceVisitor(res);
    }

    public static class ReplaceLocalInterfaceVisitor extends JavaIsoVisitor<ExecutionContext> {

        Map<String, Set<ImplementationClassName>> typeMap;

        public ReplaceLocalInterfaceVisitor(Map<String, Set<ImplementationClassName>> typeMap) {
            this.typeMap = typeMap;
        }

        @Override
        public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext executionContext) {
            J.ClassDeclaration classDeclaration = super.visitClassDeclaration(classDecl, executionContext);

            typeMap.forEach((key, value) -> {
                if (!value.isEmpty()) {
                    value.forEach(implementationClassName -> {
                        if (!TypeUtils.isOfClassType(classDeclaration.getType(), key) &&
                                TypeUtils.isAssignableTo(key, classDeclaration.getType())) {
                            LOG.info("-----> Remove implements {} on class {}", key, classDeclaration.getType().getFullyQualifiedName());
                            FileUtil.addToStatistics("R", "C", Duration.ofMinutes(5));
                            doAfterVisit(new RemoveImplements(key, null).getVisitor());
                        }
                    });
                }
            });

            return classDeclaration;
        }

        @Override
        public J.VariableDeclarations.NamedVariable visitVariable(J.VariableDeclarations.NamedVariable variable, ExecutionContext executionContext) {
            J.VariableDeclarations.NamedVariable namedVariable = super.visitVariable(variable, executionContext);
            JavaType.FullyQualified varType = TypeUtils.asFullyQualified(namedVariable.getType());
            if (varType != null && typeMap.get(varType.getFullyQualifiedName()) != null) {
                Optional<ImplementationClassName> newType = typeMap.get(varType.getFullyQualifiedName()).stream().filter(ImplementationClassName::isMainImplementation).findFirst();
                if (newType.isPresent() && !varType.getFullyQualifiedName().equals(newType.get().getClassname())) {
                    LOG.info("--> change type {} to {} for variable {}", varType.getFullyQualifiedName(), newType, namedVariable.getName());
                    maybeRemoveImport(varType);
                    J.ClassDeclaration enclosingClass = getCursor().firstEnclosing(J.ClassDeclaration.class);
                    doAfterVisit(new ChangeFieldName<>(enclosingClass.getType().getFullyQualifiedName(), namedVariable.getName().getSimpleName(), RewriteUtils.getCamelCaseVariable(newType.get().getClassname())));
                    doAfterVisit(new ChangeFieldType<>(varType, JavaType.ShallowClass.build(newType.get().getClassname())));
                    doAfterVisit(new AddInjectBeanVisitor(newType.get().getClassname()));
                }
            }
            return namedVariable;
        }
    }
}

