package org.gokb

import grails.converters.JSON
import grails.util.GrailsNameUtils

class ChartTagLib {
  static namespace = 'gokb'
  static defaultEncodeAs = 'raw'
  
  def chart = {Map attr, body ->
    
    // Check to see if we have a type.
    def type = attr?.remove("type")
    if (type) {
      
      // Start to build up the js that will render our chart.
      String command = "Morris.${GrailsNameUtils.getClassName(type)}"
      
      // Now we need to ouput the html.
      out << "<div id='${attr.element}' class='gokb-chart ${attr.remove('class') ?: ''}' width='${attr.remove('width') ?: ''}' height='${attr.remove('height') ?: ''}' ></div>"
      
      // Now use the attributes as the options for the method.
      def json = new JSON(attr)
      json.use("deep")
      
      // Add the attributes to the json.
      command += "(${json.toString()})"
      
      out << "<script type='text/javascript'>\$(document).ready(function(){${command}});</script>"
      
    } else {
      out << "Chart tag is missing required attribute 'type'."
    }
  }
}
