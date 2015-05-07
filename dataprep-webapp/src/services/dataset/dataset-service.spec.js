describe('Dataset Service', function () {
    'use strict';

    var datasets = [{name: 'my dataset'}, {name: 'my second dataset'}, {name: 'my second dataset (1)'}, {name: 'my second dataset (2)'}];
    var preparationConsolidation, datasetConsolidation;
    var promiseWithProgress;
    var preparations = [{id: '4385fa764bce39593a405d91bc23'}];

    beforeEach(module('data-prep.services.dataset'));

    beforeEach(inject(function($q, DatasetListService, DatasetRestService, PreparationListService) {
        preparationConsolidation = $q.when(preparations);
        datasetConsolidation = $q.when(true);
        promiseWithProgress = $q.when(true);

        DatasetListService.datasets = datasets;

        spyOn(DatasetListService, 'refreshDefaultPreparation').and.returnValue(datasetConsolidation);
        spyOn(DatasetListService, 'delete').and.returnValue($q.when(true));
        spyOn(DatasetListService, 'create').and.returnValue(promiseWithProgress);
        spyOn(DatasetListService, 'update').and.returnValue(promiseWithProgress);
        spyOn(DatasetRestService, 'getContent').and.returnValue($q.when({}));

        spyOn(DatasetListService, 'refreshDatasets').and.returnValue($q.when(datasets));
        spyOn(PreparationListService, 'refreshMetadataInfos').and.returnValue(preparationConsolidation);
    }));

    afterEach(inject(function(DatasetListService) {
        DatasetListService.datasets = null;
    }));

    it('should return dataset list from ListService', inject(function (DatasetService, DatasetListService) {
        //given
        DatasetListService.datasets = datasets;

        //when
        var result = DatasetService.datasetsList();

        //then
        expect(result).toBe(datasets);
    }));

    it('should adapt infos to dataset object for upload', inject(function (DatasetService) {
        //given
        var file = {
            path: '/path/to/file'
        };
        var name = 'myDataset';
        var id = 'e85afAa78556d5425bc2';

        //when
        var dataset = DatasetService.fileToDataset(file, name, id);

        //then
        expect(dataset.name).toBe(name);
        expect(dataset.progress).toBe(0);
        expect(dataset.file).toBe(file);
        expect(dataset.error).toBe(false);
        expect(dataset.id).toBe(id);
    }));

    it('should get unique dataset name', inject(function (DatasetService) {
        //given
        var name = 'my dataset';

        //when
        var uniqueName = DatasetService.getUniqueName(name);

        //then
        expect(uniqueName).toBe('my dataset (1)');
    }));

    it('should get unique dataset name with a number in it', inject(function (DatasetService) {
        //given
        var name = 'my second dataset (2)';

        //when
        var uniqueName = DatasetService.getUniqueName(name);

        //then
        expect(uniqueName).toBe('my second dataset (3)');
    }));

    it('should get a promise that resolve the existing datasets if already fetched', inject(function ($rootScope, DatasetService) {
        //given
        var results = null;

        //when
        DatasetService.getDatasets()
            .then(function(response) {
                results = response;
            });
        $rootScope.$digest();

        //then
        expect(results).toBe(datasets);
    }));

    it('should not consolidate preparations and datasets if datasets are already fetched', inject(function ($rootScope, DatasetService, DatasetListService, PreparationListService) {
        //given
        var results = null;

        //when
        DatasetService.getDatasets()
            .then(function(response) {
                results = response;
            });
        $rootScope.$digest();

        //then
        expect(PreparationListService.refreshMetadataInfos).not.toHaveBeenCalled();
        expect(DatasetListService.refreshDefaultPreparation).not.toHaveBeenCalled();
    }));

    it('should get a promise that fetch datasets', inject(function ($rootScope, DatasetService, DatasetListService) {
        //given
        var results = null;
        DatasetListService.datasets = null;

        //when
        DatasetService.getDatasets()
            .then(function(response) {
                results = response;
            });
        $rootScope.$digest();

        //then
        expect(results).toBe(datasets);
        expect(DatasetListService.refreshDatasets).toHaveBeenCalled();
    }));

    it('should consolidate preparations and datasets on new dataset fetch', inject(function ($rootScope, DatasetService, DatasetListService, PreparationListService) {
        //given
        DatasetListService.datasets = null;

        //when
        DatasetService.getDatasets();
        DatasetListService.datasets = datasets; // simulate dataset list initialisation
        $rootScope.$digest();

        //then
        expect(PreparationListService.refreshMetadataInfos).toHaveBeenCalledWith(datasets);
        expect(DatasetListService.refreshDefaultPreparation).toHaveBeenCalledWith(preparations);
    }));

    it('should delete a dataset', inject(function ($rootScope, DatasetService, DatasetListService) {
        //given
        var dataset = DatasetListService.datasets[0];

        //when
        DatasetService.delete(dataset);
        $rootScope.$digest();

        //then
        expect(DatasetListService.delete).toHaveBeenCalledWith(dataset);
    }));

    it('should consolidate preparations and datasets on dataset deletion', inject(function ($rootScope, DatasetService, DatasetListService, PreparationListService) {
        //given
        var dataset = DatasetListService.datasets[0];

        //when
        DatasetService.delete(dataset);
        $rootScope.$digest();

        //then
        expect(PreparationListService.refreshMetadataInfos).toHaveBeenCalledWith(datasets);
        expect(DatasetListService.refreshDefaultPreparation).toHaveBeenCalledWith(preparations);
    }));

    it('should create a dataset and return the http promise (with progress function)', inject(function ($rootScope, DatasetService, DatasetListService) {
        //given
        var dataset = DatasetListService.datasets[0];

        //when
        var result = DatasetService.create(dataset);
        $rootScope.$digest();

        //then
        expect(result).toBe(promiseWithProgress);
        expect(DatasetListService.create).toHaveBeenCalledWith(dataset);
    }));

    it('should consolidate preparations and datasets on dataset creation', inject(function ($rootScope, DatasetService, DatasetListService, PreparationListService) {
        //given
        var dataset = DatasetListService.datasets[0];

        //when
        DatasetService.create(dataset);
        $rootScope.$digest();

        //then
        expect(PreparationListService.refreshMetadataInfos).toHaveBeenCalledWith(datasets);
        expect(DatasetListService.refreshDefaultPreparation).toHaveBeenCalledWith(preparations);
    }));

    it('should update a dataset and return the http promise (with progress function)', inject(function ($rootScope, DatasetService, DatasetListService) {
        //given
        var dataset = DatasetListService.datasets[0];

        //when
        var result = DatasetService.update(dataset);
        $rootScope.$digest();

        //then
        expect(result).toBe(promiseWithProgress);
        expect(DatasetListService.update).toHaveBeenCalledWith(dataset);
    }));

    it('should consolidate preparations and datasets on dataset update', inject(function ($rootScope, DatasetService, DatasetListService, PreparationListService) {
        //given
        var dataset = DatasetListService.datasets[0];

        //when
        DatasetService.update(dataset);
        $rootScope.$digest();

        //then
        expect(PreparationListService.refreshMetadataInfos).toHaveBeenCalledWith(datasets);
        expect(DatasetListService.refreshDefaultPreparation).toHaveBeenCalledWith(preparations);
    }));

    it('should get content from rest service', inject(function ($rootScope, DatasetService, DatasetRestService) {
        //given
        var datasetId = '34a5dc948967b5';
        var withMetadata = true;

        //when
        DatasetService.getContent(datasetId, withMetadata);
        $rootScope.$digest();

        //then
        expect(DatasetRestService.getContent).toHaveBeenCalledWith(datasetId, withMetadata);
    }));
});