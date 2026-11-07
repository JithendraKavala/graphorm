import { describe, it, expect, beforeEach, vi } from 'vitest';
import { UniversityDataService } from '../api';

describe('UniversityDataService (api.ts)', () => {
  let service: UniversityDataService;

  beforeEach(() => {
    // Disable real network fetch during test
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('Network offline')));
    service = new UniversityDataService();
  });

  describe('Header generation & Auth configuration', () => {
    it('should generate headers without Authorization when auth is empty', () => {
      service.setAuth(null);
      const headers = service.getHeaders();
      expect(headers).toEqual({
        Accept: 'application/json',
      });
      expect(headers['Authorization']).toBeUndefined();
    });

    it('should generate Authorization header when Basic credentials are provided', () => {
      service.setAuth('admin:secretPass');
      const headers = service.getHeaders();
      expect(headers.Authorization).toBe('Basic ' + btoa('admin:secretPass'));
      expect(headers.Accept).toBe('application/json');
    });

    it('should preserve pre-encoded Basic or Bearer auth headers', () => {
      service.setAuth('Bearer token_xyz_123');
      const headers = service.getHeaders();
      expect(headers.Authorization).toBe('Bearer token_xyz_123');
    });

    it('should merge custom headers accurately', () => {
      service.setAuth('user:pass');
      const headers = service.getHeaders({ 'Content-Type': 'application/json', 'X-Custom': '1' });
      expect(headers['Content-Type']).toBe('application/json');
      expect(headers['X-Custom']).toBe('1');
      expect(headers['Authorization']).toBe('Basic ' + btoa('user:pass'));
    });
  });

  describe('Role-Based Access Control & Switching', () => {
    it('should default to ADMIN role with admin:admin123 credentials', () => {
      expect(service.getRole()).toBe('ADMIN');
      expect(service.getAuthHeader()).toBe('Basic ' + btoa('admin:admin123'));
    });

    it('should switch roles and synchronize Basic auth headers accurately', () => {
      service.setRole('INSTRUCTOR');
      expect(service.getRole()).toBe('INSTRUCTOR');
      expect(service.getAuthHeader()).toBe('Basic ' + btoa('instructor:prof123'));

      service.setRole('STUDENT');
      expect(service.getRole()).toBe('STUDENT');
      expect(service.getAuthHeader()).toBe('Basic ' + btoa('student:student123'));

      service.setRole('GUEST');
      expect(service.getRole()).toBe('GUEST');
      expect(service.getAuthHeader()).toBe('');
    });

    it('should reject unauthorized field queries with GraphORM @auth error when role lacks permission', async () => {
      service.setRole('STUDENT');
      const testResult = await service.executeAuthViolationTest();
      expect(testResult.role).toBe('STUDENT');
      expect(testResult.response.errors).toBeDefined();
      expect(testResult.response.errors?.length).toBeGreaterThan(0);
      expect(testResult.response.errors?.[0].message).toContain('Access denied for field "salary"');
    });

    it('should allow authorized field queries when executing as ADMIN', async () => {
      service.setRole('ADMIN');
      const testResult = await service.executeAuthViolationTest();
      expect(testResult.role).toBe('ADMIN');
      expect(testResult.response.errors).toBeUndefined();
    });
  });

  describe('Offline Fallback Data Services', () => {
    it('should provide default departments in offline fallback mode', async () => {
      const departments = await service.getDepartments();
      expect(departments).toBeDefined();
      expect(departments.length).toBeGreaterThanOrEqual(4);
      expect(departments.map((d) => d.code)).toContain('CS');
      expect(departments.map((d) => d.code)).toContain('MATH');
    });

    it('should provide default students and calculate stats in offline fallback mode', async () => {
      const students = await service.getStudents();
      expect(students).toBeDefined();
      expect(students.length).toBeGreaterThanOrEqual(8);

      const stats = await service.getDepartmentStats();
      expect(stats).toBeDefined();
      expect(stats.length).toBeGreaterThanOrEqual(1);

      const dashboards = await service.getStudentDashboards();
      expect(dashboards).toBeDefined();
      expect(dashboards.length).toBe(students.length);
      expect(dashboards[0]).toHaveProperty('enrolledCourses');
      expect(dashboards[0]).toHaveProperty('avgGradePoints');
    });

    it('should execute client-side simulated GraphQL query when offline', async () => {
      const query = `
        query GetOverview {
          students { id name }
          departmentStats { departmentName studentCount }
        }
      `;
      const result = await service.executeGraphQl(query);
      expect(result).toBeDefined();
      expect(result.data).toBeDefined();
      expect(result.data.students).toBeDefined();
      expect(result.data.departmentStats).toBeDefined();
      expect(result.durationMs).toBeGreaterThanOrEqual(0);
    });

    it('should support switching between graphql and rest modes', () => {
      expect(service.getMode()).toBe('graphql');
      service.setMode('rest');
      expect(service.getMode()).toBe('rest');
      service.setMode('graphql');
      expect(service.getMode()).toBe('graphql');
    });
  });
});
