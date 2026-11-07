import React, { useState, useMemo } from 'react';
import {
  Department,
  CreateDepartmentInput,
  UpdateDepartmentInput,
} from '../../types/graphorm';
import { SearchInput } from '../common/SearchInput';
import { Badge } from '../common/Badge';
import {
  Building2,
  Plus,
  Eye,
  Edit2,
  Trash2,
  Users,
  BookOpen,
  Award,
} from 'lucide-react';
import { DepartmentDetailModal } from './DepartmentDetailModal';
import { DepartmentFormModal } from './DepartmentFormModal';

interface DepartmentListProps {
  departments: Department[];
  onCreateDepartment: (input: CreateDepartmentInput) => Promise<Department>;
  onUpdateDepartment: (id: number, input: UpdateDepartmentInput) => Promise<Department>;
  onDeleteDepartment: (id: number) => Promise<boolean>;
  onSelectDepartmentDetails: (id: number) => Promise<Department | null>;
}

export const DepartmentList: React.FC<DepartmentListProps> = ({
  departments,
  onCreateDepartment,
  onUpdateDepartment,
  onDeleteDepartment,
  onSelectDepartmentDetails,
}) => {
  const [search, setSearch] = useState('');
  const [selectedDept, setSelectedDept] = useState<Department | null>(null);
  const [formDept, setFormDept] = useState<Department | null>(null);
  const [isFormOpen, setIsFormOpen] = useState(false);
  const [deleteId, setDeleteId] = useState<number | null>(null);

  const filtered = useMemo(() => {
    return departments.filter(
      (d) =>
        d.name.toLowerCase().includes(search.toLowerCase()) ||
        d.code.toLowerCase().includes(search.toLowerCase())
    );
  }, [departments, search]);

  const handleOpenDetails = async (dept: Department) => {
    const detailed = await onSelectDepartmentDetails(dept.id);
    setSelectedDept(detailed || dept);
  };

  const handleFormSubmit = async (
    input: CreateDepartmentInput | UpdateDepartmentInput,
    id?: number
  ) => {
    if (id) {
      await onUpdateDepartment(id, input as UpdateDepartmentInput);
    } else {
      await onCreateDepartment(input as CreateDepartmentInput);
    }
  };

  return (
    <div className="space-y-4">
      {/* Search & Actions Bar */}
      <div className="p-4 rounded-2xl bg-ink-850/90 border border-ink-700/70 shadow-sm flex flex-col sm:flex-row sm:items-center justify-between gap-3">
        <SearchInput
          value={search}
          onChange={setSearch}
          placeholder="Search faculties by name or department code..."
          className="w-full sm:w-80"
        />

        <button
          onClick={() => {
            setFormDept(null);
            setIsFormOpen(true);
          }}
          className="flex items-center justify-center gap-2 px-4 py-2 rounded-xl bg-gradient-to-r from-gold-500 to-gold-600 hover:from-gold-600 hover:to-gold-700 text-ink-950 font-bold text-xs shadow-[0_0_15px_-3px_rgba(212,175,55,0.3)] transition-all cursor-pointer whitespace-nowrap"
        >
          <Plus className="w-4 h-4 stroke-[2.5]" />
          <span>Add Academic Faculty</span>
        </button>
      </div>

      {/* Department Cards Grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
        {filtered.map((dept) => (
          <div
            key={dept.id}
            className="p-5 rounded-2xl bg-ink-850/90 border border-ink-700/70 hover:border-gold-500/40 transition-all group flex flex-col justify-between shadow-sm"
          >
            <div>
              <div className="flex items-center justify-between">
                <div className="w-11 h-11 rounded-2xl bg-ink-800 border border-gold-500/30 text-gold-300 flex items-center justify-center font-display font-bold text-sm tracking-wider shadow-sm">
                  {dept.code}
                </div>
                <div className="flex items-center gap-1">
                  <button
                    onClick={() => handleOpenDetails(dept)}
                    className="p-1.5 text-slate-400 hover:text-gold-300 hover:bg-ink-750 rounded-lg transition-colors cursor-pointer"
                    title="Inspect Faculty Syllabus & Roster"
                  >
                    <Eye className="w-4 h-4" />
                  </button>
                  <button
                    onClick={() => {
                      setFormDept(dept);
                      setIsFormOpen(true);
                    }}
                    className="p-1.5 text-slate-400 hover:text-cobalt-300 hover:bg-ink-750 rounded-lg transition-colors cursor-pointer"
                    title="Modify Faculty"
                  >
                    <Edit2 className="w-4 h-4" />
                  </button>
                  <button
                    onClick={() => setDeleteId(dept.id)}
                    className="p-1.5 text-slate-400 hover:text-garnet-400 hover:bg-ink-750 rounded-lg transition-colors cursor-pointer"
                    title="Remove Faculty"
                  >
                    <Trash2 className="w-4 h-4" />
                  </button>
                </div>
              </div>

              <h4 className="font-display font-bold text-base text-white mt-4 group-hover:text-gold-300 transition-colors">
                {dept.name}
              </h4>
              <p className="text-xs text-slate-400 font-mono mt-0.5">
                Model: Department · ID #{dept.id}
              </p>
            </div>

            <div className="mt-5 pt-3 border-t border-ink-750/70 flex items-center justify-between text-xs">
              <div className="flex items-center gap-1.5 text-slate-400">
                <Building2 className="w-3.5 h-3.5 text-gold-400" />
                <span>Undergrad & Graduate</span>
              </div>
              <Badge variant="gold" size="sm">
                Accredited Faculty
              </Badge>
            </div>
          </div>
        ))}
      </div>

      {/* Modals */}
      <DepartmentDetailModal
        department={selectedDept}
        isOpen={!!selectedDept}
        onClose={() => setSelectedDept(null)}
        onEdit={(d) => {
          setFormDept(d);
          setIsFormOpen(true);
        }}
      />

      <DepartmentFormModal
        isOpen={isFormOpen}
        onClose={() => {
          setIsFormOpen(false);
          setFormDept(null);
        }}
        department={formDept}
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
            <h4 className="font-display font-bold text-base text-white">Disband Academic Faculty</h4>
            <p className="text-xs text-slate-400 mt-2 leading-relaxed">
              Are you sure you wish to delete faculty #{deleteId}? Existing courses and students affiliated with this department will need reassignment.
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
                  await onDeleteDepartment(deleteId);
                  setDeleteId(null);
                }}
                className="px-4 py-2 rounded-xl bg-garnet-600 hover:bg-garnet-500 text-white text-xs font-bold transition-colors cursor-pointer"
              >
                Disband Faculty
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
