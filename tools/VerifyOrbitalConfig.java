import java.nio.file.*;
import java.lang.reflect.*;
import java.util.*;
/** Executes installed eco's real updater against temporary files and candidate defaults. */
public final class VerifyOrbitalConfig {
 static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 public static void main(String[] args)throws Exception{
  Path parent=Path.of(args[0]);Files.createDirectories(parent);
  Path dir=Files.createTempDirectory(parent,"updater-");
  Class<?> type=Class.forName("com.willfp.eco.core.config.ConfigType");
  Object yaml=Enum.valueOf((Class)type,"YAML");
  Class<?> like=Class.forName("com.willfp.eco.core.PluginLike");
  Class<?> handler=Class.forName("com.willfp.eco.core.config.updating.ConfigHandler");
  Object h=Proxy.newProxyInstance(handler.getClassLoader(),new Class[]{handler},(p,m,a)->null);
  Object plugin=Proxy.newProxyInstance(like.getClassLoader(),new Class[]{like},(p,m,a)->switch(m.getName()){
   case "getDataFolder" -> dir.toFile(); case "getConfigHandler" -> h;
   case "toString" -> "OrbitalConfigTemporaryFixture";case "hashCode" -> 1;case "equals" -> p==a[0];default -> null;
  });
  Class<?> source=Class.forName("com.willfp.libreforge.effects.orbitals.OrbitalProfilesYml",false,like.getClassLoader());
  Class<?> updater=Class.forName("com.willfp.eco.internal.config.EcoUpdatableConfig");
  var constructor=updater.getConstructor(type,String.class,like,String.class,Class.class,boolean.class,boolean.class,String[].class);
  // New file copies the exact JAR defaults.
  Object fresh=constructor.newInstance(yaml,"orbital-shields",plugin,"",source,true,true,new String[]{"profiles"});
  var get=updater.getMethod("get",String.class);
  check("shield".equals(get.invoke(fresh,"profiles.escudo_piloto.item")),"pilot defaults missing");
  Path file=dir.resolve("orbital-shields.yml");
  String initial=Files.readString(file);
  String custom=initial+"  otro_perfil:\n    item: glass\n    scale: 0.4\n    custom-field: preserved\nobsolete-top-level: remove-me\n";
  Files.writeString(file,custom);
  Object updated=constructor.newInstance(yaml,"orbital-shields",plugin,"",source,true,true,new String[]{"profiles"});
  for(int i=0;i<3;i++) updater.getMethod("update").invoke(updated);
  check("shield".equals(get.invoke(updated,"profiles.escudo_piloto.item")),"pilot removed");
  check("glass".equals(get.invoke(updated,"profiles.otro_perfil.item")),"custom profile removed");
  check("preserved".equals(get.invoke(updated,"profiles.otro_perfil.custom-field")),"custom field removed");
  check(get.invoke(updated,"obsolete-top-level")==null,"global cleanup disabled");
  String saved=Files.readString(file);updater.getMethod("update").invoke(updated);
  check(saved.equals(Files.readString(file)),"repeat update changed file");
  System.out.println("PASS real eco updater: JAR defaults, pilot + custom profile preserved across repeated updates; outside cleanup retained; temporary files only.");
 }
}
