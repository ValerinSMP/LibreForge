"""Bounded delivery on actual Dev baselines; never publish unrelated source work."""
from pathlib import Path
import ast,hashlib,json,subprocess,sys,zipfile
root=Path(__file__).resolve().parents[1];suite=root.parent
out=Path(sys.argv[1]);asm=sys.argv[2];out.mkdir(parents=True,exist_ok=True);work=out/'composition';work.mkdir(exist_ok=True)
tree=ast.parse((suite/'eco/tools/compose-requirement-views.py').read_text())
helper=next(ast.literal_eval(n.value) for n in tree.body if isinstance(n,ast.Assign) and any(isinstance(t,ast.Name) and t.id=='java' for t in n.targets))
(work/'GraftViews.java').write_text(helper);(work/'Empty.java').write_text('final class Empty {private Empty(){}}')
(work/'RegisterPhysicalClick.java').write_text('''import java.nio.file.*;import org.objectweb.asm.*;import org.objectweb.asm.tree.*;
public class RegisterPhysicalClick {
 public static void main(String[] a)throws Exception{
 var c=new ClassNode();new ClassReader(Files.readAllBytes(Path.of(a[0]))).accept(c,0);
 String registry="com/willfp/libreforge/triggers/Triggers",trigger="com/willfp/libreforge/triggers/impl/TriggerPhysicalClick";
 var m=c.methods.stream().filter(x->x.name.equals("<clinit>")).findFirst().orElseThrow();int count=0;
 for(var op:m.instructions.toArray())if(op.getOpcode()==Opcodes.RETURN){
 var b=new InsnList();b.add(new FieldInsnNode(Opcodes.GETSTATIC,registry,"INSTANCE","L"+registry+";"));
 b.add(new FieldInsnNode(Opcodes.GETSTATIC,trigger,"INSTANCE","L"+trigger+";"));
 b.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,registry,"register","(Lcom/willfp/eco/core/registry/Registrable;)Lcom/willfp/eco/core/registry/Registrable;",false));
 b.add(new InsnNode(Opcodes.POP));m.instructions.insertBefore(op,b);count++;}if(count!=1)throw new AssertionError(count);
 var w=new ClassWriter(ClassWriter.COMPUTE_MAXS);c.accept(w);Files.write(Path.of(a[1]),w.toByteArray());}
}''')
subprocess.run(['javac','--release','21','-cp',asm,'-d',str(work),*[str(work/n) for n in ['GraftViews.java','Empty.java','RegisterPhysicalClick.java']]],check=True)
def transform(mode,old,fresh):
 (work/'before.class').write_bytes(old);(work/'fresh.class').write_bytes(fresh)
 subprocess.run(['java','-Xmx128m','-cp',str(work)+':'+asm,'GraftViews',mode,str(work/'before.class'),str(work/'fresh.class'),str(work/'after.class'),'dev/romainguy/kotlin/math/','com/willfp/libreforge/libs/math/'],check=True)
 return (work/'after.class').read_bytes()
def classes(repo,folder,names):
 directory=suite/repo/folder
 return {n:transform('copy',(work/'Empty.class').read_bytes(),(directory/n).read_bytes()) for n in names}
plugins=Path('/var/lib/pterodactyl/volumes/48d37562-51f7-48c7-812b-b295df2e8aeb/plugins')
reports=[]
def baseline(name,sha):
 file=plugins/name;assert hashlib.sha256(file.read_bytes()).hexdigest()==sha
 with zipfile.ZipFile(file) as z:return {n:z.read(n) for n in z.namelist()}
def emit(name,sha,before,changes):
 after=before|changes;assert all(after[n]==b for n,b in before.items() if n not in changes)
 file=out/name
 with zipfile.ZipFile(file,'w',zipfile.ZIP_DEFLATED) as z:
  for n,b in sorted(after.items()):
   i=zipfile.ZipInfo(n,(2026,10,21,0,0,0));i.compress_type=zipfile.ZIP_DEFLATED;z.writestr(i,b)
 reports.append(dict(file=name,sha256=hashlib.sha256(file.read_bytes()).hexdigest(),baseline=sha,changedEntries=sorted(changes),allOtherEntriesByteIdentical=True,installed=False))
 return file
lfsha='0daf09baad1120c98d9576d5635c593f0ed594182ecd5a7029aebe2b8f95fa28';lf=baseline('libreforge-2026.27.jar',lfsha)
prefix='com/willfp/libreforge/'
names=[prefix+'effects/orbitals/'+n+'.class' for n in ['EffectOrbitalShields','OrbitalCharges','OrbitalCharges$Cooldown','OrbitalShields','OrbitalInput','OrbitalCooldownNotice']]
names += [prefix+'triggers/impl/'+n+'.class' for n in ['TriggerPhysicalClick','TriggerPhysicalClickKt','TriggerPhysicalClickKt$WhenMappings','PhysicalGestures']]
changes=classes('LibreForge','core/common/build/classes/kotlin/main',names)
n=prefix+'triggers/Trigger.class';changes[n]=transform('methods:dispatchOnEffectsMeasured',lf[n],(root/'core/common/build/classes/kotlin/main'/n).read_bytes())
n=prefix+'triggers/Triggers.class';(work/'before.class').write_bytes(lf[n]);subprocess.run(['java','-Xmx128m','-cp',str(work)+':'+asm,'RegisterPhysicalClick',str(work/'before.class'),str(work/'after.class')],check=True);changes[n]=(work/'after.class').read_bytes()
changes['orbital-shields.yml']=(root/'core/common/src/main/resources/orbital-shields.yml').read_bytes()
emit('LibreForge-2026.27-orbitals-input.jar',lfsha,lf,changes);changed=list(changes)
esha='0c936eecdc0557244ea0546d5e4e8d3f84b6d37fe2b0743e64f876a21999f4ca';es=baseline('EcoSkills-2026.27-all.jar',esha)
ns=['com/willfp/ecoskills/actionbar/ActionBarHandler.class','com/willfp/ecoskills/actionbar/ActionBarHandlerKt.class','com/willfp/ecoskills/api/EcoSkillsActionBarAPI.class']
changes=classes('EcoSkills','eco-core/core-plugin/build/classes/kotlin/main',ns)
emit('EcoSkills-2026.27-orbitals-actionbar-all.jar',esha,es,changes);changed+=list(changes)
api=out/'EcoSkills-orbitals-actionbar-api.jar'
with zipfile.ZipFile(api,'w',zipfile.ZIP_DEFLATED) as z:z.writestr(ns[-1],changes[ns[-1]])
reports.append(dict(file=api.name,sha256=hashlib.sha256(api.read_bytes()).hexdigest(),compileOnly=True))
(out/'runtime-manifest.json').write_text(json.dumps(dict(artifacts=reports,gameplayVerified=False),indent=2)+'\n')
(out/'changed-classes.txt').write_text('\n'.join(n for n in changed if n.endswith('.class'))+'\n')
print(json.dumps(reports,indent=2))
