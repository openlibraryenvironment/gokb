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