import subprocess
import sys

def main():
    try:
        # Check current unstaged changes
        status_out = subprocess.check_output(["git", "status"], stderr=subprocess.STDOUT).decode("utf-8")
        
        # Check last commit
        commit_out = subprocess.check_output(["git", "log", "-1", "--stat"], stderr=subprocess.STDOUT).decode("utf-8")
        
        # Write to file
        with open("git_changes.txt", "w", encoding="utf-8") as f:
            f.write("=== GIT STATUS (Uncommitted Changes) ===\n")
            f.write(status_out)
            f.write("\n=== LAST COMMIT DETAILS ===\n")
            f.write(commit_out)
            
        print("Git info written to git_changes.txt successfully!")
    except Exception as e:
        with open("git_changes.txt", "w", encoding="utf-8") as f:
            f.write(f"Error executing git command: {str(e)}")
        print(f"Error: {e}")

if __name__ == "__main__":
    main()
