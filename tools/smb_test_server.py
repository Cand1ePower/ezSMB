"""Isolated SMB2 test fixture, bound to loopback; never modifies OS shares."""
import pathlib, secrets, sys, logging
from impacket import smbserver
from impacket.ntlm import compute_lmhash, compute_nthash

root = pathlib.Path(__file__).resolve().parents[1] / '.tools' / 'smb-fixture'
root.mkdir(parents=True, exist_ok=True)
from windows_fixture_io import enable
enable(root)
user, password = 'ezsmb_test', secrets.token_hex(24)
(root.parent / 'test-login.properties').write_text(f'host=127.0.0.1\nport=1445\nuser={user}\npassword={password}\nshare=EZTEST\n', encoding='utf-8')
samples = [(0, 'zero.bin'), (1024, '中文 空格 😀.bin'), (100 * 1024 * 1024, 'sample-100MB.bin')]
if '--large' in sys.argv:
    samples.append((1024 ** 3, 'sample-1GB.bin'))
for size, name in samples:
    file = root / name
    if not file.exists() or file.stat().st_size != size:
        block = bytes(range(256)) * 4096
        with file.open('wb') as out:
            remaining = size
            while remaining:
                part = block[:min(len(block), remaining)]
                out.write(part); remaining -= len(part)
server = smbserver.SimpleSMBServer(listenAddress='127.0.0.1', listenPort=1445)
server.addShare('EZTEST', str(root), 'ezSMB isolated integration fixture', readOnly='no')
server.setSMB2Support(True)
server.setLogFile(str(root.parent / 'smb-server.log'))
if '--anonymous' not in sys.argv:
    server.addCredential(user, 0, compute_lmhash(password), compute_nthash(password))
elif '--null-session' in sys.argv:
    # Explicit protocol fixture for servers granting NULL sessions to empty NTLM identities.
    # Stock Impacket maps SMBJ's empty identity to Guest. Keep that separate test mode.
    # This hook changes only this loopback fixture, never the production client or OS shares.
    from impacket import smb3structs
    original_setup = None
    def null_setup(conn_id, smb, packet):
        commands, packets, status = original_setup(conn_id, smb, packet)
        data = smb.getConnectionData(conn_id, checkStatus=False)
        auth = data.get('AUTHENTICATE_MESSAGE')
        if status == 0 and auth is not None and not auth['user_name'] and not auth['domain_name']:
            for command in commands:
                command['SessionFlags'] = 2  # SMB2_SESSION_FLAG_IS_NULL
        return commands, packets, status
    original_setup = server.getServer().hookSmb2Command(smb3structs.SMB2_SESSION_SETUP, null_setup)
# Do not log NTLM challenge responses, even for this randomly generated test account.
logging.disable(logging.INFO)
print('Loopback SMB2 fixture listening on 127.0.0.1:1445', flush=True)
server.start()
