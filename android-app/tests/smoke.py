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

def launch(extra=()):
    adb('shell', 'am', 'start', '-W', '-n', ACTIVITY, *extra)
    for _ in range(45):
        time.sleep(1)
        if adb('shell', 'pidof', PACKAGE).strip():
            raw = adb('exec-out', 'run-as', PACKAGE, 'cat', 'files/study-state.json', check=False)
            if raw.startswith('{'):
                time.sleep(1)
                return
    raise AssertionError('App failed to launch')

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

def write_state(fixture):
    adb('shell', 'am', 'force-stop', PACKAGE)
    adb('shell', 'run-as', PACKAGE, 'sh', '-c', '"cat > files/study-state.json"', input=json.dumps(fixture).encode())

def set_field(caption, value):
    for _ in range(3): adb('shell','input','swipe','530','450','530','1850','180')
    for _ in range(16):
        for n in tree().iter('node'):
            if n.get('class') == 'android.widget.EditText' and n.get('content-desc') == caption:
                box = [int(v) for v in re.findall(r'\d+', n.get('bounds', ''))]
                if len(box) == 4 and box[3] > box[1]:
                    adb('shell', 'input', 'tap', str((box[0]+box[2])//2), str((box[1]+box[3])//2))
                    adb('shell', 'input', 'keyevent', '123')
                    for _ in range(5): adb('shell', 'input', 'keyevent', '67')
                    adb('shell', 'input', 'text', str(value))
                    adb('shell', 'input', 'keyevent', '4')
                    return
        adb('shell', 'input', 'swipe', '530', '1800', '530', '500', '200')
    raise AssertionError('Field missing: '+caption)

def main():
    checks=[]
    previous=ROOT/'build'/'previous'/'taxi-exam-0.1.0.apk'
    if previous.exists():
        adb('install','-r',str(previous));launch();tap('自由練習');tap('開始作答')
        prior=state();prior['night']=True;prior['coins']=321;prior['active']['index']=2
        old_id=prior['active']['id'];write_state(prior)
    adb('install', '-r', str(ROOT/'build'/'taxi-exam-0.2.0.apk'))
    adb('logcat', '-c');launch()
    if previous.exists():
        updated=state();assert updated['coins']==321 and updated['night'] is True
        assert updated['active']['id']==old_id and updated['active']['index']==2
        checks.append('0.1 -> 0.2 install update preserves active quiz, coins and settings')
    fixture=state();fixture['active']=None;fixture['night']=False;fixture['city']='基隆市';write_state(fixture)
    # Expected hashes are produced by executing all 6610 actual website explanations.
    adb('shell','run-as',PACKAGE,'sh','-c','"cat > files/web-parity.json"',input=(ROOT/'tests'/'web-parity.json').read_bytes())
    adb('shell','am','start','-W','-n',ACTIVITY,'--ez','verifyWebParity','true')
    parity=None
    for _ in range(300):
        time.sleep(1)
        raw=adb('exec-out','run-as',PACKAGE,'cat','files/web-parity-result.json',check=False)
        if raw.startswith('{'):parity=json.loads(raw);break
    assert parity is not None,'Website parity check timed out'
    (OUT/'web-parity-result.json').write_text(json.dumps(parity,ensure_ascii=False,indent=2))
    assert parity.get('passed') and parity.get('checked')==6610,parity
    checks.append('6610/6610 website question, option, topic, explanation, evidence and related-question parity')
    adb('shell','am','force-stop',PACKAGE);launch();tap('首頁');picture('01-home')
    for _ in range(30):
        if state().get('lastCheckSucceeded'): break
        time.sleep(1)
    assert state().get('lastCheckSucceeded'), 'Opening the app must check the official bank automatically'
    checks.append('Automatic bank check on open; unchanged content version reports current official bank')
    # Standard geography exam: 20 TF + 30 MC, sixty minutes, real preset.
    tap('開始模擬考');picture('02-keelung-setup');tap('開始作答')
    current=state()['active'];questions=current['questions'];assert len(questions)==50
    assert sum(q['tf'] for q in questions)==20 and all(q['tf'] for q in questions[:20])
    assert current['timed'] and 3550<current['remainingSeconds']<=3600
    assert sum(q['city']=='基隆市' for q in questions)==30
    assert all(sum(q['city']==city for q in questions)==5 for city in ['臺北市','新北市','桃園市','宜蘭縣'])
    checks.append('UI default 20 TF + 30 MC, Keelung 60/10/10/10/10 and 60 minutes')
    tap('○  是');assert state()['active']['answers'][0]==0
    picture('03-exam');tap('下一題');assert state()['active']['index']==1
    tap('儲存並返回首頁');remaining=state()['active']['remainingSeconds']
    adb('shell','am','force-stop',PACKAGE);launch();assert state()['active']['remainingSeconds']==remaining
    tap('繼續作答');assert state()['active']['index']==1
    checks.append('Answer persistence and remaining time preserved on leave/resume')
    # Fill a controlled answer sheet to exercise native result/70-point threshold.
    fixture=state();active=fixture['active'];active['answers']=[q['answer'] if i<35 else (q['answer']+1)%q['choices'] for i,q in enumerate(active['questions'])];write_state(fixture);launch();tap('繼續作答');tap('交卷並查看成績');tap('交卷')
    assert state()['history'][0]['score']==70;picture('04-result');checks.append('70 percent pass result and wrong question ledger')
    tap('查看本次錯題');picture('05-wrong-questions')
    # Preset selection is a dropdown. A different county produces its own ratios.
    tap('首頁');tap('開始模擬考');tap('報考縣市');tap('臺北市');picture('06-taipei-profile');tap('開始作答')
    paper=state()['active']['questions'];assert sum(q['city']=='臺北市' for q in paper)==15;assert sum(q['city']=='新北市' for q in paper)==15;assert sum(q['city']=='基隆市' for q in paper)==10
    checks.append('Taipei preset 30/30/20/10/10 via city dropdown')
    fixture=state();fixture['active']=None;write_state(fixture);launch();tap('開始模擬考')
    tap('只練報考縣市 100%');set_field('是非題數（可以填 0）',2);set_field('選擇題數（可以填 0）',3);tap('開始作答')
    custom=state()['active'];assert len(custom['questions'])==5 and not custom['timed'] and custom['remainingSeconds']==0
    assert all(q['city']=='臺北市' for q in custom['questions']);checks.append('Single-city 100% and custom counts have no timer')
    fixture=state();fixture['active']=None;write_state(fixture);launch();tap('題庫')
    tap('題庫縣市');tap('基隆市');tap('基隆市題解整理・最佳記憶法');picture('07-memory-guide');tap('題庫')
    tap('顯示答案');picture('08-question-explanation')
    visible=adb('shell','cat','/sdcard/taxi-window.xml');assert '收藏' not in visible
    # Direct original-number lookup exposes the reviewed explanation and comparisons.
    set_field('原題號',10);tap('跳轉');picture('09-reviewed-explanation');tap('返回閱讀')
    checks.append('Native memory guide, chapter selector, answer explanation, original-number lookup; no favorites')
    tap('我的');tap('夜間模式');assert state()['night'] is True;tap('題庫');picture('10-night-reading')
    log=adb('logcat','-d','-s','AndroidRuntime:E');assert 'FATAL EXCEPTION' not in log,log
    checks.append('Dark mode and no runtime crashes')
    (OUT/'result.json').write_text(json.dumps({'passed':True,'checks':checks},ensure_ascii=False,indent=2));print('PASS: native Android install/update and website-parity checks.')

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
