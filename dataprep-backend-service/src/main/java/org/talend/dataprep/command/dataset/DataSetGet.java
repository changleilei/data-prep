// ============================================================================
// Copyright (C) 2006-2018 Talend Inc. - www.talend.com
//
// This source code is available under agreement available at
// https://github.com/Talend/data-prep/blob/master/LICENSE
//
// You should have received a copy of the agreement
// along with this program; if not, write to Talend SA
// 9 rue Pages 92150 Suresnes, France
//
// ============================================================================

package org.talend.dataprep.command.dataset;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.stream.Stream;
import javax.annotation.PostConstruct;

import org.apache.avro.Schema;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.message.BasicHeader;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.talend.daikon.exception.TalendRuntimeException;
import org.talend.daikon.exception.error.CommonErrorCodes;
import org.talend.dataprep.api.dataset.DataSet;
import org.talend.dataprep.api.dataset.DataSetMetadata;
import org.talend.dataprep.api.dataset.RowMetadata;
import org.talend.dataprep.api.dataset.row.DataSetRow;
import org.talend.dataprep.command.GenericCommand;
import org.talend.dataprep.dataset.adapter.ApiDatasetClient;
import org.talend.dataprep.dataset.store.content.DataSetContentLimit;
import org.talend.dataprep.exception.TDPException;
import org.talend.dataprep.util.avro.AvroUtils;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.http.HttpHeaders.ACCEPT;
import static org.apache.http.entity.ContentType.APPLICATION_JSON;
import static org.springframework.beans.factory.config.ConfigurableBeanFactory.SCOPE_PROTOTYPE;
import static org.talend.daikon.exception.ExceptionContext.build;
import static org.talend.dataprep.command.Defaults.emptyStream;
import static org.talend.dataprep.exception.error.APIErrorCodes.UNABLE_TO_RETRIEVE_DATASET_CONTENT;

/**
 * Command to get a dataset.
 *
 * @deprecated use {@link ApiDatasetClient#getDataSet(String)} or {@link ApiDatasetClient#getDataSetContentAsRows(String, RowMetadata)} instead.
 * parameters are not taken in account anymore.
 */
@Component
@Scope(SCOPE_PROTOTYPE)
@Deprecated
public class DataSetGet extends GenericCommand<InputStream> {

    private static final BasicHeader ACCEPT_HEADER =
            new BasicHeader(ACCEPT, APPLICATION_JSON.withCharset(UTF_8).toString());

    private final boolean fullContent;

    private final String dataSetId;

    private final boolean includeInternalContent;

    private final boolean includeMetadata;

    private final String filter;

    @Autowired
    private DataSetContentLimit limit;

    @Autowired
    private ApiDatasetClient datasetClient;

    public DataSetGet(final String dataSetId, final boolean fullContent, final boolean includeInternalContent) {
        this(dataSetId, fullContent, includeInternalContent, EMPTY);
    }

    public DataSetGet(final String dataSetId, final boolean fullContent, final boolean includeInternalContent, String filter) {
        this(dataSetId, fullContent, includeInternalContent, filter, true);
    }

    /**
     *
     * @param dataSetId the dataset to fetch
     * @param fullContent we need the full dataset or a sample (see sample limit in datset: 10k rows)
     * @param includeInternalContent option of full content API (/datasets/{dataSetId}/content) in-row TDP content, should not be inserted => Dropping.
     * @param filter TQL filter for content
     * @param includeMetadata option of full content API (/datasets/{dataSetId}/content?metadata={includeMetadata}) {@link DataSet#setMetadata(DataSetMetadata)}
     */
    public DataSetGet(final String dataSetId, final boolean fullContent, final boolean includeInternalContent, String filter, final boolean includeMetadata) {
        super(DATASET_GROUP);
        this.fullContent = fullContent;
        this.dataSetId = dataSetId;
        this.includeInternalContent = includeInternalContent;
        this.includeMetadata = includeMetadata;
        this.filter = filter;

        on(HttpStatus.NO_CONTENT).then(emptyStream());
        on(HttpStatus.OK).then(this::readResult);
        onError(e -> new TDPException(UNABLE_TO_RETRIEVE_DATASET_CONTENT, e, build().put("id", dataSetId)));
    }

    @PostConstruct
    private void initConfiguration() {
        execute(() -> {
            URI build;
            try {
                build = new URIBuilder(datasetServiceUrl + "/api/v1/dataset-sample/" + dataSetId).build();
            } catch (URISyntaxException e) {
                throw new TalendRuntimeException(CommonErrorCodes.UNEXPECTED_EXCEPTION, e);
            }

            HttpGet httpGet = new HttpGet(build);
            httpGet.addHeader(ACCEPT_HEADER);
            return httpGet;
        });
    }

    private InputStream readResult(HttpRequestBase httpRequestBase, HttpResponse httpResponse) {
        try {
            // Totally short-circuit the hystrix command but for the POC sake
            EntityUtils.consumeQuietly(httpResponse.getEntity());
            Schema schema = datasetClient.getDataSetSchema(dataSetId);

            RowMetadata rowMetadata = AvroUtils.toRowMetadata(schema);
            Stream<DataSetRow> rows = datasetClient.getDataSetContentAsRows(dataSetId, rowMetadata);

            // build dataset object
            DataSet dataSet = new DataSet();
            DataSetMetadata dataSetMetadata = new DataSetMetadata();
            dataSetMetadata.setId(dataSetId);
            dataSetMetadata.setRowMetadata(rowMetadata);
            dataSet.setMetadata(dataSetMetadata);
            dataSet.setRecords(rows);
            // TODO : filter, limit

            // Write all in a buffer
            try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()){
                objectMapper.writeValue(outputStream, dataSet);
                return new ByteArrayInputStream(outputStream.toByteArray());
            }
        } catch (IOException e) {
            throw new TalendRuntimeException(CommonErrorCodes.UNEXPECTED_EXCEPTION, e);
        }
    }

}
