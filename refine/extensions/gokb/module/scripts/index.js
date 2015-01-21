/**
 * Set the nocache value for all jQuery ajax requests.
 */
$.ajaxSetup({cache:false});

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
 * Predefined filters that will render as tabs.
 */
GOKb.projectFilters = {
};