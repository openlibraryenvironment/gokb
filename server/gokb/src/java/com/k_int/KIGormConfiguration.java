package com.k_int;
 
import java.util.Collection;
import java.util.Iterator;
 
import org.codehaus.groovy.grails.orm.hibernate.cfg.GrailsAnnotationConfiguration;
import org.hibernate.MappingException;
import org.hibernate.mapping.ForeignKey;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.mapping.RootClass;
 
public class KIGormConfiguration extends GrailsAnnotationConfiguration {
 
   private static final long serialVersionUID = 1;
 
   private boolean _alreadyProcessed;
 
   @SuppressWarnings({"unchecked", "rawtypes"})
   @Override
   protected void secondPassCompile() throws MappingException {
      super.secondPassCompile();
 
      if (_alreadyProcessed) {
         return;
      }
 
      for (PersistentClass pc : (Collection<PersistentClass>)classes.values()) {
         if (pc instanceof RootClass) {
            RootClass root = (RootClass)pc;
            if ("org.gokb.cred.Combo".equals(root.getClassName())) {
               for (Iterator iter = root.getTable().getForeignKeyIterator(); iter.hasNext();) {
                  ForeignKey fk = (ForeignKey)iter.next();
                  // fk.setName("FK_USER_COUNTRY");  -- Do this to rename the silly grails fk_nnnnnnn thing, 
                  iter.remove();  // Or this to remove the FK entirely.
               }
            }
         }
      }
 
      _alreadyProcessed = true;
   }
}
