package es.us.isa.httpmutator.body.value.string0.operator;

import static es.us.isa.httpmutator.util.PropertyManager.readProperty;

import org.apache.commons.lang3.RandomStringUtils;

import es.us.isa.httpmutator.AbstractOperator;
import es.us.isa.httpmutator.util.OperatorNames;

/**
 * Operator that mutates a string by replacing it with a boundary value, namely
 * an empty string, an uppercase string, a lowercase string, a string of minimum
 * length or a string of maximum length.
 *
 * @author Alberto Martin-Lopez
 */
public class StringBoundaryOperator extends AbstractOperator {

    private int minLength;          // Minimum length of the randomly generated string
    private int maxLength;          // Maximum length of the randomly generated string
    private String uppercaseString;
    private String lowercaseString;

    public StringBoundaryOperator() {
        super();
        weight = Float.parseFloat(readProperty("operator.value.string.weight." + OperatorNames.BOUNDARY));
        minLength = Integer.parseInt(readProperty("operator.value.string.length.min"));
        maxLength = Integer.parseInt(readProperty("operator.value.string.length.max"));
        uppercaseString = readProperty("operator.value.string.uppercase");
        lowercaseString = readProperty("operator.value.string.lowercase");
    }

    @Override
    protected Object doMutate(Object element) {
        float randomValue = rand2.nextFloat();

        if (randomValue <= 1f/5) { // Empty string
            return "";
        } else if (randomValue <= 2f/5)  { // String of minLength
            return RandomStringUtils.random(minLength, true, true);
        } else if (randomValue <= 3f/5)  { // String of maxLength
            return RandomStringUtils.random(maxLength, true, true);
        } else if (randomValue <= 4f/5)  { // Lowercase string
            return lowercaseString;
        } else { // Uppercase string
            return uppercaseString;
        }
    }


}
