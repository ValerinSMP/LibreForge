package com.willfp.libreforge.conditions
import com.willfp.eco.core.config.interfaces.Config
import com.willfp.libreforge.*
import org.bukkit.Bukkit
import org.mockito.Mockito.*
import kotlin.test.*
class CacheOnlyConditionTest {
 private class Cached : Condition<NoCompileData>("test_cached"),CacheOnlyCondition<NoCompileData> {
  var current=false
  override fun isMetCached(dispatcher:Dispatcher<*>,config:Config,holder:ProvidedHolder,compileData:NoCompileData)=current
 }
 @Test fun `packet condition uses latest cached answer instead of previous holder result`(){
  val condition=Cached();val config=mock(Config::class.java)
  val holder=mock(ProvidedHolder::class.java);val dispatcher=mock(Dispatcher::class.java)
  val block=ConditionBlock(condition,config,NoCompileData,null,emptyList(),false,false)
  mockStatic(Bukkit::class.java).use {
   condition.current=false;assertFalse(block.isMet(dispatcher,holder))
   condition.current=true;assertTrue(block.isMet(dispatcher,holder))
   condition.current=false;assertFalse(block.isMet(dispatcher,holder))
  }
 }
 @Test fun `native inversion is preserved for cache-only evaluations`(){
  val condition=Cached();val config=mock(Config::class.java)
  val holder=mock(ProvidedHolder::class.java);val dispatcher=mock(Dispatcher::class.java)
  val block=ConditionBlock(condition,config,NoCompileData,null,emptyList(),false,true)
  mockStatic(Bukkit::class.java).use {
   condition.current=false;assertTrue(block.isMet(dispatcher,holder))
   condition.current=true;assertFalse(block.isMet(dispatcher,holder))
  }
 }
}
