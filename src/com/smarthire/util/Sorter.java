package com.smarthire.util;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A from-scratch generic merge sort, used in place of Collections.sort()
 * for ranking applications by score. Included deliberately as a DSA
 * showcase - O(n log n) guaranteed, stable, classic divide-and-conquer -
 * rather than relying entirely on library sorting for a college project.
 */
public class Sorter {

    /** Returns a new sorted list; does not mutate the input. */
    public static <T> List<T> mergeSort(List<T> input, Comparator<T> comparator) {
        List<T> copy = new ArrayList<>(input);
        if (copy.size() <= 1) return copy;
        return sort(copy, comparator);
    }

    private static <T> List<T> sort(List<T> list, Comparator<T> comparator) {
        int n = list.size();
        if (n <= 1) return list;

        int mid = n / 2;
        List<T> left = sort(new ArrayList<>(list.subList(0, mid)), comparator);
        List<T> right = sort(new ArrayList<>(list.subList(mid, n)), comparator);
        return merge(left, right, comparator);
    }

    private static <T> List<T> merge(List<T> left, List<T> right, Comparator<T> comparator) {
        List<T> result = new ArrayList<>(left.size() + right.size());
        int i = 0, j = 0;
        while (i < left.size() && j < right.size()) {
            // <= keeps the sort stable: equal elements keep their original relative order.
            if (comparator.compare(left.get(i), right.get(j)) <= 0) {
                result.add(left.get(i++));
            } else {
                result.add(right.get(j++));
            }
        }
        while (i < left.size()) result.add(left.get(i++));
        while (j < right.size()) result.add(right.get(j++));
        return result;
    }
}
