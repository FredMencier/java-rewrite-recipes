package org.refactor.eap6.properties;

public class MyBusinessClassWithProps {

    private final static String PROPS_SYSTEM_KEY = "MyFinancialApp.system";

    public String getDeploymentInformations(String name) {
        String serverName = PropsUtil.getProperty(PROPS_SYSTEM_KEY);
        return name + " deploy on " + serverName;
    }

}