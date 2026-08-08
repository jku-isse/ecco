# Glean

Glean is a lightweight command-line tool for indexing and searching local files.

## Installation

- Download the latest release for your platform.
- Run the installer and follow the prompts.

```bash
curl -sSL https://glean.example.com/install.sh | sh
```

### Docker

Run Glean without installing anything locally:

```bash
docker run --rm -v $(pwd):/data glean/glean:latest search "term"
```
