import os
import re

WORKSPACE = r"c:\Users\Lenovo\AndroidStudioProjects\CashBookBusiness"
COLORS_XML_PATH = os.path.join(WORKSPACE, "app", "src", "main", "res", "values", "colors.xml")
IGNORE_DIRS = {".git", ".gradle", ".idea", "build", "app/build"}

# Replacement map: { retired_color: target_color }
REPLACEMENTS = {
    "text_color": "text_secondary",
    "stitch_primary": "auth_glow_2",
    "stitch_primary_container": "auth_glow_1",
    "stitch_background": "auth_bg",
    "stitch_surface": "auth_bg",
    "stitch_surface_container_highest": "auth_surface_high",
    "stitch_on_surface_variant": "auth_text_light",
    "glass_white_10": "glass_border"
}

def is_ignored(path):
    rel_path = os.path.relpath(path, WORKSPACE).replace('\\', '/')
    for d in IGNORE_DIRS:
        if rel_path.startswith(d + '/') or rel_path == d:
            return True
    return False

def update_colors_xml():
    if not os.path.exists(COLORS_XML_PATH):
        print(f"Error: {COLORS_XML_PATH} not found.")
        return
        
    with open(COLORS_XML_PATH, 'r', encoding='utf-8') as f:
        content = f.read()
        
    original_len = len(content)
    
    # Remove retired color definitions
    for retired in REPLACEMENTS.keys():
        # Regex to match color XML tags: <color name="retired">value</color>
        # Handles any surrounding spaces or comments on that line
        pattern = rf'^\s*<color name="{re.escape(retired)}">.*?</color>\s*\n'
        content = re.sub(pattern, '', content, flags=re.MULTILINE)
        
    if len(content) < original_len:
        with open(COLORS_XML_PATH, 'w', encoding='utf-8') as f:
            f.write(content)
        print("Updated colors.xml: Removed retired color definitions.")
    else:
        print("colors.xml: No retired color definitions found to remove.")

def refactor_references():
    modified_files = 0
    total_replacements = 0
    
    for root_dir, dirs, files in os.walk(WORKSPACE):
        dirs[:] = [d for d in dirs if not is_ignored(os.path.join(root_dir, d))]
        
        for file in files:
            file_path = os.path.join(root_dir, file)
            if is_ignored(file_path):
                continue
                
            if not (file.endswith('.kt') or file.endswith('.java') or file.endswith('.xml') or file.endswith('.gradle') or file.endswith('.kts')):
                continue
                
            # Skip colors.xml since we updated it separately and don't want to replace text inside it
            if os.path.normpath(file_path) == os.path.normpath(COLORS_XML_PATH):
                continue
                
            try:
                with open(file_path, 'r', encoding='utf-8', errors='ignore') as f:
                    content = f.read()
                    
                file_replaced = False
                file_repl_count = 0
                new_content = content
                
                # Perform replacements for each retired color
                for retired, target in REPLACEMENTS.items():
                    # 1. XML reference replacement: @color/retired_name -> @color/target_name
                    xml_pattern = rf'@color/{re.escape(retired)}\b'
                    new_content, count1 = re.subn(xml_pattern, f'@color/{target}', new_content)
                    
                    # 2. Kotlin/Java class reference: R.color.retired_name -> R.color.target_name
                    kt_pattern = rf'\bR\.color\.{re.escape(retired)}\b'
                    new_content, count2 = re.subn(kt_pattern, f'R.color.{target}', new_content)
                    
                    # 3. Raw resource name in strings: color/retired_name -> color/target_name
                    str_pattern = rf'\bcolor/{re.escape(retired)}\b'
                    new_content, count3 = re.subn(str_pattern, f'color/{target}', new_content)
                    
                    file_repl_count += count1 + count2 + count3
                    
                if file_repl_count > 0:
                    with open(file_path, 'w', encoding='utf-8') as f:
                        f.write(new_content)
                    rel_path = os.path.relpath(file_path, WORKSPACE).replace('\\', '/')
                    print(f"Modified: {rel_path} ({file_repl_count} replacements)")
                    modified_files += 1
                    total_replacements += file_repl_count
            except Exception as e:
                print(f"Error processing {file_path}: {e}")
                
    print(f"\nRefactoring completed successfully!")
    print(f"Total files modified: {modified_files}")
    print(f"Total occurrences replaced: {total_replacements}")

def main():
    print("Starting duplicate color resource refactoring...")
    update_colors_xml()
    refactor_references()

if __name__ == "__main__":
    main()
