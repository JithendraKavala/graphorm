import React, { useState } from 'react';
import { Terminal, Copy, Check, ArrowRight } from 'lucide-react';

interface QuickstartSectionProps {
  onOpenPortal: () => void;
}

export const QuickstartSection: React.FC<QuickstartSectionProps> = ({ onOpenPortal }) => {
  const [buildTool, setBuildTool] = useState<'maven' | 'gradle'>('maven');
  const [copiedSnippet, setCopiedSnippet] = useState<string | null>(null);

  const handleCopy = (id: string, text: string) => {
    navigator.clipboard.writeText(text);
    setCopiedSnippet(id);
    setTimeout(() => setCopiedSnippet(null), 2000);
  };

  const mavenSnippet = `<dependency>
    <groupId>com.graphorm</groupId>
    <artifactId>graphorm-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>`;

  const gradleSnippet = `implementation 'com.graphorm:graphorm-spring-boot-starter:1.0.0'`;

  const applicationProps = `spring.datasource.url=jdbc:postgresql://localhost:5432/university
spring.datasource.username=postgres
spring.datasource.password=secret

# GraphORM Schema & Code Generation
graphorm.schema.path=classpath:schema.graphorm
graphorm.codegen.target-package=com.example.generated
graphorm.graphql.enabled=true`;

  const javaCode = `@Service
public class StudentService {
    private final GraphOrmClient db;

    public StudentService(GraphOrmClient db) {
        this.db = db;
    }

    public List<StudentDashboard> getDashboard() {
        return db.studentDashboard().findAll();
    }
}`;

  return (
    <section id="quickstart" className="py-20 bg-canvas border-t border-border">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="text-center max-w-3xl mx-auto space-y-4 mb-14">
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-md bg-surface border border-accent/30 text-accent font-mono text-xs font-semibold">
            <Terminal className="w-3.5 h-3.5" />
            <span>Developer Quickstart</span>
          </div>

          <h2 className="text-2xl sm:text-4xl font-bold text-text-primary tracking-tight">
            Start Building with GraphORM in 60 Seconds
          </h2>

          <p className="text-text-primary text-sm sm:text-base leading-relaxed">
            GraphORM integrates natively with Spring Boot 3.3.x auto-configuration. No custom configuration classes or manual entity mappings required.
          </p>
        </div>

        {/* 3 Step Quickstart Grid */}
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6 font-mono text-xs">
          {/* Step 1: Add Dependency */}
          <div className="rounded-lg bg-surface border border-border overflow-hidden flex flex-col justify-between">
            <div>
              <div className="bg-surface px-4 py-3 border-b border-border flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <span className="w-5 h-5 rounded-full bg-accent/20 text-accent font-bold flex items-center justify-center text-[10px]">
                    1
                  </span>
                  <span className="text-text-primary font-bold">Add Starter Dependency</span>
                </div>

                <div className="flex items-center gap-1 bg-elevated p-0.5 rounded border border-border text-[10px]">
                  <button
                    onClick={() => setBuildTool('maven')}
                    className={`px-2 py-0.5 rounded cursor-pointer ${
                      buildTool === 'maven' ? 'bg-surface text-text-primary font-semibold' : 'text-text-muted'
                    }`}
                  >
                    Maven
                  </button>
                  <button
                    onClick={() => setBuildTool('gradle')}
                    className={`px-2 py-0.5 rounded cursor-pointer ${
                      buildTool === 'gradle' ? 'bg-surface text-text-primary font-semibold' : 'text-text-muted'
                    }`}
                  >
                    Gradle
                  </button>
                </div>
              </div>

              <div className="p-4 bg-canvas text-text-primary overflow-x-auto min-h-[160px] flex items-center">
                <pre className="text-accent whitespace-pre">
                  {buildTool === 'maven' ? mavenSnippet : gradleSnippet}
                </pre>
              </div>
            </div>

            <div className="p-3 bg-surface border-t border-border flex items-center justify-between">
              <span className="text-text-muted text-[11px]">Auto-Configured Beans</span>
              <button
                onClick={() => handleCopy('dep', buildTool === 'maven' ? mavenSnippet : gradleSnippet)}
                className="flex items-center gap-1.5 text-text-muted hover:text-text-primary transition-colors cursor-pointer"
              >
                {copiedSnippet === 'dep' ? <Check className="w-3.5 h-3.5 text-emerald-400" /> : <Copy className="w-3.5 h-3.5" />}
                <span>Copy</span>
              </button>
            </div>
          </div>

          {/* Step 2: Configure Properties */}
          <div className="rounded-lg bg-surface border border-border overflow-hidden flex flex-col justify-between">
            <div>
              <div className="bg-surface px-4 py-3 border-b border-border flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <span className="w-5 h-5 rounded-full bg-amber-500/20 text-amber-400 font-bold flex items-center justify-center text-[10px]">
                    2
                  </span>
                  <span className="text-text-primary font-bold">application.properties</span>
                </div>
                <span className="text-[10px] text-text-muted">Spring Boot 3</span>
              </div>

              <div className="p-4 bg-canvas text-text-primary overflow-x-auto min-h-[160px] flex items-center">
                <pre className="text-amber-300 whitespace-pre">{applicationProps}</pre>
              </div>
            </div>

            <div className="p-3 bg-surface border-t border-border flex items-center justify-between">
              <span className="text-text-muted text-[11px]">PostgreSQL 15+ DataSource</span>
              <button
                onClick={() => handleCopy('props', applicationProps)}
                className="flex items-center gap-1.5 text-text-muted hover:text-text-primary transition-colors cursor-pointer"
              >
                {copiedSnippet === 'props' ? <Check className="w-3.5 h-3.5 text-emerald-400" /> : <Copy className="w-3.5 h-3.5" />}
                <span>Copy</span>
              </button>
            </div>
          </div>

          {/* Step 3: Inject & Query */}
          <div className="rounded-lg bg-surface border border-border overflow-hidden flex flex-col justify-between">
            <div>
              <div className="bg-surface px-4 py-3 border-b border-border flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <span className="w-5 h-5 rounded-full bg-emerald-500/20 text-emerald-400 font-bold flex items-center justify-center text-[10px]">
                    3
                  </span>
                  <span className="text-text-primary font-bold">Inject GraphOrmClient</span>
                </div>
                <span className="text-[10px] text-emerald-400">Type-Safe</span>
              </div>

              <div className="p-4 bg-canvas text-text-primary overflow-x-auto min-h-[160px] flex items-center">
                <pre className="text-emerald-300 whitespace-pre text-[11px]">{javaCode}</pre>
              </div>
            </div>

            <div className="p-3 bg-surface border-t border-border flex items-center justify-between">
              <span className="text-text-muted text-[11px]">Zero-Reflection Client</span>
              <button
                onClick={() => handleCopy('java', javaCode)}
                className="flex items-center gap-1.5 text-text-muted hover:text-text-primary transition-colors cursor-pointer"
              >
                {copiedSnippet === 'java' ? <Check className="w-3.5 h-3.5 text-emerald-400" /> : <Copy className="w-3.5 h-3.5" />}
                <span>Copy</span>
              </button>
            </div>
          </div>
        </div>

        {/* Live Reference Portal Callout Banner */}
        <div className="mt-12 p-6 rounded-lg bg-surface border border-border flex flex-col sm:flex-row items-center justify-between gap-6">
          <div className="space-y-1 text-center sm:text-left">
            <h3 className="text-lg font-bold text-text-primary flex items-center gap-2 justify-center sm:justify-start">
              <span>Explore the University Portal Reference Application</span>
              <span className="text-[10px] font-mono px-2 py-0.5 rounded bg-emerald-500/15 text-emerald-300 border border-emerald-500/30">
                Live Demo
              </span>
            </h3>
            <p className="text-text-muted text-xs font-sans max-w-xl leading-relaxed">
              Experience GraphORM running in an end-to-end full stack application with CRUD operations, AST Schema Inspector, live GraphQL playground, and View Models analytics.
            </p>
          </div>

          <button
            onClick={onOpenPortal}
            className="shrink-0 flex items-center gap-2 px-5 py-2.5 rounded-md font-mono text-xs font-bold text-text-on-accent bg-gradient-to-r from-accent to-accent-secondary hover:brightness-110 shadow-sm transition-all cursor-pointer"
          >
            <span>Launch Live Portal</span>
            <ArrowRight className="w-4 h-4" />
          </button>
        </div>
      </div>
    </section>
  );
};
