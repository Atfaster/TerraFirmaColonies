"""Isolated installed-runtime food audit. No client or original world mutations."""
from pathlib import Path
import argparse, hashlib, json, os, shutil, subprocess, zipfile, time, queue, threading

p=argparse.ArgumentParser()
p.add_argument('--base-server',type=Path,required=True)
p.add_argument('--lab-root',type=Path,required=True)
p.add_argument('--patched-jar',type=Path)
p.add_argument('--port',type=int,default=25586)
p.add_argument('--prepare-only',action='store_true')
args=p.parse_args()
base=args.base_server.resolve();root=args.lab_root.resolve()
if root.exists():raise SystemExit('Lab root must be new; previous runs are preserved')
if root==base or base.is_relative_to(root):raise SystemExit('Lab must be separate from the source server')
server=root/'server';evidence=root/'evidence';evidence.mkdir(parents=True)
if not server.exists():
    server.mkdir()
    for folder in ['libraries','mods','config','defaultconfigs','global_packs','datapacks','resourcepacks','scripts']:
        if (base/folder).exists():shutil.copytree(base/folder,server/folder)
    shutil.copy2(base/'eula.txt',server/'eula.txt')
    changes={'server-ip':'127.0.0.1','server-port':str(args.port),'level-name':'feeding-fixture','max-tick-time':'180000'}
    lines=(base/'server.properties').read_text().splitlines()
    (server/'server.properties').write_text('\n'.join(l for l in lines if l.partition('=')[0] not in changes)+'\n'+'\n'.join(f'{k}={v}' for k,v in changes.items())+'\n')
if args.patched_jar:
    candidates=[args.patched_jar.resolve()]
    assert len(candidates)==1,candidates
    target=list((server/'mods').glob('terrafirmacolonies-*.jar'));assert len(target)==1
    original=list((base/'mods').glob('terrafirmacolonies-*.jar'));assert len(original)==1
    mixin='net/ckeeze/terrafirmacolonies/mixin/EntityAIWorkCookMixin.class'
    with zipfile.ZipFile(original[0]) as old, zipfile.ZipFile(candidates[0]) as built, zipfile.ZipFile(target[0],'w') as out:
        for entry in old.infolist():
            data=old.read(entry.filename)
            if entry.filename=='terrafirmacolonies.mixins.json':
                config=json.loads(data);config['mixins'].append('EntityAIWorkCookMixin');data=json.dumps(config).encode()
            out.writestr(entry,data)
        out.writestr(mixin,built.read(mixin))
java=Path(os.environ['JAVA_HOME'])/'bin/java.exe';javac=java.with_name('javac.exe')
mc=list((server/'libraries/net/minecraft/server').rglob('*-srg.jar'));assert len(mc)==1
jars=mc+[j for j in (server/'libraries').rglob('*.jar') if '/net/minecraft/server/' not in j.as_posix()]+[j for j in (server/'mods').glob('*.jar') if j.name!='waiter-probe.jar']
classes=root/'classes';classes.mkdir(exist_ok=True)
source=Path(__file__).with_name('WaiterProbe.java')
compile_args=['-proc:none','-encoding','UTF-8','-classpath',os.pathsep.join(str(j) for j in jars),'-d',str(classes),str(source)]
argfile=root/'javac.args';argfile.write_text('\n'.join('"'+s.replace('\\','/')+'"' for s in compile_args))
subprocess.run([str(javac),'@'+str(argfile)],check=True)
with zipfile.ZipFile(server/'mods/waiter-probe.jar','w') as z:
    for f in classes.rglob('*.class'):z.write(f,f.relative_to(classes).as_posix())
    z.writestr('META-INF/mods.toml','modLoader="javafml"\nloaderVersion="[47,)"\nlicense="MIT"\n[[mods]]\nmodId="waiter_probe"\nversion="1.0"\ndisplayName="Isolated feeding audit"\n')
    z.writestr('pack.mcmeta',json.dumps({'pack':{'pack_format':15,'description':'Disposable diagnostic fixture'}}))
(evidence/'mods-sha256.json').write_text(json.dumps({f.name:hashlib.sha256(f.read_bytes()).hexdigest() for f in (server/'mods').glob('*.jar')},indent=2))
if args.prepare_only:print('Compiled waiter regression');raise SystemExit(0)
forge=list((server/'libraries/net/minecraftforge/forge').glob('1.20.1-47.4.20/win_args.txt'));assert len(forge)==1
proc=subprocess.Popen([str(java),'-Xms512M','-Xmx3G','-Dfeeding.lab=true','-Dfeeding.fixed='+str(bool(args.patched_jar)).lower(),'@'+forge[0].relative_to(server).as_posix(),'nogui'],cwd=server,stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,encoding='utf8',errors='replace')
q=queue.Queue()
def reader():
    for line in proc.stdout:q.put(line)
    q.put(None)
threading.Thread(target=reader,daemon=True).start()
complete=False;failed=False;stopped=False;deadline=time.monotonic()+420
with (evidence/'server-run.log').open('w',encoding='utf8') as log:
    while time.monotonic()<deadline:
        try:line=q.get(timeout=1)
        except queue.Empty:continue
        if line is None:break
        log.write(line);log.flush()
        if 'WAITER_PROBE' in line:print(line.rstrip(),flush=True)
        if 'WAITER_PROBE COMPLETE' in line:complete=True
        if 'WAITER_PROBE FAILED' in line:failed=True
        if (complete or failed) and not stopped:proc.stdin.write('stop\n');proc.stdin.flush();stopped=True
if proc.poll() is None:
    if not stopped:proc.stdin.write('stop\n');proc.stdin.flush()
    try:proc.wait(timeout=40)
    except subprocess.TimeoutExpired:proc.terminate();proc.wait(timeout=15)
result={'complete':complete,'failed':failed,'exit_code':proc.returncode,'scope':'Direct calls to transformed production methods in real Forge, with fixture-created colony, citizens, inventories and building levels. Does not simulate all natural AI ticks or pathfinding.'}
(evidence/'run-result.json').write_text(json.dumps(result,indent=2));print(json.dumps(result));raise SystemExit(0 if complete else 1)
