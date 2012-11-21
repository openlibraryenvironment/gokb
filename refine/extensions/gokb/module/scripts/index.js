/**
 * Get Projects.
 */
GOKb.api.getProjects = function (params, callbacks) {
	GOKb.doCommand (
    "projectList",
    params,
    null,
    callbacks
  );
};

/**
 * Checkout project.
 */
GOKb.checkoutProject = function (params, callbacks) {
	GOKb.doRefineCommand (
    "gokb/project-checkout",
    params,
    null,
    callbacks
  );
};