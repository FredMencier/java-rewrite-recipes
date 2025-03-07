package org.refactor.eap6.java.ejb;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

@Value
@EqualsAndHashCode(callSuper = true)
public class FindRemoteInterfaceVisitor extends JavaIsoVisitor<AtomicBoolean> {

    private static final Logger LOG = LoggerFactory.getLogger(FindRemoteInterfaceVisitor.class);

    J.ClassDeclaration.ClassDeclaration classDeclaration;

    static AtomicBoolean find(J j, J.ClassDeclaration.ClassDeclaration classDeclaration) {
        return new FindRemoteInterfaceVisitor(classDeclaration)
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
