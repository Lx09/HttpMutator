package es.us.isa.httpmutator.headers.mediaType;

import es.us.isa.httpmutator.AbstractMutator;
import es.us.isa.httpmutator.body.value.common.operator.NullOperator;
import es.us.isa.httpmutator.headers.mediaType.operator.MediaTypeReplacementOperator;
import es.us.isa.httpmutator.util.OperatorNames;
import static es.us.isa.httpmutator.util.PropertyManager.readProperty;

public class MediaTypeMutator extends AbstractMutator {
    public MediaTypeMutator() {
        super();
        prob = Float.parseFloat(readProperty("operator.header.mediaType.prob"));
        operators.put(OperatorNames.REPLACE, new MediaTypeReplacementOperator());
        operators.put(OperatorNames.NULL, new NullOperator(MediaTypeMutator.class));
    }
}
