# Hash Map experiments

Little project to research properties of different hashing algorithms when applied to keys typical in OEMS system.


We want to see which hashing strategy produces less collisions on keys naming patterns we typically observe.

These naming strategies include

* simple `number` (e.g. "1761610691"), variable length
* something we internally call `mm` - key is constructed using constant prefix followed by base32 sequence number (e.g. "SOURCE13:T3AAA402"),
* UUID (e.g. "BE3F223A-3E39-443E-AEB8-3932A850C051")

| Hash                   | number   | mm        | uuid     |
|------------------------|----------|-----------|----------|
| varHandle              | 13052956 | 13055786  | 13051122 |
| xxHash                 | 13051500 | 13053849  | 13052276 |
| default                | 16777126 | 19852551  | 13050206 |
| metroHash              | 13049393 | 13053721  | 13051639 |
| unrolledDefault        | 16777126 | 19852551  | 13050206 |
| vectorizedDefaultHash* | 15896600 | 19835272  | 13050653 |
| nativeHash             | 13080199 | 13095837  | 13048408 |
| faster                 | 32887039 | 33503033  | 13052028 |
| vhFaster               | 32887039 | 33503033  | 13052028 |

* - current version of this hashing allocates