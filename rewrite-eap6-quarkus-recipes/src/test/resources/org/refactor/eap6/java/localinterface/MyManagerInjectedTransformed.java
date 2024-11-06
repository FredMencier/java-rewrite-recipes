package org.refactor.eap6.java.localinterface;

import javax.ejb.EJB;

public class MyManagerInjected {

    @EJB
    MyLocalInterfaceImpl myLocalInterfaceImpl;

    public void foo() {
        //do nothing
    }
}