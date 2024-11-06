package org.refactor.eap6.java.persistence;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.xml.Assertions.xml;
import static org.openrewrite.yaml.Assertions.yaml;

class AddPersistenceUnitToYamlTest  implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new AddPersistenceUnitToYaml());
    }

    @Test
    void addPersistenceToYamlTest() {
        rewriteRun(
                xml("""
                        <?xml version="1.0" encoding="UTF-8"?>
                        <persistence xmlns="http://java.sun.com/xml/ns/persistence"
                                     xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                                     xsi:schemaLocation="http://java.sun.com/xml/ns/persistence
                                     http://java.sun.com/xml/ns/persistence/persistence_2_0.xsd" version="2.0">
                                                
                            <persistence-unit name="FinancialPU" transaction-type="JTA">
                                <description>Hibernate EntityManager Financial</description>
                                <provider>org.hibernate.ejb.HibernatePersistence</provider>
                                <jta-data-source>java:jboss/datasources/FinancialDS</jta-data-source>
                                <mapping-file>mappings.xml</mapping-file>
                                <exclude-unlisted-classes>true</exclude-unlisted-classes>
                                <properties>
                                    <property name="hibernate.dialect" value="org.hibernate.dialect.MySQLDialect"/>
                                    <property name="hibernate.connection.driver_class" value="com.mysql.cj.jdbc.Driver"/>
                                </properties>
                            </persistence-unit>
                        </persistence>
                        """),
                yaml("""
                                           """,
                        """
                            quarkus:
                                datasource:
                                    FinancialPU:
                                        db-kind: mysql
                                        jdbc:
                                           url: jdbc:mysql://localhost:3306/mydatabase
                                        username: root
                                        password: mysqladmin
                                           """)
        );
    }
}