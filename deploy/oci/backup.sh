#!/bin/sh
# QueueFlow - PostgreSQL backup of the OCI deployment (docs/deployment-oci.md).
#
#   sh deploy/oci/backup.sh
#
# Writes a pg_dump in custom format (compressed, restorable with pg_restore)
# to backups/ in the repository root, which is git-ignored, and deletes dumps
# older than KEEP_DAYS. Run from anywhere; the stack must be running. The
# database credentials are read inside the container, never printed.

set -eu

cd "$(dirname "$0")/../.."

BACKUP_DIR="${BACKUP_DIR:-backups}"
KEEP_DAYS="${KEEP_DAYS:-14}"
COMPOSE="docker compose -f compose.prod.yaml -f compose.oci.yaml"

mkdir -p "$BACKUP_DIR"
chmod 700 "$BACKUP_DIR"

target="$BACKUP_DIR/queueflow-$(date -u +%Y%m%dT%H%M%SZ).dump"

# Written under a temporary name first, so an interrupted dump never looks
# like a complete one.
$COMPOSE exec -T postgres sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" --format=custom' > "$target.partial"
mv "$target.partial" "$target"
chmod 600 "$target"

find "$BACKUP_DIR" -name 'queueflow-*.dump' -type f -mtime +"$KEEP_DAYS" -delete

echo "Backup written: $target ($(du -h "$target" | cut -f1))"
