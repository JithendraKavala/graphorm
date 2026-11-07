import React, { useState } from 'react';
import { ShieldCheck, Box, ArrowDown, Database, Cpu } from 'lucide-react';

export const ArchitectureExplorer: React.FC = () => {
  const [selectedModule, setSelectedModule] = useState<'core' | 'schema' | 'generator' | 'jdbc' | 'graphql' | 'starter' | 'cli'>('core');

  const modules = {
    core: {
      name: 'graphorm-core',
      role: 'Foundational Models & AST',
      deps: 'Zero external dependencies (No DB, No Spring)',
      description: 'Defines the schema AST, ModelRegistry, Unified Query Model, and domain exceptions. Acts as the immutable metadata backbone for all other modules.',
      files: ['ModelMetadata.java', 'FieldMetadata.java', 'QueryAst.java', 'ReadOnlyViewException.java'],
    },
    schema: {
      name: 'graphorm-schema',
      role: 'ANTLR4 Parser & Semantic Validator',
      deps: 'Core + ANTLR4 Runtime only',
      description: 'Parses schema.graphorm files using ANTLR4 grammar. Validates semantic invariants (unique IDs, valid relation targets, read-only view constraints) and emits diagnostic errors with line and column caret pointers.',
      files: ['GraphOrmSchema.g4', 'AstVisitor.java', 'SchemaValidator.java', 'DiagnosticFormatter.java'],
    },
    generator: {
      name: 'graphorm-generator',
      role: 'JavaPoet Java 21 Code Generation',
      deps: 'Core + JavaPoet only',
      description: 'Generates immutable Java 21 records with fluent with* mutators, Criteria filter builders, and type-safe ModelAccessor and ReadOnlyModelAccessor client APIs.',
      files: ['RecordGenerator.java', 'CriteriaBuilderGenerator.java', 'ClientApiGenerator.java'],
    },
    jdbc: {
      name: 'graphorm-jdbc',
      role: 'SQL Generation & PostgreSQL Dialect',
      deps: 'Core + java.sql only (No Spring Framework)',
      description: 'Translates the unified query AST into optimized SQL using PostgresDialect. Executes queries via dynamic RowMappers, handles batch loading, and manages transaction state via TransactionManager SPI.',
      files: ['PostgresDialect.java', 'JdbcExecutor.java', 'DynamicRowMapper.java', 'TransactionManager.java'],
    },
    graphql: {
      name: 'graphorm-graphql',
      role: 'Auto DataFetchers & DataLoader Batching',
      deps: 'Core + JDBC + GraphQL Java',
      description: 'Generates GraphQL SDL at runtime and registers generic DataFetchers. Analyzes selection sets to prune SQL projections and instruments DataLoaders to batch relationship queries into IN (?, ?, ...) calls.',
      files: ['GraphQlSdlGenerator.java', 'GenericDataFetcher.java', 'GraphOrmDataLoaderInstrumentation.java'],
    },
    starter: {
      name: 'graphorm-spring-boot-starter',
      role: 'Spring Boot 3 Auto-Configuration',
      deps: 'Spring Boot 3 + GraphQL + JDBC',
      description: 'Auto-configures GraphOrmClient beans, adapts Spring TransactionManager to DataSourceUtils, and registers customizers for Spring for GraphQL startup.',
      files: ['GraphOrmAutoConfiguration.java', 'SpringTransactionManagerAdapter.java', 'GraphQlSourceCustomizer.java'],
    },
    cli: {
      name: 'graphorm-cli',
      role: 'Developer Picocli CLI Tooling',
      deps: 'Core + Schema + Generator + JDBC',
      description: 'Standalone developer CLI providing commands: graphorm init, graphorm validate, graphorm generate, and live PostgreSQL schema reverse-engineering with graphorm introspect.',
      files: ['GraphOrmCli.java', 'ValidateCommand.java', 'GenerateCommand.java', 'IntrospectCommand.java'],
    },
  };

  return (
    <section id="architecture" className="py-20 bg-[#0D1117] border-t border-[#30363D]">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="text-center max-w-3xl mx-auto space-y-4 mb-14">
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-md bg-[#161B22] border border-sky-500/30 text-sky-400 font-mono text-xs font-semibold">
            <Box className="w-3.5 h-3.5" />
            <span>Zero-Leakage Architecture</span>
          </div>

          <h2 className="text-2xl sm:text-4xl font-bold text-[#F0F6FC] tracking-tight">
            Multi-Module Architecture & Invariants
          </h2>

          <p className="text-[#8B949E] text-sm sm:text-base leading-relaxed">
            GraphORM enforces strict dependency boundaries between modules. Foundation modules have zero database or framework dependencies, ensuring high portability and testability.
          </p>
        </div>

        {/* Interactive Architecture Flow & Inspector */}
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 items-start">
          {/* Module Diagram Grid (Left) */}
          <div className="lg:col-span-7 space-y-3 font-mono text-xs">
            {/* Tier 1: Foundation */}
            <div className="p-3 rounded-lg bg-[#161B22] border border-[#30363D] flex items-center justify-between">
              <span className="text-[#8B949E] font-semibold uppercase text-[10px] tracking-wider">Foundation Tier</span>
              <span className="text-[10px] text-emerald-400 bg-[#21262D] px-2 py-0.5 rounded border border-[#30363D]">Zero DB / Spring</span>
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <button
                onClick={() => setSelectedModule('core')}
                className={`p-3 rounded-lg border text-left transition-colors cursor-pointer ${
                  selectedModule === 'core'
                    ? 'bg-[#21262D] border-sky-400 text-[#F0F6FC]'
                    : 'bg-[#161B22] border-[#30363D] text-[#8B949E] hover:text-[#F0F6FC] hover:bg-[#21262D]'
                }`}
              >
                <div className="flex items-center justify-between">
                  <span className="font-bold text-[#F0F6FC]">graphorm-core</span>
                  <Box className="w-3.5 h-3.5 text-sky-400" />
                </div>
                <p className="text-[10px] text-[#6E7681] mt-1">AST, Models, Exceptions</p>
              </button>

              <button
                onClick={() => setSelectedModule('schema')}
                className={`p-3 rounded-lg border text-left transition-colors cursor-pointer ${
                  selectedModule === 'schema'
                    ? 'bg-[#21262D] border-sky-400 text-[#F0F6FC]'
                    : 'bg-[#161B22] border-[#30363D] text-[#8B949E] hover:text-[#F0F6FC] hover:bg-[#21262D]'
                }`}
              >
                <div className="flex items-center justify-between">
                  <span className="font-bold text-[#F0F6FC]">graphorm-schema</span>
                  <Cpu className="w-3.5 h-3.5 text-sky-400" />
                </div>
                <p className="text-[10px] text-[#6E7681] mt-1">ANTLR4 Grammar & Validator</p>
              </button>
            </div>

            {/* Down Connector */}
            <div className="flex justify-center py-0.5">
              <ArrowDown className="w-4 h-4 text-slate-600" />
            </div>

            {/* Tier 2: Engines */}
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <button
                onClick={() => setSelectedModule('generator')}
                className={`p-3 rounded-lg border text-left transition-colors cursor-pointer ${
                  selectedModule === 'generator'
                    ? 'bg-[#21262D] border-sky-400 text-[#F0F6FC]'
                    : 'bg-[#161B22] border-[#30363D] text-[#8B949E] hover:text-[#F0F6FC] hover:bg-[#21262D]'
                }`}
              >
                <div className="flex items-center justify-between">
                  <span className="font-bold text-[#F0F6FC]">graphorm-generator</span>
                  <Cpu className="w-3.5 h-3.5 text-amber-400" />
                </div>
                <p className="text-[10px] text-[#6E7681] mt-1">JavaPoet Java 21 Records</p>
              </button>

              <button
                onClick={() => setSelectedModule('jdbc')}
                className={`p-3 rounded-lg border text-left transition-colors cursor-pointer ${
                  selectedModule === 'jdbc'
                    ? 'bg-[#21262D] border-sky-400 text-[#F0F6FC]'
                    : 'bg-[#161B22] border-[#30363D] text-[#8B949E] hover:text-[#F0F6FC] hover:bg-[#21262D]'
                }`}
              >
                <div className="flex items-center justify-between">
                  <span className="font-bold text-[#F0F6FC]">graphorm-jdbc</span>
                  <Database className="w-3.5 h-3.5 text-emerald-400" />
                </div>
                <p className="text-[10px] text-[#6E7681] mt-1">PostgresDialect & RowMapper</p>
              </button>
            </div>

            {/* Down Connector */}
            <div className="flex justify-center py-0.5">
              <ArrowDown className="w-4 h-4 text-slate-600" />
            </div>

            {/* Tier 3: Integrations & Starters */}
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
              <button
                onClick={() => setSelectedModule('graphql')}
                className={`p-3 rounded-lg border text-left transition-colors cursor-pointer ${
                  selectedModule === 'graphql'
                    ? 'bg-[#21262D] border-sky-400 text-[#F0F6FC]'
                    : 'bg-[#161B22] border-[#30363D] text-[#8B949E] hover:text-[#F0F6FC] hover:bg-[#21262D]'
                }`}
              >
                <span className="font-bold text-[#F0F6FC] block truncate">graphorm-graphql</span>
                <p className="text-[10px] text-[#6E7681] mt-1">DataLoaders & Fetchers</p>
              </button>

              <button
                onClick={() => setSelectedModule('starter')}
                className={`p-3 rounded-lg border text-left transition-colors cursor-pointer ${
                  selectedModule === 'starter'
                    ? 'bg-[#21262D] border-sky-400 text-[#F0F6FC]'
                    : 'bg-[#161B22] border-[#30363D] text-[#8B949E] hover:text-[#F0F6FC] hover:bg-[#21262D]'
                }`}
              >
                <span className="font-bold text-[#F0F6FC] block truncate">spring-boot-starter</span>
                <p className="text-[10px] text-[#6E7681] mt-1">Auto-Configuration</p>
              </button>

              <button
                onClick={() => setSelectedModule('cli')}
                className={`p-3 rounded-lg border text-left transition-colors cursor-pointer ${
                  selectedModule === 'cli'
                    ? 'bg-[#21262D] border-sky-400 text-[#F0F6FC]'
                    : 'bg-[#161B22] border-[#30363D] text-[#8B949E] hover:text-[#F0F6FC] hover:bg-[#21262D]'
                }`}
              >
                <span className="font-bold text-[#F0F6FC] block truncate">graphorm-cli</span>
                <p className="text-[10px] text-[#6E7681] mt-1">Developer CLI Tool</p>
              </button>
            </div>
          </div>

          {/* Module Inspector Detail Box (Right) */}
          <div className="lg:col-span-5 rounded-lg bg-[#161B22] border border-[#30363D] overflow-hidden font-mono text-xs shadow-md">
            <div className="bg-[#161B22] px-4 py-3 border-b border-[#30363D] flex items-center justify-between">
              <span className="text-[#F0F6FC] font-bold">{modules[selectedModule].name}</span>
              <span className="text-[10px] px-2 py-0.5 rounded bg-[#21262D] text-sky-400 border border-[#30363D]">
                Module Inspector
              </span>
            </div>

            <div className="p-5 space-y-4">
              <div>
                <p className="text-[10px] text-[#8B949E] uppercase tracking-wider font-semibold">Responsibility</p>
                <p className="text-[#F0F6FC] font-semibold mt-0.5">{modules[selectedModule].role}</p>
              </div>

              <div>
                <p className="text-[10px] text-[#8B949E] uppercase tracking-wider font-semibold">Dependency Invariant</p>
                <p className="text-emerald-400 font-medium mt-0.5">{modules[selectedModule].deps}</p>
              </div>

              <div>
                <p className="text-[10px] text-[#8B949E] uppercase tracking-wider font-semibold">Architecture Details</p>
                <p className="text-slate-300 text-xs leading-relaxed mt-0.5 font-sans">
                  {modules[selectedModule].description}
                </p>
              </div>

              <div>
                <p className="text-[10px] text-[#8B949E] uppercase tracking-wider font-semibold">Key Classes</p>
                <div className="flex flex-wrap gap-1.5 mt-1">
                  {modules[selectedModule].files.map((file) => (
                    <span key={file} className="px-2 py-0.5 rounded bg-[#0D1117] text-[#8B949E] border border-[#30363D] text-[10px]">
                      {file}
                    </span>
                  ))}
                </div>
              </div>
            </div>

            <div className="p-3 bg-[#161B22] border-t border-[#30363D] flex items-center gap-2 text-emerald-400 text-[11px]">
              <ShieldCheck className="w-4 h-4 shrink-0" />
              <span>Verified Invariant: Tested with JSR-199 & Testcontainers</span>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
};
