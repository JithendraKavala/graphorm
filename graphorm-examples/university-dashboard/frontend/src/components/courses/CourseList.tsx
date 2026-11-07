import React, { useState, useMemo } from 'react';
import {
  Course,
  Department,
  Instructor,
  CreateCourseInput,
  UpdateCourseInput,
} from '../../types/graphorm';
import { SearchInput } from '../common/SearchInput';
import { Badge } from '../common/Badge';
import {
  BookOpen,
  Plus,
  Edit2,
  Trash2,
  Filter,
  UserCheck,
  Building2,
  Award,
} from 'lucide-react';
import { CourseFormModal } from './CourseFormModal';

interface CourseListProps {
  courses: Course[];
  departments: Department[];
  instructors: Instructor[];
  onCreateCourse: (input: CreateCourseInput) => Promise<Course>;
  onUpdateCourse: (id: number, input: UpdateCourseInput) => Promise<Course>;
  onDeleteCourse: (id: number) => Promise<boolean>;
}

export const CourseList: React.FC<CourseListProps> = ({
  courses,
  departments,
  instructors,
  onCreateCourse,
  onUpdateCourse,
  onDeleteCourse,
}) => {
  const [search, setSearch] = useState('');
  const [selectedDeptId, setSelectedDeptId] = useState<string>('all');
  const [formCourse, setFormCourse] = useState<Course | null>(null);
  const [isFormOpen, setIsFormOpen] = useState(false);
  const [deleteId, setDeleteId] = useState<number | null>(null);

  const filtered = useMemo(() => {
    return courses.filter((c) => {
      const matchesSearch =
        c.title.toLowerCase().includes(search.toLowerCase()) ||
        c.code.toLowerCase().includes(search.toLowerCase());
      const matchesDept =
        selectedDeptId === 'all' ? true : c.departmentId === Number(selectedDeptId);
      return matchesSearch && matchesDept;
    });
  }, [courses, search, selectedDeptId]);

  const handleFormSubmit = async (
    input: CreateCourseInput | UpdateCourseInput,
    id?: number
  ) => {
    if (id) {
      await onUpdateCourse(id, input as UpdateCourseInput);
    } else {
      await onCreateCourse(input as CreateCourseInput);
    }
  };

  return (
    <div className="space-y-4">
      {/* Top Search & Filter Bar */}
      <div className="p-4 rounded-2xl bg-ink-850/90 border border-ink-700/70 shadow-sm flex flex-col sm:flex-row sm:items-center justify-between gap-3">
        <div className="flex-1 flex flex-wrap items-center gap-2.5">
          <SearchInput
            value={search}
            onChange={setSearch}
            placeholder="Search curriculum offerings by title or course code..."
            className="w-full sm:w-72"
          />

          <div className="flex items-center gap-1.5 bg-ink-900/80 border border-ink-700/70 rounded-xl px-2.5 py-1.5 text-xs">
            <Filter className="w-3.5 h-3.5 text-slate-400" />
            <select
              value={selectedDeptId}
              onChange={(e) => setSelectedDeptId(e.target.value)}
              className="bg-transparent text-slate-200 focus:outline-none cursor-pointer text-xs"
            >
              <option value="all">All Academic Faculties</option>
              {departments.map((d) => (
                <option key={d.id} value={d.id}>
                  {d.name} ({d.code})
                </option>
              ))}
            </select>
          </div>
        </div>

        <button
          onClick={() => {
            setFormCourse(null);
            setIsFormOpen(true);
          }}
          className="flex items-center justify-center gap-2 px-4 py-2 rounded-xl bg-gradient-to-r from-cobalt-600 to-cobalt-500 hover:from-cobalt-500 hover:to-cobalt-400 text-white font-bold text-xs shadow-[0_0_15px_-3px_rgba(59,130,246,0.3)] transition-all cursor-pointer whitespace-nowrap"
        >
          <Plus className="w-4 h-4 stroke-[2.5]" />
          <span>New Course Offering</span>
        </button>
      </div>

      {/* Course Cards Grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
        {filtered.map((course) => {
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
                        setFormCourse(course);
                        setIsFormOpen(true);
                      }}
                      className="p-1.5 text-slate-400 hover:text-cobalt-300 hover:bg-ink-750 rounded-lg transition-colors cursor-pointer"
                      title="Modify Syllabus"
                    >
                      <Edit2 className="w-4 h-4" />
                    </button>
                    <button
                      onClick={() => setDeleteId(course.id)}
                      className="p-1.5 text-slate-400 hover:text-garnet-400 hover:bg-ink-750 rounded-lg transition-colors cursor-pointer"
                      title="Retire Course Offering"
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
        })}
      </div>

      {/* Modal */}
      <CourseFormModal
        isOpen={isFormOpen}
        onClose={() => {
          setIsFormOpen(false);
          setFormCourse(null);
        }}
        course={formCourse}
        departments={departments}
        instructors={instructors}
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
            <h4 className="font-display font-bold text-base text-white">Retire Curriculum Course</h4>
            <p className="text-xs text-slate-400 mt-2 leading-relaxed">
              Are you sure you wish to retire course offering #{deleteId}? Existing enrollments mapped to this course ID will be dropped.
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
                  await onDeleteCourse(deleteId);
                  setDeleteId(null);
                }}
                className="px-4 py-2 rounded-xl bg-garnet-600 hover:bg-garnet-500 text-white text-xs font-bold transition-colors cursor-pointer"
              >
                Retire Course
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
