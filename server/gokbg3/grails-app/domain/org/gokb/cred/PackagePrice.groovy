package org.gokb.cred

/**
 * PackagePrice - variant prices for a package based on different kinds of deal.
 * Allow a package to hold multiple variant prices - EG list price for a normal subscription, list price for 
 * perpetual access, list price for one off or top-up access.
 * Requirements derived from Jisc DAC project - See owen stephens for more info.
 */
class PackagePrice {

  Package owner
  RefdataValue priceType  // Examples are list, list-perpetual, list, list-topup, etc
  RefdataValue currency // Currency for price
  Date startDate
  Date endDate
  Float price

  static mapping = {
        owner column:'pp_owner_pkg_fk'
    priceType column:'pp_type_fk'
     currency column:'pp_currency_fk'
    startDate column:'pp_start_date'
      endDate column:'pp_end_date'
        price column:'pp_price'
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
