package org.exemple.java.localinterface;

import javax.ejb.EJB;

public class MyManagerInjected {

    @EJB
    MyLocalInterface myLocalInterface;

    public void foo() {
        //do nothing
    }
}