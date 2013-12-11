package com.k_int

import org.hibernate.proxy.HibernateProxy

class ClassUtils {
  public static <T> T deproxy(def element) {
    if (element instanceof HibernateProxy) {
      return (T) ((HibernateProxy) element).getHibernateLazyInitializer().getImplementation();
    }
    return (T) element;
  }
}
