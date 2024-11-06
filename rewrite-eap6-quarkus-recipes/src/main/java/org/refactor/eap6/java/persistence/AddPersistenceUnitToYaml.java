package org.refactor.eap6.java.persistence;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.EqualsAndHashCode;
import lombok.Value;
import org.refactor.eap6.yaml.ConvertPropsToYaml;
import org.openrewrite.*;
import org.openrewrite.xml.XPathMatcher;
import org.openrewrite.xml.XmlIsoVisitor;
import org.openrewrite.xml.tree.Xml;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.tree.Yaml;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Value
@EqualsAndHashCode(callSuper = false)
public class AddPersistenceUnitToYaml  extends ScanningRecipe<AddPersistenceUnitToYaml.Scanned> {

    private static final Logger LOG = LoggerFactory.getLogger(ConvertPropsToYaml.class);

    @JsonCreator
    public AddPersistenceUnitToYaml() {
    }

    @Override
    public String getDisplayName() {
        return "AddPersistenceUnitToYaml";
    }

    @Override
    public String getDescription() {
        return "AddPersistenceUnitToYaml.";
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(20);
    }

    static class Scanned {
        private final List<String> propertiesList = new ArrayList<>();
    }

    @Override
    public Scanned getInitialValue(ExecutionContext ctx) {
        return new Scanned();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Scanned acc) {

        return new XmlIsoVisitor<>() {

            private final XPathMatcher xPathMatcher = new XPathMatcher("/persistence/persistence-unit");

//            @Override
//            public boolean isAcceptable(SourceFile sourceFile, ExecutionContext executionContext) {
//                return super.isAcceptable(sourceFile, executionContext) && sourceFile.getSourcePath().endsWith("persistence.xml");
//            }

            @Override
            public Xml.Document visitDocument(Xml.Document document, ExecutionContext ctx) {
                Xml.Document doc = super.visitDocument(document, ctx);
                    Xml.Tag root = doc.getRoot();
                    List<Xml.Tag> persistenceUnits = root.getChildren("persistence-unit");
                    for (Xml.Tag persistenceUnit : persistenceUnits) {
                        List<Xml.Attribute> attributes = persistenceUnit.getAttributes();
                        Optional<Xml.Attribute> name = attributes.stream().filter(attribute -> attribute.getKey().getName().equals("name")).findFirst();
                        if (name.isPresent()) {
                            acc.propertiesList.add("quarkus.datasource." + name.get().getValue().getValue());
                        }
                        List<Xml.Tag> properties = persistenceUnit.getChildren("properties");
                        for (Xml.Tag property : properties) {
                            List<Xml.Tag> props = property.getChildren("property");
                            for (Xml.Tag prop: props) {
                                Optional<Xml.Attribute> mysql = prop.getAttributes().stream().filter(attribute -> attribute.getValue().getValue().contains("mysql")).findFirst();
                                if (mysql.isPresent()) {

                                }
                            }
                        }
                    }
                return doc;
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
            return document1;
        }

    }

}
