package org.refactor.eap6.yaml;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.ExecutionContext;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.properties.PropertiesVisitor;
import org.openrewrite.properties.tree.Properties;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.tree.Yaml;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Value
@EqualsAndHashCode(callSuper = false)
public class PopulateYamlFile extends ScanningRecipe<PopulateYamlFile.Scanned> {

    private static final Logger LOG = LoggerFactory.getLogger(PopulateYamlFile.class);

    @JsonCreator
    public PopulateYamlFile() {
    }

    @Override
    public String getDisplayName() {
        return "PopulateYamlFile";
    }

    @Override
    public String getDescription() {
        return "PopulateYamlFile.";
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

        return new PropertiesVisitor<ExecutionContext>() {

            @Override
            public Properties visitFile(Properties.File file, ExecutionContext executionContext) {
                Properties properties = super.visitFile(file, executionContext);
//                DeleteSourceFiles deleteSourceFiles = new DeleteSourceFiles(file.getSourcePath().toFile().getName());
//                doAfterVisit(deleteSourceFiles.getVisitor());
                return properties;
            }

            @Override
            public Properties visitEntry(Properties.Entry entry, ExecutionContext executionContext) {
                Properties.Entry e = (Properties.Entry) super.visitEntry(entry, executionContext);
                acc.propertiesMap.put(e.getKey(), e.getValue().getText());
                return e;
            }

        };
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Scanned scanned) {
        return new PopulateYamlFileVisitor(scanned);
    }

    public static class PopulateYamlFileVisitor extends YamlIsoVisitor<ExecutionContext> {

        private final Scanned scan;

        public PopulateYamlFileVisitor(Scanned scanned) {
            this.scan = scanned;
        }

        @Override
        public Yaml.Document visitDocument(Yaml.Document document, ExecutionContext executionContext) {
            Yaml.Document document1 = super.visitDocument(document, executionContext);
            return document1;
        }

    }
}
