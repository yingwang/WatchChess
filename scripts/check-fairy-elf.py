"""Check the static AArch64 contract and NDK r26 constructor sentinel ordering."""
import struct
import sys
from pathlib import Path

data = Path(sys.argv[1]).read_bytes()
assert data[:6] == b'\x7fELF\x02\x01', 'Expected little-endian ELF64'
assert struct.unpack_from('<HH', data, 16) == (2, 183), 'Expected AArch64 ET_EXEC'
phoff, shoff = struct.unpack_from('<QQ', data, 32)
phsize, phnum, shsize, shnum, shstrndx = struct.unpack_from('<HHHHH', data, 54)
headers = [struct.unpack_from('<IIQQQQQQ', data, phoff + i * phsize) for i in range(phnum)]
assert not any(h[0] in (2, 3) for h in headers), 'DYNAMIC/INTERP is forbidden'
tls = [h for h in headers if h[0] == 7]
assert len(tls) == 1 and tls[0][7] >= 64, 'PT_TLS must be aligned to at least 64 bytes'
sections = [struct.unpack_from('<IIQQQQIIQQ', data, shoff + i * shsize) for i in range(shnum)]
names = sections[shstrndx]
strings = data[names[4]:names[4] + names[5]]
for s in sections:
    name = strings[s[0]:].split(b'\0', 1)[0]
    if name == b'.init_array':
        entries = struct.unpack_from('<' + 'Q' * (s[5] // 8), data, s[4])
        assert entries[0] == 0xffffffffffffffff, 'Missing r26 start sentinel'
        assert entries[-1] == 0, 'Missing r26 end sentinel'
        assert 0 not in entries[1:-1], 'Constructor placed after end sentinel (LTO bug)'
        break
else:
    raise AssertionError('Missing constructor table')
print('PASS: static AArch64, TLS alignment >= 64, constructor sentinel is last')
