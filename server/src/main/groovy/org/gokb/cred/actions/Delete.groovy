package org.gokb.cred.actions

import org.gokb.cred.KBComponent

class Delete extends A_Action {
  @Override
  public Object doAction(def param = []) {
    if (!param instanceof KBComponent) {
      throw new IllegalArgumentException ("Delete action requires 1 argument to be supplied that is an instance of KBComponent")
    }
    
    // Do the delete action.
    KBComponent comp = param as KBComponent
    comp.deleteSoft()
  }
}
