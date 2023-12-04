/*
 * Copyright (c) Joseph Prichard 2023.
 */

package utils;

import java.util.List;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class StreamUtils {
    public static <A, B> Stream<Pair<A, B>> zip(List<A> listA, List<B> listB) {
        var n = Math.min(listA.size(), listB.size());
        return StreamSupport.stream(
            new Spliterators.AbstractSpliterator<>(n, Spliterator.SIZED) {
                private int i = 0;

                public boolean tryAdvance(Consumer<? super Pair<A, B>> action) {
                    if (i >= n) {
                        return false;
                    }
                    action.accept(new Pair<>(listA.get(i), listB.get(i)));
                    i++;
                    return true;
                }
            },
        false
        );
    }
}
