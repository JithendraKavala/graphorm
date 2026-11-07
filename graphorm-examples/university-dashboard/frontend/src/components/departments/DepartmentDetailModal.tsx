import React from 'react';
import { Modal } from '../common/Modal';
import { Badge } from '../common/Badge';
import { Department } from '../../types/graphorm';
import { Building2, Users, BookOpen, GraduationCap, UserCheck, Shield } from 'lucide-react';

interface DepartmentDetailModalProps {
  department: Department | null;
  isOpen: boolean;
  onClose: () => void;
  onEdit: (department: Department) => void;
}

export const DepartmentDetailModal: React.FC<DepartmentDetailModalProps> = ({
  department,
  isOpen,
  onClose,
  onEdit,
}) => {
  if (!department) return null;

  const students = department.students || [];
  const courses = department.courses || [];
  const instructors = department.instructors || [];

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title="Academic Faculty Specification"
      subtitle={`Faculty Division Code: ${department.code} · GraphORM Model: Department`}
      badge={`Dept #${department.id}`}
      maxWidth="xl"
    >
      <div className="space-y-5">
        {/* Banner */}
        <div className="p-5 rounded-2xl bg-gradient-to-r from-ink-900 via-ink-850 to-ink-900 border border-ink-750/80 flex items-center justify-between">
          <div className="flex items-center gap-4">
            <div className="w-13 h-13 rounded-2xl bg-ink-800 border border-gold-500/40 text-gold-300 flex items-center justify-center font-display font-bold text-lg shadow-[0_0_15px_-3px_rgba(212,175,55,0.2)]">
              {department.code}
            </div>
            <div>
              <h4 className="font-display font-bold text-lg text-white">
                {department.name}
              </h4>
              <p className="text-xs text-slate-400 font-mono mt-0.5">
                Primary Division #{department.id} · Table: departments
              </p>
            </div>
          </div>
          <Badge variant="gold" size="lg">
            Active Faculty Chair
          </Badge>
        </div>

        {/* Capacity Metrics */}
        <div className="grid grid-cols-3 gap-3">
          <div className="p-3.5 rounded-xl bg-ink-900/80 border border-ink-750 text-center">
            <span className="text-[11px] text-slate-400 font-mono">Scholars Enrolled</span>
            <div className="font-display text-2xl font-bold text-gold-300 mt-0.5 tabular-nums">
              {students.length}
            </div>
          </div>
          <div className="p-3.5 rounded-xl bg-ink-900/80 border border-ink-750 text-center">
            <span className="text-[11px] text-slate-400 font-mono">Curriculum Courses</span>
            <div className="font-display text-2xl font-bold text-cobalt-300 mt-0.5 tabular-nums">
              {courses.length}
            </div>
          </div>
          <div className="p-3.5 rounded-xl bg-ink-900/80 border border-ink-750 text-center">
            <span className="text-[11px] text-slate-400 font-mono">Faculty Professors</span>
            <div className="font-display text-2xl font-bold text-laurel-300 mt-0.5 tabular-nums">
              {instructors.length}
            </div>
          </div>
        </div>

        {/* Faculty Instructors with @auth demo */}
        <div>
          <div className="flex items-center justify-between mb-2.5">
            <h5 className="text-xs font-semibold uppercase tracking-wider text-slate-400 font-mono">
              Faculty Professors & Academic Staff
            </h5>
            <div className="flex items-center gap-1 text-[10px] text-gold-400 font-mono">
              <Shield className="w-3 h-3" />
              <span>@auth(roles: ["ADMIN"])</span>
            </div>
          </div>

          {instructors.length === 0 ? (
            <p className="text-xs text-slate-400 italic p-4 rounded-xl bg-ink-900/40 border border-ink-750">
              No faculty professors assigned to this department yet.
            </p>
          ) : (
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-2.5">
              {instructors.map((inst) => (
                <div
                  key={inst.id}
                  className="p-3 rounded-xl bg-ink-900/80 border border-ink-750 flex items-center justify-between"
                >
                  <div className="flex items-center gap-2.5">
                    <div className="w-7 h-7 rounded-lg bg-ink-800 border border-ink-700 text-gold-400 flex items-center justify-center font-bold text-xs">
                      {inst.name.charAt(0)}
                    </div>
                    <div>
                      <p className="text-xs font-semibold text-slate-200">{inst.name}</p>
                      <p className="text-[11px] text-slate-400 font-mono">{inst.email}</p>
                    </div>
                  </div>
                  {inst.salary && (
                    <span className="text-[11px] font-mono text-laurel-300 font-semibold px-2 py-0.5 rounded bg-laurel-500/10 border border-laurel-500/20">
                      ${inst.salary.toLocaleString()}
                    </span>
                  )}
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Courses List */}
        <div>
          <h5 className="text-xs font-semibold uppercase tracking-wider text-slate-400 mb-2.5 font-mono">
            Active Curriculum Syllabus
          </h5>
          {courses.length === 0 ? (
            <p className="text-xs text-slate-400 italic p-4 rounded-xl bg-ink-900/40 border border-ink-750">
              No curriculum offerings scheduled under this faculty.
            </p>
          ) : (
            <div className="space-y-2 max-h-48 overflow-y-auto pr-1">
              {courses.map((c) => (
                <div
                  key={c.id}
                  className="p-3 rounded-xl bg-ink-900/70 border border-ink-750 flex items-center justify-between"
                >
                  <div className="flex items-center gap-2.5">
                    <BookOpen className="w-4 h-4 text-cobalt-400" />
                    <div>
                      <span className="text-xs font-semibold text-slate-200">
                        {c.code}: {c.title}
                      </span>
                      <p className="text-[11px] text-slate-400 font-mono">{c.credits} Credit Units</p>
                    </div>
                  </div>
                  <Badge variant="sky" size="sm">
                    {c.instructor?.name || 'Staff Faculty'}
                  </Badge>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Footer */}
        <div className="pt-4 border-t border-ink-750/70 flex items-center justify-between">
          <button
            onClick={() => {
              onClose();
              onEdit(department);
            }}
            className="px-4 py-2 rounded-xl bg-ink-800 hover:bg-ink-750 text-slate-200 text-xs font-semibold transition-colors cursor-pointer"
          >
            Edit Faculty
          </button>
          <button
            onClick={onClose}
            className="px-5 py-2 rounded-xl bg-gold-600 hover:bg-gold-500 text-ink-950 font-bold text-xs transition-colors cursor-pointer"
          >
            Close Details
          </button>
        </div>
      </div>
    </Modal>
  );
};
