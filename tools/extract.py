import re
from pathlib import Path

def extract_strings(file_path):
    content = Path(file_path).read_text('utf-8')
    # Use a simpler regex to grab strings
    matches = re.findall(r'if\s*\(\s*zh\s*\)\s*"([^"]+)"\s*else\s*"([^"]+)"', content)
    for zh_val, en_val in matches:
        print(f"ZH: {zh_val}")
        print(f"EN: {en_val}")

extract_strings('feature/connections/src/main/kotlin/sh/haven/feature/connections/ConnectionsScreen.kt')
