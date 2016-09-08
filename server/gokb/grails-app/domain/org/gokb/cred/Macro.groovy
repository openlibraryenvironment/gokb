package org.gokb.cred

class Macro extends KBComponent {
  
  String refineTransformations
  String description
  
  static mapping = {
    includes KBComponent.mapping
    refineTransformations type: 'text'
    description maxSize:128
 }
}
