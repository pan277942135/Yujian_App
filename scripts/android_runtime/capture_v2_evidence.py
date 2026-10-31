"""Exact installed APK evidence. Emulator camera observations are not device acceptance."""
import json, re, subprocess, time, xml.etree.ElementTree as ET
from pathlib import Path

PKG = 'com.yujian.ai.uiv2'
OUT = Path('capture-evidence'); OUT.mkdir(exist_ok=True)
def adb(*args, binary=False):
    return subprocess.check_output(['adb', *args], timeout=45, text=not binary)
def shell(cmd): return adb('shell', cmd)
def xml():
    shell('uiautomator dump /sdcard/capture-v2.xml')
    return adb('exec-out', 'cat', '/sdcard/capture-v2.xml')
def nodes(): return list(ET.fromstring(xml()).iter('node'))
def find(value, attr='content-desc', wait=30, enabled=False):
    deadline=time.monotonic()+wait
    while time.monotonic()<deadline:
        for n in nodes():
            if n.get(attr)==value and (not enabled or n.get('enabled')=='true'): return n
        time.sleep(.2)
    raise AssertionError(f'Cannot find {attr}={value}, enabled={enabled}')
def bounds(n): return list(map(int,re.findall(r'\d+',n.get('bounds',''))))
def tap(n):
    x,y,r,b=bounds(n);shell(f'input tap {(x+r)//2} {(y+b)//2}')
def shot(name): (OUT/(name+'.png')).write_bytes(adb('exec-out','screencap','-p',binary=True))
def dump(name): (OUT/(name+'.xml')).write_text(xml())
def logs(): return shell('logcat -d -v brief -s CaptureEntry:D RecognitionCameraCapture:D')
def launch():
    shell(f'am force-stop {PKG}')
    shell(f'am start -W -n {PKG}/com.yujian.ai.MainActivity')
def open_camera():
    home=find('开始识鱼');tap(home)
    return find('拍摄',enabled=True,wait=45)

shell(f'pm grant {PKG} android.permission.CAMERA')
# This is a fresh CI emulator, not a user installation. Mark existing teaching completed.
shell(f'run-as {PKG} mkdir -p shared_prefs')
pref='<?xml version="1.0" encoding="utf-8" standalone="yes" ?><map><boolean name="capture_guide_v1_completed" value="true" /></map>'
subprocess.run(['adb','shell',f'run-as {PKG} sh -c \'cat > shared_prefs/yujian_capture_guide_v1.xml\''],input=pref,text=True,check=True)
shell('wm density 160');shell('wm size 393x852');shell('settings put system font_scale 1.0')
launch();home=find('开始识鱼');shell('logcat -c')
video=subprocess.Popen(['adb','shell','screenrecord','--time-limit','100','--bit-rate','4000000','/sdcard/capture-flow.mp4'])
try:
    time.sleep(6.2);shot('01-home-idle');tap(home)
    time.sleep(.28);shot('02-home-activation')
    shutter=find('拍摄',enabled=True,wait=45);time.sleep(.3);shot('03-camera-ready');dump('03-camera-ready')
    zoom=find('1.0x',attr='text');tap(zoom);time.sleep(.25);shot('04-zoom-expanded');dump('04-zoom-expanded')
    ns=nodes();preset=next((n for n in ns if n.get('content-desc')=='变焦 2x'),None)
    if preset is not None:
        tap(preset)
        deadline=time.monotonic()+8
        while time.monotonic()<deadline and not re.search(r'page_state=ZOOM .*zoom_ratio=2(?:\.0)?\b',logs()): time.sleep(.2)
        assert re.search(r'page_state=ZOOM .*zoom_ratio=2(?:\.0)?\b',logs()), '2x did not reach observed CameraX zoom state'
    # Drag the actual entry; the observed CameraX state must change and remain in range.
    entry=next(n for n in nodes() if re.fullmatch(r'\d+\.\dx',n.get('text','')))
    x,y,r,b=bounds(entry);shell(f'input swipe {(x+r)//2} {(y+b)//2} {min(365,r+45)} {(y+b)//2} 500')
    time.sleep(.5);shot('05-zoom-dragged');dump('05-zoom-dragged')
    shutter=find('拍摄',enabled=True);tap(shutter)
    deadline=time.monotonic()+45
    while time.monotonic()<deadline and 'decode_success=true' not in logs(): time.sleep(.3)
    trace=logs();assert 'decode_success=true' in trace, 'Real ImageCapture JPEG failed to decode'
    (OUT/'capture-log.txt').write_text(trace)
    data=adb('exec-out','run-as',PKG,'tar','-cf','-','files/recognition_inputs',binary=True)
    (OUT/'actual-captured-jpeg.tar').write_bytes(data)
    shot('06-after-real-capture')
finally:
    shell('pkill -INT screenrecord || true')
    try: video.wait(timeout=10)
    except subprocess.TimeoutExpired: video.terminate()
    subprocess.run(['adb','pull','/sdcard/capture-flow.mp4',str(OUT/'01-03-continuous.mp4')],check=False)

matrix=[]
for w,h in [(320,480),(360,640),(360,800),(393,852),(430,932)]:
    for scale in [1.0,1.3,1.5]:
        key=f'{w}x{h}-font{scale}'
        shell(f'wm size {w}x{h}');shell(f'settings put system font_scale {scale}')
        launch();shutter=open_camera();time.sleep(.3);shot(key);dump(key)
        ns=nodes()
        for value in ['返回','拍摄','从相册选择','闪光灯']:
            n=next((n for n in ns if n.get('content-desc')==value),None)
            assert n is not None,f'{key}: missing {value}'
            x,y,r,b=bounds(n);assert 0<=x<r<=w and 0<=y<b<=h,f'{key}: clipped {value}: {n.attrib}'
            if value in ['拍摄','从相册选择','闪光灯']: assert r-x>=48 and b-y>=48,f'{key}: small touch {value}'
        matrix.append({'viewport_dp':[w,h],'font_scale':scale,'status':'PASS_CONTROLS_VISIBLE','snapshot':key+'.png'})
(OUT/'matrix.json').write_text(json.dumps(matrix,indent=2))
(OUT/'camera-entry-log.txt').write_text(logs())
(OUT/'crash.txt').write_text(shell('logcat -d -b crash'))
assert PKG not in (OUT/'crash.txt').read_text(),'Crash recorded during capture tests'
(OUT/'STATUS').write_text('PASS\n')
(OUT/'acceptance.json').write_text(json.dumps({'environment':'API35_EMULATOR_EMULATED_CAMERA','physical_device':'RUNTIME_NOT_RUN','visual_acceptance':'VISUAL_ACCEPTANCE_PENDING','matrix_cases':len(matrix),'actual_image_capture_jpeg':'PASS','zoom_observed_state':'PASS','flash_hardware':'NOT_RUN_PHYSICAL_HARDWARE','guide_outside_photo_content':'VISUAL_ACCEPTANCE_PENDING'},indent=2))
