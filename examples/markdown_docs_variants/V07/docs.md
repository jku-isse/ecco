# Glean

Glean is a lightweight command-line tool for indexing and searching local files.

## Installation

- Download the latest release for your platform.
- Run the installer and follow the prompts.

```bash
curl -sSL https://glean.example.com/install.sh | sh
```

### Windows

Install via PowerShell:

```powershell
iwr -useb https://glean.example.com/install.ps1 | iex
```

### Docker

Run Glean without installing anything locally:

```bash
docker run --rm -v $(pwd):/data glean/glean:latest search "term"
```

## API Reference

| Method | Path | Description |
|--------|------|--------------|
| GET | /search?q=... | Full-text search across the index. |
| POST | /export | Export the current index as JSON. |

## FAQ

### Does Glean send my data anywhere?

No. Glean indexes and searches entirely on your local machine.

### Can I exclude files from the index?

Yes, add patterns to a `.gleanignore` file in your project root.
