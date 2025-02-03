package org.refactor.maven;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.maven.AddPropertyVisitor;
import org.openrewrite.maven.MavenIsoVisitor;
import org.openrewrite.maven.MavenVisitor;
import org.openrewrite.maven.tree.*;
import org.openrewrite.xml.ChangeTagValueVisitor;
import org.openrewrite.xml.tree.Xml;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class ManageDependenciesVersion extends ScanningRecipe<Map<GroupArtifactVersion, List<ResolvedDependency>>> {

    @Option(displayName = "scope",
            description = "given dependencies scope to apply recipe.",
            required = false)
    @Nullable
    String scope;

    @Override
    public String getDisplayName() {
        return "Manage dependencies";
    }

    @Override
    public String getDescription() {
        return "Make existing dependencies managed by moving their version to be specified in the dependencyManagement section of the POM.";
    }

    @Override
    public Map<GroupArtifactVersion, List<ResolvedDependency>> getInitialValue(ExecutionContext ctx) {
        return new HashMap<>();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Map<GroupArtifactVersion, List<ResolvedDependency>> rootGavToDependencies) {
        return new MavenIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Document visitDocument(Xml.Document document, ExecutionContext ctx) {
                Xml.Document doc = super.visitDocument(document, ctx);
                Map<Scope, List<ResolvedDependency>> dependencies = getResolutionResult().getDependencies();
                if (scope != null && !scope.isEmpty()) {
                    Scope scopeEnum = Scope.fromName(scope);
                    dependencies = Map.of(scopeEnum, dependencies.get(scopeEnum));
                }
                List<ResolvedDependency> dependencyList = dependencies.values().stream().flatMap(List::stream).filter(resolvedDependency -> resolvedDependency.getDepth() == 0).collect(Collectors.toList());
                ResolvedGroupArtifactVersion root = findRootPom(getResolutionResult()).getPom().getGav();
                rootGavToDependencies.computeIfAbsent(new GroupArtifactVersion(root.getGroupId(), root.getArtifactId(), root.getVersion()), v -> new ArrayList<>()).addAll(dependencyList);
                return doc;
            }
        };
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Map<GroupArtifactVersion, List<ResolvedDependency>> rootGavToDependencies) {
        return new MavenVisitor<ExecutionContext>() {
            @Override
            public Xml visitDocument(Xml.Document document, ExecutionContext ctx) {
                Xml maven = super.visitDocument(document, ctx);

                rootGavToDependencies.values().forEach(scopeDependenciesList -> scopeDependenciesList.forEach(resolvedDependency -> {
                    String propertyVersion = String.format("%s.version", resolvedDependency.getArtifactId());
                    doAfterVisit(new ChangeVersionTagVisitor(resolvedDependency.getGroupId(), resolvedDependency.getArtifactId(), String.format("${%s}", propertyVersion)));
                    doAfterVisit(new AddPropertyVisitor(String.format("%s", propertyVersion), resolvedDependency.getVersion(), false));
                }));
                return maven;
            }
        };
    }

    private MavenResolutionResult findRootPom(MavenResolutionResult pom) {
        if (pom.getParent() == null) {
            return pom;
        }
        return findRootPom(pom.getParent());
    }

    private static class ChangeVersionTagVisitor extends MavenIsoVisitor<ExecutionContext> {
        private final String groupPattern;
        private final String artifactPattern;

        private final String newValue;

        public ChangeVersionTagVisitor(String groupPattern, String artifactPattern, String newValue) {
            this.groupPattern = groupPattern;
            this.artifactPattern = artifactPattern;
            this.newValue = newValue;
        }

        @Override
        public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ctx) {
            if (isDependencyTag() && isDependencyTag(groupPattern, artifactPattern)) {
                tag.getChild("version").ifPresent(versionTag -> doAfterVisit(new ChangeTagValueVisitor<>(versionTag, newValue)));
                return tag;
            }
            return super.visitTag(tag, ctx);
        }
    }
}
