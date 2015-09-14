(function () {
    'use strict';

    /**
     * @ngdoc service
     * @name data-prep.datagrid.service:DatagridTooltipService
     * @description Datagrid private service that manage the grid tooltip
     * @requires data-prep.datagrid.service:DatagridStyleService
     * @requires data-prep.services.playground.service:DatagridService
     * @requires data-prep.services.utils.service:TextFormatService
     */
    function DatagridTooltipService($timeout, DatagridService, TextFormatService) {
        var grid;
        var tooltipTimeout, tooltipShowPromise;
        var tooltipDelay = 300;

        var service = {
            showTooltip: false,
            tooltip: {},
            tooltipRuler: null,
            init: init
        };
        return service;

        //--------------------------------------------------------------------------------------------------------------

        /**
         * @ngdoc method
         * @name cancelTooltip
         * @methodOf data-prep.datagrid.service:DatagridTooltipService
         * @description Cancel the current tooltip promise
         */
        function cancelTooltip() {
            if (tooltipTimeout) {
                clearTimeout(tooltipTimeout);
                tooltipTimeout = null;
            }
            if (tooltipShowPromise) {
                $timeout.cancel(tooltipShowPromise);
                tooltipShowPromise = null;
            }
        }

        /**
         * @ngdoc method
         * @name createTooltip
         * @methodOf data-prep.datagrid.service:DatagridTooltipService
         * @param {object} event The Slickgrid cell enter event
         * @description Update the tooltip component and display with a delay
         */
        function createTooltip(event) {
            tooltipTimeout = setTimeout(function () {
                var cell = grid.getCellFromEvent(event);
                if (!cell) {
                    return;
                }

                var row = cell.row;
                var item = DatagridService.dataView.getItem(row);

                var column = grid.getColumns()[cell.cell];
                var value = item[column.id];
                //convert to string
                value += '';

                if (shouldShowTooltip(value, cell)) {
                    tooltipShowPromise = $timeout(function () {
                        service.tooltip = {
                            position: {
                                x: event.clientX,
                                y: event.clientY
                            },
                            htmlStr: TextFormatService.computeHTMLForLeadingOrTrailingHiddenChars(value)
                        };
                        service.showTooltip = true;
                    });
                }
            }, tooltipDelay);
        }

        /**
         * @ngdoc method
         * @name updateTooltip
         * @methodOf data-prep.datagrid.service:DatagridTooltipService
         * @param {object} event The Slickgrid cell enter event
         * @description Cancel the old tooltip promise if necessary and create a new one
         */
        function updateTooltip(event) {
            cancelTooltip();
            createTooltip(event);
        }

        /**
         * @ngdoc method
         * @name hideTooltip
         * @methodOf data-prep.datagrid.service:DatagridTooltipService
         * @description Cancel the old tooltip promise if necessary and hide the tooltip
         */
        function hideTooltip() {
            cancelTooltip();
            if (service.showTooltip) {
                $timeout(function () {
                    service.showTooltip = false;
                });
            }
        }

        /**
         * @ngdoc method
         * @name shouldShowTooltip
         * @methodOf data-prep.datagrid.service:DatagridTooltipService
         * @description Check if the text should be shown in a tooltip (content too long)
         * @param {string} text the text to display
         * @param {object} cell The cell containing the text
         */
        function shouldShowTooltip(text, cell) {
            //do NOT show if content is empty
            if (text === '') {
                return false;
            }

            //show if content is multiline (avoid too loud check with div size)
            var textConverted = text + '';
            if (textConverted.indexOf('\n') > -1) {
                return true;
            }

            //heavy check based on div size
            var box = grid.getCellNodeBox(cell.row, cell.cell);
            var ruler = service.tooltipRuler;
            ruler.text(textConverted);

            // return if the content is bigger than the displayed box by computing the diff between the displayed box
            // and the hidden tooltip ruler size minus the cell padding
            return (box.right - box.left - 12 ) <= ruler.width() || (box.bottom - box.top) < ruler.height();
        }

        /**
         * @ngdoc method
         * @name attachTooltipListener
         * @methodOf data-prep.datagrid.service:DatagridTooltipService
         * @description Attach cell hover for tooltips listeners
         */
        function attachTooltipListener() {
            //show tooltip on hover
            grid.onMouseEnter.subscribe(updateTooltip);

            //hide tooltip on leave
            grid.onMouseLeave.subscribe(hideTooltip);
        }

        /**
         * @ngdoc method
         * @name init
         * @methodOf data-prep.datagrid.service:DatagridTooltipService
         * @param {object} newGrid The new grid
         * @description Initialize the grid and attach the tooltips listeners
         */
        function init(newGrid) {
            grid = newGrid;
            attachTooltipListener();
        }
    }

    angular.module('data-prep.datagrid')
        .service('DatagridTooltipService', DatagridTooltipService);
})();