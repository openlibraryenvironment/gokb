package com.k_int.gokb.refine.functions;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Properties;

import org.json.JSONException;
import org.json.JSONWriter;

import com.google.refine.grel.ControlFunctionRegistry;
import com.google.refine.grel.Function;

public class DateCeiling implements Function {

  private static final String REGEX_YEAR_ONLY = "^\\d{4}$";
  private static final String REGEX_YEAR_MONTH_ONLY = "^\\d{4}\\-\\d{2}$";

    @Override
    public Object call(Properties bindings, Object[] args) {
      
      // Get the registered toDate() function and call it.
      Object val = ControlFunctionRegistry.getFunction("toDate").call(bindings, args);
      
      // Either Calendar or date returned?
      if (val instanceof Date) {
        GregorianCalendar cal = new GregorianCalendar();
        cal.setTime((Date) val);
        val = cal;
      }
      if (!(val instanceof Calendar)) {
        return val;
      } else if (! (val instanceof GregorianCalendar)) {
        GregorianCalendar cal = new GregorianCalendar ();
        cal.setTime(((Calendar) val).getTime());
        val = cal;
      }
      
      // We know we have a date here now.
      GregorianCalendar cal = (GregorianCalendar)val;
      
      // The first arg could only have been a number or String at this point so let's ensure a string.
      String input = ("" + args[0]).trim();
      
      // We'll now test for certain criteria.
      if (input.matches(REGEX_YEAR_ONLY)) {
        
        // Assume only year was supplied. Move to the last day of the current calendar.
        cal.set(Calendar.MONTH, cal.getActualMaximum(Calendar.MONTH));
        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH));
      } else if (input.matches(REGEX_YEAR_MONTH_ONLY)) {
        
        // Assume the year and month.
        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH));
      }
      
      return cal;
    }


    @Override
    public void write(JSONWriter writer, Properties options)
            throws JSONException {

        writer.object();
        writer.key("description"); writer.value("Tries to parse the supplied String value as a date and then returns the last date of the month or year depending on what was supplied.");
        writer.key("params"); writer.value("String input");
        writer.key("returns"); writer.value("date.");
        writer.endObject();
    }
}
