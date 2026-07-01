# Helpbot - Testing Document Ingestion

## Folder Structure

```
helpbot-mcp-server/
  localstack/
    documents/
      public/         ← public docs (searchable by everyone)
        INFO.pdf
      internal/       ← internal docs (searchable only via search_admin)
        (drop files here)
    init-s3-bucket.sh ← uploads documents/ into S3 on Docker start
```

## S3 Bucket Layout

On Docker Compose startup, files are uploaded to:

```
s3://helpbot-documents/
  public/INFO.pdf
  internal/<your-files>
```

## Step-by-Step Testing

### 1. Start infrastructure + MCP server

```bash
cd helpbot-mcp-server
./gradlew bootRun
```

This auto-starts Docker Compose (LocalStack, Ollama, pgvector) and uploads
seed documents from `localstack/documents/` into S3.

### 2. Verify documents are in S3

```bash
# Find the LocalStack port
docker compose -f ../compose.yaml ps localstack

# List bucket contents (replace PORT with actual mapped port)
aws --endpoint-url=http://localhost:PORT s3 ls s3://helpbot-documents/ --recursive
```

### 3. Trigger ingestion of all documents

```bash
curl -X POST http://localhost:8080/api/ingest/all
```

This scans both `internal/` and `public/` prefixes in S3, downloads each
file, chunks it with Tika, and stores embeddings in pgvector with the
appropriate `internal` metadata flag.

### 4. Test search via MCP Inspector

1. Open MCP Inspector
2. Connect to: `http://localhost:8080/mcp` (Streamable HTTP)
3. Call the `search` tool — returns only **public** documents
4. Call the `search_admin` tool — returns **all** documents (public + internal)

### 5. Adding new documents

Drop files into the appropriate folder:

| Visibility | Location |
|---|---|
| Public | `localstack/documents/public/` |
| Internal | `localstack/documents/internal/` |

Then either:
- **Restart** Docker Compose (files get re-uploaded to S3 automatically)
- **Or** upload manually via AWS CLI and call the ingest endpoint

```bash
# Manual upload example
aws --endpoint-url=http://localhost:PORT s3 cp my-doc.pdf s3://helpbot-documents/public/my-doc.pdf

# Then trigger ingestion
curl -X POST http://localhost:8080/api/ingest/all
```

