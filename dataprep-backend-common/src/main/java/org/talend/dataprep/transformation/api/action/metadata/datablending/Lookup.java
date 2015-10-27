package org.talend.dataprep.transformation.api.action.metadata.datablending;

import static org.apache.commons.lang.StringUtils.EMPTY;
import static org.talend.dataprep.transformation.api.action.parameters.ParameterType.STRING;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.talend.dataprep.api.dataset.ColumnMetadata;
import org.talend.dataprep.api.dataset.DataSetMetadata;
import org.talend.dataprep.api.dataset.DataSetRow;
import org.talend.dataprep.transformation.api.action.context.TransformationContext;
import org.talend.dataprep.transformation.api.action.metadata.category.ActionCategory;
import org.talend.dataprep.transformation.api.action.metadata.common.AbstractActionMetadata;
import org.talend.dataprep.transformation.api.action.metadata.common.ActionMetadata;
import org.talend.dataprep.transformation.api.action.metadata.common.DataSetAction;
import org.talend.dataprep.transformation.api.action.metadata.common.ImplicitParameters;
import org.talend.dataprep.transformation.api.action.parameters.ColumnParameter;
import org.talend.dataprep.transformation.api.action.parameters.Parameter;

/**
 *
 */
@Component(Lookup.ACTION_BEAN_PREFIX + Lookup.LOOKUP_ACTION_NAME)
public class Lookup extends AbstractActionMetadata implements DataSetAction {

    /** The action name. */
    public static final String LOOKUP_ACTION_NAME = "lookup"; //$NON-NLS-1$

    /** Adapted value of the name parameter. */
    private String adaptedNameValue = EMPTY;

    /** Adapted value of the dataset_id parameter. */
    private String adaptedDatasetIdValue = EMPTY;

    /** Adapted value of the url parameter. */
    private String adaptedUrlValue = EMPTY;

    /**
     * @return A unique name used to identify action.
     */
    @Override
    public String getName() {
        return LOOKUP_ACTION_NAME;
    }

    /**
     * @return A 'category' for the action used to group similar actions (eg. 'math', 'repair'...).
     */
    @Override
    public String getCategory() {
        return ActionCategory.DATA_BLENDING.getDisplayName();
    }

    /**
     * @see ActionMetadata#getParameters()
     */
    @Override
    public List<Parameter> getParameters() {
        final List<Parameter> parameters = ImplicitParameters.getParameters();
        parameters.add(new Parameter("lookup_ds_name", STRING, adaptedNameValue, false, false));
        parameters.add(new Parameter("lookup_ds_id", STRING, adaptedDatasetIdValue, false, false));
        parameters.add(new Parameter("lookup_ds_url", STRING, adaptedUrlValue, false, false));
        parameters.add(new Parameter("lookup_join_on", STRING, EMPTY, false, false));
        parameters.add(new ColumnParameter("lookup_selected_cols", EMPTY, false, false, Collections.emptyList(), true));
        return parameters;
    }

    /**
     * @see ActionMetadata#acceptColumn(ColumnMetadata)
     */
    @Override
    public boolean acceptColumn(ColumnMetadata column) {
        // because this is a specific action, suggestion will be handled by the API. Hence, default value is false.
        return false;
    }


    /**
     * Adapt the parameters default values according to the given dataset.
     *
     * @param dataset the dataset to adapt the parameters value from.
     * @param datasetUrl the dataset url to use in parameters.
     */
    public void adapt(DataSetMetadata dataset, String datasetUrl) {
        adaptedNameValue = dataset.getName();
        adaptedDatasetIdValue = dataset.getId();
        adaptedUrlValue = datasetUrl;
    }

    /**
     * @see DataSetAction#applyOnDataSet(DataSetRow, TransformationContext, Map)
     */
    @Override
    public void applyOnDataSet(DataSetRow row, TransformationContext context, Map<String, String> parameters) {
        // nothing to do yet...
    }
}
