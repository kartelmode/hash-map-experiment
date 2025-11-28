# Hash Map experiments

This project contains a small research effort on how different hashing algorithms and map implementations behave under order-cache workloads in an OEMS system.

First, we evaluate the distribution quality of several hash functions using our typical order-identifier naming patterns.  

Next, we benchmark the better-performing hash functions across multiple map implementations under access patterns representative of real OEMS workloads.

## Step 1: Hashing Functions

### Key Naming Patterns

We want to evaluate which hashing strategies produce fewer collisions on key patterns commonly used in OEMS order identifiers.

While OEMS systems allow arbitrary alphanumeric/textual order IDs, real-world usage tends to cluster around a few predictable formats:

* **`number`** — an `INT64` sequence number formatted as text  
  Example: `"1761610691"`
* **`mm`** — our internal “mm” style: a constant prefix plus a base32 sequence number  
  Example: `"SOURCE13:T3AAA402"`
* **`UUID`** — a standard UUID  
  Example: `"BE3F223A-3E39-443E-AEB8-3932A850C051"`

The full set of naming styles is broader, but these three cover most practical cases and are representative enough to evaluate hash distribution.

A key detail: in the first two patterns, consecutive identifiers differ only in the last few bytes. UUIDs differ more uniformly across the entire string and are naturally better distributed.

**NOTE:** This project uses ASCII textual identifiers only.

### Hash Functions

Our goal is to identify a small set of hash functions that balance computational speed and distribution quality, since both factors directly impact overall hash-table performance.

* **default** - Java default hash function
* **unrolledDefault** - a variation of default hasher where we process 4 bytes per loop iteration
* **xxHash** - XXHash (see [this](https://xxhash.com/)) - copied from openhft? TODO: Preserve authorship
* **metro**
* **faster** - faster version of default hasher (traverses left side of the key as array of INT32 and INT16 before handling what remains as byte)
* **vhFaster** - a variation of faster hash that uses VarHandlers (Java 17+) rather than Unsafe.

### Benchmarking

#### Collision Rate

The table below shows the number of collisions for each key-generation pattern and each hash function, based on inserting \(10^7\) keys.


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

\* **Note:** This hash implementation currently allocates short-lived objects — a hard NO for a Java-based OEMS.

All hash functions provide similarly good distribution for random keys (UUID pattern).  
For the other patterns, results vary more: the default Java hash and the “faster” variants show noticeably higher collision counts compared to the stronger hash functions.

#### Empty (Unreachable) Bucket Ratio 

The table below shows the ratio of unreachable hash-table cells to the total number of elements (smaller is better).  
A cell is considered *reachable* if there exists at least one key for which the hash value, modulo the table size, maps to that cell.

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

Based on these data, the **faster** hash functions fail to touch the majority of hash-table cells even once.  
This implies a severely skewed distribution and will lead to noticeable performance degradation in any hash-table implementation.

#### Index of Dispersion

The following table shows Index of Dispersion (IOD). Good hash functions will have their IOD close to 1.0. IOD greater than 1 signifies clustering of results. 

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

Once again, UUID keys produce good hash distribution no matter which function is used.  
And once again, **faster** hashes show very significant clustering.  
Interestingly, the **default**-hash–based implementations also show some degree of clustering.

#### Distribution Percentiles {P50, P90, P99, P999}

The table below shows percentile distributions (p50, p75, p90, p99) of how many times each hash-table cell was hit.  
This gives a direct view of hash distribution quality for our three key naming patterns.


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

Yet another confirmation that the `faster` hash functions have poor distribution.

TODO: What we completely miss is op/sec JMH test so that we can understand trade off between has quality and execution cost. 

### Summary

Given their poor key distribution on the "number" and "mm" key patterns, the `faster` and `vhFaster` hash functions can be dropped from further research.

In contrast, `xxHash`, `metroHash`, `varHandle`, and `nativeHash` show consistently solid distribution across all hash-table cells, regardless of key pattern. It makes sense to take a subset of these for deeper testing. For example, `xxHash` is widely used, while `nativeHash` is noticeably faster to compute.

The `default` and `unrolledDefault` hash functions show less stable distribution than the group above, but they are still worth including in later experiments due to their speed.

## Step 2: Comparing Map implementations

Next, we evaluate different Map implementations for the OEMS order-cache use case.

### What is specific about this use case?

We focus on a market-maker scenario where the system constantly updates prices of resting orders.  
Each price update triggers an order-replace request to the market, and every replace uses a new order ID.  
We assume the market acknowledges each request. Each acknowledgement touches two IDs:  
it confirms the new one as active and deactivates the old one.

Under this load, cache **writes** happen almost as often as **reads**.  
A typical ratio might be one trade for every ~1000 price adjustments.

The cache must track all active orders plus a configurable window of recently inactive ones:

```java
public interface Cache {
    boolean putIfEmpty(DataPayload entry);
    DataPayload get(AsciiString key);
    void deactivate(DataPayload entry);
    int size();
}
```

For benchmark purposes, we test each Map implementation with 1 million entries (active + recent).
The Map also maintains a FIFO list of up to 4096 deactivated orders.
This is why the cache interface has no .remove()—deactivated orders are pushed into a FIFO area and stay there until they naturally expire.


### JMH Benchmark

As mentioned, the workload is a 50/50 mix of reads and writes:

```java
    @Benchmark
    @OperationsPerInvocation(4)
    public void benchmark() {
        findOldest();   // GET
        findExpunged(); // GET (miss)
        removeOldest(); // REMOVE
        addNewest();    // PUT
    }
```

The `removeOldest()` operation corresponds to deactivating the oldest active order.

JMH results are shown below.

All tests were run on a large Amazon EC2 R7 instance with plenty of spare CPU capacity. 
Exact hardware details matter less here—we care mainly about relative performance, not absolute numbers.

| Hash Strategy   | Key Type | Map implementation | Score (ns/op) ± Error |
|-----------------|----------|--------------------|-----------------------|
| xxHash          | number   | chaining           | 81.893  ± 1.679       |
| xxHash          | number   | linearprobe        | 70.930  ± 1.275       |
| xxHash          | number   | robinhood          | 57.247  ± 0.729       |
| xxHash          | mm       | chaining           | 100.128 ± 2.560       |
| xxHash          | mm       | linearprobe        | 88.850  ± 3.860       |
| xxHash          | mm       | robinhood          | 65.882  ± 2.515       |
| xxHash          | uuid     | chaining           | 144.904 ± 5.118       |
| xxHash          | uuid     | linearprobe        | 136.992 ± 0.601       |
| xxHash          | uuid     | robinhood          | 104.501 ± 1.260       |
| default         | number   | chaining           | 28.362  ± 0.537       |
| default         | number   | linearprobe        | 55.265  ± 1.708       |
| default         | number   | robinhood          | 44.832  ± 0.513       |
| default         | mm       | chaining           | 36.171  ± 0.262       |
| default         | mm       | linearprobe        | 78.265  ± 0.771       |
| default         | mm       | robinhood          | 49.117  ± 0.141       |
| default         | uuid     | chaining           | 156.116 ± 1.215       |
| default         | uuid     | linearprobe        | 161.020 ± 6.575       |
| default         | uuid     | robinhood          | 112.858 ± 2.041       |
| unrolledDefault | number   | chaining           | 28.147  ± 1.180       |
| unrolledDefault | number   | linearprobe        | 55.430  ± 0.567       |
| unrolledDefault | number   | robinhood          | 42.736  ± 0.171       |
| unrolledDefault | mm       | chaining           | 34.953  ± 0.194       |
| unrolledDefault | mm       | linearprobe        | 74.320  ± 0.447       |
| unrolledDefault | mm       | robinhood          | 48.790  ± 2.885       |
| unrolledDefault | uuid     | chaining           | 142.156 ± 1.246       |
| unrolledDefault | uuid     | linearprobe        | 151.103 ± 3.590       |
| unrolledDefault | uuid     | robinhood          | 107.175 ± 3.973       |
| nativeHash      | number   | chaining           | 73.518  ± 0.940       |
| nativeHash      | number   | linearprobe        | 59.066  ± 0.831       |
| nativeHash      | number   | robinhood          | 49.031  ± 0.461       |
| nativeHash      | mm       | chaining           | 88.947  ± 0.869       |
| nativeHash      | mm       | linearprobe        | 74.744  ± 1.138       |
| nativeHash      | mm       | robinhood          | 56.419  ± 1.323       |
| nativeHash      | uuid     | chaining           | 137.067 ± 5.240       |
| nativeHash      | uuid     | linearprobe        | 134.238 ± 3.451       |
| nativeHash      | uuid     | robinhood          | 95.075  ± 1.147       |

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

Why so? Native version stores 3 additional arrays, then when we're trying to find value by the key, it accesses these 3 additional arrays and every cache miss in the entry array with high probability gives additional cache misses.

This is acceptable for rawLinearprobe too, because it stores additional array with proportional length to the capacity of hashmap.

## java.util.collections.HashMap

The data below demonstrates the results for hash map using `HashMap` from java.util.collections. For `number` and `mm` key naming strategy it works ideal with default hash function, whenever nativeHash is better for `uuid` naming strategy.

### xxHash

| Key Type | Score (ns/op) ± Error | L1-dcache-load-misses (#/op) ± Error | L1-dcache-loads (#/op) ± Error |
|----------|-----------------------|--------------------------------------|--------------------------------|
| number   | 44.672 ± 1.030        | 2.145 ± 0.223                        | 62.759 ± 1.633                 |
| mm       | 52.721 ± 2.620        | 2.236 ± 1.184                        | 75.227 ± 0.364                 |
| uuid     | 107.706 ± 3.578       | 2.835 ± 1.009                        | 133.805 ± 1.497                |

### default

| Key Type | Score (ns/op) ± Error | L1-dcache-load-misses (#/op) ± Error | L1-dcache-loads (#/op) ± Error |
|----------|-----------------------|--------------------------------------|--------------------------------|
| number   | 44.295 ± 0.559        | 2.155 ± 0.391                        | 63.385 ± 15.463                |
| mm       | 53.131 ± 0.637        | 2.223 ± 0.304                        | 75.288 ± 2.935                 |
| uuid     | 106.847 ± 5.341       | 2.818 ± 0.712                        | 133.525 ± 7.395                |

### unrolledDefault

| Key Type | Score (ns/op) ± Error | L1-dcache-load-misses (#/op) ± Error | L1-dcache-loads (#/op) ± Error |
|----------|-----------------------|--------------------------------------|--------------------------------|
| number   | 45.005 ± 1.166        | 2.137 ± 0.166                        | 62.798 ± 2.777                 |
| mm       | 51.730 ± 0.676        | 2.224 ± 0.297                        | 75.048 ± 2.673                 |
| uuid     | 106.087 ± 4.570       | 2.879 ± 1.444                        | 133.376 ± 3.952                |

### NativeHash

| Key Type | Score (ns/op) ± Error | L1-dcache-load-misses (#/op) ± Error | L1-dcache-loads (#/op) ± Error |
|----------|-----------------------|--------------------------------------|--------------------------------|
| number   | 44.107 ± 0.717        | 2.145 ± 0.523                        | 63.268 ± 15.478                |
| mm       | 56.567 ± 2.430        | 2.336 ± 0.922                        | 75.370 ± 8.464                 |
| uuid     | 108.970 ± 6.118       | 2.826 ± 0.567                        | 135.153 ± 45.876               |

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

## Leaderboards

// TODO: add plots

The tables below demonstrate statistical leaderboards(top-5) for every hash function for every key naming strategy. 

### Number

| Rank | Implementation   | Parameter Set   | ns/op ± error   |
|------|------------------|-----------------|-----------------|
| 1    | Chaining         | unrolledDefault | 28.147 ± 1.180  |
| 2    | Chaining         | default         | 28.362 ± 0.537  |
| 3    | RobinHood        | unrolledDefault | 42.736 ± 0.171  |
| 4    | Java's HashMap   | nativeHash      | 44.107 ± 0.717  |
| 5    | Java's HashMap   | default         | 44.295 ± 0.559  |

### MM

| Rank | Implementation   | Parameter Set   | ns/op ± error   |
|------|------------------|-----------------|-----------------|
| 1    | Chaining         | unrolledDefault | 34.953 ± 0.194  |
| 2    | Chaining         | default         | 36.171 ± 0.262  |
| 3    | RobinHood        | unrolledDefault | 48.790 ± 2.885  |
| 4    | RobinHood        | default         | 49.117 ± 0.141  |
| 5    | Java's HashMap   | unrolledDefault | 51.730 ± 0.676  |

### UUID

| Rank | Implementation   | Parameter Set   | ns/op ± error    |
|------|------------------|-----------------|------------------|
| 1    | RobinHood        | nativeHash      | 95.075 ± 1.147   |
| 2    | RobinHood        | xxHash          | 104.501 ± 1.260  |
| 3    | Java's HashMap   | unrolledDefault | 106.087 ± 4.570  |
| 4    | Java's HashMap   | default         | 106.847 ± 5.341  |
| 5    | RobinHood        | unrolledDefault | 107.175 ± 3.973  |


## Conclusion

Different map key naming strategies call for different hash functions. 

* For `Number` and `MM` naming strategies it's recommended to use `unrolledDefault` hash function with `Chaining` hashmap.
* For `UUID` key naming it's recommended to use `nativeHash` hash function with `RobinHood` hashmap.
* If we don't know our key naming pattern it is recommended to use `nativeHash` or `xxHash` hash functions with `RobinHood` Map implementation.

