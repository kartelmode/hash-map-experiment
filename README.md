# Hash Map experiments

Little project to research properties of different hashing algorithms when applied to keys typical in OEMS system.


We want to see which hashing strategy produces less collisions on keys naming patterns we typically observe.

These naming strategies include

* simple `number` (e.g. "1761610691"), variable length
* something we internally call `mm` - key is constructed using constant prefix followed by base32 sequence number (e.g. "SOURCE13:T3AAA402"),
* UUID (e.g. "BE3F223A-3E39-443E-AEB8-3932A850C051")

TODO: header, описание хешей, добавить выводы из таблиц

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

## 🧩 Empty Bucket Ratio (smaller is better)

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

## ⚖️ Index of Dispersion

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


## 📊 Percentiles {P50, P90, P99, P999}

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



## JMH Benchmark

```declarative

  (hashStrategy)  (keyNaming)   (mapClass)           Score           Error  Units
          xxHash       number     chaining          96.749 +-         2.714  ns/op
          xxHash       number  linearprobe          82.806 +-         2.756  ns/op
          xxHash       number    robinhood          63.400 +-         0.803  ns/op
          xxHash           mm     chaining         100.416 +-         5.265  ns/op
          xxHash           mm  linearprobe          85.518 +-         2.238  ns/op
          xxHash           mm    robinhood          67.052 +-         2.235  ns/op
          xxHash         uuid     chaining         123.539 +-        20.780  ns/op
          xxHash         uuid  linearprobe          96.652 +-         0.763  ns/op
          xxHash         uuid    robinhood          71.194 +-         0.565  ns/op
         default       number     chaining          32.476 +-         0.379  ns/op
         default       number  linearprobe         116.161 +-         0.455  ns/op
         default       number    robinhood          47.980 +-         0.083  ns/op
         default           mm     chaining          34.516 +-         1.273  ns/op
         default           mm  linearprobe  3706548479.653 +-  26544201.756  ns/op <===
         default           mm    robinhood      114763.383 +-       166.797  ns/op <---
         default         uuid     chaining         129.748 +-        13.359  ns/op
         default         uuid  linearprobe         119.056 +-         1.212  ns/op
         default         uuid    robinhood          84.199 +-         1.362  ns/op
 unrolledDefault       number     chaining          31.281 +-         0.813  ns/op
 unrolledDefault       number  linearprobe         110.477 +-         0.811  ns/op
 unrolledDefault       number    robinhood          46.476 +-         0.612  ns/op
 unrolledDefault           mm     chaining          32.873 +-         0.193  ns/op
 unrolledDefault           mm  linearprobe  3486178559.167 +- 169273692.885  ns/op <===
 unrolledDefault           mm    robinhood      114589.742 +-        83.337  ns/op <---
 unrolledDefault         uuid     chaining         132.138 +-        10.183  ns/op
 unrolledDefault         uuid  linearprobe         121.661 +-        10.359  ns/op
 unrolledDefault         uuid    robinhood          87.834 +-         4.914  ns/op
      nativeHash       number     chaining          86.195 +-         1.347  ns/op
      nativeHash       number  linearprobe          67.745 +-         0.655  ns/op
      nativeHash       number    robinhood          57.003 +-         0.823  ns/op
      nativeHash           mm     chaining          88.288 +-         0.929  ns/op
      nativeHash           mm  linearprobe          73.304 +-         1.138  ns/op
      nativeHash           mm    robinhood          58.952 +-         1.300  ns/op
      nativeHash         uuid     chaining         101.192 +-        24.068  ns/op
      nativeHash         uuid  linearprobe          79.039 +-         1.733  ns/op
      nativeHash         uuid    robinhood          60.807 +-         1.081  ns/op
```
Where `<===` and `<---` mark combination of hash implementation that lead to "collapse" of open-addressing linear probing/robin hood implementations.
Map implementation has to traverse thousands of filled buckets.

This results was performed by using naive compacting chain after deleting the key from the map.

The second version of linear probe implementation uses smarter compacting, where entries from the chain can be placed directly to empty cell that becomes empty after deletion or during compaction. 

Updated benchmark:

```declarative

  (hashStrategy)  (keyNaming)   (mapClass)              Score          Error  Units | Previous Score            Error
         xxHash       number  linearprobe               79.525 ±       0.454  ns/op |         82.806 +-         2.756  ns/op
         xxHash           mm  linearprobe               83.803 ±       2.969  ns/op |         85.518 +-         2.238  ns/op
         xxHash         uuid  linearprobe               94.212 ±       1.816  ns/op |         96.652 +-         0.763  ns/op
        default       number  linearprobe               63.109 ±       1.282  ns/op |        116.161 +-         0.455  ns/op
        default           mm  linearprobe          1549683.071 ± 1622445.151  ns/op | 3706548479.653 +-  26544201.756  ns/op
        default         uuid  linearprobe              118.914 ±       3.430  ns/op |        119.056 +-         1.212  ns/op
unrolledDefault       number  linearprobe               61.546 ±       0.379  ns/op |        110.477 +-         0.811  ns/op
unrolledDefault           mm  linearprobe          1642120.311 ± 1546165.077  ns/op | 3486178559.167 +- 169273692.885  ns/op
unrolledDefault         uuid  linearprobe              119.870 ±       9.814  ns/op |        121.661 +-        10.359  ns/op
     nativeHash       number  linearprobe               66.201 ±       0.150  ns/op |         67.745 +-         0.655  ns/op
     nativeHash           mm  linearprobe               70.191 ±       1.130  ns/op |         73.304 +-         1.138  ns/op  
     nativeHash         uuid  linearprobe               76.748 ±       0.390  ns/op |         79.039 +-         1.733  ns/op
```

Optimisation of compacting chain gives about 5% performance improvement. As shown above, high improvement specifically for cases where elements lays in a large chain, that means about bad hash function for 50% load factor.

## Experiments with storing keys

Then 2 new versions of linear probe were implemented: nativeLinearprobe stores references to the keys and their parameters(address and length), rawLinearprobe stores copies of the keys as byte blocks with fixed length.

```declarative
Benchmark                          (hashStrategy)  (keyNaming)         (mapClass)      Score     Error      Units
benchmark                                  xxHash       number        linearprobe     75.254 ±   2.048      ns/op
benchmark:L1-dcache-load-misses:u          xxHash       number        linearprobe      2.474 ±   0.449       #/op
benchmark:L1-dcache-loads:u                xxHash       number        linearprobe     60.244 ±   1.548       #/op
benchmark                                  xxHash       number  nativeLinearprobe    102.774 ±   2.339      ns/op
benchmark:L1-dcache-load-misses:u          xxHash       number  nativeLinearprobe      5.813 ±   0.542       #/op
benchmark:L1-dcache-loads:u                xxHash       number  nativeLinearprobe     73.564 ±   1.229       #/op
benchmark                                  xxHash       number     rawLinearprobe     89.855 ±   1.314      ns/op
benchmark:L1-dcache-load-misses:u          xxHash       number     rawLinearprobe      3.248 ±   0.099       #/op
benchmark:L1-dcache-loads:u                xxHash       number     rawLinearprobe     66.344 ±   5.871       #/op
benchmark                                  xxHash           mm        linearprobe     89.031 ±   2.027      ns/op
benchmark:L1-dcache-load-misses:u          xxHash           mm        linearprobe      2.663 ±   0.310       #/op
benchmark:L1-dcache-loads:u                xxHash           mm        linearprobe     63.309 ±   0.381       #/op
benchmark                                  xxHash           mm  nativeLinearprobe    111.904 ±   3.587      ns/op
benchmark:L1-dcache-load-misses:u          xxHash           mm  nativeLinearprobe      6.097 ±   0.762       #/op
benchmark:L1-dcache-loads:u                xxHash           mm  nativeLinearprobe     74.224 ±   3.162       #/op
benchmark                                  xxHash           mm     rawLinearprobe    107.345 ±   5.653      ns/op
benchmark:L1-dcache-load-misses:u          xxHash           mm     rawLinearprobe      3.369 ±   0.332       #/op
benchmark:L1-dcache-loads:u                xxHash           mm     rawLinearprobe     68.218 ±  19.599       #/op
benchmark                                  xxHash         uuid        linearprobe    113.748 ±   1.164      ns/op
benchmark:L1-dcache-load-misses:u          xxHash         uuid        linearprobe      2.699 ±   0.148       #/op
benchmark:L1-dcache-loads:u                xxHash         uuid        linearprobe     66.682 ±   4.502       #/op
benchmark                                  xxHash         uuid  nativeLinearprobe    126.916 ±   5.668      ns/op
benchmark:L1-dcache-load-misses:u          xxHash         uuid  nativeLinearprobe      6.111 ±   0.578       #/op
benchmark:L1-dcache-loads:u                xxHash         uuid  nativeLinearprobe     78.539 ±  11.802       #/op
benchmark                                  xxHash         uuid     rawLinearprobe    123.257 ±   5.396      ns/op
benchmark:L1-dcache-load-misses:u          xxHash         uuid     rawLinearprobe      3.453 ±   0.511       #/op
benchmark:L1-dcache-loads:u                xxHash         uuid     rawLinearprobe     71.522 ±   3.509       #/op
```

The purpose of implementing these 2 maps was to check if avoiding dereferencing gives better performance or not. 
Here we can see that native linear probe version has ~2 times worse performance than default linear probe. 

Why so? Native version stores 3 additional arrays, so when we're trying to find value by the key, it accesses these 3 additional arrays and every cache miss in the entry array with high probability gives additional cache misses.

This is acceptable for rawLinearprobe too, because it stores additional array with proportional length to the capacity of hashmap.

## java.util.collections.HashMap

The data below demonstrates the results for hash map using `HashMap` from java.util.collections. For `number` and `mm` key naming strategy it works ideal with default hash function, whenever nativeHash is better for `uuid` naming strategy.

```declarative
xxHash
Benchmark                           (keyNaming)     Score     Error      Units
benchmark                               number     58.471 ±   1.320      ns/op
benchmark:L1-dcache-load-misses:u       number      2.263 ±   0.210       #/op
benchmark:L1-dcache-loads:u             number     64.861 ±   5.354       #/op
benchmark                                   mm     62.892 ±   4.111      ns/op
benchmark:L1-dcache-load-misses:u           mm      2.343 ±   1.518       #/op
benchmark:L1-dcache-loads:u                 mm     66.054 ±  24.938       #/op
benchmark                                 uuid     76.812 ±   2.818      ns/op
benchmark:L1-dcache-load-misses:u         uuid      2.644 ±   0.482       #/op
benchmark:L1-dcache-loads:u               uuid     69.869 ±  43.915       #/op

default

Benchmark                           (keyNaming)     Score    Error      Units
benchmark                               number     22.727 ±  0.283      ns/op
benchmark:L1-dcache-load-misses:u       number      0.375 ±  0.276       #/op
benchmark:L1-dcache-loads:u             number     67.625 ± 10.178       #/op
benchmark                                   mm     35.500 ±  0.217      ns/op
benchmark:L1-dcache-load-misses:u           mm      1.388 ±  0.556       #/op
benchmark:L1-dcache-loads:u                 mm     92.779 ±  2.693       #/op
benchmark                                 uuid     94.640 ±  3.484      ns/op
benchmark:L1-dcache-load-misses:u         uuid      2.396 ±  0.731       #/op
benchmark:L1-dcache-loads:u               uuid    100.120 ± 14.689       #/op

unrolledDefault
Benchmark                          (keyNaming)      Score     Error      Units
benchmark                               number     22.059 ±   0.372      ns/op
benchmark:L1-dcache-load-misses:u       number      0.369 ±   0.180       #/op
benchmark:L1-dcache-loads:u             number     79.233 ±   5.054       #/op
benchmark                                   mm     34.331 ±   0.560      ns/op
benchmark:L1-dcache-load-misses:u           mm      1.397 ±   0.435       #/op
benchmark:L1-dcache-loads:u                 mm    111.564 ±  22.118       #/op
benchmark                                 uuid     91.850 ±   6.611      ns/op
benchmark:L1-dcache-load-misses:u         uuid      2.366 ±   1.034       #/op
benchmark:L1-dcache-loads:u               uuid    120.489 ±   7.257       #/op

NativeHash
Benchmark                          (keyNaming)      Score     Error      Units
benchmark                               number     43.757 ±   0.999      ns/op
benchmark:L1-dcache-load-misses:u       number      2.206 ±   0.241       #/op
benchmark:L1-dcache-loads:u             number     49.692 ±   3.653       #/op
benchmark                                   mm     50.681 ±   3.274      ns/op
benchmark:L1-dcache-load-misses:u           mm      2.333 ±   1.438       #/op
benchmark:L1-dcache-loads:u                 mm     53.991 ±   5.983       #/op
benchmark                                 uuid     59.658 ±   2.141      ns/op
benchmark:L1-dcache-load-misses:u         uuid      2.437 ±   0.370       #/op
benchmark:L1-dcache-loads:u               uuid     59.567 ±   8.855       #/op
```

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
As we know that Java's implementation has allocations, so we don't include it in this recommendations for latency-sensitive systems.

## Leaderboards

// TODO: add plots

The tables below demonstrate statistical leaderboards for every hash function for every key naming strategy. All results were measured on `Amazon EC2 r7iz.xlarge` instance with 128 CPU cores.

// TODO: add ns/op (smaller is better)

// TODO: add top-5 for every strategy

### Number

| xxHash                          | default                         | unrolledDefault                 | nativeHash                      |
|---------------------------------|---------------------------------|---------------------------------|---------------------------------|
| Java's HashMap (58.471 ± 1.320) | Java's HashMap (22.727 ± 0.283) | Java's HashMap (22.059 ± 0.372) | Java's HashMap (43.757 ± 0.999) |
| RobinHood      (58.937 ± 2.635) | Chaining       (32.476 ± 0.379) | Chaining       (31.281 ± 0.813) | RobinHood      (46.592 ± 0.482) |
| Linearprobe    (71.057 ± 0.630) | RobinHood      (47.016 ± 0.371) | RobinHood      (44.937 ± 0.142) | Linearprobe    (57.325 ± 1.668) |
| Chaining       (96.749 ± 2.714) | Linearprobe    (56.645 ± 1.041) | Linearprobe    (56.238 ± 0.333) | Chaining       (86.195 ± 1.347) |


### MM

| xxHash                                | default                                    | unrolledDefault                            | nativeHash                      |
|---------------------------------------|--------------------------------------------|--------------------------------------------|---------------------------------|
| RobinHood           (62.718  ± 1.538) | Chaining       (34.516      ±       1.273) | Chaining       (32.873      ±       0.193) | RobinHood      (46.592 ± 0.482) |
| Java's HashMap      (66.649  ± 6.655) | Java's HashMap (35.500      ±       0.217) | Java's HashMap (34.331      ±       0.560) | Java's HashMap (50.681 ± 3.274) |
| Linearprobe         (77.839  ± 1.860) | RobinHood      (116818.565  ±    1747.045) | RobinHood      (116701.295  ±    1742.646) | Linearprobe    (65.593 ± 0.782) |
| Chaining            (100.416 ± 5.265) | Linearprobe    (1371434.037 ± 1302460.722) | Linearprobe    (1521891.429 ± 1591490.765) | Chaining       (88.288 ± 0.929) |


### UUID

| xxHash                            | default                           | unrolledDefault                   | nativeHash                        |
|-----------------------------------|-----------------------------------|-----------------------------------|-----------------------------------|
| RobinHood      (68.338 ±   0.629) | RobinHood      (84.143  ±  3.176) | RobinHood      (88.421  ±  5.056) | RobinHood      (55.929  ±  0.159) |
| Java's HashMap (76.812  ±  2.818) | Java's HashMap (94.640  ±  3.484) | Java's HashMap (94.640  ±  3.484) | Java's HashMap (59.658  ±  2.141) |
| Linearprobe    (106.472 ± 20.139) | Linearprobe    (120.989 ±  5.045) | Linearprobe    (125.302 ±  1.280) | Linearprobe    (76.748  ±  0.390) |
| Chaining       (123.539 ± 20.780) | Chaining       (129.748 ± 13.359) | Chaining       (132.138 ± 10.183) | Chaining       (101.192 ± 24.068) |


## Conclusion

For `Number` and `MM` it's recommended to use `unrolledDefault` hash function with `Chaining` hashmap implementation that is zero allocating in the long term.

For `UUID` it's recommended to use `nativeHash` hash function with `RobinHood` hashmap implementation that is zero allocating in the long term.

Other unknown for this experiment key naming strategies should use `nativeHash` or `xxHash` with `RobinHood`, because these combinations give more stable performance results for random and not only keys. 

Also it's a well-known [issue](https://vanilla-java.github.io/2018/08/15/Looking-at-randomness-and-performance-for-hash-codes.html) that default java's hash function isn't good enough at all.