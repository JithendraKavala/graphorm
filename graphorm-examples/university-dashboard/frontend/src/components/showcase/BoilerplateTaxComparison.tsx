import React, { useState } from 'react';
import { AlertCircle, CheckCircle2, FileCode, XCircle } from 'lucide-react';

export const BoilerplateTaxComparison: React.FC = () => {
  const [activeOldTab, setActiveOldTab] = useState<'entity' | 'dto' | 'mapper' | 'repo' | 'fetcher'>('entity');

  const oldCodeFiles = {
    entity: {
      name: 'StudentEntity.java',
      tag: 'JPA Entity',
      code: `@Entity
@Table(name = "students")
public class StudentEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(unique = true, nullable = false)
    private String email;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private DepartmentEntity department;

    @OneToMany(mappedBy = "student", cascade = CascadeType.ALL)
    private List<EnrollmentEntity> enrollments = new ArrayList<>();

    // 40+ lines of getters, setters, equals, hashCode, constructors...
}`,
    },
    dto: {
      name: 'StudentDto.java',
      tag: 'Projection DTO',
      code: `public record StudentDto(
    Long id,
    String name,
    String email,
    Long departmentId,
    String departmentName,
    List<EnrollmentSummaryDto> enrollments
) {
    // Repeated for every query variation or GraphQL selection requirement...
}`,
    },
    mapper: {
      name: 'StudentRowMapper.java',
      tag: 'RowMapper / MapStruct',
      code: `@Component
public class StudentRowMapper implements RowMapper<StudentDto> {
    @Override
    public StudentDto mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new StudentDto(
            rs.getLong("id"),
            rs.getString("name"),
            rs.getString("email"),
            rs.getLong("department_id"),
            rs.getString("dept_name"),
            Collections.emptyList() // Nested relations require secondary queries!
        );
    }
}`,
    },
    repo: {
      name: 'StudentRepository.java',
      tag: 'Spring Data JPA',
      code: `@Repository
public interface StudentRepository extends JpaRepository<StudentEntity, Long> {
    // Custom JPQL queries to avoid N+1 query disasters
    @Query("SELECT s FROM StudentEntity s " +
           "LEFT JOIN FETCH s.department " +
           "LEFT JOIN FETCH s.enrollments " +
           "WHERE s.department.id = :deptId")
    List<StudentEntity> findAllWithRelationsByDeptId(@Param("deptId") Long deptId);
}`,
    },
    fetcher: {
      name: 'StudentDataFetcher.java',
      tag: 'Spring for GraphQL',
      code: `@Controller
public class StudentDataFetcher {
    @QueryMapping
    public List<StudentDto> students() {
        return studentService.findAll();
    }

    // Manual batch loader required to avoid N+1 query disaster!
    @BatchMapping
    public Mono<Map<StudentDto, DepartmentDto>> department(List<StudentDto> students) {
        return departmentService.getBatchDepartments(students);
    }
}`,
    },
  };

  const graphOrmCode = `// src/main/resources/schema.graphorm
model Student {
  id           Int          @id @default(autoincrement())
  name         String
  email        String       @unique
  departmentId Int?         @map("department_id")
  department   Department?  @relation(fields: [departmentId], references: [id])
  enrollments  Enrollment[]

  @@map("students")
}

// ✨ That's it! GraphORM automatically provides:
// 1. Immutable Java 21 Student record with .with*() mutators
// 2. Type-safe criteria queries: db.student().findMany(...)
// 3. Automated GraphQL schema & DataFetchers with DataLoader batching
// 4. SQL projection & join pruning directly to PostgreSQL`;

  return (
    <section id="comparison" className="py-20 border-t border-[#30363D] bg-[#0D1117]">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="text-center max-w-3xl mx-auto space-y-4 mb-14">
          <div className="inline-flex items-center gap-1.5 px-3 py-1 rounded-md bg-[#161B22] border border-rose-500/30 text-rose-400 font-mono text-xs font-semibold">
            <XCircle className="w-3.5 h-3.5" />
            <span>The Spring Boot Boilerplate Tax</span>
          </div>

          <h2 className="text-2xl sm:text-4xl font-bold text-[#F0F6FC] tracking-tight">
            Stop writing 5 separate files for every database entity.
          </h2>

          <p className="text-[#8B949E] text-sm sm:text-base leading-relaxed">
            In standard JPA and Spring Boot development, adding a single relational model forces developers to manually sync entity mappings, DTO records, row mappers, repository methods, and GraphQL controllers.
          </p>
        </div>

        {/* Side-by-Side Comparison Container */}
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-8 items-start">
          {/* Traditional JPA Box */}
          <div className="rounded-lg bg-[#161B22] border border-[#30363D] overflow-hidden flex flex-col shadow-md">
            <div className="bg-[#161B22] px-4 py-3 border-b border-[#30363D] flex items-center justify-between">
              <div className="flex items-center gap-2">
                <span className="w-2.5 h-2.5 rounded-full bg-rose-500" />
                <span className="font-mono text-xs font-bold text-rose-300 uppercase tracking-wider">
                  The Traditional Way (5 Fragile Files)
                </span>
              </div>
              <span className="text-[11px] font-mono text-[#8B949E]">180+ lines of glue code</span>
            </div>

            {/* File Switcher Tabs */}
            <div className="flex border-b border-[#30363D] bg-[#0D1117] overflow-x-auto text-xs font-mono">
              {(Object.keys(oldCodeFiles) as (keyof typeof oldCodeFiles)[]).map((tabKey) => {
                const isActive = activeOldTab === tabKey;
                return (
                  <button
                    key={tabKey}
                    onClick={() => setActiveOldTab(tabKey)}
                    className={`px-3 py-2 border-b-2 whitespace-nowrap transition-colors flex items-center gap-1.5 cursor-pointer ${
                      isActive
                        ? 'border-rose-400 text-rose-300 bg-[#161B22] font-medium'
                        : 'border-transparent text-[#8B949E] hover:text-[#F0F6FC] hover:bg-[#21262D]'
                    }`}
                  >
                    <FileCode className="w-3.5 h-3.5" />
                    <span>{oldCodeFiles[tabKey].name}</span>
                  </button>
                );
              })}
            </div>

            {/* Code Body */}
            <div className="p-4 bg-[#0A0D12] font-mono text-xs text-[#F0F6FC] overflow-x-auto leading-relaxed max-h-[380px]">
              <div className="text-[11px] text-[#8B949E] mb-2 font-semibold">
                // {oldCodeFiles[activeOldTab].tag}
              </div>
              <pre className="text-slate-300 whitespace-pre">
                {oldCodeFiles[activeOldTab].code}
              </pre>
            </div>

            {/* Footnote Warning */}
            <div className="p-3 bg-[#161B22] border-t border-[#30363D] text-rose-300/90 text-xs font-mono flex items-center gap-2">
              <AlertCircle className="w-4 h-4 text-rose-400 shrink-0" />
              <span>Prone to N+1 queries, detached entity bugs, and schema-drift desynchronization.</span>
            </div>
          </div>

          {/* GraphORM Box */}
          <div className="rounded-lg bg-[#161B22] border border-emerald-500/30 overflow-hidden flex flex-col shadow-md">
            <div className="bg-[#161B22] px-4 py-3 border-b border-[#30363D] flex items-center justify-between">
              <div className="flex items-center gap-2">
                <span className="w-2.5 h-2.5 rounded-full bg-emerald-400" />
                <span className="font-mono text-xs font-bold text-emerald-300 uppercase tracking-wider">
                  The GraphORM Way (1 Unified Schema)
                </span>
              </div>
              <span className="text-[11px] font-mono text-emerald-400 font-semibold">12 clean lines</span>
            </div>

            {/* Single Source of Truth Tab */}
            <div className="flex border-b border-[#30363D] bg-[#0D1117] text-xs font-mono">
              <div className="px-4 py-2 border-b-2 border-emerald-400 text-emerald-300 bg-[#161B22] font-medium flex items-center gap-1.5">
                <FileCode className="w-3.5 h-3.5" />
                <span>schema.graphorm</span>
              </div>
            </div>

            {/* Code Body */}
            <div className="p-4 bg-[#0A0D12] font-mono text-xs text-slate-200 overflow-x-auto leading-relaxed max-h-[380px]">
              <pre className="text-slate-200 whitespace-pre">
                {graphOrmCode}
              </pre>
            </div>

            {/* Footnote Guarantee */}
            <div className="p-3 bg-[#161B22] border-t border-[#30363D] text-emerald-300/90 text-xs font-mono flex items-center gap-2">
              <CheckCircle2 className="w-4 h-4 text-emerald-400 shrink-0" />
              <span>Single source of truth. Zero reflection. Automatic N+1 elimination via DataLoaders.</span>
            </div>
          </div>
        </div>

        {/* Quantified Engineering Benefits */}
        <div className="mt-12 grid grid-cols-2 md:grid-cols-4 gap-4">
          <div className="p-4 rounded-lg bg-[#161B22] border border-[#30363D] text-center">
            <p className="text-2xl sm:text-3xl font-bold font-mono text-emerald-400">-80%</p>
            <p className="text-xs font-medium text-[#F0F6FC] mt-1">Files to Maintain</p>
            <p className="text-[10px] text-[#8B949E] font-mono mt-0.5">5 files down to 1 schema</p>
          </div>

          <div className="p-4 rounded-lg bg-[#161B22] border border-[#30363D] text-center">
            <p className="text-2xl sm:text-3xl font-bold font-mono text-sky-400">100%</p>
            <p className="text-xs font-medium text-[#F0F6FC] mt-1">Type-Safe Client</p>
            <p className="text-[10px] text-[#8B949E] font-mono mt-0.5">Java 21 immutable records</p>
          </div>

          <div className="p-4 rounded-lg bg-[#161B22] border border-[#30363D] text-center">
            <p className="text-2xl sm:text-3xl font-bold font-mono text-sky-400">0</p>
            <p className="text-xs font-medium text-[#F0F6FC] mt-1">Manual DataFetchers</p>
            <p className="text-[10px] text-[#8B949E] font-mono mt-0.5">Auto-wired GraphQL engine</p>
          </div>

          <div className="p-4 rounded-lg bg-[#161B22] border border-[#30363D] text-center">
            <p className="text-2xl sm:text-3xl font-bold font-mono text-amber-400">0 N+1</p>
            <p className="text-xs font-medium text-[#F0F6FC] mt-1">Batch DataLoader</p>
            <p className="text-[10px] text-[#8B949E] font-mono mt-0.5">Automatic SQL IN-batching</p>
          </div>
        </div>
      </div>
    </section>
  );
};
