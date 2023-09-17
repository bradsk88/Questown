package ca.bradj.questown.jobs;

import com.google.common.collect.ImmutableList;

import java.util.List;

public class FarmerActions {
    public static List<FarmerJob.FarmerAction> forWheatSeeds() {
        return ImmutableList.of(
                FarmerJob.FarmerAction.PLANT,
                FarmerJob.FarmerAction.TILL,
                FarmerJob.FarmerAction.COMPOST
        );
    }
}
