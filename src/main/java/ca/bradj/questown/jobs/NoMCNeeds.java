package ca.bradj.questown.jobs;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java.util.Map;

public class NoMCNeeds {
    public static int getActualIndex(
            int needIndex,
            ImmutableMap<Integer, Integer> qtyRequiredAtStates
    ) {
        int toSpend = needIndex;
        for (Map.Entry<Integer, Integer> qty : qtyRequiredAtStates.entrySet()) {
            if (toSpend <= qty.getValue()) {
                return qty.getKey();
            }
            toSpend -= qty.getValue();
        }
        ImmutableList<Integer> list = qtyRequiredAtStates.keySet().asList();
        return list.get(list.size() - 1);
    }
}
