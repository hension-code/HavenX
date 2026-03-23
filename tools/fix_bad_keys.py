import glob, re

xmls = glob.glob('d:/Github/Haven/feature/**/strings.xml', recursive=True) + glob.glob('d:/Github/Haven/feature/**/values-zh-rCN/strings.xml', recursive=True)

for xml in xmls:
    content = open(xml, 'r', encoding='utf-8').read()
    bad_keys = set(re.findall(r'name=\"([0-9][^\"]*)\"', content))
    if bad_keys:
        print(f'{xml} has bad keys: {bad_keys}')
        for key in bad_keys:
            kt_dir = xml.split('src/main/res')[0] + 'src/main/kotlin'
            kt_files = glob.glob(kt_dir + '/**/*.kt', recursive=True)
            for kt in kt_files:
                kt_content = open(kt, 'r', encoding='utf-8').read()
                if f'R.string.{key}' in kt_content:
                    kt_content = kt_content.replace(f'R.string.{key}', f'R.string.str_{key}')
                    open(kt, 'w', encoding='utf-8').write(kt_content)
            
            content = content.replace(f'name="{key}"', f'name="str_{key}"')
        open(xml, 'w', encoding='utf-8').write(content)
        print("Fixed", xml)
