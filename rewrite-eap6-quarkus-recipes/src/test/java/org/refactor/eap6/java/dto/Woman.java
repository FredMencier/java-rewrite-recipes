package org.refactor.eap6.java.dto;

public class Woman implements Human {

    private String hairColor;

    private String name;


    public Woman(String hairColor, String name) {
        this.hairColor = hairColor;
        this.name = name;
    }

    public String getHairColor() {
        return hairColor;
    }

    public void setHairColor(String hairColor) {
        this.hairColor = hairColor;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
