package org.refactor.eap6.java.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class AnimalComposite implements Serializable {

    private List<Cat> cats = new ArrayList<>();

    private String code;

    public AnimalComposite() {
    }

    public List<Cat> getDogs() {
        return cats;
    }

    public void setDogs(List<Cat> dogs) {
        this.cats = cats;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }
}
