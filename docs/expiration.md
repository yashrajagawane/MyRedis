# Expiration

Expiration is implemented by `ExpirationManager` and `ExpirationScheduler`.
Expiry timestamps are stored separately from values as absolute millisecond
deadlines. This keeps TTL bookkeeping independent from the typed storage model.

## Setting expiry

`EXPIRE` accepts seconds and `PEXPIRE` accepts milliseconds. `SET` supports
`EX` and `PX` options. A positive duration is required and timestamp overflow
is rejected. Applying a new expiry replaces the previous deadline.

`PERSIST` removes an existing deadline. Replacing a value with `SET` removes
its previous TTL. Atomic counter updates preserve an existing TTL so numeric
changes do not unexpectedly make a key persistent.

## TTL results

`TTL` reports whole remaining seconds, rounded up to at least one while a key
is still live. It returns `-1` for an existing persistent key and `-2` for a
missing or expired key. Internal millisecond TTLs are used for snapshot
preservation and `PEXPIRE` recovery.

## Lazy expiration

Storage operations check expiry before accessing a key. Reads, `EXISTS`, list,
set, hash, sorted-set, delete, and counter operations therefore do not expose
expired values. When the expiry marker is removed successfully, the associated
value is conditionally removed from the keyspace.

## Active expiration

`ExpirationScheduler` runs a single scheduled cleanup task every 100 ms. It
samples the current expiry-marker keys and asks storage to remove expired
values. Cleanup is intentionally simple and bounded per sample rather than a
separate timing-wheel or priority-queue subsystem.

Lazy access and scheduled cleanup can race safely. Expiry removal is
conditional on the observed timestamp, so cleanup based on an old deadline
cannot remove a value that has since been replaced or re-expired.

## Persistence interaction

Snapshots skip expired keys and include positive remaining TTLs as `PEXPIRE`
commands after the value command. Recovery therefore restores the approximate
remaining lifetime at snapshot time and replays later AOF mutations in order.
The expiration subsystem also reports successful removals through the
`expired_keys` field in `INFO`.

## Tradeoffs and limitations

The current scheduler scans expiry metadata snapshots and uses wall-clock
milliseconds. It provides eventual active cleanup rather than a hard deletion
deadline, while access-time checks provide immediate logical expiration. Clock
jumps, very large expiry maps, and process pauses can affect cleanup timing.
The design does not currently expose configurable scheduler intervals or a
memory quota for expired-key backlog.
