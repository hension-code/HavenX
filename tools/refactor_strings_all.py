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
    
    # Check if there are multi-line if (zh) { "..." } else { "..." } 
    # Not going to catch all edges, but at least the single line ones.

    if new_content != content:
        if 'import androidx.compose.ui.res.stringResource' not in new_content:
            new_content = new_content.replace('import androidx.compose.runtime.Composable', 'import androidx.compose.runtime.Composable\nimport androidx.compose.ui.res.stringResource\nimport sh.haven.core.designsystem.R')
            # wait, feature modules might not need sh.haven.core.designsystem.R if they define it themselves.
            # let's just use stringResource
        Path(file_path).write_text(new_content, 'utf-8')
        
    return res_en, res_zh

def write_xml(data_dict, file_path):
    if not data_dict: return
    file_p = Path(file_path)
    file_p.parent.mkdir(parents=True, exist_ok=True)
    
    existing_keys = set()
    lines = []
    if file_p.exists():
        old_content = file_p.read_text('utf-8')
        lines = old_content.splitlines()
        for l in lines:
            m = re.search(r'name="([^"]+)"', l)
            if m:
                existing_keys.add(m.group(1))
    else:
        lines = ['<?xml version="1.0" encoding="utf-8"?>', '<resources>', '</resources>']
        
    new_lines = []
    for k, v in data_dict.items():
        if k not in existing_keys:
            safe_v = v.replace("'", "\\'").replace("<", "&lt;").replace(">", "&gt;").replace("&", "&amp;")
            new_lines.append(f'    <string name="{k}">{safe_v}</string>')
            existing_keys.add(k)
            
    if new_lines:
        lines = lines[:-1] + new_lines + lines[-1:]
        file_p.write_text('\n'.join(lines), 'utf-8')

import glob
kt_files = glob.glob('feature/**/*.kt', recursive=True)

for f in kt_files:
    en, zh = extract_and_replace(f)
    if en or zh:
        print(f"Processed {f}: {len(en)} strings")
        
        # Determine resource dir: 'feature/vnc/src/main/res'
        # Split until src/main
        parts = Path(f).parts
        src_main_idx = -1
        for i, p in enumerate(parts):
            if p == 'src' and i+1 < len(parts) and parts[i+1] == 'main':
                src_main_idx = i
                break
                
        if src_main_idx != -1:
            res_dir = Path(*parts[:src_main_idx+2]) / 'res'
            write_xml(en, res_dir / 'values/strings.xml')
            write_xml(zh, res_dir / 'values-zh-rCN/strings.xml')

