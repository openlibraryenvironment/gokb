package org.gokb.cred

class CuratoryGroupType {

    // TODO: frontend admin section

    Level level
    String name
    MediumScope mediumScope

    static constraints = {
        level (nullable:false, blank:false)
        name (nullable:false, blank:false)
        mediumScope (nullable:false, blank:false)
    }


    static enum Level{
        PACKAGE     (1),
        TITLE       (5),
        CENTRAL    (10)

        private final int levelNumber

        Level getByName(String name){
            for (Level level : Level.values()) {
                if (level.name().equalsIgnoreCase(name)) {
                    return level
                }
            }
            return null
        }
    }

    static enum MediumScope{
        MONOGRAPH,
        SERIAL

        MediumScope getByName(String name){
            for (MediumScope scope : MediumScope.values()) {
                if (scope.name().equalsIgnoreCase(name)) {
                    return scope
                }
            }
            return null
        }
    }
}
