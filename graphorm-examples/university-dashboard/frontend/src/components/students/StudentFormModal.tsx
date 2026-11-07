import React, { useState, useEffect } from 'react';
import { Modal } from '../common/Modal';
import { Student, Department, CreateStudentInput, UpdateStudentInput } from '../../types/graphorm';

interface StudentFormModalProps {
  isOpen: boolean;
  onClose: () => void;
  student: Student | null;
  departments: Department[];
  onSubmit: (input: CreateStudentInput | UpdateStudentInput, id?: number) => Promise<void>;
}

export const StudentFormModal: React.FC<StudentFormModalProps> = ({
  isOpen,
  onClose,
  student,
  departments,
  onSubmit,
}) => {
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [gpa, setGpa] = useState<number>(3.8);
  const [departmentId, setDepartmentId] = useState<number | ''>('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (student) {
      setName(student.name);
      setEmail(student.email);
      setGpa(student.gpa ?? 3.8);
      setDepartmentId(student.departmentId ?? '');
    } else {
      setName('');
      setEmail('');
      setGpa(3.8);
      setDepartmentId(departments.length > 0 ? departments[0].id : '');
    }
    setError(null);
  }, [student, isOpen, departments]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!name.trim()) {
      setError('Please provide student full legal name.');
      return;
    }
    if (!email.trim() || !email.includes('@')) {
      setError('Please provide a valid institutional email address.');
      return;
    }

    setIsSubmitting(true);
    setError(null);
    try {
      const payload: CreateStudentInput = {
        name: name.trim(),
        email: email.trim(),
        gpa: Number(gpa),
        departmentId: departmentId === '' ? undefined : Number(departmentId),
      };

      await onSubmit(payload, student?.id);
      onClose();
    } catch (err: any) {
      setError(err?.message || 'Failed to save scholar record.');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title={student ? 'Modify Scholar Record' : 'Matriculate New Scholar'}
      subtitle="Creates or updates student model record in GraphORM schema"
      badge={student ? `ID #${student.id}` : 'Admissions'}
      maxWidth="md"
    >
      <form onSubmit={handleSubmit} className="space-y-4">
        {error && (
          <div className="p-3 rounded-xl bg-garnet-500/10 border border-garnet-500/20 text-garnet-300 text-xs font-medium">
            {error}
          </div>
        )}

        {/* Full Name */}
        <div>
          <label className="block text-xs font-semibold text-slate-300 mb-1.5 font-mono">
            Full Legal Name <span className="text-gold-400">*</span>
          </label>
          <input
            type="text"
            required
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="e.g. Katherine Johnson"
            className="w-full px-3.5 py-2 text-xs bg-ink-900 border border-ink-700 rounded-xl text-slate-100 placeholder-slate-500 focus:outline-none focus:border-gold-500/60 focus:ring-1 focus:ring-gold-500/25 transition-all"
          />
        </div>

        {/* University Email */}
        <div>
          <label className="block text-xs font-semibold text-slate-300 mb-1.5 font-mono">
            University Email <span className="text-gold-400">*</span>
          </label>
          <input
            type="email"
            required
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            placeholder="e.g. kjohnson@university.edu"
            className="w-full px-3.5 py-2 text-xs font-mono bg-ink-900 border border-ink-700 rounded-xl text-slate-100 placeholder-slate-500 focus:outline-none focus:border-gold-500/60 focus:ring-1 focus:ring-gold-500/25 transition-all"
          />
        </div>

        {/* Academic Faculty */}
        <div>
          <label className="block text-xs font-semibold text-slate-300 mb-1.5 font-mono">
            Academic Faculty
          </label>
          <select
            value={departmentId}
            onChange={(e) => setDepartmentId(e.target.value ? Number(e.target.value) : '')}
            className="w-full px-3.5 py-2 text-xs bg-ink-900 border border-ink-700 rounded-xl text-slate-100 focus:outline-none focus:border-gold-500/60 focus:ring-1 focus:ring-gold-500/25 transition-all cursor-pointer"
          >
            <option value="">-- Independent Scholar / Undeclared --</option>
            {departments.map((d) => (
              <option key={d.id} value={d.id}>
                {d.name} ({d.code})
              </option>
            ))}
          </select>
        </div>

        {/* GPA Slider */}
        <div className="p-3.5 rounded-xl bg-ink-900/60 border border-ink-750">
          <div className="flex items-center justify-between mb-2">
            <label className="block text-xs font-semibold text-slate-300 font-mono">
              Initial Grade Point Average (GPA)
            </label>
            <span className="text-xs font-mono font-bold text-gold-300 tabular-nums">
              {Number(gpa).toFixed(2)}
            </span>
          </div>
          <input
            type="range"
            min="2.0"
            max="4.0"
            step="0.01"
            value={gpa}
            onChange={(e) => setGpa(parseFloat(e.target.value))}
            className="w-full accent-gold-500 cursor-pointer"
          />
          <div className="flex justify-between text-[10px] text-slate-400 mt-1 font-mono">
            <span>2.00 Passing</span>
            <span className="text-gold-400 font-semibold">3.80 Dean's List</span>
            <span>4.00 Summa</span>
          </div>
        </div>

        {/* Actions */}
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
            className="px-5 py-2 rounded-xl bg-gold-600 hover:bg-gold-500 disabled:opacity-50 text-ink-950 font-bold text-xs shadow-sm transition-all cursor-pointer"
          >
            {isSubmitting ? 'Recording...' : student ? 'Save Changes' : 'Matriculate Scholar'}
          </button>
        </div>
      </form>
    </Modal>
  );
};
