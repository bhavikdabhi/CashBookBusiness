import os
import re
import xml.etree.ElementTree as ET

WORKSPACE = r"c:\Users\Lenovo\AndroidStudioProjects\CashBookBusiness"
COLORS_XML_PATH = os.path.join(WORKSPACE, "app", "src", "main", "res", "values", "colors.xml")
IGNORE_DIRS = {".git", ".gradle", ".idea", "build", "app/build"}

# List of color resources that should always be kept even if they have 0 usages in code (e.g., standard colors, system defaults)
KEEP_ALWAYS = {"black", "white", "primary_color", "primary_hover", "bg_color", "text_secondary", "success", "danger", "warning"}

def is_ignored(path):
    rel_path = os.path.relpath(path, WORKSPACE).replace('\\', '/')
    for d in IGNORE_DIRS:
        if rel_path.startswith(d + '/') or rel_path == d:
            return True
    return False

def get_defined_color_names():
    if not os.path.exists(COLORS_XML_PATH):
        print(f"Error: {COLORS_XML_PATH} not found.")
        return []
    try:
        tree = ET.parse(COLORS_XML_PATH)
        root = tree.getroot()
        names = []
        for elem in root.findall('color'):
            name = elem.get('name')
            if name:
                names.append(name)
        return names
    except Exception as e:
        print(f"Error parsing colors.xml: {e}")
        return []

def search_usages(color_names):
    # Map to track if any usage is found (True/False)
    usages = {name: False for name in color_names}
    
    # Compile regex patterns for each color name to detect references
    patterns = {}
    for name in color_names:
        patterns[name] = (
            re.compile(rf'@color/{re.escape(name)}\b'),
            re.compile(rf'\bR\.color\.{re.escape(name)}\b'),
            re.compile(rf'\bcolor/{re.escape(name)}\b')
        )
        
    for root_dir, dirs, files in os.walk(WORKSPACE):
        dirs[:] = [d for d in dirs if not is_ignored(os.path.join(root_dir, d))]
        
        for file in files:
            file_path = os.path.join(root_dir, file)
            if is_ignored(file_path):
                continue
                
            if not (file.endswith('.kt') or file.endswith('.java') or file.endswith('.xml') or file.endswith('.gradle') or file.endswith('.kts')):
                continue
                
            # Skip colors.xml itself to avoid self-reference matching
            if os.path.normpath(file_path) == os.path.normpath(COLORS_XML_PATH):
                continue
                
            try:
                with open(file_path, 'r', encoding='utf-8', errors='ignore') as f:
                    content = f.read()
                
                # Check for each unused color
                for name in color_names:
                    if usages[name]:
                        continue # Already found a usage, no need to search further
                        
                    xml_pat, kt_pat, color_pat = patterns[name]
                    if xml_pat.search(content) or kt_pat.search(content) or color_pat.search(content):
                        usages[name] = True
            except Exception:
                pass
                
    return usages

def remove_unused_from_xml(unused_names):
    if not unused_names:
        print("No unused colors to remove from colors.xml.")
        return
        
    with open(COLORS_XML_PATH, 'r', encoding='utf-8') as f:
        content = f.read()
        
    original_len = len(content)
    
    for name in unused_names:
        # Regex to match color XML tags: <color name="name">value</color>
        # Handles any surrounding spaces or comments on that line
        pattern = rf'^\s*<color name="{re.escape(name)}">.*?</color>\s*\n'
        content = re.sub(pattern, '', content, flags=re.MULTILINE)
        
    if len(content) < original_len:
        with open(COLORS_XML_PATH, 'w', encoding='utf-8') as f:
            f.write(content)
        print(f"Updated colors.xml: Removed {len(unused_names)} unused color definitions.")
        for name in sorted(unused_names):
            print(f"  - Removed unused color: '{name}'")
    else:
        print("colors.xml: No unused color tags were successfully removed.")

def main():
    print("Finding defined color names in colors.xml...")
    defined_names = get_defined_color_names()
    print(f"Total defined colors: {len(defined_names)}")
    
    # Filter out colors we always want to keep (e.g. system defaults, even if currently unused)
    names_to_check = [name for name in defined_names if name not in KEEP_ALWAYS]
    
    print(f"Scanning codebase for usages of {len(names_to_check)} colors...")
    usages = search_usages(names_to_check)
    
    unused_colors = []
    for name in names_to_check:
        if not usages[name]:
            unused_colors.append(name)
            
    print(f"\n--- UNUSED COLORS IDENTIFIED ({len(unused_colors)}) ---")
    if not unused_colors:
        print("All analyzed colors are currently referenced in the codebase.")
    else:
        for name in sorted(unused_colors):
            print(f"  - {name} (0 references)")
            
        # Execute removal
        print("\nRemoving unused colors from colors.xml...")
        remove_unused_from_xml(unused_colors)

if __name__ == "__main__":
    main()
