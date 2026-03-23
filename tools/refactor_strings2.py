import os
import re
from pathlib import Path

def safe_string_name(eng):
    words = re.findall(r'[a-zA-Z0-9]+', eng.lower())
    if not words: return "str_unknown"
    return "_".join(words[:5])

def fix_multi_line_and_assignments(file_path):
    content = Path(file_path).read_text('utf-8')
    res_en = {}
    res_zh = {}
    
    # 1. multi_line strings:
    # if (zh) {
    #     "zh string"
    # } else {
    #     "en string " +
    #     "more en string"
    # }
    # This is tricky because strings could be concatenated. Let's do it manually since there are only a few.
    # Actually, we can just print them and I will replace them using `replace_file_content`.
    pass

def print_findings():
    files = [
        'feature/connections/src/main/kotlin/sh/haven/feature/connections/ConnectionsScreen.kt',
        'feature/sftp/src/main/kotlin/sh/haven/feature/sftp/SftpScreen.kt',
        'feature/keys/src/main/kotlin/sh/haven/feature/keys/KeysScreen.kt'
    ]
    for f in files:
        lines = Path(f).read_text('utf-8').split('\n')
        for i, l in enumerate(lines):
            if 'val zh = ' in l:
                print(f"{f}:{i+1}: {l.strip()}")
            elif 'if (zh)' in l:
                print(f"{f}:{i+1}: {l.strip()}")

print_findings()
