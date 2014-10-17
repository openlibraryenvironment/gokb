package com.k_int.gokb.refine;

import com.k_int.gokb.refine.notifications.Notification;

import com.google.gson.Gson;

public abstract class Jsonizable implements com.google.refine.Jsonizable {
  
  public static <T extends Notification> T fromJSON (String json, Class<T> theClass) {
    Gson gson = new Gson();
    return gson.fromJson(json, theClass);
  }
  
  public String toJSON () {
    Gson gson = new Gson();
    return gson.toJson(this);
  }

}
