import React, { useState, useEffect, useCallback } from 'react';
import {
  Enrollment,
  Student,
  Course,
  CreateEnrollmentInput,
  UpdateEnrollmentInput,
  AcademicRole,
  ROLE_PROFILES,
} from '../../types/graphorm';
import { api } from '../../services/api';
import { SearchInput } from '../common/SearchInput';
import { Badge } from '../common/Badge';
import { DataPagination } from '../common/DataPagination';
import { ProtectedField } from '../common/ProtectedField';
import {
  GraduationCap,
  Plus,
  Edit2,
  Trash2,
  Filter,
  Calendar,
  Lock,
  ArrowUpDown,
  Loader2,
} from 'lucide-react';
import { EnrollmentFormModal } from './EnrollmentFormModal';

interface EnrollmentListProps {
  enrollments?: Enrollment[];
  students: Student[];
  courses: Course[];
  currentRole?: AcademicRole;
  onCreateEnrollment: (input: CreateEnrollmentInput) => Promise<Enrollment>;
  onUpdateEnrollment: (id: number, input: UpdateEnrollmentInput) => Promise<Enrollment>;
  onDeleteEnrollment: (id: number) => Promise<boolean>;
}

export const EnrollmentList: React.FC<EnrollmentListProps> = ({
  enrollments: initialEnrollments = [],
  students: propStudents = [],
  courses: propCourses = [],
  currentRole = 'ADMIN',
  onCreateEnrollment,
  onUpdateEnrollment,
  onDeleteEnrollment,
}) => {
  const [enrollments, setEnrollments] = useState<Enrollment[]>(initialEnrollments);
  const [courses, setCourses] = useState<Course[]>(propCourses);
  const [students, setStudents] = useState<Student[]>(propStudents);
  const [search, setSearch] = useState('');
  const [selectedCourseId, setSelectedCourseId] = useState<string>('all');
  const [selectedGrade, setSelectedGrade] = useState<string>('all');
  const [sortBy, setSortBy] = useState<'id' | 'student' | 'course' | 'grade' | 'date'>('id');
  const [sortOrder, setSortOrder] = useState<'asc' | 'desc'>('desc');
  const [currentPage, setCurrentPage] = useState(1);
  const [pageSize, setPageSize] = useState(8);
  const [totalItems, setTotalItems] = useState(initialEnrollments.length);
  const [totalPages, setTotalPages] = useState(1);
  const [isLoading, setIsLoading] = useState(false);

  const [formEnrollment, setFormEnrollment] = useState<Enrollment | null>(null);
  const [isFormOpen, setIsFormOpen] = useState(false);
  const [deleteId, setDeleteId] = useState<number | null>(null);

  const canMutate = ROLE_PROFILES[currentRole]?.permissions.canMutate ?? false;

  const toggleSort = (field: 'id' | 'student' | 'course' | 'grade' | 'date') => {
    if (sortBy === field) {
      setSortOrder(sortOrder === 'asc' ? 'desc' : 'asc');
    } else {
      setSortBy(field);
      setSortOrder('asc');
    }
    setCurrentPage(1);
  };

  const fetchEnrollments = useCallback(async () => {
    setIsLoading(true);
    try {
      const res = await api.getEnrollmentsPaginated({
        page: currentPage,
        pageSize,
        search: search.trim() || undefined,
        courseId: selectedCourseId !== 'all' ? selectedCourseId : undefined,
        grade: selectedGrade !== 'all' ? selectedGrade : undefined,
        sortBy,
        sortOrder,
      });
      setEnrollments(res.items);
      setTotalItems(res.pagination.totalItems);
      setTotalPages(res.pagination.totalPages);
      if (res.courses && res.courses.length > 0) {
        setCourses(res.courses);
      }
      if (res.students && res.students.length > 0) {
        setStudents(res.students);
      }
    } catch (err) {
      console.error('Failed to fetch enrollments:', err);
    } finally {
      setIsLoading(false);
    }
  }, [currentPage, pageSize, search, selectedCourseId, selectedGrade, sortBy, sortOrder, currentRole]);

  useEffect(() => {
    fetchEnrollments();
  }, [fetchEnrollments]);

  const handleFormSubmit = async (
    input: CreateEnrollmentInput | UpdateEnrollmentInput,
    id?: number
  ) => {
    if (id) {
      await onUpdateEnrollment(id, input as UpdateEnrollmentInput);
    } else {
      await onCreateEnrollment(input as CreateEnrollmentInput);
    }
    await fetchEnrollments();
  };

  return (
    <div className="space-y-4">
      {/* Search & Multi-Facet Filter Bar */}
      <div className="p-4 rounded-2xl bg-ink-850/90 border border-ink-700/70 shadow-sm flex flex-col sm:flex-row sm:items-center justify-between gap-3">
        <div className="flex-1 flex flex-wrap items-center gap-2.5">
          <SearchInput
            value={search}
            onChange={(val) => {
              setSearch(val);
              setCurrentPage(1);
            }}
            placeholder="Search enrollment ledger by scholar name or course..."
            className="w-full sm:w-72"
          />

          {/* Course Filter */}
          <div className="flex items-center gap-1.5 bg-ink-900/80 border border-ink-700/70 rounded-xl px-2.5 py-1.5 text-xs">
            <Filter className="w-3.5 h-3.5 text-slate-400" />
            <select
              value={selectedCourseId}
              onChange={(e) => {
                setSelectedCourseId(e.target.value);
                setCurrentPage(1);
              }}
              className="bg-transparent text-slate-200 focus:outline-none cursor-pointer text-xs"
              aria-label="Filter by course"
            >
              <option value="all" className="bg-ink-900 text-slate-200">All Course Offerings</option>
              {courses.map((c) => (
                <option key={c.id} value={c.id} className="bg-ink-900 text-slate-200">
                  {c.code} - {c.title}
                </option>
              ))}
            </select>
          </div>

          {/* Grade Filter */}
          <div className="flex items-center gap-1.5 bg-ink-900/80 border border-ink-700/70 rounded-xl px-2.5 py-1.5 text-xs">
            <span className="text-slate-400 font-mono text-[11px]">Grade:</span>
            <select
              value={selectedGrade}
              onChange={(e) => {
                setSelectedGrade(e.target.value);
                setCurrentPage(1);
              }}
              className="bg-transparent text-slate-200 focus:outline-none cursor-pointer text-xs"
              aria-label="Filter by letter grade"
            >
              <option value="all" className="bg-ink-900 text-slate-200">All Letter Grades</option>
              <option value="A" className="bg-ink-900 text-slate-200">Grade Tier A (A, A-)</option>
              <option value="B" className="bg-ink-900 text-slate-200">Grade Tier B (B+, B)</option>
              <option value="C" className="bg-ink-900 text-slate-200">Grade Tier C (C+, C)</option>
            </select>
          </div>

          {/* Sort Dropdown */}
          <div className="flex items-center gap-1.5 bg-ink-900/80 border border-ink-700/70 rounded-xl px-2.5 py-1.5 text-xs">
            <ArrowUpDown className="w-3.5 h-3.5 text-slate-400" />
            <select
              value={`${sortBy}-${sortOrder}`}
              onChange={(e) => {
                const [f, o] = e.target.value.split('-');
                setSortBy(f as 'id' | 'student' | 'course' | 'grade' | 'date');
                setSortOrder(o as 'asc' | 'desc');
                setCurrentPage(1);
              }}
              className="bg-transparent text-slate-200 focus:outline-none cursor-pointer text-xs"
              aria-label="Sort enrollments"
            >
              <option value="id-desc" className="bg-ink-900 text-slate-200">Recent First (ID Desc)</option>
              <option value="id-asc" className="bg-ink-900 text-slate-200">Oldest First (ID Asc)</option>
              <option value="student-asc" className="bg-ink-900 text-slate-200">Scholar Name (A-Z)</option>
              <option value="student-desc" className="bg-ink-900 text-slate-200">Scholar Name (Z-A)</option>
              <option value="course-asc" className="bg-ink-900 text-slate-200">Course Code (A-Z)</option>
              <option value="grade-asc" className="bg-ink-900 text-slate-200">Grade (A-Z)</option>
              <option value="date-desc" className="bg-ink-900 text-slate-200">Matriculation (Newest)</option>
            </select>
          </div>

          {isLoading && (
            <div className="flex items-center gap-1 text-[11px] font-mono text-cobalt-400 animate-pulse ml-1">
              <Loader2 className="w-3.5 h-3.5 animate-spin" />
              <span>Querying...</span>
            </div>
          )}
        </div>

        <button
          onClick={() => {
            if (!canMutate) return;
            setFormEnrollment(null);
            setIsFormOpen(true);
          }}
          disabled={!canMutate}
          className={`flex items-center justify-center gap-2 px-4 py-2 rounded-xl text-xs font-bold transition-all whitespace-nowrap ${
            canMutate
              ? 'bg-gradient-to-r from-cobalt-600 to-cobalt-500 hover:from-cobalt-500 hover:to-cobalt-400 text-white shadow-[0_0_15px_-3px_rgba(59,130,246,0.3)] cursor-pointer'
              : 'bg-ink-800 text-slate-500 border border-ink-700 opacity-60 cursor-not-allowed'
          }`}
          title={
            canMutate
              ? 'Enroll Scholar in Course'
              : 'Enrollment mutations require ADMIN or INSTRUCTOR privilege (@auth)'
          }
        >
          {canMutate ? <Plus className="w-4 h-4 stroke-[2.5]" /> : <Lock className="w-3.5 h-3.5" />}
          <span>Enroll Scholar</span>
        </button>
      </div>

      {/* Enrollments Table */}
      <div className="rounded-2xl bg-ink-850/90 border border-ink-700/70 overflow-hidden shadow-sm">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs">
            <thead>
              <tr className="bg-ink-900/80 border-b border-ink-750/70 text-slate-400 font-medium">
                <th
                  className="py-3 px-5 font-mono text-[11px] cursor-pointer hover:text-white transition-colors"
                  onClick={() => toggleSort('id')}
                >
                  <div className="flex items-center gap-1.5">
                    <span>Reg #</span>
                    <ArrowUpDown className="w-3 h-3" />
                  </div>
                </th>
                <th
                  className="py-3 px-5 cursor-pointer hover:text-white transition-colors"
                  onClick={() => toggleSort('student')}
                >
                  <div className="flex items-center gap-1.5">
                    <span>Enrolled Scholar</span>
                    <ArrowUpDown className="w-3 h-3" />
                  </div>
                </th>
                <th
                  className="py-3 px-5 cursor-pointer hover:text-white transition-colors"
                  onClick={() => toggleSort('course')}
                >
                  <div className="flex items-center gap-1.5">
                    <span>Curriculum Course</span>
                    <ArrowUpDown className="w-3 h-3" />
                  </div>
                </th>
                <th
                  className="py-3 px-5 cursor-pointer hover:text-white transition-colors"
                  onClick={() => toggleSort('grade')}
                >
                  <div className="flex items-center gap-1.5">
                    <span>Gradebook Record (@auth)</span>
                    <ArrowUpDown className="w-3 h-3" />
                  </div>
                </th>
                <th
                  className="py-3 px-5 cursor-pointer hover:text-white transition-colors"
                  onClick={() => toggleSort('date')}
                >
                  <div className="flex items-center gap-1.5">
                    <span>Matriculation Date</span>
                    <ArrowUpDown className="w-3 h-3" />
                  </div>
                </th>
                <th className="py-3 px-5 text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-ink-750/50">
              {enrollments.length === 0 ? (
                <tr>
                  <td colSpan={6} className="py-12 text-center text-slate-400 text-xs">
                    {isLoading ? 'Loading records...' : 'No academic enrollment records found matching query criteria.'}
                  </td>
                </tr>
              ) : (
                enrollments.map((enr) => {
                  const student =
                    enr.student || students.find((s) => s.id === enr.studentId);
                  const course =
                    enr.course || courses.find((c) => c.id === enr.courseId);

                  return (
                    <tr
                      key={enr.id}
                      className="hover:bg-ink-800/50 transition-colors group"
                    >
                      <td className="py-3.5 px-5 font-mono text-[11px] text-slate-400">
                        #{enr.id}
                      </td>
                      <td className="py-3.5 px-5">
                        <div className="flex items-center gap-2.5">
                          <div className="w-7 h-7 rounded-lg bg-ink-800 border border-ink-700 text-gold-400 flex items-center justify-center font-bold text-xs shrink-0">
                            {student?.name ? student.name.charAt(0) : 'S'}
                          </div>
                          <div>
                            <span className="font-semibold text-white group-hover:text-gold-300 transition-colors">
                              {student?.name || `Scholar #${enr.studentId}`}
                            </span>
                            <p className="text-[11px] text-slate-400 font-mono">{student?.email}</p>
                          </div>
                        </div>
                      </td>
                      <td className="py-3.5 px-5">
                        <div className="flex items-center gap-2">
                          <span className="font-mono text-xs font-semibold px-2 py-0.5 rounded-md bg-cobalt-500/15 text-cobalt-300 border border-cobalt-500/30">
                            {course?.code || `C#${enr.courseId}`}
                          </span>
                          <span className="text-xs text-slate-200">
                            {course?.title || 'Course Offering'}
                          </span>
                        </div>
                      </td>
                      <td className="py-3.5 px-5">
                        {/* Protected Grade Field: Masks for STUDENT and GUEST */}
                        <ProtectedField
                          currentRole={currentRole}
                          requiredRoles={['ADMIN', 'INSTRUCTOR']}
                          fallbackText="Restricted Grade"
                          value={
                            <div className="flex items-center gap-2">
                              <Badge
                                variant={
                                  enr.grade?.startsWith('A')
                                    ? 'laurel'
                                    : enr.grade?.startsWith('B')
                                    ? 'brand'
                                    : 'warning'
                                }
                                size="sm"
                              >
                                {enr.grade || 'In Progress'}
                              </Badge>
                              {enr.gradePoints != null && (
                                <span className="font-mono text-xs text-slate-400 tabular-nums">
                                  ({enr.gradePoints.toFixed(1)} pts)
                                </span>
                              )}
                            </div>
                          }
                        />
                      </td>
                      <td className="py-3.5 px-5 font-mono text-xs text-slate-400">
                        <div className="flex items-center gap-1.5">
                          <Calendar className="w-3.5 h-3.5 text-slate-400" />
                          <span>{enr.enrollmentDate}</span>
                        </div>
                      </td>
                      <td className="py-3.5 px-5 text-right">
                        <div className="flex items-center justify-end gap-1">
                          <button
                            onClick={() => {
                              if (!canMutate) return;
                              setFormEnrollment(enr);
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
                                ? 'Edit Grade & Registration'
                                : 'Modifying grades requires ADMIN or INSTRUCTOR privilege (@auth)'
                            }
                          >
                            <Edit2 className="w-4 h-4" />
                          </button>
                          <button
                            onClick={() => {
                              if (!canMutate) return;
                              setDeleteId(enr.id);
                            }}
                            disabled={!canMutate}
                            className={`p-1.5 rounded-lg transition-colors ${
                              canMutate
                                ? 'text-slate-400 hover:text-garnet-400 hover:bg-ink-750 cursor-pointer'
                                : 'text-slate-600 opacity-40 cursor-not-allowed'
                            }`}
                            title={
                              canMutate
                                ? 'Drop Enrollment'
                                : 'Dropping enrollments requires ADMIN or INSTRUCTOR privilege (@auth)'
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
      </div>

      {/* Pagination Footer */}
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
        pageSizeOptions={[5, 8, 15, 25, 50]}
        itemLabel="enrollments"
      />

      {/* Enrollment Edit/Create Form Modal */}
      {isFormOpen && (
        <EnrollmentFormModal
          isOpen={isFormOpen}
          enrollment={formEnrollment}
          students={students}
          courses={courses}
          onClose={() => setIsFormOpen(false)}
          onSubmit={handleFormSubmit}
        />
      )}

      {/* Delete Confirmation Modal */}
      {deleteId && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm animate-fade-in">
          <div className="bg-ink-850 border border-ink-700 rounded-2xl max-w-sm w-full p-6 space-y-4 shadow-xl">
            <h3 className="text-sm font-bold text-white">Drop Academic Enrollment?</h3>
            <p className="text-xs text-slate-300">
              Are you sure you want to drop enrollment record #{deleteId}? This will remove the scholar from the course gradebook.
            </p>
            <div className="flex justify-end gap-2 pt-2">
              <button
                onClick={() => setDeleteId(null)}
                className="px-3 py-1.5 text-xs text-slate-400 hover:text-white rounded-lg transition-colors cursor-pointer"
              >
                Cancel
              </button>
              <button
                onClick={async () => {
                  await onDeleteEnrollment(deleteId);
                  setDeleteId(null);
                  await fetchEnrollments();
                }}
                className="px-3.5 py-1.5 text-xs font-semibold bg-garnet-500 hover:bg-garnet-600 text-white rounded-lg shadow-sm transition-colors cursor-pointer"
              >
                Confirm Drop
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
