package minidb.plan;

/** A query cannot be bound or is outside the supported single-table subset. */
public final class PlanningException extends IllegalArgumentException {
    public PlanningException(String message) {
        super(message);
    }
}
