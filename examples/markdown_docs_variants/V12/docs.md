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

## API Reference

| Method | Path | Description |
|--------|------|--------------|
| GET | /search?q=... | Full-text search across the index. |
