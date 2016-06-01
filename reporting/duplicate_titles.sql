select min(kbc_id), count(kbc_id), kbc_normname from kbcomponent where class='org.gokb.cred.TitleInstance' group by kbc_normname having count(kbc_id) > 1 order by count(kbc_id) desc;



drop table duplicate_titles;
create table duplicate_titles as ( select kbc_name as name, min(kbc_id) as primary_component, count(kbc_id) as dup_count from kbcomponent where kbc_name is not null and class='org.gokb.cred.TitleInstance' group by kbc_name having count(kbc_id) > 1 );




create table duplicate_titles as ( select kbc_normname normname, min(kbc_id), count(kbc_id) from kbcomponent where class='org.gokb.cred.TitleInstance' group by kbc_normname having count(kbc_id) > 1 );

select kbc.kbc_id, kbc.kbc_name from kbcomponent kbc, duplicate_titles dt where kbc.kbc_normname = dt.normname limit 10;


select kbc.kbc_id, kbc.kbc_name from kbcomponent kbc, duplicate_titles dt where kbc.kbc_normname = dt.kbc_normname limit 10;




# 286 == refdata value for KBComponent.Ids

select kbc.kbc_id title_id, 
       kbc.kbc_normname normalised_title,
       kbc.kbc_name title_proper,
       id.kbc_id identifier_id,
       id.kbc_normname normalised_identifier,
       id.kbc_name identifier_proper
from kbcomponent kbc, 
     duplicate_titles dt,
     combo id_combo,
     kbcomponent id
where kbc.kbc_normname = dt.kbc_normname 
  and id_combo.combo_from_fk = kbc.kbc_id
  and id_combo.combo_type_rv_fk = 368
  and id_combo.combo_to_fk = id.kbc_id
order by kbc.kbc_normname, kbc.kbc_id, id.kbc_id
limit 10;

select kbc.kbc_id title_id,
       kbc.kbc_normname normalised_title,
       kbc.kbc_name title_proper,
       rdv_ts.rdv_value title_status,
       id.kbc_id identifier_id,
       id.kbc_normname normalised_identifier,
       id.kbc_name identifier_proper,
       rdv_id.rdv_value identifier_status
from kbcomponent kbc,
     duplicate_titles dt,
     combo id_combo,
     kbcomponent id,
     refdata_value rdv_ts,
     refdata_value rdv_id
where kbc.kbc_normname = dt.kbc_normname
  and id_combo.combo_from_fk = kbc.kbc_id
  and id_combo.combo_type_rv_fk = 368
  and id_combo.combo_to_fk = id.kbc_id
  and kbc.kbc_status_rv_fk = rdv_ts.rdv_id
  and id.kbc_status_rv_fk = rdv_id.rdv_id
order by kbc.kbc_normname, kbc.kbc_id, id.kbc_id
limit 10;

