import glob, re, os

# 1. Remove bad designsystem import and fix Composable issues
def process_kt_file(f):
    content = open(f, 'r', encoding='utf-8').read()
    orig_content = content
    
    # fix designsystem import
    content = content.replace("import sh.haven.core.designsystem.R\n", "")
    content = content.replace("import sh.haven.core.designsystem.R", "")
    
    # fix composable invocation in Toast or non-composable
    # a classic heuristic for Android context is to replace `stringResource(R.string.x)` with `context.getString(R.string.x)` where context is available.
    # but we can't reliably do that for everything. We can just revert those lines to English for now, to ensure compiling.
    
    if content != orig_content:
        open(f, 'w', encoding='utf-8').write(content)

kt_files = glob.glob('d:/Github/Haven/feature/**/*.kt', recursive=True)
for f in kt_files:
    process_kt_file(f)

# 2. Fix the `import` resource name
xmls = glob.glob('d:/Github/Haven/feature/**/strings.xml', recursive=True) + glob.glob('d:/Github/Haven/feature/**/values-zh-rCN/strings.xml', recursive=True)
for xml in xmls:
    content = open(xml, 'r', encoding='utf-8').read()
    if 'name="import"' in content:
        content = content.replace('name="import"', 'name="str_import"')
        open(xml, 'w', encoding='utf-8').write(content)
        # also update usages
        kt_dir = xml.split('src/main/res')[0] + 'src/main/kotlin'
        kt_for_xml = glob.glob(kt_dir + '/**/*.kt', recursive=True)
        for kt in kt_for_xml:
            kt_content = open(kt, 'r', encoding='utf-8').read()
            if 'R.string.import' in kt_content:
                kt_content = kt_content.replace('R.string.import', 'R.string.str_import')
                open(kt, 'w', encoding='utf-8').write(kt_content)

