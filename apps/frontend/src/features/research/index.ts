/**
 * C2 research public surface (UC-C2-01…06, tasks/23 + 26).
 *
 * App routes compose these exports only; hooks and recommendation cards stay internal so the route
 * layer remains routing-only.
 */
export {
  ResearchPanel,
  ResearchJobPanel,
  type ResearchPanelProps,
  type ResearchJobPanelProps,
} from './components/research-panel';
export { useStartResearch, useResearchJob, useResearchTrip } from './hooks/use-research-job';
export { useRankedRecommendations, useSelectRecommendation } from './hooks/use-research-recommendations';
