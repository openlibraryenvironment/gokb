package com.k_int.gokb.refine.notifications;

import java.util.Collection;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.json.JSONException;
import org.json.JSONWriter;

import com.google.refine.Jsonizable;


public class NotificationStack extends CopyOnWriteArrayList<Notification> implements Jsonizable {
  private static final String STACK_SYSTEM = "system";
  private static final long serialVersionUID = -7538591755046789121L;
  
  private static Map<String, NotificationStack> stacks = new ConcurrentHashMap<String, NotificationStack> ();
  private Map<String, Notification> byId = new ConcurrentHashMap<String, Notification> ();
  
  public static synchronized NotificationStack getSystemStack() {
    return NotificationStack.get(STACK_SYSTEM);
  }
  
  public static synchronized NotificationStack get(String id) {
    NotificationStack stack = stacks.get(id);
    if (stack == null) {
      
      // Create a stack if it doesn't exist.
      stack = new NotificationStack();
      
      // Now we also need to add to the map.
      stacks.put(id, stack);
    }
    
    return stack;
  }
  
  public synchronized Notification get (int pos) {
    
    // Grab the message.
    Notification message = super.get(pos);
    return message;
  }

  @Override
  public synchronized boolean contains (Object o) {
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
  public synchronized boolean add (Notification n) {
    add(size(), n);
    return true;
  }

  @Override
  public synchronized void add (int index, Notification n) {
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
  public synchronized boolean addAll (Collection<? extends Notification> c) {
    return addAll(size(), c);
  }

  @Override
  public synchronized boolean containsAll (Collection<?> c) {
    boolean all = true;
    for (int i=0; all && i<c.size(); i++) {
      all = contains(c);
    }
    return all;
  }

  @Override
  public synchronized boolean addAll (int index, Collection<? extends Notification> c) {
    int count = 0;
    for (Notification n : c) {
      add(index + count, n);
      count ++;
    }
    return true;
  }
  
  @Override
  public synchronized void write (JSONWriter writer, Properties options)
      throws JSONException {
    
    // JSONize this list.
    writer.array();
    
    for (Notification n : this) {
      n.write(writer, options);
    }
    
    writer.endArray();
  }
}