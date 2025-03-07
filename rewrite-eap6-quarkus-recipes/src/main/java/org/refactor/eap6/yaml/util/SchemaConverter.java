package org.refactor.eap6.yaml.util;

import io.smallrye.openapi.api.models.media.SchemaImpl;
import org.eclipse.microprofile.openapi.models.media.Schema;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class SchemaConverter {

    public static org.eclipse.microprofile.openapi.models.media.Schema convert(io.swagger.v3.oas.models.media.Schema<?> swaggerSchema) {
        if (swaggerSchema == null) {
            return null;
        }

        org.eclipse.microprofile.openapi.models.media.Schema microProfileSchema = new SchemaImpl();
        microProfileSchema.setTitle(swaggerSchema.getTitle());
        if (swaggerSchema.get$ref() != null) {
            microProfileSchema.setRef(swaggerSchema.get$ref());
        } else {
            microProfileSchema.setType(swaggerSchema.getType() != null ? org.eclipse.microprofile.openapi.models.media.Schema.SchemaType.valueOf(swaggerSchema.getType().toUpperCase()) : Schema.SchemaType.OBJECT);
        }
        if (microProfileSchema.getType() != null && microProfileSchema.getType().name().equalsIgnoreCase("number")) {
            microProfileSchema.setFormat("double");
        } else {
            microProfileSchema.setFormat(swaggerSchema.getFormat());
        }
        microProfileSchema.setDescription(swaggerSchema.getDescription());
        microProfileSchema.setDefaultValue(swaggerSchema.getDefault());
        microProfileSchema.setExample(swaggerSchema.getExample());
        microProfileSchema.setRequired(swaggerSchema.getRequired());
        microProfileSchema.setNullable(swaggerSchema.getNullable());
        microProfileSchema.setReadOnly(swaggerSchema.getReadOnly());
        microProfileSchema.setWriteOnly(swaggerSchema.getWriteOnly());
        microProfileSchema.setDeprecated(swaggerSchema.getDeprecated());
        if (swaggerSchema.getEnum() != null && !swaggerSchema.getEnum().isEmpty()) {
            microProfileSchema.setEnumeration(Arrays.asList(swaggerSchema.getEnum().toArray()));
        }
        if (swaggerSchema.getAdditionalProperties() != null) {
            microProfileSchema.setAdditionalPropertiesSchema(convert((io.swagger.v3.oas.models.media.Schema<?>) swaggerSchema.getAdditionalProperties()));
        }
        if (swaggerSchema.getProperties() != null) {
            Map<String, org.eclipse.microprofile.openapi.models.media.Schema> properties = new HashMap<>();
            swaggerSchema.getProperties().forEach((key, value) -> {
                properties.put(key, convert((io.swagger.v3.oas.models.media.Schema<?>) value));
            });
            microProfileSchema.setProperties(properties);
        }

        if (swaggerSchema.getItems() != null) {
            microProfileSchema.setItems(convert(swaggerSchema.getItems()));
        }

        return microProfileSchema;
    }
}
