package com.k_int;

import org.hibernate.criterion.DetachedCriteria;
import org.hibernate.internal.CriteriaImpl;

public class ExtendedHibernateDetachedCriteria extends DetachedCriteria {
  private static final long serialVersionUID = 6764031145748026655L;

  public ExtendedHibernateDetachedCriteria (CriteriaImpl impl) {
    super (impl, impl);
  }
}
