# GraphORM Design System — Master Specification

> **Source of Truth:** `design-system/MASTER.md`  
> **Applies to:** GraphORM Official Showcase, Interactive Demo, and Documentation Surfaces.  
> **Aesthetic Philosophy:** Neutral Developer Charcoal (GitHub Dark), High-Legibility Technical Typography, Zero-Fluff Architecture.

---

## 1. Design Philosophy & Product Identity

GraphORM is an open-source, schema-driven ORM for Java 21 and Spring Boot 3 that automatically generates type-safe database clients and Spring for GraphQL bindings. 

### Core Design Pillars
1. **Engineering Grounding:** Visually reflect the precision of Java compilers, AST parsers, and database engines. Avoid consumer SaaS clichés (floating blobs, purple or violet gradients, glowing backgrounds, large violet surfaces, bouncy animations, generic bento grids).
2. **Code as Hero:** Real code (Prisma-like schema, Java 21 records, GraphQL selections, generated SQL) is the central visual medium. Code editors and terminal outputs must feel authentic, legible, and syntax-accurate.
3. **Neutral Developer Charcoal:** Neutral grey-black canvas (`#0D1117`) with crisp charcoal panels (`#161B22`), elevated controls (`#21262D`), and structural rules (`#30363D`). Zero muddy blue wash.
4. **Information Density with Breathing Room:** Give complex architectural concepts (view models, join pruning, DataLoader batching) clear structural diagrams and tabular comparisons without clutter.
5. **Restraint & Single Bold Focus:** Spend aesthetic boldness on the **Interactive Query Pipeline Demo** (interactive schema → Java → GraphQL → SQL execution flow). Keep surrounding chrome, headers, and cards calm and structured.

---

## 2. Color System & Semantic Tokens

The color palette is built on a neutral developer charcoal foundation (GitHub Dark style), engineered for long developer sessions reading code, with WCAG AAA contrast for text and WCAG AA for interactive controls.

### 2.1 Core Palette (Neutral Developer Charcoal)

| Role | Hex | Tailwind Token | Usage |
|---|---|---|---|
| **Main Background** | `#0D1117` | `bg-[#0D1117]` / `bg-canvas` | Primary page backdrop, neutral charcoal-black |
| **Panels & Cards** | `#161B22` | `bg-[#161B22]` / `bg-surface` | Code editor canvas, component backdrops |
| **Elevated Surfaces** | `#21262D` | `bg-[#21262D]` / `bg-elevated` | Hover states, tab bars, dropdowns, headers |
| **Borders & Dividers** | `#30363D` | `border-[#30363D]` / `border-subtle` | Crisp 1px structural dividing lines |
| **Border Focus / Active** | `#38BDF8` | `border-sky-400` / `ring-sky-400` | Keyboard focus ring, active editor tabs |
| **Text Primary** | `#F0F6FC` | `text-slate-100` / `text-white` | Headlines, code text, high-emphasis copy |
| **Text Secondary** | `#8B949E` | `text-slate-400` | Explanatory copy, documentation paragraphs |
| **Text Muted** | `#6E7681` | `text-slate-500` | Line numbers, metadata tags, breadcrumbs |

### 2.2 Primary Accent Colors (Strictly No Purple / Violet)

| Token | Hex | Usage |
|---|---|---|
| **Accent Sky Blue** | `#38BDF8` | Primary brand accent, Java 21 records, type keywords, active tabs, client calls, navigation highlights |
| **Accent Emerald** | `#10B981` | Query execution, pass badges, SQL benchmark gains, live runtime status |
| **Accent Amber** | `#F59E0B` | View Models (`view`), aggregated columns, AST highlights, warnings, schema directives |
| **Accent Rose** | `#F43F5E` | N+1 alerts, manual boilerplate warnings, compiler errors (used sparingly) |

---

## 3. Typography & Type Scale

The typography pairs a precision monospaced typeface for code and metrics with a clean, neutral grotesque sans-serif for UI and documentation.

### 3.1 Typeface Families
- **Monospace (Code, AST, Metrics, Keys):** `JetBrains Mono`, `Fira Code`, monospace
- **Sans-Serif (Headings, Body, Controls):** `Inter`, `Plus Jakarta Sans`, `system-ui`, sans-serif

```css
@import url('https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&family=JetBrains+Mono:wght@400;500;600&display=swap');
```

### 3.2 Typographic Hierarchy & Scale

| Level | Size | Weight | Line Height | Tracking | Usage |
|---|---|---|---|---|---|
| **Display Hero** | `2.5rem - 3.75rem` (40–60px) | 700 Bold | 1.1 | `-0.025em` | Hero main product statement |
| **H1 Section** | `1.875rem - 2.25rem` (30–36px) | 700 Bold | 1.2 | `-0.02em` | Section headers |
| **H2 Feature** | `1.25rem - 1.5rem` (20–24px) | 600 SemiBold | 1.3 | `-0.01em` | Subsection, card titles |
| **Body Large** | `1.125rem` (18px) | 400 Regular | 1.6 | `normal` | Hero sub-copy, lead intros |
| **Body Base** | `0.9375rem` (15px) | 400 Regular | 1.6 | `normal` | General descriptions, docs, tables |
| **Code Display** | `0.875rem` (14px) | 400 / 500 | 1.55 | `0` | Code blocks, editor lines, SQL dumps |
| **Small Label** | `0.75rem` (12px) | 600 SemiBold | 1.4 | `0.04em` | Status chips, badges, line numbers |

**Typography Rules:**
- Max line length for prose: strictly under 75 characters (`max-w-prose`).
- Never accent random single words in headlines with italic gradients.
- Never use tracked uppercase for entire body sentences.

---

## 4. Spacing, Grid & Layout System

- **Base Unit:** 4px grid. Standard scale: 4px (`xs`), 8px (`sm`), 12px (`md`), 16px (`lg`), 24px (`xl`), 32px (`2xl`), 48px (`3xl`), 64px (`4xl`).
- **Container Max-Widths:**
  - Standard Content: `max-w-7xl` (1280px) centered with responsive padding (`px-4 sm:px-6 lg:px-8`).
  - Documentation / Deep-Dive Prose: `max-w-4xl` (896px).
  - Code Comparison Grids: Full container width with equal-split columns (`grid-cols-1 lg:grid-cols-2`).
- **Surface Elevation & Radii:**
  - Panels & Code Blocks: `rounded-lg` (8px), `border border-[#30363D]`, background `#161B22`.
  - Badges & Pills: `rounded-md` (6px), compact vertical padding (`py-0.5 px-2`).
  - Buttons: `rounded-md` (6px), height 38px / 44px, crisp border `#30363D`.
- **Shadows:** Minimal soft ambient shadows only (`0 4px 6px -1px rgba(0, 0, 0, 0.4)`). Strictly no colored neon or purple drop-shadows.

---

## 5. Component Design Language

### 5.1 Interactive Code Windows (The Core Component)
- **Chrome:** Sleek header with simulated terminal status dots (muted `#484F58`), active file title (`schema.graphorm`, `StudentService.java`), language pill, and a copy button with instant checkmark feedback.
- **Editor Area:** Background `#0E1117` / `#161B22`, left line numbers in `#6E7681`, syntax coloring adhering strictly to role:
  - Keywords & Types: Sky Blue (`#38BDF8`)
  - Aggregations & Directives: Amber (`#F59E0B`)
  - Strings & Identifiers: Emerald (`#10B981`)
  - Annotations & Comments: Slate (`#8B949E`)
- **Status Footer:** Shows compilation / execution latency (e.g. `Compiled: 0ms (In-Memory JSR-199)` or `SQL: 1 Query (Eliminated N+1)`).

### 5.2 Buttons & Controls
- **Primary Action (Explore / Get Started):**
  - Background: Emerald `#10B981` hovering to `#059669`.
  - Text: Slate-950 font-semibold.
  - Interaction: 150ms transition, no layout shift (`cursor-pointer`).
- **Secondary Action (Launch Portal / GitHub / Docs):**
  - Background: Surface elevated `#21262D` with border `#30363D` hovering to `#30363D`.
  - Text: Slate-200.
- **Filter Tabs / Segmented Controls:**
  - Background track `#0D1117` with active tab `#21262D`, border `#38BDF8` indicator.

### 5.3 Badges & Status Chips
- **Production Ready (`v1.0.0`):** Background `rgba(16, 185, 129, 0.12)`, text `#34D399`, border `#10B981/30`.
- **Type-Safe / Architecture (`Java 21` / `AST`):** Background `rgba(56, 189, 248, 0.12)`, text `#38BDF8`, border `#38BDF8/30`.
- **View Models / Projections (`Read-Only`):** Background `rgba(245, 158, 11, 0.12)`, text `#FBBF24`, border `#F59E0B/30`.
- **Planned / Future Target (`MySQL / H2`):** Background `rgba(139, 148, 158, 0.12)`, text `#8B949E`, border `#30363D`.

---

## 6. Interaction, Motion & Accessibility

- **Motion Principles:**
  - Micro-interactions only: 150–250ms cubic-bezier transitions (`ease-out`).
  - Tab switching in the interactive demo uses immediate cross-fade (`opacity 150ms`).
  - Never animate cards on hover using scale transforms that push adjacent content.
- **Accessibility (WCAG 2.1 AA+ Compliance):**
  - High text-to-background contrast (Minimum 7:1 for headers, 4.8:1 for secondary copy).
  - Explicit `:focus-visible` ring on every clickable item (`ring-2 ring-sky-400 ring-offset-2 ring-offset-[#0D1117]`).
  - Explicit `cursor: pointer` on all interactive cards, tabs, and buttons.
  - Zero emojis as UI icons; 100% SVG iconography from Lucide with appropriate `aria-hidden` or `aria-label`.
  - Full respect for `prefers-reduced-motion: reduce`.
