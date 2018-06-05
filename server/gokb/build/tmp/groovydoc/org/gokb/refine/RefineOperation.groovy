package org.gokb.refine

import org.grails.web.json.JSONObject
import org.grails.web.json.JSONArray
import org.grails.web.json.JSONElement


class RefineOperation {
  
  static hasMany = [operation: Serializable]
  static mapping = {operation type : 'serializable'}
  String description
  Map<String, Serializable> operation = [:]
  
  void setOperation(Map operation) {
    this.operation = serializeJSON (operation)
  }
  
  private Map serializeJSON (Map object){
    
    if (object.is(Serializable)) {
      // Just return the object here.
      return object;
    }
    
    // Convert to a serializable map.
        Map<String, Serializable> result = [:]         
        object?.each { key, value -> 
            switch (value) {
        case JSONArray :
                def list = []
        // Arrays get converted to lists.
                value.each { list << serializeJSON(it) }
                result."$key" = list
        
        break
        case JSONObject :
          // Objects should be converted to map.
                result."$key" = serializeJSON(value)
        break 
        
              default :
          // Just add the value as it should be serializable.
                result."$key" = (value == JSONObject.NULL) ? null : value
            }             
        }
        return result 
  } 
}
