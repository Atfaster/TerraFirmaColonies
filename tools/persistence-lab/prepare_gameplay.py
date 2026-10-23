"""Prepare an isolated manual-test server and a local-only client import ZIP."""
from pathlib import Path
import argparse, hashlib, json, shutil, zipfile

repo=Path(__file__).resolve().parents[2]
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--baseline-server',type=Path,default=repo/'run/greenhouse-lab/server')
parser.add_argument('--lab-root',type=Path,default=repo/'run/persistence-gameplay-lab')
parser.add_argument('--mod-jar',type=Path,required=True)
args=parser.parse_args()
base=args.baseline_server.resolve(); root=args.lab_root.resolve(); jar=args.mod_jar.resolve()
assert jar.is_file(), 'Built mod JAR required'
assert not root.exists(), 'Choose a fresh manual lab directory'
server=root/'server'; server.mkdir(parents=True)
for directory in ['libraries','mods','config','defaultconfigs']: shutil.copytree(base/directory,server/directory)
for file in ['eula.txt','ops.json']:
    if (base/file).exists(): shutil.copy2(base/file,server/file)
assert not list((server/'mods').glob('*probe*')), 'Baseline must not contain the probe'
targets=list((server/'mods').glob('terrafirmacolonies-*.jar'))
assert len(targets)==1, 'Expected one TerraFirmaColonies JAR'
shutil.copy2(jar,targets[0])
overrides={'server-ip':'127.0.0.1','server-port':'25577','level-name':'persistence-gameplay','max-tick-time':'180000'}
props=(base/'server.properties').read_text()
props='\n'.join(s for s in props.splitlines() if s.partition('=')[0] not in overrides)
(server/'server.properties').write_text(props+'\n'+'\n'.join(k+'='+v for k,v in overrides.items())+'\n')
(root/'Start-Server.ps1').write_text("$ErrorActionPreference = 'Stop'\nSet-Location -LiteralPath (Join-Path $PSScriptRoot 'server')\n& java '-Xms1G' '-Xmx4G' '@libraries/net/minecraftforge/forge/1.20.1-47.4.18/win_args.txt' 'nogui'\nexit $LASTEXITCODE\n")
manifest={'minecraft':{'version':'1.20.1','modLoaders':[{'id':'forge-47.4.18','primary':True}],'recommendedRam':6144},
          'manifestType':'minecraftModpack','manifestVersion':1,'name':'TFC Colony Persistence Test','version':'local-fix',
          'author':'Local test only','overrides':'overrides','files':[]}
mods=list((server/'mods').glob('*.jar'))
with zipfile.ZipFile(root/'TFC-Colony-Persistence-Test.zip','w',zipfile.ZIP_DEFLATED) as z:
    z.writestr('manifest.json',json.dumps(manifest,indent=2))
    for p in mods: z.write(p,'overrides/mods/'+p.name)
    z.writestr('overrides/options.txt','lang:en_us\n')
hashes={p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in mods}
(root/'mods-sha256.json').write_text(json.dumps(hashes,indent=2))
print('Manual server:',server)
print('Local-only client import:',root/'TFC-Colony-Persistence-Test.zip')
print('Address: 127.0.0.1:25577. No probe, player world or user client profile was copied.')
