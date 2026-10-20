package com.willfp.libreforge.conditions
import com.willfp.eco.core.config.interfaces.Config
import com.willfp.libreforge.Dispatcher
import com.willfp.libreforge.ProvidedHolder
/** Optional thread-safe cached evaluation. null retains native MAIN-result fallback. */
interface CacheOnlyCondition<T> {
 fun isMetCached(dispatcher: Dispatcher<*>, config: Config, holder: ProvidedHolder, compileData: T): Boolean?
}
