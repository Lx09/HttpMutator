package es.us.isa.httpmutator.headers.location;

import es.us.isa.httpmutator.AbstractMutator;
import es.us.isa.httpmutator.body.value.common.operator.NullOperator;
import es.us.isa.httpmutator.headers.location.operator.LocationMutationOperator;
import es.us.isa.httpmutator.util.OperatorNames;
import static es.us.isa.httpmutator.util.PropertyManager.readProperty;


public class LocationMutator extends AbstractMutator {
    public LocationMutator() {
        super();
        prob = Float.parseFloat(readProperty("operator.header.location.prob"));
        operators.put(OperatorNames.MUTATE, new LocationMutationOperator());
        operators.put(OperatorNames.NULL, new NullOperator(LocationMutator.class));
    }
}
