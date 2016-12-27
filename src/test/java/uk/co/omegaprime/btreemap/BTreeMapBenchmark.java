package uk.co.omegaprime.btreemap;

import org.openjdk.jmh.Main;
import org.openjdk.jmh.annotations.*;

import java.util.*;

public class BTreeMapBenchmark {
    // -XX:+UnlockDiagnosticVMOptions -XX:CompileCommand=print,*BTreeMap.get

    public static final int KEYS = 1_000_000;

    @State(Scope.Thread)
    public static class MyState {
        private static NavigableMap<Integer, Integer> createMap() {
            return
                //new it.unimi.dsi.fastutil.ints.Int2IntAVLTreeMap();
                //IntIntBTreeMap.create();
                //BTreeMap.create();
                new TreeMap<>();
                //DBMaker.newMemoryDB().make().createTreeMap("foo").make();
        }

        public final Random random = new Random();
        public final List<NavigableMap<Integer, Integer>> warmedMaps = new ArrayList<>();
        public final NavigableMap<Integer, Integer> warmedMap = createMap();
        public Integer key;
        private int next = 0;

        private int nextKey() {
            return random.nextInt(KEYS);
        }

        @Setup(Level.Trial)
        public void trialSetup() {
            // TODO: not the best because of key collisions
            for (int i = 0; i < KEYS; i++) {
                warmedMap.put(nextKey(), i);
            }

            for (int i = 0; i < KEYS; i += 100) {
                final NavigableMap<Integer, Integer> map = createMap();
                for (int j = 0; j < 100; j++) {
                    map.put(nextKey(), j);
                }

                warmedMaps.add(map);
            }
        }

        @Setup(Level.Invocation)
        public void invocationSetup() {
            key = nextKey();
        }

        public NavigableMap<Integer, Integer> smallWarmedMap() {
            final NavigableMap<Integer, Integer> result = warmedMaps.get(next);
            next = (next + 1) % warmedMaps.size();
            return result;
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

    @Benchmark
    public void lowerKey(MyState state) {
        state.warmedMap.lowerKey(state.key);
    }

    @Benchmark
    public void putSmall(MyState state) {
        state.smallWarmedMap().put(state.key, 1337);
    }

    @Benchmark
    public void getSmall(MyState state) {
        state.smallWarmedMap().get(state.key);
    }

    @Benchmark
    public void lowerKeySmall(MyState state) {
        state.smallWarmedMap().lowerKey(state.key);
    }

    public static void main(String[] args) throws Exception {
        Main.main(args);
    }
}
