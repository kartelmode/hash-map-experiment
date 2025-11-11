# Hash Map experiments

Little project to research properties of different hashing algorithms when applied to keys typical in OEMS system.


We want to see which hashing strategy produces less collisions on keys naming patterns we typically observe.

These naming strategies include

* simple `number` (e.g. "1761610691"), variable length
* something we internally call `mm` - key is constructed using constant prefix followed by base32 sequence number (e.g. "SOURCE13:T3AAA402"),
* UUID (e.g. "BE3F223A-3E39-443E-AEB8-3932A850C051")

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

Then 2 new versions of linear probe were implemented: nativeLinearprobe stores references to the keys and their parameters(address and length), rawLinearprobe stores copies of the keys as byte blocks with fixed length. 

```declarative
Benchmark                                           (hashStrategy)  (keyNaming)         (mapClass)      Score     Error      Units
HashMapBenchmark.benchmark                                  xxHash       number        linearprobe     75.254 ±   2.048      ns/op
HashMapBenchmark.benchmark:L1-dcache-load-misses:u          xxHash       number        linearprobe      2.474 ±   0.449       #/op
HashMapBenchmark.benchmark:L1-dcache-loads:u                xxHash       number        linearprobe     60.244 ±   1.548       #/op
HashMapBenchmark.benchmark                                  xxHash       number  nativeLinearprobe    102.774 ±   2.339      ns/op
HashMapBenchmark.benchmark:L1-dcache-load-misses:u          xxHash       number  nativeLinearprobe      5.813 ±   0.542       #/op
HashMapBenchmark.benchmark:L1-dcache-loads:u                xxHash       number  nativeLinearprobe     73.564 ±   1.229       #/op
HashMapBenchmark.benchmark                                  xxHash       number     rawLinearprobe     89.855 ±   1.314      ns/op
HashMapBenchmark.benchmark:L1-dcache-load-misses:u          xxHash       number     rawLinearprobe      3.248 ±   0.099       #/op
HashMapBenchmark.benchmark:L1-dcache-loads:u                xxHash       number     rawLinearprobe     66.344 ±   5.871       #/op
HashMapBenchmark.benchmark                                  xxHash           mm        linearprobe     89.031 ±   2.027      ns/op
HashMapBenchmark.benchmark:L1-dcache-load-misses:u          xxHash           mm        linearprobe      2.663 ±   0.310       #/op
HashMapBenchmark.benchmark:L1-dcache-loads:u                xxHash           mm        linearprobe     63.309 ±   0.381       #/op
HashMapBenchmark.benchmark                                  xxHash           mm  nativeLinearprobe    111.904 ±   3.587      ns/op
HashMapBenchmark.benchmark:L1-dcache-load-misses:u          xxHash           mm  nativeLinearprobe      6.097 ±   0.762       #/op
HashMapBenchmark.benchmark:L1-dcache-loads:u                xxHash           mm  nativeLinearprobe     74.224 ±   3.162       #/op
HashMapBenchmark.benchmark                                  xxHash           mm     rawLinearprobe    107.345 ±   5.653      ns/op
HashMapBenchmark.benchmark:L1-dcache-load-misses:u          xxHash           mm     rawLinearprobe      3.369 ±   0.332       #/op
HashMapBenchmark.benchmark:L1-dcache-loads:u                xxHash           mm     rawLinearprobe     68.218 ±  19.599       #/op
HashMapBenchmark.benchmark                                  xxHash         uuid        linearprobe    113.748 ±   1.164      ns/op
HashMapBenchmark.benchmark:L1-dcache-load-misses:u          xxHash         uuid        linearprobe      2.699 ±   0.148       #/op
HashMapBenchmark.benchmark:L1-dcache-loads:u                xxHash         uuid        linearprobe     66.682 ±   4.502       #/op
HashMapBenchmark.benchmark                                  xxHash         uuid  nativeLinearprobe    126.916 ±   5.668      ns/op
HashMapBenchmark.benchmark:L1-dcache-load-misses:u          xxHash         uuid  nativeLinearprobe      6.111 ±   0.578       #/op
HashMapBenchmark.benchmark:L1-dcache-loads:u                xxHash         uuid  nativeLinearprobe     78.539 ±  11.802       #/op
HashMapBenchmark.benchmark                                  xxHash         uuid     rawLinearprobe    123.257 ±   5.396      ns/op
HashMapBenchmark.benchmark:L1-dcache-load-misses:u          xxHash         uuid     rawLinearprobe      3.453 ±   0.511       #/op
HashMapBenchmark.benchmark:L1-dcache-loads:u                xxHash         uuid     rawLinearprobe     71.522 ±   3.509       #/op
```

The purpose of implementing these 2 maps was to check if avoiding dereferencing gives better performance or not. 
Here we can see that native linear probe version has ~2 times worse performance than default linear probe. 

Why so? Native version stores 3 additional arrays, so when we're trying to find value by the key, it accesses these 3 additional arrays and every cache miss in the entry array with high probability gives additional cache misses.

This is acceptable for rawLinearprobe, because it stores additional array with proportional length to the capacity of hashmap. 