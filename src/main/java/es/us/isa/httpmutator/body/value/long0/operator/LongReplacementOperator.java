package es.us.isa.httpmutator.body.value.long0.operator;

import es.us.isa.httpmutator.AbstractOperator;
import es.us.isa.httpmutator.util.OperatorNames;
import static es.us.isa.httpmutator.util.PropertyManager.readProperty;

/**
 * Operator that mutates a long by completely replacing it.
 *
 * @author Alberto Martin-Lopez
 */
public class LongReplacementOperator extends AbstractOperator {

    private long minLong;
    private long maxLong;

    public LongReplacementOperator() {
        super();
        weight = Float.parseFloat(readProperty("operator.value.long.weight." + OperatorNames.REPLACE));
        minLong = Long.parseLong(readProperty("operator.value.long.min"));
        maxLong = Long.parseLong(readProperty("operator.value.long.max"));
    }
    
    @Override
    protected Object doMutate(Object longObject) {
        return rand1.nextLong(minLong, maxLong);
    }
}
