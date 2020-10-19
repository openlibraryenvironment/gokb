[
  {
	// KBart rule
    "op": "core/text-transform",
    "description": "Text transform on cells in column date_first_issue_online using expression value.toDate()",
    "engineConfig": {
      "facets": [],
      "mode": "row-based"
    },
    "columnName": "date_first_issue_online",
    "expression": "value.toDate()",
    "onError": "keep-original",
    "repeat": false,
    "repeatCount": 10
  },
  {
	// KBart rule
    "op": "core/text-transform",
    "description": "Text transform on cells in column date_last_issue_online using expression value.toDate()",
    "engineConfig": {
      "facets": [],
      "mode": "row-based"
    },
    "columnName": "date_last_issue_online",
    "expression": "value.toDate()",
    "onError": "keep-original",
    "repeat": false,
    "repeatCount": 10
  },
  {
	// KBart rule
    "op": "core/column-addition",
    "description": "Create column platform.host.name at index 10 based on column title_url using expression grel:match(value,/http:\\/\\/.+\\.+(.+\\..+)\\/+.*/)[0]",
    "engineConfig": {
      "facets": [],
      "mode": "row-based"
    },
    "newColumnName": "platform.host.name",
    "columnInsertIndex": 10,
    "baseColumnName": "title_url",
    "expression": "grel:match(value,/http:\\/\\/.+\\.+(.+\\..+)\\/+.*/)[0]",
    "onError": "set-to-blank"
  },
  {
	// Could be generalised as a rule but better to think that this would be handled via a lookup/reconcilliation step in future
    "op": "core/mass-edit",
    "description": "Mass edit cells in column platform.host.name",
    "engineConfig": {
      "facets": [],
      "mode": "row-based"
    },
    "columnName": "platform.host.name",
    "expression": "value",
    "edits": [
      {
        "fromBlank": false,
        "fromError": false,
        "from": [
          "cambridge.org"
        ],
        "to": "Cambridge Journals"
      }
    ]
  },
  {
	// KBart rule
    "op": "core/column-rename",
    "description": "Rename column title_url to platform.host.url",
    "oldColumnName": "title_url",
    "newColumnName": "platform.host.url"
  },
  {
	// CUP rule, might be generalisable to KBart rule
    "op": "core/column-rename",
    "description": "Rename column title_id to TitleIdentifier.CUP",
    "oldColumnName": "title_id",
    "newColumnName": "TitleIdentifier.CUP"
  },
  {
	// KBart rule
    "op": "core/column-rename",
    "description": "Rename column publication_title to PublicationTitle",
    "oldColumnName": "publication_title",
    "newColumnName": "PublicationTitle"
  },
  {
	// Kbart rule
    "op": "core/column-rename",
    "description": "Rename column print_identifier to TitleIdentifier.ISSN",
    "oldColumnName": "print_identifier",
    "newColumnName": "TitleIdentifier.ISSN"
  },
  {
	// KBart rule
    "op": "core/column-rename",
    "description": "Rename column online_identifier to TitleIdentifier.eISSN",
    "oldColumnName": "online_identifier",
    "newColumnName": "TitleIdentifier.eISSN"
  },
  {
	// KBart rule
    "op": "core/column-rename",
    "description": "Rename column date_first_issue_online to DateFirstPackageIssue",
    "oldColumnName": "date_first_issue_online",
    "newColumnName": "DateFirstPackageIssue"
  },
  {
	// KBart rule
    "op": "core/column-rename",
    "description": "Rename column num_first_vol_online to VolumeFirstPackageIssue",
    "oldColumnName": "num_first_vol_online",
    "newColumnName": "VolumeFirstPackageIssue"
  },
  {
    "op": "core/column-rename",
    "description": "Rename column num_first_issue_online to NumberFirstPackageIssue",
    "oldColumnName": "num_first_issue_online",
    "newColumnName": "NumberFirstPackageIssue"
  },
  {
	// KBart rule
    "op": "core/column-rename",
    "description": "Rename column date_last_issue_online to DateLastPackageIssue",
    "oldColumnName": "date_last_issue_online",
    "newColumnName": "DateLastPackageIssue"
  },
  {
	// KBart rule
    "op": "core/column-rename",
    "description": "Rename column num_last_vol_online to VolumeLastPackageIssue",
    "oldColumnName": "num_last_vol_online",
    "newColumnName": "VolumeLastPackageIssue"
  },
  {
	// KBart rule
    "op": "core/column-rename",
    "description": "Rename column num_last_issue_online to NumberLastPackageIssue",
    "oldColumnName": "num_last_issue_online",
    "newColumnName": "NumberLastPackageIssue"
  },
  {
	// Empty column - perhaps not necessary anyway
    "op": "core/column-removal",
    "description": "Remove column first_author",
    "columnName": "first_author"
  },
  {
	// Empty column - perhaps not necessary anyway
    "op": "core/column-removal",
    "description": "Remove column embargo_info",
    "columnName": "embargo_info"
  },
  {
	// KBart rule
    "op": "core/column-rename",
    "description": "Rename column coverage_depth to CoverageDepth",
    "oldColumnName": "coverage_depth",
    "newColumnName": "CoverageDepth"
  },
  {
	// KBart rule
    "op": "core/column-rename",
    "description": "Rename column coverage_notes to CoverageNotes",
    "oldColumnName": "coverage_notes",
    "newColumnName": "CoverageNotes"
  },
  {
	// CUP Rule, but maybe bad assumption
    "op": "core/text-transform",
    "description": "Text transform on cells in column publisher_name using expression grel:\"Cambridge University Press\"",
    "engineConfig": {
      "facets": [],
      "mode": "row-based"
    },
    "columnName": "publisher_name",
    "expression": "grel:\"Cambridge University Press\"",
    "onError": "keep-original",
    "repeat": false,
    "repeatCount": 10
  },
  {
	// Empty column - perhaps no necessary anyway?
    "op": "core/column-removal",
    "description": "Remove column Column",
    "columnName": "Column"
  }
]
