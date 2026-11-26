import java.nio.file.*;
import java.util.*;
import java.util.List;
import org.hibernate.cfg.AnnotationConfiguration;
import org.hibernate.cfg.ImprovedNamingStrategy;
import org.hibernate.mapping.*;

/** Prints entity -> table and property -> columns for the OpenClinica Hibernate 3.6 mapping. */
public class DumpH3 {
    public static void main(String[] args) throws Exception {
        AnnotationConfiguration cfg = new AnnotationConfiguration();
        cfg.setNamingStrategy(new ImprovedNamingStrategy());
        cfg.setProperty("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
        cfg.addResource("org/akaza/openclinica/typedefs.hbm.xml");
        for (String c : Files.readAllLines(Paths.get(args[0]))) {
            cfg.addAnnotatedClass(Class.forName(c.trim()));
        }
        cfg.buildMappings();
        List<String> out = new ArrayList<String>();
        Iterator<?> it = cfg.getClassMappings();
        while (it.hasNext()) {
            PersistentClass pc = (PersistentClass) it.next();
            String e = pc.getEntityName();
            out.add(e + " TABLE " + pc.getTable().getName());
            if (pc.getIdentifier() instanceof SimpleValue) {
                SimpleValue sv = (SimpleValue) pc.getIdentifier();
                java.util.Properties gp = sv.getIdentifierGeneratorProperties();
                String seq = gp == null ? null : (gp.getProperty("sequence") != null ? gp.getProperty("sequence") : gp.getProperty("sequence_name"));
                out.add(e + ".<gen> " + sv.getIdentifierGeneratorStrategy() + " seq=" + seq);
            }
            if (pc.getIdentifierProperty() != null) out.add(e + ".<id> " + cols(pc.getIdentifierProperty().getValue()));
            else if (pc.getIdentifier() != null) out.add(e + ".<id> " + cols(pc.getIdentifier()));
            Iterator<?> pi = pc.getPropertyClosureIterator();
            while (pi.hasNext()) {
                Property p = (Property) pi.next();
                Value v = p.getValue();
                String extra = "";
                if (v instanceof org.hibernate.mapping.Collection) {
                    org.hibernate.mapping.Collection coll = (org.hibernate.mapping.Collection) v;
                    extra = " COLL table=" + coll.getCollectionTable().getName() + " key=" + cols(coll.getKey())
                            + (coll.getElement() != null ? " elem=" + cols(coll.getElement()) : "");
                }
                out.add(e + "." + p.getName() + " " + cols(v) + extra);
            }
        }
        Collections.sort(out);
        for (String s : out) System.out.println(s);
    }

    static String cols(Value v) {
        List<String> names = new ArrayList<String>();
        Iterator<?> ci = v.getColumnIterator();
        while (ci.hasNext()) {
            Object o = ci.next();
            names.add(o instanceof Column ? ((Column) o).getName() : "formula:" + ((Formula) o).getFormula());
        }
        return names.toString();
    }
}
