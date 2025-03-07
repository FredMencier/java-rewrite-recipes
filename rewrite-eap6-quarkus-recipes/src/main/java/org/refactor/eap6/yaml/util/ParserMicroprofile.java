package org.refactor.eap6.yaml.util;

import io.helidon.common.CollectionsHelper;
import io.smallrye.openapi.api.models.PathItemImpl;
import io.smallrye.openapi.api.models.media.SchemaImpl;
import io.smallrye.openapi.api.models.parameters.ParameterImpl;
import org.eclipse.microprofile.openapi.models.OpenAPI;
import org.eclipse.microprofile.openapi.models.PathItem;
import org.eclipse.microprofile.openapi.models.media.Schema;
import org.eclipse.microprofile.openapi.models.parameters.Parameter;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.introspector.Property;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.representer.Representer;

import java.io.PrintWriter;
import java.io.Writer;
import java.nio.CharBuffer;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ParserMicroprofile {

    public static final String OPENAPI_VERSION = "3.0.3";

    public static void toYAML(OpenAPI openAPI, Writer writer) {
        openAPI.setOpenapi(OPENAPI_VERSION);
        DumperOptions opts = new DumperOptions();
        opts.setIndent(2);
        opts.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        Yaml yaml = new Yaml(new CustomRepresenter(opts), opts);
        yaml.dump(openAPI, new TagSuppressingWriter(writer));
    }

    private static class CustomRepresenter extends Representer {

        private static final Map<Class<?>, Set<String>> childEnumNames = new HashMap<>();
        private static final Map<Class<?>, Map<String, Set<String>>> childEnumValues =
                new HashMap<>();

        static {
            childEnumNames.put(PathItemImpl.class, toEnumNames(PathItem.HttpMethod.class));
            childEnumValues.put(SchemaImpl.class,
                    CollectionsHelper.mapOf("type", toEnumNames(Schema.SchemaType.class)));
            childEnumValues.put(ParameterImpl.class,
                    CollectionsHelper.mapOf("style", toEnumNames(Parameter.Style.class),
                            "in", toEnumNames(Parameter.In.class)));
        }

        public CustomRepresenter(DumperOptions options) {
            super(options);
        }

        private static <E extends Enum<E>> Set<String> toEnumNames(Class<E> enumType) {
            Set<String> result = new HashSet<>();
            for (Enum<E> e : enumType.getEnumConstants()) {
                result.add(e.name());
            }
            return result;
        }

        @Override
        protected NodeTuple representJavaBeanProperty(Object javaBean, Property property, Object propertyValue,
                                                      org.yaml.snakeyaml.nodes.Tag customTag) {
            if (propertyValue == null) {
                return null;
            }
            NodeTuple result = super.representJavaBeanProperty(javaBean, property, propertyValue, customTag);

            if (childEnumNames.getOrDefault(javaBean.getClass(), Collections.emptySet()).contains(property.getName())) {
                result = new NodeTuple(adjustNode(result.getKeyNode()), result.getValueNode());
            }
            if (propertyValue instanceof Enum && childEnumValues.getOrDefault(javaBean.getClass(),
                            Collections.emptyMap())
                    .getOrDefault(property.getName(), Collections.emptySet())
                    .contains(((Enum) propertyValue).name())) {
                result = new NodeTuple(result.getKeyNode(), adjustNode(result.getValueNode()));
            }
            return result;
        }

        private static Node adjustNode(Node n) {
            Node result = n;
            if (n instanceof ScalarNode) {
                ScalarNode orig = (ScalarNode) n;
                result = new ScalarNode(orig.getTag(), orig.getValue()
                        .toLowerCase(),
                        orig.getStartMark(), orig.getEndMark(), orig.getScalarStyle());
            }
            return result;
        }
    }

    static class TagSuppressingWriter extends PrintWriter {

        private static final Pattern UNQUOTED_TRAILING_TAG_PATTERN = Pattern.compile("\\![^\"]+$");

        TagSuppressingWriter(Writer out) {
            super(out);
        }

        @Override
        public void write(char[] cbuf, int off, int len) {
            int effLen = detag(CharBuffer.wrap(cbuf), off, len);
            if (effLen > 0) {
                super.write(cbuf, off, effLen);
            }
        }

        @Override
        public void write(String s, int off, int len) {
            int effLen = detag(s, off, len);
            if (effLen > 0) {
                super.write(s, off, effLen);
            }
        }

        private int detag(CharSequence cs, int off, int len) {
            int result = len;
            Matcher m = UNQUOTED_TRAILING_TAG_PATTERN.matcher(cs.subSequence(off, off + len));
            if (m.matches()) {
                result = len - (m.end() - m.start());
            }

            return result;
        }
    }
}
