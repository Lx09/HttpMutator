package es.us.isa.httpmutator.stats;

import java.util.HashMap;
import java.util.Map;

public class OperatorUsageStats {

    private static final OperatorUsageStats instance = new OperatorUsageStats();

    private OperatorUsageStats() {
    }

    public static OperatorUsageStats getInstance() {
        return instance;
    }

    private final Map<String, Integer> counter = new HashMap<>();

    public void increment(String packageName) {
        counter.merge(packageName, 1, Integer::sum);
    }

    public Map<String, Integer> getStats() {
        return new HashMap<>(counter);
    }

    @Override
    public String toString() {
        // output string sorted by key
        return counter.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey() + ": " + e.getValue())
                .reduce("", (a, b) -> a + b + "\n");
    }
}
