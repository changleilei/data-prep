package org.talend.dataprep.transformation.pipeline.builder;

import static org.talend.dataprep.transformation.actions.common.ActionMetadata.Behavior.NEED_STATISTICS_INVALID;
import static org.talend.dataprep.transformation.actions.common.ActionMetadata.Behavior.NEED_STATISTICS_PATTERN;
import static org.talend.dataprep.transformation.actions.common.ImplicitParameters.FILTER;

import java.util.List;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

import org.apache.commons.lang.StringUtils;
import org.talend.dataprep.api.dataset.ColumnMetadata;
import org.talend.dataprep.api.dataset.statistics.StatisticsAdapter;
import org.talend.dataprep.api.preparation.Action;
import org.talend.dataprep.quality.AnalyzerService;
import org.talend.dataprep.transformation.actions.common.ActionMetadata;
import org.talend.dataprep.transformation.api.transformer.json.NullAnalyzer;
import org.talend.dataprep.transformation.pipeline.ActionRegistry;
import org.talend.dataprep.transformation.pipeline.Node;
import org.talend.dataprep.transformation.pipeline.node.BasicNode;
import org.talend.dataprep.transformation.pipeline.node.InvalidDetectionNode;
import org.talend.dataprep.transformation.pipeline.node.StatisticsNode;
import org.talend.dataprep.transformation.pipeline.node.TypeDetectionNode;
import org.talend.dataquality.common.inference.Analyzer;
import org.talend.dataquality.common.inference.Analyzers;

public class StatisticsNodesBuilder {
    private Function<List<ColumnMetadata>, Analyzer<Analyzers.Result>> NO_OP_ANALYZER =
            l -> Analyzers.with(NullAnalyzer.INSTANCE);
    private Predicate<ColumnMetadata> ALL_COLUMNS = c -> true;

    private AnalyzerService analyzerService;
    private ActionRegistry actionRegistry;
    private StatisticsAdapter statisticsAdapter;
    private List<Action> actions;
    private List<ColumnMetadata> columns;
    private boolean allowSchemaAnalysis = true;

    private ActionsProfile actionsProfile;
    private Map<Action, ActionMetadata> actionToMetadata;

    private StatisticsNodesBuilder() {
    }

    public static StatisticsNodesBuilder builder() {
        return new StatisticsNodesBuilder();
    }

    public StatisticsNodesBuilder analyzerService(final AnalyzerService analyzerService) {
        this.analyzerService = analyzerService;
        return this;
    }

    public StatisticsNodesBuilder actionRegistry(final ActionRegistry actionRegistry) {
        this.actionRegistry = actionRegistry;
        return this;
    }

    public StatisticsNodesBuilder statisticsAdapter(final StatisticsAdapter statisticsAdapter) {
        this.statisticsAdapter = statisticsAdapter;
        return this;
    }

    public StatisticsNodesBuilder allowSchemaAnalysis(final boolean allowSchemaAnalysis) {
        this.allowSchemaAnalysis = allowSchemaAnalysis;
        return this;
    }

    public StatisticsNodesBuilder actions(final List<Action> actions) {
        this.actions = actions;
        return this;
    }

    public StatisticsNodesBuilder columns(final List<ColumnMetadata> columns) {
        this.columns = columns;
        return this;
    }

    public Node buildPreStatistics() {
        //TODO remove this and fix tests
        if(analyzerService == null) {
            return new BasicNode();
        }

        performActionsProfiling();
        return getTypeDetectionNode(ALL_COLUMNS);
    }

    public Node buildPostStatistics() {
        //TODO remove this and fix tests
        if(analyzerService == null) {
            return new BasicNode();
        }

        performActionsProfiling();
        if (actionsProfile.needFullAnalysis()) {
            return NodeBuilder
                    .from(getTypeDetectionNode(actionsProfile.getFilterForFullAnalysis()))
                    .to(getInvalidDetectionNode(actionsProfile.getFilterForInvalidAnalysis()))
                    .to(getFullStatisticsNode(actionsProfile.getFilterForInvalidAnalysis()))
                    .build();
        }

        if (actionsProfile.needOnlyInvalidAnalysis()) {
            return NodeBuilder
                    .from(getInvalidDetectionNode(actionsProfile.getFilterForInvalidAnalysis()))
                    .to(getQualityStatisticsNode(actionsProfile.getFilterForInvalidAnalysis()))
                    .build();
        }
        return new BasicNode();
    }

    public Node buildIntermediateStatistics(final Action nextAction) {
        //TODO remove this and fix tests
        if(analyzerService == null) {
            return new BasicNode();
        }

        performActionsProfiling();

        if (needIntermediateStatistics(nextAction)) {
            final Set<ActionMetadata.Behavior> behavior = actionToMetadata.get(nextAction).getBehavior();
            if (behavior.contains(NEED_STATISTICS_PATTERN)) {
                return NodeBuilder.from(getPatternDetectionNode(actionsProfile.getFilterForPatternAnalysis())).build();
            } else if (behavior.contains(ActionMetadata.Behavior.NEED_STATISTICS_INVALID)) {
                return NodeBuilder
                        .from(getTypeDetectionNode(actionsProfile.getFilterForFullAnalysis()))
                        .to(getInvalidDetectionNode(actionsProfile.getFilterForInvalidAnalysis()))
                        .build();
            } else {
                return null;
            }
        }
        return null;
    }

    private boolean needIntermediateStatistics(final Action nextAction) {
        // next action indicates that it need fresh statistics
        final Set<ActionMetadata.Behavior> behavior = actionToMetadata.get(nextAction).getBehavior();
        if (behavior.contains(NEED_STATISTICS_PATTERN) || behavior.contains(NEED_STATISTICS_INVALID)) {
            return true;
        }

        // action has filter that is on valid/invalid
        if (nextAction.getParameters().containsKey(FILTER.getKey())) {
            // action has a filterForFullAnalysis, to cover cases where filters are on invalid values
            final String filterAsString = nextAction.getParameters().get(FILTER.getKey());
            return StringUtils.contains(filterAsString, "valid") || StringUtils.contains(filterAsString, "invalid");
        }

        return false;
    }

    private void performActionsProfiling() {
        if (actionsProfile != null) {
            return;
        }
        checkInputs();

        final ActionsStaticProfiler profiler = new ActionsStaticProfiler(actionRegistry);
        actionToMetadata = profiler.getActionMetadataByAction(actions);
        actionsProfile = profiler.profile(columns, actions, actionToMetadata);
    }

    private void checkInputs() {
        if (actionRegistry == null) {
            throw new MissingResourceException("You need to provide an actionRegistry", "ActionRegistry", null);
        }
        if (statisticsAdapter == null) {
            throw new MissingResourceException("You need to provide an statistics adapter", "StatisticsAdapter", null);
        }
        if (actions == null) {
            throw new MissingResourceException("You need to provide the whole list of actions", "List", null);
        }
        if (columns == null) {
            throw new MissingResourceException("You need to provide the whole list of columns", "List", null);
        }
    }

    /**
     * Create a full analyzer
     */
    private Function<List<ColumnMetadata>, Analyzer<Analyzers.Result>> getQualityAnalyzer() {
        return columns -> analyzerService.build(columns, AnalyzerService.Analysis.QUALITY);
    }

    /**
     * Create a full analyzer
     */
    private Function<List<ColumnMetadata>, Analyzer<Analyzers.Result>> getFullAnalyzer() {
        return columns -> analyzerService.build(columns, //
                        AnalyzerService.Analysis.QUALITY, //
                        AnalyzerService.Analysis.CARDINALITY, //
                        AnalyzerService.Analysis.FREQUENCY, //
                        AnalyzerService.Analysis.PATTERNS, //
                        AnalyzerService.Analysis.LENGTH, //
                        AnalyzerService.Analysis.QUANTILES, //
                        AnalyzerService.Analysis.SUMMARY, //
                        AnalyzerService.Analysis.HISTOGRAM);
    }

    private Node getTypeDetectionNode(final Predicate<ColumnMetadata> columnFilter) {
        return allowSchemaAnalysis ?
                new TypeDetectionNode(columnFilter, statisticsAdapter, analyzerService::schemaAnalysis) :
                new BasicNode();
    }

    private Node getPatternDetectionNode(final Predicate<ColumnMetadata> columnFilter) {
        return allowSchemaAnalysis ? new TypeDetectionNode(columnFilter, statisticsAdapter,
                c -> analyzerService.build(c, AnalyzerService.Analysis.PATTERNS)) : new BasicNode();
    }

    private Node getInvalidDetectionNode(final Predicate<ColumnMetadata> columnFilter) {
        return new InvalidDetectionNode(analyzerService, columnFilter);
    }

    private Node getQualityStatisticsNode(final Predicate<ColumnMetadata> columnFilter) {
        return new StatisticsNode(getQualityAnalyzer(), columnFilter, statisticsAdapter);
    }

    private Node getFullStatisticsNode(final Predicate<ColumnMetadata> columnFilter) {
        return new StatisticsNode(getFullAnalyzer(), columnFilter, statisticsAdapter);
    }
}
