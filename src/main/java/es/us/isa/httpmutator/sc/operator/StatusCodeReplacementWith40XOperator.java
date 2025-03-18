package es.us.isa.httpmutator.sc.operator;

import es.us.isa.httpmutator.AbstractOperator;
import es.us.isa.httpmutator.util.OperatorNames;
import static es.us.isa.httpmutator.util.PropertyManager.readProperty;

/**
 * Operator that mutates the status code of an HTTP response by changing it to a different one.
 */
public class StatusCodeReplacementWith40XOperator extends AbstractOperator{
    private final int[] SC40X = {400, 401, 403, 404, 409};

    public StatusCodeReplacementWith40XOperator() {
        super();
        weight = Float.parseFloat(readProperty("operator.sc.weight." + OperatorNames.REPLACE_WITH_40X));
    }

    @Override
    protected Object doMutate(Object statusCode) {
        int newStatusCode = SC40X[rand2.nextInt(SC40X.length)];
        while (newStatusCode == (int)statusCode) {
            newStatusCode = SC40X[rand2.nextInt(SC40X.length)];
        }
        return newStatusCode;
    }
}
