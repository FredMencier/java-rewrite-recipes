package org.refactor.eap6.java.dto;

public class Cat {

    private String name;

    private Integer pattes;

    public Cat(String name, Integer pattes) {
        this.name = name;
        this.pattes = pattes;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getPattes() {
        return pattes;
    }

    public void setPattes(Integer pattes) {
        this.pattes = pattes;
    }
}
