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
    PACKAGE  (25),
    TITLE    (50),
    CENTRAL  (75)

    private final int levelNumber

    Level(int levelNumber) {
      this.levelNumber = levelNumber
    }

    int getLevelNumber(){
      levelNumber
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
