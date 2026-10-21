import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
/** Directed artifact ABI/closure check, including actual installed Mythic public member signatures. */
public final class VerifyMythicPhaseRuntime {
 static boolean retired(String n) {return n.startsWith("com/willfp/libreforge/effects/orbitals/OrbitalRig")||n.startsWith("com/willfp/libreforge/effects/orbitals/OrbitalProfile")||n.startsWith("com/willfp/ecoenchants/progression/VfxProfiles")||n.startsWith("com/willfp/ecoenchants/progression/OriginalRuntime$VisualScene");}
 static boolean retiredMethod(String owner,String name) {
  return owner.equals("com/willfp/ecoenchants/progression/OriginalRuntime") && (Set.of("visualWorldUnload","visualChunkUnload").contains(name)||name.startsWith("access$")&&(name.toLowerCase().contains("visual")));
 }
 static void abi(String oldPath,String newPath)throws Exception {
  try(var old=new JarFile(oldPath);var fresh=new JarFile(newPath)){
   int count=0;var entries=old.entries();
   while(entries.hasMoreElements()){
    var e=entries.nextElement();String n=e.getName();if(!n.startsWith("com/willfp/")||n.contains("/libs/")||n.contains("$")||!n.endsWith(".class")||retired(n))continue;
    var o=VerifyCombatTargets.read(old.getInputStream(e).readAllBytes());var newer=fresh.getJarEntry(n);if(newer==null)throw new AssertionError("Unexpected class retirement "+n);
    var c=VerifyCombatTargets.read(fresh.getInputStream(newer).readAllBytes());
    for(var m:o.methods)if(VerifyCombatTargets.visible(m.access)&&!retiredMethod(o.name,m.name)) {
     if(c.methods.stream().noneMatch(x->x.name.equals(m.name)&&x.desc.equals(m.desc)&&VerifyCombatTargets.visible(x.access)&&((x.access^m.access)&Opcodes.ACC_STATIC)==0))throw new AssertionError("Unexpected ABI loss "+n+"."+m.name+m.desc);count++;
    }
    for(var f:o.fields)if(VerifyCombatTargets.visible(f.access)&&c.fields.stream().noneMatch(x->x.name.equals(f.name)&&x.desc.equals(f.desc)&&VerifyCombatTargets.visible(x.access)))throw new AssertionError("ABI field loss "+n+"."+f.name);
   }
   System.out.println("ABI PASS "+count+" methods; only explicit owned renderer retirements allowed: "+newPath);
  }
 }
 public static void main(String[] args)throws Exception {
  // eco,newLF,skills,newEE,oldLF,oldEE,installedMythic,changedClasses
  VerifyBootstrapClosure.main(Arrays.copyOfRange(args,0,4));abi(args[4],args[1]);abi(args[5],args[3]);if(args.length>8)abi(args[8],args[2]);
  try(var vendor=new JarFile(args[6])){
   var entries=vendor.entries();while(entries.hasMoreElements()){
    var e=entries.nextElement();if(!e.getName().startsWith("io/lumine/")||!e.getName().endsWith(".class"))continue;
    var c=new ClassNode();new ClassReader(vendor.getInputStream(e)).accept(c,ClassReader.SKIP_CODE|ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);VerifyBootstrapClosure.all.put(c.name,c);
   }
  }
  int methods=0,refs=0,vendorRefs=0;
  for(String entry:Files.readAllLines(Path.of(args[7]))) {
   String n=entry.substring(0,entry.length()-6);var c=VerifyBootstrapClosure.all.get(n);if(c==null)throw new AssertionError("Missing composed class "+n);
   for(var m:c.methods){methods++;for(var ins:m.instructions){
    String owner=null,name=null,desc=null;boolean field=false;
    if(ins instanceof MethodInsnNode v){owner=v.owner;name=v.name;desc=v.desc;}
    if(ins instanceof FieldInsnNode v){owner=v.owner;name=v.name;desc=v.desc;field=true;}
    if(owner!=null&&(owner.startsWith("com/willfp/")||owner.startsWith("io/lumine/mythic/"))) {
     if(!VerifyBootstrapClosure.member(owner,name,desc,field,new HashSet<>()))throw new AssertionError("Missing reference "+n+" -> "+owner+"."+name+desc);
     refs++;if(owner.startsWith("io/lumine/mythic/"))vendorRefs++;
    }
    if(ins instanceof TypeInsnNode v && (v.desc.startsWith("com/willfp/libreforge/")||v.desc.startsWith("com/willfp/ecoenchants/")||v.desc.startsWith("com/willfp/ecoskills/")||v.desc.startsWith("io/lumine/mythic/"))&&!VerifyBootstrapClosure.all.containsKey(v.desc))throw new AssertionError("Missing owned/vendor type "+v.desc);
    if(ins instanceof InvokeDynamicInsnNode v)for(Object a:v.bsmArgs)if(a instanceof Handle h)VerifyBootstrapClosure.handle(h);
   }}
  }
  System.out.println("Phase closure PASS: "+methods+" methods / "+refs+" references, including "+vendorRefs+" actual Mythic references. No server lifecycle executed.");
 }
}
