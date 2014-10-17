package com.k_int.gokb.refine.notifications;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.json.JSONException;
import org.json.JSONWriter;

import com.google.refine.Jsonizable;


public class NotificationStack extends ArrayList<Notification> implements Jsonizable {
  private static final String STACK_SYSTEM = "system";
  private static final long serialVersionUID = -7538591755046789121L;
  
  private static Map<String, NotificationStack> stacks = new HashMap<String, NotificationStack> ();
  private Map<String, Notification> byId = new HashMap<String, Notification> ();
  
  public static NotificationStack getSystemStack() {
    return NotificationStack.get(STACK_SYSTEM);
  }
  
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

  @Override
  public boolean contains (Object o) {
    if (o instanceof Notification) {
      
      Notification n = (Notification) o;
      if (n.getId() != null) {
        return byId.containsKey(n.getId());
      }
      
      return super.contains(o);
    }
    
    return false;
  }

  @Override
  public boolean add (Notification n) {
    add(size(), n);
    return true;
  }

  @Override
  public void add (int index, Notification n) {
    String id = n.getId();
    
    // Only add if not already here.
    if (!contains (n)) {
      super.add(index, n);
      
      if (id != null && !byId.containsKey(id)) {
        byId.put(id, n);
      }
    }
  }

  @Override
  public boolean addAll (Collection<? extends Notification> c) {
    return addAll(size(), c);
  }

  @Override
  public boolean containsAll (Collection<?> c) {
    boolean all = true;
    for (int i=0; all && i<c.size(); i++) {
      all = contains(c);
    }
    return all;
  }

  @Override
  public boolean addAll (int index, Collection<? extends Notification> c) {
    int count = 0;
    for (Notification n : c) {
      add(index + count, n);
      count ++;
    }
    return true;
  }
  
  @Override
  public void write (JSONWriter writer, Properties options)
      throws JSONException {
    
    // JSONize this list.
    writer.array();
    
    for (Notification n : this) {
      n.write(writer, options);
    }
    
    writer.endArray();
  }
}