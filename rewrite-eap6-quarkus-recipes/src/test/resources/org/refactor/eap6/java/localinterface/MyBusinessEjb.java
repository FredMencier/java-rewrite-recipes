package org.refactor.eap6.java.localinterface;

import javax.ejb.Stateless;
import java.util.Collections;
import java.util.List;
@Stateless
public class MyBusinessEjb implements MyLocalInterface {
    @Override
    public List<String> findAllPerson() {
        return Collections.emptyList();
    }
}