"""Loopback-only SMB2 share with public sample files for documentation captures.

Run with a Python environment containing impacket. Connect via adb reverse
tcp:1445 tcp:1445 and smb://127.0.0.1:1445. Ephemeral login details are
written to ignored output/screenshot-login.json; delete them after capture.
This is a real SMB server; no application code or stored app state is replaced.
"""
import logging
import json
import secrets
from pathlib import Path
from impacket import smbserver
from impacket.ntlm import compute_lmhash, compute_nthash
from windows_fixture_io import enable

root = Path(__file__).resolve().parents[1] / 'output' / 'screenshot-share'
root.mkdir(parents=True, exist_ok=True)
enable(root)
for folder in ['Documents', 'Photos', 'Uploads']:
    (root / folder).mkdir(exist_ok=True)
samples = {
    '欢迎使用 ezSMB.txt': '这是专门用于界面截图的公开测试文件。\n手机与电脑之间，轻松传文件。\n',
    'Documents/项目说明.txt': '通过真实 SMB2 连接读取此目录。\n',
    'Documents/旅行清单.txt': '相机\n充电器\n出行资料\n',
    '使用说明.md': '# ezSMB\n\n点击文件下载，点击上传选择手机文件。\n',
}
for name, contents in samples.items():
    (root / name).write_text(contents, encoding='utf-8')
sample = root / 'sample-100MB.bin'
if not sample.exists():
    with sample.open('wb') as stream:
        block = bytes(range(256)) * 4096
        for _ in range(100):
            stream.write(block)
server = smbserver.SimpleSMBServer(listenAddress='127.0.0.1', listenPort=1445)
server.setSMB2Support(True)
server.getServer().getServerConfig().set('global', 'server_name', 'EZSMB-DEMO')
server.getServer().processConfigFile()
server.addShare('Share', str(root), 'ezSMB documentation samples', readOnly='no')
server.setLogFile(str(root.parent / 'screenshot-smb.log'))
password = secrets.token_hex(16)
server.addCredential('demo', 0, compute_lmhash(password), compute_nthash(password))
(root.parent / 'screenshot-login.json').write_text(
    json.dumps({'username': 'demo', 'password': password}), encoding='utf-8')
logging.disable(logging.INFO)
print('Documentation SMB2 share listening on 127.0.0.1:1445', flush=True)
server.start()
