import React, { useState, useEffect } from 'react';
import { Modal } from '../common/Modal';
import { Course, Department, Instructor, CreateCourseInput, UpdateCourseInput } from '../../types/graphorm';

interface CourseFormModalProps {
  isOpen: boolean;
  onClose: () => void;
  course: Course | null;
  departments: Department[];
  instructors: Instructor[];
  onSubmit: (input: CreateCourseInput | UpdateCourseInput, id?: number) => Promise<void>;
}

export const CourseFormModal: React.FC<CourseFormModalProps> = ({
  isOpen,
  onClose,
  course,
  departments,
  instructors,
  onSubmit,
}) => {
  const [title, setTitle] = useState('');
  const [code, setCode] = useState('');
  const [credits, setCredits] = useState<number>(4);
  const [departmentId, setDepartmentId] = useState<number>(1);
  const [instructorId, setInstructorId] = useState<number | ''>('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (course) {
      setTitle(course.title);
      setCode(course.code);
      setCredits(course.credits);
      setDepartmentId(course.departmentId);
      setInstructorId(course.instructorId ?? '');
    } else {
      setTitle('');
      setCode('');
      setCredits(4);
      setDepartmentId(departments[0]?.id || 1);
      setInstructorId('');
    }
    setError(null);
  }, [course, isOpen, departments]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!title.trim() || !code.trim()) {
      setError('Please provide course title and course catalog code.');
      return;
    }

    setIsSubmitting(true);
    setError(null);
    try {
      const payload: CreateCourseInput = {
        title: title.trim(),
        code: code.trim().toUpperCase(),
        credits: Number(credits),
        departmentId: Number(departmentId),
        instructorId: instructorId === '' ? undefined : Number(instructorId),
      };
      await onSubmit(payload, course?.id);
      onClose();
    } catch (err: any) {
      setError(err?.message || 'Failed to record course.');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title={course ? 'Modify Course Offering' : 'Register Curriculum Offering'}
      subtitle="Defines Course model instance in GraphORM database"
      badge={course ? `ID #${course.id}` : 'Curriculum'}
      maxWidth="md"
    >
      <form onSubmit={handleSubmit} className="space-y-4">
        {error && (
          <div className="p-3 rounded-xl bg-garnet-500/10 border border-garnet-500/20 text-garnet-300 text-xs font-medium">
            {error}
          </div>
        )}

        <div>
          <label className="block text-xs font-semibold text-slate-300 mb-1.5 font-mono">
            Course Title <span className="text-gold-400">*</span>
          </label>
          <input
            type="text"
            required
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            placeholder="e.g. Distributed Database Engine Architecture"
            className="w-full px-3.5 py-2 text-xs bg-ink-900 border border-ink-700 rounded-xl text-slate-100 placeholder-slate-500 focus:outline-none focus:border-cobalt-500/60 focus:ring-1 focus:ring-cobalt-500/25 transition-all"
          />
        </div>

        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className="block text-xs font-semibold text-slate-300 mb-1.5 font-mono">
              Course Code <span className="text-gold-400">*</span>
            </label>
            <input
              type="text"
              required
              value={code}
              onChange={(e) => setCode(e.target.value.toUpperCase())}
              placeholder="e.g. CS450"
              className="w-full px-3.5 py-2 text-xs font-mono uppercase bg-ink-900 border border-ink-700 rounded-xl text-slate-100 placeholder-slate-500 focus:outline-none focus:border-cobalt-500/60 focus:ring-1 focus:ring-cobalt-500/25 transition-all"
            />
          </div>

          <div>
            <label className="block text-xs font-semibold text-slate-300 mb-1.5 font-mono">
              Credit Units <span className="text-gold-400">*</span>
            </label>
            <select
              value={credits}
              onChange={(e) => setCredits(Number(e.target.value))}
              className="w-full px-3.5 py-2 text-xs bg-ink-900 border border-ink-700 rounded-xl text-slate-100 focus:outline-none focus:border-cobalt-500/60 cursor-pointer"
            >
              <option value={1}>1 Unit (Lab)</option>
              <option value={2}>2 Units (Seminar)</option>
              <option value={3}>3 Units (Lecture)</option>
              <option value={4}>4 Units (Comprehensive)</option>
              <option value={5}>5 Units (Advanced Research)</option>
            </select>
          </div>
        </div>

        <div>
          <label className="block text-xs font-semibold text-slate-300 mb-1.5 font-mono">
            Host Academic Faculty <span className="text-gold-400">*</span>
          </label>
          <select
            value={departmentId}
            onChange={(e) => setDepartmentId(Number(e.target.value))}
            className="w-full px-3.5 py-2 text-xs bg-ink-900 border border-ink-700 rounded-xl text-slate-100 focus:outline-none focus:border-cobalt-500/60 cursor-pointer"
          >
            {departments.map((d) => (
              <option key={d.id} value={d.id}>
                {d.name} ({d.code})
              </option>
            ))}
          </select>
        </div>

        <div>
          <label className="block text-xs font-semibold text-slate-300 mb-1.5 font-mono">
            Faculty Professor
          </label>
          <select
            value={instructorId}
            onChange={(e) => setInstructorId(e.target.value ? Number(e.target.value) : '')}
            className="w-full px-3.5 py-2 text-xs bg-ink-900 border border-ink-700 rounded-xl text-slate-100 focus:outline-none focus:border-cobalt-500/60 cursor-pointer"
          >
            <option value="">-- Faculty Chair Unassigned / TBA --</option>
            {instructors.map((i) => (
              <option key={i.id} value={i.id}>
                {i.name} ({i.email})
              </option>
            ))}
          </select>
        </div>

        <div className="pt-4 border-t border-ink-750/70 flex items-center justify-end gap-3">
          <button
            type="button"
            onClick={onClose}
            className="px-4 py-2 rounded-xl bg-ink-800 hover:bg-ink-750 text-slate-300 text-xs font-semibold transition-colors cursor-pointer"
          >
            Cancel
          </button>
          <button
            type="submit"
            disabled={isSubmitting}
            className="px-5 py-2 rounded-xl bg-cobalt-600 hover:bg-cobalt-500 disabled:opacity-50 text-white font-bold text-xs shadow-sm transition-all cursor-pointer"
          >
            {isSubmitting ? 'Recording...' : course ? 'Save Changes' : 'Confirm Offering'}
          </button>
        </div>
      </form>
    </Modal>
  );
};
