package com.k_int.gokb.refine.notifications;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;


public class NotificationStack extends ArrayList<Notification> {
  private static final long serialVersionUID = -7538591755046789121L;
  
  private static Map<String, NotificationStack> stacks = new HashMap<String, NotificationStack> ();
  
  public static NotificationStack get(String id) {
    NotificationStack stack = stacks.get(id);
    if (stack == null) {
      
      // Create a stack if it doesn't exist.
      stack = new NotificationStack();
      
      // Now we also need to add to the map.
      stacks.put(id, stack);
    }
    
    return stack;
  }
  
  public Notification get (int pos) {
    
    // Grab the message.
    Notification message = super.get(pos);
    return message;
  }
}