package org.gokb;

import org.gokb.cred.KBComponent;
import org.hibernate.SessionFactory;

public class HibernateCriteriaBuilder<T extends KBComponent> extends grails.orm.HibernateCriteriaBuilder {

  public HibernateCriteriaBuilder(Class<T> targetClass, SessionFactory sessionFactory, boolean uniqueResult) {
    super(targetClass, sessionFactory, uniqueResult);
  }

  public HibernateCriteriaBuilder(Class<T> targetClass, SessionFactory sessionFactory) {
    super(targetClass, sessionFactory);
  }
  
  @SuppressWarnings("unchecked")
  public Class<T> getTargetClass() {
    return (Class<T>)super.getTargetClass();
  }
}
