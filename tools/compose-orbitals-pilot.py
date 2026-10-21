"""Add only the shared orbital effect and two lifecycle calls to pinned Dev LibreForge."""
from pathlib import Path
import ast,hashlib,json,subprocess,sys,zipfile
root=Path(__file__).resolve().parents[1];out=Path(sys.argv[1]);asm=sys.argv[2];out.mkdir(parents=True,exist_ok=True)
work=out/'composition';work.mkdir(exist_ok=True)
base=Path('/var/lib/pterodactyl/volumes/48d37562-51f7-48c7-812b-b295df2e8aeb/plugins/libreforge-2026.27.jar')
expected='1035c2610a8dd91c4fd6425a809ed2f64d0369c2c9c8b100bebcbc22082c2368'
assert hashlib.sha256(base.read_bytes()).hexdigest()==expected
with zipfile.ZipFile(base) as z:before={n:z.read(n) for n in z.namelist()}
assert not any('/effects/orbitals/' in n for n in before)
# Reuse the existing reviewed shading adapter; do not republish a full dirty checkout.
tree=ast.parse((root.parent/'eco/tools/compose-requirement-views.py').read_text())
helper=next(ast.literal_eval(n.value) for n in tree.body if isinstance(n,ast.Assign) and any(isinstance(t,ast.Name) and t.id=='java' for t in n.targets))
(work/'GraftViews.java').write_text(helper)
(work/'Empty.java').write_text('final class Empty { private Empty(){} }')
patch=r'''import java.nio.file.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
public class OrbitalRegistration {
 public static void main(String[] a)throws Exception {
  var c=new ClassNode();new ClassReader(Files.readAllBytes(Path.of(a[0]))).accept(c,0);
  String effects="com/willfp/libreforge/effects/Effects", runtime="com/willfp/libreforge/effects/orbitals/OrbitalShields";
  if(c.name.equals(effects)) {
   var m=c.methods.stream().filter(x->x.name.equals("<clinit>")).findFirst().orElseThrow();int inserted=0;
   for(var op:m.instructions.toArray())if(op.getOpcode()==Opcodes.RETURN){
    var b=new InsnList();b.add(new FieldInsnNode(Opcodes.GETSTATIC,effects,"INSTANCE","L"+effects+";"));
    String effect="com/willfp/libreforge/effects/orbitals/EffectOrbitalShields";
    b.add(new FieldInsnNode(Opcodes.GETSTATIC,effect,"INSTANCE","L"+effect+";"));
    b.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,effects,"register","(Lcom/willfp/eco/core/registry/Registrable;)Lcom/willfp/eco/core/registry/Registrable;",false));
    b.add(new InsnNode(Opcodes.POP));m.instructions.insertBefore(op,b);inserted++;
   }if(inserted!=1)throw new AssertionError("Unexpected registry initializer");
  }else {
   for(String name:java.util.List.of("handleReload","handleDisable")){
    var m=c.methods.stream().filter(x->x.name.equals(name)&&x.desc.equals("()V")).findFirst().orElseThrow();
    var b=new InsnList();b.add(new FieldInsnNode(Opcodes.GETSTATIC,runtime,"INSTANCE","L"+runtime+";"));
    b.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,runtime,"reset","()V",false));m.instructions.insert(b);
   }
  }
  var w=new ClassWriter(ClassWriter.COMPUTE_MAXS);c.accept(w);Files.write(Path.of(a[1]),w.toByteArray());
 }
}'''
(work/'OrbitalRegistration.java').write_text(patch)
subprocess.run(['javac','--release','21','-cp',asm,'-d',str(work),str(work/'GraftViews.java'),str(work/'Empty.java'),str(work/'OrbitalRegistration.java')],check=True)
changes={};classes=root/'core/common/build/classes/kotlin/main'
for p in (classes/'com/willfp/libreforge/effects/orbitals').glob('*.class'):
 (work/'fresh.class').write_bytes(p.read_bytes())
 subprocess.run(['java','-Xmx128m','-cp',str(work)+':'+asm,'GraftViews','copy',str(work/'Empty.class'),str(work/'fresh.class'),str(work/'after.class'),'dev/romainguy/kotlin/math/','com/willfp/libreforge/libs/math/'],check=True)
 changes[str(p.relative_to(classes))]=(work/'after.class').read_bytes()
assert len(changes)>=8
for n in ['com/willfp/libreforge/effects/Effects.class','com/willfp/libreforge/LibreforgeSpigotPlugin.class']:
 (work/'before.class').write_bytes(before[n])
 subprocess.run(['java','-Xmx128m','-cp',str(work)+':'+asm,'OrbitalRegistration',str(work/'before.class'),str(work/'after.class')],check=True)
 changes[n]=(work/'after.class').read_bytes()
after=before|changes
assert all(after[n]==b for n,b in before.items() if n not in changes)
assert all(after[n]==b for n,b in before.items() if not n.endswith('.class') or '/counters/' in n)
jar=out/'LibreForge-2026.27-orbitals-pilot.jar'
with zipfile.ZipFile(jar,'w',zipfile.ZIP_DEFLATED) as z:
 for n,b in sorted(after.items()):
  info=zipfile.ZipInfo(n,(2026,10,21,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;z.writestr(info,b)
report=dict(file=jar.name,sha256=hashlib.sha256(jar.read_bytes()).hexdigest(),baseline=expected,changedEntries=sorted(changes),existingChanged=[n for n in changes if n in before],resourcesAndCountersByteIdentical=True,installed=False,active=False,gameplayVerified=False)
(out/'runtime-manifest.json').write_text(json.dumps(report,indent=2)+'\n')
(out/'changed-classes.txt').write_text('\n'.join(changes)+'\n')
print(json.dumps(report))
