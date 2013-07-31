includeTargets << grailsScript("_GrailsDocs")
 
target(main: "Generate the property matrix for KBComponents") {
    //make sure they all compile
    depends(compile)
 
    depends(docs)
}
 
setDefaultTarget(main)
