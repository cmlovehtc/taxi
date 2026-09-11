"""Real-device smoke checks on an isolated Android emulator, using UIAutomator + adb."""
import json
import pathlib
import re
import subprocess
import time
import traceback
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[1]
OUT = ROOT / 'build' / 'device-check'
OUT.mkdir(parents=True, exist_ok=True)
PACKAGE = 'tw.cmlove.taxiexam.preview'
ACTIVITY = PACKAGE + '/tw.cmlove.taxiexam.MainActivity'

def adb(*args, check=True, binary=False, input=None):
    result = subprocess.run(['adb', *args], input=input, capture_output=True, timeout=45)
    if check and result.returncode:
        raise RuntimeError(result.stderr.decode(errors='replace'))
    return result.stdout if binary else result.stdout.decode(errors='replace')

def launch():
    adb('shell', 'am', 'start', '-W', '-n', ACTIVITY)
    time.sleep(2)
    assert adb('shell', 'pidof', PACKAGE).strip(), 'App failed to launch'

def tree():
    adb('shell', 'uiautomator', 'dump', '/sdcard/taxi-window.xml')
    raw = adb('shell', 'cat', '/sdcard/taxi-window.xml')
    (OUT / 'last-window.xml').write_text(raw)
    return ET.fromstring(raw)

def tap(caption, attempts=7):
    for attempt in range(attempts):
        for node in tree().iter('node'):
            if node.get('text') == caption or node.get('content-desc') == caption:
                box = [int(n) for n in re.findall(r'\d+', node.get('bounds', ''))]
                if len(box) == 4 and box[2] > box[0] and box[3] > box[1]:
                    adb('shell', 'input', 'tap', str((box[0]+box[2])//2), str((box[1]+box[3])//2))
                    time.sleep(.35)
                    return
        adb('shell', 'input', 'swipe', '530', '1900', '530', '500', '250')
        time.sleep(.3)
    raise AssertionError('Visible action not found: ' + caption)

def state():
    return json.loads(adb('exec-out', 'run-as', PACKAGE, 'cat', 'files/study-state.json'))

def picture(name):
    (OUT / (name + '.png')).write_bytes(adb('exec-out', 'screencap', '-p', binary=True))

def main():
    adb('install', '-r', str(ROOT/'build'/'taxi-exam-0.1.0.apk'))
    adb('logcat', '-c')
    launch()
    picture('01-home')
    tap('自由練習')
    tap('開始作答')
    current = state()['active']
    assert len(current['questions']) == 10
    question = current['questions'][current['index']]
    caption = ('○  是' if question['answer'] == 0 else '×  否') if question['tf'] else '選項 '+str(question['answer']+1)
    tap(caption)
    assert state()['active']['answers'][0] == question['answer']
    picture('02-quiz-feedback')
    tap('下一題')
    assert state()['active']['index'] == 1
    adb('shell', 'input', 'keyevent', '4')
    tap('儲存並離開')
    adb('shell', 'am', 'force-stop', PACKAGE)
    launch()
    tap('繼續作答')
    assert state()['active']['index'] == 1
    assert state()['active']['answers'][0] == question['answer']
    for index in range(1, 10):
        current = state()['active']
        q = current['questions'][current['index']]
        answer = (q['answer']+1) % q['choices'] if index == 1 else q['answer']
        tap(('○  是' if answer == 0 else '×  否') if q['tf'] else '選項 '+str(answer+1))
        if index < 9:
            tap('下一題')
    tap('交卷並查看成績')
    tap('交卷')
    result = state()
    assert result['active'] is None
    assert result['history'][0]['score'] == 90, result['history'][0]
    assert result['coins'] == 27 and len(result['earned']) == 9
    assert len(result['wrong']) == 1
    picture('03-result')
    tap('查看本次錯題')
    picture('04-wrong-question')
    tap('車庫')
    tap('深藍')
    assert state()['carColor'] == '#41658C'
    picture('05-garage')
    tap('購買並安裝')
    assert state()['coins'] == 27 and not state()['owned'], 'Unaffordable purchase changed balance'
    tap('我的')
    tap('夜間模式')
    assert state()['night'] is True
    picture('06-night-mode')
    tap('首頁')
    picture('07-night-home')
    # A controlled balance fixture exercises successful purchases in this empty test emulator.
    fixture = state()
    fixture['coins'] = 200
    adb('shell', 'am', 'force-stop', PACKAGE)
    adb('shell', 'run-as', PACKAGE, 'sh', '-c', '"cat > files/study-state.json"', input=json.dumps(fixture).encode())
    launch()
    tap('車庫')
    tap('購買並安裝')
    assert state()['coins'] == 50 and state()['owned'] == ['dashcam'], 'Purchase ledger failed'
    log = adb('logcat', '-d', '-s', 'AndroidRuntime:E')
    assert 'FATAL EXCEPTION' not in log, log
    (OUT/'result.json').write_text(json.dumps({'passed': True, 'checks': ['APK install', 'native launch', '10-question weighted practice', 'answer persistence', 'force-stop/resume', '90-percent score', 'wrong-answer notebook', 'first-correct rewards', 'free car color', 'insufficient funds', 'successful purchase', 'night mode', 'no runtime crash']}, ensure_ascii=False, indent=2))
    print('PASS: APK installed and native UI flows verified on Android 15.')

try:
    main()
except Exception:
    (OUT/'failure.txt').write_text(traceback.format_exc())
    try:
        picture('failure')
        (OUT/'logcat.txt').write_text(adb('logcat', '-d', '-t', '1000', check=False))
    except Exception:
        pass
    raise
