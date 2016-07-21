package org.gokb

import org.springframework.security.access.annotation.Secured;
import grails.util.GrailsNameUtils
import org.gokb.cred.*
import org.hibernate.SessionFactory;
import org.hibernate.transform.AliasToEntityMapResultTransformer

class DecisionSupportController {

  def grailsApplication
  def springSecurityService

  public static String CRITERIA_QUERY = '''select c.id,
       sum(case dsac.value.value when 'Green' then 1 else 0 end),
       sum(case dsac.value.value when 'Red' then 1 else 0 end),
       sum(case dsac.value.value when 'Amber' then 1 else 0 end)
from DSCriterion as c left outer join c.appliedCriterion as dsac with dsac.appliedTo.id = ?
group by c.id
order by c.id
''';

  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def index() {
    def result = [:]
    log.debug("DecisionSupportController::index");
    def dimension = params.dimension?:'Platform'

    result.matrix = calculateMatrix(dimension, params.q?:'%');

    result
  }


  def untwo() {
    log.debug("untwo");
  }

  private def calculateMatrix(dimension, q) {

    def criterion_heads = [];
    def criterion = [];

    DSCategory.executeQuery('select cat from DSCategory as cat order by cat.id').each { cat ->
     def groupcount = 0
      cat.criterion.each { crit ->
        criterion.add([id:crit.id, title:crit.title, description:crit.description, explanation:crit.explanation, color:cat.colour])
        groupcount++
      }
      criterion_heads.add([name:cat.description, count:groupcount, color:cat.colour])
    }

    def qry = null;


    switch (dimension) {
      case 'Package':
        break;
      case 'Title':
        break;
      case 'Platform':
      default:
        qry = 'select p from Platform as p where p.name like :q';
        break;
    }

    def rowdata = []
    KBComponent.executeQuery(qry,[q:q]).each { row ->
      // Select all the criteria we need
      log.debug("Process row: ${row.name}");
      def row_info = DSCriterion.executeQuery(CRITERIA_QUERY,[row.id]);


      log.debug("Row info: ${row_info}");

      def thisrow = [component:row, data:[]];
      def result_idx = 0;


      // This adds the columns to the row
      criterion.each { crit ->
        // If we're not yet at the end of the counts for this component, and this matches
        if ( ( result_idx<row_info.size() ) && ( crit.id == row_info.get(result_idx)[0] ) ) {
          log.debug("Adding data from db");
          thisrow.data.add(row_info.get(result_idx))
          result_idx++;
        }
        else {
          log.debug("No votes");
          thisrow.data.add([ crit.id, 0, 0, 0 ])
        }
      }

      log.debug("result ${thisrow}");
      rowdata.add(thisrow);
    }
    


    [
      criterion_heads: criterion_heads,
      criterion:criterion,
      rowdata:rowdata,
    ]


  }

}
