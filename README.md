# TRACETracker

## Modules


A module that implements the `ModuleInterface`, therefore, enabling applications to register themselves and their profiles.

> While all location, fraud and activity recognition are considered modules, only some actually implement the `ModuleInterface`.

| Modules (Actual) |
| --- |
| Wifi Module |
| QR Code Module |
| NFC Module |
| Network Module |
| Fused Location Module |
| GPS Module |
| DedReckoning Module |

### Duty-Cycle Modules

The Duty-Cycle service allows the use of a module periodically. The LBS application provides
to the Collector Manager an application profifle which specifes an energy consumption profifle and
the technologies with which location should be obtained. The energy profile received dictates in a
Duty-Cycle service, the size of a cycle and the duty-cycled percentage of time. The Duty Cycle
service also allows a programmer to specify in a given module whether or not there is a time-out
renewal after a given event.

The GPS Module duty-cycles GPS over a period of time with timeout renewal when a valid position
is obtained via any other source (lowest priority). The Network Module is used whenever available
and if no other technology is being used to collect information. The BT Module is used by
opportunistic contact after obtaining an accurate position (e.g., from GPS).

| Duty-Cycle Modules |
|---|
| Wifi Module |
| Network Module |
| GPS Module |

> The GPS Module is different from the remaining, as it is actually a `Service`. ***WHY?***

### Trigger-based Modules

This architecture comtemplates two trigger based modules: _NFC Module_, and _QR Code Module_. However, it is important to note that neither of these modules share a common interface.