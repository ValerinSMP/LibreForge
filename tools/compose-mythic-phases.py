"""Compose reviewed phase adapters on actual Dev baselines; retire only owned renderers."""
from pathlib import Path
import ast,hashlib,json,subprocess,sys,zipfile,shutil
root=Path(__file__).resolve().parents[1];suite=root.parent;out=Path(sys.argv[1]);asm=sys.argv[2]
out.mkdir(parents=True,exist_ok=True);work=out/'composition';work.mkdir(exist_ok=True)
tree=ast.parse((suite/'eco/tools/compose-requirement-views.py').read_text())
helper=next(ast.literal_eval(n.value) for n in tree.body if isinstance(n,ast.Assign) and any(isinstance(t,ast.Name) and t.id=='java' for t in n.targets))
(work/'GraftViews.java').write_text(helper);(work/'Empty.java').write_text('final class Empty {private Empty(){}}')
subprocess.run(['javac','--release','21','-cp',asm,'-d',str(work),str(work/'GraftViews.java'),str(work/'Empty.java')],check=True)
def transform(mode,old,fresh):
 (work/'before.class').write_bytes(old);(work/'fresh.class').write_bytes(fresh)
 subprocess.run(['java','-Xmx128m','-cp',str(work)+':'+asm,'GraftViews',mode,str(work/'before.class'),str(work/'fresh.class'),str(work/'after.class'),'dev/romainguy/kotlin/math/','com/willfp/libreforge/libs/math/'],check=True)
 return (work/'after.class').read_bytes()
def readjar(file,sha):
 assert hashlib.sha256(file.read_bytes()).hexdigest()==sha,(file,'baseline changed')
 with zipfile.ZipFile(file) as z:return {n:z.read(n) for n in z.namelist()}
def compiled(repo,folder,prefixes):
 directory=suite/repo/folder
 return {str(p.relative_to(directory)):transform('copy',(work/'Empty.class').read_bytes(),p.read_bytes()) for p in directory.rglob('*.class') if any(str(p.relative_to(directory)).startswith(t) for t in prefixes)}
reports=[];changed=[]
def emit(name,before,changes,removed,baseline):
 after=(before|changes)
 for n in removed:after.pop(n,None)
 assert all(after.get(n)==b for n,b in before.items() if n not in changes and n not in removed)
 assert all(after.get(n)==b for n,b in before.items() if n.startswith(('com/willfp/ecoskills/api/','com/willfp/ecoenchants/api/')))
 file=out/name
 with zipfile.ZipFile(file,'w',zipfile.ZIP_DEFLATED) as z:
  for n,b in sorted(after.items()):
   i=zipfile.ZipInfo(n,(2026,10,21,0,0,0));i.compress_type=zipfile.ZIP_DEFLATED;z.writestr(i,b)
 reports.append(dict(file=name,sha256=hashlib.sha256(file.read_bytes()).hexdigest(),baseline=baseline,changedEntries=sorted(changes),retiredEntries=sorted(removed),allOtherEntriesByteIdentical=True,installed=False,gameplayVerified=False))
 changed.extend(n for n in changes if n.endswith('.class'))
 return file
inputdir=Path('/root/development/staging/ecosuite-orbitals-input-20261021')
lfsha='c0dcf5900ce83908595ba4e597dd5ba64e4a5dfeec17fae9de8667a53a753ba5';before=readjar(inputdir/'LibreForge-2026.27-orbitals-input.jar',lfsha)
changes=compiled('LibreForge','core/common/build/classes/kotlin/main',['com/willfp/libreforge/visual/','com/willfp/libreforge/integrations/mythicmobs/visual/','com/willfp/libreforge/integrations/mythicmobs/MythicMobsIntegration','com/willfp/libreforge/integrations/mythicmobs/impl/effect/EffectCastMythicSkill','com/willfp/libreforge/effects/orbitals/'])
removed=[n for n in before if n=='orbital-shields.yml' or any(n.startswith('com/willfp/libreforge/effects/orbitals/'+t) for t in ['OrbitalRig','OrbitalProfile'])]
for n in removed:changes.pop(n,None)
n='com/willfp/libreforge/LibreforgeSpigotPlugin.class'
changes[n]=transform('methods:handleReload,handleDisable',before[n],(root/'core/common/build/classes/kotlin/main'/n).read_bytes())
changes['mythic-visuals.yml']=(root/'core/common/src/main/resources/mythic-visuals.yml').read_bytes()
emit('LibreForge-2026.27-mythic-phases.jar',before,changes,removed,lfsha)
plugins=Path('/var/lib/pterodactyl/volumes/48d37562-51f7-48c7-812b-b295df2e8aeb/plugins')
eesha='0b17315c911f87d5695a7c754f258dd11096f3ea7cb56f163666ec998c84d677';before=readjar(plugins/'EcoEnchants-2026.27-all.jar',eesha)
changes=compiled('EcoEnchants','eco-core/core-plugin/build/classes/kotlin/main',['com/willfp/ecoenchants/progression/OriginalRuntime','com/willfp/ecoenchants/progression/NativeBurstProgression','com/willfp/ecoenchants/progression/EffectNativeBurst'])
removed=[n for n in before if n.startswith(('com/willfp/ecoenchants/progression/VfxProfiles','com/willfp/ecoenchants/progression/OriginalRuntime$VisualScene'))]
for n in removed:changes.pop(n,None)
changes['config.yml']=(suite/'EcoEnchants/eco-core/core-plugin/src/main/resources/config.yml').read_bytes()
emit('EcoEnchants-2026.27-mythic-phases-all.jar',before,changes,removed,eesha)
esha='0c936eecdc0557244ea0546d5e4e8d3f84b6d37fe2b0743e64f876a21999f4ca'
before=readjar(plugins/'EcoSkills-2026.27-all.jar',esha)
changes=compiled('EcoSkills','eco-core/core-plugin/build/classes/kotlin/main',['com/willfp/ecoskills/actionbar/ActionBarHandler.class','com/willfp/ecoskills/actionbar/ActionBarHandlerKt.class','com/willfp/ecoskills/api/EcoSkillsActionBarAPI.class'])
emit('EcoSkills-2026.27-mythic-actionbar-all.jar',before,changes,[],esha)
(out/'runtime-manifest.json').write_text(json.dumps(dict(artifacts=reports,installed=False,active=False,gameplayVerified=False),indent=2)+'\n')
(out/'changed-classes.txt').write_text('\n'.join(changed)+'\n')
print(json.dumps(reports,indent=2))
