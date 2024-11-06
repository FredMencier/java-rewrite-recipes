package org.refactor.eap6.maven;

import org.openrewrite.ExecutionContext;
import org.openrewrite.maven.MavenIsoVisitor;
import org.openrewrite.xml.AddToTagVisitor;
import org.openrewrite.xml.XPathMatcher;
import org.openrewrite.xml.tree.Content;
import org.openrewrite.xml.tree.Xml;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

public class AddMavenQuarkusPluginVisitor extends MavenIsoVisitor<ExecutionContext> {

    private static final Logger LOG = LoggerFactory.getLogger(AddMavenQuarkusPluginVisitor.class);

    private static final XPathMatcher BUILD_MATCHER = new XPathMatcher("/project/build");

    private static final XPathMatcher PACKAGING_MATCHER = new XPathMatcher("/project/packaging");

    private static final XPathMatcher MODULE_MATCHER = new XPathMatcher("/project/modules/module");


    public static final String GROUPID_IO_QUARKUS = "io.quarkus";

    public static final String GROUPID_IO_QUARKUS_PLATFORM = "io.quarkus.platform";

    private static final String GROUPID_IO_SMALLRYE = "io.smallrye";

    public static final String ARTIFACTID_QUARKUS_MAVEN_PLUGIN = "quarkus-maven-plugin";

    public static final String ARTIFACTID_JANDEX_MAVEN_PLUGIN = "jandex-maven-plugin";

    private static final String QUARKUS_MAVEN_PLUGIN = "<plugin>\n" +
            "<groupId>" + GROUPID_IO_QUARKUS_PLATFORM + "</groupId>\n" +
            "<artifactId>" + ARTIFACTID_QUARKUS_MAVEN_PLUGIN + "</artifactId>\n" +
            "<version>3.8.2</version>\n" +
            "<extensions>true</extensions><executions><execution><goals><goal>build</goal><goal>generate-code</goal><goal>generate-code-tests</goal></goals></execution></executions>\n" +
            "</plugin>";

    private static final String JANDEX_MAVEN_PLUGIN = "<plugin>\n" +
            "<groupId>" + GROUPID_IO_SMALLRYE + "</groupId>\n" +
            "<artifactId>" + ARTIFACTID_JANDEX_MAVEN_PLUGIN + "</artifactId>\n" +
            "<version>3.0.5</version>\n" +
            "<executions><execution><id>make-index</id><goals><goal>jandex</goal></goals></execution></executions>\n" +
            "</plugin>";

    private ScannedModules scan;


    public AddMavenQuarkusPluginVisitor(ScannedModules scanned) {
        this.scan = scanned;
    }

    @Override
    public Xml.Document visitDocument(Xml.Document document, ExecutionContext ctx) {
        Xml.Document doc = super.visitDocument(document, ctx);
        Xml.Tag root = doc.getRoot();

        if (!root.getChild("build").isPresent() && root.getChildValue("artifactId").isPresent() && root.getChildValue("artifactId").get().equals(scan.getBusinessModuleName())) {
            doc = (Xml.Document) new AddToTagVisitor<>(root, Xml.Tag.build("<build/>"))
                    .visitNonNull(doc, ctx, getCursor().getParentOrThrow());
        }
        return doc;
    }

    @Override
    public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ctx) {
        Xml.Tag t = super.visitTag(tag, ctx);
        String artifactId = getResolutionResult().getPom().getGav().getArtifactId();
        if (BUILD_MATCHER.matches(getCursor()) && scan.getBusinessModuleName().equals(artifactId)) {
            Optional<Xml.Tag> maybePlugins = t.getChild("plugins");
            Xml.Tag plugins;
            if (maybePlugins.isPresent()) {
                plugins = maybePlugins.get();
            } else {
                t = (Xml.Tag) new AddToTagVisitor<>(t, Xml.Tag.build("<plugins/>")).visitNonNull(t, ctx, getCursor().getParentOrThrow());
                plugins = t.getChild("plugins").get();
            }

            Optional<Xml.Tag> maybePlugin = plugins.getChildren().stream()
                    .filter(plugin ->
                            "plugin".equals(plugin.getName()) &&
                                    ARTIFACTID_QUARKUS_MAVEN_PLUGIN.equals(plugin.getChildValue("artifactId").orElse(null))).findAny();

            if (!maybePlugin.isPresent()) {
                Xml.Tag pluginTag = Xml.Tag.build(QUARKUS_MAVEN_PLUGIN);
                t = (Xml.Tag) new AddToTagVisitor<>(plugins, pluginTag).visitNonNull(t, ctx, getCursor().getParentOrThrow());
            }
        }

        if (BUILD_MATCHER.matches(getCursor()) && !"".equals(scan.getBusinessModuleName()) && !scan.getBusinessModuleName().equals(artifactId)) {
            LOG.info("Add jandex to module {}", artifactId);
            Optional<Xml.Tag> maybePlugins = t.getChild("plugins");
            Xml.Tag plugins;
            if (maybePlugins.isPresent()) {
                plugins = maybePlugins.get();
            } else {
                t = (Xml.Tag) new AddToTagVisitor<>(t, Xml.Tag.build("<plugins/>")).visitNonNull(t, ctx, getCursor().getParentOrThrow());
                plugins = t.getChild("plugins").get();
            }

            Optional<Xml.Tag> maybePlugin = plugins.getChildren().stream()
                    .filter(plugin ->
                            "plugin".equals(plugin.getName()) &&
                                    ARTIFACTID_JANDEX_MAVEN_PLUGIN.equals(plugin.getChildValue("artifactId").orElse(null))).findAny();

            if (!maybePlugin.isPresent()) {
                Xml.Tag pluginTag = Xml.Tag.build(JANDEX_MAVEN_PLUGIN);
                t = (Xml.Tag) new AddToTagVisitor<>(plugins, pluginTag).visitNonNull(t, ctx, getCursor().getParentOrThrow());
            }
        }

        if (PACKAGING_MATCHER.matches(getCursor()) && (scan.getEjbModuleName().equals(artifactId) || scan.getWebModuleName().equals(artifactId))) {
            List<? extends Content> content = t.getContent();
            if (content != null && !content.isEmpty()) {
                Xml.CharData existingValue = (Xml.CharData) content.get(0);
                if (existingValue.getText().equals("ejb") || existingValue.getText().equals("war")) {
                    return t.withValue("jar");
                }
            }
        }

        if (MODULE_MATCHER.matches(getCursor()) && (scan.getEarModuleName().equals(t.getValue().get()) || scan.getBatchModuleNameList().contains(t.getValue().get()))) {
            return null;
        }
        return t;
    }
}
