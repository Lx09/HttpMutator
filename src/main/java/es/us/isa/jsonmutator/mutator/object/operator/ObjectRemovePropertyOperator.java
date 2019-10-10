package es.us.isa.jsonmutator.mutator.object.operator;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Lists;
import es.us.isa.jsonmutator.mutator.AbstractOperator;
import es.us.isa.jsonmutator.util.OperatorNames;
import java.util.List;
import static es.us.isa.jsonmutator.util.PropertyManager.readProperty;

/**
 * Operator that mutates an object by removing a number of properties from it.
 *
 * @author Alberto Martin-Lopez
 */
public class ObjectRemovePropertyOperator extends AbstractOperator {

    private int maxRemovedProperties;     // Maximum number of properties to remove to the object
    private int minRemovedProperties;     // Minimum number of properties to remove to the object

    public ObjectRemovePropertyOperator() {
        super();
        weight = Float.parseFloat(readProperty("operator.object.weight." + OperatorNames.REMOVE_PROPERTY));
        maxRemovedProperties = Integer.parseInt(readProperty("operator.object.removedProperties.max"));
        minRemovedProperties = Integer.parseInt(readProperty("operator.object.removedProperties.min"));
    }

    public Object mutate(Object objectNodeObject) {
        ObjectNode objectNode = (ObjectNode)objectNodeObject;
        int randomValue;
        int removedProperties = rand1.nextInt(minRemovedProperties, maxRemovedProperties); // Remove between min and max properties to object
        List<String> propertyNames = Lists.newArrayList(objectNode.fieldNames());

        for (int i=1; i<=removedProperties; i++)
            if (objectNode.size() > 0) {
                randomValue = rand2.nextInt(propertyNames.size());
                objectNode.remove(propertyNames.get(randomValue)); // Remove a random property
                propertyNames.remove(randomValue); // Remove property from the list of properties to possibly remove
            }

        return objectNode;
    }
}
