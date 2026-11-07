#!/bin/bash
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" >/dev/null 2>&1 && pwd)"

echo "=== Starting GraphORM University Full-Stack Showcase ==="

# 1. Start Backend (Spring Boot 3 + PostgreSQL)
if curl -s http://127.0.0.1:8080/api/university/health | grep -q "UP"; then
  echo "Backend is already running on port 8080."
else
  echo "[1/2] Launching GraphORM Backend on port 8080..."
  if command -v systemd-run >/dev/null 2>&1 && systemctl --user status >/dev/null 2>&1; then
    systemctl --user stop graphorm-backend 2>/dev/null || true
    systemd-run --user --unit=graphorm-backend java -Dserver.port=8080 -jar "$DIR/backend/target/graphorm-example-university-dashboard-backend-0.1.0-SNAPSHOT.jar"
  else
    nohup java -Dserver.port=8080 -jar "$DIR/backend/target/graphorm-example-university-dashboard-backend-0.1.0-SNAPSHOT.jar" </dev/null > "$DIR/backend.log" 2>&1 &
    BACKEND_PID=$!
    disown $BACKEND_PID 2>/dev/null || true
    echo $BACKEND_PID > "$DIR/backend.pid"
  fi

  echo "Waiting for backend health endpoint..."
  for i in {1..30}; do
    if curl -s http://127.0.0.1:8080/api/university/health | grep -q "UP"; then
      echo "Backend is live at http://127.0.0.1:8080"
      break
    fi
    sleep 1
  done
fi

# 2. Start Frontend (Vite + React)
if curl -s http://127.0.0.1:5173 | grep -q "GraphORM"; then
  echo "Frontend is already running on port 5173."
else
  echo "[2/2] Launching React Dashboard Frontend on port 5173..."
  if command -v systemd-run >/dev/null 2>&1 && systemctl --user status >/dev/null 2>&1; then
    systemctl --user stop graphorm-frontend 2>/dev/null || true
    systemd-run --user --unit=graphorm-frontend --working-directory="$DIR/frontend" "$DIR/frontend/node_modules/.bin/vite" preview --port 5173 --host
  else
    cd "$DIR/frontend"
    nohup "$DIR/frontend/node_modules/.bin/vite" preview --port 5173 --host </dev/null > "$DIR/frontend.log" 2>&1 &
    FRONTEND_PID=$!
    disown $FRONTEND_PID 2>/dev/null || true
    echo $FRONTEND_PID > "$DIR/frontend.pid"
  fi

  echo "Waiting for frontend to respond..."
  for i in {1..15}; do
    if curl -s http://127.0.0.1:5173 | grep -q "GraphORM"; then
      echo "Frontend is live at http://127.0.0.1:5173"
      break
    fi
    sleep 1
  done
fi

echo ""
echo "=========================================================="
echo " GraphORM University Dashboard is Running!"
echo "   - Frontend UI:       http://localhost:5173"
echo "   - Backend REST API:  http://localhost:8080/api/university"
echo "   - GraphQL Endpoint:  http://localhost:8080/graphql"
echo "   - GraphiQL Studio:   http://localhost:8080/graphiql"
echo "   - Backend Log:       journalctl --user -u graphorm-backend -f"
echo "   - Frontend Log:      journalctl --user -u graphorm-frontend -f"
echo "=========================================================="
