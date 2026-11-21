"""Package signed Eclipse update-site JARs and refresh p2 artifact sizes.
Standard library only. Run after Eclipse Build All and jarsigner.
"""
from pathlib import Path
import sys, io, zipfile, xml.etree.ElementTree as ET, hashlib, re
BASE = Path(__file__).resolve().parent
SITE = BASE / 'com.ktylabs.stm32configurator.updatesite'
FEATURE = BASE / 'com.ktylabs.stm32configurator.feature' / 'feature.xml'

def version():
    root = ET.parse(FEATURE).getroot()
    v = root.attrib['version']
    if not v or '.qualifier' in v:
        raise ValueError('Use a concrete version in feature.xml before building.')
    return v

def replace_zip_entry(path, name, data):
    with zipfile.ZipFile(path) as z:
        entries = [(i, z.read(i.filename)) for i in z.infolist()]
    temp = path.with_suffix('.tmp')
    with zipfile.ZipFile(temp, 'w') as z:
        for info, payload in entries:
            z.writestr(info, data if info.filename == name else payload)
    temp.replace(path)

def package():
    v = version()
    targets = {
        ('osgi.bundle', 'com.ktylabs.stm32configurator', v): SITE / 'plugins' / f'com.ktylabs.stm32configurator_{v}.jar',
        ('org.eclipse.update.feature', 'com.ktylabs.stm32configurator.feature', v): SITE / 'features' / f'com.ktylabs.stm32configurator.feature_{v}.jar',
    }
    for path in targets.values():
        if not path.is_file():
            raise FileNotFoundError(path)
        with zipfile.ZipFile(path) as z:
            if not any(n.upper().startswith('META-INF/') and n.upper().endswith('.SF') for n in z.namelist()):
                raise ValueError(f'Unsigned JAR: {path}')
    artifactjar = SITE / 'artifacts.jar'
    contentjar = SITE / 'content.jar'
    if not artifactjar.is_file() or not contentjar.is_file():
        raise FileNotFoundError('Build the Eclipse update site first: artifacts.jar/content.jar missing')
    with zipfile.ZipFile(artifactjar) as z:
        root = ET.fromstring(z.read('artifacts.xml'))
    found = set()
    artifacts = root.find('artifacts')
    # Prune older KTYLabs releases from the p2 artifact repository.
    for item in list(artifacts):
        if item.get('id', '').startswith('com.ktylabs.stm32configurator') and item.get('version') != v:
            artifacts.remove(item)
    artifacts.set('size', str(len(artifacts)))
    for artifact in artifacts:
        key = (artifact.get('classifier'), artifact.get('id'), artifact.get('version'))
        if key not in targets: continue
        found.add(key)
        payload = targets[key].read_bytes()
        props = artifact.find('properties')
        if props is None:
            props = ET.SubElement(artifact, 'properties')
        vals = {p.get('name'):p for p in props.findall('property')}
        updates = {'download.size':str(len(payload))}
        for name in vals:
            if name in ('artifact.size',): updates[name] = str(len(payload))
            if name in ('download.md5', 'artifact.md5'): updates[name] = hashlib.md5(payload).hexdigest()
            if name in ('download.sha256', 'artifact.sha256'): updates[name] = hashlib.sha256(payload).hexdigest()
        for name, val in updates.items():
            if name in vals: vals[name].set('value',val)
            else: ET.SubElement(props,'property',name=name,value=val)
        props.set('size',str(len(props)))
    if found != set(targets):
        raise ValueError('p2 metadata does not contain both current KTYLabs JARs; run Eclipse Build All again')
    xml = b"<?xml version='1.0' encoding='UTF-8'?>\n<?artifactRepository version='1.1.0'?>\n" + ET.tostring(root,encoding='utf-8')
    replace_zip_entry(artifactjar,'artifacts.xml',xml)
    # Prune matching installable units, including feature.group and feature.jar.
    with zipfile.ZipFile(contentjar) as z:
        content = ET.fromstring(z.read('content.xml'))
    units = content.find('units')
    if units is None:
        raise ValueError('content.xml has no installable units')
    for unit in list(units):
        if unit.get('id', '').startswith('com.ktylabs.stm32configurator') and unit.get('version') != v:
            units.remove(unit)
    units.set('size', str(len(units)))
    contentxml = (b"<?xml version='1.0' encoding='UTF-8'?>\n"
                  + ET.tostring(content, encoding='utf-8'))
    replace_zip_entry(contentjar, 'content.xml', contentxml)
    # Remove older KTYLabs binaries from the generated release without touching
    # the Eclipse build output, so the next Build All remains unaffected.
    def include(path):
        rel = path.relative_to(SITE).as_posix()
        if rel.startswith(('plugins/com.ktylabs.stm32configurator_',
                           'features/com.ktylabs.stm32configurator.feature_')):
            return rel.endswith('_' + v + '.jar')
        return True
    # Distribute the exact current KTYLabs release, plus any dependencies included by PDE.
    # Retain other repository files as generated, so p2 metadata stays consistent.
    outfile = BASE / 'STM32-CMake-Configurator.zip'
    with zipfile.ZipFile(outfile,'w',zipfile.ZIP_DEFLATED,compresslevel=6) as z:
        for p in sorted(SITE.rglob('*')):
            if not p.is_file() or p.name == 'features.zip': continue
            if p.suffix == '.zip' and p.name.lower() == 'features.zip': continue
            if not include(p): continue
            z.write(p,p.relative_to(SITE).as_posix())
    checksum = hashlib.sha256(outfile.read_bytes()).hexdigest()
    checksum_file = BASE / 'STM32-CMake-Configurator.sha256.txt'
    checksum_file.write_text(f'{checksum}  {outfile.name}\n', encoding='ascii')
    print(f'Created {outfile} ({outfile.stat().st_size:,} bytes)')
    print(f'SHA-256: {checksum}')
    print(f'Checksum file: {checksum_file}')
    print(f'Excluded older KTYLabs releases; current version: {v}')
    print('Self-signed certificates require manual trust. Verify certificate fingerprint before trusting.')

if __name__ == '__main__':
    try:
        if len(sys.argv) != 2 or sys.argv[1] not in ('version','package'):
            raise ValueError('Usage: py -3 ReleaseTools.py version|package')
        if sys.argv[1] == 'version': print(version())
        else: package()
    except Exception as e:
        print('ERROR:',e,file=sys.stderr)
        sys.exit(1)
