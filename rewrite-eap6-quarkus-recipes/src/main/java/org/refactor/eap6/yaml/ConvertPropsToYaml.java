package org.refactor.eap6.yaml;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.EqualsAndHashCode;
import lombok.Value;
import org.refactor.eap6.yaml.propstoyaml.Props2YAML;
import org.openrewrite.*;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.yaml.MergeYamlVisitor;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.tree.Yaml;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Value
@EqualsAndHashCode(callSuper = false)
public class ConvertPropsToYaml extends ScanningRecipe<ConvertPropsToYaml.Scanned> {

    private static final Logger LOG = LoggerFactory.getLogger(ConvertPropsToYaml.class);

    @JsonCreator
    public ConvertPropsToYaml() {
    }

    @Override
    public String getDisplayName() {
        return "ConvertPropsToYaml";
    }

    @Override
    public String getDescription() {
        return "ConvertPropsToYaml.";
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(10);
    }

    static class Scanned {
        private final Map<String, String> propertiesMap = new HashMap<>();
    }

    @Override
    public Scanned getInitialValue(ExecutionContext ctx) {
        return new Scanned();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Scanned acc) {

        return new TreeVisitor<Tree, ExecutionContext>() {

            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext executionContext) {
                if (tree instanceof SourceFile) {
                    SourceFile sourceFile = (SourceFile) tree;
                    Path sourcePath = sourceFile.getSourcePath();
                    PathMatcher pathMatcher = sourcePath.getFileSystem().getPathMatcher("regex:.*.properties");
                    if (pathMatcher.matches(sourcePath)) {
                        Props2YAML props2YAML = Props2YAML.fromFile(sourcePath.toFile());
                        String yaml = props2YAML.convert();
                        acc.propertiesMap.put(sourcePath.toString(), yaml);
                        return tree;
                    }
                }
                return tree;
            }
        };
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Scanned scanned) {
        return Preconditions.check(new FindSourceFiles("**/application*.yaml"), new ConvertPropsToYamlVisitor(scanned));
    }

    public static class ConvertPropsToYamlVisitor extends YamlIsoVisitor<ExecutionContext> {

        private final Scanned scan;

        public ConvertPropsToYamlVisitor(Scanned scanned) {
            this.scan = scanned;
        }

        @Override
        public Yaml.Document visitDocument(Yaml.Document document, ExecutionContext executionContext) {
            Yaml.Document document1 = super.visitDocument(document, executionContext);
            Set<Map.Entry<String, String>> entries = scan.propertiesMap.entrySet();
            StringBuilder stringBuilder = new StringBuilder();
            for(Map.Entry<String, String> entry : entries) {
                stringBuilder.append(entry.getValue());
            }
            return document1.withBlock((Yaml.Block) new MergeYamlVisitor<>(document1.getBlock(), stringBuilder.toString(), Boolean.FALSE, null).visit(document1.getBlock(), executionContext, getCursor()));
        }

    }
}
