/*
 * Copyright (c) 2024. Robin Hillyard
 */

package com.phasmidsoftware.dsaipg.sort.linearithmic;

import com.phasmidsoftware.dsaipg.sort.*;
import com.phasmidsoftware.dsaipg.sort.elementary.InsertionSort;
import com.phasmidsoftware.dsaipg.util.Config;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

import static com.phasmidsoftware.dsaipg.util.Config_Benchmark.*;

/**
 * Class MergeSort.
 *
 * @param <X> the underlying comparable type.
 */
public class MergeSort<X extends Comparable<X>> extends SortWithComparableHelper<X> {

    public static final String DESCRIPTION = "MergeSort";

    /**
     * Constructor for MergeSort
     * <p>
     * NOTE this is used only by unit tests, using its own instrumented helper.
     *
     * @param helper an explicit instance of Helper to be used.
     */
    public MergeSort(Helper<X> helper) {
        super(helper);
        insertionSort = setupInsertionSort(helper);
    }

    /**
     * Constructor for MergeSort
     *
     * @param N      the number elements we expect to sort.
     * @param nRuns  the expected number of runs.
     * @param config the configuration.
     */
    public MergeSort(int N, int nRuns, Config config) {
        super(DESCRIPTION + getConfigString(config), N, nRuns, config);
        insertionSort = setupInsertionSort(getHelper());
    }

    private InsertionSort<X> setupInsertionSort(final Helper<X> helper) {
        return new InsertionSort<>(helper.clone("MergeSort: insertion sort"));
    }

    public X[] sort(X[] xs, boolean makeCopy) {
        getHelper().init(xs.length);
        additionalMemory(xs.length);
        X[] result = makeCopy ? Arrays.copyOf(xs, xs.length) : xs;
        sort(result, 0, result.length);
        additionalMemory(-xs.length);
        return result;
    }

    public void sort(X[] a, int from, int to) {
        Config config = helper.getConfig();
        boolean noCopy = config.getBoolean(MERGESORT, NOCOPY);
        // CONSIDER don't copy but just allocate according to the xs/aux interchange optimization
        @SuppressWarnings("unchecked") X[] aux = noCopy ? helper.copyArray(a) : (X[]) new Comparable[a.length];
        sort(a, aux, from, to);
    }

    private void sort(X[] a, X[] aux, int from, int to) {
        Config config = helper.getConfig();
        boolean insurance = config.getBoolean(MERGESORT, INSURANCE);
        boolean noCopy = config.getBoolean(MERGESORT, NOCOPY);
        if (to <= from + helper.cutoff()) { // XXX check that a cutoff value of 1 effectively stops the cutoff mechanism.
            insertionSort.sort(a, from, to);
            return;
        }

        int mid = from + (to - from) / 2;

        // test noCopy first

        if(noCopy) {
            sort(aux, a, from, mid);
            sort(aux, a, mid, to);
            if(insurance && helper.less(aux, mid - 1, mid)) {
                helper.copyBlock(aux, from, a, from, to - from);
            } else {
                merge(aux, a, from, mid, to);
            }
        } else {
            sort(a, aux, from, mid);
            sort(a, aux, mid, to);
            helper.copyBlock(a, from, aux, from, to - from);
            if(insurance && helper.less(a, mid - 1, mid)) {
                return;
            } else {
                merge(aux, a, from, mid, to);
            }
        }
    }

//        if(insurance) {
//            sort(aux, a, from, mid);
//            sort(aux, a, mid, to);
//            if(noCopy && helper.less(aux[mid - 1], aux[mid])) {
//                helper.copyBlock(aux, from, a, from, to - from);
//                // we still need to do a copy
//                // merge is a slow way of doing a copy
//            } else {
//                merge(aux, a, from, mid, to);
//            }
//
//        } else {
//            sort(a, aux, from, mid);
//            sort(a, aux, mid, to);
//            helper.copyBlock(a, from, aux, from, to - from);
//            if(noCopy && helper.less(a[mid - 1], a[mid])) {
//                return;
//            }
//            merge(a, aux, from, mid, to);

            // TO BE IMPLEMENTED  : implement merge sort with insurance and no-copy optimizations
//throw new RuntimeException("implementation missing");


    // CONSIDER combine with MergeSortBasic, perhaps.
    private void merge(X[] sorted, X[] result, int from, int mid, int to) {
        int i = from;
        int j = mid;
        X v = helper.get(sorted, i);
        X w = helper.get(sorted, j);
        for (int k = from; k < to; k++) {
            if (i >= mid) {
                helper.copy(w, result, k);
                if (++j < to) w = helper.get(sorted, j);
            } else if (j >= to) {
                helper.copy(v, result, k);
                if (++i < mid) v = helper.get(sorted, i);
            } else if (helper.less(w, v)) {
                helper.incrementFixes(mid - i);
                helper.copy(w, result, k);
                if (++j < to) w = helper.get(sorted, j);
            } else {
                helper.copy(v, result, k);
                if (++i < mid) v = helper.get(sorted, i);
            }
        }
    }

    public static final String MERGESORT = "mergesort";
    public static final String NOCOPY = "nocopy";
    public static final String INSURANCE = "insurance";

    private static String getConfigString(Config config) {
        StringBuilder stringBuilder = new StringBuilder();
        if (config.getBoolean(MERGESORT, INSURANCE)) stringBuilder.append(" with insurance comparison");
        if (config.getBoolean(MERGESORT, NOCOPY)) stringBuilder.append(" with no copy");
        int cutoff = config.getInt(HELPER, CUTOFF, CUTOFF_DEFAULT);
        if (cutoff != CUTOFF_DEFAULT) {
            if (cutoff == 1) stringBuilder.append(" with no cutoff");
            else stringBuilder.append(" with cutoff ").append(cutoff);
        }
        return stringBuilder.toString();
    }

    private final InsertionSort<X> insertionSort;


    private int arrayMemory = -1;
    private int additionalMemory;
    private int maxMemory;

    public void setArrayMemory(int n) {
        if (arrayMemory == -1) {
            arrayMemory = n;
            additionalMemory(n);
        }
    }

    public void additionalMemory(int n) {
        additionalMemory += n;
        if (maxMemory < additionalMemory) maxMemory = additionalMemory;
    }

    public Double getMemoryFactor() {
        if (arrayMemory == -1)
            throw new SortException("Array memory has not been set");
        return 1.0 * maxMemory / arrayMemory;
    }

//    public static void main(String args[]) throws IOException {
//        Config config = Config.load(MergeSort.class);
//        final int k = 5;
//        final int N = (int) Math.pow(2, k);
//        final Helper<Integer> helper1 = HelperFactory.create("insertion sort", N, setupConfig2("true", "0", "1", "", "", "true", "false"));
//        final Integer[] xs = helper1.random(Integer.class, r -> r.nextInt(10000));
//
//        Sort<Integer> s = new MergeSort<>(xs.length, 1, config);
//        Integer[] ys = s.sort(xs);
//        for(Integer i : ys) {
//            System.out.print(i + " ");
//        }
//    }

}
