package uk.co.omegaprime.btree;

import org.openjdk.jmh.Main;
import org.openjdk.jmh.annotations.*;

import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

public class BTreeMapBenchmark {
    // -XX:+UnlockDiagnosticVMOptions -XX:CompileCommand=print,*BTreeMap.get

    public static final int KEYS = 100_000;

    @State(Scope.Thread)
    public static class MyState {
        public final Random random = new Random();
        public final Map<Integer, Integer> warmedMap = BTreeMap.create();
        public Integer key;

        private int nextKey() {
            return random.nextInt(KEYS);
        }

        @Setup(Level.Trial)
        public void trialSetup() {
            for (int i = 0; i < KEYS; i++) {
                warmedMap.put(nextKey(), i);
            }
        }

        @Setup(Level.Invocation)
        public void invocationSetup() {
            key = nextKey();
        }
    }

    @Benchmark
    public void put(MyState state) {
        state.warmedMap.put(state.key, 1337);
    }

    @Benchmark
    public void get(MyState state) {
        state.warmedMap.get(state.key);
    }

    public static void main(String[] args) throws Exception {
        Main.main(args);
    }
}
