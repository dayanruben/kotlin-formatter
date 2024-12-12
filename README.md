# Kotlin Formatter

**A command-line tool for formatting Kotlin source code files**, implemented as a wrapper around [ktfmt](https://github.com/facebook/ktfmt/tree/main).

It can be used to automate code formatting, ensuring a clean and consistent codebase, while integrating seamlessly into development workflows.

The tool can:

- **Format files and directories**: Apply consistent formatting to files, directories, or standard input.
- **Integrate with Git workflows**:
  - **Pre-commit**: Format staged files before committing. 
  - **Pre-push**: Check committed files before pushing.


## Usage

```bash
kotlin-format [OPTIONS] [FILES...]
```
### Options

| Option                  | Description                                                                                |
|-------------------------|-------------------------------------------------------------------------------------------|
| `--set-exit-if-changed` | Exit with a non-zero code if any files needed changes.                                    |
| `--dry-run`             | Display the changes that would be made without writing them to disk.                     |
| `--pre-commit`          | Format staged files as part of the pre-commit process. *Mutually exclusive with `--pre-push`.* |
| `--pre-push`            | Check committed files as part of the pre-push process. *Mutually exclusive with `--pre-commit`.* |
| `--push-commit=<text>`  | The SHA of the commit to use for pre-push. Defaults to `HEAD`.                            |
| `--print-stats`         | Emit performance-related statistics to help diagnose performance issues.                 |
| `-h, --help`            | Show help message and exit.                                                          |

### Arguments

| Argument      | Description                                |
|---------------|--------------------------------------------|
| `<files>`     | Files or directories to format. Use `-` for standard input. |

