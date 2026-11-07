import React, { useState, useMemo } from 'react';
import {
  Enrollment,
  Student,
  Course,
  CreateEnrollmentInput,
  UpdateEnrollmentInput,
} from '../../types/graphorm';
import { SearchInput } from '../common/SearchInput';
import { Badge } from '../common/Badge';
import {
  GraduationCap,
  Plus,
  Edit2,
  Trash2,
  Filter,
  Calendar,
  User,
  BookOpen,
} from 'lucide-react';
import { EnrollmentFormModal } from './EnrollmentFormModal';

interface EnrollmentListProps {
  enrollments: Enrollment[];
  students: Student[];
  courses: Course[];
  onCreateEnrollment: (input: CreateEnrollmentInput) => Promise<Enrollment>;
  onUpdateEnrollment: (id: number, input: UpdateEnrollmentInput) => Promise<Enrollment>;
  onDeleteEnrollment: (id: number) => Promise<boolean>;
}

export const EnrollmentList: React.FC<EnrollmentListProps> = ({
  enrollments,
  students,
  courses,
  onCreateEnrollment,
  onUpdateEnrollment,
  onDeleteEnrollment,
}) => {
  const [search, setSearch] = useState('');
  const [selectedCourseId, setSelectedCourseId] = useState<string>('all');
  const [formEnrollment, setFormEnrollment] = useState<Enrollment | null>(null);
  const [isFormOpen, setIsFormOpen] = useState(false);
  const [deleteId, setDeleteId] = useState<number | null>(null);

  const filtered = useMemo(() => {
    return enrollments.filter((e) => {
      const studentName = e.student?.name || students.find((s) => s.id === e.studentId)?.name || '';
      const courseTitle = e.course?.title || courses.find((c) => c.id === e.courseId)?.title || '';
      const courseCode = e.course?.code || courses.find((c) => c.id === e.courseId)?.code || '';

      const matchesSearch =
        studentName.toLowerCase().includes(search.toLowerCase()) ||
        courseTitle.toLowerCase().includes(search.toLowerCase()) ||
        courseCode.toLowerCase().includes(search.toLowerCase());

      const matchesCourse =
        selectedCourseId === 'all' ? true : e.courseId === Number(selectedCourseId);

      return matchesSearch && matchesCourse;
    });
  }, [enrollments, students, courses, search, selectedCourseId]);

  const handleFormSubmit = async (
    input: CreateEnrollmentInput | UpdateEnrollmentInput,
    id?: number
  ) => {
    if (id) {
      await onUpdateEnrollment(id, input as UpdateEnrollmentInput);
    } else {
      await onCreateEnrollment(input as CreateEnrollmentInput);
    }
  };

  return (
    <div className="space-y-4">
      {/* Search & Actions */}
      <div className="p-4 rounded-2xl bg-ink-850/90 border border-ink-700/70 shadow-sm flex flex-col sm:flex-row sm:items-center justify-between gap-3">
        <div className="flex-1 flex flex-wrap items-center gap-2.5">
          <SearchInput
            value={search}
            onChange={setSearch}
            placeholder="Search registrar records by scholar or course..."
            className="w-full sm:w-72"
          />

          <div className="flex items-center gap-1.5 bg-ink-900/80 border border-ink-700/70 rounded-xl px-2.5 py-1.5 text-xs">
            <Filter className="w-3.5 h-3.5 text-slate-400" />
            <select
              value={selectedCourseId}
              onChange={(e) => setSelectedCourseId(e.target.value)}
              className="bg-transparent text-slate-200 focus:outline-none cursor-pointer text-xs"
            >
              <option value="all">All Curriculum Courses</option>
              {courses.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.code} - {c.title}
                </option>
              ))}
            </select>
          </div>
        </div>

        <button
          onClick={() => {
            setFormEnrollment(null);
            setIsFormOpen(true);
          }}
          className="flex items-center justify-center gap-2 px-4 py-2 rounded-xl bg-gradient-to-r from-laurel-600 to-laurel-500 hover:from-laurel-500 hover:to-laurel-400 text-ink-950 font-bold text-xs shadow-[0_0_15px_-3px_rgba(16,185,129,0.3)] transition-all cursor-pointer whitespace-nowrap"
        >
          <Plus className="w-4 h-4 stroke-[2.5]" />
          <span>Register Course Enrollment</span>
        </button>
      </div>

      {/* Enrollments Ledger Table */}
      <div className="rounded-2xl bg-ink-850/90 border border-ink-700/70 overflow-hidden shadow-sm">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs text-slate-300">
            <thead className="bg-ink-900/90 border-b border-ink-750/80 text-[10px] uppercase tracking-wider text-slate-400 font-mono select-none">
              <tr>
                <th className="py-3 px-5">Reg ID</th>
                <th className="py-3 px-5">Scholar</th>
                <th className="py-3 px-5">Curriculum Course</th>
                <th className="py-3 px-5">Grade / Merit Points</th>
                <th className="py-3 px-5">Registration Date</th>
                <th className="py-3 px-5 text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-ink-750/50">
              {filtered.length === 0 ? (
                <tr>
                  <td colSpan={6} className="py-12 text-center text-slate-400 text-xs">
                    No academic enrollment records found.
                  </td>
                </tr>
              ) : (
                filtered.map((enr) => {
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
                              setFormEnrollment(enr);
                              setIsFormOpen(true);
                            }}
                            className="p-1.5 text-slate-400 hover:text-cobalt-300 hover:bg-ink-750 rounded-lg transition-colors cursor-pointer"
                            title="Edit Grade & Registration"
                          >
                            <Edit2 className="w-4 h-4" />
                          </button>
                          <button
                            onClick={() => setDeleteId(enr.id)}
                            className="p-1.5 text-slate-400 hover:text-garnet-400 hover:bg-ink-750 rounded-lg transition-colors cursor-pointer"
                            title="Drop Enrollment"
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

      {/* Modal */}
      <EnrollmentFormModal
        isOpen={isFormOpen}
        onClose={() => {
          setIsFormOpen(false);
          setFormEnrollment(null);
        }}
        enrollment={formEnrollment}
        students={students}
        courses={courses}
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
            <h4 className="font-display font-bold text-base text-white">Drop Course Enrollment</h4>
            <p className="text-xs text-slate-400 mt-2 leading-relaxed">
              Are you sure you wish to withdraw enrollment #{deleteId}? The scholar will be deregistered from this course ledger.
            </p>
            <div className="mt-5 flex items-center justify-center gap-3">
              <button
                onClick={() => setDeleteId(null)}
                className="px-4 py-2 rounded-xl bg-ink-800 hover:bg-ink-750 text-slate-300 text-xs font-semibold transition-colors cursor-pointer"
              >
                Cancel
              </button>
              <button
                onClick={async () => {
                  await onDeleteEnrollment(deleteId);
                  setDeleteId(null);
                }}
                className="px-4 py-2 rounded-xl bg-garnet-600 hover:bg-garnet-500 text-white text-xs font-bold transition-colors cursor-pointer"
              >
                Withdraw Enrollment
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
