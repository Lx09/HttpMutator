package es.us.isa.httpmutator.body.value.string0.operator;

import static es.us.isa.httpmutator.util.PropertyManager.readProperty;

import org.apache.commons.lang3.RandomStringUtils;

import es.us.isa.httpmutator.AbstractOperator;
import es.us.isa.httpmutator.util.OperatorNames;

/**
 * Operator that mutates a string by adding, removing or replacing one
 * single character.
 *
 * @author Alberto Martin-Lopez
 */
public class StringMutationOperator extends AbstractOperator {

    public StringMutationOperator() {
        super();
        weight = Float.parseFloat(readProperty("operator.value.string.weight." + OperatorNames.MUTATE));
    }
    
    @Override
    protected Object doMutate(Object stringObject) {
        String string = (String)stringObject;
        StringBuilder sb = new StringBuilder(string);
        int charPosition = string.length()==0 ? 0 : rand1.nextInt(0, string.length()-1);
        float randomValue = rand2.nextFloat();

        if (randomValue <= 1f/3 && string.length()>0) { // Remove char
            sb.deleteCharAt(charPosition);
        } else if (randomValue <= 2f/3 || string.length()==0)  { // Add char
            sb.insert(charPosition, RandomStringUtils.random(1, true, true));
        } else { // Replace char
            sb.deleteCharAt(charPosition);
            sb.insert(charPosition, RandomStringUtils.random(1, true, true));
        }

        return sb.toString();
    }
}
