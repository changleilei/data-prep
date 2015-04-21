(function () {
    'use strict';

    /**
     * @ngdoc service
     * @name data-prep.services.dataset.service:DatasetGridService
     * @description Dataset grid service. This service holds the datagrid (SliickGrid) view and the (SlickGrid) filters
     */
    function DatasetGridService() {
        var self = this;

        /**
         * @ngdoc property
         * @name metadata
         * @propertyOf data-prep.services.dataset.service:DatasetGridService
         * @description the loaded metadata
         * @type {Object}
         */
        self.metadata = null;
        /**
         * @ngdoc property
         * @name data
         * @propertyOf data-prep.services.dataset.service:DatasetGridService
         * @description the loaded data
         * @type {Object}
         */
        self.data = null;

        /**
         * @ngdoc property
         * @name dataView
         * @propertyOf data-prep.services.dataset.service:DatasetGridService
         * @description the SlickGrid dataView
         * @type {Object}
         */
        self.dataView = new Slick.Data.DataView({inlineFilters: false});
        /**
         * @ngdoc property
         * @name filters
         * @propertyOf data-prep.services.dataset.service:DatasetGridService
         * @description the filters applied to the dataview
         * @type {function[]}
         */
        self.filters = [];

        /**
         * @ngdoc property
         * @name selectedColumn
         * @propertyOf data-prep.services.dataset.service:DatasetGridService
         * @description the selected grid column
         * @type {object}
         */
        self.selectedColumn = null;

        //------------------------------------------------------------------------------------------------------
        //---------------------------------------------------DATA-----------------------------------------------
        //------------------------------------------------------------------------------------------------------

        /**
         * @ngdoc method
         * @name insertUniqueIds
         * @methodOf data-prep.services.dataset.service:DatasetGridService
         * @param {Object[]} records - the records to adapt
         * @description [PRIVATE] Insert unique id for each record (needed for DataView)
         */
        var insertUniqueIds = function (records) {
            _.forEach(records, function (item, index) {
                item.tdpId = index;
            });
        };

        /**
         * @ngdoc method
         * @name updateDataviewRecords
         * @methodOf data-prep.services.dataset.service:DatasetGridService
         * @param {Object[]} records - the records to insert
         * @description [PRIVATE] Set dataview records
         */
        var updateDataviewRecords = function (records) {
            insertUniqueIds(records);

            self.dataView.beginUpdate();
            self.dataView.setItems(records, 'tdpId');
            self.dataView.endUpdate();

        };

        /**
         * @ngdoc method
         * @name setDataset
         * @methodOf data-prep.services.dataset.service:DatasetGridService
         * @param {Object} metadata - the new metadata to load
         * @param {Object} data - the new data to load
         * @description Set dataview records and metadata to the datagrid
         */
        self.setDataset = function (metadata, data) {
            updateDataviewRecords(data.records);

            self.metadata = metadata;
            self.data = data;
        };

        /**
         * @ngdoc method
         * @name updateRecords
         * @methodOf data-prep.services.dataset.service:DatasetGridService
         * @param {Object[]} records - the new records
         * @description Update the data records in the datagrid
         */
        self.updateRecords = function (records) {
            updateDataviewRecords(records);
            self.data.records = records;
        };

        //------------------------------------------------------------------------------------------------------
        //------------------------------------------------DATA UTILS--------------------------------------------
        //------------------------------------------------------------------------------------------------------
        /**
         * @ngdoc method
         * @name getColumns
         * @methodOf data-prep.services.dataset.service:DatasetGridService
         * @param {boolean} excludeNumeric - filter the numeric columns
         * @param {boolean} excludeBoolean - filter the boolean columns
         * @description Filter the column ids
         * @returns {string[]} - the column list that match the desired filters
         */
        self.getColumns = function(excludeNumeric, excludeBoolean) {
            var numericTypes = ['numeric', 'integer', 'float', 'double'];
            var cols = self.data.columns;

            if(excludeNumeric) {
                cols = _.filter(cols, function (col) {
                    return numericTypes.indexOf(col.type) === -1;
                });
            }
            if(excludeBoolean) {
                cols = _.filter(cols, function(col) {
                    return col.type !== 'boolean';
                });
            }

            return _.map(cols, function (col) {
                return col.id;
            });
        };

        /**
         * @ngdoc method
         * @name getColumnsContaining
         * @methodOf data-prep.services.dataset.service:DatasetGridService
         * @param {string} regexp - the regexp
         * @param {boolean} canBeNumeric - filter the numeric columns
         * @param {boolean} canBeBoolean - filter the boolean columns
         * @description Return the column id list that has a value that match the regexp
         * @returns {Object[]} - the column list that contains a value that match the regexp
         */
        self.getColumnsContaining = function(regexp, canBeNumeric, canBeBoolean) {
            var results = [];

            var data = self.data.records;
            var potentialColumns = self.getColumns(!canBeNumeric, !canBeBoolean);

            //we loop over the datas while there is data and potential columns that can contains the searched term
            //if a col value for a row contains the term, we add it to result
            var dataIndex = 0;
            while (dataIndex < data.length && potentialColumns.length) {
                var record = data[dataIndex];
                for (var colIndex in potentialColumns) {
                    var colId = potentialColumns[colIndex];
                    if (record[colId].toLowerCase().match(regexp)) {
                        potentialColumns.splice(colIndex, 1);
                        results.push(colId);
                    }
                }

                potentialColumns = _.difference(potentialColumns, results);
                dataIndex++;
            }

            return results;
        };

        /**
         * @ngdoc method
         * @name getRowsContaining
         * @methodOf data-prep.services.dataset.service:DatasetGridService
         * @param {string} colId - the column id
         * @param {string} term - the term the cell must contain
         * @description Return displayed rows index where data[rowId][colId] contains the searched term
         * @returns {Object[]} - the rows that has the value
         */
        self.getRowsContaining = function(colId, term) {
            var result = [];
            for(var i = 0; i < self.dataView.getLength(); ++i) {
                var item = self.dataView.getItem(i);
                if((term === '' && item[colId] === '') || (term !== '' && item[colId].indexOf(term) > -1)) {
                    result.push(i);
                }
            }

            return result;
        };

        /**
         * @ngdoc method
         * @name setSelectedColumn
         * @methodOf data-prep.services.dataset.service:DatasetGridService
         * @param {string} colId - the column id
         * @description Set the selected column
         */
        self.setSelectedColumn = function(colId) {
            self.selectedColumn = _.find(self.data.columns, function(col) {
                return col.id === colId;
            });
        };

        //------------------------------------------------------------------------------------------------------
        //-------------------------------------------------FILTERS----------------------------------------------
        //------------------------------------------------------------------------------------------------------
        /**
         * @ngdoc method
         * @name filterFn
         * @methodOf data-prep.services.dataset.service:DatasetGridService
         * @param {object} item - the item to test
         * @param {object} args - object containing the filters predicates
         * @description [PRIVATE] Filter function. It iterates over all filters and return if the provided item fit the predicates
         * @returns {boolean} - true if the item pass all the filters
         */
        function filterFn(item, args) {
            for (var i = 0; i < args.filters.length; i++) {
                var filter = args.filters[i];
                if(!filter(item)) {
                    return false;
                }
            }
            return true;
        }

        /**
         * @ngdoc method
         * @name updateDataViewFilters
         * @methodOf data-prep.services.dataset.service:DatasetGridService
         * @description [PRIVATE] Update filters in dataview
         */
        var updateDataViewFilters = function() {
            self.dataView.beginUpdate();
            self.dataView.setFilterArgs({
                filters: self.filters
            });
            self.dataView.setFilter(filterFn);
            self.dataView.endUpdate();
        };

        /**
         * @ngdoc method
         * @name addFilter
         * @methodOf data-prep.services.dataset.service:DatasetGridService
         * @description Add a filter in dataview
         * @param {object} filter - the filter function to add
         */
        self.addFilter = function(filter) {
            self.filters.push(filter);
            updateDataViewFilters();
        };

        /**
         * @ngdoc method
         * @name updateFilter
         * @methodOf data-prep.services.dataset.service:DatasetGridService
         * @description Update a filter in dataview
         * @param {object} oldFilter - the filter function to replace
         * @param {object} newFilter - the new filter function
         */
        self.updateFilter = function(oldFilter, newFilter) {
            var index = self.filters.indexOf(oldFilter);
            self.filters.splice(index, 1, newFilter);
            updateDataViewFilters();
        };

        /**
         * @ngdoc method
         * @name removeFilter
         * @methodOf data-prep.services.dataset.service:DatasetGridService
         * @description Remove a filter in dataview
         * @param {object} filter - the filter function to remove
         */
        self.removeFilter = function(filter) {
            var filterIndex = self.filters.indexOf(filter);
            if(filterIndex > -1) {
                self.filters.splice(filterIndex, 1);
                updateDataViewFilters();
            }
        };

        /**
         * @ngdoc method
         * @name resetFilters
         * @methodOf data-prep.services.dataset.service:DatasetGridService
         * @description Remove all filters from dataview
         */
        self.resetFilters = function() {
            self.filters = [];
            updateDataViewFilters();
        };
    }

    angular.module('data-prep.services.dataset')
        .service('DatasetGridService', DatasetGridService);

})();