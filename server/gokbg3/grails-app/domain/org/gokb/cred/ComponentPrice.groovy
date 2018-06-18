package org.gokb.cred

/**
 * ComponentPrice - variant prices for a component based on different scenarios.
 * Allow a package/tipp to hold multiple variant prices - EG list price for a normal subscription, list price for 
 * perpetual access, list price for one off or top-up access.
 * Requirements derived from Jisc DAC project - See owen stephens for more info.
 */
class ComponentPrice {

  KBComponent owner
  RefdataValue priceType  // Examples are list, list-perpetual, list, list-topup, etc
  RefdataValue currency // Currency for price
  Date startDate
  Date endDate
  Float price

  static mapping = {
        owner column:'cp_owner_component_fk'
    priceType column:'cp_type_fk'
     currency column:'cp_currency_fk'
    startDate column:'cp_start_date'
      endDate column:'cp_end_date'
        price column:'cp_price'
  }

  static constraints = {
    owner(nullable:false)
    priceType(nullable:false, blank:true)
    currency(nullable:true, blank:true)
    startDate(nullable:false, blank:true)
    endDate(nullable:true, blank:true)
    price(nullable:true, blank:true)
  }

}
