"""Compose only the reviewed combat-menu classes on pinned Dev artifacts."""
from pathlib import Path
import hashlib,json,sys,zipfile,shutil,subprocess,ast
suite=Path(__file__).resolve().parents[2]
out=Path(sys.argv[1]);out.mkdir(parents=True,exist_ok=True)
plugins=Path("/var/lib/pterodactyl/volumes/48d37562-51f7-48c7-812b-b295df2e8aeb/plugins")
plans=[("LibreForge","libreforge-2026.27.jar","92e8598992f894f7933d0540e003e76bb353eacd3e1aeafc8e9b476cd1b31b21",
 "core/common/build/classes/kotlin/main",["com/willfp/libreforge/HolderStates.class","com/willfp/libreforge/observation/"],"LibreForge-2026.27-combat-menu.jar"),
 ("EcoSkills","EcoSkills-2026.27-all.jar","a0bd1e35574cd143e4735ae036dae8af983af9d0f39cc5c39193c7e6771bb631",
 "eco-core/core-plugin/build/classes/kotlin/main",["com/willfp/ecoskills/gui/menus/StatsGUI.class","com/willfp/ecoskills/gui/menus/Combat"],"EcoSkills-2026.27-combat-menu-all.jar")]
reports=[];changed=[]
work=out/"composition";work.mkdir(exist_ok=True)
asm=sys.argv[2]
tree=ast.parse((suite/"eco/tools/compose-requirement-views.py").read_text())
helper=next(ast.literal_eval(n.value) for n in tree.body if isinstance(n,ast.Assign) and any(isinstance(t,ast.Name) and t.id=="java" for t in n.targets))
(work/"GraftViews.java").write_text(helper)
(work/"Empty.java").write_text("final class Empty {private Empty(){}}")
subprocess.run(["javac","--release","21","-cp",asm,"-d",str(work),str(suite/"LibreForge/tools/AppendCachedObservation.java"),str(work/"GraftViews.java"),str(work/"Empty.java")],check=True)
def relocate(data):
 (work/"input.class").write_bytes(data)
 subprocess.run(["java","-Xmx128m","-cp",str(work)+":"+asm,"GraftViews","copy",str(work/"Empty.class"),str(work/"input.class"),str(work/"relocated.class"),"-","-"],check=True)
 return (work/"relocated.class").read_bytes()
for project,baseline,expected,folder,prefixes,name in plans:
 base=plugins/baseline;assert hashlib.sha256(base.read_bytes()).hexdigest()==expected,"Baseline changed"
 with zipfile.ZipFile(base) as z:before={n:z.read(n) for n in z.namelist()}
 root=suite/project/folder
 updates={str(p.relative_to(root)):relocate(p.read_bytes()) for p in root.rglob("*.class") if any(str(p.relative_to(root)).startswith(x) for x in prefixes)}
 assert updates,project
 if project=="LibreForge":
  entry="com/willfp/libreforge/HolderStates.class"
  (work/"old.class").write_bytes(before[entry]);(work/"fresh.class").write_bytes(updates[entry])
  subprocess.run(["java","-Xmx128m","-cp",str(work)+":"+asm,"AppendCachedObservation",str(work/"old.class"),str(work/"fresh.class"),str(work/"after.class")],check=True)
  updates[entry]=(work/"after.class").read_bytes()
 if project=="EcoSkills":updates["combat-menu.yml"]=(suite/project/"eco-core/core-plugin/src/main/resources/combat-menu.yml").read_bytes()
 after=before|updates
 assert all(after[n]==b for n,b in before.items() if n not in updates)
 assert all(after[n]==b for n,b in before.items() if n.startswith("com/willfp/ecoskills/api/"))
 candidate=out/name
 with zipfile.ZipFile(candidate,"w",zipfile.ZIP_DEFLATED) as z:
  for n,b in sorted(after.items()):
   i=zipfile.ZipInfo(n,(2026,10,22,0,0,0));i.compress_type=zipfile.ZIP_DEFLATED;z.writestr(i,b)
 reports.append(dict(project=project,file=name,sha256=hashlib.sha256(candidate.read_bytes()).hexdigest(),baseline=expected,changedEntries=sorted(updates),allOtherEntriesByteIdentical=True))
 changed.extend(n for n in updates if n.endswith(".class"))
(out/"runtime-manifest.json").write_text(json.dumps(dict(artifacts=reports,gameplayVerified=False),indent=2)+"\n")
(out/"changed-classes.txt").write_text("\n".join(changed)+"\n")
(out/"SHA256SUMS").write_text("".join(x["sha256"]+"  "+x["file"]+"\n" for x in reports))
print(json.dumps(reports,indent=2))
