

SET @minns = null;

select @minns:=min(id) from identifier_namespace where idns_value = 'Taylor & Francis';

# Step one : find out the lowest identifier id for "taylor and francis"
# select min(id) from identifier_namespace where idns_value = 'Taylor & Francis';

select id from identifier_namespace where idns_value = 'Taylor & Francis' and id <> @minns;

# Step two : make all identifiers with a namespace of "taylor and francis" point to that identifier
update kbcomponent set id_ns = @minns where id_ns in ( select id from identifier_namespace where idns_value = 'Taylor & Francis' and id <> @minns);

