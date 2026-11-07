import React, { useState, useEffect, useCallback } from 'react';
import {
  Course,
  Department,
  Instructor,
  CreateCourseInput,
  UpdateCourseInput,
  AcademicRole,
  ROLE_PROFILES,
} from '../../types/graphorm';
import { api } from '../../services/api';
import { SearchInput } from '../common/SearchInput';
import { Badge } from '../common/Badge';
import { DataPagination } from '../common/DataPagination';
import {
  BookOpen,
  Plus,
  Edit2,
  Trash2,
  Filter,
  UserCheck,
  Building2,
  Lock,
  ArrowUpDown,
  Loader2,
} from 'lucide-react';
import { CourseFormModal } from './CourseFormModal';

interface CourseListProps {
  courses?: Course[];
  departments: Department[];
  instructors: Instructor[];
  currentRole?: AcademicRole;
  onCreateCourse: (input: CreateCourseInput) => Promise<Course>;
  onUpdateCourse: (id: number, input: UpdateCourseInput) => Promise<Course>;
  onDeleteCourse: (id: number) => Promise<boolean>;
}

export const CourseList: React.FC<CourseListProps> = ({
  courses: initialCourses = [],
  departments: propDepartments = [],
  instructors: propInstructors = [],
  currentRole = 'ADMIN',
  onCreateCourse,
  onUpdateCourse,
  onDeleteCourse,
}) => {
  const [courses, setCourses] = useState<Course[]>(initialCourses);
  const [departments, setDepartments] = useState<Department[]>(propDepartments);
  const [instructors, setInstructors] = useState<Instructor[]>(propInstructors);
  const [search, setSearch] = useState('');
  const [selectedDeptId, setSelectedDeptId] = useState<string>('all');
  const [selectedInstructorId, setSelectedInstructorId] = useState<string>('all');
  const [selectedCredits, setSelectedCredits] = useState<string>('all');
  const [sortBy, setSortBy] = useState<'title' | 'code' | 'credits' | 'id'>('code');
  const [sortOrder, setSortOrder] = useState<'asc' | 'desc'>('asc');
  const [currentPage, setCurrentPage] = useState(1);
  const [pageSize, setPageSize] = useState(6);
  const [totalItems, setTotalItems] = useState(initialCourses.length);
  const [totalPages, setTotalPages] = useState(1);
  const [isLoading, setIsLoading] = useState(false);

  const [formCourse, setFormCourse] = useState<Course | null>(null);
  const [isFormOpen, setIsFormOpen] = useState(false);
  const [deleteId, setDeleteId] = useState<number | null>(null);

  const canMutate = ROLE_PROFILES[currentRole]?.permissions.canMutate ?? false;

  const fetchCourses = useCallback(async () => {
    setIsLoading(true);
    try {
      const res = await api.getCoursesPaginated({
        page: currentPage,
        pageSize,
        search: search.trim() || undefined,
        departmentId: selectedDeptId !== 'all' ? selectedDeptId : undefined,
        instructorId: selectedInstructorId !== 'all' ? selectedInstructorId : undefined,
        credits: selectedCredits !== 'all' ? selectedCredits : undefined,
        sortBy,
        sortOrder,
      });
      setCourses(res.items);
      setTotalItems(res.pagination.totalItems);
      setTotalPages(res.pagination.totalPages);
      if (res.departments && res.departments.length > 0) {
        setDepartments(res.departments);
      }
      if (res.instructors && res.instructors.length > 0) {
        setInstructors(res.instructors);
      }
    } catch (err) {
      console.error('Failed to fetch courses:', err);
    } finally {
      setIsLoading(false);
    }
  }, [currentPage, pageSize, search, selectedDeptId, selectedInstructorId, selectedCredits, sortBy, sortOrder, currentRole]);

  useEffect(() => {
    fetchCourses();
  }, [fetchCourses]);

  const handleFormSubmit = async (
    input: CreateCourseInput | UpdateCourseInput,
    id?: number
  ) => {
    if (id) {
      await onUpdateCourse(id, input as UpdateCourseInput);
    } else {
      await onCreateCourse(input as CreateCourseInput);
    }
    await fetchCourses();
  };

  return (
    <div className="space-y-4">
      {/* Top Search & Multi-Facet Filter Bar */}
      <div className="p-4 rounded-2xl bg-ink-850/90 border border-ink-700/70 shadow-sm flex flex-col sm:flex-row sm:items-center justify-between gap-3">
        <div className="flex-1 flex flex-wrap items-center gap-2.5">
          <SearchInput
            value={search}
            onChange={(val) => {
              setSearch(val);
              setCurrentPage(1);
            }}
            placeholder="Search curriculum offerings by title or course code..."
            className="w-full sm:w-64"
          />

          {/* Department Filter */}
          <div className="flex items-center gap-1.5 bg-ink-900/80 border border-ink-700/70 rounded-xl px-2.5 py-1.5 text-xs">
            <Filter className="w-3.5 h-3.5 text-slate-400" />
            <select
              value={selectedDeptId}
              onChange={(e) => {
                setSelectedDeptId(e.target.value);
                setCurrentPage(1);
              }}
              className="bg-transparent text-slate-200 focus:outline-none cursor-pointer text-xs"
              aria-label="Filter by department"
            >
              <option value="all" className="bg-ink-900 text-slate-200">All Academic Faculties</option>
              {departments.map((d) => (
                <option key={d.id} value={d.id} className="bg-ink-900 text-slate-200">
                  {d.name} ({d.code})
                </option>
              ))}
            </select>
          </div>

          {/* Instructor Filter */}
          <div className="flex items-center gap-1.5 bg-ink-900/80 border border-ink-700/70 rounded-xl px-2.5 py-1.5 text-xs">
            <UserCheck className="w-3.5 h-3.5 text-slate-400" />
            <select
              value={selectedInstructorId}
              onChange={(e) => {
                setSelectedInstructorId(e.target.value);
                setCurrentPage(1);
              }}
              className="bg-transparent text-slate-200 focus:outline-none cursor-pointer text-xs"
              aria-label="Filter by instructor"
            >
              <option value="all" className="bg-ink-900 text-slate-200">All Faculty Chairs</option>
              {instructors.map((i) => (
                <option key={i.id} value={i.id} className="bg-ink-900 text-slate-200">
                  Prof. {i.name}
                </option>
              ))}
            </select>
          </div>

          {/* Credits Filter */}
          <div className="flex items-center gap-1.5 bg-ink-900/80 border border-ink-700/70 rounded-xl px-2.5 py-1.5 text-xs">
            <span className="text-slate-400 font-mono text-[11px]">Units:</span>
            <select
              value={selectedCredits}
              onChange={(e) => {
                setSelectedCredits(e.target.value);
                setCurrentPage(1);
              }}
              className="bg-transparent text-slate-200 focus:outline-none cursor-pointer text-xs"
              aria-label="Filter by credits"
            >
              <option value="all" className="bg-ink-900 text-slate-200">All Credits</option>
              <option value="3" className="bg-ink-900 text-slate-200">3 Units</option>
              <option value="4" className="bg-ink-900 text-slate-200">4 Units</option>
            </select>
          </div>

          {/* Sort Control */}
          <div className="flex items-center gap-1.5 bg-ink-900/80 border border-ink-700/70 rounded-xl px-2.5 py-1.5 text-xs">
            <ArrowUpDown className="w-3.5 h-3.5 text-slate-400" />
            <select
              value={`${sortBy}-${sortOrder}`}
              onChange={(e) => {
                const [f, o] = e.target.value.split('-');
                setSortBy(f as 'title' | 'code' | 'credits' | 'id');
                setSortOrder(o as 'asc' | 'desc');
                setCurrentPage(1);
              }}
              className="bg-transparent text-slate-200 focus:outline-none cursor-pointer text-xs"
              aria-label="Sort courses"
            >
              <option value="code-asc" className="bg-ink-900 text-slate-200">Course Code (A-Z)</option>
              <option value="code-desc" className="bg-ink-900 text-slate-200">Course Code (Z-A)</option>
              <option value="title-asc" className="bg-ink-900 text-slate-200">Course Title (A-Z)</option>
              <option value="title-desc" className="bg-ink-900 text-slate-200">Course Title (Z-A)</option>
              <option value="credits-desc" className="bg-ink-900 text-slate-200">Units (High-Low)</option>
              <option value="credits-asc" className="bg-ink-900 text-slate-200">Units (Low-High)</option>
              <option value="id-asc" className="bg-ink-900 text-slate-200">Identifier (Low-High)</option>
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
            setFormCourse(null);
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
              ? 'New Course Offering'
              : 'Course creation requires ADMIN or INSTRUCTOR privilege (@auth)'
          }
        >
          {canMutate ? <Plus className="w-4 h-4 stroke-[2.5]" /> : <Lock className="w-3.5 h-3.5" />}
          <span>New Course Offering</span>
        </button>
      </div>

      {/* Course Cards Grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
        {courses.length === 0 ? (
          <div className="col-span-full py-12 text-center text-slate-400 text-xs bg-ink-850/90 border border-ink-700/70 rounded-2xl">
            {isLoading ? 'Loading records...' : 'No course curriculum offerings found matching query criteria.'}
          </div>
        ) : (
          courses.map((course) => {
            const dept = course.department || departments.find((d) => d.id === course.departmentId);
            const inst = course.instructor || instructors.find((i) => i.id === course.instructorId);

            return (
              <div
                key={course.id}
                className="p-5 rounded-2xl bg-ink-850/90 border border-ink-700/70 hover:border-cobalt-500/40 transition-all group flex flex-col justify-between shadow-sm"
              >
                <div>
                  <div className="flex items-start justify-between gap-3">
                    <div className="flex items-center gap-2">
                      <span className="font-mono text-xs font-bold px-2 py-0.5 rounded-lg bg-cobalt-500/15 text-cobalt-300 border border-cobalt-500/30">
                        {course.code}
                      </span>
                      <span className="text-[11px] text-slate-400 font-mono">
                        {course.credits} {course.credits === 1 ? 'Unit' : 'Units'}
                      </span>
                    </div>

                    <div className="flex items-center gap-1">
                      <button
                        onClick={() => {
                          if (!canMutate) return;
                          setFormCourse(course);
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
                            ? 'Modify Syllabus'
                            : 'Editing requires ADMIN or INSTRUCTOR privilege (@auth)'
                        }
                      >
                        <Edit2 className="w-4 h-4" />
                      </button>
                      <button
                        onClick={() => {
                          if (!canMutate) return;
                          setDeleteId(course.id);
                        }}
                        disabled={!canMutate}
                        className={`p-1.5 rounded-lg transition-colors ${
                          canMutate
                            ? 'text-slate-400 hover:text-garnet-400 hover:bg-ink-750 cursor-pointer'
                            : 'text-slate-600 opacity-40 cursor-not-allowed'
                        }`}
                        title={
                          canMutate
                            ? 'Retire Course Offering'
                            : 'Retiring courses requires ADMIN or INSTRUCTOR privilege (@auth)'
                        }
                      >
                        <Trash2 className="w-4 h-4" />
                      </button>
                    </div>
                  </div>

                  <h4 className="font-display font-bold text-base text-white mt-3 group-hover:text-cobalt-300 transition-colors">
                    {course.title}
                  </h4>

                  <div className="mt-4 space-y-2 text-xs">
                    <div className="flex items-center gap-2 text-slate-400">
                      <Building2 className="w-3.5 h-3.5 text-slate-400" />
                      <span className="text-slate-300">{dept?.name || 'Academic Faculty'}</span>
                    </div>
                    <div className="flex items-center gap-2 text-slate-400">
                      <UserCheck className="w-3.5 h-3.5 text-gold-400" />
                      <span className="text-slate-300">{inst?.name || 'Faculty Chair To Be Assigned'}</span>
                    </div>
                  </div>
                </div>

                <div className="mt-5 pt-3 border-t border-ink-750/70 flex items-center justify-between text-xs">
                  <span className="font-mono text-slate-400 text-[10px]">Course ID #{course.id}</span>
                  <Badge variant="sky" size="sm">
                    Active Term
                  </Badge>
                </div>
              </div>
            );
          })
        )}
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
        pageSizeOptions={[6, 9, 15, 30]}
        itemLabel="courses"
      />

      {/* Course Edit/Create Form Modal */}
      {isFormOpen && (
        <CourseFormModal
          isOpen={isFormOpen}
          course={formCourse}
          departments={departments}
          instructors={instructors}
          onClose={() => setIsFormOpen(false)}
          onSubmit={handleFormSubmit}
        />
      )}

      {/* Delete Confirmation Modal */}
      {deleteId && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm animate-fade-in">
          <div className="bg-ink-850 border border-ink-700 rounded-2xl max-w-sm w-full p-6 space-y-4 shadow-xl">
            <h3 className="text-sm font-bold text-white">Retire Course Offering?</h3>
            <p className="text-xs text-slate-300">
              Are you sure you want to retire course #{deleteId}? This will cascade to active student enrollments.
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
                  await onDeleteCourse(deleteId);
                  setDeleteId(null);
                  await fetchCourses();
                }}
                className="px-3.5 py-1.5 text-xs font-semibold bg-garnet-500 hover:bg-garnet-600 text-white rounded-lg shadow-sm transition-colors cursor-pointer"
              >
                Confirm Deletion
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
