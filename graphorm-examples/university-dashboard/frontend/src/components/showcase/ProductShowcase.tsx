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
    <div className="min-h-screen bg-canvas text-text-primary font-sans selection:bg-accent/30 selection:text-accent">
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
