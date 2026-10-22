import java.nio.file.*;
import java.util.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
/** Append only the new cache observer; retain every original holder engine method/helper. */
public final class AppendCachedObservation {
 public static void main(String[] args) throws Exception {
  var old = new ClassNode(); new ClassReader(Files.readAllBytes(Path.of(args[0]))).accept(old, 0);
  var fresh = new ClassNode(); new ClassReader(Files.readAllBytes(Path.of(args[1]))).accept(fresh, 0);
  var method = fresh.methods.stream().filter(m -> m.name.startsWith("peekPublishedEffects$")).findFirst().orElseThrow();
  if(old.methods.stream().anyMatch(m -> m.name.equals(method.name))) throw new AssertionError("Observer already installed");
  for(var instruction:method.instructions) if(instruction instanceof FieldInsnNode field && field.owner.equals(old.name)) {
   if(old.fields.stream().noneMatch(f -> f.name.equals(field.name)&&f.desc.equals(field.desc))) throw new AssertionError("Baseline field mismatch "+field.name);
  }
  old.methods.add(method);
  var writer = new ClassWriter(0); old.accept(writer); Files.write(Path.of(args[2]), writer.toByteArray());
 }
}
