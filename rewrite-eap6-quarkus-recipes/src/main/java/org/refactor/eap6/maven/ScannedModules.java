package org.refactor.eap6.maven;

import lombok.Data;
import org.openrewrite.SourceFile;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

@Data
public class ScannedModules {

    private String ejbModuleName = "";

    private String businessModuleName = "";

    private String earModuleName = "";

    private String webModuleName = "";

    private final Set<String> testModuleNameList = new HashSet<>();
    private final Set<String> batchModuleNameList = new HashSet<>();
    private final Set<SourceFile> confFiles = new HashSet<>();
    private AtomicBoolean shouldCreateConfigFiles;

    @Override
    public String toString() {
        return "Scanned{" +
                ", ejbModuleName='" + ejbModuleName + '\'' +
                ", businessModuleName='" + businessModuleName + '\'' +
                ", earModuleName='" + earModuleName + '\'' +
                ", webModuleName=" + webModuleName +
                '}';
    }
}
