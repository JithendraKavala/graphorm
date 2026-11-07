import React, { useState, useMemo } from 'react';
import {
  Student,
  Department,
  CreateStudentInput,
  UpdateStudentInput,
} from '../../types/graphorm';
import { SearchInput } from '../common/SearchInput';
import { Badge } from '../common/Badge';
import {
  UserPlus,
  ArrowUpDown,
  Eye,
  Edit2,
  Trash2,
  Filter,
  GraduationCap,
  Building2,
  ChevronLeft,
  ChevronRight,
  LayoutGrid,
  List,
  Mail,
  Award,
} from 'lucide-react';
import { StudentDetailModal } from './StudentDetailModal';
import { StudentFormModal } from './StudentFormModal';

interface StudentListProps {
  students: Student[];
  departments: Department[];
  onCreateStudent: (input: CreateStudentInput) => Promise<Student>;
  onUpdateStudent: (id: number, input: UpdateStudentInput) => Promise<Student>;
  onDeleteStudent: (id: number) => Promise<boolean>;
}

export const StudentList: React.FC<StudentListProps> = ({
  students,
  departments,
  onCreateStudent,
  onUpdateStudent,
  onDeleteStudent,
}) => {
  const [search, setSearch] = useState('');
  const [selectedDeptId, setSelectedDeptId] = useState<string>('all');
  const [minGpa, setMinGpa] = useState<number>(0);
  const [sortField, setSortField] = useState<'name' | 'gpa' | 'id'>('name');
  const [sortOrder, setSortOrder] = useState<'asc' | 'desc'>('asc');
  const [viewMode, setViewMode] = useState<'table' | 'cards'>('table');
  const [currentPage, setCurrentPage] = useState(1);
  const pageSize = 8;

  // Modals state
  const [detailStudent, setDetailStudent] = useState<Student | null>(null);
  const [formStudent, setFormStudent] = useState<Student | null>(null);
  const [isFormOpen, setIsFormOpen] = useState(false);
  const [deleteId, setDeleteId] = useState<number | null>(null);

  // Filter & sort
  const filteredStudents = useMemo(() => {
    return students
      .filter((s) => {
        const matchesSearch =
          s.name.toLowerCase().includes(search.toLowerCase()) ||
          s.email.toLowerCase().includes(search.toLowerCase());
        const matchesDept =
          selectedDeptId === 'all'
            ? true
            : selectedDeptId === 'none'
            ? s.departmentId == null
            : s.departmentId === Number(selectedDeptId);
        const matchesGpa = (s.gpa ?? 0) >= minGpa;
        return matchesSearch && matchesDept && matchesGpa;
      })
      .sort((a, b) => {
        let valA = a[sortField];
        let valB = b[sortField];
        if (valA == null) return 1;
        if (valB == null) return -1;
        if (typeof valA === 'string' && typeof valB === 'string') {
          return sortOrder === 'asc'
            ? valA.localeCompare(valB)
            : valB.localeCompare(valA);
        }
        return sortOrder === 'asc'
          ? (valA as number) - (valB as number)
          : (valB as number) - (valA as number);
      });
  }, [students, search, selectedDeptId, minGpa, sortField, sortOrder]);

  const totalPages = Math.ceil(filteredStudents.length / pageSize) || 1;
  const paginatedStudents = filteredStudents.slice(
    (currentPage - 1) * pageSize,
    currentPage * pageSize
  );

  const toggleSort = (field: 'name' | 'gpa' | 'id') => {
    if (sortField === field) {
      setSortOrder(sortOrder === 'asc' ? 'desc' : 'asc');
    } else {
      setSortField(field);
      setSortOrder('asc');
    }
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
  };

  const confirmDelete = async (id: number) => {
    await onDeleteStudent(id);
    setDeleteId(null);
  };

  return (
    <div className="space-y-4">
      {/* Top Filter & Command Bar */}
      <div className="p-4 rounded-2xl bg-ink-850/90 border border-ink-700/70 shadow-sm flex flex-col md:flex-row md:items-center justify-between gap-3">
        <div className="flex-1 flex flex-wrap items-center gap-2.5">
          <SearchInput
            value={search}
            onChange={setSearch}
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
              className="bg-transparent text-slate-200 focus:outline-none cursor-pointer text-xs"
            >
              <option value="all">All Academic Faculties</option>
              <option value="none">Undeclared / Independent</option>
              {departments.map((d) => (
                <option key={d.id} value={d.id}>
                  {d.name} ({d.code})
                </option>
              ))}
            </select>
          </div>

          {/* GPA Slider */}
          <div className="hidden xl:flex items-center gap-2 px-3 py-1.5 rounded-xl bg-ink-900/80 border border-ink-700/70 text-xs">
            <span className="text-slate-400 font-mono text-[11px]">Min GPA:</span>
            <input
              type="range"
              min="0"
              max="3.9"
              step="0.1"
              value={minGpa}
              onChange={(e) => {
                setMinGpa(parseFloat(e.target.value));
                setCurrentPage(1);
              }}
              className="w-20 accent-gold-500 cursor-pointer"
            />
            <span className="font-mono text-gold-300 font-bold">{minGpa.toFixed(1)}</span>
          </div>

          {/* View Toggle */}
          <div className="flex items-center bg-ink-900/80 border border-ink-700/70 p-0.5 rounded-xl text-xs">
            <button
              onClick={() => setViewMode('table')}
              className={`p-1.5 rounded-lg transition-colors cursor-pointer ${
                viewMode === 'table' ? 'bg-ink-750 text-white' : 'text-slate-400 hover:text-slate-200'
              }`}
              title="Table View"
            >
              <List className="w-3.5 h-3.5" />
            </button>
            <button
              onClick={() => setViewMode('cards')}
              className={`p-1.5 rounded-lg transition-colors cursor-pointer ${
                viewMode === 'cards' ? 'bg-ink-750 text-white' : 'text-slate-400 hover:text-slate-200'
              }`}
              title="Dossier Cards View"
            >
              <LayoutGrid className="w-3.5 h-3.5" />
            </button>
          </div>
        </div>

        <button
          onClick={() => {
            setFormStudent(null);
            setIsFormOpen(true);
          }}
          className="flex items-center justify-center gap-2 px-4 py-2 rounded-xl bg-gradient-to-r from-gold-500 to-gold-600 hover:from-gold-600 hover:to-gold-700 text-ink-950 font-bold text-xs shadow-[0_0_15px_-3px_rgba(212,175,55,0.3)] transition-all cursor-pointer whitespace-nowrap"
        >
          <UserPlus className="w-4 h-4 stroke-[2.5]" />
          <span>Matriculate Scholar</span>
        </button>
      </div>

      {/* Main Content: Table or Cards */}
      {viewMode === 'table' ? (
        <div className="rounded-2xl bg-ink-850/90 border border-ink-700/70 overflow-hidden shadow-sm">
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs text-slate-300">
              <thead className="bg-ink-900/90 border-b border-ink-750/80 text-[10px] uppercase tracking-wider text-slate-400 font-mono select-none">
                <tr>
                  <th
                    onClick={() => toggleSort('id')}
                    className="py-3 px-5 cursor-pointer hover:text-slate-200 transition-colors"
                  >
                    <div className="flex items-center gap-1.5">
                      <span>ID</span>
                      <ArrowUpDown className="w-3 h-3 text-slate-400" />
                    </div>
                  </th>
                  <th
                    onClick={() => toggleSort('name')}
                    className="py-3 px-5 cursor-pointer hover:text-slate-200 transition-colors"
                  >
                    <div className="flex items-center gap-1.5">
                      <span>Scholar Identity</span>
                      <ArrowUpDown className="w-3 h-3 text-slate-400" />
                    </div>
                  </th>
                  <th className="py-3 px-5">Faculty Department</th>
                  <th
                    onClick={() => toggleSort('gpa')}
                    className="py-3 px-5 cursor-pointer hover:text-slate-200 transition-colors"
                  >
                    <div className="flex items-center gap-1.5">
                      <span>Cumulative GPA</span>
                      <ArrowUpDown className="w-3 h-3 text-slate-400" />
                    </div>
                  </th>
                  <th className="py-3 px-5">Registered Courses</th>
                  <th className="py-3 px-5 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-ink-750/50">
                {paginatedStudents.length === 0 ? (
                  <tr>
                    <td colSpan={6} className="py-12 text-center text-slate-400 text-xs">
                      No scholar records found matching current query criteria.
                    </td>
                  </tr>
                ) : (
                  paginatedStudents.map((student) => {
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
                          <Badge
                            variant={gpa >= 3.8 ? 'laurel' : gpa >= 3.5 ? 'brand' : 'warning'}
                            size="sm"
                          >
                            {gpa >= 3.8 && <Award className="w-3 h-3 text-laurel-400" />}
                            <span className="font-mono tabular-nums font-semibold">{gpa.toFixed(2)}</span>
                          </Badge>
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
                                setFormStudent(student);
                                setIsFormOpen(true);
                              }}
                              className="p-1.5 text-slate-400 hover:text-cobalt-300 hover:bg-ink-750 rounded-lg transition-colors cursor-pointer"
                              title="Modify Scholar Details"
                            >
                              <Edit2 className="w-4 h-4" />
                            </button>
                            <button
                              onClick={() => setDeleteId(student.id)}
                              className="p-1.5 text-slate-400 hover:text-garnet-400 hover:bg-ink-750 rounded-lg transition-colors cursor-pointer"
                              title="Expel / Remove Scholar"
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

          {/* Pagination */}
          <div className="px-5 py-3 bg-ink-900/70 border-t border-ink-750/70 flex items-center justify-between text-xs text-slate-400">
            <div>
              Showing{' '}
              <span className="text-white font-semibold tabular-nums">
                {filteredStudents.length === 0 ? 0 : (currentPage - 1) * pageSize + 1}
              </span>{' '}
              to{' '}
              <span className="text-white font-semibold tabular-nums">
                {Math.min(currentPage * pageSize, filteredStudents.length)}
              </span>{' '}
              of <span className="text-white font-semibold tabular-nums">{filteredStudents.length}</span> matriculated scholars
            </div>

            <div className="flex items-center gap-2">
              <button
                onClick={() => setCurrentPage((p) => Math.max(p - 1, 1))}
                disabled={currentPage === 1}
                className="p-1.5 rounded-lg border border-ink-750 text-slate-400 hover:text-white disabled:opacity-25 disabled:pointer-events-none transition-colors cursor-pointer"
              >
                <ChevronLeft className="w-4 h-4" />
              </button>
              <span className="px-2.5 py-0.5 font-mono text-slate-300 text-xs">
                {currentPage} / {totalPages}
              </span>
              <button
                onClick={() => setCurrentPage((p) => Math.min(p + 1, totalPages))}
                disabled={currentPage === totalPages}
                className="p-1.5 rounded-lg border border-ink-750 text-slate-400 hover:text-white disabled:opacity-25 disabled:pointer-events-none transition-colors cursor-pointer"
              >
                <ChevronRight className="w-4 h-4" />
              </button>
            </div>
          </div>
        </div>
      ) : (
        /* Cards Dossier Grid */
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
          {paginatedStudents.map((student) => {
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
                    <Badge
                      variant={gpa >= 3.8 ? 'laurel' : gpa >= 3.5 ? 'brand' : 'warning'}
                      size="sm"
                    >
                      GPA {gpa.toFixed(2)}
                    </Badge>
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
                        setFormStudent(student);
                        setIsFormOpen(true);
                      }}
                      className="p-1.5 text-slate-400 hover:text-cobalt-300 hover:bg-ink-750 rounded-lg transition-colors cursor-pointer"
                      title="Modify Scholar"
                    >
                      <Edit2 className="w-3.5 h-3.5" />
                    </button>
                    <button
                      onClick={() => setDeleteId(student.id)}
                      className="p-1.5 text-slate-400 hover:text-garnet-400 hover:bg-ink-750 rounded-lg transition-colors cursor-pointer"
                      title="Expel Scholar"
                    >
                      <Trash2 className="w-3.5 h-3.5" />
                    </button>
                  </div>
                </div>
              </div>
            );
          })}
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
