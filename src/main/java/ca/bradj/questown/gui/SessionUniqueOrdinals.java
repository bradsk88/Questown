package ca.bradj.questown.gui;

import ca.bradj.questown.QT;
import ca.bradj.questown.jobs.IStatus;
import ca.bradj.questown.jobs.IStatusFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * SessionUniqueOrdinals is a singleton that assigns a unique integer to each
 * IStatus that has been registered with it. These ordinals should not be
 * stored to disk because they only have meaning within a single server run.
 */
public class SessionUniqueOrdinals {

    private static List<IStatus<?>> claimed = new ArrayList<>();

    private static Unknown UNKNOWN = new Unknown();
    private static class Unknown implements IStatus<Unknown> {
        @Override
        public IStatusFactory<Unknown> getFactory() {
            return null;
        }

        @Override
        public boolean isGoingToJobsite() {
            return false;
        }

        @Override
        public boolean isDroppingLoot() {
            return false;
        }

        @Override
        public boolean isCollectingSupplies() {
            return false;
        }

        @Override
        public String name() {
            return "UNRECOGNIZED STATUS ORDINAL";
        }

        @Override
        public boolean isUnset() {
            return false;
        }

        @Override
        public boolean isAllowedToTakeBreaks() {
            return false;
        }

        @Override
        public String getCategoryId() {
            return null;
        }
    };

    public static IStatus<?> getStatus(int i) {
        if (i < 0 || i >= claimed.size()) {
            QT.JOB_LOGGER.error("Unrecognized ordinal provided: {}", i);
            return UNKNOWN;
        }
        return claimed.get(i);
    }

    public static int getOrdinal(IStatus<?> newStatus) {
        int i = claimed.indexOf(newStatus);
        if (i < 0) {
            QT.GUI_LOGGER.error("Trying to get ordinal for unregistered status");
        }
        return i;
    }

    public static <S extends IStatus<?>> S register(S s) {
        claimed.add(s);
        return s;
    }
}
