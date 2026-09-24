databaseChangeLog = {
    // Merged after initial split from g5_master

    changeSet(author: "horn (generated)", id: "1790176327340-12") {
        createTable(tableName: "scheduled_job_control") {
            column(autoIncrement: "true", name: "id", type: "BIGINT") {
                constraints(nullable: "false", primaryKey: "true", primaryKeyName: "scheduled_job_controlPK")
            }

            column(name: "version", type: "BIGINT")

            column(name: "last_end", type: "TIMESTAMP WITHOUT TIME ZONE")

            column(name: "last_start_complete", type: "TIMESTAMP WITHOUT TIME ZONE")

            column(name: "last_end_complete", type: "TIMESTAMP WITHOUT TIME ZONE")

            column(name: "last_start", type: "TIMESTAMP WITHOUT TIME ZONE")

            column(name: "job_type_id", type: "BIGINT")
        }
    }

    changeSet(author: "horn (generated)", id: "1790176327340-81") {
        addForeignKeyConstraint(baseColumnNames: "job_type_id", baseTableName: "scheduled_job_control", constraintName: "FKek6dhfe48uxwpm3bnspq57g1x", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "rdv_id", referencedTableName: "refdata_value", validate: "true")
    }

    changeSet(author: "horn (generated)", id: "1790176327340-21") {
        addColumn(tableName: "source") {
            column(name: "last_import_file_date", type: "date")
        }
    }

    changeSet(author: "horn (generated)", id: "1790176327340-66") {
        createIndex(indexName: "tcs_owner_idx", tableName: "tippcoverage_statement") {
            column(name: "owner_id")
        }
    }

    // New join tables for manyByCombo entries

    // KBComponent.fileAttachments

    changeSet(author: "horn (generated)", id: "1790176327340-7") {
        createTable(tableName: "component_attachment") {
            column(autoIncrement: "true", name: "ca_id", type: "BIGINT") {
                constraints(nullable: "false", primaryKey: "true", primaryKeyName: "component_attachmentPK")
            }

            column(name: "version", type: "BIGINT")

            column(name: "ca_file_fk", type: "BIGINT") {
                constraints(nullable: "false")
            }

            column(name: "ca_date_created", type: "TIMESTAMP WITHOUT TIME ZONE")

            column(name: "ca_import_name", type: "VARCHAR(255)")

            column(name: "ca_last_updated", type: "TIMESTAMP WITHOUT TIME ZONE")

            column(name: "ca_comp_fk", type: "BIGINT") {
                constraints(nullable: "false")
            }
        }
    }

    changeSet(author: "horn (generated)", id: "1790176327340-79") {
        addForeignKeyConstraint(baseColumnNames: "ca_file_fk", baseTableName: "component_attachment", constraintName: "FKc38c3ayuxcpux17992otu5usb", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "kbc_id", referencedTableName: "data_file", validate: "true")
    }

    changeSet(author: "horn (generated)", id: "1790176327340-88") {
        addForeignKeyConstraint(baseColumnNames: "ca_comp_fk", baseTableName: "component_attachment", constraintName: "FKmjt67w45gn07s55jdlrpvg5ai", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "kbc_id", referencedTableName: "kbcomponent", validate: "true")
    }

    changeSet(author: "horn (generated)", id: "1790176327340-40") {
        createIndex(indexName: "ca_cmp_idx", tableName: "component_attachment") {
            column(name: "ca_comp_fk")
        }
    }

    changeSet(author: "horn (generated)", id: "1790176327340-41") {
        createIndex(indexName: "ca_created_idx", tableName: "component_attachment") {
            column(name: "ca_date_created")
        }
    }

    changeSet(author: "horn (generated)", id: "1790176327340-42") {
        createIndex(indexName: "ca_file_idx", tableName: "component_attachment") {
            column(name: "ca_file_fk")
        }
    }

    changeSet(author: "horn (generated)", id: "1790176327340-43") {
        createIndex(indexName: "ca_full_idx", tableName: "component_attachment") {
            column(name: "ca_file_fk")

            column(name: "ca_comp_fk")
        }
    }

    changeSet(author: "horn (generated)", id: "1790176327340-197") {
        grailsChange {
            change {
                int countUpdate = 0

                def ca_combos = sql.rows('''select combo_to_fk,
                        combo_from_fk,
                        date_created,
                        last_updated from combo
                        where combo_type_rv_fk = (
                            select rdv_id from refdata_value
                            where rdv_owner = (
                                SELECT rdc_id FROM refdata_category
                                WHERE rdc_description = 'Combo.Type'
                            )
                            and rdv_value = 'KBComponent.FileAttachments'
                        );''')


                combos.each {
                    countUpdate++
                    sql.execute("""insert into component_attachment(
                                ca_id,
                                version,
                                ca_date_created,
                                ca_last_updated,
                                ca_comp_fk,
                                ca_file_fk
                            )
                            values (
                                (select nextval ('hibernate_sequence')),
                                0,
                                ${it.date_created},
                                ${it.last_updated},
                                ${it.combo_from_fk},
                                ${it.combo_to_fk}
                            );""")
                }

                confirm("insert combo into component_attachment: ${countUpdate}")
                changeSet.setComments("insert combo into component_attachment: ${countUpdate}")
            }
            rollback {}
        }
    }

    // Curatory Groups

    changeSet(author: "horn (generated)", id: "1790176327340-9") {
        createTable(tableName: "org_curatory_group") {
            column(autoIncrement: "true", name: "cgo_id", type: "BIGINT") {
                constraints(nullable: "false", primaryKey: "true", primaryKeyName: "org_curatory_groupPK")
            }

            column(name: "version", type: "BIGINT")

            column(name: "cgo_org_fk", type: "BIGINT") {
                constraints(nullable: "false")
            }

            column(name: "cgo_date_created", type: "TIMESTAMP WITHOUT TIME ZONE")

            column(name: "cgo_last_updated", type: "TIMESTAMP WITHOUT TIME ZONE")

            column(name: "cgo_group_fk", type: "BIGINT") {
                constraints(nullable: "false")
            }
        }
    }

    changeSet(author: "horn (generated)", id: "1790176327340-93") {
        addForeignKeyConstraint(baseColumnNames: "cgo_org_fk", baseTableName: "org_curatory_group", constraintName: "FKp82wqy7225qpufto31ncoe8gy", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "kbc_id", referencedTableName: "org", validate: "true")
    }

    changeSet(author: "horn (generated)", id: "1790176327340-101") {
        addForeignKeyConstraint(baseColumnNames: "cgo_group_fk", baseTableName: "org_curatory_group", constraintName: "FKs5965tqnomtuvh08lmstyll47", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "kbc_id", referencedTableName: "curatory_group", validate: "true")
    }

    changeSet(author: "horn (generated)", id: "1790176327340-44") {
        createIndex(indexName: "cgo_cmp_idx", tableName: "org_curatory_group") {
            column(name: "cgo_org_fk")
        }
    }

    changeSet(author: "horn (generated)", id: "1790176327340-45") {
        createIndex(indexName: "cgo_created_idx", tableName: "org_curatory_group") {
            column(name: "cgo_date_created")
        }
    }

    changeSet(author: "horn (generated)", id: "1790176327340-46") {
        createIndex(indexName: "cgo_file_idx", tableName: "org_curatory_group") {
            column(name: "cgo_group_fk")
        }
    }

    changeSet(author: "horn (generated)", id: "1790176327340-47") {
        createIndex(indexName: "cgo_full_idx", tableName: "org_curatory_group") {
            column(name: "cgo_org_fk")

            column(name: "cgo_group_fk")
        }
    }

    changeSet(author: "horn (generated)", id: "1790176327340-200") {
        grailsChange {
            change {
                int countUpdate = 0

                def ca_combos = sql.rows('''select combo_to_fk,
                        combo_from_fk,
                        date_created,
                        last_updated
                        from combo
                        where combo_type_rv_fk = (
                            select rdv_id from refdata_value
                            where rdv_owner = (
                                SELECT rdc_id FROM refdata_category
                                WHERE rdc_description = 'Combo.Type'
                            )
                            and rdv_value = 'Org.CuratoryGroups'
                        );''')


                combos.each {
                    countUpdate++
                    sql.execute("""insert into org_curatory_group(
                                cgo_id,
                                version,
                                cgo_date_created,
                                cgo_last_updated,
                                cgo_org_fk,
                                cgo_group_fk
                            )
                            values (
                                (select nextval ('hibernate_sequence')),
                                0,
                                ${it.date_created},
                                ${it.last_updated},
                                ${it.combo_from_fk},
                                ${it.combo_to_fk}
                            );""")
                }

                confirm("insert combo into org_curatory_group: ${countUpdate}")
                changeSet.setComments("insert combo into org_curatory_group: ${countUpdate}")
            }
            rollback {}
        }
    }

    changeSet(author: "horn (generated)", id: "1790176327340-10") {
        createTable(tableName: "package_curatory_group") {
            column(autoIncrement: "true", name: "cgpa_id", type: "BIGINT") {
                constraints(nullable: "false", primaryKey: "true", primaryKeyName: "package_curatory_groupPK")
            }

            column(name: "version", type: "BIGINT")

            column(name: "cgpa_date_created", type: "TIMESTAMP WITHOUT TIME ZONE")

            column(name: "cgpa_last_updated", type: "TIMESTAMP WITHOUT TIME ZONE")

            column(name: "cgpa_pkg_fk", type: "BIGINT") {
                constraints(nullable: "false")
            }

            column(name: "cgpa_group_fk", type: "BIGINT") {
                constraints(nullable: "false")
            }
        }
    }

    changeSet(author: "horn (generated)", id: "1790176327340-83") {
        addForeignKeyConstraint(baseColumnNames: "cgpa_group_fk", baseTableName: "package_curatory_group", constraintName: "FKfkqb92gewn9n4yx8dwwh3p45k", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "kbc_id", referencedTableName: "curatory_group", validate: "true")
    }

    changeSet(author: "horn (generated)", id: "1790176327340-87") {
        addForeignKeyConstraint(baseColumnNames: "cgpa_pkg_fk", baseTableName: "package_curatory_group", constraintName: "FKkj6npttab585hix3aa45so7pj", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "kbc_id", referencedTableName: "package", validate: "true")
    }

    changeSet(author: "horn (generated)", id: "1790176327340-48") {
        createIndex(indexName: "cgpa_cmp_idx", tableName: "package_curatory_group") {
            column(name: "cgpa_pkg_fk")
        }
    }

    changeSet(author: "horn (generated)", id: "1790176327340-49") {
        createIndex(indexName: "cgpa_created_idx", tableName: "package_curatory_group") {
            column(name: "cgpa_date_created")
        }
    }

    changeSet(author: "horn (generated)", id: "1790176327340-50") {
        createIndex(indexName: "cgpa_file_idx", tableName: "package_curatory_group") {
            column(name: "cgpa_group_fk")
        }
    }

    changeSet(author: "horn (generated)", id: "1790176327340-51") {
        createIndex(indexName: "cgpa_full_idx", tableName: "package_curatory_group") {
            column(name: "cgpa_group_fk")

            column(name: "cgpa_pkg_fk")
        }
    }

    changeSet(author: "horn (generated)", id: "1790176327340-201") {
        grailsChange {
            change {
                int countUpdate = 0

                def ca_combos = sql.rows('''select combo_to_fk,
                        combo_from_fk,
                        date_created,
                        last_updated
                        from combo
                        where combo_type_rv_fk = (
                            select rdv_id from refdata_value
                            where rdv_owner = (
                                SELECT rdc_id FROM refdata_category
                                WHERE rdc_description = 'Combo.Type'
                            )
                            and rdv_value = 'Package.CuratoryGroups'
                        );''')


                combos.each {
                    countUpdate++
                    sql.execute("""insert into package_curatory_group(
                                cgpa_id,
                                version,
                                cgpa_date_created,
                                cgpa_last_updated,
                                cgpa_pkg_fk,
                                cgpa_group_fk
                            )
                            values (
                                (select nextval ('hibernate_sequence')),
                                0,
                                ${it.date_created},
                                ${it.last_updated},
                                ${it.combo_from_fk},
                                ${it.combo_to_fk}
                            );""")
                }

                confirm("insert combo into package_curatory_group: ${countUpdate}")
                changeSet.setComments("insert combo into package_curatory_group: ${countUpdate}")
            }
            rollback {}
        }
    }

    changeSet(author: "horn (generated)", id: "1790176327340-11") {
        createTable(tableName: "platform_curatory_group") {
            column(autoIncrement: "true", name: "plcg_id", type: "BIGINT") {
                constraints(nullable: "false", primaryKey: "true", primaryKeyName: "platform_curatory_groupPK")
            }

            column(name: "version", type: "BIGINT")

            column(name: "plcg_date_created", type: "TIMESTAMP WITHOUT TIME ZONE")

            column(name: "plcg_last_updated", type: "TIMESTAMP WITHOUT TIME ZONE")

            column(name: "plcg_platform_fk", type: "BIGINT") {
                constraints(nullable: "false")
            }

            column(name: "plcg_group_fk", type: "BIGINT") {
                constraints(nullable: "false")
            }
        }
    }

    changeSet(author: "horn (generated)", id: "1790176327340-80") {
        addForeignKeyConstraint(baseColumnNames: "plcg_group_fk", baseTableName: "platform_curatory_group", constraintName: "FKe47s7b01qxo1mfqdqklxyrddg", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "kbc_id", referencedTableName: "curatory_group", validate: "true")
    }

    changeSet(author: "horn (generated)", id: "1790176327340-100") {
        addForeignKeyConstraint(baseColumnNames: "plcg_platform_fk", baseTableName: "platform_curatory_group", constraintName: "FKs4fpld0tq6hxpb9gy63b7y4hv", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "kbc_id", referencedTableName: "platform", validate: "true")
    }

    changeSet(author: "horn (generated)", id: "1790176327340-62") {
        createIndex(indexName: "plcg_cmp_idx", tableName: "platform_curatory_group") {
            column(name: "plcg_platform_fk")
        }
    }

    changeSet(author: "horn (generated)", id: "1790176327340-63") {
        createIndex(indexName: "plcg_created_idx", tableName: "platform_curatory_group") {
            column(name: "plcg_date_created")
        }
    }

    changeSet(author: "horn (generated)", id: "1790176327340-64") {
        createIndex(indexName: "plcg_file_idx", tableName: "platform_curatory_group") {
            column(name: "plcg_group_fk")
        }
    }

    changeSet(author: "horn (generated)", id: "1790176327340-65") {
        createIndex(indexName: "plcg_full_idx", tableName: "platform_curatory_group") {
            column(name: "plcg_group_fk")

            column(name: "plcg_platform_fk")
        }
    }

    changeSet(author: "horn (generated)", id: "1790176327340-202") {
        grailsChange {
            change {
                int countUpdate = 0

                def ca_combos = sql.rows('''select combo_to_fk,
                        combo_from_fk,
                        date_created,
                        last_updated
                        from combo
                        where combo_type_rv_fk = (
                            select rdv_id from refdata_value
                            where rdv_owner = (
                                SELECT rdc_id FROM refdata_category
                                WHERE rdc_description = 'Combo.Type'
                            )
                            and rdv_value = 'Platform.CuratoryGroups'
                        );''')


                combos.each {
                    countUpdate++
                    sql.execute("""insert into platform_curatory_group(
                                plcg_id,
                                version,
                                plcg_date_created,
                                plcg_last_updated,
                                plcg_platform_fk,
                                plcg_group_fk
                            )
                            values (
                                (select nextval ('hibernate_sequence')),
                                0,
                                ${it.date_created},
                                ${it.last_updated},
                                ${it.combo_from_fk},
                                ${it.combo_to_fk}
                            );""")
                }

                confirm("insert combo into platform_curatory_group: ${countUpdate}")
                changeSet.setComments("insert combo into platform_curatory_group: ${countUpdate}")
            }
            rollback {}
        }
    }

    changeSet(author: "horn (generated)", id: "1790176327340-13") {
        createTable(tableName: "source_curatory_group") {
            column(autoIncrement: "true", name: "cgs_id", type: "BIGINT") {
                constraints(nullable: "false", primaryKey: "true", primaryKeyName: "source_curatory_groupPK")
            }

            column(name: "version", type: "BIGINT")

            column(name: "cgs_date_created", type: "TIMESTAMP WITHOUT TIME ZONE")

            column(name: "cgs_last_updated", type: "TIMESTAMP WITHOUT TIME ZONE")

            column(name: "cgs_group_fk", type: "BIGINT") {
                constraints(nullable: "false")
            }

            column(name: "cgs_src_fk", type: "BIGINT") {
                constraints(nullable: "false")
            }
        }
    }

    changeSet(author: "horn (generated)", id: "1790176327340-77") {
        addForeignKeyConstraint(baseColumnNames: "cgs_group_fk", baseTableName: "source_curatory_group", constraintName: "FKai5s1bpe2ti6mvgjuhvrngtl", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "kbc_id", referencedTableName: "curatory_group", validate: "true")
    }

    changeSet(author: "horn (generated)", id: "1790176327340-90") {
        addForeignKeyConstraint(baseColumnNames: "cgs_src_fk", baseTableName: "source_curatory_group", constraintName: "FKn5tygq3nnacpwaj2txkfhusps", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "kbc_id", referencedTableName: "source", validate: "true")
    }

    changeSet(author: "horn (generated)", id: "1790176327340-52") {
        createIndex(indexName: "cgs_cmp_idx", tableName: "source_curatory_group") {
            column(name: "cgs_src_fk")
        }
    }

    changeSet(author: "horn (generated)", id: "1790176327340-53") {
        createIndex(indexName: "cgs_created_idx", tableName: "source_curatory_group") {
            column(name: "cgs_date_created")
        }
    }

    changeSet(author: "horn (generated)", id: "1790176327340-54") {
        createIndex(indexName: "cgs_file_idx", tableName: "source_curatory_group") {
            column(name: "cgs_group_fk")
        }
    }

    changeSet(author: "horn (generated)", id: "1790176327340-55") {
        createIndex(indexName: "cgs_full_idx", tableName: "source_curatory_group") {
            column(name: "cgs_group_fk")

            column(name: "cgs_src_fk")
        }
    }

    changeSet(author: "horn (generated)", id: "1790176327340-203") {
        grailsChange {
            change {
                int countUpdate = 0

                def ca_combos = sql.rows('''select combo_to_fk,
                                            combo_from_fk,
                                            date_created,
                                            last_updated
                                            from combo
                                            where combo_type_rv_fk = (
                                                select rdv_id from refdata_value
                                                where rdv_owner = (
                                                    SELECT rdc_id FROM refdata_category
                                                    WHERE rdc_description = 'Combo.Type'
                                                )
                                                and rdv_value = 'Source.CuratoryGroups'
                                            );''')


                combos.each {
                    countUpdate++
                    sql.execute("""insert into source_curatory_group(
                                        cgs_id,
                                        version,
                                        cgs_date_created,
                                        cgs_last_updated,
                                        cgs_src_fk,
                                        cgs_group_fk
                                    )
                                    values (
                                        (select nextval ('hibernate_sequence')),
                                        0,
                                        ${it.date_created},
                                        ${it.last_updated},
                                        ${it.combo_from_fk},
                                        ${it.combo_to_fk}
                                    );""")
                }

                confirm("insert combo into source_curatory_group: ${countUpdate}")
                changeSet.setComments("insert combo into source_curatory_group: ${countUpdate}")
            }
            rollback {}
        }
    }

    // TitleInstance

    changeSet(author: "horn (generated)", id: "1790176327340-14") {
        createTable(tableName: "title_publisher") {
            column(autoIncrement: "true", name: "tp_id", type: "BIGINT") {
                constraints(nullable: "false", primaryKey: "true", primaryKeyName: "title_publisherPK")
            }

            column(name: "version", type: "BIGINT")

            column(name: "tp_date_created", type: "TIMESTAMP WITHOUT TIME ZONE")

            column(name: "tp_last_updated", type: "TIMESTAMP WITHOUT TIME ZONE")

            column(name: "tp_title_fk", type: "BIGINT") {
                constraints(nullable: "false")
            }

            column(name: "tp_publisher_fk", type: "BIGINT") {
                constraints(nullable: "false")
            }

            column(name: "tp_start_date", type: "date")

            column(name: "tp_end_date", type: "date")

            column(name: "tp_status_rv_fk", type: "BIGINT")
        }
    }

    changeSet(author: "horn (generated)", id: "1790176327340-82") {
        addForeignKeyConstraint(baseColumnNames: "tp_title_fk", baseTableName: "title_publisher", constraintName: "FKems8ol2vg8slidxnr890dtggi", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "kbc_id", referencedTableName: "title_instance", validate: "true")
    }

    changeSet(author: "horn (generated)", id: "1790176327340-84") {
        addForeignKeyConstraint(baseColumnNames: "tp_status_rv_fk", baseTableName: "title_publisher", constraintName: "FKg58mi1bmteodrroditud93yqb", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "rdv_id", referencedTableName: "refdata_value", validate: "true")
    }

    changeSet(author: "horn (generated)", id: "1790176327340-85") {
        addForeignKeyConstraint(baseColumnNames: "tp_publisher_fk", baseTableName: "title_publisher", constraintName: "FKg798cqjv1kfxebhu4f7tmwq58", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "kbc_id", referencedTableName: "org", validate: "true")
    }

    changeSet(author: "horn (generated)", id: "1790176327340-67") {
        createIndex(indexName: "tp_created_idx", tableName: "title_publisher") {
            column(name: "tp_date_created")
        }
    }

    changeSet(author: "horn (generated)", id: "1790176327340-68") {
        createIndex(indexName: "tp_full_idx", tableName: "title_publisher") {
            column(name: "tp_publisher_fk")

            column(name: "tp_title_fk")

            column(name: "tp_status_rv_fk")
        }
    }

    changeSet(author: "horn (generated)", id: "1790176327340-69") {
        createIndex(indexName: "tp_pub_idx", tableName: "title_publisher") {
            column(name: "tp_publisher_fk")

            column(name: "tp_status_rv_fk")
        }
    }

    changeSet(author: "horn (generated)", id: "1790176327340-70") {
        createIndex(indexName: "tp_ttl_idx", tableName: "title_publisher") {
            column(name: "tp_title_fk")

            column(name: "tp_status_rv_fk")
        }
    }

    changeSet(author: "horn (generated)", id: "1790176327340-204") {
        grailsChange {
            change {
                sql.execute("""insert into refdata_category(
                            rdc_id,
                            rdc_version,
                            rdc_description,
                            rdc_label
                        )
                        values (
                            (select nextval ('hibernate_sequence')),
                            0,
                            'TitlePublisher.Status',
                            'TitlePublisher.Status'
                        );""")
            }
            rollback {}
        }
    }

    changeSet(author: "horn (generated)", id: "1790176327340-205") {
        grailsChange {
            change {
                def new_category_id = sql.rows("select rdc_id from refdata_category where rdc_label = 'TitlePublisher.Status';")[0].rdc_id

                def old_status_vals = sql.execute("""select * from refdata_value
                        where rdv_owner = (
                            select rdc_id from refdata_category
                            where rdc_label = 'Combo.Status'
                        );""")

                old_status_vals.each {
                    sql.execute("""insert into refdata_value(
                                rdv_id,
                                rdv_version,
                                rdv_value,
                                rdv_owner,
                                rdv_sortkey
                            )
                            values (
                                (select nextval ('hibernate_sequence')),
                                0,
                                ${it.rdv_value},
                                ${new_category_id},
                                ${it.rdv_sortkey}
                            );""")
                }

                int countUpdate = 0

                def combos = sql.rows('''select * from combo
                        where combo_type_rv_fk = (
                            select rdv_id from refdata_value
                            where rdv_owner = (
                                SELECT rdc_id FROM refdata_category
                                WHERE rdc_description = 'Combo.Type'
                            )
                            and rdv_value = 'TitleInstance.Publisher'
                        );''')


                combos.each {
                    countUpdate++
                    sql.execute("""insert into title_publisher(
                                tp_id,
                                version,
                                tp_date_created,
                                tp_last_updated,
                                tp_title_fk,
                                tp_publisher_fk,
                                tp_start_date,
                                tp_end_date,
                                tp_status_rv_fk
                            )
                            values (
                                (select nextval ('hibernate_sequence')),
                                0,
                                ${it.date_created},
                                ${it.last_updated},
                                ${it.combo_from_fk},
                                ${it.combo_to_fk},
                                ${it.getDate('combo_start_date')},
                                ${it.getDate('combo_end_date')},
                                (
                                    select rdv_id from refdata_value
                                    where rdv_owner = (
                                        select rdc_id from refdata_category
                                        where rdc_id = ${new_category_id}
                                    )
                                    and rdv_value = (
                                        select rdv_value from refdata_value
                                        where rdv_id = ${it.combo_status_rv_fk}
                                    )
                                )
                            );""")
                }

                confirm("insert combo into title_publisher: ${countUpdate}")
                changeSet.setComments("insert combo into title_publisher: ${countUpdate}")
            }
            rollback {}
        }
    }

    // Identifiers

    changeSet(author: "horn (generated)", id: "1790176327340-145") {
        dropForeignKeyConstraint(baseTableName: "identifier", constraintName: "fkphbws3fisbobrrirar6y1x3fo")
    }

    changeSet(author: "horn (generated)", id: "1790176327340-174") {
        renameColumn(oldColumnName: "kbc_id", newColumnName: "id", tableName: "identifier")
    }

    changeSet(author: "horn (generated)", id: "1790176327340-16") {
        addColumn(tableName: "identifier") {
            column(name: "id_date_created", type: "timestamp")
        }
    }

    changeSet(author: "horn (generated)", id: "1790176327340-17") {
        addColumn(tableName: "identifier") {
            column(name: "id_last_updated", type: "timestamp")
        }
    }

    changeSet(author: "horn (generated)", id: "1790176327340-18") {
        addColumn(tableName: "identifier") {
            column(name: "id_normname", type: "varchar(2048)")
        }
    }

    changeSet(author: "horn (generated)", id: "1790176327340-19") {
        addColumn(tableName: "identifier") {
            column(name: "id_uuid", type: "varchar(255)")
        }
    }


    changeSet(author: "horn (generated)", id: "1790176327340-38") {
        addColumn(tableName: "identifier") {
            column(name: "version", type: "int8")
        }
    }


    changeSet(author: "horn (generated)", id: "1790176327340-60") {
        createIndex(indexName: "id_normname_idx", tableName: "identifier") {
            column(name: "id_normname")
        }
    }

    changeSet(author: "horn (generated)", id: "1790176327340-61") {
        createIndex(indexName: "id_uuid_idx", tableName: "identifier") {
            column(name: "id_uuid")
        }
    }

    changeSet(author: "horn (generated)", id: "1790176327340-198") {
        grailsChange {
            change {
                Integer countUpdate = 0

                def id_kbc_info = sql.rows('''select kbc_id,
                        kbc_date_created,
                        kbc_last_updated,
                        kbc_normname,
                        kbc_uuid
                        from kbcomponent
                        where exists (
                            select 1 from identifier
                            where id = kbc_id
                        );''')

                id_kbc_info.each {
                    countUpdate++
                    sql.execute("""update identifier
                            set version = 0,
                            id_date_created = ${it.date_created},
                            ca_last_updated = ${it.last_updated},
                            id_normname = ${it.kbc_normname},
                            id_uuid = ${it.kbc_uuid}
                            where id = ${it.kbc_id};""")
                }

                confirm("transfer fields to identifier: ${countUpdate}")
                changeSet.setComments("transfer fields to identifier: ${countUpdate}")
            }
        }
        rollback {}
    }

    changeSet(author: "horn (generated)", id: "1790176327340-8") {
        createTable(tableName: "component_identifier") {
            column(autoIncrement: "true", name: "ci_id", type: "BIGINT") {
                constraints(nullable: "false", primaryKey: "true", primaryKeyName: "component_identifierPK")
            }

            column(name: "version", type: "BIGINT")

            column(name: "ci_date_created", type: "TIMESTAMP WITHOUT TIME ZONE")

            column(name: "ci_last_updated", type: "TIMESTAMP WITHOUT TIME ZONE")

            column(name: "ci_end_date", type: "date")

            column(name: "ci_start_date", type: "date")

            column(name: "ci_comp_fk", type: "BIGINT") {
                constraints(nullable: "false")
            }

            column(name: "ci_status_rv_fk", type: "BIGINT")

            column(name: "ci_ident_fk", type: "BIGINT") {
                constraints(nullable: "false")
            }
        }
    }

    changeSet(author: "horn (generated)", id: "1790176327340-78") {
        addForeignKeyConstraint(baseColumnNames: "ci_status_rv_fk", baseTableName: "component_identifier", constraintName: "FKajwolcxmnvrhkctyadv1bdh8l", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "rdv_id", referencedTableName: "refdata_value", validate: "true")
    }

    changeSet(author: "horn (generated)", id: "1790176327340-102") {
        addForeignKeyConstraint(baseColumnNames: "ci_comp_fk", baseTableName: "component_identifier", constraintName: "FKsvch54g61wb2yw4dq0jcy2r2d", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "kbc_id", referencedTableName: "kbcomponent", validate: "true")
    }

    changeSet(author: "horn (generated)", id: "1790176327340-103") {
        addForeignKeyConstraint(baseColumnNames: "ci_ident_fk", baseTableName: "component_identifier", constraintName: "FKtaxcm9xrvfmi900q72wl2kcvg", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "id", referencedTableName: "identifier", validate: "true")
    }

    changeSet(author: "horn (generated)", id: "1790176327340-56") {
        createIndex(indexName: "ci_cmp_idx", tableName: "component_identifier") {
            column(name: "ci_comp_fk")

            column(name: "ci_status_rv_fk")
        }
    }

    changeSet(author: "horn (generated)", id: "1790176327340-57") {
        createIndex(indexName: "ci_created_idx", tableName: "component_identifier") {
            column(name: "ci_date_created")
        }
    }

    changeSet(author: "horn (generated)", id: "1790176327340-58") {
        createIndex(indexName: "ci_full_idx", tableName: "component_identifier") {
            column(name: "ci_comp_fk")

            column(name: "ci_status_rv_fk")

            column(name: "ci_ident_fk")
        }
    }

    changeSet(author: "horn (generated)", id: "1790176327340-59") {
        createIndex(indexName: "ci_ident_idx", tableName: "component_identifier") {
            column(name: "ci_status_rv_fk")

            column(name: "ci_ident_fk")
        }
    }

    changeSet(author: "horn (generated)", id: "1790176327340-206") {
        grailsChange {
            change {
                sql.execute("""insert into refdata_category(
                            rdc_id,
                            rdc_version,
                            rdc_description,
                            rdc_label
                        )
                        values (
                            (select nextval ('hibernate_sequence')),
                            0,
                            'ComponentIdentifier.Status',
                            'ComponentIdentifier.Status'
                        );""")
            }
            rollback {}
        }
    }

    changeSet(author: "horn (generated)", id: "1790176327340-199") {
        grailsChange {
            change {
                int countUpdate = 0

                def ca_combos = sql.rows('''select combo_to_fk,
                        combo_from_fk,
                        date_created,
                        last_updated,
                        combo_status_rv_fk
                        from combo
                        where combo_type_rv_fk = (
                            select rdv_id from refdata_value
                            where rdv_owner = (
                                SELECT rdc_id FROM refdata_category
                                WHERE rdc_description = 'Combo.Type'
                            )
                            and rdv_value = 'KBComponent.Ids'
                        );''')


                combos.each {
                    countUpdate ++
                    sql.execute("""insert into component_identifier(
                                ca_id,
                                version,
                                ca_date_created,
                                ca_last_updated,
                                ca_comp_fk,
                                ca_file_fk
                            )
                            values (
                                (select nextval ('hibernate_sequence')),
                                0,
                                ${it.date_created},
                                ${it.last_updated},
                                ${it.combo_from_fk},
                                ${it.combo_to_fk}
                            );""")
                }

                confirm("insert combo into component_identifier: ${countUpdate}")
                changeSet.setComments("insert combo into component_identifier: ${countUpdate}")
            }
            rollback {}
        }
    }

    // Office

    changeSet(author: "horn (generated)", id: "1790176327340-22") {
        addColumn(tableName: "office") {
            column(name: "office_org_fk", type: "int8") {
                constraints(nullable: "false")
            }
        }
    }

    changeSet(author: "horn (generated)", id: "1790176327340-72") {
        addForeignKeyConstraint(baseColumnNames: "office_org_fk", baseTableName: "office", constraintName: "FK4vjj3l2ko7j4eja0b570e7g91", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "kbc_id", referencedTableName: "org", validate: "true")
    }

    changeSet(author: "horn (generated)", id: "1790176327340-207") {
        grailsChange {
            change {
                int countUpdate = 0

                def ca_combos = sql.rows('''select combo_to_fk,
                        combo_from_fk,
                        date_created,
                        last_updated,
                        combo_status_rv_fk
                        from combo
                        where combo_type_rv_fk = (
                            select rdv_id from refdata_value
                            where rdv_owner = (
                                SELECT rdc_id FROM refdata_category
                                WHERE rdc_description = 'Combo.Type'
                            )
                            and rdv_value = 'Office.Org'
                        );''')


                combos.each {
                    countUpdate ++
                    sql.execute("""update office
                            set office_org_fk = ${it.combo_to_fk}
                            where kbc_id = ${it.combo_from_fk};""")
                }

                confirm("insert combo into office.org: ${countUpdate}")
                changeSet.setComments("insert combo into office.org: ${countUpdate}")
            }
            rollback {}
        }
    }

    // Org

    changeSet(author: "horn (generated)", id: "1790176327340-23") {
        addColumn(tableName: "org") {
            column(name: "org_parent_fk", type: "int8")
        }
    }


    changeSet(author: "horn (generated)", id: "1790176327340-71") {
        addForeignKeyConstraint(baseColumnNames: "org_parent_fk", baseTableName: "org", constraintName: "FK3ysi8pwtmyee8cwt7ytjubsr2", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "kbc_id", referencedTableName: "org", validate: "true")
    }

    changeSet(author: "horn (generated)", id: "1790176327340-208") {
        grailsChange {
            change {
                int countUpdate = 0

                def ca_combos = sql.rows('''select combo_to_fk,
                        combo_from_fk,
                        date_created,
                        last_updated,
                        combo_status_rv_fk
                        from combo
                        where combo_type_rv_fk = (
                            select rdv_id from refdata_value
                            where rdv_owner = (
                                SELECT rdc_id FROM refdata_category
                                WHERE rdc_description = 'Combo.Type'
                            )
                            and rdv_value = 'Org.Parent'
                        );''')


                combos.each {
                    countUpdate ++
                    sql.execute("""update org
                            set org_parent_fk = ${it.combo_to_fk}
                            where kbc_id = ${it.combo_from_fk};""")
                }

                confirm("insert combo into org.parent: ${countUpdate}")
                changeSet.setComments("insert combo into org.parent: ${countUpdate}")
            }
            rollback {}
        }
    }

    changeSet(author: "horn (generated)", id: "1790176327340-25") {
        addColumn(tableName: "org") {
            column(name: "org_successor_fk", type: "int8")
        }
    }

    changeSet(author: "horn (generated)", id: "1790176327340-73") {
        addForeignKeyConstraint(baseColumnNames: "org_successor_fk", baseTableName: "org", constraintName: "FK4vrla6yp8t78u4a0wa9cjoeuc", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "kbc_id", referencedTableName: "org", validate: "true")
    }

    changeSet(author: "horn (generated)", id: "1790176327340-208") {
        grailsChange {
            change {
                int countUpdate = 0

                def ca_combos = sql.rows('''select combo_to_fk,
                        combo_from_fk,
                        date_created,
                        last_updated,
                        combo_status_rv_fk
                        from combo
                        where combo_type_rv_fk = (
                            select rdv_id from refdata_value
                            where rdv_owner = (
                                SELECT rdc_id FROM refdata_category
                                WHERE rdc_description = 'Combo.Type'
                            )
                            and rdv_value = 'Org.Previous'
                        );''')


                combos.each {
                    countUpdate ++
                    sql.execute("""update org
                            set org_successor_fk = ${it.combo_from_fk}
                            where kbc_id = ${it.combo_to_fk};""")
                }

                confirm("insert combo into org.successor: ${countUpdate}")
                changeSet.setComments("insert combo into org.successor: ${countUpdate}")
            }
            rollback {}
        }
    }

    // Package

    changeSet(author: "horn (generated)", id: "1790176327340-26") {
        addColumn(tableName: "package") {
            column(name: "pkg_content_provider_fk", type: "int8")
        }
    }

    changeSet(author: "horn (generated)", id: "1790176327340-74") {
        addForeignKeyConstraint(baseColumnNames: "pkg_content_provider_fk", baseTableName: "package", constraintName: "FK8dxq4q9xstbhjd019r64gxw5y", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "kbc_id", referencedTableName: "org", validate: "true")
    }

    changeSet(author: "horn (generated)", id: "1790176327340-209") {
        grailsChange {
            change {
                int countUpdate = 0

                def ca_combos = sql.rows('''select combo_to_fk,
                        combo_from_fk,
                        date_created,
                        last_updated,
                        combo_status_rv_fk
                        from combo
                        where combo_type_rv_fk = (
                            select rdv_id from refdata_value
                            where rdv_owner = (
                                SELECT rdc_id FROM refdata_category
                                WHERE rdc_description = 'Combo.Type'
                            )
                            and rdv_value = 'Package.ContentProvider'
                        );''')


                combos.each {
                    countUpdate ++
                    sql.execute("""update package
                            set pkg_content_provider_fk = ${it.combo_from_fk}
                            where kbc_id = ${it.combo_to_fk};""")
                }

                confirm("insert combo into package.contentProvider: ${countUpdate}")
                changeSet.setComments("insert combo into package.contentProvider: ${countUpdate}")
            }
            rollback {}
        }
    }

    changeSet(author: "horn (generated)", id: "1790176327340-27") {
        addColumn(tableName: "package") {
            column(name: "pkg_nominal_platform_fk", type: "int8")
        }
    }

    changeSet(author: "horn (generated)", id: "1790176327340-104") {
        addForeignKeyConstraint(baseColumnNames: "pkg_nominal_platform_fk", baseTableName: "package", constraintName: "FKtji5rpd3emxprdidedl006f9u", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "kbc_id", referencedTableName: "platform", validate: "true")
    }

    changeSet(author: "horn (generated)", id: "1790176327340-210") {
        grailsChange {
            change {
                int countUpdate = 0

                def ca_combos = sql.rows('''select combo_to_fk,
                        combo_from_fk,
                        date_created,
                        last_updated,
                        combo_status_rv_fk
                        from combo
                        where combo_type_rv_fk = (
                            select rdv_id from refdata_value
                            where rdv_owner = (
                                SELECT rdc_id FROM refdata_category
                                WHERE rdc_description = 'Combo.Type'
                            )
                            and rdv_value = 'Package.NominalPlatform'
                        );''')


                combos.each {
                    countUpdate ++
                    sql.execute("""update package
                            set pkg_nominal_platform_fk = ${it.combo_to_fk}
                            where kbc_id = ${it.combo_from_fk};""")
                }

                confirm("insert combo into package.nominalPlatform: ${countUpdate}")
                changeSet.setComments("insert combo into package.nominalPlatform: ${countUpdate}")
            }
            rollback {}
        }
    }

    changeSet(author: "horn (generated)", id: "1790176327340-28") {
        addColumn(tableName: "package") {
            column(name: "pkg_parent_fk", type: "int8")
        }
    }

    changeSet(author: "horn (generated)", id: "1790176327340-99") {
        addForeignKeyConstraint(baseColumnNames: "pkg_parent_fk", baseTableName: "package", constraintName: "FKry77h02jm3abr8hgwyojw6coh", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "kbc_id", referencedTableName: "package", validate: "true")
    }

    changeSet(author: "horn (generated)", id: "1790176327340-211") {
        grailsChange {
            change {
                int countUpdate = 0

                def ca_combos = sql.rows('''select combo_to_fk,
                        combo_from_fk,
                        date_created,
                        last_updated,
                        combo_status_rv_fk
                        from combo
                        where combo_type_rv_fk = (
                            select rdv_id from refdata_value
                            where rdv_owner = (
                                SELECT rdc_id FROM refdata_category
                                WHERE rdc_description = 'Combo.Type'
                            )
                            and rdv_value = 'Package.Parent'
                        );''')


                combos.each {
                    countUpdate ++
                    sql.execute("""update package
                            set pkg_parent_fk = ${it.combo_to_fk}
                            where kbc_id = ${it.combo_from_fk};""")
                }

                confirm("insert combo into package.parent: ${countUpdate}")
                changeSet.setComments("insert combo into package.parent: ${countUpdate}")
            }
            rollback {}
        }
    }

    changeSet(author: "horn (generated)", id: "1790176327340-29") {
        addColumn(tableName: "package") {
            column(name: "pkg_previous_fk", type: "int8")
        }
    }

    changeSet(author: "horn (generated)", id: "1790176327340-98") {
        addForeignKeyConstraint(baseColumnNames: "pkg_previous_fk", baseTableName: "package", constraintName: "FKro48nnuy4g7l3bhk9xwwmuj89", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "kbc_id", referencedTableName: "package", validate: "true")
    }

    changeSet(author: "horn (generated)", id: "1790176327340-212") {
        grailsChange {
            change {
                int countUpdate = 0

                def ca_combos = sql.rows('''select combo_to_fk,
                        combo_from_fk,
                        date_created,
                        last_updated,
                        combo_status_rv_fk
                        from combo
                        where combo_type_rv_fk = (
                            select rdv_id from refdata_value
                            where rdv_owner = (
                                SELECT rdc_id FROM refdata_category
                                WHERE rdc_description = 'Combo.Type'
                            )
                            and rdv_value = 'Package.Previous'
                        );''')


                combos.each {
                    countUpdate ++
                    sql.execute("""update package
                            set pkg_previous_fk = ${it.combo_to_fk}
                            where kbc_id = ${it.combo_from_fk};""")
                }

                confirm("insert combo into package.previous: ${countUpdate}")
                changeSet.setComments("insert combo into package.previous: ${countUpdate}")
            }
            rollback {}
        }
    }

    changeSet(author: "horn (generated)", id: "1790176327340-30") {
        addColumn(tableName: "package") {
            column(name: "pkg_provider_fk", type: "int8")
        }
    }

    changeSet(author: "horn (generated)", id: "1790176327340-89") {
        addForeignKeyConstraint(baseColumnNames: "pkg_provider_fk", baseTableName: "package", constraintName: "FKmxamrrttlt0bq2eu1ioesegg2", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "kbc_id", referencedTableName: "org", validate: "true")
    }

    changeSet(author: "horn (generated)", id: "1790176327340-212") {
        grailsChange {
            change {
                int countUpdate = 0

                def ca_combos = sql.rows('''select combo_to_fk,
                        combo_from_fk,
                        date_created,
                        last_updated,
                        combo_status_rv_fk
                        from combo
                        where combo_type_rv_fk = (
                            select rdv_id from refdata_value
                            where rdv_owner = (
                                SELECT rdc_id FROM refdata_category
                                WHERE rdc_description = 'Combo.Type'
                            )
                            and rdv_value = 'Package.Provider'
                        );''')


                combos.each {
                    countUpdate ++
                    sql.execute("""update package
                            set pkg_provider_fk = ${it.combo_to_fk}
                            where kbc_id = ${it.combo_from_fk};""")
                }

                confirm("insert combo into package.provider: ${countUpdate}")
                changeSet.setComments("insert combo into package.provider: ${countUpdate}")
            }
            rollback {}
        }
    }

    // Platform

    changeSet(author: "horn (generated)", id: "1790176327340-31") {
        addColumn(tableName: "platform") {
            column(name: "plat_provider_fk", type: "int8")
        }
    }

    changeSet(author: "horn (generated)", id: "1790176327340-96") {
        addForeignKeyConstraint(baseColumnNames: "plat_provider_fk", baseTableName: "platform", constraintName: "FKrgirc6hvu9v50t0wqoylcrltf", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "kbc_id", referencedTableName: "org", validate: "true")
    }

    changeSet(author: "horn (generated)", id: "1790176327340-212") {
        grailsChange {
            change {
                int countUpdate = 0

                def ca_combos = sql.rows('''select combo_to_fk,
                        combo_from_fk,
                        date_created,
                        last_updated,
                        combo_status_rv_fk
                        from combo
                        where combo_type_rv_fk = (
                            select rdv_id from refdata_value
                            where rdv_owner = (
                                SELECT rdc_id FROM refdata_category
                                WHERE rdc_description = 'Combo.Type'
                            )
                            and rdv_value = 'Platform.Provider'
                        );''')


                combos.each {
                    countUpdate ++
                    sql.execute("""update platform
                            set plat_provider_fk = ${it.combo_to_fk}
                            where kbc_id = ${it.combo_from_fk};""")
                }

                confirm("insert combo into platform.provider: ${countUpdate}")
                changeSet.setComments("insert combo into platform.provider: ${countUpdate}")
            }
            rollback {}
        }
    }

    // TIPL

    changeSet(author: "horn (generated)", id: "1790176327340-33") {
        addColumn(tableName: "title_instance_platform") {
            column(name: "tipl_host_platform_fk", type: "int8") {
                constraints(nullable: "false")
            }
        }
    }

    changeSet(author: "horn (generated)", id: "1790176327340-94") {
        addForeignKeyConstraint(baseColumnNames: "tipl_host_platform_fk", baseTableName: "title_instance_platform", constraintName: "FKpuwoiit5uadm3gp9rc5jf4h8u", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "kbc_id", referencedTableName: "platform", validate: "true")
    }

    changeSet(author: "horn (generated)", id: "1790176327340-212") {
        grailsChange {
            change {
                int countUpdate = 0

                def ca_combos = sql.rows('''select combo_to_fk,
                        combo_from_fk,
                        date_created,
                        last_updated,
                        combo_status_rv_fk
                        from combo
                        where combo_type_rv_fk = (
                            select rdv_id from refdata_value
                            where rdv_owner = (
                                SELECT rdc_id FROM refdata_category
                                WHERE rdc_description = 'Combo.Type'
                            )
                            and rdv_value = 'Platform.HostedTitles'
                        );''')


                combos.each {
                    countUpdate ++
                    sql.execute("""update title_instance_platform
                            set tipl_host_platform_fk = ${it.combo_from_fk}
                            where kbc_id = ${it.combo_to_fk};""")
                }

                confirm("insert combo into tipl.hostPlatform: ${countUpdate}")
                changeSet.setComments("insert combo into tipl.hostPlatform: ${countUpdate}")
            }
            rollback {}
        }
    }

    changeSet(author: "horn (generated)", id: "1790176327340-34") {
        addColumn(tableName: "title_instance_platform") {
            column(name: "tipl_title_fk", type: "int8") {
                constraints(nullable: "false")
            }
        }
    }

    changeSet(author: "horn (generated)", id: "1790176327340-97") {
        addForeignKeyConstraint(baseColumnNames: "tipl_title_fk", baseTableName: "title_instance_platform", constraintName: "FKrn0umksw35ywgqgblq2tt7brx", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "kbc_id", referencedTableName: "title_instance", validate: "true")
    }

    changeSet(author: "horn (generated)", id: "1790176327340-212") {
        grailsChange {
            change {
                int countUpdate = 0

                def ca_combos = sql.rows('''select combo_to_fk,
                        combo_from_fk,
                        date_created,
                        last_updated,
                        combo_status_rv_fk
                        from combo
                        where combo_type_rv_fk = (
                            select rdv_id from refdata_value
                            where rdv_owner = (
                                SELECT rdc_id FROM refdata_category
                                WHERE rdc_description = 'Combo.Type'
                            )
                            and rdv_value = 'TitleInstance.Tipls'
                        );''')


                combos.each {
                    countUpdate ++
                    sql.execute("""update title_instance_platform
                            set tipl_title_fk = ${it.combo_from_fk}
                            where kbc_id = ${it.combo_to_fk};""")
                }

                confirm("insert combo into tipl.title: ${countUpdate}")
                changeSet.setComments("insert combo into tipl.title: ${countUpdate}")
            }
            rollback {}
        }
    }

    // TIPP

    changeSet(author: "horn (generated)", id: "1790176327340-35") {
        addColumn(tableName: "title_instance_package_platform") {
            column(name: "tipp_host_platform_fk", type: "int8")
        }
    }

    changeSet(author: "horn (generated)", id: "1790176327340-92") {
        addForeignKeyConstraint(baseColumnNames: "tipp_host_platform_fk", baseTableName: "title_instance_package_platform", constraintName: "FKoiotwfahqljocmuksac3p5kov", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "kbc_id", referencedTableName: "platform", validate: "true")
    }

    changeSet(author: "horn (generated)", id: "1790176327340-212") {
        grailsChange {
            change {
                int countUpdate = 0

                def ca_combos = sql.rows('''select combo_to_fk,
                        combo_from_fk,
                        date_created,
                        last_updated,
                        combo_status_rv_fk
                        from combo
                        where combo_type_rv_fk = (
                            select rdv_id from refdata_value
                            where rdv_owner = (
                                SELECT rdc_id FROM refdata_category
                                WHERE rdc_description = 'Combo.Type'
                            )
                            and rdv_value = 'Platform.HostedTipps'
                        );''')


                combos.each {
                    countUpdate ++
                    sql.execute("""update title_instance_package_platform
                            set tipp_host_platform_fk = ${it.combo_from_fk}
                            where kbc_id = ${it.combo_to_fk};""")
                }

                confirm("insert combo into tipp.hostPlatform: ${countUpdate}")
                changeSet.setComments("insert combo into tipp.hostPlatform: ${countUpdate}")
            }
            rollback {}
        }
    }


    changeSet(author: "horn (generated)", id: "1790176327340-36") {
        addColumn(tableName: "title_instance_package_platform") {
            column(name: "tipp_pkg_fk", type: "int8")
        }
    }

    changeSet(author: "horn (generated)", id: "1790176327340-76") {
        addForeignKeyConstraint(baseColumnNames: "tipp_pkg_fk", baseTableName: "title_instance_package_platform", constraintName: "FK9rad3hn4ct51x6d2nruxxcakq", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "kbc_id", referencedTableName: "package", validate: "true")
    }

    changeSet(author: "horn (generated)", id: "1790176327340-212") {
        grailsChange {
            change {
                int countUpdate = 0

                def ca_combos = sql.rows('''select combo_to_fk,
                        combo_from_fk,
                        date_created,
                        last_updated,
                        combo_status_rv_fk
                        from combo
                        where combo_type_rv_fk = (
                            select rdv_id from refdata_value
                            where rdv_owner = (
                                SELECT rdc_id FROM refdata_category
                                WHERE rdc_description = 'Combo.Type'
                            )
                            and rdv_value = 'Package.Tipps'
                        );''')


                combos.each {
                    countUpdate ++
                    sql.execute("""update title_instance_package_platform
                            set tipp_pkg_fk = ${it.combo_from_fk}
                            where kbc_id = ${it.combo_to_fk};""")
                }

                confirm("insert combo into tipp.pkg: ${countUpdate}")
                changeSet.setComments("insert combo into tipp.pkg: ${countUpdate}")
            }
            rollback {}
        }
    }

    changeSet(author: "horn (generated)", id: "1790176327340-37") {
        addColumn(tableName: "title_instance_package_platform") {
            column(name: "tipp_title_fk", type: "int8")
        }
    }

    changeSet(author: "horn (generated)", id: "1790176327340-91") {
        addForeignKeyConstraint(baseColumnNames: "tipp_title_fk", baseTableName: "title_instance_package_platform", constraintName: "FKof4dd82vcvdm5oje81vyin3bk", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "kbc_id", referencedTableName: "title_instance", validate: "true")
    }

    changeSet(author: "horn (generated)", id: "1790176327340-212") {
        grailsChange {
            change {
                int countUpdate = 0

                def ca_combos = sql.rows('''select combo_to_fk,
                        combo_from_fk,
                        date_created,
                        last_updated,
                        combo_status_rv_fk
                        from combo
                        where combo_type_rv_fk = (
                            select rdv_id from refdata_value
                            where rdv_owner = (
                                SELECT rdc_id FROM refdata_category
                                WHERE rdc_description = 'Combo.Type'
                            )
                            and rdv_value = 'TitleInstance.Tipps'
                        );''')


                combos.each {
                    countUpdate ++
                    sql.execute("""update title_instance_package_platform
                            set tipp_title_fk = ${it.combo_from_fk}
                            where kbc_id = ${it.combo_to_fk};""")
                }

                confirm("insert combo into tipp.title: ${countUpdate}")
                changeSet.setComments("insert combo into tipp.title: ${countUpdate}")
            }
            rollback {}
        }
    }

    // Cleanup

    changeSet(author: "horn (generated)", id: "1790176327340-105") {
        dropForeignKeyConstraint(baseTableName: "combo", constraintName: "fk26noxwwqejpm28iqmmikunvrj")
    }

    changeSet(author: "horn (generated)", id: "1790176327340-107") {
        dropForeignKeyConstraint(baseTableName: "party", constraintName: "fk2owjboy27ws0j58fgdt1hyrv4")
    }

    changeSet(author: "horn (generated)", id: "1790176327340-108") {
        dropForeignKeyConstraint(baseTableName: "dsapplied_criterion", constraintName: "fk37jwybh38m40xfotaa3ox4sis")
    }

    changeSet(author: "horn (generated)", id: "1790176327340-109") {
        dropForeignKeyConstraint(baseTableName: "party", constraintName: "fk4fr090rvrak58ypun15io5jln")
    }

    changeSet(author: "horn (generated)", id: "1790176327340-110") {
        dropForeignKeyConstraint(baseTableName: "macro_tags_value", constraintName: "fk4pu4gveqnt1herj35s2h6dd74")
    }

    changeSet(author: "horn (generated)", id: "1790176327340-111") {
        dropForeignKeyConstraint(baseTableName: "folder_entry", constraintName: "fk4sann6e8xej2j3tl3kb7vkarl")
    }

    changeSet(author: "horn (generated)", id: "1790176327340-112") {
        dropForeignKeyConstraint(baseTableName: "dsapplied_criterion", constraintName: "fk5nsrwpcb6p46abqcj0glm4pxp")
    }

    changeSet(author: "horn (generated)", id: "1790176327340-113") {
        dropForeignKeyConstraint(baseTableName: "review_request", constraintName: "fk6gdr4avi5vjxe6yek4rv1gpsf")
    }

    changeSet(author: "horn (generated)", id: "1790176327340-114") {
        dropForeignKeyConstraint(baseTableName: "dsapplied_criterion", constraintName: "fk81ttek8ss1pasiujldmlo0n8w")
    }

    changeSet(author: "horn (generated)", id: "1790176327340-115") {
        dropForeignKeyConstraint(baseTableName: "refine_project", constraintName: "fk8mqjtduwpcrex1oiecc96mwjo")
    }

    changeSet(author: "horn (generated)", id: "1790176327340-116") {
        dropForeignKeyConstraint(baseTableName: "folder_entry", constraintName: "fk8xdck8vgx7yb7ymmlm400kuga")
    }

    changeSet(author: "horn (generated)", id: "1790176327340-117") {
        dropForeignKeyConstraint(baseTableName: "macro", constraintName: "fk93myiagvnf7wdts4yao9wkoqr")
    }

    changeSet(author: "horn (generated)", id: "1790176327340-119") {
        dropForeignKeyConstraint(baseTableName: "imprint", constraintName: "fk9yla4vwugjed0utk9btt02oj3")
    }

    changeSet(author: "horn (generated)", id: "1790176327340-120") {
        dropForeignKeyConstraint(baseTableName: "dscriterion", constraintName: "fkacyn2jqtodlvnogdu65iqvio1")
    }

    changeSet(author: "horn (generated)", id: "1790176327340-121") {
        dropForeignKeyConstraint(baseTableName: "update_token", constraintName: "fkb703yjmo3anmgiakeva9aja3r")
    }


    changeSet(author: "horn (generated)", id: "1790176327340-123") {
        dropForeignKeyConstraint(baseTableName: "refine_project", constraintName: "fkc4xy0nr41tmx8em0y23d2xl9s")
    }

    changeSet(author: "horn (generated)", id: "1790176327340-124") {
        dropForeignKeyConstraint(baseTableName: "user_organisation_membership", constraintName: "fkcm3mmoxy32c483pks9w40pq6f")
    }

    changeSet(author: "horn (generated)", id: "1790176327340-125") {
        dropForeignKeyConstraint(baseTableName: "title_instance", constraintName: "fkes1slhq1sbmothegvrpocgr9e")
    }

    changeSet(author: "horn (generated)", id: "1790176327340-126") {
        dropForeignKeyConstraint(baseTableName: "dsapplied_criterion", constraintName: "fkfj1cwdl76rjw9oavn0w9mj1xo")
    }

    changeSet(author: "horn (generated)", id: "1790176327340-127") {
        dropForeignKeyConstraint(baseTableName: "refine_project", constraintName: "fkfy9hht47txw7g4oow7iesud6n")
    }

    changeSet(author: "horn (generated)", id: "1790176327340-128") {
        dropForeignKeyConstraint(baseTableName: "dsnote", constraintName: "fkh4mk4a95bu8ney9tloewadbi8")
    }

    changeSet(author: "horn (generated)", id: "1790176327340-129") {
        dropForeignKeyConstraint(baseTableName: "dscriterion", constraintName: "fkh75licju6txf8u09s7myjgwiu")
    }

    changeSet(author: "horn (generated)", id: "1790176327340-130") {
        dropForeignKeyConstraint(baseTableName: "user_organisation_membership", constraintName: "fkhgpxnxo67iwwfm340kadauftb")
    }

    changeSet(author: "horn (generated)", id: "1790176327340-131") {
        dropForeignKeyConstraint(baseTableName: "user_organisation_membership", constraintName: "fki3kwo4pasx5ucd1ncubyxhsfs")
    }

    changeSet(author: "horn (generated)", id: "1790176327340-132") {
        dropForeignKeyConstraint(baseTableName: "combo", constraintName: "fki9gqetad4umeu83v7l62j8awb")
    }

    changeSet(author: "horn (generated)", id: "1790176327340-133") {
        dropForeignKeyConstraint(baseTableName: "license", constraintName: "fkiofygmyp65vm9jgatit7jnm76")
    }

    changeSet(author: "horn (generated)", id: "1790176327340-134") {
        dropForeignKeyConstraint(baseTableName: "license", constraintName: "fkjgh122iv0a4yk15vgvlp1ljh6")
    }

    changeSet(author: "horn (generated)", id: "1790176327340-135") {
        dropForeignKeyConstraint(baseTableName: "macro_tags_value", constraintName: "fkkdd191fgv8ni91r9h0crjnux8")
    }

    changeSet(author: "horn (generated)", id: "1790176327340-136") {
        dropForeignKeyConstraint(baseTableName: "rule", constraintName: "fkl53fg4avr2l4xvx6qpl7oxaf8")
    }

    changeSet(author: "horn (generated)", id: "1790176327340-137") {
        dropForeignKeyConstraint(baseTableName: "update_token", constraintName: "fkl96ipchsdicm19og5k32vifj7")
    }

    changeSet(author: "horn (generated)", id: "1790176327340-138") {
        dropForeignKeyConstraint(baseTableName: "combo", constraintName: "fkletguvojj9v9ucu34iuoxkov2")
    }

    changeSet(author: "horn (generated)", id: "1790176327340-139") {
        dropForeignKeyConstraint(baseTableName: "refine_project", constraintName: "fkm9w8r8ecjjahv0msvms066jyg")
    }

    changeSet(author: "horn (generated)", id: "1790176327340-140") {
        dropForeignKeyConstraint(baseTableName: "package", constraintName: "fknn7kk7k1cnmess5xmo4aidq0i")
    }

    changeSet(author: "horn (generated)", id: "1790176327340-141") {
        dropForeignKeyConstraint(baseTableName: "title_instance", constraintName: "fknpmbxucn52ulosptk44bmffkg")
    }

    changeSet(author: "horn (generated)", id: "1790176327340-142") {
        dropForeignKeyConstraint(baseTableName: "user_organisation_membership", constraintName: "fknva6rv4mevxywko9jq5pg25ux")
    }

    changeSet(author: "horn (generated)", id: "1790176327340-143") {
        dropForeignKeyConstraint(baseTableName: "refine_project_skipped_titles", constraintName: "fko76nbo79df952y5b32ic15frd")
    }

    changeSet(author: "horn (generated)", id: "1790176327340-144") {
        dropForeignKeyConstraint(baseTableName: "combo", constraintName: "fkp6k1rfprw3o255ijut9gqr237")
    }

    changeSet(author: "horn (generated)", id: "1790176327340-146") {
        dropForeignKeyConstraint(baseTableName: "refine_project", constraintName: "fkqle1j5swnf10869ndc7hhik6e")
    }

    changeSet(author: "horn (generated)", id: "1790176327340-147") {
        dropForeignKeyConstraint(baseTableName: "refine_project", constraintName: "fkru7hao9hwirc2faa99lj1wfr7")
    }

    changeSet(author: "horn (generated)", id: "1790176327340-148") {
        dropForeignKeyConstraint(baseTableName: "classification", constraintName: "fks4kmo6a3srqgl9ag48aj15108")
    }

    changeSet(author: "horn (generated)", id: "1790176327340-149") {
        dropForeignKeyConstraint(baseTableName: "folder_entry", constraintName: "fkse1dy2yymgie3ucb2i2guo4pa")
    }

    changeSet(author: "horn (generated)", id: "1790176327340-150") {
        dropForeignKeyConstraint(baseTableName: "title_instance_package_platform", constraintName: "fksxt9j6270a5mt3vehbghjtxtb")
    }

    changeSet(author: "horn (generated)", id: "1790176327340-151") {
        dropForeignKeyConstraint(baseTableName: "refine_project", constraintName: "fkt70yc8y9yotrq10rakh8yi0ot")
    }

    changeSet(author: "horn (generated)", id: "1790176327340-152") {
        dropUniqueConstraint(constraintName: "uk_ewy7285o9j55jgydja5ipl92a", tableName: "party")
    }

    changeSet(author: "horn (generated)", id: "1790176327340-153") {
        dropTable(tableName: "audit_log")
    }

    changeSet(author: "horn (generated)", id: "1790176327340-154") {
        dropTable(tableName: "classification")
    }

    changeSet(author: "horn (generated)", id: "1790176327340-155") {
        dropTable(tableName: "combo")
    }

    changeSet(author: "horn (generated)", id: "1790176327340-156") {
        dropTable(tableName: "document")
    }

    changeSet(author: "horn (generated)", id: "1790176327340-157") {
        dropTable(tableName: "dsapplied_criterion")
    }

    changeSet(author: "horn (generated)", id: "1790176327340-158") {
        dropTable(tableName: "dscategory")
    }

    changeSet(author: "horn (generated)", id: "1790176327340-159") {
        dropTable(tableName: "dscriterion")
    }

    changeSet(author: "horn (generated)", id: "1790176327340-160") {
        dropTable(tableName: "dsnote")
    }

    changeSet(author: "horn (generated)", id: "1790176327340-161") {
        dropTable(tableName: "folder_entry")
    }

    changeSet(author: "horn (generated)", id: "1790176327340-162") {
        dropTable(tableName: "imprint")
    }

    changeSet(author: "horn (generated)", id: "1790176327340-163") {
        dropTable(tableName: "license")
    }

    changeSet(author: "horn (generated)", id: "1790176327340-164") {
        dropTable(tableName: "macro")
    }

    changeSet(author: "horn (generated)", id: "1790176327340-165") {
        dropTable(tableName: "macro_tags_value")
    }

    changeSet(author: "horn (generated)", id: "1790176327340-209") {
        dropTable(tableName: "org_role")
    }

    changeSet(author: "horn (generated)", id: "1790176327340-166") {
        renameTable(oldTableName: "org_refdata_value", newTableName: "org_role")
    }

    changeSet(author: "horn (generated)", id: "1790176327340-167") {
        dropTable(tableName: "refine_operation")
    }

    changeSet(author: "horn (generated)", id: "1790176327340-168") {
        dropTable(tableName: "refine_project")
    }

    changeSet(author: "horn (generated)", id: "1790176327340-169") {
        dropTable(tableName: "refine_project_skipped_titles")
    }

    changeSet(author: "horn (generated)", id: "1790176327340-170") {
        dropTable(tableName: "rule")
    }

    changeSet(author: "horn (generated)", id: "1790176327340-171") {
        dropTable(tableName: "update_token")
    }

    changeSet(author: "horn (generated)", id: "1790176327340-172") {
        dropTable(tableName: "user_organisation_membership")
    }

    changeSet(author: "horn (generated)", id: "1790176327340-175") {
        dropColumn(columnName: "ba_password", tableName: "web_hook_endpoint")
    }

    changeSet(author: "horn (generated)", id: "1790176327340-176") {
        dropColumn(columnName: "ba_username", tableName: "web_hook_endpoint")
    }

    changeSet(author: "horn (generated)", id: "1790176327340-177") {
        dropColumn(columnName: "continuing_series_id", tableName: "title_instance")
    }

    changeSet(author: "horn (generated)", id: "1790176327340-178") {
        dropColumn(columnName: "ftp_url", tableName: "source")
    }

    changeSet(author: "horn (generated)", id: "1790176327340-179") {
        dropColumn(columnName: "mission_id", tableName: "party")
    }

    changeSet(author: "horn (generated)", id: "1790176327340-183") {
        dropColumn(columnName: "owner_id", tableName: "party")
    }

    changeSet(author: "horn (generated)", id: "1790176327340-184") {
        dropColumn(columnName: "pkg_refine_project_fk", tableName: "package")
    }

    changeSet(author: "horn (generated)", id: "1790176327340-185") {
        dropColumn(columnName: "reason_retired_id", tableName: "title_instance")
    }

    changeSet(author: "horn (generated)", id: "1790176327340-186") {
        dropColumn(columnName: "refine_project_id", tableName: "review_request")
    }

    changeSet(author: "horn (generated)", id: "1790176327340-187") {
        dropColumn(columnName: "tipp_coverage_depth", tableName: "title_instance_package_platform")
    }

    changeSet(author: "horn (generated)", id: "1790176327340-188") {
        dropColumn(columnName: "tipp_coverage_note", tableName: "title_instance_package_platform")
    }

    changeSet(author: "horn (generated)", id: "1790176327340-189") {
        dropColumn(columnName: "tipp_embargo", tableName: "title_instance_package_platform")
    }

    changeSet(author: "horn (generated)", id: "1790176327340-190") {
        dropColumn(columnName: "tipp_end_date", tableName: "title_instance_package_platform")
    }

    changeSet(author: "horn (generated)", id: "1790176327340-191") {
        dropColumn(columnName: "tipp_end_issue", tableName: "title_instance_package_platform")
    }

    changeSet(author: "horn (generated)", id: "1790176327340-192") {
        dropColumn(columnName: "tipp_end_volume", tableName: "title_instance_package_platform")
    }

    changeSet(author: "horn (generated)", id: "1790176327340-193") {
        dropColumn(columnName: "tipp_start_date", tableName: "title_instance_package_platform")
    }

    changeSet(author: "horn (generated)", id: "1790176327340-194") {
        dropColumn(columnName: "tipp_start_issue", tableName: "title_instance_package_platform")
    }

    changeSet(author: "horn (generated)", id: "1790176327340-195") {
        dropColumn(columnName: "tipp_start_volume", tableName: "title_instance_package_platform")
    }

    changeSet(author: "horn (generated)", id: "1790176327340-196") {
        dropColumn(columnName: "type", tableName: "source")
    }

    changeSet(author: "horn (generated)", id: "1790176327340-1") {
        dropNotNullConstraint(columnDataType: "varchar(255)", columnName: "ep_url", tableName: "web_hook_endpoint")
    }

    changeSet(author: "horn (generated)", id: "1790176327340-2") {
        dropNotNullConstraint(columnDataType: "varchar(255)", columnName: "name", tableName: "web_hook_endpoint")
    }

}
