package org.refactor.xml;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.lang.NonNull;
import org.openrewrite.maven.MavenVisitor;
import org.openrewrite.xml.XPathMatcher;
import org.openrewrite.xml.tree.Xml;

import java.util.Optional;

import static org.openrewrite.xml.AddOrUpdateChild.addOrUpdateChild;

@Value
@EqualsAndHashCode(callSuper = false)
public class ChangePluginConfigurationValue extends Recipe {

    private static final XPathMatcher PLUGINS_MATCHER = new XPathMatcher("/project/build/plugins");

    @Option(displayName = "Group",
            description = "The first part of the coordinate 'org.apache.maven.plugins:maven-compiler-plugin:VERSION' of the plugin to modify.",
            example = "org.apache.maven.plugins")
    String groupId;

    @Option(displayName = "Artifact",
            description = "The second part of a coordinate 'org.apache.maven.plugins:maven-compiler-plugin:VERSION' of the plugin to modify.",
            example = "maven-compiler-plugin")
    String artifactId;

    @Option(displayName = "Tag",
            description = "The name of the tag to change the value of.",
            example = "source")
    String tagToUpdate;

    @Option(displayName = "Value",
            description = "The value to set the tag to.",
            example = "21")
    String newValue;

    @JsonCreator
    public ChangePluginConfigurationValue(@NonNull @JsonProperty("groupId") String groupId, @JsonProperty("artifactId") String artifactId, @NonNull @JsonProperty("tagToUpdate") String tagToUpdate, @NonNull @JsonProperty("newValue") String newValue) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.tagToUpdate = tagToUpdate;
        this.newValue = newValue;
    }

    @Override
    public String getDisplayName() {
        return "Change Maven plugin configuration value for a tag";
    }

    @Override
    public String getInstanceNameSuffix() {
        return String.format("for `%s:%s`", groupId, artifactId);
    }

    @Override
    public String getDescription() {
        return "Chane the specified tag value configuration to a Maven plugin. Will not add the plugin if it does not already exist in the pom.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new MavenVisitor<>() {
            @Override
            public Xml visitTag(Xml.Tag tag, ExecutionContext ctx) {
                Xml.Tag plugins = (Xml.Tag) super.visitTag(tag, ctx);
                if (PLUGINS_MATCHER.matches(getCursor())) {
                    Optional<Xml.Tag> maybePlugin = plugins.getChildren().stream()
                            .filter(plugin ->
                                    "plugin".equals(plugin.getName()) &&
                                            groupId.equals(plugin.getChildValue("groupId").orElse(null)) &&
                                            artifactId.equals(plugin.getChildValue("artifactId").orElse(null))
                            )
                            .findAny();
                    if (maybePlugin.isPresent()) {
                        Xml.Tag foundPlugin = maybePlugin.get();
                        Optional<Xml.Tag> configuration = foundPlugin.getChild("configuration");
                        if (configuration.isPresent()) {
                            Xml.Tag configToUpdate = configuration.get();
                            Xml.Tag configurationUpdated = configToUpdate.withChildValue(tagToUpdate, newValue);
                            plugins = addOrUpdateChild(plugins, foundPlugin, configurationUpdated,
                                    getCursor().getParentOrThrow());
                        }
                    }
                }
                return plugins;
            }
        };
    }
}
