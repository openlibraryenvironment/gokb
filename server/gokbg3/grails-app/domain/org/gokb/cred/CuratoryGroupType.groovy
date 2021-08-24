package org.gokb.cred

class CuratoryGroupType extends KBComponent{

  // TODO: frontend admin section

  Level level
  String name

  static constraints = {
    level (nullable:false, blank:false)
    name (nullable:false, blank:false)
  }


  static enum Level{
    PACKAGE  ("PACKAGE", 25),
    TITLE    ("TITLE", 50),
    CENTRAL  ("CENTRAL", 75)

    private final String name
    private final int levelNumber


    private Level(String name) {
      this.name = name
    }

    Level getByName(String name){
      for (Level level : Level.values()) {
        if (level.name().equalsIgnoreCase(name)) {
          return level
        }
      }
      return null
    }
  }
}
