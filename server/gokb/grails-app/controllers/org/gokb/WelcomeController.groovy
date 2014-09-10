package org.gokb

import grails.util.GrailsNameUtils

import org.gokb.cred.*
import org.hibernate.SessionFactory
import org.hibernate.transform.AliasToEntityMapResultTransformer

class WelcomeController {
  
  SessionFactory sessionFactory

  def index() { 

    // The defaults for these widgets.
    def result=[:].withDefault {
      [
        xkey:'month',
        hideHover: 'auto',
        resize: true,
        
      ].withDefault {
        []
      }
    }

    def widgets = [
      'Titles' : [
        'type'      : 'line',
        'datasets'  : [
          [
            'query'     : 'select count(p.id) as titlesnew from TitleInstance as p where p.dateCreated > :startdate and p.dateCreated < :enddate',
            'ykey'      : 'titlesnew',
            'label'     : 'New Titles',
            'lineColor' : '#FF0000'
          ],[
            'query'     : 'select count(p.id) as titlesall from TitleInstance as p where p.dateCreated < :enddate',
            'ykey'      : 'titlesall',
            'label'     : 'Total Titles',
            'lineColor' : '#0000FF'
          ]
        ]
      ],
      'Organizations' : [
        'type'      : 'line',
        'datasets'  : [
          [
            'query'     : 'select count(p.id) as orgsnew from Org as p where p.dateCreated > :startdate and p.dateCreated < :enddate',
            'ykey'      : 'orgsnew',
            'label'     : 'New Organizations',
            'lineColor' : '#FF0000'
          ],[
            'query'     : 'select count(p.id) as orgsall from Org as p where p.dateCreated < :enddate',
            'ykey'      : 'orgsall',
            'label'     : 'Total Organizations',
            'lineColor' : '#0000FF'
          ]
        ]
      ],
      'Packages' : [
        'type'      : 'line',
        'datasets'  : [
          [
            'query'     : 'select count(p.id) as pkgsnew from Package as p where p.dateCreated > :startdate and p.dateCreated < :enddate',
            'ykey'      : 'pkgsnew',
            'label'     : 'New Packages',
            'lineColor' : '#FF0000'
          ],[
            'query'     : 'select count(p.id) as pkgsall from Package as p where p.dateCreated < :enddate',
            'ykey'      : 'pkgsall',
            'label'     : 'Total Packages',
            'lineColor' : '#0000FF'
          ]
        ]
      ],
    ]

    // The calendar for the queries.
    Calendar calendar = Calendar.getInstance()
    int start_year = calendar.get(Calendar.YEAR) - 1
    int start_month = calendar.get(Calendar.MONTH)

    // For each month in the past 12 months, execute each stat query defined in the month_queries array and stuff
    // the count for that stat in the matrix (Rows = stats, cols = months)
    widgets.each {widget_name, widget_data ->
        
      // Widget data.
      def wData = [:].withDefault {
        [:].withDefault { [] }
      }
      
      // The datasets.
      def ds = widget_data.remove("datasets")
      
      result."${widget_name}" << (widget_data + [
        'element' : GrailsNameUtils.getPropertyName(widget_name),
      ])
      
      ds.each { Map d ->
        
        String q = d.remove("query")        
        
        // Clear the calendar.
        calendar.clear();
        calendar.set(Calendar.MONTH, start_month);
        calendar.set(Calendar.YEAR, start_year);
        
        for ( int i=0; i<12; i++ ) {
          def period_start_date = calendar.getTime()
          calendar.add(Calendar.MONTH, 1)
          def period_end_date = calendar.getTime()
  
          def query_params = [:]
          if ( q.contains(':startdate') ) {
            query_params.startdate = period_start_date
          }
          if ( q.contains(':enddate') ) {
            query_params.enddate = period_end_date
          }
          
          log.debug("Finding ${widget_name} from ${period_start_date} to ${period_end_date}")
          
          // Merge in the singles into their plural container.
          d.each { String pName, pVal ->
            String plural = "${pName}s"
            if (!result."${widget_name}"."${plural}".contains(pVal)) {
              result."${widget_name}"."${plural}" << pVal
            } 
          }
          
          // Execute the query directly with Hibernate so we can just get a list of Maps.
          List<Map> qres = sessionFactory.getCurrentSession().createQuery(q).with {
            setProperties(query_params)
            setReadOnly(true)
            setResultTransformer (AliasToEntityMapResultTransformer.INSTANCE)
            list()
          }
          
          // xkey and ykey.
          String xkey = result."${widget_name}"."xkey"
          def xVal = "${calendar.get(Calendar.YEAR)}-${calendar.get(Calendar.MONTH)}"
          String ykey = d."ykey"
          
          wData."${xVal}" << [
            "${xkey}" : "${xVal}",
            "${ykey}" : qres[0]."${ykey}"
          ]
        }
      }
      
      // Add the results.
      result."${widget_name}"."data" = wData.values()
    }

    log.debug("${result}")
    ["widgets" : result]  
  }
}
