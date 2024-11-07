package org.refactor.java;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.marker.SearchResult;

import java.time.Duration;

public class CheckJavax extends Recipe {

    @Override
    public String getDisplayName() {
        return "Check if javax.* (except javax.ejb.*) is present in source code";
    }

    @Override
    public String getDescription() {
        return "Check if javax.* (except javax.ejb.*) is present in source code.";
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(10);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<>() {

            public J.Import visitImport(J.Import anImport, ExecutionContext executionContext) {
                if (anImport.getTypeName().startsWith("javax") && !anImport.getTypeName().startsWith("javax.ejb")) {
                    return SearchResult.found(anImport, "//FIXME javax");
                }
                return super.visitImport(anImport, executionContext);
            }
        };
    }
}
