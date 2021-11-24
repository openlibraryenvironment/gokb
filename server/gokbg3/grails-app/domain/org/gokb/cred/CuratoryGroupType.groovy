package org.gokb.cred

import javax.persistence.Transient

class CuratoryGroupType{

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


  /**
   *  refdataFind generic pattern needed by inplace edit taglib to provide reference data to typedowns and other UI components.
   *  objects implementing this method can be easily located and listed / selected
   */
  static def refdataFind(params) {
    def result = []
    def ql = Class.forName(params.baseClass).findAllByNameIlike("${params.q}%", params)
    if (ql) {
      ql.each { t ->
        if (!params.filter1) {
          result.add([id: "${t.class.name}:${t.id}", text: "${t.name}"])
        }
      }
    }
    result
  }


  String toString() {
    return name
  }

}
