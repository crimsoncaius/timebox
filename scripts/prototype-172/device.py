"""Review helper. All device operations go through the reservation helper."""
import subprocess, sys, re, xml.etree.ElementTree as ET
from pathlib import Path

TOKEN = '02d7e60066f5431e84b0a3bb202fe8d8'
OUT = Path('docs/design/day-visibility-172')
OUT.mkdir(parents=True, exist_ok=True)
def adb(*args):
    subprocess.run([sys.executable,'scripts/android-emulator.py','adb',TOKEN,*args],check=True,stdout=subprocess.DEVNULL)
def nodes():
    adb('shell','uiautomator','dump','/sdcard/issue172.xml')
    adb('pull','/sdcard/issue172.xml',str(OUT/'latest.xml'))
    return list(ET.parse(OUT/'latest.xml').iter('node'))
def tap(node):
    x,y,x2,y2=map(int,re.findall(r'\d+',node.attrib['bounds']))
    adb('shell','input','tap',str((x+x2)//2),str((y+y2)//2))
def click(label):
    matches=[n for n in nodes() if n.get('text')==label or n.get('content-desc')==label]
    if not matches: raise ValueError(f'Not visible: {label}')
    target = next((n for n in matches if n.get('clickable') == 'true' or n.get('checkable') == 'true'), matches[0])
    tap(target)
def capture(name):
    tree=nodes()
    adb('shell','screencap','-p','/sdcard/issue172.png')
    adb('pull','/sdcard/issue172.png',str(OUT/f'{name}.png'))
    (OUT/f'{name}.xml').write_bytes((OUT/'latest.xml').read_bytes())
    print(name, [(n.get('text'),n.get('bounds')) for n in tree if n.get('text') in ('PLANNED','ACTUAL')])

if __name__=='__main__':
    action=sys.argv[1]
    if action=='capture': capture(sys.argv[2])
    elif action=='click': click(sys.argv[2])
    elif action=='hide':
        click('View')
        for n in nodes():
            if n.get('checkable')=='true' and n.get('checked')=='true': tap(n)
        click('Done')
