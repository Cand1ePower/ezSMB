"""Capture the foreground ezSMB screen only; never overwrite an existing image."""
import argparse
import pathlib
import subprocess
import sys

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--adb', default='adb', help='Path to Android adb')
parser.add_argument('--serial', help='Authorized device serial, required if multiple devices are connected')
parser.add_argument('--output', required=True, type=pathlib.Path)
args = parser.parse_args()
if args.output.exists():
    parser.error('Output already exists; choose another filename.')
devices = subprocess.check_output([args.adb, 'devices'], text=True)
authorized = [line.split()[0] for line in devices.splitlines()[1:] if line.endswith('\tdevice')]
serial = args.serial or (authorized[0] if len(authorized) == 1 else None)
if serial not in authorized:
    parser.error('Connect and authorize one device, or specify --serial.')
command = [args.adb, '-s', serial]
windows = subprocess.check_output(command + ['shell', 'dumpsys', 'window', 'windows'], text=True)
focus = [line for line in windows.splitlines() if 'mCurrentFocus=' in line]
if not focus or 'com.ezsmb.app/' not in focus[0]:
    sys.exit('ezSMB is not the focused app. Open the intended ezSMB screen first.')
data = subprocess.check_output(command + ['exec-out', 'screencap', '-p'])
if not data.startswith(b'\x89PNG\r\n\x1a\n'):
    sys.exit('Device did not return a PNG; nothing was saved.')
args.output.parent.mkdir(parents=True, exist_ok=True)
with args.output.open('xb') as output:
    output.write(data)
print(f'Saved {args.output}. Review names, paths and notifications before publishing.')
