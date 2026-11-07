import React, { useState, useEffect, useCallback } from 'react';
import {
  Student,
  Department,
  CreateStudentInput,
  UpdateStudentInput,
  AcademicRole,
  ROLE_PROFILES,
} from '../../types/graphorm';
import { api } from '../../services/api';
import { SearchInput } from '../common/SearchInput';
import { Badge } from '../common/Badge';
import { DataPagination } from '../common/DataPagination';
import { ProtectedField } from '../common/ProtectedField';
import {
  UserPlus,
  ArrowUpDown,
  Eye,
  Edit2,
  Trash2,
  Filter,
  GraduationCap,
  Building2,
  LayoutGrid,
  List,
  Mail,
  Award,
  Lock,
  Loader2,
} from 'lucide-react';
import { StudentDetailModal } from './StudentDetailModal';
import { StudentFormModal } from './StudentFormModal';

interface StudentListProps {
  students?: Student[];
  departments: Department[];
  currentRole?: AcademicRole;
  onCreateStudent: (input: CreateStudentInput) => Promise<Student>;
  onUpdateStudent: (id: number, input: UpdateStudentInput) => Promise<Student>;
  onDeleteStudent: (id: number) => Promise<boolean>;
}

export const StudentList: React.FC<StudentListProps> = ({
  students: initialStudents = [],
  departments: propDepartments = [],
  currentRole = 'ADMIN',
  onCreateStudent,
  onUpdateStudent,
  onDeleteStudent,
}) => {
  const [students, setStudents] = useState<Student[]>(initialStudents);
  const [departments, setDepartments] = useState<Department[]>(propDepartments);
  const [search, setSearch] = useState('');
  const [selectedDeptId, setSelectedDeptId] = useState<string>('all');
  const [minGpa, setMinGpa] = useState<number>(0);
  const [sortField, setSortField] = useState<'name' | 'gpa' | 'id'>('name');
  const [sortOrder, setSortOrder] = useState<'asc' | 'desc'>('asc');
  const [viewMode, setViewMode] = useState<'table' | 'cards'>('table');
  const [currentPage, setCurrentPage] = useState(1);
  const [pageSize, setPageSize] = useState(8);
  const [totalItems, setTotalItems] = useState(initialStudents.length);
  const [totalPages, setTotalPages] = useState(1);
  const [isLoading, setIsLoading] = useState(false);
  const [presetFilter, setPresetFilter] = useState<'all' | 'high_honors' | 'honors' | 'independent'>('all');

  // Modals state
  const [detailStudent, setDetailStudent] = useState<Student | null>(null);
  const [formStudent, setFormStudent] = useState<Student | null>(null);
  const [isFormOpen, setIsFormOpen] = useState(false);
  const [deleteId, setDeleteId] = useState<number | null>(null);

  const canMutate = ROLE_PROFILES[currentRole]?.permissions.canMutate ?? false;

  // Server-side query execution with pushdown SQL (WHERE, ORDER BY, LIMIT ? OFFSET ?)
  const fetchStudents = useCallback(async () => {
    setIsLoading(true);
    try {
      let effectiveMinGpa: number | undefined = minGpa > 0 ? minGpa : undefined;
      let effectiveDeptId: string | undefined = selectedDeptId !== 'all' ? selectedDeptId : undefined;

      if (presetFilter === 'high_honors') {
        effectiveMinGpa = Math.max(effectiveMinGpa ?? 0, 3.9);
      } else if (presetFilter === 'honors') {
        effectiveMinGpa = Math.max(effectiveMinGpa ?? 0, 3.5);
      } else if (presetFilter === 'independent') {
        effectiveDeptId = 'none';
      }

      const res = await api.getStudentsPaginated({
        page: currentPage,
        pageSize,
        search: search.trim() || undefined,
        departmentId: effectiveDeptId,
        minGpa: effectiveMinGpa,
        sortBy: sortField,
        sortOrder,
      });

      setStudents(res.items);
      setTotalItems(res.pagination.totalItems);
      setTotalPages(res.pagination.totalPages);
      if (res.departments && res.departments.length > 0) {
        setDepartments(res.departments);
      }
    } catch (err) {
      console.error('Failed to fetch students:', err);
    } finally {
      setIsLoading(false);
    }
  }, [currentPage, pageSize, search, selectedDeptId, minGpa, presetFilter, sortField, sortOrder, currentRole]);

  useEffect(() => {
    fetchStudents();
  }, [fetchStudents]);

  const toggleSort = (field: 'name' | 'gpa' | 'id') => {
    if (sortField === field) {
      setSortOrder(sortOrder === 'asc' ? 'desc' : 'asc');
    } else {
      setSortField(field);
      setSortOrder('asc');
    }
    setCurrentPage(1);
  };

  const handleFormSubmit = async (
    input: CreateStudentInput | UpdateStudentInput,
    id?: number
  ) => {
    if (id) {
      await onUpdateStudent(id, input as UpdateStudentInput);
    } else {
      await onCreateStudent(input as CreateStudentInput);
    }
    await fetchStudents();
  };

  const confirmDelete = async (id: number) => {
    await onDeleteStudent(id);
    setDeleteId(null);
    await fetchStudents();
  };

  return (
    <div className="space-y-4">
      {/* Top Filter & Command Bar */}
      <div className="p-4 rounded-2xl bg-ink-850/90 border border-ink-700/70 shadow-sm flex flex-col md:flex-row md:items-center justify-between gap-3">
        <div className="flex-1 flex flex-wrap items-center gap-2.5">
          <SearchInput
            value={search}
            onChange={(val) => {
              setSearch(val);
              setCurrentPage(1);
            }}
            placeholder="Search scholars by name or university email..."
            className="w-full sm:w-72"
          />

          {/* Department Filter */}
          <div className="flex items-center gap-1.5 bg-ink-900/80 border border-ink-700/70 rounded-xl px-2.5 py-1.5 text-xs">
            <Filter className="w-3.5 h-3.5 text-slate-400" />
            <select
              value={selectedDeptId}
              onChange={(e) => {
                setSelectedDeptId(e.target.value);
                setCurrentPage(1);
              }}
              className="bg-transparent text-slate-200 font-medium focus:outline-none cursor-pointer"
              aria-label="Filter by department"
            >
              <option value="all">All Academic Faculties</option>
              {departments.map((d) => (
                <option key={d.id} value={d.id} className="bg-ink-900 text-slate-200">
                  {d.name} ({d.code})
                </option>
              ))}
              <option value="none" className="bg-ink-900 text-slate-200">
                Independent / Undecided
              </option>
            </select>
          </div>

          {/* Min GPA Filter */}
          <div className="flex items-center gap-2 bg-ink-900/80 border border-ink-700/70 rounded-xl px-3 py-1.5 text-xs">
            <span className="text-slate-400 font-mono text-[11px]">Min GPA:</span>
            <input
              type="range"
              min="0"
              max="4.0"
              step="0.1"
              value={minGpa}
              onChange={(e) => {
                setMinGpa(parseFloat(e.target.value));
                setCurrentPage(1);
              }}
              className="w-20 accent-sky-500 cursor-pointer"
              aria-label="Filter by minimum GPA"
            />
            <span className="font-mono text-white text-[11px] font-semibold w-7">
              {minGpa.toFixed(1)}
            </span>
          </div>

          {/* Sort Dropdown */}
          <div className="flex items-center gap-1.5 bg-ink-900/80 border border-ink-700/70 rounded-xl px-2.5 py-1.5 text-xs">
            <ArrowUpDown className="w-3.5 h-3.5 text-slate-400" />
            <select
              value={`${sortField}-${sortOrder}`}
              onChange={(e) => {
                const [f, o] = e.target.value.split('-');
                setSortField(f as 'name' | 'gpa' | 'id');
                setSortOrder(o as 'asc' | 'desc');
                setCurrentPage(1);
              }}
              className="bg-transparent text-slate-200 focus:outline-none cursor-pointer text-xs"
              aria-label="Sort scholars"
            >
              <option value="name-asc" className="bg-ink-900 text-slate-200">Name (A-Z)</option>
              <option value="name-desc" className="bg-ink-900 text-slate-200">Name (Z-A)</option>
              <option value="gpa-desc" className="bg-ink-900 text-slate-200">GPA (High-Low)</option>
              <option value="gpa-asc" className="bg-ink-900 text-slate-200">GPA (Low-High)</option>
              <option value="id-asc" className="bg-ink-900 text-slate-200">ID (Low-High)</option>
            </select>
          </div>

          {isLoading && (
            <div className="flex items-center gap-1 text-[11px] font-mono text-sky-400 animate-pulse ml-1">
              <Loader2 className="w-3.5 h-3.5 animate-spin" />
              <span>Querying...</span>
            </div>
          )}
        </div>

        {/* View Mode & Actions */}
        <div className="flex items-center gap-2">
          <div className="flex items-center bg-ink-900/80 border border-ink-700/70 rounded-xl p-0.5">
            <button
              onClick={() => setViewMode('table')}
              className={`p-1.5 rounded-lg transition-colors cursor-pointer ${
                viewMode === 'table'
                  ? 'bg-ink-750 text-white shadow-sm'
                  : 'text-slate-400 hover:text-white'
              }`}
              title="Table Grid"
              aria-label="Table View"
            >
              <List className="w-4 h-4" />
            </button>
            <button
              onClick={() => setViewMode('cards')}
              className={`p-1.5 rounded-lg transition-colors cursor-pointer ${
                viewMode === 'cards'
                  ? 'bg-ink-750 text-white shadow-sm'
                  : 'text-slate-400 hover:text-white'
              }`}
              title="Card Dossier"
              aria-label="Card View"
            >
              <LayoutGrid className="w-4 h-4" />
            </button>
          </div>

          <button
            onClick={() => {
              setFormStudent(null);
              setIsFormOpen(true);
            }}
            disabled={!canMutate}
            className={`flex items-center gap-2 px-3.5 py-2 rounded-xl text-xs font-semibold shadow-sm transition-all ${
              canMutate
                ? 'bg-sky-500 hover:bg-sky-400 text-[#0D1117] cursor-pointer'
                : 'bg-ink-800 text-slate-500 border border-ink-700 opacity-60 cursor-not-allowed'
            }`}
            title={
              canMutate
                ? 'Matriculate New Scholar'
                : 'Scholar creation requires ADMIN or INSTRUCTOR privilege (@auth)'
            }
          >
            {canMutate ? <UserPlus className="w-4 h-4" /> : <Lock className="w-3.5 h-3.5" />}
            <span>Matriculate Scholar</span>
          </button>
        </div>
      </div>

      {/* Preset Quick Chips */}
      <div className="flex items-center gap-2 text-xs flex-wrap px-1">
        <span className="text-slate-500 font-mono text-[11px]">Filter Presets:</span>
        <button
          onClick={() => {
            setPresetFilter('all');
            setCurrentPage(1);
          }}
          className={`px-2.5 py-1 rounded-lg font-mono text-[11px] transition-colors cursor-pointer ${
            presetFilter === 'all'
              ? 'bg-sky-500/20 text-sky-300 border border-sky-500/40'
              : 'bg-ink-900 text-slate-400 border border-ink-750 hover:text-slate-200'
          }`}
        >
          All Scholars ({totalItems})
        </button>
        <button
          onClick={() => {
            setPresetFilter('high_honors');
            setCurrentPage(1);
          }}
          className={`px-2.5 py-1 rounded-lg font-mono text-[11px] transition-colors cursor-pointer ${
            presetFilter === 'high_honors'
              ? 'bg-laurel-500/20 text-laurel-300 border border-laurel-500/40'
              : 'bg-ink-900 text-slate-400 border border-ink-750 hover:text-slate-200'
          }`}
        >
          High Honors (GPA ≥ 3.9)
        </button>
        <button
          onClick={() => {
            setPresetFilter('honors');
            setCurrentPage(1);
          }}
          className={`px-2.5 py-1 rounded-lg font-mono text-[11px] transition-colors cursor-pointer ${
            presetFilter === 'honors'
              ? 'bg-gold-500/20 text-gold-300 border border-gold-500/40'
              : 'bg-ink-900 text-slate-400 border border-ink-750 hover:text-slate-200'
          }`}
        >
          Honors (GPA ≥ 3.5)
        </button>
        <button
          onClick={() => {
            setPresetFilter('independent');
            setCurrentPage(1);
          }}
          className={`px-2.5 py-1 rounded-lg font-mono text-[11px] transition-colors cursor-pointer ${
            presetFilter === 'independent'
              ? 'bg-purple-500/20 text-purple-300 border border-purple-500/40'
              : 'bg-ink-900 text-slate-400 border border-ink-750 hover:text-slate-200'
          }`}
        >
          Independent / Unassigned
        </button>
      </div>

      {/* Main Content: Table or Cards */}
      {viewMode === 'table' ? (
        <div className="rounded-2xl bg-ink-850/90 border border-ink-700/70 overflow-hidden shadow-sm">
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead>
                <tr className="bg-ink-900/80 border-b border-ink-750/70 text-slate-400 font-medium">
                  <th
                    className="py-3 px-5 cursor-pointer hover:text-white transition-colors"
                    onClick={() => toggleSort('id')}
                  >
                    <div className="flex items-center gap-1.5 font-mono text-[11px]">
                      <span>ID</span>
                      <ArrowUpDown className="w-3 h-3" />
                    </div>
                  </th>
                  <th
                    className="py-3 px-5 cursor-pointer hover:text-white transition-colors"
                    onClick={() => toggleSort('name')}
                  >
                    <div className="flex items-center gap-1.5">
                      <span>Scholar Dossier</span>
                      <ArrowUpDown className="w-3 h-3" />
                    </div>
                  </th>
                  <th className="py-3 px-5">Faculty & Department</th>
                  <th
                    className="py-3 px-5 cursor-pointer hover:text-white transition-colors"
                    onClick={() => toggleSort('gpa')}
                  >
                    <div className="flex items-center gap-1.5">
                      <span>Cumulative GPA</span>
                      <ArrowUpDown className="w-3 h-3" />
                    </div>
                  </th>
                  <th className="py-3 px-5">Enrollment Load</th>
                  <th className="py-3 px-5 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-ink-750/50 text-slate-300">
                {students.length === 0 ? (
                  <tr>
                    <td colSpan={6} className="py-12 text-center text-slate-400 text-xs">
                      {isLoading ? 'Loading records...' : 'No scholar records found matching current query criteria.'}
                    </td>
                  </tr>
                ) : (
                  students.map((student) => {
                    const gpa = student.gpa ?? 3.5;
                    const enrollmentsCount = student.enrollments ? student.enrollments.length : 0;
                    return (
                      <tr
                        key={student.id}
                        className="hover:bg-ink-800/50 transition-colors group"
                      >
                        <td className="py-3.5 px-5 font-mono text-[11px] text-slate-400">
                          #{student.id}
                        </td>
                        <td className="py-3.5 px-5">
                          <div className="flex items-center gap-3">
                            <div className="w-8 h-8 rounded-xl bg-ink-800 border border-gold-500/30 text-gold-300 flex items-center justify-center font-display font-bold text-xs shrink-0 shadow-sm">
                              {student.name.charAt(0)}
                            </div>
                            <div>
                              <div className="font-semibold text-white group-hover:text-gold-300 transition-colors">
                                {student.name}
                              </div>
                              <div className="text-[11px] text-slate-400 font-mono">{student.email}</div>
                            </div>
                          </div>
                        </td>
                        <td className="py-3.5 px-5">
                          {student.department ? (
                            <div className="flex items-center gap-2">
                              <Building2 className="w-3.5 h-3.5 text-slate-400" />
                              <span className="text-xs text-slate-200 font-medium">
                                {student.department.name}
                              </span>
                              <span className="text-[10px] font-mono text-gold-300 px-1.5 py-0.2 bg-gold-500/10 border border-gold-500/20 rounded">
                                {student.department.code}
                              </span>
                            </div>
                          ) : (
                            <span className="text-xs text-slate-400 italic">Independent Scholar</span>
                          )}
                        </td>
                        <td className="py-3.5 px-5">
                          {/* Protected GPA Field: Masks when STUDENT or GUEST */}
                          <ProtectedField
                            currentRole={currentRole}
                            requiredRoles={['ADMIN', 'INSTRUCTOR']}
                            fallbackText="Restricted"
                            value={
                              <Badge
                                variant={gpa >= 3.8 ? 'laurel' : gpa >= 3.5 ? 'brand' : 'warning'}
                                size="sm"
                              >
                                {gpa >= 3.8 && <Award className="w-3 h-3 text-laurel-400" />}
                                <span className="font-mono tabular-nums font-semibold">
                                  {gpa.toFixed(2)}
                                </span>
                              </Badge>
                            }
                          />
                        </td>
                        <td className="py-3.5 px-5 font-mono text-xs text-slate-400">
                          <div className="flex items-center gap-1.5">
                            <GraduationCap className="w-3.5 h-3.5 text-cobalt-400" />
                            <span>{enrollmentsCount} {enrollmentsCount === 1 ? 'unit' : 'units'}</span>
                          </div>
                        </td>
                        <td className="py-3.5 px-5 text-right">
                          <div className="flex items-center justify-end gap-1">
                            <button
                              onClick={() => setDetailStudent(student)}
                              className="p-1.5 text-slate-400 hover:text-gold-300 hover:bg-ink-750 rounded-lg transition-colors cursor-pointer"
                              title="Inspect Transcript Dossier"
                            >
                              <Eye className="w-4 h-4" />
                            </button>
                            <button
                              onClick={() => {
                                if (!canMutate) return;
                                setFormStudent(student);
                                setIsFormOpen(true);
                              }}
                              disabled={!canMutate}
                              className={`p-1.5 rounded-lg transition-colors ${
                                canMutate
                                  ? 'text-slate-400 hover:text-cobalt-300 hover:bg-ink-750 cursor-pointer'
                                  : 'text-slate-600 opacity-40 cursor-not-allowed'
                              }`}
                              title={
                                canMutate
                                  ? 'Modify Scholar Details'
                                  : 'Editing requires ADMIN or INSTRUCTOR privilege (@auth)'
                              }
                            >
                              <Edit2 className="w-4 h-4" />
                            </button>
                            <button
                              onClick={() => {
                                if (!canMutate) return;
                                setDeleteId(student.id);
                              }}
                              disabled={!canMutate}
                              className={`p-1.5 rounded-lg transition-colors ${
                                canMutate
                                  ? 'text-slate-400 hover:text-garnet-400 hover:bg-ink-750 cursor-pointer'
                                  : 'text-slate-600 opacity-40 cursor-not-allowed'
                              }`}
                              title={
                                canMutate
                                  ? 'Expel / Remove Scholar'
                                  : 'Deletion requires ADMIN or INSTRUCTOR privilege (@auth)'
                              }
                            >
                              <Trash2 className="w-4 h-4" />
                            </button>
                          </div>
                        </td>
                      </tr>
                    );
                  })
                )}
              </tbody>
            </table>
          </div>

          {/* Standardized Reusable Server-Side Pagination */}
          <DataPagination
            pagination={{
              currentPage,
              pageSize,
              totalItems,
              totalPages,
            }}
            onPageChange={setCurrentPage}
            onPageSizeChange={(newSize) => {
              setPageSize(newSize);
              setCurrentPage(1);
            }}
            pageSizeOptions={[5, 8, 10, 25, 50]}
            itemLabel="scholars"
            className="rounded-t-none border-t border-b-0 border-x-0"
          />
        </div>
      ) : (
        /* Cards Dossier Grid */
        <div className="space-y-4">
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
            {students.length === 0 ? (
              <div className="col-span-full py-12 text-center text-slate-400 text-xs bg-ink-850/90 border border-ink-700/70 rounded-2xl">
                {isLoading ? 'Loading records...' : 'No scholar records found matching query criteria.'}
              </div>
            ) : (
              students.map((student) => {
                const gpa = student.gpa ?? 3.5;
                const enrollmentsCount = student.enrollments ? student.enrollments.length : 0;
                return (
                  <div
                    key={student.id}
                    className="p-5 rounded-2xl bg-ink-850/90 border border-ink-700/70 hover:border-gold-500/40 transition-all group flex flex-col justify-between shadow-sm"
                  >
                    <div>
                      <div className="flex items-start justify-between gap-2">
                        <div className="w-11 h-11 rounded-2xl bg-ink-800 border border-gold-500/30 text-gold-300 flex items-center justify-center font-display font-bold text-base shadow-sm">
                          {student.name.charAt(0)}
                        </div>
                        <ProtectedField
                          currentRole={currentRole}
                          requiredRoles={['ADMIN', 'INSTRUCTOR']}
                          fallbackText="Restricted"
                          value={
                            <Badge
                              variant={gpa >= 3.8 ? 'laurel' : gpa >= 3.5 ? 'brand' : 'warning'}
                              size="sm"
                            >
                              GPA {gpa.toFixed(2)}
                            </Badge>
                          }
                        />
                      </div>

                      <h4 className="font-semibold text-white mt-3 text-sm group-hover:text-gold-300 transition-colors">
                        {student.name}
                      </h4>
                      <div className="flex items-center gap-1.5 text-xs text-slate-400 mt-0.5 font-mono">
                        <Mail className="w-3 h-3 text-slate-400 shrink-0" />
                        <span className="truncate">{student.email}</span>
                      </div>

                      <div className="mt-4 pt-3 border-t border-ink-750/70 space-y-2 text-xs">
                        <div className="flex items-center justify-between text-slate-400">
                          <span>Faculty:</span>
                          <span className="font-medium text-slate-200">
                            {student.department?.code || 'Independent'}
                          </span>
                        </div>
                        <div className="flex items-center justify-between text-slate-400">
                          <span>Enrolled Units:</span>
                          <span className="font-mono text-cobalt-300 font-semibold">
                            {enrollmentsCount} Courses
                          </span>
                        </div>
                      </div>
                    </div>

                    <div className="mt-4 pt-3 border-t border-ink-750/70 flex items-center justify-between">
                      <span className="font-mono text-[10px] text-slate-400">ID #{student.id}</span>
                      <div className="flex items-center gap-1">
                        <button
                          onClick={() => setDetailStudent(student)}
                          className="p-1.5 text-slate-400 hover:text-gold-300 hover:bg-ink-750 rounded-lg transition-colors cursor-pointer"
                          title="Inspect Profile"
                        >
                          <Eye className="w-3.5 h-3.5" />
                        </button>
                        <button
                          onClick={() => {
                            if (!canMutate) return;
                            setFormStudent(student);
                            setIsFormOpen(true);
                          }}
                          disabled={!canMutate}
                          className={`p-1.5 rounded-lg transition-colors ${
                            canMutate
                              ? 'text-slate-400 hover:text-cobalt-300 hover:bg-ink-750 cursor-pointer'
                              : 'text-slate-600 opacity-40 cursor-not-allowed'
                          }`}
                          title={
                            canMutate
                              ? 'Modify Scholar'
                              : 'Editing requires ADMIN or INSTRUCTOR privilege (@auth)'
                          }
                        >
                          <Edit2 className="w-3.5 h-3.5" />
                        </button>
                        <button
                          onClick={() => {
                            if (!canMutate) return;
                            setDeleteId(student.id);
                          }}
                          disabled={!canMutate}
                          className={`p-1.5 rounded-lg transition-colors ${
                            canMutate
                              ? 'text-slate-400 hover:text-garnet-400 hover:bg-ink-750 cursor-pointer'
                              : 'text-slate-600 opacity-40 cursor-not-allowed'
                          }`}
                          title={
                            canMutate
                              ? 'Expel Scholar'
                              : 'Deletion requires ADMIN or INSTRUCTOR privilege (@auth)'
                          }
                        >
                          <Trash2 className="w-3.5 h-3.5" />
                        </button>
                      </div>
                    </div>
                  </div>
                );
              })
            )}
          </div>

          <DataPagination
            pagination={{
              currentPage,
              pageSize,
              totalItems,
              totalPages,
            }}
            onPageChange={setCurrentPage}
            onPageSizeChange={(newSize) => {
              setPageSize(newSize);
              setCurrentPage(1);
            }}
            pageSizeOptions={[5, 8, 10, 25, 50]}
            itemLabel="scholars"
          />
        </div>
      )}

      {/* Modals */}
      <StudentDetailModal
        student={detailStudent}
        isOpen={!!detailStudent}
        onClose={() => setDetailStudent(null)}
        onEdit={(s) => {
          setFormStudent(s);
          setIsFormOpen(true);
        }}
      />

      <StudentFormModal
        isOpen={isFormOpen}
        onClose={() => {
          setIsFormOpen(false);
          setFormStudent(null);
        }}
        student={formStudent}
        departments={departments}
        onSubmit={handleFormSubmit}
      />

      {/* Delete Confirmation Modal */}
      {deleteId != null && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
          <div
            className="fixed inset-0 bg-ink-950/80 backdrop-blur-sm"
            onClick={() => setDeleteId(null)}
          />
          <div className="relative w-full max-w-sm bg-ink-850 border border-ink-700/80 p-6 rounded-2xl shadow-elevated z-10 text-center">
            <div className="w-12 h-12 rounded-full bg-garnet-500/10 text-garnet-400 border border-garnet-500/20 flex items-center justify-center mx-auto mb-4">
              <Trash2 className="w-6 h-6" />
            </div>
            <h4 className="font-display font-bold text-base text-white">Confirm Scholar Expulsion</h4>
            <p className="text-xs text-slate-400 mt-2 leading-relaxed">
              Are you certain you wish to withdraw scholar #{deleteId}? This cascades across all course enrollments in the GraphORM schema.
            </p>
            <div className="mt-5 flex items-center justify-center gap-3">
              <button
                onClick={() => setDeleteId(null)}
                className="px-4 py-2 rounded-xl bg-ink-800 hover:bg-ink-750 text-slate-300 text-xs font-semibold transition-colors cursor-pointer"
              >
                Cancel
              </button>
              <button
                onClick={() => confirmDelete(deleteId)}
                className="px-4 py-2 rounded-xl bg-garnet-600 hover:bg-garnet-500 text-white text-xs font-bold transition-colors cursor-pointer"
              >
                Withdraw Scholar
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
