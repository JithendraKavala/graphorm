import React, { useState, useEffect } from 'react';
import { Modal } from '../common/Modal';
import { Department, CreateDepartmentInput, UpdateDepartmentInput } from '../../types/graphorm';

interface DepartmentFormModalProps {
  isOpen: boolean;
  onClose: () => void;
  department: Department | null;
  onSubmit: (input: CreateDepartmentInput | UpdateDepartmentInput, id?: number) => Promise<void>;
}

export const DepartmentFormModal: React.FC<DepartmentFormModalProps> = ({
  isOpen,
  onClose,
  department,
  onSubmit,
}) => {
  const [name, setName] = useState('');
  const [code, setCode] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (department) {
      setName(department.name);
      setCode(department.code);
    } else {
      setName('');
      setCode('');
    }
    setError(null);
  }, [department, isOpen]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!name.trim() || !code.trim()) {
      setError('Please provide department name and academic abbreviation code.');
      return;
    }

    setIsSubmitting(true);
    setError(null);
    try {
      await onSubmit({ name: name.trim(), code: code.trim().toUpperCase() }, department?.id);
      onClose();
    } catch (err: any) {
      setError(err?.message || 'Failed to record department.');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title={department ? 'Modify Academic Faculty' : 'Charter Academic Faculty'}
      subtitle="Defines faculty division in GraphORM schema metadata"
      badge={department ? `ID #${department.id}` : 'Charter'}
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
            Faculty Department Name <span className="text-gold-400">*</span>
          </label>
          <input
            type="text"
            required
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="e.g. Electrical & Computer Engineering"
            className="w-full px-3.5 py-2 text-xs bg-ink-900 border border-ink-700 rounded-xl text-slate-100 placeholder-slate-500 focus:outline-none focus:border-gold-500/60 focus:ring-1 focus:ring-gold-500/25 transition-all"
          />
        </div>

        <div>
          <label className="block text-xs font-semibold text-slate-300 mb-1.5 font-mono">
            Division Code (Acronym) <span className="text-gold-400">*</span>
          </label>
          <input
            type="text"
            required
            maxLength={10}
            value={code}
            onChange={(e) => setCode(e.target.value.toUpperCase())}
            placeholder="e.g. ECE"
            className="w-full px-3.5 py-2 text-xs font-mono uppercase bg-ink-900 border border-ink-700 rounded-xl text-slate-100 placeholder-slate-500 focus:outline-none focus:border-gold-500/60 focus:ring-1 focus:ring-gold-500/25 transition-all"
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
            className="px-5 py-2 rounded-xl bg-gold-600 hover:bg-gold-500 disabled:opacity-50 text-ink-950 font-bold text-xs shadow-sm transition-all cursor-pointer"
          >
            {isSubmitting ? 'Recording...' : department ? 'Save Changes' : 'Charter Faculty'}
          </button>
        </div>
      </form>
    </Modal>
  );
};
