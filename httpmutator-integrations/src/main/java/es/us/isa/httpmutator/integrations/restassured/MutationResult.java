package es.us.isa.httpmutator.integrations.restassured;

public class MutationResult {
    private final String label;
    private final int total;
    private final int killed;

    public MutationResult(String label, int total, int killed) {
        this.label = label;
        this.total = total;
        this.killed = killed;
    }

    public int getTotal() {
        return total;
    }

    public int getKilled() {
        return killed;
    }

    public double getScore() {
        return total == 0 ? 1.0 : (double) killed / total;
    }
}
