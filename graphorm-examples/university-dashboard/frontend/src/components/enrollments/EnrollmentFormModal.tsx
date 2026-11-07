import React, { useState, useEffect } from 'react';
import { Modal } from '../common/Modal';
import {
  Enrollment,
  Student,
  Course,
  CreateEnrollmentInput,
  UpdateEnrollmentInput,
} from '../../types/graphorm';

interface EnrollmentFormModalProps {
  isOpen: boolean;
  onClose: () => void;
  enrollment: Enrollment | null;
  students: Student[];
  courses: Course[];
  onSubmit: (input: CreateEnrollmentInput | UpdateEnrollmentInput, id?: number) => Promise<void>;
}

export const EnrollmentFormModal: React.FC<EnrollmentFormModalProps> = ({
  isOpen,
  onClose,
  enrollment,
  students,
  courses,
  onSubmit,
}) => {
  const [studentId, setStudentId] = useState<number>(1);
  const [courseId, setCourseId] = useState<number>(1);
  const [grade, setGrade] = useState<string>('A');
  const [gradePoints, setGradePoints] = useState<number>(4.0);
  const [enrollmentDate, setEnrollmentDate] = useState<string>(
    new Date().toISOString().split('T')[0]
  );
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (enrollment) {
      setStudentId(enrollment.studentId);
      setCourseId(enrollment.courseId);
      setGrade(enrollment.grade || 'In Progress');
      setGradePoints(enrollment.gradePoints ?? 4.0);
      setEnrollmentDate(enrollment.enrollmentDate);
    } else {
      setStudentId(students[0]?.id || 1);
      setCourseId(courses[0]?.id || 1);
      setGrade('A');
      setGradePoints(4.0);
      setEnrollmentDate(new Date().toISOString().split('T')[0]);
    }
    setError(null);
  }, [enrollment, isOpen, students, courses]);

  const handleGradeChange = (newGrade: string) => {
    setGrade(newGrade);
    switch (newGrade) {
      case 'A+':
      case 'A':
        setGradePoints(4.0);
        break;
      case 'A-':
        setGradePoints(3.7);
        break;
      case 'B+':
        setGradePoints(3.3);
        break;
      case 'B':
        setGradePoints(3.0);
        break;
      case 'B-':
        setGradePoints(2.7);
        break;
      case 'C+':
        setGradePoints(2.3);
        break;
      case 'C':
        setGradePoints(2.0);
        break;
      default:
        setGradePoints(0.0);
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsSubmitting(true);
    setError(null);
    try {
      const payload: CreateEnrollmentInput = {
        studentId: Number(studentId),
        courseId: Number(courseId),
        grade,
        gradePoints: Number(gradePoints),
        enrollmentDate,
      };
      await onSubmit(payload, enrollment?.id);
      onClose();
    } catch (err: any) {
      setError(err?.message || 'Failed to record enrollment.');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title={enrollment ? 'Modify Enrollment & Merit Points' : 'Register Course Enrollment'}
      subtitle="GraphORM Model: Enrollment (Relational Join between Student and Course)"
      badge={enrollment ? `Reg #${enrollment.id}` : 'Registrar'}
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
            Scholar <span className="text-gold-400">*</span>
          </label>
          <select
            value={studentId}
            onChange={(e) => setStudentId(Number(e.target.value))}
            className="w-full px-3.5 py-2 text-xs bg-ink-900 border border-ink-700 rounded-xl text-slate-100 focus:outline-none focus:border-laurel-500/60 cursor-pointer"
          >
            {students.map((s) => (
              <option key={s.id} value={s.id}>
                {s.name} ({s.email})
              </option>
            ))}
          </select>
        </div>

        <div>
          <label className="block text-xs font-semibold text-slate-300 mb-1.5 font-mono">
            Curriculum Course Offering <span className="text-gold-400">*</span>
          </label>
          <select
            value={courseId}
            onChange={(e) => setCourseId(Number(e.target.value))}
            className="w-full px-3.5 py-2 text-xs bg-ink-900 border border-ink-700 rounded-xl text-slate-100 focus:outline-none focus:border-laurel-500/60 cursor-pointer"
          >
            {courses.map((c) => (
              <option key={c.id} value={c.id}>
                {c.code} - {c.title} ({c.credits} Units)
              </option>
            ))}
          </select>
        </div>

        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className="block text-xs font-semibold text-slate-300 mb-1.5 font-mono">
              Grade Status
            </label>
            <select
              value={grade}
              onChange={(e) => handleGradeChange(e.target.value)}
              className="w-full px-3.5 py-2 text-xs bg-ink-900 border border-ink-700 rounded-xl text-slate-100 focus:outline-none focus:border-laurel-500/60 cursor-pointer"
            >
              <option value="A">A (4.0 Honor)</option>
              <option value="A-">A- (3.7 High Merit)</option>
              <option value="B+">B+ (3.3 Merit)</option>
              <option value="B">B (3.0 Standard)</option>
              <option value="B-">B- (2.7 Above Passing)</option>
              <option value="C+">C+ (2.3 Passing)</option>
              <option value="C">C (2.0 Low Passing)</option>
              <option value="In Progress">In Progress (Active Term)</option>
            </select>
          </div>

          <div>
            <label className="block text-xs font-semibold text-slate-300 mb-1.5 font-mono">
              Grade Points
            </label>
            <input
              type="number"
              step="0.1"
              value={gradePoints}
              onChange={(e) => setGradePoints(parseFloat(e.target.value))}
              className="w-full px-3.5 py-2 text-xs font-mono bg-ink-900 border border-ink-700 rounded-xl text-slate-100 focus:outline-none focus:border-laurel-500/60"
            />
          </div>
        </div>

        <div>
          <label className="block text-xs font-semibold text-slate-300 mb-1.5 font-mono">
            Registration Date
          </label>
          <input
            type="date"
            value={enrollmentDate}
            onChange={(e) => setEnrollmentDate(e.target.value)}
            className="w-full px-3.5 py-2 text-xs font-mono bg-ink-900 border border-ink-700 rounded-xl text-slate-100 focus:outline-none focus:border-laurel-500/60"
          />
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
            className="px-5 py-2 rounded-xl bg-laurel-600 hover:bg-laurel-500 disabled:opacity-50 text-ink-950 font-bold text-xs shadow-sm transition-all cursor-pointer"
          >
            {isSubmitting ? 'Recording...' : enrollment ? 'Save Merit Record' : 'Confirm Registration'}
          </button>
        </div>
      </form>
    </Modal>
  );
};
