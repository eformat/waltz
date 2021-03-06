/*
 * Waltz - Enterprise Architecture
 * Copyright (C) 2016, 2017 Waltz open source project
 * See README.md for more information
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

import _ from "lodash";
import {initialiseData} from "../../../common/index";
import {CORE_API} from "../../../common/services/core-api-utils";
import {downloadTextFile} from "../../../common/file-utils";
import {mkApplicationSelectionOptions} from "../../../common/selector-utils";
import {hierarchyQueryScope} from "../../../common/services/enums/hierarchy-query-scope";
import {lifecycleStatus} from "../../../common/services/enums/lifecycle-status";

import template from "./person-apps-section.html";


const bindings = {
    filters: "<",
    parentEntityRef: "<"
};


const initialState = {
    itApps: [],
    endUserApps: [],
    direct: [],
    indirect: []
};



function buildAppInvolvementSummary(apps = [], involvements = [], involvementKinds = []) {
    const appsById = _.keyBy(apps, "id");
    const involvementKindsById = _.keyBy(involvementKinds, "id");

    const directlyInvolvedAppIds = _.chain(involvements).map("entityReference.id").uniq().value();

    const allAppIds = _.map(apps, "id");
    const indirectlyInvolvedAppIds = _.difference(allAppIds, directlyInvolvedAppIds);

    const directAppInvolvements = _.chain(involvements)
        .groupBy("entityReference.id")
        .map((grp, key) => {
            let app = appsById[key];
            app = _.assign(app, {roles: _.map(grp, g => involvementKindsById[g.kindId].name )});
            return app;
        })
        .value();

    const indirectAppInvolvements = _.map(indirectlyInvolvedAppIds, id => appsById[id]);

    const summary = {
        direct: directAppInvolvements,
        indirect: indirectAppInvolvements,
        all: apps
    };
    return summary;
}


function controller(serviceBroker) {
    const vm = initialiseData(this, initialState);

    function buildInvolvementSummaries(person, allApps = []) {
        return serviceBroker
            .loadAppData(CORE_API.InvolvementKindStore.findAll)
            .then(r => vm.involvementKinds = r.data)
            .then(() => serviceBroker
                .loadViewData(
                    CORE_API.InvolvementStore.findByEmployeeId,
                    [ person.employeeId ]))
            .then(r => {
                const involvementsByKind = _.groupBy(r.data, "entityReference.kind");
                const summary = buildAppInvolvementSummary(
                    allApps,
                    _.concat(
                        involvementsByKind["APPLICATION"] || [],
                        involvementsByKind["END_USER_APPLICATION"] || []
                    ),
                    vm.involvementKinds);

                vm.direct =  summary.direct;
                vm.indirect = summary.indirect;
            });
    }

    function loadITManagedApps(entityReference) {
        const selector = mkApplicationSelectionOptions(
            entityReference,
            hierarchyQueryScope.CHILDREN.key,
            [lifecycleStatus.ACTIVE.key],
            vm.filters);

        return serviceBroker
            .loadViewData(
                CORE_API.ApplicationStore.findBySelector,
                [selector])
            .then(r => vm.itApps =
                _.map(r.data, d => Object.assign({}, d, {management: "IT"})));
    }

    function loadEndUserManagedApps(entityReference) {
        const selector = mkApplicationSelectionOptions(
            entityReference,
            undefined,
            undefined,
            vm.filters);

        return serviceBroker
            .loadViewData(
                CORE_API.EndUserAppStore.findBySelector,
                [ selector ])
            .then(r => {
                const enrichApp = d => Object.assign(
                    {},
                    d,
                    {
                        management: "End User",
                        platform: d.kind,
                        kind: "EUC",
                        overallRating: "Z"
                    });

                vm.endUserApps = _.map(
                    r.data,
                    enrichApp);
            });
    }


    const loadAll = () => {
        const endUserPromise = loadEndUserManagedApps(vm.parentEntityRef);
        const appPromise = serviceBroker
            .loadViewData(CORE_API.PersonStore.getById, [ vm.parentEntityRef.id ])
            .then(r => {
                vm.person = r.data;
                return loadITManagedApps(vm.parentEntityRef);
            });

        endUserPromise
            .then(() => appPromise)
            .then(() => {
                vm.allApps = _.union(vm.itApps, vm.endUserApps);
                buildInvolvementSummaries(vm.person, vm.allApps);
            });
    };

    vm.$onInit = () => {
        loadAll();
    };

    vm.$onChanges = (changes) => {
        if(changes.filters) {
            console.log("filters changed");
            loadAll();
        }
        vm.selector = mkApplicationSelectionOptions(
            vm.parentEntityRef,
            undefined,
            undefined,
            vm.filters);
    };

    // -- INTERACT

    vm.exportApps = () => {

        const header = [
            "Application",
            "Asset Code",
            "Kind",
            "Overall Rating",
            "Risk Rating",
            "Business Criticality",
            "Lifecycle Phase",
            "Roles"
        ];

        const dataRows = _
            .chain(vm.allApps)
            .map(app => {
                return [
                    app.name,
                    app.assetCode || "",
                    app.kind || "",
                    app.overallRating || "",
                    app.riskRating || "",
                    app.businessCriticality || "",
                    app.lifecyclePhase || "",
                    _.join(app.roles, ", ")
                ];
            })
            .value();

        const rows = [header]
            .concat(dataRows);

        downloadTextFile(rows, ",", `apps_${vm.person.employeeId}.csv`);
    };

}

controller.$inject = [
    "ServiceBroker"
];


const component = {
    template,
    bindings,
    controller
};


const id = "waltzPersonAppsSection";


export default {
    component,
    id
};

