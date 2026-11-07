import React, { useState, useEffect, useCallback } from 'react';
import {
  Department,
  CreateDepartmentInput,
  UpdateDepartmentInput,
  AcademicRole,
  ROLE_PROFILES,
} from '../../types/graphorm';
import { api } from '../../services/api';
import { SearchInput } from '../common/SearchInput';
import { Badge } from '../common/Badge';
import { DataPagination } from '../common/DataPagination';
import {
  Building2,
  Plus,
  Eye,
  Edit2,
  Trash2,
  ArrowUpDown,
  Lock,
  Loader2,
} from 'lucide-react';
import { DepartmentDetailModal } from './DepartmentDetailModal';
import { DepartmentFormModal } from './DepartmentFormModal';

interface DepartmentListProps {
  departments?: Department[];
  currentRole?: AcademicRole;
  onCreateDepartment: (input: CreateDepartmentInput) => Promise<Department>;
  onUpdateDepartment: (id: number, input: UpdateDepartmentInput) => Promise<Department>;
  onDeleteDepartment: (id: number) => Promise<boolean>;
  onSelectDepartmentDetails: (id: number) => Promise<Department | null>;
}

export const DepartmentList: React.FC<DepartmentListProps> = ({
  departments: initialDepartments = [],
  currentRole = 'ADMIN',
  onCreateDepartment,
  onUpdateDepartment,
  onDeleteDepartment,
  onSelectDepartmentDetails,
}) => {
  const [departments, setDepartments] = useState<Department[]>(initialDepartments);
  const [search, setSearch] = useState('');
  const [sortBy, setSortBy] = useState<'name' | 'code' | 'id'>('name');
  const [sortOrder, setSortOrder] = useState<'asc' | 'desc'>('asc');
  const [currentPage, setCurrentPage] = useState(1);
  const [pageSize, setPageSize] = useState(6);
  const [totalItems, setTotalItems] = useState(initialDepartments.length);
  const [totalPages, setTotalPages] = useState(1);
  const [isLoading, setIsLoading] = useState(false);
  const [selectedDept, setSelectedDept] = useState<Department | null>(null);
  const [formDept, setFormDept] = useState<Department | null>(null);
  const [isFormOpen, setIsFormOpen] = useState(false);
  const [deleteId, setDeleteId] = useState<number | null>(null);

  const canMutate = ROLE_PROFILES[currentRole]?.permissions.canMutate ?? false;

  const fetchDepartments = useCallback(async () => {
    setIsLoading(true);
    try {
      const res = await api.getDepartmentsPaginated({
        page: currentPage,
        pageSize,
        search: search.trim() || undefined,
        sortBy,
        sortOrder,
      });
      setDepartments(res.items);
      setTotalItems(res.pagination.totalItems);
      setTotalPages(res.pagination.totalPages);
    } catch (err) {
      console.error('Failed to fetch departments:', err);
    } finally {
      setIsLoading(false);
    }
  }, [currentPage, pageSize, search, sortBy, sortOrder, currentRole]);

  useEffect(() => {
    fetchDepartments();
  }, [fetchDepartments]);

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
    await fetchDepartments();
  };

  return (
    <div className="space-y-4">
      {/* Search & Actions Bar */}
      <div className="p-4 rounded-2xl bg-ink-850/90 border border-ink-700/70 shadow-sm flex flex-col sm:flex-row sm:items-center justify-between gap-3">
        <div className="flex-1 flex flex-wrap items-center gap-2.5">
          <SearchInput
            value={search}
            onChange={(val) => {
              setSearch(val);
              setCurrentPage(1);
            }}
            placeholder="Search faculties by name or department code..."
            className="w-full sm:w-80"
          />

          {/* Sort Control */}
          <div className="flex items-center gap-1.5 bg-ink-900/80 border border-ink-700/70 rounded-xl px-2.5 py-1.5 text-xs">
            <ArrowUpDown className="w-3.5 h-3.5 text-slate-400" />
            <select
              value={`${sortBy}-${sortOrder}`}
              onChange={(e) => {
                const [f, o] = e.target.value.split('-');
                setSortBy(f as 'name' | 'code' | 'id');
                setSortOrder(o as 'asc' | 'desc');
                setCurrentPage(1);
              }}
              className="bg-transparent text-slate-200 focus:outline-none cursor-pointer text-xs"
              aria-label="Sort faculties"
            >
              <option value="name-asc" className="bg-ink-900 text-slate-200">Faculty Name (A-Z)</option>
              <option value="name-desc" className="bg-ink-900 text-slate-200">Faculty Name (Z-A)</option>
              <option value="code-asc" className="bg-ink-900 text-slate-200">Faculty Code (A-Z)</option>
              <option value="id-asc" className="bg-ink-900 text-slate-200">Identifier (Low-High)</option>
            </select>
          </div>

          {isLoading && (
            <div className="flex items-center gap-1 text-[11px] font-mono text-gold-400 animate-pulse ml-1">
              <Loader2 className="w-3.5 h-3.5 animate-spin" />
              <span>Querying...</span>
            </div>
          )}
        </div>

        <button
          onClick={() => {
            if (!canMutate) return;
            setFormDept(null);
            setIsFormOpen(true);
          }}
          disabled={!canMutate}
          className={`flex items-center justify-center gap-2 px-4 py-2 rounded-xl text-xs font-bold transition-all whitespace-nowrap ${
            canMutate
              ? 'bg-gradient-to-r from-gold-500 to-gold-600 hover:from-gold-600 hover:to-gold-700 text-ink-950 shadow-[0_0_15px_-3px_rgba(212,175,55,0.3)] cursor-pointer'
              : 'bg-ink-800 text-slate-500 border border-ink-700 opacity-60 cursor-not-allowed'
          }`}
          title={
            canMutate
              ? 'Add Academic Faculty'
              : 'Faculty creation requires ADMIN privilege (@auth)'
          }
        >
          {canMutate ? <Plus className="w-4 h-4 stroke-[2.5]" /> : <Lock className="w-3.5 h-3.5" />}
          <span>Add Academic Faculty</span>
        </button>
      </div>

      {/* Department Cards Grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
        {departments.length === 0 ? (
          <div className="col-span-full py-12 text-center text-slate-400 text-xs bg-ink-850/90 border border-ink-700/70 rounded-2xl">
            {isLoading ? 'Loading records...' : 'No academic faculties found matching search query.'}
          </div>
        ) : (
          departments.map((dept) => (
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
                        if (!canMutate) return;
                        setFormDept(dept);
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
                          ? 'Modify Faculty'
                          : 'Modifying faculties requires ADMIN privilege (@auth)'
                      }
                    >
                      <Edit2 className="w-4 h-4" />
                    </button>
                    <button
                      onClick={() => {
                        if (!canMutate) return;
                        setDeleteId(dept.id);
                      }}
                      disabled={!canMutate}
                      className={`p-1.5 rounded-lg transition-colors ${
                        canMutate
                          ? 'text-slate-400 hover:text-garnet-400 hover:bg-ink-750 cursor-pointer'
                          : 'text-slate-600 opacity-40 cursor-not-allowed'
                      }`}
                      title={
                        canMutate
                          ? 'Remove Faculty'
                          : 'Removing faculties requires ADMIN privilege (@auth)'
                      }
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
          ))
        )}
      </div>

      {/* Pagination Bar */}
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
        pageSizeOptions={[3, 6, 9, 18]}
        itemLabel="academic faculties"
      />

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
                  await fetchDepartments();
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
