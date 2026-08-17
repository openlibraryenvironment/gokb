package org.gokb

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

class RuntimeVariableService {

  private static ConcurrentMap<String, String> runtimeVariables = new ConcurrentHashMap<>()
  private static ArrayList<String> allowedKeys = Arrays.asList(new String[]
          {"disablePackageCaching", "test"})

  public Map getRuntimeVariables () {
    return runtimeVariables
  }

  public String getRuntimeVariable (String key) {
    return runtimeVariables.get(key)
  }

  // add new key or update existing one
  public void addOrUpdateRuntimeVariable (String key, String value) {
    if (allowedKeys.contains(key)) {
      runtimeVariables.put(key, value)
    }
  }

  public void removeRuntimeVariable (String key) {
    runtimeVariables.remove(key)
  }

}
