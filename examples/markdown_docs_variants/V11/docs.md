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

## Troubleshooting

If indexing fails, you may see:

```
error: could not open index file: permission denied
```

> **Tip:** run `glean reindex --force` to rebuild the index from scratch.
