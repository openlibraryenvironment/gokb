package org.gokb
import groovy.lang.Closure
import grails.gorm.DetachedCriteria
import grails.orm.HibernateCriteriaBuilder
import org.gokb.cred.*
import org.codehaus.groovy.grails.commons.GrailsClassUtils
import org.hibernate.criterion.CriteriaSpecification

class ComboCriteria {
  
  //TODO: All the detached criteria queries need to be able to exit the method if they return no results. This
  // is because all the conditions must be met and an empty set in any of them means the result will be empty.

  private HibernateCriteriaBuilder crit
  private static ComboCriteria comboCrit

  private ComboCriteria (HibernateCriteriaBuilder crit) {
    //	crit.createAlias("incomingCombos", "incomingCombos")
    this.crit = crit
  }

  public static ComboCriteria createFor(HibernateCriteriaBuilder crit) {
    if (comboCrit == null || comboCrit.crit != crit) comboCrit = new ComboCriteria (crit)
    comboCrit
  }

  def methodMissing(String name, args) {
    // Just try and invoke on the criteria.

    // We need to try and invoke on the HibernateCriteria.
    crit.invokeMethod(name, args)
  }

  public ComboCriteria add (String propertyName, String operator, args = []) {
    add(propertyName, operator, args, null)
  }

  private def add = {String propertyName, String operator, args = [].toArray(), Class the_class ->

    // Start class as the target class of the query builder, if none supplied.
    the_class = the_class ?: crit.targetClass;

    // Get all the combo properties defined on the class.
    Map allProps = KBComponent.getAllComboPropertyDefinitionsFor(the_class)

    // Split the property and go through each property as needed.
    List props = propertyName.split("\\.")

    // If props length > 1 then we need to first add all the necessary associations.
    if (props.size() > 1) {

      // Pop the first element from the list.
      String prop = props.remove(0)

      // Set the property value to the new list minus the head.
      def newPropName = props.join(".")

      // Get the type that the property maps to (check combo props first).
      Class target_class = allProps[prop]

      // Combo property?
      if (target_class) {
        boolean incoming = KBComponent.lookupComboMappingFor (the_class, Combo.MAPPED_BY, prop)

        // Combo property... Let's add the association.
        if (incoming) {

          // Use detached criteria to add a IN clause.
          def subquery = new DetachedCriteria(the_class).build {
            "incomingCombos" {
              and {
                eq (
                    "type",
                    RefdataCategory.lookupOrCreate (
                    "Combo.Type",
                    the_class.getComboTypeValueFor (the_class, prop)
                    )
                    )
                fromComponent {
                  add (newPropName, operator, args, target_class)
                }
              }
            }

            projections {
              property ("id")
            }
          }.list()

          // Make sure the ID is in the list.
          "in" ("id", subquery ? subquery : [-1])

        } else {

          // Detached criteria.
          def subquery = new DetachedCriteria(the_class).build {
            "outgoingCombos" {
              and {
                eq (
                    "type",
                    RefdataCategory.lookupOrCreate (
                    "Combo.Type",
                    the_class.getComboTypeValueFor (the_class, prop)
                    )

                    )
                fromComponent {
                  add (newPropName, operator, args, target_class)
                }
              }
            }

            projections {
              property ("id")
            }
          }.list()

          // Make sure the ID is in the list.
          "in" ("id", subquery ? subquery : [-1])
        }
      } else {
        // Normal groovy/grails property.
        target_class = GrailsClassUtils.getPropertyType(the_class, prop)
        crit.targetClass = the_class

        // Add the association here.
        // SO: Because of the dot notation we must ensure we use a left join here.
        "${prop}" (CriteriaSpecification.LEFT_JOIN) {
          add (newPropName, operator, args, target_class)
        }
      }

    } else {

      // We need to do the comparison.

      // Check if this is a combo property.
      if (allProps[propertyName]) {

        // Add association using either incoming or outgoing properties.
        boolean incoming = KBComponent.lookupComboMappingFor (the_class, Combo.MAPPED_BY, propertyName)

        if (incoming) {

          // Detached criteria.
          def subquery = new DetachedCriteria(the_class).build {
            "incomingCombos" {
              and {
                eq (
                    "type",
                    RefdataCategory.lookupOrCreate (
                    "Combo.Type",
                    the_class.getComboTypeValueFor (the_class, propertyName)
                    )
                    )
                def methodProps = ["fromComponent"]
                methodProps.addAll(args)
                invokeMethod(operator, methodProps.toArray())
              }
            }

            projections {
              property ("id")
            }
          }.list()

          // Make sure the ID is in the list.
          "in" ("id", subquery ? subquery : [-1])

        } else {

          // Detached criteria.
          def subquery = new DetachedCriteria(the_class).build {
            "outgoingCombos" {
              and {
                eq (
                    "type",
                    RefdataCategory.lookupOrCreate (
                    "Combo.Type",
                    the_class.getComboTypeValueFor (the_class, propertyName)
                    )
                    )
                def methodProps = ["toComponent"]
                methodProps.addAll(args)
                invokeMethod(operator, methodProps.toArray())
              }
            }

            projections {
              property ("id")
            }
          }.list()

          // Make sure the ID is in the list.
          "in" ("id", subquery ? subquery : [-1])
        }
      } else {
        // Normal grails property.
        def methodProps = [propertyName]
        methodProps.addAll(args)
        invokeMethod(operator, methodProps.toArray())
      }
    }

    // return this.
    this
  }
}
