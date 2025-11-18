# Hash Map experiments

Little project to research properties of different hashing algorithms when applied to keys typical in OEMS system.


We want to see which hashing strategy produces less collisions on keys naming patterns we typically observe.

These naming strategies include

* simple `number` (e.g. "1761610691"), variable length
* something we internally call `mm` - key is constructed using constant prefix followed by base32 sequence number (e.g. "SOURCE13:T3AAA402"),
* UUID (e.g. "BE3F223A-3E39-443E-AEB8-3932A850C051")

TODO: описание хешей

## Hash functions

It's necessary to identify several optimal hash functions based on their computational speed and quality, as these parameters directly affect the performance of hash tables. 

You can find sources [here](https://github.com/kartelmode/hash-map-experiment/tree/main/src/main/java/hashing).

### Collisions rate

| Hash                   | number   | mm        | uuid     |
|------------------------|----------|-----------|----------|
| varHandle              | 13047618 | 13050294  | 13051122 |
| xxHash                 | 13052140 | 13054964  | 13052276 |
| default                | 16611402 | 19842334  | 13050206 |
| metroHash              | 13053294 | 13051070  | 13051639 |
| unrolledDefault        | 16611402 | 19842334  | 13050206 |
| vectorizedDefaultHash* | 13265343 | 19842334  | 13050653 |
| nativeHash             | 13039401 | 13076831  | 13048408 |
| faster                 | 33504432 | 33503224  | 13052028 |
| vhFaster               | 33504432 | 33503224  | 13052028 |

* - current version of this hashing allocates

The table presents the number of collisions for each key generation strategy and each hash function with 10^7 inserted keys. 

It can be observed that all hash functions demonstrate equally effective distribution when applied to random keys (UUID pattern). 
However, for other patterns, the results are less straightforward – default hash functions and faster variants exhibit a higher number of collisions compared to the other hash functions.

### Empty Bucket Ratio (smaller is better)

| Hash                  | number   | mm       | uuid     |
| --------------------- | -------- | -------- | -------- |
| varHandle             | 0.000051 | 0.000047 | 0.000048 |
| xxHash                | 0.000044 | 0.000041 | 0.000051 |
| default               | 0.002269 | 0.004358 | 0.000043 |
| metroHash             | 0.000040 | 0.000046 | 0.000044 |
| unrolledDefault       | 0.002269 | 0.004358 | 0.000043 |
| vectorizedDefaultHash | 0.001272 | 0.004358 | 0.000047 |
| nativeHash            | 0.000045 | 0.000054 | 0.000038 |
| faster                | 0.992943 | 0.984737 | 0.000044 |
| vhFaster              | 0.992943 | 0.984737 | 0.000044 |

The table above illustrates the ratio of unreachable hash table cells to the total number of elements. A cell is considered reachable if there exists a key for which the value of the hash function, modulo the table size, yields the index of that cell.

Based on these data, the faster and vhFaster hash functions don't access the majority of hash table cells even once, which leads to a significant degradation in performance for any hash table implementation.

### Index of Dispersion

(≈ 1 = good, » 1 = clustering, « 1 = suspicious)

| Hash                  | number      | mm         | uuid     |
| --------------------- | ----------- | ---------- | -------- |
| varHandle             | 0.990134    | 0.989610   | 0.990976 |
| xxHash                | 0.989583    | 0.990266   | 0.989941 |
| default               | 2.849634    | 2.899583   | 0.988512 |
| metroHash             | 0.990048    | 0.989624   | 0.990162 |
| unrolledDefault       | 2.849634    | 2.899583   | 0.988512 |
| vectorizedDefaultHash | 2.535957    | 2.900980   | 0.990611 |
| nativeHash            | 0.980283    | 1.010127   | 0.990428 |
| faster                | 1539.050781 | 858.425110 | 0.990534 |
| vhFaster              | 1539.050781 | 858.425110 | 0.990534 |

// TODO:

### Percentiles {P50, P90, P99, P999}

| Hash                  | number           | mm                | uuid             |
| --------------------- | ---------------- | ----------------- | ---------------- |
| varHandle             | {10, 14, 18, 21} | {10, 14, 18, 21}  | {10, 14, 18, 21} |
| xxHash                | {10, 14, 18, 21} | {10, 14, 18, 21}  | {10, 14, 18, 21} |
| default               | {9, 17, 25, 29}  | {9, 17, 25, 31}   | {10, 14, 18, 21} |
| metroHash             | {10, 14, 18, 21} | {10, 14, 18, 21}  | {10, 14, 18, 21} |
| unrolledDefault       | {9, 17, 25, 29}  | {9, 17, 25, 31}   | {10, 14, 18, 21} |
| vectorizedDefaultHash | {9, 17, 22, 25}  | {9, 17, 25, 31}   | {10, 14, 18, 21} |
| nativeHash            | {10, 14, 18, 21} | {10, 14, 18, 21}  | {10, 14, 18, 21} |
| faster                | {0, 0, 0, 2000}  | {0, 0, 288, 1472} | {10, 14, 18, 21} |
| vhFaster              | {0, 0, 0, 2000}  | {0, 0, 288, 1472} | {10, 14, 18, 21} |

The table presents percentile distributions (likely p50, p75, p90, p99) measuring the number of accesses per hash table cell, which directly indicates hash distribution quality across three key generation strategies.

These findings corroborate the data presented in the [Empty Bucket Ratio](#empty-bucket-ratio-smaller-is-better) table, which demonstrates significantly poorer distribution across hash table cells. Consequently, the performance of hash tables utilizing the `faster` and `vhFaster` hash functions will reach critical levels, despite their low computation time.

### Summary

Given their poor key distribution on the "number" and "mm" generation strategies, the "faster" and "vhFaster" hash functions can be excluded from further consideration in subsequent research.

In contrast, "xxHash," "metroHash," "varHandle," and "nativeHash" consistently demonstrate robust distribution across the entire set of hash table cells, regardless of the key generation strategy employed. Therefore, it is reasonable to select a subset of these functions for more detailed investigation. For example, "xxHash" may be chosen due to its widespread adoption, while "nativeHash" offers the advantage of faster computation compared to the other functions in this group.

The "default" and "unrolledDefault" hash functions exhibit less stable distribution results than those mentioned above. Nevertheless, both merit inclusion in future experiments due to their high computational efficiency.

## JMH Benchmark

| Hash Strategy   | Key Type | Map implementation | Score (ns/op) ± Error                | Notes   |
|-----------------|----------|--------------------|--------------------------------------|---------|
| xxHash          | number   | chaining           | 96.749             ± 2.714           |         |
| xxHash          | number   | linearprobe        | 82.806             ± 2.756           |         |
| xxHash          | number   | robinhood          | 63.400             ± 0.803           |         |
| xxHash          | mm       | chaining           | 100.416            ± 5.265           |         |
| xxHash          | mm       | linearprobe        | 85.518             ± 2.238           |         |
| xxHash          | mm       | robinhood          | 67.052             ± 2.235           |         |
| xxHash          | uuid     | chaining           | 123.539            ± 20.780          |         |
| xxHash          | uuid     | linearprobe        | 96.652             ± 0.763           |         |
| xxHash          | uuid     | robinhood          | 71.194             ± 0.565           |         |
| default         | number   | chaining           | 32.476             ± 0.379           |         |
| default         | number   | linearprobe        | 116.161            ± 0.455           |         |
| default         | number   | robinhood          | 47.980             ± 0.083           |         |
| default         | mm       | chaining           | 34.516             ± 1.273           |         |
| default         | mm       | linearprobe        | 3,706,548,479.653  ± 26,544,201.756  | Outlier |
| default         | mm       | robinhood          | 114,763.383        ± 166.797         | Outlier |
| default         | uuid     | chaining           | 129.748            ± 13.359          |         |
| default         | uuid     | linearprobe        | 119.056            ± 1.212           |         |
| default         | uuid     | robinhood          | 84.199             ± 1.362           |         |
| unrolledDefault | number   | chaining           | 31.281             ± 0.813           |         |
| unrolledDefault | number   | linearprobe        | 110.477            ± 0.811           |         |
| unrolledDefault | number   | robinhood          | 46.476             ± 0.612           |         |
| unrolledDefault | mm       | chaining           | 32.873             ± 0.193           |         |
| unrolledDefault | mm       | linearprobe        | 3,486,178,559.167  ± 169,273,692.885 | Outlier |
| unrolledDefault | mm       | robinhood          | 114,589.742        ± 83.337          | Outlier |
| unrolledDefault | uuid     | chaining           | 132.138            ± 10.183          |         |
| unrolledDefault | uuid     | linearprobe        | 121.661            ± 10.359          |         |
| unrolledDefault | uuid     | robinhood          | 87.834             ± 4.914           |         |
| nativeHash      | number   | chaining           | 86.195             ± 1.347           |         |
| nativeHash      | number   | linearprobe        | 67.745             ± 0.655           |         |
| nativeHash      | number   | robinhood          | 57.003             ± 0.823           |         |
| nativeHash      | mm       | chaining           | 88.288             ± 0.929           |         |
| nativeHash      | mm       | linearprobe        | 73.304             ± 1.138           |         |
| nativeHash      | mm       | robinhood          | 58.952             ± 1.300           |         |
| nativeHash      | uuid     | chaining           | 101.192            ± 24.068          |         |
| nativeHash      | uuid     | linearprobe        | 79.039             ± 1.733           |         |
| nativeHash      | uuid     | robinhood          | 60.807             ± 1.081           |         |

**Notes:**
- Outlier: combination of hash implementation that lead to "collapse" of open-addressing linear probing/robin hood implementations.
- All scores are in nanoseconds per operation (ns/op).

These results were performed by using naive compacting chain after deleting the key from the linear probing hashmap implementations.

The second version of linear probe implementation uses smarter compacting, where entries from the chain can be placed directly to empty cell that becomes empty after deletion or during compaction. 

Updated benchmark:

| Hash Strategy   | Key Type | Score (ns/op) ± Error         | Previous Score (ns/op) ± Error      |
|-----------------|----------|-------------------------------|-------------------------------------|
| xxHash          | number   | 79.525        ± 0.454         | 82.806            ± 2.756           |
| xxHash          | mm       | 83.803        ± 2.969         | 85.518            ± 2.238           |
| xxHash          | uuid     | 94.212        ± 1.816         | 96.652            ± 0.763           |
| default         | number   | 63.109        ± 1.282         | 116.161           ± 0.455           |
| default         | mm       | 1,549,683.071 ± 1,622,445.151 | 3,706,548,479.653 ± 26,544,201.756  |
| default         | uuid     | 118.914       ± 3.430         | 119.056           ± 1.212           |
| unrolledDefault | number   | 61.546        ± 0.379         | 110.477           ± 0.811           |
| unrolledDefault | mm       | 1,642,120.311 ± 1,546,165.077 | 3,486,178,559.167 ± 169,273,692.885 |
| unrolledDefault | uuid     | 119.870       ± 9.814         | 121.661           ± 10.359          |
| nativeHash      | number   | 66.201        ± 0.150         | 67.745            ± 0.655           |
| nativeHash      | mm       | 70.191        ± 1.130         | 73.304            ± 1.138           |
| nativeHash      | uuid     | 76.748        ± 0.390         | 79.039            ± 1.733           |

Optimisation of compacting chain gives about 5% performance improvement. As shown above, high improvement specifically for cases where elements lays in a large chain, that means about bad hash function for 50% load factor.

## Experiments with storing keys

Then 2 new versions of linear probe were implemented: nativeLinearprobe stores references to the keys and their parameters(address and length), rawLinearprobe stores copies of the keys as byte blocks with fixed length.

### number

| Map Class         | Score (ns/op) ± Error | L1-dcache-load-misses (#/op) ± Error | L1-dcache-loads (#/op) ± Error |
|-------------------|-----------------------|--------------------------------------|--------------------------------|
| linearprobe       | 75.254  ± 2.048       | 2.474 ± 0.449                        | 60.244 ± 1.548                 |
| nativeLinearprobe | 102.774 ± 2.339       | 5.813 ± 0.542                        | 73.564 ± 1.229                 |
| rawLinearprobe    | 89.855  ± 1.314       | 3.248 ± 0.099                        | 66.344 ± 5.871                 |

### mm

| Map Class         | Score (ns/op) ± Error | L1-dcache-load-misses (#/op) ± Error | L1-dcache-loads (#/op) ± Error |
|-------------------|-----------------------|--------------------------------------|--------------------------------|
| linearprobe       | 89.031  ± 2.027       | 2.663 ± 0.310                        | 63.309 ± 0.381                 |
| nativeLinearprobe | 111.904 ± 3.587       | 6.097 ± 0.762                        | 74.224 ± 3.162                 |
| rawLinearprobe    | 107.345 ± 5.653       | 3.369 ± 0.332                        | 68.218 ± 19.599                |

### uuid

| Map Class         | Score (ns/op) ± Error | L1-dcache-load-misses (#/op) ± Error | L1-dcache-loads (#/op) ± Error |
|-------------------|-----------------------|--------------------------------------|--------------------------------|
| linearprobe       | 113.748 ± 1.164       | 2.699 ± 0.148                        | 66.682 ± 4.502                 |
| nativeLinearprobe | 126.916 ± 5.668       | 6.111 ± 0.578                        | 78.539 ± 11.802                |
| rawLinearprobe    | 123.257 ± 5.396       | 3.453 ± 0.511                        | 71.522 ± 3.509                 |

The purpose of implementing these 2 maps was to check if avoiding dereferencing gives better performance or not. 
Here we can see that native linear probe version has ~2 times worse performance than default linear probe. 

Why so? Native version stores 3 additional arrays, so when we're trying to find value by the key, it accesses these 3 additional arrays and every cache miss in the entry array with high probability gives additional cache misses.

This is acceptable for rawLinearprobe too, because it stores additional array with proportional length to the capacity of hashmap.

## java.util.collections.HashMap

The data below demonstrates the results for hash map using `HashMap` from java.util.collections. For `number` and `mm` key naming strategy it works ideal with default hash function, whenever nativeHash is better for `uuid` naming strategy.

### xxHash

| Key Type | Score (ns/op) | Error | L1-dcache-load-misses (#/op) | Error | L1-dcache-loads (#/op) | Error |
|----------|---------------|-------|------------------------------|-------|------------------------|-------|
| number   | 58.471        | 1.320 | 2.263                        | 0.210 | 64.861                 | 5.354 |
| mm       | 62.892        | 4.111 | 2.343                        | 1.518 | 66.054                 | 24.938|
| uuid     | 76.812        | 2.818 | 2.644                        | 0.482 | 69.869                 | 43.915|

### default

| Key Type | Score (ns/op) | Error | L1-dcache-load-misses (#/op) | Error | L1-dcache-loads (#/op) | Error |
|----------|---------------|-------|------------------------------|-------|------------------------|-------|
| number   | 22.727        | 0.283 | 0.375                        | 0.276 | 67.625                 | 10.178|
| mm       | 35.500        | 0.217 | 1.388                        | 0.556 | 92.779                 | 2.693 |
| uuid     | 94.640        | 3.484 | 2.396                        | 0.731 | 100.120                | 14.689|

### unrolledDefault

| Key Type | Score (ns/op) | Error | L1-dcache-load-misses (#/op) | Error | L1-dcache-loads (#/op) | Error |
|----------|---------------|-------|------------------------------|-------|------------------------|-------|
| number   | 22.059        | 0.372 | 0.369                        | 0.180 | 79.233                 | 5.054 |
| mm       | 34.331        | 0.560 | 1.397                        | 0.435 | 111.564                | 22.118|
| uuid     | 91.850        | 6.611 | 2.366                        | 1.034 | 120.489                | 7.257 |

### NativeHash

| Key Type | Score (ns/op) | Error | L1-dcache-load-misses (#/op) | Error | L1-dcache-loads (#/op) | Error |
|----------|---------------|-------|------------------------------|-------|------------------------|-------|
| number   | 43.757        | 0.999 | 2.206                        | 0.241 | 49.692                 | 3.653 |
| mm       | 50.681        | 3.274 | 2.333                        | 1.438 | 53.991                 | 5.983 |
| uuid     | 59.658        | 2.141 | 2.437                        | 0.370 | 59.567                 | 8.855 |

### Allocations

```declarative
                              (keyNaming)      Score    Error   Units
benchmark                          number     41.716 ±  1.443   ns/op
benchmark:gc.alloc.rate            number    182.955 ±  6.418  MB/sec
benchmark:gc.alloc.rate.norm       number      8.000 ±  0.001    B/op
benchmark:gc.count                 number     21.000           counts
benchmark:gc.time                  number    425.000               ms
benchmark                              mm     45.773 ±  0.748   ns/op
benchmark:gc.alloc.rate                mm    166.689 ±  2.712  MB/sec
benchmark:gc.alloc.rate.norm           mm      8.000 ±  0.001    B/op
benchmark:gc.count                     mm     18.000           counts
benchmark:gc.time                      mm    329.000               ms
benchmark                            uuid     51.819 ±  0.883   ns/op
benchmark:gc.alloc.rate              uuid    147.243 ±  2.499  MB/sec
benchmark:gc.alloc.rate.norm         uuid      8.000 ±  0.001    B/op
benchmark:gc.count                   uuid     24.000           counts
benchmark:gc.time                    uuid    370.000               ms
```

From the info above we can see that Java's hashmap allocates data that is unacceptable in our case, because it causes calling garbage collection that is bad for latency in the real life.

// TODO: refactor
As we know that Java's implementation has allocations, so we don't recommend to user it in latency-sensitive systems.

## Leaderboards

// TODO: add plots

The tables below demonstrate statistical leaderboards(top-5) for every hash function for every key naming strategy. All results were measured on `Amazon EC2 r7iz.16xlarge` instance with 128 CPU cores.

### Number

| Rank | Implementation   | Parameter Set      | ns/op (± error)      |
|------|------------------|--------------------|----------------------|
| 1    | Java's HashMap   | unrolledDefault    | 22.059 ± 0.372       |
| 2    | Java's HashMap   | default            | 22.727 ± 0.283       |
| 3    | Chaining         | unrolledDefault    | 31.281 ± 0.813       |
| 4    | Chaining         | default            | 32.476 ± 0.379       |
| 5    | RobinHood        | unrolledDefault    | 44.937 ± 0.142       |

### MM

| Rank | Implementation | Parameter Set   | ns/op (± error)      |
|------|----------------|-----------------|----------------------|
| 1    | Chaining       | unrolledDefault | 32.873 ± 0.193       |
| 2    | Java's HashMa  | unrolledDefault | 34.331 ± 0.560       |
| 3    | Chaining       | default         | 34.516 ± 1.273       |
| 4    | Java's HashMap | default         | 35.500 ± 0.217       |
| 5    | RobinHood      | nativeHash      | 46.592 ± 0.482       |

### UUID

| Rank | Implementation   | Parameter Set      | ns/op (± error)      |
|------|------------------|--------------------|----------------------|
| 1    | RobinHood        | nativeHash         | 55.929 ± 0.159       |
| 2    | Java's HashMap   | nativeHash         | 59.658 ± 2.141       |
| 3    | RobinHood        | xxHash             | 68.338 ± 0.629       |
| 4    | Linearprobe      | nativeHash         | 76.748 ± 0.390       |
| 5    | Java's HashMap   | xxHash             | 76.812 ± 2.818       |


## Conclusion

For `Number` and `MM` it's recommended to use `unrolledDefault` hash function with `Chaining` hashmap implementation that is zero allocating in the long term.

For `UUID` it's recommended to use `nativeHash` hash function with `RobinHood` hashmap implementation that is zero allocating in the long term.

Other unknown for this experiment key naming strategies should use `nativeHash` or `xxHash` with `RobinHood`, because these combinations give more stable performance results for random and not only keys. 

Also it's a well-known [issue](https://vanilla-java.github.io/2018/08/15/Looking-at-randomness-and-performance-for-hash-codes.html) that default java's hash function isn't good enough at all.