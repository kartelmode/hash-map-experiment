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

This is acceptable for rawLinearprobe, because it stores additional array with proportional length to the capacity of hashmap.

```declarative
default
Benchmark                          (keyNaming)    Score     Error      Units
benchmark                               number   23.086 ±   0.261      ns/op
benchmark:L1-dcache-load-misses:u       number    0.361 ±   0.474       #/op
benchmark:L1-dcache-loads:u             number   73.606 ±   3.857       #/op
benchmark                                   mm   26.555 ±   0.608      ns/op
benchmark:L1-dcache-load-misses:u           mm    0.567 ±   0.513       #/op
benchmark:L1-dcache-loads:u                 mm   73.848 ±  15.163       #/op
benchmark                                 uuid   92.757 ±   3.888      ns/op
benchmark:L1-dcache-load-misses:u         uuid    2.394 ±   1.031       #/op
benchmark:L1-dcache-loads:u               uuid   98.843 ±  16.895       #/op

xxHash
aws#29|Benchmark                                           (keyNaming)  (maxInactiveKeys)  Mode  Cnt    Score     Error      Units
aws#29|HashMapBenchmark.benchmark                               number               4096  avgt    9   58.111 ±   1.885      ns/op
aws#29|HashMapBenchmark.benchmark:CPI                           number               4096  avgt    3    0.793 ±   0.207  clks/insn
aws#29|HashMapBenchmark.benchmark:IPC                           number               4096  avgt    3    1.261 ±   0.327  insns/clk
aws#29|HashMapBenchmark.benchmark:L1-dcache-load-misses:u       number               4096  avgt    3    2.254 ±   0.132       #/op
aws#29|HashMapBenchmark.benchmark:L1-dcache-loads:u             number               4096  avgt    3   62.263 ±   5.178       #/op
aws#29|HashMapBenchmark.benchmark:L1-dcache-stores:u            number               4096  avgt    3   18.157 ±   1.270       #/op
aws#29|HashMapBenchmark.benchmark:L1-icache-load-misses:u       number               4096  avgt    3    0.006 ±   0.008       #/op
aws#29|HashMapBenchmark.benchmark:branch-misses:u               number               4096  avgt    3    0.288 ±   0.028       #/op
aws#29|HashMapBenchmark.benchmark:branches:u                    number               4096  avgt    3   42.662 ±   0.611       #/op
aws#29|HashMapBenchmark.benchmark:cycles:u                      number               4096  avgt    3  239.990 ±  68.300       #/op
aws#29|HashMapBenchmark.benchmark:dTLB-load-misses:u            number               4096  avgt    3    0.607 ±   0.130       #/op
aws#29|HashMapBenchmark.benchmark:dTLB-loads:u                  number               4096  avgt    3   61.613 ±  11.691       #/op
aws#29|HashMapBenchmark.benchmark:dTLB-store-misses:u           number               4096  avgt    3    0.003 ±   0.002       #/op
aws#29|HashMapBenchmark.benchmark:dTLB-stores:u                 number               4096  avgt    3   18.258 ±   1.901       #/op
aws#29|HashMapBenchmark.benchmark:iTLB-load-misses:u            number               4096  avgt    3    0.001 ±   0.001       #/op
aws#29|HashMapBenchmark.benchmark:instructions:u                number               4096  avgt    3  302.607 ±  14.665       #/op
aws#29|HashMapBenchmark.benchmark                                   mm               4096  avgt    9   66.649 ±   6.655      ns/op
aws#29|HashMapBenchmark.benchmark:CPI                               mm               4096  avgt    3    0.802 ±   0.948  clks/insn
aws#29|HashMapBenchmark.benchmark:IPC                               mm               4096  avgt    3    1.251 ±   1.525  insns/clk
aws#29|HashMapBenchmark.benchmark:L1-dcache-load-misses:u           mm               4096  avgt    3    2.428 ±   1.735       #/op
aws#29|HashMapBenchmark.benchmark:L1-dcache-loads:u                 mm               4096  avgt    3   65.850 ±  22.173       #/op
aws#29|HashMapBenchmark.benchmark:L1-dcache-stores:u                mm               4096  avgt    3   18.304 ±   2.778       #/op
aws#29|HashMapBenchmark.benchmark:L1-icache-load-misses:u           mm               4096  avgt    3    0.007 ±   0.006       #/op
aws#29|HashMapBenchmark.benchmark:branch-misses:u                   mm               4096  avgt    3    0.292 ±   0.071       #/op
aws#29|HashMapBenchmark.benchmark:branches:u                        mm               4096  avgt    3   49.584 ±   2.930       #/op
aws#29|HashMapBenchmark.benchmark:cycles:u                          mm               4096  avgt    3  275.235 ± 341.303       #/op
aws#29|HashMapBenchmark.benchmark:dTLB-load-misses:u                mm               4096  avgt    3    0.674 ±   0.859       #/op
aws#29|HashMapBenchmark.benchmark:dTLB-loads:u                      mm               4096  avgt    3   64.851 ±   8.634       #/op
aws#29|HashMapBenchmark.benchmark:dTLB-store-misses:u               mm               4096  avgt    3    0.003 ±   0.001       #/op
aws#29|HashMapBenchmark.benchmark:dTLB-stores:u                     mm               4096  avgt    3   18.365 ±   4.667       #/op
aws#29|HashMapBenchmark.benchmark:iTLB-load-misses:u                mm               4096  avgt    3    0.001 ±   0.001       #/op
aws#29|HashMapBenchmark.benchmark:instructions:u                    mm               4096  avgt    3  343.319 ±  25.761       #/op
aws#29|HashMapBenchmark.benchmark                                 uuid               4096  avgt    9   75.715 ±   1.765      ns/op
aws#29|HashMapBenchmark.benchmark:CPI                             uuid               4096  avgt    3    0.919 ±   0.108  clks/insn
aws#29|HashMapBenchmark.benchmark:IPC                             uuid               4096  avgt    3    1.088 ±   0.128  insns/clk
aws#29|HashMapBenchmark.benchmark:L1-dcache-load-misses:u         uuid               4096  avgt    3    2.647 ±   0.389       #/op
aws#29|HashMapBenchmark.benchmark:L1-dcache-loads:u               uuid               4096  avgt    3   71.766 ±  17.781       #/op
aws#29|HashMapBenchmark.benchmark:L1-dcache-stores:u              uuid               4096  avgt    3   28.583 ±   0.528       #/op
aws#29|HashMapBenchmark.benchmark:L1-icache-load-misses:u         uuid               4096  avgt    3    0.009 ±   0.015       #/op
aws#29|HashMapBenchmark.benchmark:branch-misses:u                 uuid               4096  avgt    3    0.295 ±   0.076       #/op
aws#29|HashMapBenchmark.benchmark:branches:u                      uuid               4096  avgt    3   38.144 ±   4.838       #/op
aws#29|HashMapBenchmark.benchmark:cycles:u                        uuid               4096  avgt    3  314.980 ±  29.392       #/op
aws#29|HashMapBenchmark.benchmark:dTLB-load-misses:u              uuid               4096  avgt    3    0.700 ±   0.188       #/op
aws#29|HashMapBenchmark.benchmark:dTLB-loads:u                    uuid               4096  avgt    3   70.645 ±  13.814       #/op
aws#29|HashMapBenchmark.benchmark:dTLB-store-misses:u             uuid               4096  avgt    3    0.013 ±   0.029       #/op
aws#29|HashMapBenchmark.benchmark:dTLB-stores:u                   uuid               4096  avgt    3   28.651 ±   4.135       #/op
aws#29|HashMapBenchmark.benchmark:iTLB-load-misses:u              uuid               4096  avgt    3    0.001 ±   0.001       #/op
aws#29|HashMapBenchmark.benchmark:instructions:u                  uuid               4096  avgt    3  342.841 ±  22.887       #/op

Unrolled
aws#33|Benchmark                                           (keyNaming)  (maxInactiveKeys)  Mode  Cnt    Score     Error      Units
aws#33|HashMapBenchmark.benchmark                               number               4096  avgt    9   22.162 ±   0.201      ns/op
aws#33|HashMapBenchmark.benchmark:CPI                           number               4096  avgt    3    0.342 ±   0.083  clks/insn
aws#33|HashMapBenchmark.benchmark:IPC                           number               4096  avgt    3    2.925 ±   0.705  insns/clk
aws#33|HashMapBenchmark.benchmark:L1-dcache-load-misses:u       number               4096  avgt    3    0.360 ±   0.030       #/op
aws#33|HashMapBenchmark.benchmark:L1-dcache-loads:u             number               4096  avgt    3   79.363 ±   2.887       #/op
aws#33|HashMapBenchmark.benchmark:L1-dcache-stores:u            number               4096  avgt    3   13.027 ±   0.927       #/op
aws#33|HashMapBenchmark.benchmark:L1-icache-load-misses:u       number               4096  avgt    3    0.004 ±   0.002       #/op
aws#33|HashMapBenchmark.benchmark:branch-misses:u               number               4096  avgt    3    0.086 ±   0.022       #/op
aws#33|HashMapBenchmark.benchmark:branches:u                    number               4096  avgt    3   36.471 ±   3.156       #/op
aws#33|HashMapBenchmark.benchmark:cycles:u                      number               4096  avgt    3  103.566 ±  14.829       #/op
aws#33|HashMapBenchmark.benchmark:dTLB-load-misses:u            number               4096  avgt    3    0.030 ±   0.005       #/op
aws#33|HashMapBenchmark.benchmark:dTLB-loads:u                  number               4096  avgt    3   81.903 ±   6.224       #/op
aws#33|HashMapBenchmark.benchmark:dTLB-store-misses:u           number               4096  avgt    3   ≈ 10⁻⁴                 #/op
aws#33|HashMapBenchmark.benchmark:dTLB-stores:u                 number               4096  avgt    3   12.681 ±   1.959       #/op
aws#33|HashMapBenchmark.benchmark:iTLB-load-misses:u            number               4096  avgt    3   ≈ 10⁻³                 #/op
aws#33|HashMapBenchmark.benchmark:instructions:u                number               4096  avgt    3  302.906 ±  45.948       #/op
aws#33|HashMapBenchmark.benchmark                                   mm               4096  avgt    9   26.371 ±   0.217      ns/op
aws#33|HashMapBenchmark.benchmark:CPI                               mm               4096  avgt    3    0.306 ±   0.036  clks/insn
aws#33|HashMapBenchmark.benchmark:IPC                               mm               4096  avgt    3    3.268 ±   0.379  insns/clk
aws#33|HashMapBenchmark.benchmark:L1-dcache-load-misses:u           mm               4096  avgt    3    0.574 ±   0.212       #/op
aws#33|HashMapBenchmark.benchmark:L1-dcache-loads:u                 mm               4096  avgt    3   88.569 ±   3.861       #/op
aws#33|HashMapBenchmark.benchmark:L1-dcache-stores:u                mm               4096  avgt    3   15.555 ±   0.710       #/op
aws#33|HashMapBenchmark.benchmark:L1-icache-load-misses:u           mm               4096  avgt    3    0.005 ±   0.003       #/op
aws#33|HashMapBenchmark.benchmark:branch-misses:u                   mm               4096  avgt    3    0.194 ±   0.017       #/op
aws#33|HashMapBenchmark.benchmark:branches:u                        mm               4096  avgt    3   43.391 ±   1.849       #/op
aws#33|HashMapBenchmark.benchmark:cycles:u                          mm               4096  avgt    3  103.275 ±   9.022       #/op
aws#33|HashMapBenchmark.benchmark:dTLB-load-misses:u                mm               4096  avgt    3    0.011 ±   0.013       #/op
aws#33|HashMapBenchmark.benchmark:dTLB-loads:u                      mm               4096  avgt    3   87.766 ±   9.371       #/op
aws#33|HashMapBenchmark.benchmark:dTLB-store-misses:u               mm               4096  avgt    3   ≈ 10⁻⁴                 #/op
aws#33|HashMapBenchmark.benchmark:dTLB-stores:u                     mm               4096  avgt    3   15.610 ±   1.123       #/op
aws#33|HashMapBenchmark.benchmark:iTLB-load-misses:u                mm               4096  avgt    3   ≈ 10⁻³                 #/op
aws#33|HashMapBenchmark.benchmark:instructions:u                    mm               4096  avgt    3  337.473 ±  26.695       #/op
aws#33|HashMapBenchmark.benchmark                                 uuid               4096  avgt    9   92.472 ±   4.220      ns/op
aws#33|HashMapBenchmark.benchmark:CPI                             uuid               4096  avgt    3    0.763 ±   0.338  clks/insn
aws#33|HashMapBenchmark.benchmark:IPC                             uuid               4096  avgt    3    1.311 ±   0.584  insns/clk
aws#33|HashMapBenchmark.benchmark:L1-dcache-load-misses:u         uuid               4096  avgt    3    2.398 ±   0.598       #/op
aws#33|HashMapBenchmark.benchmark:L1-dcache-loads:u               uuid               4096  avgt    3  121.850 ±   6.490       #/op
aws#33|HashMapBenchmark.benchmark:L1-dcache-stores:u              uuid               4096  avgt    3   12.919 ±   5.230       #/op
aws#33|HashMapBenchmark.benchmark:L1-icache-load-misses:u         uuid               4096  avgt    3    0.010 ±   0.006       #/op
aws#33|HashMapBenchmark.benchmark:branch-misses:u                 uuid               4096  avgt    3    0.303 ±   0.019       #/op
aws#33|HashMapBenchmark.benchmark:branches:u                      uuid               4096  avgt    3   47.512 ±   4.718       #/op
aws#33|HashMapBenchmark.benchmark:cycles:u                        uuid               4096  avgt    3  379.669 ± 161.702       #/op
aws#33|HashMapBenchmark.benchmark:dTLB-load-misses:u              uuid               4096  avgt    3    0.709 ±   0.313       #/op
aws#33|HashMapBenchmark.benchmark:dTLB-loads:u                    uuid               4096  avgt    3  122.920 ±   8.494       #/op
aws#33|HashMapBenchmark.benchmark:dTLB-store-misses:u             uuid               4096  avgt    3    0.007 ±   0.019       #/op
aws#33|HashMapBenchmark.benchmark:dTLB-stores:u                   uuid               4096  avgt    3   13.480 ±   2.239       #/op
aws#33|HashMapBenchmark.benchmark:iTLB-load-misses:u              uuid               4096  avgt    3    0.001 ±   0.001       #/op
aws#33|HashMapBenchmark.benchmark:instructions:u                  uuid               4096  avgt    3  497.391 ±  10.197       #/op

NativeHash
aws#37|Benchmark                                           (keyNaming)  (maxInactiveKeys)  Mode  Cnt    Score     Error      Units
aws#37|HashMapBenchmark.benchmark                               number               4096  avgt    9   43.721 ±   1.008      ns/op
aws#37|HashMapBenchmark.benchmark:CPI                           number               4096  avgt    3    0.970 ±   0.283  clks/insn
aws#37|HashMapBenchmark.benchmark:IPC                           number               4096  avgt    3    1.031 ±   0.298  insns/clk
aws#37|HashMapBenchmark.benchmark:L1-dcache-load-misses:u       number               4096  avgt    3    2.230 ±   0.402       #/op
aws#37|HashMapBenchmark.benchmark:L1-dcache-loads:u             number               4096  avgt    3   51.242 ±   4.802       #/op
aws#37|HashMapBenchmark.benchmark:L1-dcache-stores:u            number               4096  avgt    3   10.016 ±   0.268       #/op
aws#37|HashMapBenchmark.benchmark:L1-icache-load-misses:u       number               4096  avgt    3    0.005 ±   0.003       #/op
aws#37|HashMapBenchmark.benchmark:branch-misses:u               number               4096  avgt    3    0.284 ±   0.009       #/op
aws#37|HashMapBenchmark.benchmark:branches:u                    number               4096  avgt    3   32.816 ±   3.866       #/op
aws#37|HashMapBenchmark.benchmark:cycles:u                      number               4096  avgt    3  184.027 ±  50.602       #/op
aws#37|HashMapBenchmark.benchmark:dTLB-load-misses:u            number               4096  avgt    3    0.614 ±   0.298       #/op
aws#37|HashMapBenchmark.benchmark:dTLB-loads:u                  number               4096  avgt    3   51.084 ±   5.573       #/op
aws#37|HashMapBenchmark.benchmark:dTLB-store-misses:u           number               4096  avgt    3    0.007 ±   0.006       #/op
aws#37|HashMapBenchmark.benchmark:dTLB-stores:u                 number               4096  avgt    3   10.005 ±   2.201       #/op
aws#37|HashMapBenchmark.benchmark:iTLB-load-misses:u            number               4096  avgt    3    0.001 ±   0.001       #/op
aws#37|HashMapBenchmark.benchmark:instructions:u                number               4096  avgt    3  189.647 ±  23.596       #/op
aws#37|HashMapBenchmark.benchmark                                   mm               4096  avgt    9   51.173 ±   5.154      ns/op
aws#37|HashMapBenchmark.benchmark:CPI                               mm               4096  avgt    3    1.030 ±   1.242  clks/insn
aws#37|HashMapBenchmark.benchmark:IPC                               mm               4096  avgt    3    0.973 ±   1.142  insns/clk
aws#37|HashMapBenchmark.benchmark:L1-dcache-load-misses:u           mm               4096  avgt    3    2.372 ±   2.022       #/op
aws#37|HashMapBenchmark.benchmark:L1-dcache-loads:u                 mm               4096  avgt    3   55.337 ±   3.230       #/op
aws#37|HashMapBenchmark.benchmark:L1-dcache-stores:u                mm               4096  avgt    3   10.394 ±   2.857       #/op
aws#37|HashMapBenchmark.benchmark:L1-icache-load-misses:u           mm               4096  avgt    3    0.006 ±   0.014       #/op
aws#37|HashMapBenchmark.benchmark:branch-misses:u                   mm               4096  avgt    3    0.282 ±   0.058       #/op
aws#37|HashMapBenchmark.benchmark:branches:u                        mm               4096  avgt    3   29.734 ±   0.637       #/op
aws#37|HashMapBenchmark.benchmark:cycles:u                          mm               4096  avgt    3  215.250 ± 274.140       #/op
aws#37|HashMapBenchmark.benchmark:dTLB-load-misses:u                mm               4096  avgt    3    0.658 ±   0.803       #/op
aws#37|HashMapBenchmark.benchmark:dTLB-loads:u                      mm               4096  avgt    3   54.303 ±  18.876       #/op
aws#37|HashMapBenchmark.benchmark:dTLB-store-misses:u               mm               4096  avgt    3    0.010 ±   0.009       #/op
aws#37|HashMapBenchmark.benchmark:dTLB-stores:u                     mm               4096  avgt    3   10.187 ±   1.467       #/op
aws#37|HashMapBenchmark.benchmark:iTLB-load-misses:u                mm               4096  avgt    3    0.001 ±   0.001       #/op
aws#37|HashMapBenchmark.benchmark:instructions:u                    mm               4096  avgt    3  208.912 ±  13.790       #/op
aws#37|HashMapBenchmark.benchmark                                 uuid               4096  avgt    9   58.878 ±   0.913      ns/op
aws#37|HashMapBenchmark.benchmark:CPI                             uuid               4096  avgt    3    1.053 ±   0.177  clks/insn
aws#37|HashMapBenchmark.benchmark:IPC                             uuid               4096  avgt    3    0.950 ±   0.161  insns/clk
aws#37|HashMapBenchmark.benchmark:L1-dcache-load-misses:u         uuid               4096  avgt    3    2.459 ±   0.508       #/op
aws#37|HashMapBenchmark.benchmark:L1-dcache-loads:u               uuid               4096  avgt    3   60.936 ±  25.030       #/op
aws#37|HashMapBenchmark.benchmark:L1-dcache-stores:u              uuid               4096  avgt    3   10.645 ±   5.671       #/op
aws#37|HashMapBenchmark.benchmark:L1-icache-load-misses:u         uuid               4096  avgt    3    0.007 ±   0.011       #/op
aws#37|HashMapBenchmark.benchmark:branch-misses:u                 uuid               4096  avgt    3    0.285 ±   0.064       #/op
aws#37|HashMapBenchmark.benchmark:branches:u                      uuid               4096  avgt    3   31.350 ±  13.276       #/op
aws#37|HashMapBenchmark.benchmark:cycles:u                        uuid               4096  avgt    3  247.079 ±  75.335       #/op
aws#37|HashMapBenchmark.benchmark:dTLB-load-misses:u              uuid               4096  avgt    3    0.685 ±   0.113       #/op
aws#37|HashMapBenchmark.benchmark:dTLB-loads:u                    uuid               4096  avgt    3   60.434 ±   6.667       #/op
aws#37|HashMapBenchmark.benchmark:dTLB-store-misses:u             uuid               4096  avgt    3    0.009 ±   0.001       #/op
aws#37|HashMapBenchmark.benchmark:dTLB-stores:u                   uuid               4096  avgt    3   10.483 ±   0.856       #/op
aws#37|HashMapBenchmark.benchmark:iTLB-load-misses:u              uuid               4096  avgt    3    0.001 ±   0.001       #/op
aws#37|HashMapBenchmark.benchmark:instructions:u                  uuid               4096  avgt    3  234.705 ±  92.008       #/op

```