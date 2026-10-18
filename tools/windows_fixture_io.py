"""Windows-only local fixture adapter: permit rename of an open SMB file.

CPython os.open does not request FILE_SHARE_DELETE. Impacket's POSIX-style
rename-while-open therefore fails on Windows. Only files inside the explicit
fixture directory use CreateFileW; production Android code is unaffected.
"""
def enable(root):
    import os
    if os.name != 'nt':
        return
    import ctypes, msvcrt
    from ctypes import wintypes
    original = os.open
    kernel = ctypes.WinDLL('kernel32', use_last_error=True)
    create = kernel.CreateFileW
    create.argtypes = [wintypes.LPCWSTR, wintypes.DWORD, wintypes.DWORD,
                       ctypes.c_void_p, wintypes.DWORD, wintypes.DWORD, wintypes.HANDLE]
    create.restype = wintypes.HANDLE
    close = kernel.CloseHandle
    close.argtypes = [wintypes.HANDLE]
    root_path = os.path.normcase(os.path.abspath(root))
    def shared_open(path, flags, mode=0o777, *, dir_fd=None):
        absolute = os.path.normcase(os.path.abspath(path))
        if dir_fd is not None or os.path.commonpath([absolute, root_path]) != root_path:
            return original(path, flags, mode, dir_fd=dir_fd)
        access = 0xC0000000 if flags & os.O_RDWR else (0x40000000 if flags & os.O_WRONLY else 0x80000000)
        disposition = (1 if flags & os.O_EXCL else (2 if flags & os.O_TRUNC else 4)) if flags & os.O_CREAT else (5 if flags & os.O_TRUNC else 3)
        handle = create(os.fsdecode(path), access, 7, None, disposition, 0x80, None)
        if handle == wintypes.HANDLE(-1).value:
            raise ctypes.WinError(ctypes.get_last_error())
        try:
            return msvcrt.open_osfhandle(handle, flags & (os.O_APPEND | os.O_RDONLY | os.O_WRONLY | os.O_RDWR | os.O_BINARY))
        except BaseException:
            close(handle)
            raise
    os.open = shared_open
