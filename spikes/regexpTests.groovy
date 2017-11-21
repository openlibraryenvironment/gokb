#!/usr/bin/groovy

// Groovy Scriptlet experimenting with distance measures
def embargo_regexp = /^([RP]\d+[DMY](;?))+$/


def testCases = [
  [ 'R10Y;P30D',true ], ['R10Y', true], ['R10Y;P30D;R10Y;P30Y', true]
]

testCases.each {
  print("test ${it[0]} == ${it[1]}  ")

  if ( ( it[0] ==~ embargo_regexp ) == it[1] ) {
    println("passed")
  }
  else {
    println("failed")
  }
}
