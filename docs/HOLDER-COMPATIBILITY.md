# Holder compatibility in the selected upstream backport

The development source is personal. This backport preserves the native upstream holder architecture; it does not migrate the complete EcoSuite repositories.

## Consumers and callbacks

EcoArmor, EcoEnchants, EcoItems, EcoSkills and Talismans provide holders to LibreForge. They are rebuilt against the task-local eco API and LibreForge common artifact. Providers which do not implement the new optional scope methods keep their full-scan defaults; the new interface methods are JVM defaults. `BoundCounter.accept` retains filters, conditions and callbacks.

`HolderStates` owns admission, item/condition refresh and removal. Disable receives the provided holder used for enable, including each occurrence. Entity removal/unload and player quit remove states; reload resets them; shutdown performs the upstream sweep. No permission cache is enabled without an invalidation integration.

The Valerin damage bypass, PvP listener, tick trigger deduplication, non-destructive breakage, modifier list cleanup and cooldown sweep remain in place. Trace measurement remains bounded and wraps refresh preparation/evaluation; it is not performance proof.

HuskSync and respawn keep `refreshAfterSync`: schedule on the main thread, check online state, force and flush holder refresh, then restore missing permanent potions. The flush is necessary because force-refresh now queues native state changes. Cancelled inventory clicks are still observed: GUI plugins can cancel a click and modify its items. Updates are applied after the event.

EcoArmor's `count_toward_set` refresh is deferred and coalesced. Armor set holders cannot grant their own extra set pieces. Existing broken-item filtering and effective durability remain. EcoEnchants' new book handler uses eco's drag/drop extension point; existing crystal handling and the fork's anvil remain in their responsible plugins.

## Public persistence and display contracts

Persistent data writes use a bounded executor. `read` remains synchronous and executes the serializer on its caller, avoiding a nested wait on the same bounded executor during profile serialization. No database schema, credentials or live data migration is changed by this delivery. JVM executor tests do not validate MySQL/MariaDB service behavior.

Display stale-line removal is based on before/after module output. Foreign lore remains; component copying and original item hashes prevent repeated wrapping/cache churn. EcoItems keeps its current legacy display path with formatting normalization; the original's full item updater/resource-pack redesign is not imported.

## Verification boundary

JVM tests and builds verify source-level compatibility. Only Test JARs may be installed in this task. The owner alone restarts/reloads every Minecraft server, including Test. Equip/unequip, health, respawn, reconnect, inventory GUI and prices need gameplay checks. HuskSync transfer requires a later explicitly authorized two-node validation. Minecraft 26.2 is adapter/build coverage, not observed gameplay.
