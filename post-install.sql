create index norm_id_value_idx on kbcomponent(kbc_normname(64));
create index combo_full_idx on combo(combo_from_fk, combo_to_fk,combo_type_rv_fk);


Need index on kbc_shortcode
