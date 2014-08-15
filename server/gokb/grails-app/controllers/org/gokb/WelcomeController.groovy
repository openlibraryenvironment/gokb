package org.gokb

import grails.util.GrailsNameUtils;

import org.gokb.cred.*;

class WelcomeController {

  def index() { 

    // The defaults for these widgets.
    def result=[:].withDefault {
      [
        xkey:'month',
        ykeys: ['total'],
        labels: ['Total'],
        hideHover: 'auto',
        resize: true,
        data:[]
      ]
    }

    def widgets = [
      'New Titles' : [
        'query'     : 'select count(p.id) from TitleInstance as p where p.dateCreated > ? and p.dateCreated < ?',
        'type'      : 'line',
        'lineColors': ['#FF0000']
      ],
      'New Orgs' : [
        'query'   : 'select count(p.id) from Org as p where p.dateCreated > ? and p.dateCreated < ?',
        'type'    : 'line',
        'lineColors': ['#0000FF']
      ],
      'New Packages' : [
        'query'     : 'select count(p.id) from Package as p where p.dateCreated > ? and p.dateCreated < ?',
        'type'      : 'line',
        'lineColors': ['#00FF00']
      ],
    ]

    Calendar calendar = Calendar.getInstance();
    int start_year = calendar.get(Calendar.YEAR) - 1;
    int start_month = calendar.get(Calendar.MONTH);

    // For each month in the past 12 months, execute each stat query defined in the month_queries array and stuff
    // the count for that stat in the matrix (Rows = stats, cols = months)
    widgets.each {widget_name, widget_data ->
      
      // The query.
      String q = widget_data.remove("query")
      
      result."${widget_name}" << (widget_data + [
        'element' : GrailsNameUtils.getPropertyName(widget_name),
      ])
      
      // Clear the calendar.
      calendar.clear();
      calendar.set(Calendar.MONTH, start_month);
      calendar.set(Calendar.YEAR, start_year);
      
      for ( int i=0; i<12; i++ ) {
        def period_start_date = calendar.getTime()
        calendar.add(Calendar.MONTH, 1)
        def period_end_date = calendar.getTime()
        
        log.debug("Finding ${widget_name} from ${period_start_date} to ${period_end_date}");
        result."${widget_name}".data.add([
          'month': "${calendar.get(Calendar.YEAR)}-${calendar.get(Calendar.MONTH)}",
          'total': KBComponent.executeQuery(q,[period_start_date, period_end_date])[0]
        ])
      }
    }

    log.debug("${result}")
    ["widgets" : result]  
  }
}
