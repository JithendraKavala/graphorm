import React, { useState, useEffect, useCallback } from 'react';
import {
  ActiveTab,
  DataSourceMode,
  AcademicRole,
  Student,
  Department,
  Course,
  Instructor,
  Enrollment,
  DepartmentStats,
  StudentDashboard,
  DashboardWidgetConfig,
  ActivityItem,
  CreateStudentInput,
  UpdateStudentInput,
  CreateDepartmentInput,
  UpdateDepartmentInput,
  CreateCourseInput,
  UpdateCourseInput,
  CreateEnrollmentInput,
  UpdateEnrollmentInput,
} from './types/graphorm';
import { api } from './services/api';

// Product Showcase Landing Page
import { ProductShowcase } from './components/showcase/ProductShowcase';

// Layout
import { Sidebar } from './components/layout/Sidebar';
import { Header } from './components/layout/Header';

// Dashboard
import { KpiCards } from './components/dashboard/KpiCards';
import { EnrollmentTrendChart } from './components/dashboard/EnrollmentTrendChart';
import { DepartmentDistributionChart } from './components/dashboard/DepartmentDistributionChart';
import { GpaDistributionChart } from './components/dashboard/GpaDistributionChart';
import { RecentActivityFeed } from './components/dashboard/RecentActivityFeed';
import { QuickActions } from './components/dashboard/QuickActions';
import { WidgetCustomizerModal } from './components/dashboard/WidgetCustomizerModal';

// CRUD
import { StudentList } from './components/students/StudentList';
import { StudentFormModal } from './components/students/StudentFormModal';
import { DepartmentList } from './components/departments/DepartmentList';
import { CourseList } from './components/courses/CourseList';
import { CourseFormModal } from './components/courses/CourseFormModal';
import { EnrollmentList } from './components/enrollments/EnrollmentList';
import { EnrollmentFormModal } from './components/enrollments/EnrollmentFormModal';

// Analytics & Developer tools
import { AnalyticsView } from './components/analytics/AnalyticsView';
import { SchemaInspector } from './components/schema-explorer/SchemaInspector';
import { GraphQlPlayground } from './components/schema-explorer/GraphQlPlayground';

export function App() {
  const [activeTab, setActiveTab] = useState<ActiveTab>(() => {
    if (typeof window !== 'undefined' && window.location.hash) {
      const hash = window.location.hash.replace('#', '');
      if (['dashboard', 'students', 'departments', 'courses', 'enrollments', 'analytics', 'schema-explorer', 'graphql-playground'].includes(hash)) {
        return hash as ActiveTab;
      }
    }
    return 'landing';
  });
  const [dataSourceMode, setDataSourceMode] = useState<DataSourceMode>('graphql');
  const [currentRole, setCurrentRole] = useState<AcademicRole>(() => api.getRole());
  const [isConnected, setIsConnected] = useState<boolean>(false);
  const [isRefreshing, setIsRefreshing] = useState<boolean>(false);
  const [isMobileSidebarOpen, setIsMobileSidebarOpen] = useState<boolean>(false);

  // Entities State
  const [students, setStudents] = useState<Student[]>([]);
  const [departments, setDepartments] = useState<Department[]>([]);
  const [courses, setCourses] = useState<Course[]>([]);
  const [instructors, setInstructors] = useState<Instructor[]>([]);
  const [enrollments, setEnrollments] = useState<Enrollment[]>([]);
  const [departmentStats, setDepartmentStats] = useState<DepartmentStats[]>([]);
  const [studentDashboards, setStudentDashboards] = useState<StudentDashboard[]>([]);
  const [activities, setActivities] = useState<ActivityItem[]>([]);

  // Modals & Customizer State
  const [isCustomizerOpen, setIsCustomizerOpen] = useState(false);
  const [quickStudentOpen, setQuickStudentOpen] = useState(false);
  const [quickCourseOpen, setQuickCourseOpen] = useState(false);
  const [quickEnrollmentOpen, setQuickEnrollmentOpen] = useState(false);

  // Dashboard Customizable Widgets
  const [widgets, setWidgets] = useState<DashboardWidgetConfig[]>([
    { id: 'kpis', title: 'Telemetry Metrics', visible: true, order: 1 },
    { id: 'trend', title: 'Enrollment Trajectory', visible: true, order: 2 },
    { id: 'departments', title: 'Faculty Census & GPA Distribution', visible: true, order: 3 },
    { id: 'activity', title: 'Audit Ledger Stream', visible: true, order: 4 },
    { id: 'shortcuts', title: 'Direct Pipeline Dispatch', visible: true, order: 5 },
  ]);

  useEffect(() => {
    const handleHash = () => {
      const hash = window.location.hash.replace("#", "");
      if (["landing", "dashboard", "students", "departments", "courses", "enrollments", "analytics", "schema-explorer", "graphql-playground"].includes(hash)) {
        setActiveTab(hash as ActiveTab);
      }
    };
    window.addEventListener("hashchange", handleHash);
    return () => window.removeEventListener("hashchange", handleHash);
  }, []);

  const handleTabChange = (tab: ActiveTab) => {
    setActiveTab(tab);
    window.location.hash = tab;
  };

  // Load data on-demand for the current active page/tab only
  const loadDataForTab = useCallback(async (tab: ActiveTab) => {
    // Only 'dashboard' aggregates telemetry across all entities in a single unified query.
    // List pages (students, departments, courses, enrollments) and analytics manage
    // their own self-contained server-side single query with pagination and relations!
    if (tab !== 'dashboard') {
      setIsConnected(api.isConnected());
      return;
    }

    setIsRefreshing(true);
    try {
      const overview = await api.getDashboardOverview();
      setDepartments(overview.departments);
      setInstructors(overview.instructors);
      setStudents(overview.students);
      setCourses(overview.courses);
      setEnrollments(overview.enrollments);
      setDepartmentStats(overview.departmentStats);
      setActivities(overview.activities);
      setIsConnected(api.isConnected());
    } catch (err) {
      console.error("Failed to load dashboard overview:", err);
    } finally {
      setIsRefreshing(false);
    }
  }, []);

  // Fetch data only for the current active page
  useEffect(() => {
    loadDataForTab(activeTab);
  }, [activeTab, loadDataForTab]);

  // Subscribe to changes (e.g. after mutations in mock or live backend)
  useEffect(() => {
    const unsubscribe = api.subscribe(() => {
      loadDataForTab(activeTab);
    });
    return () => unsubscribe();
  }, [activeTab, loadDataForTab]);

  const handleDataSourceModeChange = (mode: DataSourceMode) => {
    setDataSourceMode(mode);
    api.setMode(mode);
    loadDataForTab(activeTab);
  };

  const handleRoleChange = (newRole: AcademicRole) => {
    api.setRole(newRole);
    setCurrentRole(newRole);
    loadDataForTab(activeTab);
  };

  const handleQuickAction = async (action: string) => {
    switch (action) {
      case 'landing':
        handleTabChange('landing');
        break;
      case 'student':
        if (departments.length === 0) {
          const depts = await api.getDepartments();
          setDepartments(depts);
        }
        setQuickStudentOpen(true);
        break;
      case 'course':
        if (departments.length === 0 || instructors.length === 0) {
          const [depts, insts] = await Promise.all([api.getDepartments(), api.getInstructors()]);
          setDepartments(depts);
          setInstructors(insts);
        }
        setQuickCourseOpen(true);
        break;
      case 'enrollment':
        if (students.length === 0 || courses.length === 0) {
          const [studs, crss] = await Promise.all([api.getStudents(), api.getCourses()]);
          setStudents(studs);
          setCourses(crss);
        }
        setQuickEnrollmentOpen(true);
        break;
      case 'department':
        handleTabChange('departments');
        break;
      case 'schema':
        handleTabChange('schema-explorer');
        break;
      case 'graphql':
        handleTabChange('graphql-playground');
        break;
      default:
        break;
    }
  };

  // If viewing official product showcase landing page
  if (activeTab === 'landing') {
    return <ProductShowcase onOpenPortal={() => handleTabChange('dashboard')} />;
  }

  return (
    <div className="flex min-h-screen bg-[#0D1117] text-[#F0F6FC] font-sans antialiased">
      {/* Sidebar with Desktop Sticky & Mobile Drawer */}
      <Sidebar
        activeTab={activeTab}
        setActiveTab={handleTabChange}
        dataSourceMode={dataSourceMode}
        setDataSourceMode={handleDataSourceModeChange}
        isConnected={isConnected}
        onOpenCustomizer={() => setIsCustomizerOpen(true)}
        isMobileOpen={isMobileSidebarOpen}
        onCloseMobile={() => setIsMobileSidebarOpen(false)}
      />

      {/* Main Content Area */}
      <div className="flex-1 flex flex-col min-w-0">
        <Header
          activeTab={activeTab}
          dataSourceMode={dataSourceMode}
          currentRole={currentRole}
          onRoleChange={handleRoleChange}
          onRefresh={() => loadDataForTab(activeTab)}
          isRefreshing={isRefreshing}
          onQuickAction={handleQuickAction}
          onToggleMobileSidebar={() => setIsMobileSidebarOpen(!isMobileSidebarOpen)}
        />

        <main className="flex-1 p-4 sm:p-6 lg:p-8 max-w-7xl w-full mx-auto space-y-5">
          {/* TAB: DASHBOARD */}
          {activeTab === 'dashboard' && (
            <div className="space-y-5">
              {widgets
                .filter((w) => w.visible)
                .sort((a, b) => a.order - b.order)
                .map((widget) => {
                  switch (widget.id) {
                    case 'kpis':
                      return (
                        <KpiCards
                          key="kpis"
                          students={students}
                          courses={courses}
                          departments={departments}
                          enrollments={enrollments}
                        />
                      );
                    case 'trend':
                      return <EnrollmentTrendChart key="trend" />;
                    case 'departments':
                      return (
                        <div key="departments" className="grid grid-cols-1 lg:grid-cols-2 gap-4">
                          <DepartmentDistributionChart stats={departmentStats} />
                          <GpaDistributionChart students={students} />
                        </div>
                      );
                    case 'gpa':
                      return null; // Handled alongside departments in grid
                    case 'activity':
                      return <RecentActivityFeed key="activity" activities={activities} />;
                    case 'shortcuts':
                      return <QuickActions key="shortcuts" onAction={handleQuickAction} />;
                    default:
                      return null;
                  }
                })}
            </div>
          )}

          {/* TAB: STUDENTS CRUD */}
          {activeTab === 'students' && (
            <StudentList
              students={students}
              departments={departments}
              currentRole={currentRole}
              onCreateStudent={(input) => api.createStudent(input)}
              onUpdateStudent={(id, input) => api.updateStudent(id, input)}
              onDeleteStudent={(id) => api.deleteStudent(id)}
            />
          )}

          {/* TAB: DEPARTMENTS CRUD */}
          {activeTab === 'departments' && (
            <DepartmentList
              departments={departments}
              currentRole={currentRole}
              onCreateDepartment={(input) => api.createDepartment(input)}
              onUpdateDepartment={(id, input) => api.updateDepartment(id, input)}
              onDeleteDepartment={(id) => api.deleteDepartment(id)}
              onSelectDepartmentDetails={(id) => api.getDepartmentById(id)}
            />
          )}

          {/* TAB: COURSES CRUD */}
          {activeTab === 'courses' && (
            <CourseList
              courses={courses}
              departments={departments}
              instructors={instructors}
              currentRole={currentRole}
              onCreateCourse={(input) => api.createCourse(input)}
              onUpdateCourse={(id, input) => api.updateCourse(id, input)}
              onDeleteCourse={(id) => api.deleteCourse(id)}
            />
          )}

          {/* TAB: ENROLLMENTS CRUD */}
          {activeTab === 'enrollments' && (
            <EnrollmentList
              enrollments={enrollments}
              students={students}
              courses={courses}
              currentRole={currentRole}
              onCreateEnrollment={(input) => api.createEnrollment(input)}
              onUpdateEnrollment={(id, input) => api.updateEnrollment(id, input)}
              onDeleteEnrollment={(id) => api.deleteEnrollment(id)}
            />
          )}

          {/* TAB: VIEW MODELS & ANALYTICS */}
          {activeTab === 'analytics' && (
            <AnalyticsView
              departmentStats={departmentStats}
              studentDashboards={studentDashboards}
            />
          )}

          {/* TAB: SCHEMA DEFINITION & AST INSPECTOR */}
          {activeTab === 'schema-explorer' && <SchemaInspector />}

          {/* TAB: GRAPHQL STUDIO PLAYGROUND */}
          {activeTab === 'graphql-playground' && <GraphQlPlayground />}
        </main>
      </div>

      {/* Widget Customizer Modal */}
      <WidgetCustomizerModal
        isOpen={isCustomizerOpen}
        onClose={() => setIsCustomizerOpen(false)}
        widgets={widgets}
        onUpdateWidgets={setWidgets}
      />

      {/* Quick Create Student Modal */}
      <StudentFormModal
        isOpen={quickStudentOpen}
        onClose={() => setQuickStudentOpen(false)}
        student={null}
        departments={departments}
        onSubmit={async (input) => {
          await api.createStudent(input as CreateStudentInput);
        }}
      />

      {/* Quick Create Course Modal */}
      <CourseFormModal
        isOpen={quickCourseOpen}
        onClose={() => setQuickCourseOpen(false)}
        course={null}
        departments={departments}
        instructors={instructors}
        onSubmit={async (input) => {
          await api.createCourse(input as CreateCourseInput);
        }}
      />

      {/* Quick Create Enrollment Modal */}
      <EnrollmentFormModal
        isOpen={quickEnrollmentOpen}
        onClose={() => setQuickEnrollmentOpen(false)}
        enrollment={null}
        students={students}
        courses={courses}
        onSubmit={async (input) => {
          await api.createEnrollment(input as CreateEnrollmentInput);
        }}
      />
    </div>
  );
}

export default App;
