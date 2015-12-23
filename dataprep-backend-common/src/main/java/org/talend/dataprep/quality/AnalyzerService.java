package org.talend.dataprep.quality;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.talend.dataprep.api.dataset.ColumnMetadata;
import org.talend.dataprep.api.dataset.statistics.PatternFrequency;
import org.talend.dataprep.api.dataset.statistics.date.StreamDateHistogramAnalyzer;
import org.talend.dataprep.api.dataset.statistics.number.StreamNumberHistogramAnalyzer;
import org.talend.dataprep.api.type.Type;
import org.talend.dataprep.api.type.TypeUtils;
import org.talend.dataprep.exception.TDPException;
import org.talend.dataprep.exception.error.CommonErrorCodes;
import org.talend.dataprep.transformation.api.action.metadata.common.ActionMetadataUtils;
import org.talend.dataprep.transformation.api.action.metadata.date.DateParser;
import org.talend.dataquality.semantic.classifier.SemanticCategoryEnum;
import org.talend.dataquality.semantic.recognizer.CategoryRecognizerBuilder;
import org.talend.dataquality.semantic.statistics.SemanticAnalyzer;
import org.talend.dataquality.semantic.statistics.SemanticQualityAnalyzer;
import org.talend.dataquality.standardization.index.ClassPathDirectory;
import org.talend.dataquality.statistics.cardinality.CardinalityAnalyzer;
import org.talend.dataquality.statistics.frequency.AbstractFrequencyAnalyzer;
import org.talend.dataquality.statistics.frequency.DataTypeFrequencyAnalyzer;
import org.talend.dataquality.statistics.frequency.pattern.CompositePatternFrequencyAnalyzer;
import org.talend.dataquality.statistics.frequency.recognition.AbstractPatternRecognizer;
import org.talend.dataquality.statistics.frequency.recognition.DateTimePatternRecognizer;
import org.talend.dataquality.statistics.frequency.recognition.EmptyPatternRecognizer;
import org.talend.dataquality.statistics.frequency.recognition.LatinExtendedCharPatternRecognizer;
import org.talend.dataquality.statistics.numeric.quantile.QuantileAnalyzer;
import org.talend.dataquality.statistics.numeric.summary.SummaryAnalyzer;
import org.talend.dataquality.statistics.quality.DataTypeQualityAnalyzer;
import org.talend.dataquality.statistics.quality.ValueQualityAnalyzer;
import org.talend.dataquality.statistics.text.TextLengthAnalyzer;
import org.talend.dataquality.statistics.type.DataTypeAnalyzer;
import org.talend.dataquality.statistics.type.DataTypeEnum;
import org.talend.datascience.common.inference.Analyzer;
import org.talend.datascience.common.inference.Analyzers;
import org.talend.datascience.common.inference.ValueQualityStatistics;
/**
 * Service in charge of analyzing dataset quality.
 */
@Service
public class AnalyzerService implements DisposableBean {

    @Autowired
    private DateParser dateParser;

    /**
     * This class' logger.
     */
    public static final Logger LOGGER = LoggerFactory.getLogger(AnalyzerService.class);

    private static CategoryRecognizerBuilder newCategoryRecognizer() {
        try {
            final URI ddPath = AnalyzerService.class.getResource("/luceneIdx/dictionary").toURI(); //$NON-NLS-1$
            final URI kwPath = AnalyzerService.class.getResource("/luceneIdx/keyword").toURI(); //$NON-NLS-1$
            return CategoryRecognizerBuilder.newBuilder() //
                    .ddPath(ddPath) //
                    .kwPath(kwPath) //
                    .lucene();
        } catch (URISyntaxException e) {
            throw new TDPException(CommonErrorCodes.UNEXPECTED_EXCEPTION, e);
        }
    }

    /**
     * Build a {@link ValueQualityAnalyzer analyzer} for both type and domain validation.
     *
     * @param columns The column metadata information where type and domain are extracted from.
     * @return A non-initialized but configured {@link ValueQualityAnalyzer}.
     */
    public ValueQualityAnalyzer getQualityAnalyzer(List<ColumnMetadata> columns) {
        final CategoryRecognizerBuilder categoryBuilder = newCategoryRecognizer();
        final DataTypeEnum[] types = TypeUtils.convert(columns);
        List<String> domainList = columns.stream() //
                .map(c -> {
                    final SemanticCategoryEnum category = SemanticCategoryEnum.getCategoryById(c.getDomain().toUpperCase());
                    return category == null ? SemanticCategoryEnum.UNKNOWN.getId() : category.getId();
                }) //
                .collect(Collectors.toList());
        final String[] domains = domainList.toArray(new String[domainList.size()]);
        DataTypeQualityAnalyzer dataTypeQualityAnalyzer = new DataTypeQualityAnalyzer(types);
        columns.forEach(c -> dataTypeQualityAnalyzer.addCustomDateTimePattern(getMostUsedDatePattern(c)));
        SemanticQualityAnalyzer semanticQualityAnalyzer = new SemanticQualityAnalyzer(categoryBuilder, domains);
        return new ValueQualityAnalyzer(dataTypeQualityAnalyzer, semanticQualityAnalyzer, true);
    }

    public Analyzer<Analyzers.Result> full(final List<ColumnMetadata> columns) {
        // Configure quality & semantic analysis (if column metadata information is present in stream).
        final DataTypeEnum[] types = TypeUtils.convert(columns);

        // Configure value quality analysis
        final Analyzer<Analyzers.Result> analyzer = Analyzers.with(getQualityAnalyzer(columns), // Value quality
                                                                                                // (invalid values...)
                getDataTypeAnalyzer(columns), // Type analysis (especially useful for new columns).
                new CardinalityAnalyzer(), // Cardinality (distinct + duplicate)
                new DataTypeFrequencyAnalyzer(), // Raw data Frequency analysis
                getPatternFrequencyAnalyzer(columns), // Pattern Frequency analysis
                new TextLengthAnalyzer(), // Text length analysis (for applicable columns)
                new SemanticAnalyzer(newCategoryRecognizer()), // Semantic analysis
                new QuantileAnalyzer(types), // Quantile analysis
                new SummaryAnalyzer(types), // Summary (min, max, mean, variance)
                new StreamNumberHistogramAnalyzer(types), // Number Histogram
                new StreamDateHistogramAnalyzer(columns, types, dateParser)); // Date Histogram
        analyzer.init();
        return analyzer;
    }

    public Analyzer<Analyzers.Result> baselineAnalysis(final List<ColumnMetadata> columns) {
        // Configure value quality analysis
        final Analyzer<Analyzers.Result> analyzer = Analyzers.with(getQualityAnalyzer(columns), // Value quality
                                                                                                // (invalid values...)
                getDataTypeAnalyzer(columns), // Type analysis (especially useful for new columns).
                new CardinalityAnalyzer(), // Cardinality (distinct + duplicate)
                new DataTypeFrequencyAnalyzer(), // Raw data Frequency analysis
                getPatternFrequencyAnalyzer(columns), // Pattern Frequency analysis
                new SemanticAnalyzer(newCategoryRecognizer())); // Semantic analysis
        analyzer.init();
        return analyzer;
    }

    public Analyzer<Analyzers.Result> advancedAnalysis(final List<ColumnMetadata> columns) {
        // Configure quality & semantic analysis (if column metadata information is present in stream).
        final DataTypeEnum[] types = TypeUtils.convert(columns);

        // Configure value quality analysis
        final Analyzer<Analyzers.Result> analyzer = Analyzers.with(new TextLengthAnalyzer(), // Text length analysis
                                                                                             // (for applicable columns)
                new QuantileAnalyzer(types), // Quantile analysis
                new SummaryAnalyzer(types), // Summary (min, max, mean, variance)
                new StreamNumberHistogramAnalyzer(types), // Number Histogram
                new StreamDateHistogramAnalyzer(columns, types, dateParser)); // Date Histogram
        analyzer.init();
        return analyzer;
    }

    /**
     * @see AnalyzerService#getPatternFrequencyAnalyzer(List)
     */
    public AbstractFrequencyAnalyzer getPatternFrequencyAnalyzer(ColumnMetadata column) {
        return getPatternFrequencyAnalyzer(Collections.singletonList(column));
    }

    /**
     * @param columns the columns to analyze.
     * @return the analyzer for the given columns.
     */
    public AbstractFrequencyAnalyzer getPatternFrequencyAnalyzer(List<ColumnMetadata> columns) {

        // deal with specific date, even custom date pattern
        final DateTimePatternRecognizer dateTimePatternFrequencyAnalyzer = getDateTimePatternFrequencyAnalyzer(columns);

        // warning, the order is important
        List<AbstractPatternRecognizer> patternFrequencyAnalyzers = new ArrayList<>();
        patternFrequencyAnalyzers.add(new EmptyPatternRecognizer());
        patternFrequencyAnalyzers.add(dateTimePatternFrequencyAnalyzer);
        patternFrequencyAnalyzers.add(new LatinExtendedCharPatternRecognizer());

        return new CompositePatternFrequencyAnalyzer(patternFrequencyAnalyzers);
    }

    private DateTimePatternRecognizer getDateTimePatternFrequencyAnalyzer(final List<ColumnMetadata> columns) {
        final DateTimePatternRecognizer dateTimePatternFrequencyAnalyzer = new DateTimePatternRecognizer();
        final List<String> mostUsedDatePatterns = getMostUsedDatePatterns(columns);
        dateTimePatternFrequencyAnalyzer.addCustomDateTimePatterns(mostUsedDatePatterns);
        return dateTimePatternFrequencyAnalyzer;
    }

    public Analyzer<Analyzers.Result> qualityAnalysis(List<ColumnMetadata> columns) {
        DataTypeEnum[] types = TypeUtils.convert(columns);
        // Run analysis
        final CategoryRecognizerBuilder categoryBuilder = newCategoryRecognizer();
        // Configure value quality analysis
        final ValueQualityAnalyzer valueQualityAnalyzer = getQualityAnalyzer(columns);
        final Analyzer<Analyzers.Result> analyzer = Analyzers.with(valueQualityAnalyzer, //
                new SummaryAnalyzer(types), //
                new SemanticAnalyzer(categoryBuilder), //
                getDataTypeAnalyzer(columns));
        analyzer.init();
        return analyzer;
    }

    /**
     * <p>
     * Analyse the... Schema !
     * </p>
     * <ul>
     * <li>ValueQuality</li>
     * <li>Semantic</li>
     * <li>DataType</li>
     * </ul>
     *
     * @param columns the columns to analyze.
     * @return the analyzers to perform for the schema.
     */
    public Analyzer<Analyzers.Result> schemaAnalysis(List<ColumnMetadata> columns) {
        // Run analysis
        final CategoryRecognizerBuilder categoryBuilder = newCategoryRecognizer();
        // Configure value quality analysis
        final ValueQualityAnalyzer valueQualityAnalyzer = getQualityAnalyzer(columns);
        final Analyzer<Analyzers.Result> analyzer = Analyzers.with(valueQualityAnalyzer, //
                new SemanticAnalyzer(categoryBuilder), //
                getDataTypeAnalyzer(columns));
        analyzer.init();
        return analyzer;
    }

    /**
     * @see DisposableBean#destroy()
     */
    @Override
    public void destroy() throws Exception {
        LOGGER.info("Clean up analyzers...");
        final Map<String, Analyzer<ValueQualityStatistics>> cache = ActionMetadataUtils.getAnalyzerCache();
        for (Map.Entry<String, Analyzer<ValueQualityStatistics>> entry : cache.entrySet()) {
            try {
                entry.getValue().close();
            } catch (Exception e) {
                LOGGER.error("Unable to close analyzer for type '" + entry.getKey() + "'.", e);
            }
        }
        ClassPathDirectory.destroy();
        LOGGER.info("Clean up analyzers done.");
    }

    private DataTypeAnalyzer getDataTypeAnalyzer(List<ColumnMetadata> columns) {
        final List<String> mostUsedDatePatterns = getMostUsedDatePatterns(columns);
        return new DataTypeAnalyzer(mostUsedDatePatterns);
    }

    /**
     * Return the list of most used patterns for dates.
     *
     * @param columns the columns to analyze.
     * @return the list of most used patterns for dates or an empty list if there's none.
     */
    private List<String> getMostUsedDatePatterns(List<ColumnMetadata> columns) {

        List<String> patterns = new ArrayList<>(columns.size());
        for (ColumnMetadata column : columns) {
            final String pattern = getMostUsedDatePattern(column);
            if (StringUtils.isNotBlank(pattern)) {
                patterns.add(pattern);
            }
        }

        return patterns;
    }

    /**
     * In case of a date column, return the most used pattern.
     *
     * @param column the column to inspect.
     * @return the most used pattern or null if there's none.
     */
    private String getMostUsedDatePattern(ColumnMetadata column) {
        // only filter out non date columns
        if (Type.get(column.getType()) != Type.DATE) {
            return null;
        }
        final List<PatternFrequency> patternFrequencies = column.getStatistics().getPatternFrequencies();
        if (!patternFrequencies.isEmpty()) {
            patternFrequencies.sort((p1, p2) -> Long.compare(p2.getOccurrences(), p1.getOccurrences()));
            return patternFrequencies.get(0).getPattern();
        }
        return null;
    }
}
