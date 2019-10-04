package es.us.isa.restmutator.mutator.value.string0.operator;

import es.us.isa.restmutator.mutator.AbstractOperator;
import org.apache.commons.lang3.RandomStringUtils;

import static es.us.isa.restmutator.util.PropertyManager.readProperty;

/**
 * Operator that mutates a string by adding, removing or replacing one
 * single character.
 *
 * @author Alberto Martin-Lopez
 */
public class StringMutationOperator extends AbstractOperator {

    public StringMutationOperator() {
        super();
        weight = Float.parseFloat(readProperty("operator.value.string.weight.mutate"));
    }

    public Object mutate(Object stringObject) {
        String string = (String)stringObject;
        StringBuilder sb = new StringBuilder(string);
        int charPosition = rand1.nextInt(0, string.length()-1);
        float randomValue = rand2.nextFloat();

        if (randomValue <= 1f/3) { // Remove char
            sb.deleteCharAt(charPosition);
        } else if (randomValue <= 2f/3)  { // Add char
            sb.insert(charPosition, RandomStringUtils.random(1, true, true));
        } else { // Replace char
            sb.deleteCharAt(charPosition);
            sb.insert(charPosition, RandomStringUtils.random(1, true, true));
        }

        return sb.toString();
    }
}
