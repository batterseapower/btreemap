package uk.co.omegaprime.btreemap;

import java.util.Comparator;

/**
 * An equivalent to {@link java.util.Comparator} for primitive {@code {{K.unboxed}}} values.
 * <p>
 * You may optionally have your custom comparators implement this interface, which may improve
 * the ability of the JVM to optimize the code by removing unnecessary boxing.
 * <p>
 * The default implementation of the {@code Comparator} interface simply throws a {@code NullPointerException}
 * if a {@code null} boxed value is supplied.
 */
public interface Comparator<$K$> extends Comparator<{{K.boxed}}> {
    int compare{{K.name}}($K$ x, $K$ y);

    default int compare({{K.boxed}} x, {{K.boxed}} y) {
        return compare{{K.name}}(x, y);
    }

    /** Construct an unboxed, specialized comparator using a boxed one */
    static Comparator<$K$> unbox(Comparator<? super {{K.boxed}}> that) {
        if (that instanceof Comparator<$K$>) {
            return (Comparator<$K$>)that;
        } else {
            return new Comparator<$K$>() {
                @Override
                public int compare{{K.name}}($K$ x, $K$ y) {
                    return that.compare(x, y);
                }

                @Override
                public int compare({{K.boxed}} x, {{K.boxed}} y) {
                    return that.compare(x, y);
                }
            };
        }
    }
}
