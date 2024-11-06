package org.refactor.eap6.properties;

import org.eclipse.microprofile.config.inject.ConfigProperty;

public class MyBusinessClassWithProps {

    @ConfigProperty(name = PROPS_SYSTEM_KEY)
    Optional<java.lang.String> serverName;

    private final static String PROPS_SYSTEM_KEY = "MyFinancialApp.system";

    public String getDeploymentInformations(String name) {
        return name + " deploy on " + serverName;
    }

}