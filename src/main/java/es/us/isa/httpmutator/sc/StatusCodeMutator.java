package es.us.isa.httpmutator.sc;

import static es.us.isa.httpmutator.util.PropertyManager.readProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import es.us.isa.httpmutator.AbstractMutator;
import es.us.isa.httpmutator.AbstractOperator;
import es.us.isa.httpmutator.sc.operator.StatusCodeReplacementWith20XOperator;
import es.us.isa.httpmutator.sc.operator.StatusCodeReplacementWith40XOperator;
import es.us.isa.httpmutator.sc.operator.StatusCodeReplacementWith50XOperator;
import es.us.isa.httpmutator.util.OperatorNames;

public class StatusCodeMutator extends AbstractMutator {
    private final Random rand = new Random();

    public StatusCodeMutator() {
        super();
        prob = Float.parseFloat(readProperty("operator.sc.prob"));
        operators.put(OperatorNames.REPLACE_WITH_20X, new StatusCodeReplacementWith20XOperator());
        operators.put(OperatorNames.REPLACE_WITH_40X, new StatusCodeReplacementWith40XOperator());
        operators.put(OperatorNames.REPLACE_WITH_50X, new StatusCodeReplacementWith50XOperator());
    }

    public List<Integer> getAllMutants(int statusCode, double probability) {    
        List<Integer> mutants = new ArrayList<>();
        for (AbstractOperator operator : operators.values()) {
            mutants.add((Integer) operator.mutate(statusCode));
        }
        return mutants;
    }
}
