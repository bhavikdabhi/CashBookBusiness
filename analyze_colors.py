import os
import re
import xml.etree.ElementTree as ET

# Paths
WORKSPACE = r"c:\Users\Lenovo\AndroidStudioProjects\CashBookBusiness"
COLORS_XML_PATH = os.path.join(WORKSPACE, "app", "src", "main", "res", "values", "colors.xml")

# Ignore folders
IGNORE_DIRS = {".git", ".gradle", ".idea", "build", "app/build"}

def normalize_hex(hex_str):
    # Strip whitespace, # and make uppercase
    h = hex_str.strip().lstrip('#').upper()
    if len(h) == 3:
        # e.g. FFF -> FFFFFF
        h = "".join(c*2 for c in h)
    if len(h) == 4:
        # e.g. FFFF -> FFFFFFFF
        h = "".join(c*2 for c in h)
    if len(h) == 6:
        # e.g. 6366F1 -> FF6366F1 (fully opaque)
        h = "FF" + h
    return h

def get_color_definitions():
    if not os.path.exists(COLORS_XML_PATH):
        print(f"Error: {COLORS_XML_PATH} does not exist.")
        return {}
    
    try:
        tree = ET.parse(COLORS_XML_PATH)
        root = tree.getroot()
        colors = {}
        for elem in root.findall('color'):
            name = elem.get('name')
            value = elem.text
            if name and value:
                normalized = normalize_hex(value)
                colors[name] = (value, normalized)
        return colors
    except Exception as e:
        print(f"Error parsing colors.xml: {e}")
        return {}

def find_duplicates(colors):
    # Group by normalized value
    val_to_names = {}
    for name, (raw_val, norm_val) in colors.items():
        val_to_names.setdefault(norm_val, []).append((name, raw_val))
    
    duplicates = {}
    for norm_val, name_raws in val_to_names.items():
        if len(name_raws) > 1:
            duplicates[norm_val] = name_raws
    return duplicates

def is_ignored(path):
    rel_path = os.path.relpath(path, WORKSPACE).replace('\\', '/')
    for d in IGNORE_DIRS:
        if rel_path.startswith(d + '/') or rel_path == d:
            return True
    return False

def search_usages(color_names):
    usages = {name: [] for name in color_names}
    
    # Compile regexes for each color name
    # XML usages: @color/name
    # Kotlin/Java usages: R.color.name
    patterns = {}
    for name in color_names:
        patterns[name] = (
            re.compile(rf'@color/{re.escape(name)}\b'),
            re.compile(rf'\bR\.color\.{re.escape(name)}\b'),
            re.compile(rf'\bcolor/{re.escape(name)}\b')
        )
    
    for root_dir, dirs, files in os.walk(WORKSPACE):
        # Filter ignored dirs
        dirs[:] = [d for d in dirs if not is_ignored(os.path.join(root_dir, d))]
        
        for file in files:
            file_path = os.path.join(root_dir, file)
            if is_ignored(file_path):
                continue
            
            # Skip binary files or common non-source files
            if not (file.endswith('.kt') or file.endswith('.java') or file.endswith('.xml') or file.endswith('.gradle') or file.endswith('.kts')):
                continue
                
            # Skip colors.xml itself to avoid self-reference
            if os.path.normpath(file_path) == os.path.normpath(COLORS_XML_PATH):
                continue
                
            try:
                with open(file_path, 'r', encoding='utf-8', errors='ignore') as f:
                    lines = f.readlines()
                for line_idx, line in enumerate(lines, 1):
                    for name in color_names:
                        xml_pat, kt_pat, color_pat = patterns[name]
                        if xml_pat.search(line) or kt_pat.search(line) or color_pat.search(line):
                            rel_path = os.path.relpath(file_path, WORKSPACE).replace('\\', '/')
                            usages[name].append((rel_path, line_idx, line.strip()))
            except Exception as e:
                pass
                
    return usages

def search_hardcoded_colors():
    # Find raw hex colors in all files (except colors.xml and ignored files)
    # Hex regex: #[0-9a-fA-F]{3,8}
    hex_pattern = re.compile(r'#([0-9a-fA-F]{3,4}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})\b')
    hardcoded = []
    
    for root_dir, dirs, files in os.walk(WORKSPACE):
        dirs[:] = [d for d in dirs if not is_ignored(os.path.join(root_dir, d))]
        
        for file in files:
            file_path = os.path.join(root_dir, file)
            if is_ignored(file_path):
                continue
                
            if not (file.endswith('.kt') or file.endswith('.java') or file.endswith('.xml') or file.endswith('.gradle') or file.endswith('.kts')):
                continue
                
            # Skip colors.xml where definition is expected
            if os.path.normpath(file_path) == os.path.normpath(COLORS_XML_PATH):
                continue
                
            try:
                with open(file_path, 'r', encoding='utf-8', errors='ignore') as f:
                    lines = f.readlines()
                for line_idx, line in enumerate(lines, 1):
                    # Check for hex colors
                    matches = hex_pattern.findall(line)
                    if matches:
                        # Make sure it isn't an XML comment or unrelated pattern (like Git hashes, although Git hashes don't typically start with # in source files except comments)
                        # Let's filter out if it looks like a hex color (prefix #)
                        actual_matches = []
                        # The regex findall returns the group (e.g. standard hex digits) but we want the full match with '#'
                        for match in re.finditer(r'#[0-9a-fA-F]{3,8}\b', line):
                            val = match.group(0)
                            # Let's verify it is indeed a valid hex code format: #RGB, #RGBA, #RRGGBB, #AARRGGBB
                            clean_val = val.lstrip('#')
                            if len(clean_val) in (3, 4, 6, 8):
                                actual_matches.append(val)
                        
                        if actual_matches:
                            rel_path = os.path.relpath(file_path, WORKSPACE).replace('\\', '/')
                            hardcoded.append((rel_path, line_idx, line.strip(), actual_matches))
            except Exception as e:
                pass
                
    return hardcoded

def main():
    colors = get_color_definitions()
    print(f"Total defined colors found: {len(colors)}")
    
    duplicates = find_duplicates(colors)
    print(f"\n--- DUPLICATE COLORS DEFINED IN COLORS.XML ---")
    if not duplicates:
        print("No duplicate colors found.")
    else:
        # Flatten duplicate names to search for
        all_dup_names = []
        for norm_val, name_raws in duplicates.items():
            names = [nr[0] for nr in name_raws]
            all_dup_names.extend(names)
            names_str = ", ".join(f"{name} ({raw})" for name, raw in name_raws)
            print(f"Normalized Value: #{norm_val} -> {names_str}")
        
        usages = search_usages(all_dup_names)
        print(f"\n--- USAGES OF DUPLICATE COLOR RESOURCE IDS ---")
        for name in all_dup_names:
            ref_list = usages[name]
            print(f"\nColor Resource: @color/{name} (Used {len(ref_list)} times):")
            if not ref_list:
                print("  Not referenced anywhere in source files.")
            else:
                for rel_path, line_idx, line in ref_list:
                    print(f"  - {rel_path}:{line_idx} -> {line}")
                    
    hardcoded = search_hardcoded_colors()
    print(f"\n--- HARDCODED HEX COLOR CODES IN THE CODEBASE ---")
    if not hardcoded:
        print("No hardcoded hex colors found.")
    else:
        # Group by file to make it cleaner
        by_file = {}
        for rel_path, line_idx, line, matches in hardcoded:
            by_file.setdefault(rel_path, []).append((line_idx, line, matches))
        
        print(f"Found hardcoded colors in {len(by_file)} files:")
        for rel_path, items in sorted(by_file.items()):
            print(f"\nFile: {rel_path} ({len(items)} instances):")
            for line_idx, line, matches in items:
                print(f"  - Line {line_idx} [{', '.join(matches)}] -> {line}")

if __name__ == "__main__":
    main()
