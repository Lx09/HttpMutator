package es.us.isa.httpmutator.body.value.null0;

import com.fasterxml.jackson.databind.node.NullNode;

import es.us.isa.httpmutator.AbstractMutator;
import es.us.isa.httpmutator.body.value.common.operator.ChangeTypeOperator;
import es.us.isa.httpmutator.util.OperatorNames;
import static es.us.isa.httpmutator.util.PropertyManager.readProperty;

/**
 * Given a set of null mutation operators, the NullMutator selects one based
 * on their weights and returns the mutated null.
 *
 * @author Alberto Martin-Lopez
 */
public class NullMutator extends AbstractMutator {

    public NullMutator() {
        super();
        prob = Float.parseFloat(readProperty("operator.value.null.prob"));
        operators.put(OperatorNames.CHANGE_TYPE, new ChangeTypeOperator(NullNode.class));
    }
}
