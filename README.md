# btreemap

This is a simple and high-performance Java B-tree that is intended to be used as a drop-in replacement for java.util.TreeMap
in situations where the vanilla binary trees used by the JDK are just too slow.

For maps with 100k keys, performance is like this on my machine:

```
Benchmark                   Mode  Cnt        Score        Error  Units
BTreeMapBenchmark.get       thrpt   20  5500161.400 ±  70850.009  ops/s
BTreeMapBenchmark.lowerKey  thrpt   20  4720565.667 ± 259185.836  ops/s
BTreeMapBenchmark.put       thrpt   20  3757808.623 ±  37792.128  ops/s
```

With `TreeMap` the numbers are about 50%-60% worse:

```
Benchmark                   Mode  Cnt        Score       Error  Units
BTreeMapBenchmark.get       thrpt   20  3404585.749 ± 47180.578  ops/s
BTreeMapBenchmark.lowerKey  thrpt   20  3255788.782 ± 54390.949  ops/s
BTreeMapBenchmark.put       thrpt   20  2542375.846 ± 38924.217  ops/s
```

The source code lives at [GitHub](https://github.com/batterseapower/btreemap/). If you just want some JARs, check out [Maven Central](http://mvnrepository.com/artifact/uk.co.omega-prime/btreemap/).

[![Build Status](https://travis-ci.org/batterseapower/btreemap.svg?branch=master)](https://travis-ci.org/batterseapower/btreemap)
