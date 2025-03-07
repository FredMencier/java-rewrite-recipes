package org.refactor.eap6.util;

import java.util.Arrays;
import java.util.stream.Collectors;

public class RewriteUtils {
      public static String getCamelCaseVariable(final String varName) {
        String sub = varName.substring(varName.lastIndexOf(".") + 1);
        return sub.substring(0, 1).toLowerCase() + sub.substring(1);
    }

    public static String normalizeVariable(String varName) {
        String cleanVar = varName.replaceAll("\\.", "_").toLowerCase();
        return Arrays.stream(cleanVar.split("_")).map(RewriteUtils::firstUpperCase).collect(Collectors.joining());
    }

    public static String firstUpperCase(final String s) {
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }
}
