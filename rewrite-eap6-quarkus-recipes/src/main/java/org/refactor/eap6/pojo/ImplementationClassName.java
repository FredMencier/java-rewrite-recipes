package org.refactor.eap6.pojo;

public class ImplementationClassName {
  private String classname;
    private boolean mainImplementation;

    public ImplementationClassName(String classname, boolean mainImplementation) {
        this.classname = classname;
        this.mainImplementation = mainImplementation;
    }

    public String getClassname() {
        return classname;
    }

    public void setClassname(String classname) {
        this.classname = classname;
    }

    public boolean isMainImplementation() {
        return mainImplementation;
    }

    public void setMainImplementation(boolean mainImplementation) {
        this.mainImplementation = mainImplementation;
    }
}
