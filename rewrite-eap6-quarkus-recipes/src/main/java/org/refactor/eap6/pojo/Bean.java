package org.refactor.eap6.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class Bean {

    private String typeSimpleName;

    private String typeFullyQualified;

    private String varname;
}
