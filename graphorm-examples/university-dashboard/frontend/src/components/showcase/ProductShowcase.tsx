import React from 'react';
import { ShowcaseNav } from './ShowcaseNav';
import { ShowcaseHero } from './ShowcaseHero';
import { BoilerplateTaxComparison } from './BoilerplateTaxComparison';
import { QueryPipelineDemo } from './QueryPipelineDemo';
import { ViewModelsDeepDive } from './ViewModelsDeepDive';
import { ArchitectureExplorer } from './ArchitectureExplorer';
import { FeaturesGrid } from './FeaturesGrid';
import { QuickstartSection } from './QuickstartSection';
import { ShowcaseFooter } from './ShowcaseFooter';

interface ProductShowcaseProps {
  onOpenPortal: () => void;
}

export const ProductShowcase: React.FC<ProductShowcaseProps> = ({ onOpenPortal }) => {
  return (
    <div className="min-h-screen bg-[#0D1117] text-slate-100 font-sans selection:bg-sky-500/30 selection:text-sky-200">
      <ShowcaseNav onOpenPortal={onOpenPortal} />
      <main>
        <ShowcaseHero onOpenPortal={onOpenPortal} />
        <BoilerplateTaxComparison />
        <QueryPipelineDemo />
        <ViewModelsDeepDive />
        <ArchitectureExplorer />
        <FeaturesGrid />
        <QuickstartSection onOpenPortal={onOpenPortal} />
      </main>
      <ShowcaseFooter />
    </div>
  );
};
