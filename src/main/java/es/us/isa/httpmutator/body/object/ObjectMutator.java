package es.us.isa.httpmutator.body.object;

import com.fasterxml.jackson.databind.node.ObjectNode;

import es.us.isa.httpmutator.body.AbstractObjectOrArrayMutator;
import es.us.isa.httpmutator.body.object.operator.ObjectAddElementOperator;
import es.us.isa.httpmutator.body.object.operator.ObjectRemoveElementOperator;
import es.us.isa.httpmutator.body.object.operator.ObjectRemoveObjectTypeElementOperator;
import es.us.isa.httpmutator.body.value.common.operator.ChangeTypeOperator;
import es.us.isa.httpmutator.body.value.common.operator.NullOperator;
import es.us.isa.httpmutator.util.OperatorNames;

/**
 * Given a set of object mutation operators, the ObjectMutator selects one based
 * on their weights and returns the mutated object.
 *
 * @author Alberto Martin-Lopez
 */
public class ObjectMutator extends AbstractObjectOrArrayMutator {

    public ObjectMutator() {
        super();
    }

    public void resetOperators() {
        operators.clear();
        operators.put(OperatorNames.REMOVE_ELEMENT, new ObjectRemoveElementOperator());
        operators.put(OperatorNames.REMOVE_OBJECT_ELEMENT, new ObjectRemoveObjectTypeElementOperator());
        operators.put(OperatorNames.ADD_ELEMENT, new ObjectAddElementOperator());
        operators.put(OperatorNames.NULL, new NullOperator(ObjectNode.class));
        operators.put(OperatorNames.CHANGE_TYPE, new ChangeTypeOperator(ObjectNode.class));
    }

    public void resetFirstLevelOperators() {
        operators.remove(OperatorNames.NULL);
        operators.remove(OperatorNames.CHANGE_TYPE);
    }
}
