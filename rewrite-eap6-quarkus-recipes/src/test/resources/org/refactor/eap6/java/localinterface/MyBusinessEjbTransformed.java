package org.refactor.eap6.java.localinterface;

import javax.ejb.Stateless;
import java.util.Collections;
import java.util.List;
@Stateless
public class MyBusinessEjb {
    public List<String> findAllPerson() {
        return Collections.emptyList();
    }
}