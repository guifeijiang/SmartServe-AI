package com.knowflow.ticket;

public final class TicketStatus {

    public static final String NEW = "NEW";
    public static final String ASSIGNED = "ASSIGNED";
    public static final String IN_PROGRESS = "IN_PROGRESS";
    public static final String RESOLVED = "RESOLVED";
    public static final String CLOSED = "CLOSED";

    private TicketStatus() {
    }

    public static boolean canTransit(String from, String to) {
        if (NEW.equals(from)) {
            return ASSIGNED.equals(to);
        }
        if (ASSIGNED.equals(from)) {
            return IN_PROGRESS.equals(to);
        }
        if (IN_PROGRESS.equals(from)) {
            return RESOLVED.equals(to);
        }
        if (RESOLVED.equals(from)) {
            return CLOSED.equals(to);
        }
        return false;
    }
}
