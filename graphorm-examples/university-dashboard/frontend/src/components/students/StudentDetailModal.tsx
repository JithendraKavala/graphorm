import React from 'react';
import { Modal } from '../common/Modal';
import { Badge } from '../common/Badge';
import { Student } from '../../types/graphorm';
import { Mail, Building2, BookOpen, Award, GraduationCap, Edit2 } from 'lucide-react';

interface StudentDetailModalProps {
  student: Student | null;
  isOpen: boolean;
  onClose: () => void;
  onEdit: (student: Student) => void;
}

export const StudentDetailModal: React.FC<StudentDetailModalProps> = ({
  student,
  isOpen,
  onClose,
  onEdit,
}) => {
  if (!student) return null;

  const enrollments = student.enrollments || [];
  const gpa = student.gpa ?? 3.5;
  const totalCredits = enrollments.reduce((sum, e) => sum + (e.course?.credits || 3), 0);

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title="Scholar Dossier & Academic Transcript"
      subtitle={`Official Student Ledger Record #${student.id} · Type-Safe GraphORM Projection`}
      badge={`ID #${student.id}`}
      maxWidth="xl"
    >
      <div className="space-y-5">
        {/* Profile Dossier Header */}
        <div className="p-5 rounded-2xl bg-gradient-to-r from-ink-900 via-ink-850 to-ink-900 border border-ink-750/80 flex flex-col sm:flex-row sm:items-center justify-between gap-4">
          <div className="flex items-center gap-4">
            <div className="w-14 h-14 rounded-2xl bg-gradient-to-br from-gold-500/20 via-ink-800 to-cobalt-500/20 border border-gold-500/40 flex items-center justify-center text-gold-300 font-display font-bold text-xl shadow-[0_0_15px_-3px_rgba(212,175,55,0.2)]">
              {student.name.split(' ').map((n) => n[0]).join('')}
            </div>
            <div>
              <h4 className="font-display font-bold text-lg text-white tracking-wide">
                {student.name}
              </h4>
              <div className="flex items-center gap-2 mt-1 text-xs text-slate-400 font-mono">
                <Mail className="w-3.5 h-3.5 text-slate-400" />
                <span>{student.email}</span>
              </div>
            </div>
          </div>

          <div>
            <Badge
              variant={gpa >= 3.8 ? 'laurel' : gpa >= 3.5 ? 'brand' : 'warning'}
              size="lg"
            >
              {gpa >= 3.8 && <Award className="w-3.5 h-3.5 text-laurel-400" />}
              <span>{gpa >= 3.8 ? 'Dean’s Honor Roll' : 'Matriculated Scholar'}</span>
            </Badge>
          </div>
        </div>

        {/* Academic Performance Strip */}
        <div className="grid grid-cols-3 gap-3">
          <div className="p-3.5 rounded-xl bg-ink-900/80 border border-ink-750 text-center">
            <span className="text-[11px] text-slate-400 font-mono">Cumulative GPA</span>
            <div className="font-display text-2xl font-bold text-gold-300 mt-0.5 tabular-nums">
              {gpa.toFixed(2)}
            </div>
          </div>
          <div className="p-3.5 rounded-xl bg-ink-900/80 border border-ink-750 text-center">
            <span className="text-[11px] text-slate-400 font-mono">Curriculum Units</span>
            <div className="font-display text-2xl font-bold text-cobalt-300 mt-0.5 tabular-nums">
              {enrollments.length}
            </div>
          </div>
          <div className="p-3.5 rounded-xl bg-ink-900/80 border border-ink-750 text-center">
            <span className="text-[11px] text-slate-400 font-mono">Credits Earned</span>
            <div className="font-display text-2xl font-bold text-laurel-300 mt-0.5 tabular-nums">
              {totalCredits}
            </div>
          </div>
        </div>

        {/* Department Info */}
        <div className="p-4 rounded-xl bg-ink-900/60 border border-ink-750 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <Building2 className="w-5 h-5 text-gold-400" />
            <div>
              <p className="text-xs font-semibold text-white">
                {student.department?.name || 'Independent / General Studies Faculty'}
              </p>
              <p className="text-[11px] text-slate-400 font-mono">
                Code: {student.department?.code || 'INDEP'} · Department Model ID #{student.departmentId || 'None'}
              </p>
            </div>
          </div>
          <Badge variant="sky" size="sm">
            Major Program
          </Badge>
        </div>

        {/* Enrolled Courses & Grades */}
        <div>
          <div className="flex items-center justify-between mb-2.5">
            <h5 className="text-xs font-semibold uppercase tracking-wider text-slate-400 font-mono">
              Course Registrations & Conferred Grades
            </h5>
            <span className="text-[10px] text-slate-400 font-mono">GraphORM Nested Rel</span>
          </div>

          {enrollments.length === 0 ? (
            <div className="p-6 text-center rounded-xl bg-ink-900/40 border border-ink-750 text-xs text-slate-400">
              No active course registrations on record for this scholar.
            </div>
          ) : (
            <div className="space-y-2 max-h-56 overflow-y-auto pr-1">
              {enrollments.map((e) => (
                <div
                  key={e.id}
                  className="p-3 rounded-xl bg-ink-900/70 border border-ink-750 flex items-center justify-between"
                >
                  <div className="flex items-center gap-3">
                    <BookOpen className="w-4 h-4 text-cobalt-400" />
                    <div>
                      <p className="text-xs font-semibold text-slate-200">
                        {e.course?.code || `Course #${e.courseId}`}: {e.course?.title || 'Curriculum Course'}
                      </p>
                      <p className="text-[11px] text-slate-400 font-mono">
                        {e.course?.credits || 3} Credits · Registered: {e.enrollmentDate}
                      </p>
                    </div>
                  </div>
                  <div className="flex items-center gap-3">
                    <span className="text-xs font-mono text-slate-400 tabular-nums">
                      {e.gradePoints != null ? `${e.gradePoints.toFixed(1)} pts` : '--'}
                    </span>
                    <Badge variant={e.grade?.startsWith('A') ? 'laurel' : 'gold'} size="sm">
                      {e.grade || 'In Progress'}
                    </Badge>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Modal Actions */}
        <div className="pt-4 border-t border-ink-750/70 flex items-center justify-between">
          <button
            onClick={() => {
              onClose();
              onEdit(student);
            }}
            className="flex items-center gap-1.5 px-4 py-2 rounded-xl bg-ink-800 hover:bg-ink-750 text-slate-200 text-xs font-semibold transition-colors cursor-pointer"
          >
            <Edit2 className="w-3.5 h-3.5" />
            <span>Edit Scholar</span>
          </button>
          <button
            onClick={onClose}
            className="px-5 py-2 rounded-xl bg-gold-600 hover:bg-gold-500 text-ink-950 font-bold text-xs transition-colors cursor-pointer"
          >
            Dismiss Dossier
          </button>
        </div>
      </div>
    </Modal>
  );
};
