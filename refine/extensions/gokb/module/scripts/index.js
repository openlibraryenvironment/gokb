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
GOKb.projectFilters = [
  {
    "name"    : "my-projects",
    "title"   : "My Projects",
    "filter"  : function (value, index) {
      return value[5].id == GOKb['core']['current-user']['id'];
    }
  }
];