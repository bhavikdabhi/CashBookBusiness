import subprocess

def main():
    try:
        # Get diff of the last commit
        diff_out = subprocess.check_output(["git", "show", "HEAD"], stderr=subprocess.STDOUT).decode("utf-8")
        with open("commit_diff.txt", "w", encoding="utf-8") as f:
            f.write(diff_out)
        print("Diff written successfully to commit_diff.txt!")
    except Exception as e:
        print(f"Error: {e}")

if __name__ == "__main__":
    main()
