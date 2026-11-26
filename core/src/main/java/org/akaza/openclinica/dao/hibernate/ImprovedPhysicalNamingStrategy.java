package org.akaza.openclinica.dao.hibernate;

import java.util.Locale;

import org.hibernate.boot.model.naming.Identifier;
import org.hibernate.boot.model.naming.PhysicalNamingStrategy;
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment;

/**
 * Hibernate 5 replacement for the Hibernate 3 {@code org.hibernate.cfg.ImprovedNamingStrategy}
 * the session factory used: every table, column and sequence name (explicit or implicit) goes
 * through the same {@link #addUnderscores(String)} transformation, so the mapping stays identical.
 */
public class ImprovedPhysicalNamingStrategy implements PhysicalNamingStrategy {

    @Override
    public Identifier toPhysicalCatalogName(Identifier name, JdbcEnvironment context) {
        return name;
    }

    @Override
    public Identifier toPhysicalSchemaName(Identifier name, JdbcEnvironment context) {
        return name;
    }

    @Override
    public Identifier toPhysicalTableName(Identifier name, JdbcEnvironment context) {
        return convert(name);
    }

    @Override
    public Identifier toPhysicalSequenceName(Identifier name, JdbcEnvironment context) {
        return convert(name);
    }

    @Override
    public Identifier toPhysicalColumnName(Identifier name, JdbcEnvironment context) {
        return convert(name);
    }

    private static Identifier convert(Identifier name) {
        if (name == null) {
            return null;
        }
        return Identifier.toIdentifier(addUnderscores(name.getText()), name.isQuoted());
    }

    /** Verbatim port of {@code ImprovedNamingStrategy.addUnderscores} from Hibernate 3.6. */
    static String addUnderscores(String name) {
        StringBuilder buf = new StringBuilder(name.replace('.', '_'));
        for (int i = 1; i < buf.length() - 1; i++) {
            if (Character.isLowerCase(buf.charAt(i - 1)) && Character.isUpperCase(buf.charAt(i))
                    && Character.isLowerCase(buf.charAt(i + 1))) {
                buf.insert(i++, '_');
            }
        }
        return buf.toString().toLowerCase(Locale.ROOT);
    }
}
