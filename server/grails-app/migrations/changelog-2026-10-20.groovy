databaseChangeLog = {

    changeSet(author: "horn (generated)", id: "1792498175515-5") {
        dropForeignKeyConstraint(baseTableName: "kbcomponent", constraintName: "fkaaq852543txi3egdxhxaot0qv")
    }

    changeSet(author: "horn (generated)", id: "1792498175515-6") {
        dropColumn(columnName: "kbc_duplicate_of", tableName: "kbcomponent")
    }

    changeSet(author: "horn (generated)", id: "1792498175515-1") {
        addNotNullConstraint(columnDataType: "bigint", columnName: "tipp_host_platform_fk", tableName: "title_instance_package_platform", validate: "true")
    }

    changeSet(author: "horn (generated)", id: "1792498175515-2") {
        addNotNullConstraint(columnDataType: "bigint", columnName: "tipp_pkg_fk", tableName: "title_instance_package_platform", validate: "true")
    }
}
