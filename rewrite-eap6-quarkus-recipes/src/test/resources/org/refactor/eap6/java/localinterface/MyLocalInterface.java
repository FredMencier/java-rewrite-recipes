package org.exemple.java.localinterface;

import javax.ejb.Local;
import java.util.List;

@Local
public interface MyLocalInterface {

    List<String> findAllPerson();
}