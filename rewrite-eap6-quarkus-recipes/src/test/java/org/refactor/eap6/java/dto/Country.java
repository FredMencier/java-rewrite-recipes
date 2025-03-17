package org.refactor.eap6.java.dto;

public class Country {

    String name;

    Integer avarageTemperature;

    public Country(String name, Integer avarageTemperature) {
        this.name = name;
        this.avarageTemperature = avarageTemperature;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getAvarageTemperature() {
        return avarageTemperature;
    }

    public void setAvarageTemperature(Integer avarageTemperature) {
        this.avarageTemperature = avarageTemperature;
    }
}
