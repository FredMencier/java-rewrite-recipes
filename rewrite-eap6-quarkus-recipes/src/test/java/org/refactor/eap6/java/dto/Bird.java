package org.refactor.eap6.java.dto;

public class Bird extends Animal {
    private Integer ailes;

    public Bird(String name) {
        super(name);
    }

    public Integer getAiles() {
        return ailes;
    }

    public void setAiles(Integer ailes) {
        this.ailes = ailes;
    }
}
