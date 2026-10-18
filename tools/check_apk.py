"""Read-only APK metadata and ELF segment alignment audit; does not install the APK."""
import hashlib, json, pathlib, struct, sys, zipfile
path = pathlib.Path(sys.argv[1])
result = {'apk': str(path), 'bytes': path.stat().st_size, 'sha256': hashlib.sha256(path.read_bytes()).hexdigest(), 'native_libraries': []}
with zipfile.ZipFile(path) as archive:
    for name in archive.namelist():
        if not name.startswith('lib/') or not name.endswith('.so'): continue
        data = archive.read(name)
        assert data[:4] == b'\x7fELF'
        endian = '<' if data[5] == 1 else '>'
        wide = data[4] == 2
        phoff = struct.unpack_from(endian + ('Q' if wide else 'I'), data, 32 if wide else 28)[0]
        entsize, count = struct.unpack_from(endian + 'HH', data, 54 if wide else 42)
        aligns = []
        for i in range(count):
            entry = phoff + i * entsize
            if struct.unpack_from(endian + 'I', data, entry)[0] == 1:
                aligns.append(struct.unpack_from(endian + ('Q' if wide else 'I'), data, entry + (48 if wide else 28))[0])
        result['native_libraries'].append({'name': name, 'load_segment_alignment': aligns, 'supports_16k_alignment': bool(aligns) and min(aligns) >= 16384})
print(json.dumps(result, ensure_ascii=False, indent=2))
