import os
import re
from pathlib import Path

def camel_to_snake(name):
    s1 = re.sub('(.)([A-Z][a-z]+)', r'\1_\2', name)
    return re.sub('([a-z0-9])([A-Z])', r'\1_\2', s1).lower()

def safe_string_name(eng):
    # Extract simple words
    words = re.findall(r'[a-zA-Z0-9]+', eng.lower())
    if not words: return "str_unknown"
    return "_".join(words[:5])

def extract_and_replace(file_path):
    content = Path(file_path).read_text('utf-8')
    
    res_en = {}
    res_zh = {}
    
    def replacer(match):
        zh_str = match.group(1)
        en_str = match.group(2)
        
        name = safe_string_name(en_str)
        
        var_pattern = r'\$([a-zA-Z0-9_]+)'
        
        en_vars = re.findall(var_pattern, en_str)
        zh_vars = re.findall(var_pattern, zh_str)
        
        en_xml = en_str
        zh_xml = zh_str
        args = []
        for i, v in enumerate(en_vars):
            en_xml = en_xml.replace(f'${v}', f'%{i+1}$s')
            args.append(v)
            
        for i, v in enumerate(zh_vars):
            zh_xml = zh_xml.replace(f'${v}', f'%{i+1}$s')
            
        base_name = name
        counter = 1
        while base_name in res_en and res_en[base_name] != en_xml:
            base_name = f"{name}_{counter}"
            counter += 1
            
        name = base_name
        res_en[name] = en_xml
        res_zh[name] = zh_xml
        
        if args:
            args_str = ", " + ", ".join(args)
            return f'stringResource(R.string.{name}{args_str})'
        else:
            return f'stringResource(R.string.{name})'

    new_content = re.sub(r'if\s*\(\s*zh\s*\)\s*"([^"]+)"\s*else\s*"([^"]+)"', replacer, content)
    
    if new_content != content:
        if 'androidx.compose.ui.res.stringResource' not in new_content:
            new_content = new_content.replace('import androidx.compose.runtime.Composable', 'import androidx.compose.runtime.Composable\nimport androidx.compose.ui.res.stringResource')
        Path(file_path).write_text(new_content, 'utf-8')
        
    return res_en, res_zh

def write_xml(data_dict, file_path):
    if not data_dict: return
    Path(file_path).parent.mkdir(parents=True, exist_ok=True)
    with open(file_path, 'w', encoding='utf-8') as f:
        f.write('<?xml version="1.0" encoding="utf-8"?>\n')
        f.write('<resources>\n')
        for k, v in data_dict.items():
            safe_v = v.replace("'", "\\'").replace("<", "&lt;").replace(">", "&gt;").replace("&", "&amp;")
            f.write(f'    <string name="{k}">{safe_v}</string>\n')
        f.write('</resources>\n')

files_to_process = [
    'feature/connections/src/main/kotlin/sh/haven/feature/connections/ConnectionsScreen.kt',
    'feature/sftp/src/main/kotlin/sh/haven/feature/sftp/SftpScreen.kt',
    'feature/keys/src/main/kotlin/sh/haven/feature/keys/KeysScreen.kt'
]

for f in files_to_process:
    en, zh = extract_and_replace(f)
    print(f"Processed {f}: {len(en)} strings")
    src_main = f.split('kotlin')[0]
    res_dir = Path(src_main) / 'res'
    write_xml(en, res_dir / 'values/strings.xml')
    write_xml(zh, res_dir / 'values-zh-rCN/strings.xml')
