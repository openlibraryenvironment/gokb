package com.k_int.gokb.module;

public abstract class A_ScheduledUpdates {
  protected A_ScheduledUpdates () {
    GOKbModuleImpl.singleton.registerScheduledObject(this);
  }
  public abstract void doScheduledUpdates() throws Throwable;
}
