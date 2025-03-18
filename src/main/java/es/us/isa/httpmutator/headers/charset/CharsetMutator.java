package es.us.isa.httpmutator.headers.charset;

import es.us.isa.httpmutator.AbstractMutator;
import es.us.isa.httpmutator.body.value.common.operator.NullOperator;
import es.us.isa.httpmutator.headers.charset.operator.CharsetReplacementOperator;
import es.us.isa.httpmutator.util.OperatorNames;
import static es.us.isa.httpmutator.util.PropertyManager.readProperty;

public class CharsetMutator extends AbstractMutator {
    public CharsetMutator() {
        super();
        prob = Float.parseFloat(readProperty("operator.sc.prob"));
        operators.put(OperatorNames.REPLACE, new CharsetReplacementOperator());
        operators.put(OperatorNames.NULL, new NullOperator(CharsetMutator.class));
    }

}
