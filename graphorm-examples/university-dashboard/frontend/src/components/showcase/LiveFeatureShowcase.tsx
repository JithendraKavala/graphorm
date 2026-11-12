import React, { useState, useEffect } from 'react';
import {
  Sparkles,
  Zap,
  Lock,
  Layers,
  Database,
  Radio,
  Building,
  RefreshCw,
  CheckCircle2,
  AlertTriangle,
  Play,
  ArrowRight,
  ArrowLeft,
  Clock,
  Terminal as TerminalIcon,
  Copy,
  Check,
} from 'lucide-react';
import { api } from '../../services/api';
import { Connection, Student, AuditLog, StudentDashboard, AcademicRole, ROLE_PROFILES } from '../../types/graphorm';

export const LiveFeatureShowcase: React.FC = () => {
  const [activeSection, setActiveSection] = useState<
    'relay' | 'optimistic' | 'upsert' | 'batch' | 'realtime' | 'tenant' | 'cache' | 'rbac' | 'dialect'
  >('relay');

  // 1. Relay Pagination State
  const [pageSize, setPageSize] = useState<number>(3);
  const [cursorHistory, setCursorHistory] = useState<string[]>([]);
  const [currentCursor, setCurrentCursor] = useState<string | undefined>(undefined);
  const [connectionData, setConnectionData] = useState<Connection<Student> | null>(null);
  const [loadingRelay, setLoadingRelay] = useState<boolean>(false);

  // 2. Optimistic Locking State
  const [selectedStudent, setSelectedStudent] = useState<Student | null>(null);
  const [studentVersionInput, setStudentVersionInput] = useState<number>(1);
  const [optimisticStatus, setOptimisticStatus] = useState<{
    success?: boolean;
    message?: string;
    student?: Student;
  } | null>(null);
  const [loadingOptimistic, setLoadingOptimistic] = useState<boolean>(false);

  // 3. Upsert State
  const [upsertName, setUpsertName] = useState<string>('Donald Knuth');
  const [upsertEmail, setUpsertEmail] = useState<string>('knuth@dashboard.edu');
  const [upsertGpa, setUpsertGpa] = useState<number>(3.99);
  const [upsertResult, setUpsertResult] = useState<Student | null>(null);
  const [upsertCount, setUpsertCount] = useState<number>(0);
  const [loadingUpsert, setLoadingUpsert] = useState<boolean>(false);

  // 4. Batch Operations State
  const [batchResult, setBatchResult] = useState<any[] | null>(null);
  const [batchDuration, setBatchDuration] = useState<number | null>(null);
  const [loadingBatch, setLoadingBatch] = useState<boolean>(false);

  // 5. Real-Time Stream State
  const [liveEvents, setLiveEvents] = useState<any[]>([]);
  const [mockEventType, setMockEventType] = useState<'CREATE' | 'UPDATE' | 'DELETE'>('UPDATE');
  const [mockEventModel, setMockEventModel] = useState<string>('Student');

  // 6. Multi-Tenancy State
  const [selectedTenant, setSelectedTenant] = useState<string>('tenant-main');
  const [tenantLogs, setTenantLogs] = useState<AuditLog[]>([]);
  const [newLogAction, setNewLogAction] = useState<string>('CURRICULUM_AUDIT');
  const [loadingTenant, setLoadingTenant] = useState<boolean>(false);

  // 7. Caching State
  const [cachedDashboards, setCachedDashboards] = useState<StudentDashboard[] | null>(null);
  const [cacheDuration, setCacheDuration] = useState<number | null>(null);
  const [isCacheWarm, setIsCacheWarm] = useState<boolean>(false);

  // 8. RBAC / @auth State
  const [rbacRole, setRbacRole] = useState<AcademicRole>('ADMIN');
  const [rbacResponse, setRbacResponse] = useState<any>(null);

  // Copy helper
  const [copiedKey, setCopiedKey] = useState<string | null>(null);
  const handleCopy = (text: string, key: string) => {
    navigator.clipboard.writeText(text);
    setCopiedKey(key);
    setTimeout(() => setCopiedKey(null), 2000);
  };

  // Initial load
  useEffect(() => {
    fetchRelayPage(3, undefined);
    loadInitialStudent();
    fetchTenantLogs('tenant-main');
  }, []);

  const loadInitialStudent = async () => {
    const list = await api.getStudents();
    if (list.length > 0) {
      setSelectedStudent(list[0]);
      setStudentVersionInput(list[0].version ?? 1);
    }
  };

  // 1. Relay Pagination Handlers
  const fetchRelayPage = async (first: number, after?: string) => {
    setLoadingRelay(true);
    try {
      const res = await api.getStudentsConnection(first, after);
      setConnectionData(res);
      setCurrentCursor(after);
    } finally {
      setLoadingRelay(false);
    }
  };

  const handleNextRelayPage = () => {
    if (!connectionData?.pageInfo.endCursor) return;
    setCursorHistory((prev) => [...prev, currentCursor || '']);
    fetchRelayPage(pageSize, connectionData.pageInfo.endCursor);
  };

  const handlePrevRelayPage = () => {
    if (cursorHistory.length === 0) return;
    const prevCursor = cursorHistory[cursorHistory.length - 1];
    setCursorHistory((prev) => prev.slice(0, -1));
    fetchRelayPage(pageSize, prevCursor || undefined);
  };

  // 2. Optimistic Locking Handlers
  const handleOptimisticUpdate = async (simulateConflict: boolean) => {
    if (!selectedStudent) return;
    setLoadingOptimistic(true);
    setOptimisticStatus(null);
    try {
      const versionToUse = simulateConflict ? 9999 : selectedStudent.version ?? 1;
      const updated = await api.updateStudent(selectedStudent.id, {
        name: `${selectedStudent.name.split(' (v')[0]} (v${(selectedStudent.version ?? 1) + 1})`,
        version: versionToUse,
      });
      setSelectedStudent(updated);
      setStudentVersionInput(updated.version ?? 1);
      setOptimisticStatus({
        success: true,
        message: `Student successfully updated! Version incremented from ${selectedStudent.version} to ${updated.version}.`,
        student: updated,
      });
    } catch (err: any) {
      setOptimisticStatus({
        success: false,
        message: err.message || 'Optimistic lock conflict detected!',
      });
    } finally {
      setLoadingOptimistic(false);
    }
  };

  // 3. Upsert Handlers
  const handleUpsert = async () => {
    setLoadingUpsert(true);
    try {
      const res = await api.upsertStudent({
        name: upsertName,
        email: upsertEmail,
        gpa: upsertGpa,
        departmentId: 1,
      });
      setUpsertResult(res);
      setUpsertCount((c) => c + 1);
    } finally {
      setLoadingUpsert(false);
    }
  };

  // 4. Batch Handlers
  const handleBatchInsert = async () => {
    setLoadingBatch(true);
    const start = performance.now();
    try {
      const count = Math.floor(Math.random() * 1000);
      const res = await api.batchCreateCourses([
        { title: `Distributed Consensus ${count}`, code: `CS${count}`, credits: 4, departmentId: 1 },
        { title: `Quantum Cryptanalysis ${count}`, code: `PHYS${count}`, credits: 3, departmentId: 3 },
      ]);
      setBatchResult(res);
      setBatchDuration(Math.round(performance.now() - start));
    } finally {
      setLoadingBatch(false);
    }
  };

  // 5. Multi-Tenant Handlers
  const fetchTenantLogs = async (tenantId: string) => {
    setLoadingTenant(true);
    try {
      const logs = await api.getAuditLogs(tenantId);
      setTenantLogs(logs);
      setSelectedTenant(tenantId);
    } finally {
      setLoadingTenant(false);
    }
  };

  const handleCreateTenantLog = async () => {
    setLoadingTenant(true);
    try {
      const created = await api.createAuditLog({
        tenantId: selectedTenant,
        action: newLogAction,
        details: `Operation audited under ${selectedTenant} partition`,
      });
      setTenantLogs((prev) => [created, ...prev]);
    } finally {
      setLoadingTenant(false);
    }
  };

  // 6. Cache Handlers
  const handleFetchCachedView = async () => {
    const start = performance.now();
    const dashboards = await api.getStudentDashboards();
    const duration = Math.round(performance.now() - start);
    setCachedDashboards(dashboards);
    setCacheDuration(duration);
    setIsCacheWarm(true);
  };

  const handleInvalidateCache = async () => {
    if (!selectedStudent) return;
    // Trigger mutation to cause tag invalidation
    await api.updateStudent(selectedStudent.id, {
      name: selectedStudent.name,
      version: selectedStudent.version,
    });
    setIsCacheWarm(false);
    setCacheDuration(null);
  };

  // 7. RBAC Handlers
  const handleTestRbac = async (role: AcademicRole) => {
    setRbacRole(role);
    api.setRole(role);
    const res = await api.executeAuthViolationTest();
    setRbacResponse(res.response);
  };

  // 8. Live Events simulation
  const handleTriggerLiveEvent = () => {
    const evt = {
      id: `evt-${Date.now()}`,
      action: mockEventType,
      model: mockEventModel,
      entityId: String(Math.floor(Math.random() * 50) + 1),
      tenantId: selectedTenant,
      timestamp: Date.now(),
      payload: { name: 'Live Event Mutation', timestamp: new Date().toISOString() },
    };
    api.triggerShowcaseLiveEvent(evt);
    setLiveEvents((prev) => [evt, ...prev.slice(0, 9)]);
  };

  return (
    <div className="space-y-6">
      {/* Header Banner */}
      <div className="bg-[#161B22] border border-[#30363D] rounded-xl p-6 relative overflow-hidden">
        <div className="absolute top-0 right-0 w-96 h-96 bg-sky-500/5 rounded-full blur-3xl pointer-events-none" />
        <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
          <div className="space-y-2">
            <div className="inline-flex items-center gap-2 px-3 py-1 rounded-md bg-[#21262D] border border-sky-500/30 text-sky-400 font-mono text-xs font-semibold">
              <Sparkles className="w-3.5 h-3.5" />
              <span>Full Reactor Capability Showcase</span>
            </div>
            <h1 className="text-2xl sm:text-3xl font-bold text-[#F0F6FC] tracking-tight">
              GraphORM Interactive Feature Lab
            </h1>
            <p className="text-[#8B949E] text-sm max-w-2xl">
              Live interactive workbench demonstrating Phase P0–P3 features: Relay keyset pagination,
              optimistic concurrency, upsert, batching, real-time CDC, multi-tenancy, and L2 caching.
            </p>
          </div>

          <div className="flex flex-wrap items-center gap-2">
            <span className="text-xs px-2.5 py-1 rounded bg-sky-950/40 text-sky-400 border border-sky-800/40 font-mono">
              P0 Ledger Migrations
            </span>
            <span className="text-xs px-2.5 py-1 rounded bg-emerald-950/40 text-emerald-400 border border-emerald-800/40 font-mono">
              P1 Relay & Upsert
            </span>
            <span className="text-xs px-2.5 py-1 rounded bg-purple-950/40 text-purple-400 border border-purple-800/40 font-mono">
              P2 @version Locking
            </span>
            <span className="text-xs px-2.5 py-1 rounded bg-amber-950/40 text-amber-400 border border-amber-800/40 font-mono">
              P3 Maven Plugin
            </span>
          </div>
        </div>
      </div>

      {/* Feature Selector Tabs */}
      <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-9 gap-2">
        {[
          { id: 'relay', label: 'Relay Cursors', icon: Database, badge: 'P1' },
          { id: 'optimistic', label: '@version Locking', icon: Lock, badge: 'P2' },
          { id: 'upsert', label: 'Idempotent Upsert', icon: Zap, badge: 'P1' },
          { id: 'batch', label: 'Batch Insert', icon: Layers, badge: 'P1' },
          { id: 'dialect', label: 'Multi-Dialect', icon: Database, badge: 'PG/MySQL/SQLite' },
          { id: 'realtime', label: 'CDC & @@notify', icon: Radio, badge: 'Realtime' },
          { id: 'tenant', label: 'Multi-Tenant', icon: Building, badge: 'Isolation' },
          { id: 'cache', label: 'L2 @cached', icon: RefreshCw, badge: 'Eviction' },
          { id: 'rbac', label: 'Security @auth', icon: Lock, badge: 'Field RBAC' },
        ].map((tab) => {
          const Icon = tab.icon;
          const isActive = activeSection === tab.id;
          return (
            <button
              key={tab.id}
              onClick={() => setActiveSection(tab.id as any)}
              className={`p-3 rounded-lg border text-left transition-all flex flex-col justify-between h-20 ${
                isActive
                  ? 'bg-sky-500/10 border-sky-500 text-sky-400 shadow-sm'
                  : 'bg-[#161B22] border-[#30363D] text-[#8B949E] hover:border-[#8B949E] hover:text-[#F0F6FC]'
              }`}
            >
              <div className="flex items-center justify-between w-full">
                <Icon className={`w-4 h-4 ${isActive ? 'text-sky-400' : 'text-[#8B949E]'}`} />
                <span className="text-[10px] font-mono px-1.5 py-0.5 rounded bg-[#21262D] text-[#8B949E]">
                  {tab.badge}
                </span>
              </div>
              <span className="text-xs font-semibold leading-tight">{tab.label}</span>
            </button>
          );
        })}
      </div>

      {/* Main Interactive Stage */}
      <div className="bg-[#161B22] border border-[#30363D] rounded-xl p-6">
        {/* ========================================================================= */}
        {/* TAB 1: RELAY CURSOR PAGINATION */}
        {/* ========================================================================= */}
        {activeSection === 'relay' && (
          <div className="space-y-6">
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 border-b border-[#30363D] pb-4">
              <div>
                <h3 className="text-lg font-bold text-[#F0F6FC] flex items-center gap-2">
                  <Database className="w-5 h-5 text-sky-400" />
                  Relay Keyset Cursor Pagination
                </h3>
                <p className="text-xs text-[#8B949E] mt-1">
                  Exposes GraphQL standard Connection, Edge, and PageInfo types with base64 opaque cursors.
                </p>
              </div>

              <div className="flex items-center gap-3">
                <label className="text-xs text-[#8B949E] font-mono">Page Size:</label>
                <select
                  value={pageSize}
                  onChange={(e) => {
                    const size = Number(e.target.value);
                    setPageSize(size);
                    setCursorHistory([]);
                    fetchRelayPage(size, undefined);
                  }}
                  className="bg-[#0D1117] border border-[#30363D] text-[#F0F6FC] text-xs rounded px-2.5 py-1.5 font-mono focus:outline-none focus:border-sky-500"
                >
                  <option value={2}>2 per page</option>
                  <option value={3}>3 per page</option>
                  <option value={5}>5 per page</option>
                  <option value={8}>8 per page</option>
                </select>

                <div className="flex items-center gap-1.5">
                  <button
                    onClick={handlePrevRelayPage}
                    disabled={cursorHistory.length === 0 || loadingRelay}
                    className="p-1.5 rounded bg-[#21262D] border border-[#30363D] text-[#F0F6FC] hover:bg-[#30363D] disabled:opacity-40 disabled:cursor-not-allowed text-xs flex items-center gap-1"
                  >
                    <ArrowLeft className="w-3.5 h-3.5" />
                    <span>Prev</span>
                  </button>

                  <button
                    onClick={handleNextRelayPage}
                    disabled={!connectionData?.pageInfo.hasNextPage || loadingRelay}
                    className="p-1.5 rounded bg-[#21262D] border border-[#30363D] text-[#F0F6FC] hover:bg-[#30363D] disabled:opacity-40 disabled:cursor-not-allowed text-xs flex items-center gap-1"
                  >
                    <span>Next</span>
                    <ArrowRight className="w-3.5 h-3.5" />
                  </button>
                </div>
              </div>
            </div>

            {/* Results Grid */}
            <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
              {/* Edges List */}
              <div className="space-y-3">
                <div className="flex items-center justify-between text-xs text-[#8B949E]">
                  <span className="font-mono font-semibold uppercase">Edges ({connectionData?.edges.length ?? 0})</span>
                  <span className="font-mono">Total Registry: {connectionData?.totalCount ?? 0}</span>
                </div>

                <div className="space-y-2">
                  {connectionData?.edges.map((edge, idx) => (
                    <div
                      key={edge.cursor}
                      className="bg-[#0D1117] border border-[#30363D] rounded-lg p-3 hover:border-sky-500/50 transition-colors"
                    >
                      <div className="flex items-center justify-between">
                        <div className="font-semibold text-sm text-[#F0F6FC]">{edge.node.name}</div>
                        <span className="text-[10px] font-mono px-2 py-0.5 rounded bg-sky-950/40 text-sky-400 border border-sky-800/40">
                          GPA: {edge.node.gpa ?? 'N/A'}
                        </span>
                      </div>
                      <div className="text-xs text-[#8B949E] mt-0.5">{edge.node.email}</div>
                      <div className="mt-2 pt-2 border-t border-[#21262D] flex items-center justify-between text-[11px] font-mono text-[#8B949E]">
                        <span>Cursor: {edge.cursor}</span>
                        <span className="text-slate-500">ID: {edge.node.id}</span>
                      </div>
                    </div>
                  ))}
                </div>
              </div>

              {/* PageInfo Telemetry Inspector */}
              <div className="bg-[#0D1117] border border-[#30363D] rounded-lg p-4 font-mono text-xs space-y-3">
                <div className="flex items-center justify-between text-[#8B949E] border-b border-[#21262D] pb-2">
                  <span className="flex items-center gap-1.5 text-sky-400 font-semibold">
                    <TerminalIcon className="w-3.5 h-3.5" /> PageInfo Telemetry
                  </span>
                  <span>GraphQL Relay Spec</span>
                </div>

                <div className="space-y-1.5 text-slate-300">
                  <div className="flex justify-between py-1 border-b border-[#21262D]">
                    <span className="text-[#8B949E]">hasNextPage:</span>
                    <span className={connectionData?.pageInfo.hasNextPage ? 'text-emerald-400 font-bold' : 'text-slate-500'}>
                      {String(connectionData?.pageInfo.hasNextPage)}
                    </span>
                  </div>
                  <div className="flex justify-between py-1 border-b border-[#21262D]">
                    <span className="text-[#8B949E]">hasPreviousPage:</span>
                    <span className={connectionData?.pageInfo.hasPreviousPage ? 'text-emerald-400 font-bold' : 'text-slate-500'}>
                      {String(connectionData?.pageInfo.hasPreviousPage)}
                    </span>
                  </div>
                  <div className="flex justify-between py-1 border-b border-[#21262D]">
                    <span className="text-[#8B949E]">startCursor:</span>
                    <span className="text-sky-300 truncate max-w-[200px]">
                      {connectionData?.pageInfo.startCursor || 'null'}
                    </span>
                  </div>
                  <div className="flex justify-between py-1 border-b border-[#21262D]">
                    <span className="text-[#8B949E]">endCursor:</span>
                    <span className="text-sky-300 truncate max-w-[200px]">
                      {connectionData?.pageInfo.endCursor || 'null'}
                    </span>
                  </div>
                </div>

                <div className="pt-2">
                  <div className="text-[11px] text-[#8B949E] mb-1">Generated GraphQL Query:</div>
                  <pre className="bg-[#161B22] p-2.5 rounded text-[11px] text-sky-300 overflow-x-auto">
{`query GetStudentsConnection {
  studentsConnection(first: ${pageSize}${currentCursor ? `, after: "${currentCursor}"` : ''}) {
    edges {
      cursor
      node { id name email gpa version }
    }
    pageInfo { hasNextPage hasPreviousPage startCursor endCursor }
    totalCount
  }
}`}
                  </pre>
                </div>
              </div>
            </div>
          </div>
        )}

        {/* ========================================================================= */}
        {/* TAB 2: OPTIMISTIC LOCKING (@version) */}
        {/* ========================================================================= */}
        {activeSection === 'optimistic' && (
          <div className="space-y-6">
            <div className="border-b border-[#30363D] pb-4">
              <h3 className="text-lg font-bold text-[#F0F6FC] flex items-center gap-2">
                <Lock className="w-5 h-5 text-purple-400" />
                @version Optimistic Concurrency Control
              </h3>
              <p className="text-xs text-[#8B949E] mt-1">
                GraphORM injects <code className="text-purple-300 font-mono">WHERE id = ? AND version = ?</code>.
                When concurrent transactions collide, GraphORM throws an <code className="text-purple-300 font-mono">OptimisticLockException</code>,
                translated automatically to HTTP 409 Conflict.
              </p>
            </div>

            <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
              {/* Interactive Controls */}
              <div className="space-y-4">
                <div className="bg-[#0D1117] border border-[#30363D] rounded-lg p-4 space-y-3">
                  <div className="text-xs font-mono font-semibold text-[#8B949E] uppercase">
                    Target Student Record
                  </div>
                  {selectedStudent ? (
                    <div className="space-y-2">
                      <div className="flex items-center justify-between">
                        <span className="text-sm font-bold text-[#F0F6FC]">{selectedStudent.name}</span>
                        <span className="text-xs font-mono px-2 py-0.5 rounded bg-purple-950/40 text-purple-400 border border-purple-800/40">
                          Current @version: {selectedStudent.version ?? 1}
                        </span>
                      </div>
                      <div className="text-xs text-[#8B949E] font-mono">Email: {selectedStudent.email}</div>
                    </div>
                  ) : (
                    <div className="text-xs text-slate-500">Loading student record...</div>
                  )}
                </div>

                <div className="space-y-3">
                  <div className="text-xs text-[#8B949E]">Test Actions:</div>

                  {/* Valid Update */}
                  <button
                    onClick={() => handleOptimisticUpdate(false)}
                    disabled={loadingOptimistic || !selectedStudent}
                    className="w-full py-2.5 px-4 rounded-lg bg-emerald-600 hover:bg-emerald-500 text-white font-semibold text-xs transition-colors flex items-center justify-center gap-2"
                  >
                    <CheckCircle2 className="w-4 h-4" />
                    <span>Submit Valid Update (Version matches current: {selectedStudent?.version ?? 1})</span>
                  </button>

                  {/* Conflict Update */}
                  <button
                    onClick={() => handleOptimisticUpdate(true)}
                    disabled={loadingOptimistic || !selectedStudent}
                    className="w-full py-2.5 px-4 rounded-lg bg-rose-600/20 hover:bg-rose-600/30 text-rose-300 border border-rose-500/40 font-semibold text-xs transition-colors flex items-center justify-center gap-2"
                  >
                    <AlertTriangle className="w-4 h-4 text-rose-400" />
                    <span>Simulate Stale Concurrent Collision (Supply version 9999 → Expect 409)</span>
                  </button>
                </div>
              </div>

              {/* Status & SQL Output */}
              <div className="space-y-4">
                {optimisticStatus && (
                  <div
                    className={`p-4 rounded-lg border text-xs font-mono space-y-2 ${
                      optimisticStatus.success
                        ? 'bg-emerald-950/20 border-emerald-500/40 text-emerald-300'
                        : 'bg-rose-950/20 border-rose-500/40 text-rose-300'
                    }`}
                  >
                    <div className="flex items-center gap-2 font-bold text-sm">
                      {optimisticStatus.success ? (
                        <>
                          <CheckCircle2 className="w-4 h-4 text-emerald-400" />
                          <span>Update Succeeded (HTTP 200 OK)</span>
                        </>
                      ) : (
                        <>
                          <AlertTriangle className="w-4 h-4 text-rose-400" />
                          <span>Optimistic Lock Conflict Caught (HTTP 409 Conflict)</span>
                        </>
                      )}
                    </div>
                    <p className="text-slate-300">{optimisticStatus.message}</p>
                  </div>
                )}

                <div className="bg-[#0D1117] border border-[#30363D] rounded-lg p-4 font-mono text-xs space-y-2">
                  <div className="text-[#8B949E] text-[11px] font-semibold">Underlying Generated SQL Execution:</div>
                  <pre className="text-purple-300 bg-[#161B22] p-2.5 rounded overflow-x-auto text-[11px]">
{`-- GraphORM generated optimistic update statement
UPDATE "students"
SET "name" = ?, "email" = ?, "gpa" = ?, "version" = ?
WHERE ("id" = ? AND "version" = ?)`}
                  </pre>
                  <p className="text-[11px] text-slate-400">
                    If affected row count is 0, GraphORM immediately aborts with <code className="text-purple-300">OptimisticLockException</code>.
                  </p>
                </div>
              </div>
            </div>
          </div>
        )}

        {/* ========================================================================= */}
        {/* TAB 3: IDEMPOTENT UPSERT */}
        {/* ========================================================================= */}
        {activeSection === 'upsert' && (
          <div className="space-y-6">
            <div className="border-b border-[#30363D] pb-4">
              <h3 className="text-lg font-bold text-[#F0F6FC] flex items-center gap-2">
                <Zap className="w-5 h-5 text-amber-400" />
                Idempotent Upsert Operations
              </h3>
              <p className="text-xs text-[#8B949E] mt-1">
                Leverages GraphORM <code className="text-amber-300 font-mono">UpsertQuery</code> targeting unique index constraints
                (<code className="text-amber-300 font-mono">ON CONFLICT ("email") DO UPDATE SET ...</code>) without duplicate key errors.
              </p>
            </div>

            <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
              {/* Form Controls */}
              <div className="space-y-4">
                <div className="bg-[#0D1117] border border-[#30363D] rounded-lg p-4 space-y-3">
                  <div>
                    <label className="text-xs font-mono text-[#8B949E]">Full Name:</label>
                    <input
                      type="text"
                      value={upsertName}
                      onChange={(e) => setUpsertName(e.target.value)}
                      className="w-full mt-1 bg-[#161B22] border border-[#30363D] text-[#F0F6FC] text-xs rounded p-2 font-mono focus:border-amber-400 focus:outline-none"
                    />
                  </div>

                  <div>
                    <label className="text-xs font-mono text-[#8B949E]">Unique Email (Conflict Target):</label>
                    <input
                      type="email"
                      value={upsertEmail}
                      onChange={(e) => setUpsertEmail(e.target.value)}
                      className="w-full mt-1 bg-[#161B22] border border-[#30363D] text-[#F0F6FC] text-xs rounded p-2 font-mono focus:border-amber-400 focus:outline-none"
                    />
                  </div>

                  <div>
                    <label className="text-xs font-mono text-[#8B949E]">GPA:</label>
                    <input
                      type="number"
                      step="0.01"
                      value={upsertGpa}
                      onChange={(e) => setUpsertGpa(parseFloat(e.target.value))}
                      className="w-full mt-1 bg-[#161B22] border border-[#30363D] text-[#F0F6FC] text-xs rounded p-2 font-mono focus:border-amber-400 focus:outline-none"
                    />
                  </div>
                </div>

                <button
                  onClick={handleUpsert}
                  disabled={loadingUpsert}
                  className="w-full py-2.5 px-4 rounded-lg bg-amber-500 hover:bg-amber-400 text-slate-950 font-bold text-xs transition-colors flex items-center justify-center gap-2"
                >
                  <Play className="w-4 h-4 fill-current" />
                  <span>Execute Idempotent Upsert (Execution #{upsertCount + 1})</span>
                </button>
              </div>

              {/* Output Display */}
              <div className="space-y-4">
                <div className="bg-[#0D1117] border border-[#30363D] rounded-lg p-4 font-mono text-xs space-y-2">
                  <div className="text-amber-400 font-semibold flex items-center justify-between">
                    <span>Generated PostgreSQL Upsert SQL</span>
                    <span className="text-[10px] text-[#8B949E]">Dialect: PostgreSqlDialect</span>
                  </div>
                  <pre className="bg-[#161B22] p-2.5 rounded text-[11px] text-amber-200 overflow-x-auto">
{`INSERT INTO "students" ("name", "email", "gpa", "department_id", "version")
VALUES (?, ?, ?, ?, ?)
ON CONFLICT ("email")
DO UPDATE SET
  "name" = EXCLUDED."name",
  "gpa" = EXCLUDED."gpa",
  "department_id" = EXCLUDED."department_id"
RETURNING *`}
                  </pre>
                </div>

                {upsertResult && (
                  <div className="bg-emerald-950/20 border border-emerald-500/40 rounded-lg p-4 font-mono text-xs space-y-2">
                    <div className="text-emerald-400 font-bold flex items-center gap-2">
                      <CheckCircle2 className="w-4 h-4" />
                      <span>Upsert Succeeded (Record ID: {upsertResult.id})</span>
                    </div>
                    <pre className="bg-[#161B22] p-2.5 rounded text-[11px] text-emerald-300">
                      {JSON.stringify(upsertResult, null, 2)}
                    </pre>
                  </div>
                )}
              </div>
            </div>
          </div>
        )}

        {/* ========================================================================= */}
        {/* TAB 4: BATCH OPERATIONS */}
        {/* ========================================================================= */}
        {activeSection === 'batch' && (
          <div className="space-y-6">
            <div className="border-b border-[#30363D] pb-4">
              <h3 className="text-lg font-bold text-[#F0F6FC] flex items-center gap-2">
                <Layers className="w-5 h-5 text-sky-400" />
                Atomic Multi-Row Batch Operations
              </h3>
              <p className="text-xs text-[#8B949E] mt-1">
                Executes multi-row insertions in a single SQL statement (<code className="text-sky-300 font-mono">VALUES (...), (...)</code>)
                within a single database transaction.
              </p>
            </div>

            <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
              <div className="space-y-4">
                <div className="bg-[#0D1117] border border-[#30363D] rounded-lg p-4 space-y-3">
                  <div className="text-xs font-mono font-semibold text-[#8B949E] uppercase">
                    Batch Payload Specification (2 Records)
                  </div>
                  <pre className="bg-[#161B22] p-2.5 rounded text-[11px] font-mono text-sky-300">
{`[
  { "title": "Distributed Consensus", "code": "CS501", "credits": 4, "departmentId": 1 },
  { "title": "Quantum Cryptanalysis", "code": "PHYS501", "credits": 3, "departmentId": 3 }
]`}
                  </pre>
                </div>

                <button
                  onClick={handleBatchInsert}
                  disabled={loadingBatch}
                  className="w-full py-2.5 px-4 rounded-lg bg-sky-600 hover:bg-sky-500 text-white font-semibold text-xs transition-colors flex items-center justify-center gap-2"
                >
                  <Layers className="w-4 h-4" />
                  <span>Execute Atomic Batch Insert</span>
                </button>
              </div>

              <div className="space-y-4">
                <div className="bg-[#0D1117] border border-[#30363D] rounded-lg p-4 font-mono text-xs space-y-2">
                  <div className="text-sky-400 font-semibold">Generated Batch SQL:</div>
                  <pre className="bg-[#161B22] p-2.5 rounded text-[11px] text-sky-200 overflow-x-auto">
{`INSERT INTO "courses" ("title", "code", "credits", "department_id")
VALUES (?, ?, ?, ?), (?, ?, ?, ?)
RETURNING *`}
                  </pre>
                </div>

                {batchResult && (
                  <div className="bg-emerald-950/20 border border-emerald-500/40 rounded-lg p-4 font-mono text-xs space-y-2">
                    <div className="flex items-center justify-between text-emerald-400 font-bold">
                      <span className="flex items-center gap-1.5">
                        <CheckCircle2 className="w-4 h-4" />
                        Inserted {batchResult.length} Courses
                      </span>
                      <span className="text-[11px]">Latency: {batchDuration} ms</span>
                    </div>
                    <pre className="bg-[#161B22] p-2 rounded text-[11px] text-emerald-300 max-h-40 overflow-y-auto">
                      {JSON.stringify(batchResult, null, 2)}
                    </pre>
                  </div>
                )}
              </div>
            </div>
          </div>
        )}

        {/* ========================================================================= */}
        {/* TAB 5: MULTI-DIALECT SPI (PostgreSQL / MySQL / SQLite) */}
        {/* ========================================================================= */}
        {activeSection === 'dialect' && (
          <div className="space-y-6">
            <div className="border-b border-[#30363D] pb-4">
              <h3 className="text-lg font-bold text-[#F0F6FC] flex items-center gap-2">
                <Database className="w-5 h-5 text-sky-400" />
                Multi-Dialect SPI — PostgreSQL · MySQL 8+ · SQLite
              </h3>
              <p className="text-xs text-[#8B949E] mt-1">
                GraphORM's <code className="text-sky-300 font-mono">QueryExecutionEngine</code> SPI abstracts all SQL
                generation behind dialect-specific implementations. The correct dialect is auto-detected via JDBC
                metadata (<code className="text-sky-300 font-mono">SqlDialect.fromDatabaseProductName</code>), so no
                code changes are needed when switching databases.
              </p>
            </div>

            <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
              {/* PostgreSQL */}
              <div className="bg-[#0D1117] border border-[#30363D] rounded-lg p-4 space-y-3">
                <div className="flex items-center justify-between">
                  <span className="text-sky-400 font-mono font-bold text-sm">PostgreSQL 15+</span>
                  <span className="text-[10px] font-mono px-2 py-0.5 rounded bg-sky-500/10 text-sky-400 border border-sky-500/30">Default</span>
                </div>
                <ul className="text-xs text-[#8B949E] space-y-1.5 font-mono">
                  <li className="flex gap-2"><span className="text-emerald-400">✓</span> <span><code className="text-sky-200">RETURNING</code> clause (zero extra round-trips)</span></li>
                  <li className="flex gap-2"><span className="text-emerald-400">✓</span> <span><code className="text-sky-200">pg_advisory_xact_lock</code> for migrations</span></li>
                  <li className="flex gap-2"><span className="text-emerald-400">✓</span> <span>LISTEN/NOTIFY subscription events</span></li>
                  <li className="flex gap-2"><span className="text-emerald-400">✓</span> <span>Full-text search & JSONB operators</span></li>
                  <li className="flex gap-2"><span className="text-emerald-400">✓</span> <span>ON CONFLICT DO UPDATE (upsert)</span></li>
                </ul>
              </div>

              {/* MySQL */}
              <div className="bg-[#0D1117] border border-[#30363D] rounded-lg p-4 space-y-3">
                <div className="flex items-center justify-between">
                  <span className="text-amber-400 font-mono font-bold text-sm">MySQL 8+</span>
                  <span className="text-[10px] font-mono px-2 py-0.5 rounded bg-amber-500/10 text-amber-400 border border-amber-500/30">MySqlDialect</span>
                </div>
                <ul className="text-xs text-[#8B949E] space-y-1.5 font-mono">
                  <li className="flex gap-2"><span className="text-emerald-400">✓</span> <span>Backtick identifier quoting</span></li>
                  <li className="flex gap-2"><span className="text-emerald-400">✓</span> <span><code className="text-amber-200">LIMIT ? OFFSET ?</code> pagination</span></li>
                  <li className="flex gap-2"><span className="text-emerald-400">✓</span> <span>ON DUPLICATE KEY UPDATE (upsert)</span></li>
                  <li className="flex gap-2"><span className="text-emerald-400">✓</span> <span>Error codes 1062, 1451/1452, 3819/4025</span></li>
                  <li className="flex gap-2"><span className="text-emerald-400">✓</span> <span>MariaDB compatibility</span></li>
                </ul>
              </div>

              {/* SQLite */}
              <div className="bg-[#0D1117] border border-[#30363D] rounded-lg p-4 space-y-3">
                <div className="flex items-center justify-between">
                  <span className="text-purple-400 font-mono font-bold text-sm">SQLite 3</span>
                  <span className="text-[10px] font-mono px-2 py-0.5 rounded bg-purple-500/10 text-purple-400 border border-purple-500/30">SqliteDialect</span>
                </div>
                <ul className="text-xs text-[#8B949E] space-y-1.5 font-mono">
                  <li className="flex gap-2"><span className="text-emerald-400">✓</span> <span>INTEGER PRIMARY KEY AUTOINCREMENT</span></li>
                  <li className="flex gap-2"><span className="text-emerald-400">✓</span> <span>Boolean ↔ integer coercion</span></li>
                  <li className="flex gap-2"><span className="text-emerald-400">✓</span> <span><code className="text-purple-200">LIMIT -1 OFFSET ?</code> pagination</span></li>
                  <li className="flex gap-2"><span className="text-emerald-400">✓</span> <span>In-memory test suite (no Docker)</span></li>
                  <li className="flex gap-2"><span className="text-emerald-400">✓</span> <span>Multi-tenancy isolation tests</span></li>
                </ul>
              </div>
            </div>

            {/* Auto-detection code snippet */}
            <div className="bg-[#0D1117] border border-[#30363D] rounded-lg p-4 font-mono text-xs space-y-2">
              <div className="text-sky-400 font-semibold">Auto-Detection (Spring Boot Auto-Config):</div>
              <pre className="bg-[#161B22] p-2.5 rounded text-[11px] text-emerald-200 overflow-x-auto">{`// No manual configuration required:
@Bean
@ConditionalOnMissingBean
public SqlDialect sqlDialect(DataSource dataSource) {
    // Reads JDBC DatabaseMetaData.getDatabaseProductName()
    return SqlDialect.fromDatabaseProductName(dataSource);
    // → PostgreSqlDialect | MySqlDialect | SqliteDialect
}

// Virtual-thread safe — ReentrantLock replaces synchronized
// for Loom carrier-thread pinning prevention`}</pre>
            </div>

            <div className="p-4 rounded-lg bg-sky-500/5 border border-sky-500/20 text-xs text-sky-300 font-mono">
              <span className="font-semibold text-sky-400">ℹ Note:</span>{' '}
              Real-time subscriptions (@@notify) require PostgreSQL's LISTEN/NOTIFY. MySQL and SQLite fall back to the
              in-memory <code className="text-sky-200">InMemoryEntityEventBroker</code> for local dev testing.
            </div>
          </div>
        )}

        {/* ========================================================================= */}
        {/* TAB 6: REAL-TIME NOTIFICATIONS (@@notify & SSE) */}
        {/* ========================================================================= */}
        {activeSection === 'realtime' && (
          <div className="space-y-6">
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 border-b border-[#30363D] pb-4">
              <div>
                <h3 className="text-lg font-bold text-[#F0F6FC] flex items-center gap-2">
                  <Radio className="w-5 h-5 text-rose-400 animate-pulse" />
                  Change Data Capture (@@notify & SSE)
                </h3>
                <p className="text-xs text-[#8B949E] mt-1">
                  PostgreSQL <code className="text-rose-300 font-mono">LISTEN/NOTIFY</code> events are bridged
                  via <code className="text-rose-300 font-mono">EntityEventBroker</code> directly to Spring SSE.
                </p>
              </div>

              <div className="inline-flex items-center gap-2 px-3 py-1.5 rounded-full bg-emerald-950/40 border border-emerald-500/40 text-emerald-400 text-xs font-mono">
                <span className="w-2 h-2 rounded-full bg-emerald-400 animate-ping" />
                <span>Connected: /api/university/live-events</span>
              </div>
            </div>

            <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
              {/* Trigger simulation */}
              <div className="space-y-4">
                <div className="bg-[#0D1117] border border-[#30363D] rounded-lg p-4 space-y-3">
                  <div className="text-xs font-mono font-semibold text-[#8B949E] uppercase">
                    Dispatch Mutation Event
                  </div>
                  <div className="grid grid-cols-2 gap-3">
                    <div>
                      <label className="text-xs font-mono text-[#8B949E]">Action:</label>
                      <select
                        value={mockEventType}
                        onChange={(e) => setMockEventType(e.target.value as any)}
                        className="w-full mt-1 bg-[#161B22] border border-[#30363D] text-[#F0F6FC] text-xs rounded p-2 font-mono"
                      >
                        <option value="CREATE">CREATE</option>
                        <option value="UPDATE">UPDATE</option>
                        <option value="DELETE">DELETE</option>
                      </select>
                    </div>

                    <div>
                      <label className="text-xs font-mono text-[#8B949E]">Model Target:</label>
                      <select
                        value={mockEventModel}
                        onChange={(e) => setMockEventModel(e.target.value)}
                        className="w-full mt-1 bg-[#161B22] border border-[#30363D] text-[#F0F6FC] text-xs rounded p-2 font-mono"
                      >
                        <option value="Student">Student (@@notify)</option>
                        <option value="Enrollment">Enrollment (@@notify)</option>
                        <option value="Course">Course</option>
                      </select>
                    </div>
                  </div>

                  <button
                    onClick={handleTriggerLiveEvent}
                    className="w-full mt-2 py-2 px-4 rounded-lg bg-rose-600 hover:bg-rose-500 text-white font-semibold text-xs transition-colors flex items-center justify-center gap-2"
                  >
                    <Radio className="w-4 h-4" />
                    <span>Publish Event via EntityEventBroker</span>
                  </button>
                </div>

                <div className="bg-[#0D1117] border border-[#30363D] rounded-lg p-4 font-mono text-xs space-y-1">
                  <div className="text-[#8B949E] font-semibold text-[11px]">Database Level Notification:</div>
                  <pre className="text-rose-300 bg-[#161B22] p-2 rounded text-[11px] overflow-x-auto">
{`NOTIFY graphorm_events, '{"entityName":"Student","eventType":"UPDATE","entityId":1}'`}
                  </pre>
                </div>
              </div>

              {/* Event Stream Log */}
              <div className="space-y-2">
                <div className="text-xs font-mono text-[#8B949E] uppercase">Live Event Stream Log</div>
                <div className="space-y-2 max-h-72 overflow-y-auto">
                  {liveEvents.length === 0 ? (
                    <div className="bg-[#0D1117] border border-[#30363D] rounded-lg p-6 text-center text-xs text-[#8B949E]">
                      Listening for PostgreSQL events... click "Publish Event" to test live ingestion.
                    </div>
                  ) : (
                    liveEvents.map((evt) => (
                      <div
                        key={evt.id}
                        className="bg-[#0D1117] border border-[#30363D] rounded-lg p-3 font-mono text-xs flex items-center justify-between"
                      >
                        <div className="space-y-1">
                          <div className="flex items-center gap-2">
                            <span
                              className={`px-1.5 py-0.5 rounded text-[10px] font-bold ${
                                evt.action === 'CREATE'
                                  ? 'bg-emerald-950 text-emerald-400'
                                  : evt.action === 'UPDATE'
                                  ? 'bg-sky-950 text-sky-400'
                                  : 'bg-rose-950 text-rose-400'
                              }`}
                            >
                              {evt.action}
                            </span>
                            <span className="text-[#F0F6FC] font-semibold">{evt.model}</span>
                            <span className="text-slate-500">ID: #{evt.entityId}</span>
                          </div>
                          <div className="text-[11px] text-slate-400">Tenant: {evt.tenantId}</div>
                        </div>
                        <span className="text-[10px] text-slate-500">
                          {new Date(evt.timestamp).toLocaleTimeString()}
                        </span>
                      </div>
                    ))
                  )}
                </div>
              </div>
            </div>
          </div>
        )}

        {/* ========================================================================= */}
        {/* TAB 6: MULTI-TENANCY ISOLATION */}
        {/* ========================================================================= */}
        {activeSection === 'tenant' && (
          <div className="space-y-6">
            <div className="border-b border-[#30363D] pb-4">
              <h3 className="text-lg font-bold text-[#F0F6FC] flex items-center gap-2">
                <Building className="w-5 h-5 text-indigo-400" />
                Multi-Tenant Partition Isolation (@tenantId)
              </h3>
              <p className="text-xs text-[#8B949E] mt-1">
                Using <code className="text-indigo-300 font-mono">TenantContext.supplyAsTenant(...)</code>,
                GraphORM injects <code className="text-indigo-300 font-mono">WHERE "tenant_id" = ?</code> into all queries and mutations automatically.
              </p>
            </div>

            <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
              {/* Tenant Switcher */}
              <div className="space-y-4">
                <div className="bg-[#0D1117] border border-[#30363D] rounded-lg p-4 space-y-3">
                  <div className="text-xs font-mono font-semibold text-[#8B949E] uppercase">
                    Switch Active Tenant Partition
                  </div>
                  <div className="grid grid-cols-3 gap-2">
                    {['tenant-main', 'tenant-science', 'tenant-engineering'].map((tenant) => (
                      <button
                        key={tenant}
                        onClick={() => fetchTenantLogs(tenant)}
                        className={`p-2.5 rounded-lg border text-xs font-mono transition-all text-center ${
                          selectedTenant === tenant
                            ? 'bg-indigo-500/20 border-indigo-500 text-indigo-300 font-bold'
                            : 'bg-[#161B22] border-[#30363D] text-[#8B949E] hover:text-[#F0F6FC]'
                        }`}
                      >
                        {tenant}
                      </button>
                    ))}
                  </div>
                </div>

                <div className="bg-[#0D1117] border border-[#30363D] rounded-lg p-4 space-y-3">
                  <div className="text-xs font-mono text-[#8B949E]">Record Audit Log in Active Partition:</div>
                  <div className="flex gap-2">
                    <input
                      type="text"
                      value={newLogAction}
                      onChange={(e) => setNewLogAction(e.target.value)}
                      className="flex-1 bg-[#161B22] border border-[#30363D] text-[#F0F6FC] text-xs rounded p-2 font-mono"
                    />
                    <button
                      onClick={handleCreateTenantLog}
                      disabled={loadingTenant}
                      className="px-4 py-2 rounded bg-indigo-600 hover:bg-indigo-500 text-white text-xs font-semibold"
                    >
                      Record
                    </button>
                  </div>
                </div>
              </div>

              {/* Tenant Scoped Logs */}
              <div className="space-y-2">
                <div className="flex items-center justify-between text-xs font-mono text-[#8B949E]">
                  <span>ISOLATED AUDIT LOGS FOR: {selectedTenant}</span>
                  <span>({tenantLogs.length} entries)</span>
                </div>

                <div className="space-y-2 max-h-72 overflow-y-auto">
                  {tenantLogs.length === 0 ? (
                    <div className="bg-[#0D1117] border border-[#30363D] rounded-lg p-6 text-center text-xs text-[#8B949E]">
                      No audit records found in partition '{selectedTenant}'.
                    </div>
                  ) : (
                    tenantLogs.map((log) => (
                      <div
                        key={log.id}
                        className="bg-[#0D1117] border border-[#30363D] rounded-lg p-3 font-mono text-xs space-y-1"
                      >
                        <div className="flex items-center justify-between">
                          <span className="text-indigo-400 font-bold">{log.action}</span>
                          <span className="text-[10px] text-slate-500">{log.performedAt}</span>
                        </div>
                        <div className="text-slate-300 text-[11px]">{log.details}</div>
                        <div className="text-[10px] text-slate-500 pt-1 border-t border-[#21262D]">
                          Partition: <span className="text-indigo-300">{log.tenantId}</span>
                        </div>
                      </div>
                    ))
                  )}
                </div>
              </div>
            </div>
          </div>
        )}

        {/* ========================================================================= */}
        {/* TAB 7: L2 @cached VIEW MODELS & TAG EVICTION */}
        {/* ========================================================================= */}
        {activeSection === 'cache' && (
          <div className="space-y-6">
            <div className="border-b border-[#30363D] pb-4">
              <h3 className="text-lg font-bold text-[#F0F6FC] flex items-center gap-2">
                <RefreshCw className="w-5 h-5 text-emerald-400" />
                L2 @cached View Models & Mutation Eviction
              </h3>
              <p className="text-xs text-[#8B949E] mt-1">
                Schema-defined view models like <code className="text-emerald-300 font-mono">StudentDashboard</code> are
                cached with tag descriptors (<code className="text-emerald-300 font-mono">tags: ["Student"]</code>).
                When a student mutation happens, cache keys matching the tag are automatically invalidated.
              </p>
            </div>

            <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
              <div className="space-y-4">
                <div className="bg-[#0D1117] border border-[#30363D] rounded-lg p-4 space-y-3 font-mono text-xs">
                  <div className="flex items-center justify-between">
                    <span className="text-[#8B949E]">Cache Status:</span>
                    <span className={`font-bold ${isCacheWarm ? 'text-emerald-400' : 'text-amber-400'}`}>
                      {isCacheWarm ? 'WARM (Active in Caffeine)' : 'COLD / INVALIDATED'}
                    </span>
                  </div>
                  {cacheDuration != null && (
                    <div className="flex items-center justify-between">
                      <span className="text-[#8B949E]">Last Query Latency:</span>
                      <span className="text-emerald-300 font-bold">{cacheDuration} ms</span>
                    </div>
                  )}
                </div>

                <div className="flex gap-3">
                  <button
                    onClick={handleFetchCachedView}
                    className="flex-1 py-2.5 px-4 rounded-lg bg-emerald-600 hover:bg-emerald-500 text-white font-semibold text-xs flex items-center justify-center gap-2"
                  >
                    <Play className="w-4 h-4 fill-current" />
                    <span>Query Cached StudentDashboard</span>
                  </button>

                  <button
                    onClick={handleInvalidateCache}
                    className="flex-1 py-2.5 px-4 rounded-lg bg-rose-600/20 hover:bg-rose-600/30 text-rose-300 border border-rose-500/40 font-semibold text-xs flex items-center justify-center gap-2"
                  >
                    <RefreshCw className="w-4 h-4" />
                    <span>Fire Mutation & Evict Tag 'Student'</span>
                  </button>
                </div>
              </div>

              {/* Schema Definition */}
              <div className="bg-[#0D1117] border border-[#30363D] rounded-lg p-4 font-mono text-xs space-y-2">
                <div className="text-emerald-400 font-semibold">Schema View Definition:</div>
                <pre className="bg-[#161B22] p-2.5 rounded text-[11px] text-emerald-200 overflow-x-auto">
{`view StudentDashboard @cached(ttl = 60, tags = ["Student", "Enrollment"]) {
  studentId       Int     @source("s.id")
  studentName     String  @source("s.name")
  departmentName  String? @source("d.dept_name")
  enrolledCourses Int     @source("COUNT(e.id)")
  avgGradePoints  Float?  @source("ROUND(AVG(e.grade_points), 2)")
}`}
                </pre>
              </div>
            </div>
          </div>
        )}

        {/* ========================================================================= */}
        {/* TAB 8: RBAC & @auth FIELD AUTHORIZATION */}
        {/* ========================================================================= */}
        {activeSection === 'rbac' && (
          <div className="space-y-6">
            <div className="border-b border-[#30363D] pb-4">
              <h3 className="text-lg font-bold text-[#F0F6FC] flex items-center gap-2">
                <Lock className="w-5 h-5 text-amber-400" />
                Declarative Field-Level Security (@auth)
              </h3>
              <p className="text-xs text-[#8B949E] mt-1">
                Sensitive fields such as <code className="text-amber-300 font-mono">Instructor.salary</code> require
                role <code className="text-amber-300 font-mono">ADMIN</code>. Non-admin roles receive a selection-set-aware
                authorization error without exposing unauthorized data.
              </p>
            </div>

            <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
              <div className="space-y-4">
                <div className="bg-[#0D1117] border border-[#30363D] rounded-lg p-4 space-y-3">
                  <div className="text-xs font-mono font-semibold text-[#8B949E] uppercase">
                    Select Test Role Profile
                  </div>
                  <div className="grid grid-cols-2 gap-2">
                    {(['ADMIN', 'INSTRUCTOR', 'STUDENT', 'GUEST'] as AcademicRole[]).map((role) => (
                      <button
                        key={role}
                        onClick={() => handleTestRbac(role)}
                        className={`p-3 rounded-lg border text-left font-mono text-xs transition-all ${
                          rbacRole === role
                            ? 'bg-amber-500/20 border-amber-500 text-amber-300 font-bold'
                            : 'bg-[#161B22] border-[#30363D] text-[#8B949E] hover:text-[#F0F6FC]'
                        }`}
                      >
                        <div className="font-bold">{role}</div>
                        <div className="text-[10px] text-slate-400 mt-1">
                          {ROLE_PROFILES[role].description.slice(0, 45)}...
                        </div>
                      </button>
                    ))}
                  </div>
                </div>

                <div className="bg-[#0D1117] border border-[#30363D] rounded-lg p-4 font-mono text-xs space-y-2">
                  <div className="text-[#8B949E] font-semibold text-[11px]">Query Executed:</div>
                  <pre className="bg-[#161B22] p-2 rounded text-[11px] text-amber-200">
{`query QueryProtectedSalary {
  instructors {
    id
    name
    salary  # @auth(roles: ["ADMIN"])
  }
}`}
                  </pre>
                </div>
              </div>

              {/* Result Pane */}
              <div className="space-y-3">
                <div className="text-xs font-mono text-[#8B949E] uppercase">GraphQL Response</div>
                {rbacResponse ? (
                  <div className="bg-[#0D1117] border border-[#30363D] rounded-lg p-4 font-mono text-xs max-h-72 overflow-y-auto">
                    <pre
                      className={`text-[11px] ${
                        rbacResponse.errors ? 'text-rose-300' : 'text-emerald-300'
                      }`}
                    >
                      {JSON.stringify(rbacResponse, null, 2)}
                    </pre>
                  </div>
                ) : (
                  <div className="bg-[#0D1117] border border-[#30363D] rounded-lg p-8 text-center text-xs text-[#8B949E]">
                    Select a role profile to execute the field-level authorization test.
                  </div>
                )}
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};
