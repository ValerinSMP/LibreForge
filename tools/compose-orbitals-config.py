"""Preserve the installed orbital candidate; replace only profile configuration integration."""
from pathlib import Path
import ast,hashlib,json,subprocess,sys,zipfile
root=Path(__file__).resolve().parents[1];out=Path(sys.argv[1]);asm=sys.argv[2]
out.mkdir(parents=True,exist_ok=True);work=out/'composition';work.mkdir(exist_ok=True)
base=Path('/var/lib/pterodactyl/volumes/48d37562-51f7-48c7-812b-b295df2e8aeb/plugins/libreforge-2026.27.jar')
expected='627c9cf88bb3e4b44f8cd12285c88205696d9320da680d1799acad119d6724ee'
assert hashlib.sha256(base.read_bytes()).hexdigest()==expected
with zipfile.ZipFile(base) as z: before={n:z.read(n) for n in z.namelist()}
tree=ast.parse((root.parent/'eco/tools/compose-requirement-views.py').read_text())
helper=next(ast.literal_eval(n.value) for n in tree.body if isinstance(n,ast.Assign) and any(isinstance(t,ast.Name) and t.id=='java' for t in n.targets))
(work/'GraftViews.java').write_text(helper);(work/'Empty.java').write_text('final class Empty {private Empty(){}}')
subprocess.run(['javac','--release','21','-cp',asm,'-d',str(work),str(work/'GraftViews.java'),str(work/'Empty.java')],check=True)
classes=root/'core/common/build/classes/kotlin/main';prefix='com/willfp/libreforge/effects/orbitals/'
changes={}
for name in ['OrbitalShields.class','OrbitalProfilesYml.class']:
 p=classes/(prefix+name);assert p.is_file(),p
 subprocess.run(['java','-Xmx128m','-cp',str(work)+':'+asm,'GraftViews','copy',str(work/'Empty.class'),str(p),str(work/'after.class'),'dev/romainguy/kotlin/math/','com/willfp/libreforge/libs/math/'],check=True)
 changes[prefix+name]=(work/'after.class').read_bytes()
changes['orbital-shields.yml']=(root/'core/common/src/main/resources/orbital-shields.yml').read_bytes()
after=before|changes
assert all(after[n]==b for n,b in before.items() if n not in changes)
jar=out/'LibreForge-2026.27-orbitals-config-fix.jar'
with zipfile.ZipFile(jar,'w',zipfile.ZIP_DEFLATED) as z:
 for n,b in sorted(after.items()):
  i=zipfile.ZipInfo(n,(2026,10,21,0,0,0));i.compress_type=zipfile.ZIP_DEFLATED;z.writestr(i,b)
report=dict(file=jar.name,sha256=hashlib.sha256(jar.read_bytes()).hexdigest(),baseline=expected,changedEntries=sorted(changes),allOtherEntriesByteIdentical=True,installed=False,gameplayVerified=False)
(out/'runtime-manifest.json').write_text(json.dumps(report,indent=2)+'\n')
(out/'changed-classes.txt').write_text('\n'.join(n for n in changes if n.endswith('.class'))+'\n')
print(json.dumps(report))
