package org.refactor.eap6.java.dto;

public class Dog extends Animal {

    private Integer pattes;

    public Dog(String name) {
        super(name);
    }

    public Integer getPattes() {
        return pattes;
    }

    public void setPattes(Integer pattes) {
        this.pattes = pattes;
    }
}
