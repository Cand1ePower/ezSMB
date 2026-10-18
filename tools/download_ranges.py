"""Download official tool archives with bounded range concurrency and verify size."""
import concurrent.futures, pathlib, subprocess, sys
url, dest, size = sys.argv[1], pathlib.Path(sys.argv[2]), int(sys.argv[3])
dest.parent.mkdir(parents=True, exist_ok=True)
chunk = 4 * 1024 * 1024
def get(i):
    start, end = i * chunk, min((i + 1) * chunk, size) - 1
    part = dest.with_suffix(dest.suffix + f'.part{i}')
    if not part.exists() or part.stat().st_size != end - start + 1:
        subprocess.run(['curl.exe', '-sSfL', '--retry', '4', '--max-time', '240', '-r', f'{start}-{end}', '-o', str(part), url], check=True)
    if part.stat().st_size != end - start + 1: raise RuntimeError('Server ignored range')
    print(f'part {i} complete', flush=True)
    return part
with concurrent.futures.ThreadPoolExecutor(max_workers=12) as pool:
    parts = list(pool.map(get, range((size + chunk - 1) // chunk)))
with dest.open('wb') as out:
    for part in parts:
        out.write(part.read_bytes())
assert dest.stat().st_size == size
print(f'Complete: {dest}', flush=True)
