// ============================================================================
//
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

package org.talend.dataprep.helper.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Payload send for aggregate operation.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AggregateOperation {

    public String operator;

    public String columnId;

    public AggregateOperation(String operator, String columnId) {
        this.operator = operator;
        this.columnId = columnId;
    }
}