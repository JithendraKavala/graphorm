#!/bin/bash

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" >/dev/null 2>&1 && pwd)"

echo "=== Stopping GraphORM University Showcase Services ==="

if command -v systemctl >/dev/null 2>&1 && systemctl --user status >/dev/null 2>&1; then
  systemctl --user stop graphorm-frontend 2>/dev/null || true
  systemctl --user stop graphorm-backend 2>/dev/null || true
fi

if [ -f "$DIR/frontend.pid" ]; then
  PID=$(cat "$DIR/frontend.pid")
  echo "Stopping frontend (PID: $PID)..."
  kill "$PID" 2>/dev/null || true
  rm -f "$DIR/frontend.pid"
fi

if [ -f "$DIR/backend.pid" ]; then
  PID=$(cat "$DIR/backend.pid")
  echo "Stopping backend (PID: $PID)..."
  kill "$PID" 2>/dev/null || true
  rm -f "$DIR/backend.pid"
fi

# Fallback cleanup for ports 5173 and 8080 if lingering
fuser -k 5173/tcp 2>/dev/null || true
fuser -k 8080/tcp 2>/dev/null || true

echo "All services stopped."
